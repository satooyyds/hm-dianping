---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by HONOR.
--- DateTime: 2023/5/9 15:31
---

---参数id
---优惠券id
local voucherID = ARGV[1];
---用户id
local userID = ARGV[2];

---库存id，用于查验库存
local stockKey = "seckill:stock:" .. voucherID;
---订单id，用于查验一人一单
local orderKey = "seckill:order:" .. voucherID;

---查验库存
if(tonumber(redis.call('get',stockKey)) <= 0) then
    return 1
end

---查验一人一单
if (redis.call('sismember',orderKey,userID) == 1) then
    return 2
end

redis.call('incrby',stockKey, -1)
redis.call('sadd',orderKey,userID)

return 0