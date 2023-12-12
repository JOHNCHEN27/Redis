package com.hmdp.service;

import com.hmdp.controller.ShopController;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    //查询商户信息
    Result queryById(Long id);

    //更新商铺信息
    Result updateShop(Shop shop);

    //按商铺类型查询商铺
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
