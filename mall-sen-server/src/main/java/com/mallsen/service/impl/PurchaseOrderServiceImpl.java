package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mallsen.context.BaseContext;
import com.mallsen.dto.InventoryDTO;
import com.mallsen.dto.PurchaseOrderDTO;
import com.mallsen.dto.PurchaseOrderPageQueryDTO;
import com.mallsen.entity.PurchaseOrder;
import com.mallsen.entity.PurchaseOrderItem;
import com.mallsen.exception.PurchaseOrderNotFoundException;
import com.mallsen.mapper.PurchaseOrderMapper;
import com.mallsen.result.PageResult;
import com.mallsen.service.InventoryService;
import com.mallsen.service.PurchaseOrderService;
import com.mallsen.vo.PurchaseOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public PurchaseOrderVO create(PurchaseOrderDTO dto) {
        String orderNumber = "PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", (int) (Math.random() * 10000));

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderDTO.PurchaseOrderItemDTO item : dto.getItems()) {
            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);
        }

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(orderNumber)
                .status(PurchaseOrder.PENDING)
                .totalAmount(total)
                .notes(dto.getNotes())
                .createTime(LocalDateTime.now())
                .createUser(BaseContext.getCurrentId())
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();
        purchaseOrderMapper.insert(order);

        List<PurchaseOrderItem> items = dto.getItems().stream().map(i -> {
            BigDecimal subtotal = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
            return PurchaseOrderItem.builder()
                    .purchaseOrderId(order.getId())
                    .productId(i.getProductId())
                    .productName(i.getProductName())
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .subtotal(subtotal)
                    .build();
        }).collect(Collectors.toList());
        purchaseOrderMapper.insertItems(items);

        return buildVO(order, items);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        PurchaseOrder order = purchaseOrderMapper.getById(id);
        if (order == null) {
            throw new PurchaseOrderNotFoundException("采购单不存在");
        }

        //状态机校验：防止 RECEIVED 重复触发重复入库、非法跳转
        validateTransition(order.getStatus(), status);

        order.setStatus(status);
        order.setUpdateTime(LocalDateTime.now());
        order.setUpdateUser(BaseContext.getCurrentId());
        purchaseOrderMapper.update(order);

        if (PurchaseOrder.RECEIVED.equals(status)) {
            List<PurchaseOrderItem> items = purchaseOrderMapper.getItemsByOrderId(id);
            for (PurchaseOrderItem item : items) {
                InventoryDTO dto = new InventoryDTO();
                dto.setProductId(item.getProductId());
                dto.setType(1);
                dto.setQuantity(item.getQuantity());
                dto.setRemark("采购入库: " + order.getOrderNumber());
                inventoryService.inbound(dto);
            }
            log.info("采购单 {} 已完成入库，共入库 {} 种商品", order.getOrderNumber(), items.size());
        }
    }

    /**
     * 采购单状态机：PENDING -> APPROVED -> RECEIVED（终态），PENDING/APPROVED -> CANCELLED（终态）
     * @param current 当前状态
     * @param target 目标状态
     */
    private void validateTransition(String current, String target) {
        if (PurchaseOrder.CANCELLED.equals(current) || PurchaseOrder.RECEIVED.equals(current)) {
            throw new PurchaseOrderNotFoundException("采购单已处于终态（" + current + "），不允许再变更");
        }
        boolean valid = (PurchaseOrder.PENDING.equals(current) && PurchaseOrder.APPROVED.equals(target))
                || (PurchaseOrder.PENDING.equals(current) && PurchaseOrder.CANCELLED.equals(target))
                || (PurchaseOrder.APPROVED.equals(current) && PurchaseOrder.RECEIVED.equals(target))
                || (PurchaseOrder.APPROVED.equals(current) && PurchaseOrder.CANCELLED.equals(target));
        if (!valid) {
            throw new PurchaseOrderNotFoundException("非法的采购单状态流转：" + current + " -> " + target);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PurchaseOrder order = purchaseOrderMapper.getById(id);
        if (order == null) {
            throw new PurchaseOrderNotFoundException("采购单不存在");
        }
        purchaseOrderMapper.deleteItemsByOrderId(id);
        purchaseOrderMapper.deleteById(id);
    }

    @Override
    public PurchaseOrderVO getById(Long id) {
        PurchaseOrder order = purchaseOrderMapper.getById(id);
        if (order == null) {
            throw new PurchaseOrderNotFoundException("采购单不存在");
        }
        List<PurchaseOrderItem> items = purchaseOrderMapper.getItemsByOrderId(id);
        return buildVO(order, items);
    }

    @Override
    public PageResult pageQuery(PurchaseOrderPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<PurchaseOrder> page = purchaseOrderMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    private PurchaseOrderVO buildVO(PurchaseOrder order, List<PurchaseOrderItem> items) {
        return PurchaseOrderVO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .createTime(order.getCreateTime())
                .createUser(order.getCreateUser())
                .items(items)
                .build();
    }
}
