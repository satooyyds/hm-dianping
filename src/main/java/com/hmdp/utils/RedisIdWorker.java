package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /*
    * 开始时间戳
    * */
    private static final long BEGIN_TIMESTAMP = 1672571100L;

    /*
    * 序列号位数
    * */
    private static final int COUNT_BITS = 32;

    /*
    * 利用redis生成全局唯一ID
    * long(64bit) = 符号位(1bit) + 时间戳(31bit) + 序列号(32bit)
    * */
    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long local = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = local - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1 获取当前时间,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd:"));
        //2.2 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + date);
        //3.拼接
        return count << COUNT_BITS | timestamp;
    }

}
