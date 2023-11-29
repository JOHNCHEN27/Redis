package com.lncanswer.config;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * @author LNC
 * @version 1.0
 * @description
 * @date 2023/11/29 20:19
 */
public class JedisConnectionFactory {

    /**
     * jedis本身线程不安全，并且频繁的创建和销毁连接会有性能损耗，
     * 因此推荐使用jedis连接池来代替jedis直连方式
     */
    private static  final JedisPool jedisPool;

    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        //最大连接
        jedisPoolConfig.setMaxTotal(8);
        //最大空闲连接
        jedisPoolConfig.setMaxIdle(8);
        //最小空闲连接
        jedisPoolConfig.setMinIdle(0);
        //设置最长等待时间
        jedisPoolConfig.setMaxWait(Duration.ofMillis(200));
        jedisPool = new JedisPool(jedisPoolConfig,"192.168.101.129",6379,1000,"redis");
    }
    //获取jedis对象
    public static Jedis getJedis(){
        return jedisPool.getResource();
    }


}
