package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
     * 线程池
     * */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*
    * 存入数据
    * */
    public void set(String key, Object o, Long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(o),ttl, unit);
    }

    /*
    * 存入带逻辑过期时间的数据
    * */
    public void setLogicExpire(String key, Object o, Long ttl, TimeUnit unit) {
        RedisData<Object> objectRedisData = new RedisData<>();
        objectRedisData.setData(o);
        objectRedisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(objectRedisData));
    }

    /*
    * 解决缓存穿透
    * */
    public <T, ID> T getWithPathThrow(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback,
                                      Long ttl, TimeUnit unit) {
        String shopKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isNotBlank(json)) {
            T t = JSONUtil.toBean(json, type);
            return t;
        }
        if("".equals(json)) {
            return null;
        }
        //查询数据库重建缓存
        T t = dbFallback.apply(id);
        if (t == null) {
            //防止缓存穿透，缓存空值
            set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        set(shopKey, t, ttl, unit);

        return t;
    }

    /*
     * 逻辑删除解决缓存击穿
     * */
    public <T, ID> T getWithLogicExpire(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback,
                                               Long ttl, TimeUnit unit) {
        String shopKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T t = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //数据是否过期
        //未过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return t;
        }
        //过期
        String lockKey = SHOP_KEY + id;

        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(lockKey, stringRedisTemplate);
        boolean isLock = simpleRedisLock.tryLock(LOCK_SHOP_TTL);
        if (!isLock) {
            return t;
        }

        //doubleCheck
        if (StrUtil.isBlank(json)) {
            return null;
        }
        redisData = JSONUtil.toBean(json, RedisData.class);
        t = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return t;
        }
        //开启新线程查询数据库重建缓存
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                T t1 = dbFallback.apply(id);
                this.setLogicExpire(shopKey, t1, ttl,unit);
            } catch (Exception e) {
                throw  new RuntimeException(e);
            } finally {
                simpleRedisLock.unlock();
            }
        });
        return t;
    }
}
