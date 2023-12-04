package com.hmdp.utils;

import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @author LNC
 * @version 1.0
 * @description 缓存工具类
 * @date 2023/12/4 8:58
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    //构造方法
    public CacheClient (StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //添加缓存 设置过期时间 保证一致性
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //设置逻辑过期时间 -- 解决缓存击穿问题
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透  -- 发送缓冲穿透的时候 设置null值或其他值 并设置过期时间
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1、从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、判读是否存在
        if (StrUtil.isNotBlank(json)){
            //存在直接返回
            return JSONUtil.toBean(json,type);
        }

        // 判断命中的是否是null值
        if (json != null){
            //返回一个错误信息
            return null;
        }

        //3、不存在查询数据库 函数式编程 ID是参数类型 R是返回值类型
        R r = dbFallBack.apply(id);
        //4、判断数据库查询是否为null
        if (r == null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //5、存在写入redis 调用编写的set方法
        this.set(key,r,time,unit);

        return r;
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit){
        String key =keyPrefix +id;
        //1、从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、判断是否存在
        if (StrUtil.isBlank(json)){
            //3、不存在返回错误信息
            return null;
        }
        //3、缓存命中 需要把JSON反序列化成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);

        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4、判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期直接返回店铺信息
            return r;
        }
        //5、已过期 缓存重建
        String lockKey =LOCK_SHOP_KEY +id;
        boolean isLock = tryLock(lockKey);
        //6、判断是否加锁成功
        if (isLock){
            //加锁成功 开启独立线程 实现缓存重建
            //查询数据库之前 再次查询redis缓存 进行双重检测
            //1、从redis查询缓存
            String json1 = stringRedisTemplate.opsForValue().get(key);

            //2、缓存命中 需要把JSON反序列化成对象
            RedisData redisData1 = JSONUtil.toBean(json1, RedisData.class);
            R r1 = JSONUtil.toBean((JSONObject) redisData1.getData(), type);
            LocalDateTime expireTime1 = redisData1.getExpireTime();
            if (expireTime1.isAfter(LocalDateTime.now())){
                return r1;
            }

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
//
                    //查询数据库
                    R r2 = dbFallBack.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r2,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //最后记得释放锁
                    unlock(lockKey);
                }
            });
        }
        //未抢夺到锁 返回过期的数据
        return r;

    }


    //获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


}
