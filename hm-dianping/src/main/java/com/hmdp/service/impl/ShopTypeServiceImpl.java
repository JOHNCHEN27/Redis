package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lnc
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    ShopTypeMapper shopTypeMapper;

    /**
     * 查询商品类型列表
     * @return
     */
    @Override
    public Result queryShopTypeList() {
        //1、从redis缓存中查询
        String key = SHOP_TYPE_LIST;

        List<String> shopType = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2、判断是否为空
        if (shopType != null && !shopType.isEmpty()){
            //3、不为空返回redis缓存 首先转化为List类型
            List<ShopType> shopTypes = parseShopTypeString(shopType.get(0));
            System.out.println("查询到redis缓存 -- 转化后的shoptypes: " +shopTypes);
            return Result.ok(shopTypes);

        }
        //4、为空则查询数据库
        List<ShopType> shopTypes = shopTypeMapper.selectAll();
        if (shopTypes == null){
            return Result.fail("商品列表为空");
        }
        //5、存入redis缓存 将List所有元素拼接成一个字符串
        StringBuffer result = new StringBuffer();
        for (ShopType shopType1 : shopTypes) {
            //遍历List拿到每个item 拼接到StringBuffer
            result.append(shopType1.toString());
        }
        String finalShopType = result.toString();
        System.out.println("finalShopType = " + finalShopType);

        stringRedisTemplate.opsForList().rightPush(key,finalShopType);
        //为缓存设置过期时间
        stringRedisTemplate.expire(key,CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

    return Result.ok(shopTypes);
    }

    //将字符串ShopType转化为List类型
    public static List<ShopType> parseShopTypeString(String shopTypeString) {
        List<ShopType> shopTypes = new ArrayList<>();

        // 定义正则表达式模式以提取值
        Pattern pattern = Pattern.compile("ShopType\\(id=(\\d+), name=(.*?), icon=(.*?), sort=(\\d+), createTime=(.*?), updateTime=(.*?)\\)");

        // 使用正则表达式匹配字符串
        Matcher matcher = pattern.matcher(shopTypeString);

        // 遍历匹配结果
        while (matcher.find()) {
            // 提取匹配到的值
            Long id = Long.valueOf(Integer.parseInt(matcher.group(1)));
            String name = matcher.group(2);
            String icon = matcher.group(3);
            int sort = Integer.parseInt(matcher.group(4));
            //将字符串类型转化为LocalDateTime类型
            LocalDateTime createTime = LocalDateTime.parse(matcher.group(5), DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime updateTime = LocalDateTime.parse(matcher.group(6), DateTimeFormatter.ISO_DATE_TIME);


            // 创建 ShopType 对象并添加到列表中
            ShopType shopType = new ShopType(id, name, icon, sort, createTime, updateTime);
            shopTypes.add(shopType);
        }

        return shopTypes;
    }
}
