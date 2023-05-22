package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static final String keyPrefix = "lock:";
    private static final String ID_Prefix = UUID.randomUUID().toString(true)+"-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //脚本加载路径
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //脚本返回结果
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        long threadId = Thread.currentThread().getId();
        //获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(keyPrefix+name, ID_Prefix+threadId, timeoutSec, TimeUnit.SECONDS);
        //防止拆箱后出现空值情况
        return BooleanUtil.isTrue(flag);
    }

    @Override
    public void unlock() {
        //lua脚本保证查验线程标识和释放锁的操作原子性，防止锁被误删
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(keyPrefix+name),
                ID_Prefix+Thread.currentThread().getId()
        );

        //获取线程标识

//        String realThread = stringRedisTemplate.opsForValue().get(keyPrefix + name);
//        if (.equals(realThread)) {
//            stringRedisTemplate.delete(keyPrefix+name);
//        }
    }
}
