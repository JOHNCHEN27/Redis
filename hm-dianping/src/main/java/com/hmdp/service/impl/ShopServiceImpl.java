package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lnc
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 根据商品id查询数据
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //1、从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY +id;

        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
        log.info("查询到redis缓存：{}",shopMap);
        //2、判断是否存在
        if (!shopMap.isEmpty()){
            //不为空返回
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            log.info("转化后的shop:{}",shop);
            return Result.ok(shop);
        }
        
        //3、为空查询数据库
        Shop shop = getById(id);
        //4、判断是否存在
        if (shop == null){
            return Result.fail("店铺不存在");
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

        return Result.ok(shop);
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
}
