package com.mallsen.controller.user;

import com.mallsen.dto.OrdersPaymentDTO;
import com.mallsen.dto.OrdersSubmitDTO;
import com.mallsen.result.PageResult;
import com.mallsen.result.Result;
import com.mallsen.service.OrderService;
import com.mallsen.vo.OrderPaymentVO;
import com.mallsen.vo.OrderSubmitVO;
import com.mallsen.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Tag(name = "用户小单相关接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/submit")
    @Operation(summary = "用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody @Valid OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单，订单信息：{}", ordersSubmitDTO);

        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    @PutMapping("/payment")
    @Operation(summary = "用户支付")
    public Result<OrderPaymentVO> paument(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        log.info("用户下单，订单信息：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成欲支付交易单：{}", orderPaymentVO);
        orderService.paySuccess(ordersPaymentDTO.getOrderNumber());
        return Result.success(orderPaymentVO);
    }

    @GetMapping("historyOrders")
    @Operation(summary = "历史订单查询")
    public Result<PageResult> page(int page, int pageSize, Integer status) {
        PageResult pageResult = orderService.pageQuery(page, pageSize, status);
        return Result.success(pageResult);
    }

    @GetMapping("/orderDetail/{id}")
    @Operation(summary = "订单详情查询")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        log.info("订单详情查询，订单id：{}", id);
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }

    @PutMapping("/cancel/{id}")
    @Operation(summary = "取消订单")
    public Result cancel(@PathVariable("id") Long id) {
        log.info("取消订单，订单id：{}", id);
        orderService.userCancelById(id);
        return Result.success();
    }

    @PostMapping("/repetition/{id}")
    @Operation(summary = "再来一单")
    public Result repetition(@PathVariable Long id) {
        log.info("再来一单，订单id：{}", id);
        orderService.repetition(id);
        return Result.success();
    }

    @GetMapping("/reminder/{id}")
    @Operation(summary = "催单")
    public Result reminder(@PathVariable Long id) {
        log.info("催单，订单id：{}", id);
        orderService.reminder(id);
        return Result.success();
    }
}
