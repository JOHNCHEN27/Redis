package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lnc
 * @since 2023-12-5
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisWorker redisWorker;
    /**
     * 秒杀下单实现
     * @param voucherId
     * @return
     */
    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1、查询优惠卷信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2、判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            //如果秒杀卷开始时间在 当前时间之后说明未开始
            return Result.fail("秒杀未开始!!!");
        }
        //3、判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            //结束时间在当前时间之前 说明已经结束了
            return Result.fail("秒杀已经结束!!!");
        }
        //4、判断库存是否充足
        if (seckillVoucher.getStock()<1){
            //库存不足 返回错误信息
            return Result.fail("已经被抢空了~");
        }
        //5、充足扣减库存
        seckillVoucher.setStock(seckillVoucher.getStock()-1);
        seckillVoucher.setUpdateTime(LocalDateTime.now());
        boolean success = seckillVoucherService.updateById(seckillVoucher);
        if (!success){
            //扣减失败
            return Result.fail("库存不足!");
        }
        //6、创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1 订单id用Redis生成的全局唯一ID
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setUpdateTime(LocalDateTime.now());
        //更新数据库
        save(voucherOrder);

        //返回订单id
        return Result.ok(orderId);
    }
}
