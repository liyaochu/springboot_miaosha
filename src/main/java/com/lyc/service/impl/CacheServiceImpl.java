package com.lyc.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lyc.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * @program: springboot_miaosha
 * @description:
 * @author: Jhon_Li
 * @create: 2019-07-29 15:30
 **/
@Service
public class CacheServiceImpl implements CacheService {

    private Cache<String,Object> commonCache=null;

    @PostConstruct
    public void init(){
        commonCache= CacheBuilder.newBuilder()
                //设置缓存容器的初始容量
                .initialCapacity(10)
                //缓存中最大可以存100个key,超过100个之后,会按照lru算法移除缓存
                .maximumSize(100)
                //设置多少秒过期
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();

    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
