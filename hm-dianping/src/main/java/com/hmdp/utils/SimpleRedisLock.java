package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.support.collections.DefaultRedisList;

import java.util.Collections;
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
    //ID标识前缀 --用来区别不同的jvm进程防止锁误删
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    //脚本文件定义
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //利用静态代码块进行初始化
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //指定静态文件位置
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        //设置返回值类型
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的时间，过期自动释放
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识 -- 利用uuid拼接线程号来防止误删锁问题
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadID , timeoutSec, TimeUnit.SECONDS);
        //防止自动拆箱 是true返回true  false或null一律返回false
        return Boolean.TRUE.equals(success);
    }

    /**
     * 基于lua脚本释放锁   --解决多条命令原子性问题，保证判断锁一致和释放锁为原子性操作
     */
    @Override
    public void unlock() {
        //调用lua脚本 代码编程一行解决多条件原子性问题
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name), //参数1
                ID_PREFIX +Thread.currentThread().getId()); //参数2
    }


    /**
     * 释放锁 --释放锁之前判断是不是属于自己的锁 用存取的值跟自己的标记的值做对比，防止误删
     */
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (threadId.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
