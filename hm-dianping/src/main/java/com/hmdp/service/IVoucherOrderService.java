package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    //秒杀下单
    Result seckillVoucher(Long voucherId);

    //创建优惠卷订单接口
    Result createVoucherOrder(Long voucherId);

    //改造后的创建优惠卷订单接口
    void createVoucherOrder(VoucherOrder voucherOrder);
}
