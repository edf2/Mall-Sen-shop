package com.mallsen.service.impl;

import com.mallsen.entity.Product;
import com.mallsen.mapper.OrderDetailMapper;
import com.mallsen.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存预测公共逻辑：BusinessAIServiceImpl 与 PredictTask 共用，
 * 通过一次批量 SQL 统计回看期内各商品销量，避免对每个商品单独发一条 SQL（N+1）。
 */
@Component
@RequiredArgsConstructor
public class StockPredictionHelper {

    private final ProductMapper productMapper;
    private final OrderDetailMapper orderDetailMapper;

    /**
     * 预测未来 thresholdDays 天内可能缺货的商品
     * @param thresholdDays 缺货预警阈值（预计可售天数 <= 该值则视为缺货风险）
     * @param lookbackDays  回看统计销量的天数
     * @return 缺货风险商品列表，按预计可售天数升序
     */
    public List<Map<String, Object>> predict(int thresholdDays, int lookbackDays) {
        LocalDateTime startTime = LocalDateTime.of(LocalDate.now().minusDays(lookbackDays), LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.now();

        // 一次批量查询所有商品在回看期内的总销量，避免 N+1
        Map<Long, Integer> salesMap = orderDetailMapper.getProductSalesInPeriod(startTime, endTime)
                .stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("productId")).longValue(),
                        m -> ((Number) m.getOrDefault("totalSales", 0)).intValue(),
                        (a, b) -> a + b
                ));

        List<Product> allProducts = productMapper.list(new Product());
        List<Map<String, Object>> predictions = new ArrayList<>();

        for (Product product : allProducts) {
            if (product.getStock() == null || product.getStock() <= 0) {
                continue;
            }

            double totalSales = salesMap.getOrDefault(product.getId(), 0);
            double avgDailySales = totalSales / lookbackDays;
            int estimatedDays = avgDailySales > 0 ? (int) (product.getStock() / avgDailySales) : Integer.MAX_VALUE;

            if (estimatedDays <= thresholdDays) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", product.getId());
                item.put("productName", product.getName());
                item.put("currentStock", product.getStock());
                item.put("dailySales", BigDecimal.valueOf(avgDailySales).setScale(2, RoundingMode.HALF_UP));
                item.put("estimatedDaysLeft", estimatedDays);
                predictions.add(item);
            }
        }

        predictions.sort(Comparator.comparingInt(m -> (int) m.get("estimatedDaysLeft")));
        return predictions;
    }
}
