package com.mallsen.service.impl;

import com.mallsen.entity.PurchaseOrder;
import com.mallsen.entity.PurchaseOrderItem;
import com.mallsen.exception.PurchaseOrderNotFoundException;
import com.mallsen.mapper.PurchaseOrderMapper;
import com.mallsen.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购单状态机测试：校验 PENDING->APPROVED->RECEIVED 等合法流转、
 * 非法跳转拦截、终态不可变、以及 RECEIVED 只触发一次入库。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private PurchaseOrderServiceImpl service;

    @Test
    void approvePending_isAllowed() {
        when(purchaseOrderMapper.getById(1L)).thenReturn(
                PurchaseOrder.builder().id(1L).status(PurchaseOrder.PENDING).build());

        service.updateStatus(1L, PurchaseOrder.APPROVED);

        verify(purchaseOrderMapper).update(any());
        verify(inventoryService, never()).inbound(any());
    }

    @Test
    void pendingToReceived_isRejected() {
        when(purchaseOrderMapper.getById(1L)).thenReturn(
                PurchaseOrder.builder().id(1L).status(PurchaseOrder.PENDING).build());

        assertThrows(PurchaseOrderNotFoundException.class,
                () -> service.updateStatus(1L, PurchaseOrder.RECEIVED));

        verify(purchaseOrderMapper, never()).update(any());
    }

    @Test
    void approvedToReceived_triggersInboundOnce() {
        when(purchaseOrderMapper.getById(1L)).thenReturn(
                PurchaseOrder.builder().id(1L).status(PurchaseOrder.APPROVED).build());
        when(purchaseOrderMapper.getItemsByOrderId(1L)).thenReturn(List.of(
                PurchaseOrderItem.builder().productId(100L).quantity(5).build()));

        service.updateStatus(1L, PurchaseOrder.RECEIVED);

        verify(purchaseOrderMapper).update(any());
        verify(inventoryService).inbound(any());
    }

    @Test
    void terminalState_canNotBeChanged() {
        when(purchaseOrderMapper.getById(1L)).thenReturn(
                PurchaseOrder.builder().id(1L).status(PurchaseOrder.RECEIVED).build());

        assertThrows(PurchaseOrderNotFoundException.class,
                () -> service.updateStatus(1L, PurchaseOrder.CANCELLED));

        verify(purchaseOrderMapper, never()).update(any());
    }
}