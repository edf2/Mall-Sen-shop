package com.mallsen.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.util.CollectionUtils;
import com.mallsen.constant.InventoryConstant;
import com.mallsen.constant.MessageConstant;
import com.mallsen.constant.StatusConstant;
import com.mallsen.context.BaseContext;
import com.mallsen.dto.*;
import com.mallsen.entity.*;
import com.mallsen.exception.AddressBookBusinessException;
import com.mallsen.exception.OrderBusinessException;
import com.mallsen.mapper.*;
import com.mallsen.message.OrderAsyncMessage;
import com.mallsen.component.MqOutboxRelayer;
import com.mallsen.component.ProductCacheEvictor;
import com.mallsen.result.PageResult;
import com.mallsen.service.OrderService;
import com.mallsen.vo.OrderPaymentVO;
import com.mallsen.vo.OrderStatisticsVO;
import com.mallsen.vo.OrderSubmitVO;
import com.mallsen.vo.OrderVO;
import com.mallsen.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    /**
     * 分布式锁 TTL：下单事务含库存预占与落单，锁需足够时长避免在事务执行过程中提前过期
     */
    private static final long LOCK_TTL_SECONDS = 60L;

    /**
     * Redis 不可用降级放行时的特殊 token，用于释放锁时跳过校验
     */
    private static final String LOCK_DEGRADED = "__lock_degraded__";

    /**
     * 订单号唯一索引冲突时的最大重试次数（generateOrderNumber 毫秒级时间戳 + 随机数，
     * 唯一索引作为最终兜底，冲突则重新生成重试）
     */
    private static final int MAX_ORDER_NUMBER_RETRY = 5;

    /**
     * 订单号随机段后的进程内自增序号，进一步降低多实例并发撞号概率
     */
    private final AtomicInteger orderNumberSeq = new AtomicInteger(0);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private StockReleaseService stockReleaseService;

    @Autowired
    private PaymentServiceRouter paymentServiceRouter;

    @Autowired
    private MqOutboxMapper mqOutboxMapper;

    @Autowired
    private MqOutboxRelayer mqOutboxRelayer;

    @Autowired
    private ProductCacheEvictor productCacheEvictor;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 用户下单
     * 流程：事务外校验配送范围（百度地图 3 次 HTTP，避免长事务）-> 事务内预占库存（条件扣减，防止并发超卖）
     * -> 生成唯一订单号 -> 落订单 -> 落明细 -> 清空购物车。
     * 库存预占后无论是否支付，取消/超时都会统一归还（见 StockReleaseService）。
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();

        //幂等：同一用户并发重复提交时串行化，避免重复下单
        String lockKey = "order:submit:lock:" + userId;
        String lockToken = tryAcquireLock(lockKey);
        if (lockToken == null) {
            throw new OrderBusinessException("订单提交过于频繁，请稍后重试");
        }
        try {
            //事务外：地址校验 + 配送范围检查（3 次百度地图 HTTP 调用）。
            //网络 I/O 放在事务外，避免长事务持有数据库连接、拖垮连接池
            AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
            if (addressBook == null) {
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }
            // 百货商城采用全国配送，不限制收货地址范围。
            // 原 sky_take_out 的“百度地图配送半径 5km 校验”属外卖残留逻辑，已移除，
            // 避免其强依赖外部百度地图 AK 导致下单失败，也与商城业务口径一致。

            //事务内：预占库存 -> 落订单 -> 落明细 -> 清空购物车。
            //订单号唯一索引冲突时整体回滚并重新生成订单号重试（最多 MAX_ORDER_NUMBER_RETRY 次）
            for (int attempt = 1; attempt <= MAX_ORDER_NUMBER_RETRY; attempt++) {
                try {
                    return transactionTemplate.execute(status -> doSubmitOrder(userId, ordersSubmitDTO, addressBook));
                } catch (DuplicateKeyException e) {
                    if (attempt == MAX_ORDER_NUMBER_RETRY) {
                        log.error("订单号唯一索引连续冲突，放弃重试，userId={}", userId, e);
                        throw e;
                    }
                    log.warn("订单号唯一索引冲突，第 {}/{} 次重新生成订单号", attempt, MAX_ORDER_NUMBER_RETRY);
                }
            }
            throw new OrderBusinessException("下单失败，请稍后重试");
        } finally {
            //事务提交后再释放锁，避免 @Transactional 尚未提交时锁已被释放
            releaseLockAfterCommit(lockKey, lockToken);
        }
    }

    /**
     * 事务内下单（预占库存 + 落单 + 落明细 + 清空购物车）。
     * 被 submitOrder 的事务模板调用；订单号冲突时整体回滚，由外层重试。
     */
    private OrderSubmitVO doSubmitOrder(Long userId, OrdersSubmitDTO ordersSubmitDTO, AddressBook addressBook) {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list == null || list.size() == 0) {
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        String orderNumber = generateOrderNumber();

        //预占库存：条件更新（stock >= quantity）原子扣减，任一商品库存不足则整体回滚；
        //订单金额由服务端按 product.price × number 重算，客户端传入的 amount 一律忽略，防止篡改价格
        Map<Long, BigDecimal> lineAmountMap = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ShoppingCart cart : list) {
            if (cart.getProductId() == null) {
                continue;
            }
            Product product = productMapper.getById(cart.getProductId());
            if (product == null) {
                throw new OrderBusinessException("商品 " + cart.getName() + " 不存在");
            }
            if (product.getStatus() != StatusConstant.ENABLE) {
                throw new OrderBusinessException("商品 " + product.getName() + " 已下架");
            }
            preDeductStockAndRecord(product, cart.getNumber(), userId, orderNumber, "下单预占库存：" + orderNumber);

            BigDecimal lineAmount = roundMoney(product.getPrice().multiply(BigDecimal.valueOf(cart.getNumber())));
            lineAmountMap.put(cart.getProductId(), lineAmount);
            totalAmount = totalAmount.add(lineAmount);
        }

        //向订单表插入一条数据。
        //排除 payMethod（基本类型 int，未传时为 0 属非法值）与 amount（必须由服务端重算），
        //避免 BeanUtils 盲拷把客户端伪造金额/非法支付方式带进来；其余字段显式给出默认值
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders, "payMethod", "amount", "packAmount", "tablewareNumber");
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(orderNumber);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orders.setUserId(userId);
        orders.setAmount(totalAmount);
        orders.setPayMethod(ordersSubmitDTO.getPayMethod() == 0 ? 1 : ordersSubmitDTO.getPayMethod());
        orders.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus() == null ? 1 : ordersSubmitDTO.getDeliveryStatus());
        orders.setTablewareStatus(ordersSubmitDTO.getTablewareStatus() == null ? 1 : ordersSubmitDTO.getTablewareStatus());
        orders.setTablewareNumber(ordersSubmitDTO.getTablewareNumber() == null ? 0 : ordersSubmitDTO.getTablewareNumber());
        orders.setPackAmount(ordersSubmitDTO.getPackAmount() == null ? 0 : ordersSubmitDTO.getPackAmount());

        orderMapper.insert(orders);

        //向订单明细表插入n条数据（明细金额同样以服务端价格为准）。
        //排除购物车主键 id/userId/createTime，避免把购物车的 id 拷进 order_detail.id 破坏自增主键
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart cart : list) {
            if (cart.getProductId() == null) {
                continue;
            }
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail, "id", "userId", "createTime");
            orderDetail.setOrderId(orders.getId());
            orderDetail.setAmount(lineAmountMap.get(cart.getProductId()));
            orderDetails.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetails);

        //清空用户的购物车的数据
        shoppingCartMapper.deletByUserId(userId);

        //封装返回结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 支付订单
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        Orders orderDB = orderMapper.getByOrderNumber(orderNumber);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //校验订单归属：仅允许支付本人订单，防止越权支付他人订单
        Long userId = BaseContext.getCurrentId();
        if (orderDB.getUserId() == null || !orderDB.getUserId().equals(userId)) {
            throw new OrderBusinessException("无权支付该订单");
        }

        //仅待付款订单可支付
        if (!Orders.PENDING_PAYMENT.equals(orderDB.getStatus())) {
            throw new OrderBusinessException("订单状态不正确，无法支付");
        }

        //支付策略切换：mallsen.pay.mock=true 走 MockPaymentService，false 走 WeChatPaymentService
        return paymentServiceRouter.getCurrent().createPayment(orderDB);
    }

    /**
     * 支付成功,修改订单状态
     * @param orderNumber
     */
    @Override
    @Transactional
    public void paySuccess(String orderNumber) {
        //根据订单号查订单
        Orders orderDB = orderMapper.getByOrderNumber(orderNumber);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //幂等处理：已支付则直接返回，避免重复回调导致重复扣库存/重复推送
        if (Orders.PAID.equals(orderDB.getPayStatus())) {
            log.info("订单已支付，忽略重复回调: orderNumber={}", orderNumber);
            return;
        }

        //支付流水状态由当前支付策略负责（mock 落库；真实微信支付由验签回调承载，见 PaymentService 实现说明）
        paymentServiceRouter.getCurrent().markPaid(orderNumber);

        //根据订单ID修改订单
        Orders orders = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.updateById(orders);

        //Outbox 模式：把异步消息与订单状态更新放进同一事务，避免「库已更新、MQ 未发」导致销量永不确认。
        //投递在事务提交后进行（afterCommit 即时投递一次 + MqOutboxRelayTask 每 5 秒定时兜底重试），
        //投递失败保留待发送状态，消费端已有 Redis 幂等，保证 at-least-once 且不会重复累加销量
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderDB.getId());
        OrderAsyncMessage msg = OrderAsyncMessage.builder()
                .orderId(orderDB.getId())
                .orderNumber(orderNumber)
                .userId(orderDB.getUserId())
                .orderDetailList(orderDetails)
                .build();
        mqOutboxMapper.insert(MqOutbox.builder()
                .orderId(orderDB.getId())
                .orderNumber(orderNumber)
                .payload(JSON.toJSONString(msg))
                .status(MqOutbox.PENDING)
                .retryCount(0)
                .createTime(LocalDateTime.now())
                .build());

        //事务提交后再做副作用（发 MQ、推送 WebSocket），与 Outbox「提交后副作用」原则保持一致：
        //若事务回滚，既不能发出销量确认消息，也不能向商家端推送一个其实并未生效的订单。
        //Outbox 即时投递 + 来单提醒推送合并进同一个 afterCommit 回调
        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("type", 1);//1表示来单提醒，2表示用户催单
        wsMsg.put("orderId", orders.getId());
        wsMsg.put("content", orderNumber);

        String wsJson = JSON.toJSONString(wsMsg);
        registerAfterCommit(() -> {
            try {
                mqOutboxRelayer.relayPending(100);
            } catch (Exception e) {
                log.error("支付成功后的 Outbox 即时投递失败，将由定时任务兜底重试，orderNumber={}", orderNumber, e);
            }
            try {
                webSocketServer.sendToAllClient(wsJson);
            } catch (Exception e) {
                log.error("支付成功后的 WebSocket 来单提醒发送失败，orderNumber={}", orderNumber, e);
            }
        });
    }

    /**
     * 分页查询订单
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery(int pageNum, int pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        //分页查询
        Page<Orders> page = orderMapper.page(ordersPageQueryDTO);

        //建立订单明细响应表
        List<OrderVO> orderVOS = new ArrayList<>();

        //批量查询订单明细（一次 IN 查询代替逐单查询，避免 N+1）
        if (page != null && page.getTotal() > 0) {
            List<Long> orderIds = page.getResult().stream()
                    .map(Orders::getId)
                    .collect(Collectors.toList());
            List<OrderDetail> orderDetails = orderDetailMapper.getByOrderIdsWithLatestImage(orderIds);
            Map<Long, List<OrderDetail>> detailMap = orderDetails.stream()
                    .collect(Collectors.groupingBy(OrderDetail::getOrderId));

            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(detailMap.getOrDefault(orders.getId(), Collections.emptyList()));
                orderVOS.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOS);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细（获取最新商品图片）
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderIdWithLatestImage(id);

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetails);

        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    @Override
    @Transactional
    public void userCancelById(Long id) {
        //根据订单ID查询订单
        Orders orderDB = orderMapper.getById(id);

        //检查订单是否存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //检查订单状态是否正确
        if (orderDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //下单时已预占库存，取消时统一归还（已支付订单同时回滚销量）
        stockReleaseService.release(orderDB);

        Orders orders = new Orders();
        orders.setId(orderDB.getId());

        //待接单且已支付状态下取消，需要给用户退款并落库（pay_record 标记为已退款）
        if (orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)
                && Orders.PAID.equals(orderDB.getPayStatus())) {
            orders.setPayStatus(Orders.REFUND);
            paymentServiceRouter.getCurrent().refund(orderDB.getNumber());
        }

        //更新订单状态
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(orders);


    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        //获取当前用户ID
        Long userId = BaseContext.getCurrentId();

        //获取当前订单明细信息（获取最新商品图片）
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderIdWithLatestImage(id);

        //将订单明细信息转换为购物车对象
        List<ShoppingCart> shoppingCarts = orderDetails.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            //将订单明细详细的的菜品信息拷贝到购物车对象
            BeanUtils.copyProperties(x,shoppingCart,"id");
            //设置用户ID和下单时间
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        //将购物车对象添加到购物车表
        shoppingCartMapper.insertBatch(shoppingCarts);
    }

    /**
     * 搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.page(ordersPageQueryDTO);

        //部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.updateById(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    @Transactional
    public void reject(OrdersRejectionDTO ordersRejectionDTO) {
        //查询订单信息
        Orders orderDB = orderMapper.getById(ordersRejectionDTO.getId());

        //判断订单是否可以取消
        if (orderDB == null || !orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //下单时已预占库存，拒单时统一归还（已支付订单同时回滚销量）
        stockReleaseService.release(orderDB);

        //更新数据
        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());

        //退款（已支付订单：置 pay_status=REFUND 并标记支付流水为已退款）
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            orders.setPayStatus(Orders.REFUND);
            paymentServiceRouter.getCurrent().refund(orderDB.getNumber());
        }

        orderMapper.updateById(orders);

    }

    @Override
    @Transactional
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //已取消订单不允许重复取消，避免重复归还库存
        if (Orders.CANCELLED.equals(orderDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //下单时已预占库存，取消时统一归还（已支付订单同时回滚销量）
        stockReleaseService.release(orderDB);

        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        //已支付订单：置 pay_status=REFUND 并标记支付流水为已退款
        if (orderDB.getPayStatus().equals(Orders.PAID)) {
            orders.setPayStatus(Orders.REFUND);
            paymentServiceRouter.getCurrent().refund(orderDB.getNumber());
        }

        orderMapper.updateById(orders);
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders orderDB = orderMapper.getById(id);

        //判罚是否可以派送
        if (orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //更新订单状态
        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.updateById(orders);
    }

    /**
     *  完成订单
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders orderDB = orderMapper.getById(id);

        //判断是否可以完成订单
        if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //更新订单状态
        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.updateById(orders);

    }

    /**
     * 客户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders orderDB = orderMapper.getById(id);

        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map map = new HashMap();
        map.put("type",2);//1表示来单提醒2表示用户催单
        map.put("orderId",id);
        map.put("content","订单号:" + orderDB.getNumber());

        String json = JSON.toJSONString(map);

        webSocketServer.sendToAllClient(json);
    }

    /**
     * 修改订单收货地址（供AI使用）
     * @param orderId
     * @param address
     * @param consignee
     * @param phone
     */
    @Override
    @Transactional
    public void updateAddress(Long orderId, String address, String consignee, String phone) {
        Orders orderDB = orderMapper.getById(orderId);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orderDB.getStatus() > Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException("订单已接单，无法修改地址");
        }

        orderDB.setAddress(address);
        if (consignee != null && !consignee.isEmpty()) {
            orderDB.setConsignee(consignee);
        }
        if (phone != null && !phone.isEmpty()) {
            orderDB.setPhone(phone);
        }
        orderMapper.updateById(orderDB);
    }

    /**
     * 退款申请（供AI使用）
     * @param orderId
     * @param reason
     */
    @Override
    @Transactional
    public void applyRefund(Long orderId, String reason) {
        Orders orderDB = orderMapper.getById(orderId);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orderDB.getStatus() != Orders.TO_BE_CONFIRMED && orderDB.getStatus() != Orders.CONFIRMED) {
            throw new OrderBusinessException("当前订单状态不支持退款");
        }

        //下单时已预占库存，退款时统一归还（已支付订单同时回滚销量）
        stockReleaseService.release(orderDB);

        //已支付的订单退款：标记支付流水为已退款
        if (Orders.PAID.equals(orderDB.getPayStatus())) {
            paymentServiceRouter.getCurrent().refund(orderDB.getNumber());
        }

        orderDB.setStatus(Orders.CANCELLED);
        orderDB.setPayStatus(Orders.REFUND);
        orderDB.setCancelReason(reason);
        orderDB.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(orderDB);
    }

    /**
     * AI代客下单
     * @param dto
     * @return
     */
    @Override
    public Orders createOrderForAi(AiOrderDTO dto) {
        if (dto.getUserId() == null) {
            throw new OrderBusinessException("用户ID不能为空");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new OrderBusinessException("商品列表不能为空");
        }
        if (dto.getAddressBookId() == null) {
            throw new OrderBusinessException("地址ID不能为空");
        }
        if (dto.getAddressDetail() == null || dto.getConsignee() == null || dto.getPhone() == null) {
            throw new OrderBusinessException("收货地址、联系人、电话不能为空");
        }

        Long userId = dto.getUserId();
        String lockKey = "order:submit:lock:" + userId;
        String lockToken = tryAcquireLock(lockKey);
        if (lockToken == null) {
            throw new OrderBusinessException("订单提交过于频繁，请稍后重试");
        }
        try {
            //订单号唯一索引冲突时整体回滚并重新生成订单号重试（最多 MAX_ORDER_NUMBER_RETRY 次）
            for (int attempt = 1; attempt <= MAX_ORDER_NUMBER_RETRY; attempt++) {
                try {
                    return transactionTemplate.execute(status -> doCreateOrderForAi(dto, userId));
                } catch (DuplicateKeyException e) {
                    if (attempt == MAX_ORDER_NUMBER_RETRY) {
                        log.error("AI代客下单订单号唯一索引连续冲突，放弃重试，userId={}", userId, e);
                        throw e;
                    }
                    log.warn("AI代客下单订单号唯一索引冲突，第 {}/{} 次重新生成订单号", attempt, MAX_ORDER_NUMBER_RETRY);
                }
            }
            throw new OrderBusinessException("AI代客下单失败，请稍后重试");
        } finally {
            //事务提交后再释放锁，避免事务尚未提交时锁已被释放
            releaseLockAfterCommit(lockKey, lockToken);
        }
    }

    /**
     * 事务内 AI 代客下单（预占库存 + 落订单 + 落明细）。订单号冲突时整体回滚，由外层重试。
     */
    private Orders doCreateOrderForAi(AiOrderDTO dto, Long userId) {
        String orderNumber = generateOrderNumber();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderDetail> orderDetails = new ArrayList<>();

        for (AiOrderDTO.AiOrderItemDTO item : dto.getItems()) {
            Long productId = item.getProductId();
            Integer quantity = item.getQuantity();

            Product product = productMapper.getById(productId);
            if (product == null) {
                throw new OrderBusinessException("商品 " + productId + " 不存在");
            }
            if (product.getStatus() != StatusConstant.ENABLE) {
                throw new OrderBusinessException("商品 " + product.getName() + " 已下架");
            }
            //预占库存：条件更新原子扣减，防止超卖（含库存流水与缓存清理）
            preDeductStockAndRecord(product, quantity, userId, orderNumber, "AI代客下单预占库存：" + orderNumber);

            BigDecimal lineAmount = roundMoney(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            totalAmount = totalAmount.add(lineAmount);

            OrderDetail detail = new OrderDetail();
            detail.setName(product.getName());
            detail.setProductId(productId);
            detail.setNumber(quantity);
            detail.setAmount(lineAmount);
            detail.setImage(product.getImage());
            orderDetails.add(detail);
        }

        Orders order = new Orders();
        order.setNumber(orderNumber);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setUserId(userId);
        order.setOrderTime(LocalDateTime.now());
        order.setPayStatus(Orders.UN_PAID);
        order.setAmount(totalAmount);
        order.setRemark(dto.getRemark());
        order.setAddressBookId(dto.getAddressBookId());
        order.setPhone(dto.getPhone());
        order.setConsignee(dto.getConsignee());
        order.setAddress(dto.getAddressDetail());
        order.setPayMethod(1);
        order.setDeliveryStatus(1);
        order.setPackAmount(0);
        order.setTablewareNumber(0);
        order.setTablewareStatus(1);

        orderMapper.insert(order);

        for (OrderDetail detail : orderDetails) {
            detail.setOrderId(order.getId());
        }
        orderDetailMapper.insertBatch(orderDetails);

        return order;
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {        // 需要返回订单菜品信息，OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (CollectionUtils.isEmpty(ordersList)) {
            return orderVOList;
        }

        //批量查询订单明细（一次 IN 查询代替逐单查询，避免 N+1）
        List<Long> orderIds = ordersList.stream()
                .map(Orders::getId)
                .collect(Collectors.toList());
        List<OrderDetail> allDetails = orderDetailMapper.getByOrderIds(orderIds);
        Map<Long, List<OrderDetail>> detailMap = allDetails.stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));

        for (Orders orders : ordersList) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);

            List<OrderDetail> orderDetails = detailMap.getOrDefault(orders.getId(), Collections.emptyList());
            orderVO.setOrderProducts(buildOrderProductsStr(orderDetails));
            orderVOList.add(orderVO);
        }
        return orderVOList;
    }

    /**
     * 预占库存并记录出库流水、清理缓存。
     * 供普通下单（ShoppingCart）与 AI 代客下单共用，避免重复编码、保持库存扣减策略一致。
     *
     * @param product     商品（须非空且已入库）
     * @param quantity    本次预占数量
     * @param userId      操作人（下单用户）
     * @param orderNumber 关联订单号
     * @param remark      库存流水备注
     */
    private void preDeductStockAndRecord(Product product, Integer quantity, Long userId, String orderNumber, String remark) {
        int affected = productMapper.deductStock(product.getId(), quantity);
        if (affected == 0) {
            throw new OrderBusinessException("商品 " + product.getName() + " 库存不足");
        }
        //重新读取扣减后的实际库存作为流水快照，并发下不再依赖扣减前的旧值，保证账实相符
        Product fresh = productMapper.getById(product.getId());
        int stockAfter = fresh != null ? fresh.getStock() : product.getStock() - quantity;
        int stockBefore = stockAfter + quantity;
        InventoryRecord record = InventoryRecord.builder()
                .productId(product.getId())
                .type(InventoryConstant.TYPE_OUTBOUND)
                .quantity(-quantity)
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .remark(remark)
                .createTime(LocalDateTime.now())
                .createUser(userId)
                .build();
        inventoryMapper.insertRecord(record);
        //缓存失效遵循 Cache Aside：写库提交后再删缓存（afterCommit）+ 延迟双删兜底脏读窗口。
        //库存扣减与订单落单同一事务，若在事务内删除缓存，一旦回滚会出现「库未变、缓存已删」，
        //故交由 ProductCacheEvictor 统一在提交后失效分类缓存
        productCacheEvictor.evictCategoryAfterCommit(product.getCategoryId());
    }

    /**
     * 根据订单明细列表拼接商品信息字符串
     *
     * @param orderDetailList
     * @return
     */
    private String buildOrderProductsStr(List<OrderDetail> orderDetailList) {
        return orderDetailList.stream()
                .map(x -> x.getName() + "*" + x.getNumber() + ";")
                .collect(Collectors.joining());
    }

    /**
     * 生成唯一订单号：时间戳(到毫秒) + 6位随机数 + 进程内自增序号。
     * 多实例并发下仍可能撞号，因此 DB 层 orders.number 建唯一索引作为最终兜底，
     * 冲突时由 submitOrder / createOrderForAi 整体回滚并重新生成重试。
     * @return 订单号
     */
    private String generateOrderNumber() {
        int seq = orderNumberSeq.incrementAndGet() % 1000;
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
                + String.format("%03d", seq);
    }

    /**
     * 金额统一保留两位小数，避免出现 49.995*2=99.990 这类精度不一致问题
     * @param value 未舍入金额
     * @return 两位小数金额
     */
    private BigDecimal roundMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取分布式锁（Redis SETNX + 随机 token）。
     * 返回锁 token 用于释放时校验持有者；返回 null 表示未获取到锁；
     * 返回 LOCK_DEGRADED 表示 Redis 不可用时降级放行（不参与锁校验）。
     * @param key 锁key
     * @return 锁 token，或 null
     */
    private String tryAcquireLock(String key) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, Duration.ofSeconds(LOCK_TTL_SECONDS));
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (Exception e) {
            log.error("获取分布式锁失败，key: {}", key, e);
            //锁不可用时降级放行，避免依赖故障阻塞下单
            return LOCK_DEGRADED;
        }
    }

    /**
     * 释放分布式锁：仅当 key 的值仍为本次持有者的 token 时才原子删除（Lua 校验）。
     * 避免锁过期被他人重新获取后，本请求结束时误删他人的锁。
     * @param key 锁key
     * @param token 获取锁时返回的 token
     */
    private void releaseLock(String key, String token) {
        if (token == null || LOCK_DEGRADED.equals(token)) {
            return;
        }
        try {
            //原子地校验并删除：value 等于 token 才允许删除，防止误删他人锁
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Collections.singletonList(key), token);
        } catch (Exception e) {
            log.error("释放分布式锁失败，key: {}", key, e);
        }
    }

    /**
     * 事务提交后再释放锁，避免锁释放早于事务提交造成的临界区窗口。
     * 若当前无事务（如单元测试/降级路径），则立即释放。
     */
    private void releaseLockAfterCommit(String key, String token) {
        registerAfterCommit(() -> releaseLock(key, token));
    }

    /**
     * 在事务提交后执行动作；若无活动事务则立即执行。
     * 用于「先落库、提交成功后再做副作用」（如发 MQ）的场景，避免事务回滚却已产生副作用。
     */
    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

}
