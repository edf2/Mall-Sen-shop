package com.mallsen.service.impl;

import com.mallsen.entity.Product;
import com.mallsen.mapper.OrderDetailMapper;
import com.mallsen.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 库存缺货预测测试：按“回看期日均销量”计算预计可售天数并过滤出低库存风险商品。
 */
@ExtendWith(MockitoExtension.class)
class StockPredictionHelperTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @InjectMocks
    private StockPredictionHelper helper;

    @Test
    void predict_filtersOnlyRiskProducts() {
        // 商品1：库存 10，30 天销 300 → 日均 10 → 预计可售 1 天 → 7 天内缺货风险
        Product risk = Product.builder().id(1L).stock(10).build();
        // 商品2：库存 1000，0 销量 → 预计可售充足 → 不告警
        Product safe = Product.builder().id(2L).stock(1000).build();
        // 商品3：零库存，应被跳过（不进入预测列表）
        Product zero = Product.builder().id(3L).stock(0).build();

        when(productMapper.list(any())).thenReturn(List.of(risk, safe, zero));
        when(orderDetailMapper.getProductSalesInPeriod(any(), any()))
                .thenReturn(List.of(Map.of("productId", 1L, "totalSales", 300)));

        List<Map<String, Object>> predictions = helper.predict(7, 30);

        assertEquals(1, predictions.size());
        assertEquals(1L, ((Number) predictions.get(0).get("productId")).longValue());
    }

@Test
    void predict_noSales_returnsEmpty() {
        Product safe = Product.builder().id(1L).stock(1000).build();
        when(productMapper.list(any())).thenReturn(List.of(safe));
        when(orderDetailMapper.getProductSalesInPeriod(any(), any())).thenReturn(List.of());

        List<Map<String, Object>> predictions = helper.predict(7, 30);

        assertEquals(0, predictions.size());
    }
}