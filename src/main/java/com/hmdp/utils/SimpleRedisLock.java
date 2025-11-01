package com.hmdp.utils;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.lang.UUID;
import com.sun.el.parser.AstTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.AbstractCollection;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String s, StringRedisTemplate stringRedisTemplate) {
    }
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation((Resource) new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程标识
        String threadId= ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+threadId,threadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        //调用Lua脚本
////        //获取线程标识
        String threadId= ID_PREFIX + Thread.currentThread().getId();
////        //获取锁标识
////        String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+threadId);
//        //比较
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX+threadId);
//        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX+threadId),
                ID_PREFIX+Thread.currentThread().getId());
    }
}
