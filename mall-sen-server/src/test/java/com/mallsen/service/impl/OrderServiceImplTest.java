package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.mallsen.constant.MessageConstant;
import com.mallsen.component.MqOutboxRelayer;
import com.mallsen.component.ProductCacheEvictor;
import com.mallsen.context.BaseContext;
import com.mallsen.dto.*;
import com.mallsen.entity.*;
import com.mallsen.exception.AddressBookBusinessException;
import com.mallsen.exception.OrderBusinessException;
import com.mallsen.mapper.*;
import com.mallsen.result.PageResult;
import com.mallsen.service.PaymentService;
import com.mallsen.utils.HttpClientUtil;
import com.mallsen.vo.OrderPaymentVO;
import com.mallsen.vo.OrderStatisticsVO;
import com.mallsen.vo.OrderSubmitVO;
import com.mallsen.vo.OrderVO;
import com.mallsen.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @Mock
    private AddressBookMapper addressBookMapper;

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private WebSocketServer webSocketServer;

    @Mock
    private PaymentServiceRouter paymentServiceRouter;

    @Mock
    private PaymentService paymentService;

    @Mock
    private MqOutboxMapper mqOutboxMapper;

    @Mock
    private MqOutboxRelayer mqOutboxRelayer;

    @Mock
    private ProductCacheEvictor productCacheEvictor;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private StockReleaseService stockReleaseService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private OrdersSubmitDTO ordersSubmitDTO;
    private AddressBook addressBook;
    private ShoppingCart shoppingCart;
    private Product product;
    private Orders order;
    private OrderDetail orderDetail;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);

        ordersSubmitDTO = new OrdersSubmitDTO();
        ordersSubmitDTO.setAddressBookId(10L);
        ordersSubmitDTO.setPayMethod(1);
        ordersSubmitDTO.setRemark("尽快送达");
        ordersSubmitDTO.setAmount(new BigDecimal("99.99"));
        ordersSubmitDTO.setPackAmount(0);
        ordersSubmitDTO.setTablewareNumber(1);
        ordersSubmitDTO.setTablewareStatus(1);
        ordersSubmitDTO.setDeliveryStatus(1);

        addressBook = AddressBook.builder()
                .id(10L)
                .userId(1L)
                .consignee("张三")
                .phone("13800138000")
                .cityName("北京市")
                .districtName("朝阳区")
                .detail("某街道100号")
                .build();

        shoppingCart = ShoppingCart.builder()
                .id(1L)
                .userId(1L)
                .productId(100L)
                .name("测试商品")
                .image("test.jpg")
                .number(2)
                .amount(new BigDecimal("99.99"))
                .productSpec("默认")
                .build();

        product = Product.builder()
                .id(100L)
                .name("测试商品")
                .stock(50)
                .categoryId(1L)
                .price(new BigDecimal("49.995"))
                .status(1)
                .build();

        order = Orders.builder()
                .id(1L)
                .number("20240101000000001")
                .userId(1L)
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("99.99"))
                .consignee("张三")
                .phone("13800138000")
                .orderTime(LocalDateTime.now())
                .build();

        orderDetail = OrderDetail.builder()
                .id(1L)
                .orderId(1L)
                .productId(100L)
                .name("测试商品")
                .number(2)
                .amount(new BigDecimal("99.99"))
                .build();

        //下单/AI下单走 TransactionTemplate：让 mock 直接执行回调里的业务逻辑（本测试无真实事务，释放锁与 Outbox 投递的 afterCommit 都会立即执行）
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactionTemplate).execute(any(TransactionCallback.class));

        //支付策略路由：默认让 getCurrent() 返回 paymentService mock
        lenient().when(paymentServiceRouter.getCurrent()).thenReturn(paymentService);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    // ---- submitOrder ----

    @Test
    void submitOrder_shouldThrowWhenAddressIsNull() {
        when(addressBookMapper.getById(10L)).thenReturn(null);

        AddressBookBusinessException ex = assertThrows(AddressBookBusinessException.class,
                () -> orderService.submitOrder(ordersSubmitDTO));
        assertEquals(MessageConstant.ADDRESS_BOOK_IS_NULL, ex.getMessage());
    }

    @Test
    void submitOrder_shouldThrowWhenCartIsEmpty() {
        when(addressBookMapper.getById(10L)).thenReturn(addressBook);

        try (MockedStatic<HttpClientUtil> httpMock = mockStatic(HttpClientUtil.class)) {
            httpMock.when(() -> HttpClientUtil.doGet(contains("geocoding"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"location\":{\"lat\":\"39.9\",\"lng\":\"116.4\"}}}");
            httpMock.when(() -> HttpClientUtil.doGet(contains("directionlite"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"routes\":[{\"distance\":1000}]}}");

            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of());

            AddressBookBusinessException ex = assertThrows(AddressBookBusinessException.class,
                    () -> orderService.submitOrder(ordersSubmitDTO));
            assertEquals(MessageConstant.SHOPPING_CART_IS_NULL, ex.getMessage());
        }
    }

    @Test
    void submitOrder_shouldThrowWhenProductNotFound() {
        when(addressBookMapper.getById(10L)).thenReturn(addressBook);

        try (MockedStatic<HttpClientUtil> httpMock = mockStatic(HttpClientUtil.class)) {
            httpMock.when(() -> HttpClientUtil.doGet(contains("geocoding"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"location\":{\"lat\":\"39.9\",\"lng\":\"116.4\"}}}");
            httpMock.when(() -> HttpClientUtil.doGet(contains("directionlite"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"routes\":[{\"distance\":1000}]}}");

            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(shoppingCart));
            when(productMapper.getById(100L)).thenReturn(null);

            OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                    () -> orderService.submitOrder(ordersSubmitDTO));
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    @Test
    void submitOrder_shouldThrowWhenStockInsufficient() {
        product.setStock(1);
        when(addressBookMapper.getById(10L)).thenReturn(addressBook);

        try (MockedStatic<HttpClientUtil> httpMock = mockStatic(HttpClientUtil.class)) {
            httpMock.when(() -> HttpClientUtil.doGet(contains("geocoding"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"location\":{\"lat\":\"39.9\",\"lng\":\"116.4\"}}}");
            httpMock.when(() -> HttpClientUtil.doGet(contains("directionlite"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"routes\":[{\"distance\":1000}]}}");

            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(shoppingCart));
            when(productMapper.getById(100L)).thenReturn(product);

            OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                    () -> orderService.submitOrder(ordersSubmitDTO));
            assertTrue(ex.getMessage().contains("库存不足"));
        }
    }

    @Test
    void submitOrder_shouldSucceed() {
        when(addressBookMapper.getById(10L)).thenReturn(addressBook);

        try (MockedStatic<HttpClientUtil> httpMock = mockStatic(HttpClientUtil.class)) {
            httpMock.when(() -> HttpClientUtil.doGet(contains("geocoding"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"location\":{\"lat\":\"39.9\",\"lng\":\"116.4\"}}}");
            httpMock.when(() -> HttpClientUtil.doGet(contains("directionlite"), anyMap()))
                    .thenReturn("{\"status\":\"0\",\"result\":{\"routes\":[{\"distance\":1000}]}}");

            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(shoppingCart));
            when(productMapper.getById(100L)).thenReturn(product);
            when(productMapper.deductStock(100L, 2)).thenReturn(1);
            doAnswer(invocation -> {
                Orders o = invocation.getArgument(0);
                o.setId(1L);
                return null;
            }).when(orderMapper).insert(any(Orders.class));

            OrderSubmitVO result = orderService.submitOrder(ordersSubmitDTO);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(new BigDecimal("99.99"), result.getOrderAmount());
            verify(orderMapper).insert(any(Orders.class));
            verify(orderDetailMapper).insertBatch(anyList());
            verify(shoppingCartMapper).deletByUserId(1L);
        }
    }

    // ---- payment ----

    @Test
    void payment_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getByOrderNumber("not-exist")).thenReturn(null);

        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("not-exist");

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.payment(dto));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, ex.getMessage());
    }

    @Test
    void payment_shouldThrowWhenNotOwnOrder() {
        order.setUserId(999L);
        when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);

        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("20240101000000001");

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.payment(dto));
        assertTrue(ex.getMessage().contains("无权支付"));
    }

    @Test
    void payment_shouldThrowWhenStatusNotPending() {
        order.setStatus(Orders.TO_BE_CONFIRMED);
        when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);

        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("20240101000000001");

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.payment(dto));
        assertTrue(ex.getMessage().contains("订单状态不正确"));
    }

    @Test
    void payment_shouldCreatePaymentViaCurrentStrategy() {
        order.setStatus(Orders.PENDING_PAYMENT);
        when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);
        OrderPaymentVO expected = OrderPaymentVO.builder().packageStr("prepay_id=test").build();
        when(paymentServiceRouter.getCurrent().createPayment(order)).thenReturn(expected);

        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("20240101000000001");

        OrderPaymentVO result = orderService.payment(dto);

        assertSame(expected, result);
        verify(paymentService).createPayment(order);
    }

    // ---- paySuccess ----

    @Test
    void paySuccess_shouldUpdateOrderAndWriteOutbox() {
        when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(List.of(orderDetail));

        orderService.paySuccess("20240101000000001");

        verify(orderMapper).updateById(argThat(o ->
                o.getStatus().equals(Orders.TO_BE_CONFIRMED) &&
                o.getPayStatus().equals(Orders.PAID)
        ));
        verify(paymentService).markPaid("20240101000000001");
        verify(mqOutboxMapper).insert(any(MqOutbox.class));
        //无活动事务时 afterCommit 立即执行，触发 Outbox 即时投递
        verify(mqOutboxRelayer).relayPending(100);
        verify(webSocketServer).sendToAllClient(anyString());
    }

    @Test
    void paySuccess_marksPaidThroughCurrentPaymentStrategy() {
        when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(List.of(orderDetail));

        orderService.paySuccess("20240101000000001");

        verify(paymentService).markPaid("20240101000000001");
        verify(orderMapper).updateById(argThat(o -> o.getPayStatus().equals(Orders.PAID)));
    }

    @Test
    void paySuccess_shouldNotDoSideEffectsBeforeCommit() {
        //模拟存在活动事务：Outbox 投递与 WebSocket 推送都必须推迟到 afterCommit，
        //否则事务回滚后仍会发出消息/推送一个并未生效的订单
        TransactionSynchronizationManager.initSynchronization();
        try {
            when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);
            when(orderDetailMapper.getByOrderId(1L)).thenReturn(List.of(orderDetail));

            orderService.paySuccess("20240101000000001");

            verify(mqOutboxRelayer, never()).relayPending(anyInt());
            verify(webSocketServer, never()).sendToAllClient(anyString());

            //提交后：Outbox 即时投递 + 来单提醒推送
            TransactionSynchronizationUtils.triggerAfterCommit();

            verify(mqOutboxRelayer).relayPending(100);
            verify(webSocketServer).sendToAllClient(argThat(json ->
                    json.contains("\"type\":1") &&
                    json.contains("\"orderId\":1") &&
                    json.contains("\"content\":\"20240101000000001\"")
            ));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void paySuccess_shouldIgnoreDuplicateCallback() {
        order.setPayStatus(Orders.PAID);
        when(orderMapper.getByOrderNumber("20240101000000001")).thenReturn(order);

        orderService.paySuccess("20240101000000001");

        verify(orderMapper, never()).updateById(any(Orders.class));
        verify(paymentService, never()).markPaid(anyString());
        verify(webSocketServer, never()).sendToAllClient(anyString());
    }

    // ---- pageQuery ----

    @SuppressWarnings("unchecked")
    @Test
    void pageQuery_shouldReturnPageResult() {
        Page<Orders> page = new Page<>(1, 10);
        page.add(order);
        page.setTotal(1L);
        when(orderMapper.page(any(OrdersPageQueryDTO.class))).thenReturn(page);
        when(orderDetailMapper.getByOrderIds(List.of(1L))).thenReturn(List.of(orderDetail));

        PageResult result = orderService.pageQuery(1, 10, null);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        OrderVO vo = (OrderVO) result.getRecords().get(0);
        assertEquals(1, vo.getOrderDetailList().size());
    }

    // ---- details ----

    @Test
    void details_shouldReturnOrderVO() {
        when(orderMapper.getById(1L)).thenReturn(order);
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(List.of(orderDetail));

        OrderVO result = orderService.details(1L);

        assertNotNull(result);
        assertEquals("20240101000000001", result.getNumber());
        assertEquals(1, result.getOrderDetailList().size());
    }

    // ---- userCancelById ----

    @Test
    void userCancelById_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getById(1L)).thenReturn(null);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.userCancelById(1L));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, ex.getMessage());
    }

    @Test
    void userCancelById_shouldThrowWhenStatusInvalid() {
        order.setStatus(Orders.COMPLETED);
        when(orderMapper.getById(1L)).thenReturn(order);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.userCancelById(1L));
        assertEquals(MessageConstant.ORDER_STATUS_ERROR, ex.getMessage());
    }

    @Test
    void userCancelById_shouldCancelUnpaidOrder() {
        order.setPayStatus(Orders.UN_PAID);
        order.setStatus(Orders.PENDING_PAYMENT);
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.userCancelById(1L);

        verify(stockReleaseService).release(order);
        verify(orderMapper).updateById(argThat(o ->
                o.getStatus().equals(Orders.CANCELLED)
        ));
    }

    @Test
    void userCancelById_shouldReleaseStockThroughServiceWhenPaid() {
        order.setPayStatus(Orders.PAID);
        order.setStatus(Orders.TO_BE_CONFIRMED);
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.userCancelById(1L);

        verify(stockReleaseService).release(argThat(o -> o.getId().equals(1L)));
        verify(paymentService).refund(order.getNumber());
        verify(orderMapper).updateById(argThat(o -> o.getStatus().equals(Orders.CANCELLED)));
    }

    // ---- confirm ----

    @Test
    void confirm_shouldUpdateStatus() {
        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(1L);

        orderService.confirm(confirmDTO);

        verify(orderMapper).updateById(argThat(o ->
                o.getId().equals(1L) &&
                o.getStatus().equals(Orders.CONFIRMED)
        ));
    }

    // ---- reject ----

    @Test
    void reject_shouldThrowWhenOrderNotFound() {
        OrdersRejectionDTO rejectDTO = new OrdersRejectionDTO();
        rejectDTO.setId(1L);
        rejectDTO.setRejectionReason("库存不足");

        when(orderMapper.getById(1L)).thenReturn(null);

        assertThrows(OrderBusinessException.class,
                () -> orderService.reject(rejectDTO));
    }

    @Test
    void reject_shouldThrowWhenStatusNotToBeConfirmed() {
        OrdersRejectionDTO rejectDTO = new OrdersRejectionDTO();
        rejectDTO.setId(1L);
        order.setStatus(Orders.CONFIRMED);

        when(orderMapper.getById(1L)).thenReturn(order);

        assertThrows(OrderBusinessException.class,
                () -> orderService.reject(rejectDTO));
    }

    @Test
    void reject_shouldSucceed() {
        OrdersRejectionDTO rejectDTO = new OrdersRejectionDTO();
        rejectDTO.setId(1L);
        rejectDTO.setRejectionReason("库存不足");

        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.reject(rejectDTO);

        verify(orderMapper).updateById(argThat(o ->
                o.getStatus().equals(Orders.CANCELLED) &&
                o.getRejectionReason().equals("库存不足")
        ));
    }

    // ---- cancel ----

    @Test
    void cancel_shouldCancelOrder() {
        OrdersCancelDTO cancelDTO = new OrdersCancelDTO();
        cancelDTO.setId(1L);
        cancelDTO.setCancelReason("商家取消");

        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.cancel(cancelDTO);

        verify(orderMapper).updateById(argThat(o ->
                o.getStatus().equals(Orders.CANCELLED) &&
                o.getCancelReason().equals("商家取消")
        ));
    }

    // ---- delivery ----

    @Test
    void delivery_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getById(1L)).thenReturn(null);

        assertThrows(OrderBusinessException.class,
                () -> orderService.delivery(1L));
    }

    @Test
    void delivery_shouldThrowWhenStatusNotConfirmed() {
        order.setStatus(Orders.TO_BE_CONFIRMED);
        when(orderMapper.getById(1L)).thenReturn(order);

        assertThrows(OrderBusinessException.class,
                () -> orderService.delivery(1L));
    }

    @Test
    void delivery_shouldSucceed() {
        order.setStatus(Orders.CONFIRMED);
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.delivery(1L);

        verify(orderMapper).updateById(argThat(o ->
                o.getId().equals(1L) &&
                o.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)
        ));
    }

    // ---- complete ----

    @Test
    void complete_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getById(1L)).thenReturn(null);

        assertThrows(OrderBusinessException.class,
                () -> orderService.complete(1L));
    }

    @Test
    void complete_shouldThrowWhenNotInDelivery() {
        order.setStatus(Orders.TO_BE_CONFIRMED);
        when(orderMapper.getById(1L)).thenReturn(order);

        assertThrows(OrderBusinessException.class,
                () -> orderService.complete(1L));
    }

    @Test
    void complete_shouldSucceed() {
        order.setStatus(Orders.DELIVERY_IN_PROGRESS);
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.complete(1L);

        verify(orderMapper).updateById(argThat(o ->
                o.getId().equals(1L) &&
                o.getStatus().equals(Orders.COMPLETED)
        ));
    }

    // ---- statistics ----

    @Test
    void statistics_shouldReturnCounts() {
        when(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED)).thenReturn(5);
        when(orderMapper.countByStatus(Orders.CONFIRMED)).thenReturn(3);
        when(orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS)).thenReturn(8);

        OrderStatisticsVO result = orderService.statistics();

        assertEquals(5, result.getToBeConfirmed());
        assertEquals(3, result.getConfirmed());
        assertEquals(8, result.getDeliveryInProgress());
    }

    // ---- repetition ----

    @Test
    void repetition_shouldCopyOrderDetailsToCart() {
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(List.of(orderDetail));

        orderService.repetition(1L);

        verify(shoppingCartMapper).insertBatch(argThat(carts -> {
            ShoppingCart cart = carts.get(0);
            return cart.getUserId().equals(1L) &&
                    cart.getProductId().equals(100L) &&
                    cart.getName().equals("测试商品") &&
                    cart.getNumber() == 2;
        }));
    }

    // ---- reminder ----

    @Test
    void reminder_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getById(1L)).thenReturn(null);

        assertThrows(OrderBusinessException.class,
                () -> orderService.reminder(1L));
    }

    @Test
    void reminder_shouldSendWebSocketMessage() {
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.reminder(1L);

        verify(webSocketServer).sendToAllClient(argThat(json ->
                json.contains("orderId") && json.contains("type") && json.contains("content")
        ));
    }

    // ---- conditionSearch ----

    @SuppressWarnings("unchecked")
    @Test
    void conditionSearch_shouldReturnResult() {
        OrdersPageQueryDTO queryDTO = new OrdersPageQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setPageSize(10);

        Page<Orders> page = new Page<>(1, 10);
        page.add(order);
        page.setTotal(1L);
        when(orderMapper.page(queryDTO)).thenReturn(page);
        when(orderDetailMapper.getByOrderIds(List.of(1L))).thenReturn(List.of(orderDetail));

        PageResult result = orderService.conditionSearch(queryDTO);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        OrderVO vo = (OrderVO) result.getRecords().get(0);
        assertTrue(vo.getOrderProducts().contains("测试商品*2"));
    }

    // ---- updateAddress ----

    @Test
    void updateAddress_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getById(1L)).thenReturn(null);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.updateAddress(1L, "新地址", "", ""));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, ex.getMessage());
    }

    @Test
    void updateAddress_shouldThrowWhenOrderConfirmed() {
        order.setStatus(Orders.CONFIRMED);
        when(orderMapper.getById(1L)).thenReturn(order);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.updateAddress(1L, "新地址", "", ""));
        assertTrue(ex.getMessage().contains("无法修改地址"));
    }

    @Test
    void updateAddress_shouldUpdateAddress() {
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.updateAddress(1L, "新地址", "李四", "13900139000");

        verify(orderMapper).updateById(argThat(o ->
                o.getAddress().equals("新地址") &&
                o.getConsignee().equals("李四") &&
                o.getPhone().equals("13900139000")
        ));
    }

    @Test
    void updateAddress_shouldKeepContactWhenBlank() {
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.updateAddress(1L, "新地址", "", "");

        verify(orderMapper).updateById(argThat(o ->
                o.getAddress().equals("新地址") &&
                o.getConsignee().equals(order.getConsignee()) &&
                o.getPhone().equals(order.getPhone())
        ));
    }

    // ---- applyRefund ----

    @Test
    void applyRefund_shouldThrowWhenOrderNotFound() {
        when(orderMapper.getById(1L)).thenReturn(null);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.applyRefund(1L, "不想要了"));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, ex.getMessage());
    }

    @Test
    void applyRefund_shouldThrowWhenStatusNotSupported() {
        order.setStatus(Orders.DELIVERY_IN_PROGRESS);
        when(orderMapper.getById(1L)).thenReturn(order);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.applyRefund(1L, "不想要了"));
        assertTrue(ex.getMessage().contains("不支持退款"));
    }

    @Test
    void applyRefund_shouldSucceed() {
        order.setStatus(Orders.TO_BE_CONFIRMED);
        order.setPayStatus(Orders.PAID);
        when(orderMapper.getById(1L)).thenReturn(order);

        orderService.applyRefund(1L, "不想要了");

        verify(paymentService).refund(order.getNumber());
        verify(orderMapper).updateById(argThat(o ->
                o.getStatus().equals(Orders.CANCELLED) &&
                o.getPayStatus().equals(Orders.REFUND) &&
                o.getCancelReason().equals("不想要了")
        ));
    }

    // ---- createOrderForAi ----

    @Test
    void createOrderForAi_shouldThrowWhenUserIdMissing() {
        AiOrderDTO dto = new AiOrderDTO();
        dto.setItems(List.of());

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.createOrderForAi(dto));
        assertTrue(ex.getMessage().contains("用户ID不能为空"));
    }

    @Test
    void createOrderForAi_shouldThrowWhenItemsEmpty() {
        AiOrderDTO dto = new AiOrderDTO();
        dto.setUserId(1L);
        dto.setItems(List.of());

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.createOrderForAi(dto));
        assertTrue(ex.getMessage().contains("商品列表不能为空"));
    }

    @Test
    void createOrderForAi_shouldThrowWhenAddressInfoMissing() {
        AiOrderDTO dto = new AiOrderDTO();
        dto.setUserId(1L);
        AiOrderDTO.AiOrderItemDTO item = new AiOrderDTO.AiOrderItemDTO();
        item.setProductId(100L);
        item.setQuantity(1);
        dto.setItems(List.of(item));

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.createOrderForAi(dto));
        assertTrue(ex.getMessage().contains("收货地址、联系人、电话不能为空"));
    }

    @Test
    void createOrderForAi_shouldThrowWhenProductNotFound() {
        AiOrderDTO dto = buildValidAiOrderDTO();
        when(productMapper.getById(100L)).thenReturn(null);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.createOrderForAi(dto));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void createOrderForAi_shouldThrowWhenProductOffShelf() {
        AiOrderDTO dto = buildValidAiOrderDTO();
        product.setStatus(0);
        when(productMapper.getById(100L)).thenReturn(product);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.createOrderForAi(dto));
        assertTrue(ex.getMessage().contains("已下架"));
    }

    @Test
    void createOrderForAi_shouldThrowWhenStockInsufficient() {
        AiOrderDTO dto = buildValidAiOrderDTO();
        product.setStock(1);
        when(productMapper.getById(100L)).thenReturn(product);

        OrderBusinessException ex = assertThrows(OrderBusinessException.class,
                () -> orderService.createOrderForAi(dto));
        assertTrue(ex.getMessage().contains("库存不足"));
    }

    @Test
    void createOrderForAi_shouldSucceed() {
        AiOrderDTO dto = buildValidAiOrderDTO();
        when(productMapper.getById(100L)).thenReturn(product);
        when(productMapper.deductStock(100L, 2)).thenReturn(1);
        doAnswer(invocation -> {
            Orders o = invocation.getArgument(0);
            o.setId(5L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        Orders result = orderService.createOrderForAi(dto);

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals(Orders.PENDING_PAYMENT, result.getStatus());
        assertEquals(1L, result.getUserId());
        verify(orderMapper).insert(argThat(o -> o.getAmount().compareTo(new BigDecimal("99.99")) == 0));
        verify(orderDetailMapper).insertBatch(argThat(details -> {
            OrderDetail detail = details.get(0);
            return detail.getOrderId().equals(5L) &&
                    detail.getProductId().equals(100L) &&
                    detail.getNumber() == 2;
        }));
    }

    private AiOrderDTO buildValidAiOrderDTO() {
        AiOrderDTO dto = new AiOrderDTO();
        dto.setUserId(1L);
        dto.setAddressDetail("北京市朝阳区某街道100号");
        dto.setConsignee("张三");
        dto.setPhone("13800138000");
        dto.setRemark("AI代客下单");
        AiOrderDTO.AiOrderItemDTO item = new AiOrderDTO.AiOrderItemDTO();
        item.setProductId(100L);
        item.setQuantity(2);
        dto.setItems(List.of(item));
        return dto;
    }
}
