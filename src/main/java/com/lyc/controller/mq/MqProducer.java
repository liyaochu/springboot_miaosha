package com.lyc.controller.mq;

import com.alibaba.fastjson.JSON;
import com.lyc.dao.StockLogDoMapper;
import com.lyc.dataobject.StockLogDo;
import com.lyc.error.BusinessException;
import com.lyc.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @program: springboot_miaosha
 * @description:
 * @author: Jhon_Li
 * @create: 2019-07-29 19:18
 **/
@Component
public class MqProducer {
    private DefaultMQProducer producer;
    private TransactionMQProducer transactionMQProducer;
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockLogDoMapper stockLogDoMapper;

    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();

        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object args) {
                //真正要做的a事,创建订单
                String stockLogId=null;
                try {
                    Integer itemId = (Integer) ((Map) args).get("ItemId");
                    Integer promoId = (Integer) ((Map) args).get("promoId");
                    Integer userId = (Integer) ((Map) args).get("userId");
                    Integer amount = (Integer) ((Map) args).get("amount");
                    stockLogId = (String) ((Map) args).get("stockLogId");

                    orderService.createOrder(userId, itemId, promoId, amount,stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    //设置stockLog为回滚状态
                    StockLogDo stockLogDo = stockLogDoMapper.selectByPrimaryKey(stockLogId);
                    stockLogDo.setStatus(3);
                    stockLogDoMapper.updateByPrimaryKeySelective(stockLogDo);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }

                return LocalTransactionState.COMMIT_MESSAGE;
            }

            /**
             *
             * @param messageExt
             * @return 当上面的createOrder 方法,执行后,如果消息中间件一直未收到commit,rollback,就会执行checkLocalTransaction的回调,
             * 来判断是否扣减库存成功,
             */
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                //根据是否扣减库存成功,来判断要返回commit,RollBack还是继续UNKNOWN
                byte[] body = messageExt.getBody();

                String str = new String(body);
                Map<String, Object> map = JSON.parseObject(str, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                String stockLogId = (String) map.get("stockLogId");
                StockLogDo stockLogDo = stockLogDoMapper.selectByPrimaryKey(stockLogId);
                if (Objects.isNull(stockLogDo)){
                    return LocalTransactionState.UNKNOW;
                }
                if (stockLogDo.getStatus().intValue()==2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if(stockLogDo.getStatus().intValue()==1){
                    return LocalTransactionState.UNKNOW;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }


    //事物性同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId, Integer promoId, Integer itemId, Integer amount,String stockLogId) {
        HashMap<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        bodyMap.put("stockLogId", stockLogId);
        HashMap<String, Object> argsMap = new HashMap<>();
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("stockLogId", stockLogId);


        Message message = new Message(topicName, "increase", JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult transactionSendResult = null;
        try {
            //发送的这条消息是受保护状态,在这个状态下是不被消费的
            transactionSendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }

        if(transactionSendResult.getLocalTransactionState()==LocalTransactionState.ROLLBACK_MESSAGE){
            return false;
        }else if(transactionSendResult.getLocalTransactionState()==LocalTransactionState.COMMIT_MESSAGE){
            return true;

        }else{
            return false;
        }
    }


    //同步库存扣减消息
    public boolean asyncReduceStock(Integer itemId, Integer amount) {
        HashMap<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        Message message = new Message(topicName, "increase", JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));


        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
