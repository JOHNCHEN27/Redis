package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.support.BiIntFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lnc
 * @since 2023/12/2
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {



    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    /**
     * 根据商品id查询数据
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
       //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
       // Shop shop = queryWithLogicalExpire(id);
       // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null){
            Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //缓存击穿 --逻辑过期解决 缓存重建
    public Shop queryWithLogicalExpire(Long id){
        //1、从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY +id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2、判断是否存在
        if (StrUtil.isBlank(shopJson)){
            //不存在返回null
            return null;
        }
        //3、缓存命中 把JSON反序列化成对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //4.1 未过期返回shop
            return shop;
        }
        //4.2 已经过期 需要缓存重建
        //5、缓存重建
        //5.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY +id;
        boolean isLock = tryLock(lockKey);
        //5.2 判断是否加锁成功
        if (isLock){
            //TODO 5.3  注意加锁成功需要再次查询redis缓存 如果存在则无需开启独立线程重建缓存，防止多线程重复查询

            //加锁成功 开启独立线程去查询数据库 构建缓存
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    //重建缓存
                    this.saveShopToRedis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //5.3 加锁失败 返回过期的商铺信息
        return shop;
    }

    //缓存击穿 --互斥锁解决缓存重建
    //模拟redis缓存崩溃 redis宕机的情况 如何进行缓存重建
    public Shop queryWithMutex(Long id){
        //1、从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY +id;

        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
        //2、判断是否存在
        if (!shopMap.isEmpty()){
            //未命中返回 null
            if (shopMap.get("error") != null && shopMap.get("error").equals("0")){
               // return Result.fail("该店铺信息不存在");
                return null;
            }
            //不为空返回
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            return shop;
        }

        //3、实现缓存重建
        //3.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //3.2 判断是否成功
            if (!isLock){
                //3.3失败 休眠并重试
                Thread.sleep(50);
                //睡眠之后继续查询redis缓存
                return queryWithMutex(id);
            }
            //3.4 TODO 成功 查询数据库之前首先要再次查询redis缓存 防止前面线程已经写入缓存
            //再次查询redis缓存
            Map<Object, Object> shopMap1 = stringRedisTemplate.opsForHash().entries(key);
            //判断是否存在 存在直接返回 无需查询数据库重建
            if (!shopMap1.isEmpty()){
                //未命中返回 null
                if (shopMap1.get("error") != null && shopMap1.get("error").equals("0")){
                    // return Result.fail("该店铺信息不存在");
                    return null;
                }
                //不为空返回
                Shop shop1 = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
                return shop1;
            }

            shop = getById(id);
            //4、判断是否存在
            if (shop == null){
                //不存在 写入一个空值 有效解决缓存穿透问题 设置有效期为两分钟
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("error","0");
                stringRedisTemplate.opsForHash().putAll(key,hashMap);
                stringRedisTemplate.expire(key,CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //5、存在存入redis缓存
            //将查询出的shop类型转化为map
            Map<String, Object> mapShop = BeanUtil.beanToMap(shop,new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((filedName,filedValue)->
                                    filedValue!= null ? filedValue.toString() : ""));

            stringRedisTemplate.opsForHash().putAll(key,mapShop);
            //为缓存设置过期时间
            stringRedisTemplate.expire(key,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unlock(lockKey);

        }
        return shop;
    }

    //缓存穿透
    public Shop queryWithPassThrough(Long id){
        //1、从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY +id;

        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
        log.info("查询到redis缓存：{}",shopMap);
        //2、判断是否存在
        if (!shopMap.isEmpty()){
            //判断是否是 error 0 是则说明发生了缓存穿透 直接返回错误数据
            if (shopMap.get("error") != null && shopMap.get("error").equals("0")){
                //return Result.fail("该店铺信息不存在");
                return null;
            }
            //不为空返回
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            log.info("转化后的shop:{}",shop);
           // return Result.ok(shop);
            return shop;
        }

        //3、为空查询数据库
        Shop shop = getById(id);
        //4、判断是否存在
        if (shop == null){
            //不存在 写入一个空值 有效解决缓存穿透问题 设置有效期为两分钟
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("error","0");
            stringRedisTemplate.opsForHash().putAll(key,hashMap);
            stringRedisTemplate.expire(key,CACHE_NULL_TTL,TimeUnit.MINUTES);
            //return Result.fail("店铺不存在");
            return null;
        }
        //5、存在存入redis缓存
        //将查询出的shop类型转化为map
        Map<String, Object> mapShop = BeanUtil.beanToMap(shop,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName,filedValue)->
                                filedValue!= null ? filedValue.toString() : ""));

        stringRedisTemplate.opsForHash().putAll(key,mapShop);
        //为缓存设置过期时间
        stringRedisTemplate.expire(key,CACHE_SHOP_TTL, TimeUnit.MINUTES);

       // return Result.ok(shop);
        return shop;
    }

    //尝试获取锁
    private boolean tryLock(String key){
        //利用setnx 设置锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //利用BooleanUtil.isTrue 工具类方法来防止boolean的拆箱问题
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //TODO 封装逻辑过期时间 考虑改进用Hash类型存储
    public void saveShopToRedis(Long id,Long expireSeconds){
        //查询店铺数据
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));

    }

    /**
     * 更新商铺信息 并删除缓存
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result updateShop(Shop shop) {
        //1、先更新数据库
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺Id不能为空");
        }
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY +id);
        return Result.ok();
    }

    /**
     * 按商铺类型查询商铺信息
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1、判断是否需要根据坐标查询
        if (x == null || y ==null){
            //经度纬度为空 直接返回数据库
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        //2、计算分页参数 从哪开始查 差多少数据
        int from =(current -1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //3、redis 按距离排序 分页 结果：shopId distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000), //默认单位是米
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //4、解析出id
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //判断size 是否小于from
        if (list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        //4.1 截取from - end 部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach( result->{
            //获取商铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5、根据id查询商铺
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + ids + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
