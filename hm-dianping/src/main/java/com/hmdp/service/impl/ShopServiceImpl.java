package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
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
        //2、判断是否存在
        if (!shopMap.isEmpty()){
            //不为空返回
            Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
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
                        .setFieldValueEditor((filedName,filedValue)->
                                filedValue.toString())
                        .setIgnoreNullValue(true));
        stringRedisTemplate.opsForHash().putAll(key,mapShop);

        return Result.ok(shop);
    }
}
