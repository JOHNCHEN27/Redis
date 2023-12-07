package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

/**
 * @author LNC
 * @version 1.0
 * @description Redisson测试
 * @date 2023/12/7 16:23
 */
@Slf4j
@SpringBootTest
public class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    //lock初始化
    @BeforeEach
    void setUp(){
        lock = redissonClient.getLock("order");
    }

    /**
     * redisson 提供的锁具有可重入特性
     * 可重入 -- 一个方法加锁之后调用另一个方法，另一方方法中继续加锁 使用一个值来记录锁的重入次数
     * 使用的类型为 Hash类型
     */
    @Test
    void method1(){
        //尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock){
            log.error("获取锁失败... 1");
            return;
        }
        try {
            log.info("获取锁成功.... 1");
            method2();
            log.info("开始执行业务... 1");
        }finally {
            log.warn("准备释放锁.... 1");
            lock.unlock();
        }
    }

    void method2(){
        //尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock){
            log.error("获取锁失败... 2");
            return;
        }
        try {
            log.info("获取锁成功.... 2");
            log.info("开始执行业务... 2");
        }finally {
            log.warn("准备释放锁.... 2");
            lock.unlock();
        }
    }
}
