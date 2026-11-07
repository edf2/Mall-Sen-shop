package com.mallsen.service;

import com.mallsen.dto.*;
import com.mallsen.entity.Orders;
import com.mallsen.result.PageResult;
import com.mallsen.vo.OrderPaymentVO;
import com.mallsen.vo.OrderStatisticsVO;
import com.mallsen.vo.OrderSubmitVO;
import com.mallsen.vo.OrderVO;

public interface OrderService {

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO);

    /**
     * 支付成功，修改订单状态
     * @param orderNumber
     */
    void paySuccess(String orderNumber);

    /**
     * 查询历史订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery(int page, int pageSize, Integer status);

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 用户取消订单
     * @param id
     */
    void userCancelById(Long id);

    /**
     * 再来一单
     * @param id
     */
    void repetition(Long id);

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 各个状态的订单数量统计
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    void reject(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    void cancel(OrdersCancelDTO ordersCancelDTO);

    /**
     * 派送订单
     * @param id
     */
    void delivery(Long id);

    /**
     * 完成订单
     * @param id
     */
    void complete(Long id);

    /**
     * 订单催单
     * @param id
     */
    void reminder(Long id);

    /**
     * 修改订单收货地址（供AI使用）
     * @param orderId
     * @param address
     * @param consignee
     * @param phone
     */
    void updateAddress(Long orderId, String address, String consignee, String phone);

    /**
     * 退款申请（供AI使用）
     * @param orderId
     * @param reason
     */
    void applyRefund(Long orderId, String reason);

    /**
     * AI代客下单
     * @param dto
     * @return
     */
    Orders createOrderForAi(AiOrderDTO dto);
}
