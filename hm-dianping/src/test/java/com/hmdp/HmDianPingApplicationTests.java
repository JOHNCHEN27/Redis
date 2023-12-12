package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import io.lettuce.core.api.async.RedisGeoAsyncCommands;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl service;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void test(){
        service.saveShopToRedis(1L,10L);
    }

    /**
     * 预先处理加载店铺数据
     */
    @Test
    void loadShopData(){
        //1、查询所有的商铺集合
        List<Shop> list = service.list();
        //2、对集合按照商铺类型进行分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        //3、把每一组分别存储到redis中
        for (Map.Entry<Long,List<Shop>> entry : map.entrySet()){
            //获取类型id
            Long typeId = entry.getKey();
            String key =SHOP_GEO_KEY + typeId;
            //获取同类型店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //写入redis  GEOADD key 纬度 经度 member
            for (Shop shop : value){
                //stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }


    //UV统计 --HyperLogLog的用法
    @Test
    void testHyperLogLog(){
        String  [] values =new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i%1000;
            values[j] = "user_"+i;
            if (j == 999){
                //发送到redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
            }
        }
        //统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);
    }



}
