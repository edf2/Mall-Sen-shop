package com.mallsen.controller.aigc;

import com.mallsen.constant.StatusConstant;
import com.mallsen.context.BaseContext;
import com.mallsen.dto.AiOrderDTO;
import com.mallsen.dto.ProductPageQueryDTO;
import com.mallsen.entity.AddressBook;
import com.mallsen.entity.Orders;
import com.mallsen.exception.OrderBusinessException;
import com.mallsen.result.PageResult;
import com.mallsen.result.Result;
import com.mallsen.service.AddressBookService;
import com.mallsen.service.OrderService;
import com.mallsen.service.ProductService;
import com.mallsen.vo.AiDefaultAddressVO;
import com.mallsen.vo.AiOrderCreateVO;
import com.mallsen.vo.OrderVO;
import com.mallsen.vo.ProductVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户侧 AI 数据接口（供用户智能客服 Agent 调用，用户 JWT 拦截器鉴权）
 * 安全要点：userId 一律取自已认证的 JWT（BaseContext），客户端/Python 传入的 userId 一律忽略；
 * 订单类操作均校验订单归属，防止越权读取/修改/退款他人订单。
 */
@RestController
@RequestMapping("/user/business/ai")
@Tag(name = "AI 用户服务接口")
@Slf4j
@RequiredArgsConstructor
public class UserAIController {

    private final ProductService productService;
    private final OrderService orderService;
    private final AddressBookService addressBookService;

    @GetMapping("/product/search")
    @Operation(summary = "搜索商品（供AI推荐用）")
    public Result<?> searchProduct(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("AI搜索商品: keyword={}, page={}", keyword, page);
        ProductPageQueryDTO queryDTO = new ProductPageQueryDTO();
        queryDTO.setName(keyword);
        queryDTO.setStatus(StatusConstant.ENABLE);
        queryDTO.setPage(page);
        queryDTO.setPageSize(pageSize);

        PageResult pageResult = productService.pageQuery(queryDTO);
        long total = pageResult.getTotal();
        @SuppressWarnings("unchecked")
        List<ProductVO> records = (List<ProductVO>) pageResult.getRecords();

        List<Map<String, Object>> recordList = records.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", p.getId());
            m.put("productName", p.getName());
            m.put("price", p.getPrice());
            m.put("image", p.getImage());
            m.put("description", p.getDescription());
            m.put("stock", p.getStock());
            m.put("sales", p.getSales());
            return m;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("records", recordList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return Result.success(result);
    }

    @GetMapping("/order/detail/{id}")
    @Operation(summary = "查询订单详情（供AI使用，仅本人订单）")
    public Result<?> getOrderDetail(@PathVariable Long id) {
        log.info("AI查询订单详情: id={}", id);
        try {
            OrderVO orderVO = orderService.details(id);
            checkOrderOwner(orderVO);

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderVO.getId());
            result.put("orderNumber", orderVO.getNumber());
            result.put("status", orderVO.getStatus());
            result.put("amount", orderVO.getAmount());
            result.put("phone", orderVO.getPhone());
            result.put("address", orderVO.getAddress());
            result.put("consignee", orderVO.getConsignee());
            result.put("createTime", orderVO.getOrderTime());
            result.put("remark", orderVO.getRemark());
            result.put("payStatus", orderVO.getPayStatus());

            List<Map<String, Object>> items = orderVO.getOrderDetailList().stream().map(d -> {
                Map<String, Object> m = new HashMap<>();
                m.put("productId", d.getProductId());
                m.put("productName", d.getName());
                m.put("quantity", d.getNumber());
                m.put("amount", d.getAmount());
                m.put("image", d.getImage());
                return m;
            }).toList();
            result.put("items", items);
            return Result.success(result);
        } catch (OrderBusinessException e) {
            log.warn("AI查询订单详情失败: id={}, msg={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询订单详情失败: id={}", id, e);
            return Result.error("订单不存在");
        }
    }

