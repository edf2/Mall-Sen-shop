package com.mallsen.service.impl;

import com.mallsen.dto.GoodsSalesDTO;
import com.mallsen.entity.Category;
import com.mallsen.entity.Product;
import com.mallsen.mapper.CategoryMapper;
import com.mallsen.mapper.OrderDetailMapper;
import com.mallsen.mapper.OrderMapper;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.vo.LowStockVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessAIServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private StockPredictionHelper stockPredictionHelper;

    @InjectMocks
    private BusinessAIServiceImpl businessAIService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(100L)
                .name("测试商品")
                .stock(50)
                .categoryId(1L)
                .build();
    }

    @Test
    void getProductSales_shouldReturnErrorWhenProductMissing() {
        when(productMapper.getById(100L)).thenReturn(null);

        Map<String, Object> result = businessAIService.getProductSales(100L, 30);

        assertEquals("商品不存在", result.get("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductSales_shouldAggregateSales() {
        when(productMapper.getById(100L)).thenReturn(product);
        when(orderMapper.getDailySalesByProduct(any())).thenReturn(
                List.of(
                        Map.of("quantity", 2),
                        Map.of("quantity", 3)
                )
        );

        Map<String, Object> result = businessAIService.getProductSales(100L, 5);

        assertEquals(100L, result.get("productId"));
        assertEquals(50, result.get("currentStock"));
        assertEquals(5.0, ((Number) result.get("totalSales")).doubleValue());
        assertEquals(new BigDecimal("1.00"), result.get("avgDailySales"));
        assertEquals(50, ((Number) result.get("estimatedDaysLeft")).intValue());
        assertEquals(2, ((List<Map<String, Object>>) result.get("dailySales")).size());
    }

    @Test
    void getProductSales_shouldReportSufficientWhenNoSales() {
        when(productMapper.getById(100L)).thenReturn(product);
        when(orderMapper.getDailySalesByProduct(any())).thenReturn(List.of());

        Map<String, Object> result = businessAIService.getProductSales(100L, 30);

        assertEquals("充足", result.get("estimatedDaysLeft"));
        assertEquals(0.0, ((Number) result.get("totalSales")).doubleValue());
    }

    @Test
    void getStockPrediction_shouldDelegateToHelper() {
        when(stockPredictionHelper.predict(7, 30)).thenReturn(List.of(Map.of("productId", 1L)));

        Object result = businessAIService.getStockPrediction(7);

        assertInstanceOf(List.class, result);
        assertEquals(1, ((List<?>) result).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSalesReport_shouldBuildTrendAndTop() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);

        when(orderMapper.sumByMap(any())).thenReturn(200.0);
        when(orderMapper.countByMap(any())).thenReturn(10);
        when(orderMapper.getSalesTop(any(), any())).thenReturn(
                List.of(
                        GoodsSalesDTO.builder().name("商品A").number(5).build(),
                        GoodsSalesDTO.builder().name("商品B").number(3).build()
                )
        );

        Map<String, Object> result = businessAIService.getSalesReport(start, end);

        assertEquals(200.0, result.get("totalAmount"));
        assertEquals(10, result.get("totalOrderCount"));
        assertEquals(2, ((List<Map<String, Object>>) result.get("topProducts")).size());
        assertEquals("商品A", ((Map<String, Object>) ((List<?>) result.get("topProducts")).get(0)).get("name"));

        List<Map<String, Object>> trend = (List<Map<String, Object>>) result.get("dailyTrend");
        assertEquals(2, trend.size());
        assertEquals("2026-01-01", trend.get(0).get("date"));
        assertEquals("2026-01-02", trend.get(1).get("date"));
    }

    @Test
    void getInventoryOverview_shouldAggregateCategories() {
        when(productMapper.list(new Product())).thenReturn(List.of(product, Product.builder().id(200L).name("其他").stock(20).categoryId(1L).build()));
        when(productMapper.getZeroStockList()).thenReturn(List.of(new LowStockVO()));
        when(productMapper.getLowStockList()).thenReturn(List.of(new LowStockVO(), new LowStockVO()));
        when(categoryMapper.list()).thenReturn(List.of(Category.builder().id(1L).name("饮品").build()));

        Map<String, Object> result = businessAIService.getInventoryOverview();

        assertEquals(2, ((Number) result.get("totalProducts")).intValue());
        assertEquals(70, ((Number) result.get("totalStock")).intValue());
        assertEquals(1, ((Number) result.get("zeroStockCount")).intValue());
        assertEquals(2, ((Number) result.get("lowStockCount")).intValue());
        Map<String, Long> dist = (Map<String, Long>) result.get("categoryDistribution");
        assertEquals(2L, dist.get("饮品").longValue());
    }
}