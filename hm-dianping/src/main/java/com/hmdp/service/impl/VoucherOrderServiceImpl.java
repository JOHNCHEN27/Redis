package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.SECKILL_VOUCHER_INFO_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lnc
 * @since 2023-12-5
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    //注入本类代理对象 -- 将需要代理的方法提升为接口 注入接口类对象 利用此对象去执行代理的方法
    //利用代理对象执行事务方法时事务生效 ------ 非事务方法调用事务方法事务不生效 没有被spring管控
    @Resource
    IVoucherOrderService proxy;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //注入配置的redissonClient
    @Resource
    private RedissonClient redissonClient;


    @Resource
    private RedisWorker redisWorker;

    //将lua脚本定义成常量
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //利用 静态代码块赋初值
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("sekill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //创建阻塞队列
    private BlockingQueue<VoucherOrder> ordersTasks = new ArrayBlockingQueue<>(1024*1024);
    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //初始化方法 -- 加上PostConstruct注解 在类初始化的时候就会执行此方法
    @PostConstruct
    private void init(){
        //在类初始化时 就开始执行线程
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    //创建一个内部类 用来执行线程任务
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    //1、获取队列中的订单信息
                    VoucherOrder voucherOrder = ordersTasks.take();
                    //2、创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e){
                    log.error("处理订单异常",e);
                }

            }
        }
    }

    //创建订单
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        if (!isLock){
            //获取锁失败
            log.info("不允许重复下单");
            return;
        }
        try {
            //代理对象完成创建订单任务
           proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }
    /**
     * 秒杀下单实现
     * @param voucherId
     * @return
     */

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1、查询优惠卷信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2、判断秒杀是否开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
//            //如果秒杀卷开始时间在 当前时间之后说明未开始
//            return Result.fail("秒杀未开始!!!");
//        }
//        //3、判断秒杀是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
//            //结束时间在当前时间之前 说明已经结束了
//            return Result.fail("秒杀已经结束!!!");
//        }
//        //4、判断库存是否充足
//        if (seckillVoucher.getStock()<1){
//            //库存不足 返回错误信息
//            return Result.fail("已经被抢空了~");
//        }
//        //5、充足扣减库存
////        seckillVoucher.setStock(seckillVoucher.getStock()-1);
////        seckillVoucher.setUpdateTime(LocalDateTime.now());
////        boolean success = seckillVoucherService.updateById(seckillVoucher);
//
//
//        Long userId = UserHolder.getUser().getId();
////       //对用户进行加锁 用户id作为锁 此处的intern 是取字符串的值 保证值相同 而不是new的新对象作为锁
////        synchronized (userId.toString().intern()) {
////            //利用代理对象去执行事务方法  非事务方法调用事务方法 事务不生效
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        //利用redis分布式锁来解决不同JVM仍可实现一人多单问题
//       // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//        //利用redisson提供的锁机制来获取锁 --不用自己编写锁方法
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //尝试获取锁 不带参数表示失败不重试 redisson锁提供了默认锁释放时间
//        boolean isLock = lock.tryLock();
//        if (!isLock){
//            //如果获取锁失败 根据业务返回错误信息或重试
//            return Result.fail("只允许购买一单");
//        }
//        try {
//            //利用代理对象执行事务方法
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //如果执行过程抛出异常 手动释放锁
//            lock.unlock();
//        }
//    }


    /**
     * 秒杀下单优化 --利用lua脚本和redis
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId   = user.getId();
        //判断秒杀是否开始 从redis查询
        Map<Object, Object> voucherMap = stringRedisTemplate.opsForHash().entries(SECKILL_VOUCHER_INFO_KEY + voucherId);
        String begin =  voucherMap.get("beginTime").toString().replace("T"," ");
        String end = voucherMap.get("endTime").toString().replace("T", " ");
        LocalDateTime beginTime = LocalDateTime.parse(begin ,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime endTime = LocalDateTime.parse(end,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("抢购还未开始");
        }
        if (endTime.isBefore(LocalDateTime.now())){
            return Result.fail("抢购已经结束");
        }

        // 1、执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), voucherId.toString(),userId.toString());
        //2、判断结果是否为0var
        assert result != null;
        int r  = result.intValue();
        if (r != 0){
            // 不为0 说明用户下过单或者库存不足
            return Result.fail(r == 1 ? "已经被抢空了！":"不允许重复下单");
        }
        // 3 为0 有购买资格 把下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        //3.1 放入阻塞队列
        ordersTasks.add(voucherOrder);


        //4. 返回订单id
        return Result.ok();

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

    /**
     * 改造后的创建订单方法 --异步秒杀优化
     * @param voucherOrder
     * @return
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.1 保证一人一单   -- 通过查询数据库看订单是否以及存在
        Long userId = voucherOrder.getUserId(); //获取当前用户id


        VoucherOrder order = getOne(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherOrder.getVoucherId()));
        if (order != null) {
            //如果订单以及存在 则返回错误信息
            log.error("用户已经购买过一次");
            return ;
        }

        //5.2 乐观锁解决超卖现象 用商铺库存 > 0 作为修改时的条件 如果成立则扣减
        // 不一致说明在此之间发生了改变 不进行操作
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1") //set stock = stock -1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) //where id =? and stock >0
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足");
            return ;
        }

        //创建订单
        save(voucherOrder);
    }
}
