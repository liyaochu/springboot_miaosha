package com.lyc.controller.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.lyc.controller.serializer.JodaDateTimeJsonDeserializer;
import com.lyc.controller.serializer.JodaDateTimeJsonserializer;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.stereotype.Component;

/**
 * @program: springboot_miaosha
 * @description: 基于cookie 的方式完成session会话
 * @author: Jhon_Li
 * @create: 2019-07-29 11:53
 **/
@Component
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class RedisConfig {

    //重新改造redisTemplate

    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate template = new RedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);

        //1.首先解决Key的序列化方式
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);

        //解决value的序列化方式
        Jackson2JsonRedisSerializer jsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addDeserializer(DateTime.class, new JodaDateTimeJsonDeserializer());
        simpleModule.addSerializer(DateTime.class, new JodaDateTimeJsonserializer());
        //会把对应的类的信息也会存进去
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);


        objectMapper.registerModule(simpleModule);
        template.setValueSerializer(jsonRedisSerializer);
        jsonRedisSerializer.setObjectMapper(objectMapper);

        return template;
    }


}
