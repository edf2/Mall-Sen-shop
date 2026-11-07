package com.mallsen.service.impl;

import com.mallsen.mapper.OrderMapper;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.mapper.UserMapper;
import com.mallsen.vo.BusinessDataVO;
import com.mallsen.vo.OrderOverViewVO;
import com.mallsen.vo.ProductOverViewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private WorkspaceServiceImpl workspaceService;

    private LocalDateTime begin;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        begin = LocalDateTime.of(2024, 1, 1, 0, 0);
        end = LocalDateTime.of(2024, 1, 31, 23, 59);
    }

    @Test
    void getBusinessData_shouldCalculateCorrectly() {
        when(orderMapper.countByMap(any(Map.class))).thenReturn(100, 80);
        when(orderMapper.sumByMap(any(Map.class))).thenReturn(8000.0);
        when(userMapper.countByMap(any(Map.class))).thenReturn(20);

        BusinessDataVO result = workspaceService.getBusinessData(begin, end);

        assertEquals(8000.0, result.getTurnover());
        assertEquals(80, result.getValidOrderCount());
        assertEquals(0.8, result.getOrderCompletionRate(), 0.001);
        assertEquals(100.0, result.getUnitPrice(), 0.001);
        assertEquals(20, result.getNewUsers());
    }

    @Test
    void getBusinessData_shouldHandleZeroOrders() {
        when(orderMapper.countByMap(any(Map.class))).thenReturn(0, 0);
        when(orderMapper.sumByMap(any(Map.class))).thenReturn(null);
        when(userMapper.countByMap(any(Map.class))).thenReturn(0);

        BusinessDataVO result = workspaceService.getBusinessData(begin, end);

        assertEquals(0.0, result.getTurnover());
        assertEquals(0, result.getValidOrderCount());
        assertEquals(0.0, result.getOrderCompletionRate());
        assertEquals(0.0, result.getUnitPrice());
        assertEquals(0, result.getNewUsers());
    }

    @Test
    void getOrderOverView_shouldReturnCounts() {
        when(orderMapper.countByMap(any(Map.class)))
                .thenReturn(5, 10, 80, 20, 120);

        OrderOverViewVO result = workspaceService.getOrderOverView();

        assertEquals(5, result.getWaitingOrders());
        assertEquals(10, result.getDeliveredOrders());
        assertEquals(80, result.getCompletedOrders());
        assertEquals(20, result.getCancelledOrders());
        assertEquals(120, result.getAllOrders());
    }

    @Test
    void getProductOverView_shouldReturnCounts() {
        when(productMapper.countByMap(any(Map.class)))
                .thenReturn(50, 10);

        ProductOverViewVO result = workspaceService.getProductOverView();

        assertEquals(50, result.getSold());
        assertEquals(10, result.getDiscontinued());
    }
}
