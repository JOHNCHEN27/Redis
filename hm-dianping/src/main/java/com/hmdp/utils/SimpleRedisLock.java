package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author LNC
 * @version 1.0
 * @description
 * @date 2023/12/6 15:45
 */
public class SimpleRedisLock implements Ilock{

    //锁的名称
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate){
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //锁名称前缀
    private static final String KEY_PREFIX = "lock:";
    //ID标识前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的时间，过期自动释放
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadID , timeoutSec, TimeUnit.SECONDS);
        //防止自动拆箱 是true返回true  false或null一律返回false
        return Boolean.TRUE.equals(success);
    }


    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
