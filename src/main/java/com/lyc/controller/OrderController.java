package com.lyc.controller;


import com.google.common.util.concurrent.RateLimiter;
import com.lyc.controller.mq.MqProducer;
import com.lyc.error.BusinessException;
import com.lyc.error.EmBusinessError;
import com.lyc.response.CommonReturnType;
import com.lyc.service.ItemService;
import com.lyc.service.OrderService;
import com.lyc.service.PromoService;
import com.lyc.service.model.OrderModel;
import com.lyc.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Created by hzllb on 2018/11/18.
 */
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;


    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter rateLimiter;

    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(20);
        rateLimiter = RateLimiter.create(300);
    }

    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId, @RequestParam(name = "promoToken", required = false) String promoToken) throws BusinessException {


        if (rateLimiter.acquire()<0){
            throw new BusinessException(EmBusinessError.RATELIMIT);
        }
        String token = httpServletRequest.getParameterMap().get("token")[0];
        //  Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (Objects.isNull(userModel)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }

        if (promoId != null) {
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_" + promoId + "_userId" + userModel.getId() + "_itemId" + itemId + promoId);
            if (Objects.isNull(inRedisPromoToken)) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀校验令牌失败");
            }
            if (!Objects.equals(inRedisPromoToken, promoToken)) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀校验令牌失败");
            }
        }


        //判断库存是否售罄
        if (redisTemplate.hasKey("promo_item_stock_invalid" + itemId)) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //拥塞窗口为20,的等待对列,用来队列泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //加入库存流水init状态
                String stockLogId = itemService.initStockLog(itemId, amount);

                //再去完成对应下单事务性消息机制
                boolean flag = mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId);
                if (!flag) {
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            e.printStackTrace();
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        return CommonReturnType.create(null);
    }

    @RequestMapping(value = "/generateToken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generateToken(@RequestParam(name = "itemId") Integer itemId,

                                          @RequestParam(name = "promoId") Integer promoId) throws BusinessException {


        String token = httpServletRequest.getParameterMap().get("token")[0];
        //  Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (Objects.isNull(userModel)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
        if (StringUtils.isEmpty(promoToken)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");

        }

        return CommonReturnType.create(promoToken);
    }
}
