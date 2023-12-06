package com.hmdp.utils;

/**
 * @author LNC
 * @version 1.0
 * @description redis分布式锁接口类
 * @date 2023/12/6 15:43
 */
public interface Ilock {

    /**
     * 尝试获取锁 -- 非阻塞方案 只尝试一次
     * @param timeoutSec 锁持有的时间，过期自动释放
     * @return true 表示获取锁成功 false表示获取锁失败
     */
    boolean tryLock(long timeoutSec);

    void unlock();
}
