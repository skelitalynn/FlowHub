package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@EnableAspectJAutoProxy(exposeProxy = true)
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private IVoucherOrderService proxy;

    private  static final DefaultRedisScript<Long> SECKILL_SCRIPT ;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);

    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //1、获取消息队列中订单信息
                    List<MapRecord<String ,Object,Object>>list=stringRedisTemplate.opsForStream().read(Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
                    //2.判断订单信息是否为空
                    if(list.isEmpty()||list==null){
                        continue;
                    }
                    //解析数据
                    MapRecord<String ,Object,Object>record=list.get(0);
                    Map<Object,Object> value=record.getValue();
                    VoucherOrder voucherOrder= BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
                    //3.创建订单
                    createVoucherOrder(voucherOrder);
                    //4.确认消息XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
                }catch (Exception e){
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }
        }
    }
    private void handlePendingList(){
        while(true){
            try {
                //1.获取pending-list中的订单信息
                List<MapRecord<String,Object,Object>> list=stringRedisTemplate.opsForStream().read(Consumer.from("g1","c1"));
                StreamReadOptions.empty().count(1);
                StreamOffset.create("stream.orders", ReadOffset.from("0"));
                //2.判断订单信息是否为空
                if(list.isEmpty()||list==null){
                    break;
                }
                //3.解析数据
                MapRecord<String,Object,Object>record=list.get(0);
                Map<Object,Object> value=record.getValue();
                VoucherOrder voucherOrder= BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
                //3.创建订单
                createVoucherOrder(voucherOrder);
                //4.确认消息
                stringRedisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
            }catch (Exception e){
                log.error("处理pendding订单异常",e);
                try {
                    Thread.sleep(20);
                }catch (Exception ex){
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //1.获取用户
        Long userId=voucherOrder.getUserId();
        //2.创建锁对象
        RLock redisLock=redissonClient.getLock("lock:order:"+userId);
        boolean isLock = redisLock.tryLock();
        if(isLock){
            log.error("不允许重复下单");
            return;
        }try{
            //由于是spring的事务是放在ThreadLocal中，此时是多线程，事务会失效
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            redisLock.unlock();
        }
    }



    @Override
    public Result seckillVoucher(Long voucherId){
        //获取用户
        Long userId=UserHolder.getUser().getId();
        long orderId=redisIdWorker.nextId("order");

        //执行lua脚本,若这段代码执行完成，代表有购买资格，且订单发送到了消息队列中
        Long result=stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
        voucherId.toString(),userId.toString(),String.valueOf(orderId));
        int r=result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //TODO 保存阻塞队列
        //返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public  void createVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = UserHolder.getUser().getId();
        //仅对同一用户id加锁，不同用户请求可以并行处理
        //但是同一用户同时抢购多个优惠券无法同时并行
            // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id",voucherOrder.getVoucherId()).count();
            // 5.2.判断是否存在
            if (count > 0) {
                log.error("用户已经购买过了");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id",voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("<UNK>");
                return;
            }


            save(voucherOrder);
        }
    }
