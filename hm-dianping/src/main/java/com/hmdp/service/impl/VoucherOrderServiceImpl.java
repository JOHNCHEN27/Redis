package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
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

    //注入本类代理对象

    @Resource
    IVoucherOrderService proxy;


    @Resource
    private RedisWorker redisWorker;
    /**
     * 秒杀下单实现
     * @param voucherId
     * @return
     */

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
//        seckillVoucher.setStock(seckillVoucher.getStock()-1);
//        seckillVoucher.setUpdateTime(LocalDateTime.now());
//        boolean success = seckillVoucherService.updateById(seckillVoucher);

        Long userId = UserHolder.getUser().getId();
       //对用户进行加锁 用户id作为锁 此处的intern 是取字符串的值 保证值相同 而不是new的新对象作为锁
        synchronized (userId.toString().intern()) {
            //利用代理对象去执行事务方法  非事务方法调用事务方法 事务不生效
            return proxy.createVoucherOrder(voucherId);
        }
    }

    //创建优惠卷订单
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5.1 保证一人一单   -- 通过查询数据库看订单是否以及存在
        Long userId = UserHolder.getUser().getId(); //获取当前用户id


            VoucherOrder order = getOne(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getUserId, userId)
                    .eq(VoucherOrder::getVoucherId, voucherId));
            if (order != null) {
                //如果订单以及存在 则返回错误信息
                return Result.fail("不允许重复购买");
            }

            //5.2 乐观锁解决超卖现象 用商铺库存 > 0 作为修改时的条件 如果成立则扣减
            // 不一致说明在此之间发生了改变 不进行操作
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock -1") //set stock = stock -1
                    .eq("voucher_id", voucherId).gt("stock", 0) //where id =? and stock >0
                    .update();
            if (!success) {
                //扣减失败
                return Result.fail("库存不足!");
            }

            //6、创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            //6.1 订单id用Redis生成的全局唯一ID
            long orderId = redisWorker.nextId("order");
            voucherOrder.setId(orderId);
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
