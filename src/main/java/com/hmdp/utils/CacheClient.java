package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //这啥意思？
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData=new RedisData();
        redisData.setData((String)value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        //从redis查询商铺缓存
        String json=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json)){
            //存在，直接返回
            return JSONUtil.toBean(json,type);
        }
        //TODO 为什么这样设计
        //查询是否为空值
        if(json!=null){
            //返回一个错误信息
            return null;
        }

        R r=dbFallback.apply(id);
        //空值，写入redis中，缓存穿透问题
        if(r==null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        this.stringRedisTemplate.opsForValue().set(key,(String)r,time,unit);
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        //从redis中查询商铺
        String json=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json)){
            return null;
        }
        RedisData redisData= BeanUtil.toBean(json,RedisData.class);
        //将 Redis 中存储的 JSON 数据反序列化为 Java 对象。
        R r=JSONUtil.toBean((JSONObject)redisData.getData(), type);
    LocalDateTime expireTime=redisData.getExpireTime();
    if(expireTime.isAfter(LocalDateTime.now())){
        return r;
    }
    String lockKey=LOCK_SHOP_KEY+id;
    boolean isLock=tryLock(lockKey);
    if(isLock){
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try{
                //查询数据库
                R newR=dbFallback.apply(id);
                //重建缓存
                this.setWithLogicalExpire(key,newR,time,unit);
            }catch (Exception e){
                throw new RuntimeException(e);
            }finally {
                unlock(lockKey);
            }
        });
    }
    return r;
    }

    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        String shopJson=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson,type);
        }
        if(shopJson!=null){
            return null;
        }

        String lockKey=LOCK_SHOP_KEY+id;
        R r=null;
        try{
            boolean isLock=tryLock(lockKey);
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            r=dbFallback.apply(id);
            if(r==null){
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key,(String)r,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }
        return r;
    }


    private boolean tryLock(String key){
        boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
