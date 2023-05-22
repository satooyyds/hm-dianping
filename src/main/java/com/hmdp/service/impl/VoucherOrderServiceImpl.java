package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.RedisConstants.ORDER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@SuppressWarnings("all")
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherOrderService voucherOrderService;

    private static final DefaultRedisScript<Long> SEKILL_SCRIPT;

    static {
        SEKILL_SCRIPT = new DefaultRedisScript<>();
        //脚本加载路径
        SEKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        //脚本返回结果
        SEKILL_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTORS.submit(new VoucherOrderHandler());
    }

    /*
    * 生成订单阻塞队列
    * */
    private BlockingQueue<VoucherOrder> sekill_order_bq = new ArrayBlockingQueue<>(1024*1024);

    /*
    * 异步线程生成订单
    * */
    private static  final ExecutorService SECKILL_ORDER_EXECUTORS = Executors.newSingleThreadExecutor();
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder order = sekill_order_bq.take();
                    voucherOrderHandler(order);
                } catch (InterruptedException e) {
                    log.debug("处理生成订单异常："+e);
                }

            }
        }
    }



    /*
    * 秒杀优化性能
    * */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //1.获取优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.查看优惠券是否能抢
        //2.1 是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("抢购未开始！");
        }
        //2.1 是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("抢购已结束！");
        }

        //执行lua脚本查验库存和一人一单
        Long flag = stringRedisTemplate.execute(
                SEKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        if (flag != 0L) {
            return Result.fail(flag == 1L ? "该券已被抢购完！":"用户已抢购过该券！");
        }

        //生成订单id
        long id = redisIdWorker.nextId(ORDER_KEY);

        //加入阻塞队列
        // 6.生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1 优惠券id
        voucherOrder.setVoucherId(voucherId);
        //6.2 用户id
        voucherOrder.setUserId(userId);
        //订单id
        voucherOrder.setId(id);
        sekill_order_bq.add(voucherOrder);

        return Result.ok(id);
    }

    @Transactional
    @Override
    public void voucherOrderHandler(VoucherOrder voucherOrder) {
        //5.优惠券-1
        boolean flag = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)     //  用stock代替版本号作用，作为乐观锁使用
                .update();
        if (!flag){
            log.debug("优惠券-1失败！");
        }
        save(voucherOrder);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//
//        //1.获取优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.查看优惠券是否能抢
//        //2.1 是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("抢购未开始！");
//        }
//        //2.1 是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("抢购已结束！");
//        }
//        //3.1普通互斥锁
//        //库存是查询是否被更改适合用乐观锁，用户是否订购过是查询是否被插入，只能用悲观锁
//        //用userid做锁，保证只有同一个用户发送的抢购请求串行执行，加intern保证userid对象的唯一性
//        //把整个返回包含起来可以保证在数据返回之后才释放锁
////        synchronized (userId.toString().intern()) {
////            //如果不加voucherOrderService，默认调用的是this.createVoucherOrder(voucherId, userId, voucher)
////            //this代表VoucherOrderServiceImpl对象
////            //而spring事务Transactional管理的对象是voucherOrderService（代理对象）
////            return voucherOrderService.createVoucherOrder(voucherId, userId, voucher);
////        }
//
//        //3.2分布式锁，解决集群模式下锁失效问题
//        SimpleRedisLock redisLock = new SimpleRedisLock(ORDER_KEY+userId, stringRedisTemplate);
//        boolean isLock = redisLock.tryLock(1200L);
//        if(!isLock) {
//            return Result.fail("一个用户只能抢购一张票！");
//        }
//        try {
//            return voucherOrderService.createVoucherOrder(voucherId, userId, voucher);
//        } finally {
//            redisLock.unlock();
//        }
//    }

    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId, Long userId, SeckillVoucher voucher) {
        //3.查看库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("优惠券已被抢购完！");
        }
        //4.查看该用户是否已抢购过该券
        VoucherOrder order = query().eq("user_id", userId).eq("voucher_id", voucherId).one();
        if(order != null) {
            return Result.fail("该用户已抢购过该券！");
        }
        //5.优惠券-1
        boolean flag = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)     //  用stock代替版本号作用，作为乐观锁使用
                .update();
        if (!flag){
            return Result.fail("抢购失败！");
        }
        //6.生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1 优惠券id
        voucherOrder.setVoucherId(voucherId);
        //6.2 用户id
        voucherOrder.setUserId(userId);
        //6.3 订单id
        long id = redisIdWorker.nextId(ORDER_KEY);
        voucherOrder.setId(id);
        save(voucherOrder);
        return Result.ok(id);
    }
}
