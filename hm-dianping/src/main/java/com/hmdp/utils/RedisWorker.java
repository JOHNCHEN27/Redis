package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author LNC
 * @version 1.0
 * @description 基于redis实现全局唯一ID
 * @date 2023/12/5 9:58
 */
@Component
public class RedisWorker {

    /**
     * 开始的时间戳
     */
    private static final long BEGIN_TIMESTAMP =1701734400L;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    public StringRedisTemplate stringRedisTemplate;

    /**
     * 利用时间戳和序列号生成全局唯一ID
     * @param keyPrefix key前缀
     * @return
     */
    public long nextId(String keyPrefix){
        //1、生成时间戳 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        //当前时间减去开始的时间戳
        long timeStamp =  nowSecond-BEGIN_TIMESTAMP;

        //2、生成序列号 利用当天日期作为key的一部分 可以保证订单号足够
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.1 自增长 设置key 返回自增的序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3、拼接并返回
        //利用位运算 让时间戳左移 序列号的位数 再用或运算把序列号填充到后面
        return timeStamp << COUNT_BITS | count;
    }


//    //查看时间戳
//    public static void main(String [] args){
//        LocalDateTime time = LocalDateTime.of(2023,12,5,0,0,0);
//        long seconds = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("second = " + seconds);
//    }
}
