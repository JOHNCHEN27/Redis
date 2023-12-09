-- 基于lua脚本来实现异步秒杀  --采用redis的stream数据类型来实现阻塞队列
-- 1.参数列表
-- 1.1 优惠卷id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]
-- 1.3 订单id
local orderId = ARGV[3]

-- 2.1 库存key  .. 是将两个值拼接
local stockKey = "seckill:stock:" .. voucherId
-- 2.2 订单key
local orderKey = "seckill:order:" .. voucherId

-- 3.脚本业务
-- 3.1 判断库存是否充足 get stockKey
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 小于等于0 库存不足 返回 1
    return 1
end
-- 3.2 判断用户是否下单 sismember orderKey userId
if (redis.call('sismember',orderKey,userId) == 1) then
    -- 用户已经下单
    return 2
end
-- 3.3 扣库存 incrby stockKey -1
redis.call('incrby',stockKey,-1)
-- 3.4 下单 保存用户 sadd orderKey userId
redis.call('sadd',orderKey,userId)
-- 3.5 发送消息到队列中 XADD stream.orders * k1 v1 k2 v2
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
-- 成功返回0
return 0