    @GetMapping("/order/list")
    @Operation(summary = "查询本人历史订单列表（供AI使用）")
    public Result<?> getOrderList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("AI查询订单列表: page={}, pageSize={}", page, pageSize);
        // OrderService.pageQuery 内部按 BaseContext 当前用户过滤，天然隔离
        PageResult pageResult = orderService.pageQuery(page, pageSize, null);
        return Result.success(pageResult);
    }

    @PutMapping("/order/address")
    @Operation(summary = "修改本人订单收货地址（供AI使用）")
    public Result<?> updateAddress(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String newAddress = (String) request.get("newAddress");
        String contactName = (String) request.getOrDefault("contactName", "");
        String contactPhone = (String) request.getOrDefault("contactPhone", "");
        log.info("AI修改订单地址: orderId={}, address={}", orderId, newAddress);

        try {
            checkOrderOwner(orderService.details(orderId));
            orderService.updateAddress(orderId, newAddress, contactName, contactPhone);
        } catch (OrderBusinessException e) {
            log.warn("AI修改订单地址失败: orderId={}, msg={}", orderId, e.getMessage());
            return Result.error(e.getMessage());
        }

        OrderVO orderVO = orderService.details(orderId);
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("newAddress", orderVO.getAddress());
        result.put("contactName", orderVO.getConsignee());
        result.put("contactPhone", orderVO.getPhone());
        return Result.success(result);
    }

    @PutMapping("/order/refund")
    @Operation(summary = "本人订单退款申请（供AI使用）")
    public Result<?> applyRefund(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String reason = (String) request.get("reason");
        log.info("AI申请退款: orderId={}, reason={}", orderId, reason);

        try {
            checkOrderOwner(orderService.details(orderId));
            orderService.applyRefund(orderId, reason);
        } catch (OrderBusinessException e) {
            log.warn("AI申请退款失败: orderId={}, msg={}", orderId, e.getMessage());
            return Result.error(e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("status", "REFUND_APPLIED");
        result.put("message", "退款申请已提交");
        return Result.success(result);
    }

    @GetMapping("/address/default")
    @Operation(summary = "查询当前用户默认收货地址（供AI自动获取）")
    public Result<AiDefaultAddressVO> getDefaultAddress() {
        Long userId = BaseContext.getCurrentId();
        log.info("AI查询用户默认地址: userId={}", userId);

        AddressBook query = new AddressBook();
        query.setUserId(userId);
        query.setIsDefault(1);
        List<AddressBook> list = addressBookService.list(query);
        if (list == null || list.isEmpty()) {
            return Result.error("未设置默认地址");
        }
        AddressBook addr = list.get(0);
        String fullAddress = (addr.getProvinceName() != null ? addr.getProvinceName() : "")
                + (addr.getCityName() != null ? addr.getCityName() : "")
                + (addr.getDistrictName() != null ? addr.getDistrictName() : "")
                + (addr.getDetail() != null ? addr.getDetail() : "");
        AiDefaultAddressVO vo = new AiDefaultAddressVO(
                addr.getId(), addr.getConsignee(), addr.getPhone(), fullAddress, addr.getLabel());
        return Result.success(vo);
    }

    @PostMapping("/order/create")
    @Operation(summary = "AI代客下单（userId 由服务端取自已认证的JWT）")
    public Result<AiOrderCreateVO> createOrder(@RequestBody Map<String, Object> request) {
        log.info("AI代客下单请求: {}", request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) request.get("items");
        if (itemList == null || itemList.isEmpty()) {
            return Result.error("商品列表不能为空");
        }

        AiOrderDTO dto = new AiOrderDTO();
        // 身份以服务端已认证的 JWT 为准，忽略客户端传入的 userId，防止越权代下单
        Long userId = BaseContext.getCurrentId();
        log.info("当前用户ID: {}", userId);
        dto.setUserId(userId);
        dto.setAddressDetail((String) request.get("addressDetail"));
        dto.setConsignee((String) request.get("consignee"));
        dto.setPhone((String) request.get("phone"));
        dto.setRemark((String) request.getOrDefault("remark", ""));
        Object addressBookIdObj = request.get("addressBookId");
        if (addressBookIdObj != null && !addressBookIdObj.toString().isEmpty()) {
            dto.setAddressBookId(Long.valueOf(addressBookIdObj.toString()));
        }
        log.info("AI代客下单DTO: userId={}, addressDetail={}, consignee={}, phone={}, addressBookId={}",
                dto.getUserId(), dto.getAddressDetail(), dto.getConsignee(), dto.getPhone(), dto.getAddressBookId());

        List<AiOrderDTO.AiOrderItemDTO> items = itemList.stream().map(item -> {
            AiOrderDTO.AiOrderItemDTO itemDTO = new AiOrderDTO.AiOrderItemDTO();
            itemDTO.setProductId(Long.valueOf(item.get("productId").toString()));
            itemDTO.setQuantity(Integer.valueOf(item.get("quantity").toString()));
            return itemDTO;
        }).toList();
        dto.setItems(items);
        log.info("AI代客下单商品列表: {}", items);

        try {
            Orders order = orderService.createOrderForAi(dto);
            
            // 查询订单明细，填充商品列表
            List<AiOrderCreateVO.OrderDetailItem> orderItems = new java.util.ArrayList<>();
            if (order.getId() != null) {
                OrderVO orderDetail = orderService.details(order.getId());
                if (orderDetail != null && orderDetail.getOrderDetailList() != null) {
                    for (var detail : orderDetail.getOrderDetailList()) {
                        orderItems.add(new AiOrderCreateVO.OrderDetailItem(
                                detail.getProductId(),
                                detail.getName(),
                                detail.getNumber(),
                                detail.getAmount(),
                                detail.getImage()));
                    }
                }
            }
            
            AiOrderCreateVO vo = new AiOrderCreateVO(
                    order.getId(), order.getNumber(), order.getAmount(), orderItems);
            log.info("AI代客下单成功: orderId={}, orderNumber={}, amount={}",
                    order.getId(), order.getNumber(), order.getAmount());
            return Result.success(vo);
        } catch (OrderBusinessException e) {
            log.warn("AI代客下单失败: msg={}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("AI代客下单异常", e);
            return Result.error("下单失败: " + e.getMessage());
        }
    }

    /**
     * 校验订单归属：仅允许操作当前登录用户自己的订单
     */
    private void checkOrderOwner(OrderVO orderVO) {
        if (orderVO == null) {
            throw new OrderBusinessException("订单不存在");
        }
        Long userId = BaseContext.getCurrentId();
        if (orderVO.getUserId() == null || !orderVO.getUserId().equals(userId)) {
            throw new OrderBusinessException("无权操作该订单");
        }
    }
}
