package com.mallsen.service.impl;

import com.mallsen.entity.Orders;
import com.mallsen.entity.Product;
import com.mallsen.mapper.CategoryMapper;
import com.mallsen.mapper.OrderDetailMapper;
import com.mallsen.mapper.OrderMapper;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.service.BusinessAIService;
import com.mallsen.vo.LowStockVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessAIServiceImpl implements BusinessAIService {

    private static final int LOOKBACK_DAYS = 30;

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final CategoryMapper categoryMapper;
    private final StockPredictionHelper stockPredictionHelper;

    @Override
    public Map<String, Object> getProductSales(Long productId, int days) {
        Product product = productMapper.getById(productId);
        if (product == null) {
            return Map.of("error", "商品不存在");
        }

        LocalDateTime startTime = LocalDateTime.of(LocalDate.now().minusDays(days), LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.now();

        Map<String, Object> params = new HashMap<>();
        params.put("begin", startTime);
        params.put("end", endTime);
        params.put("productId", productId);
        params.put("status", Orders.COMPLETED);

        List<Map<String, Object>> dailySales = orderMapper.getDailySalesByProduct(params);

        double totalSales = dailySales.stream()
                .mapToDouble(m -> ((Number) m.getOrDefault("quantity", 0)).doubleValue())
                .sum();
        double avgDailySales = days > 0 ? totalSales / days : 0;
        int estimatedDays = avgDailySales > 0 ? (int) (product.getStock() / avgDailySales) : Integer.MAX_VALUE;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", product.getId());
        result.put("productName", product.getName());
        result.put("currentStock", product.getStock());
        result.put("dailySales", dailySales);
        result.put("avgDailySales", BigDecimal.valueOf(avgDailySales).setScale(2, RoundingMode.HALF_UP));
        result.put("totalSales", totalSales);
        result.put("estimatedDaysLeft", estimatedDays == Integer.MAX_VALUE ? "充足" : estimatedDays);
        return result;
    }

    @Override
    public Object getStockPrediction(int days) {
        return stockPredictionHelper.predict(days, LOOKBACK_DAYS);
    }

    @Override
    public Map<String, Object> getSalesReport(LocalDate start, LocalDate end) {
        LocalDateTime startTime = LocalDateTime.of(start, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        Map<String, Object> params = new HashMap<>();
        params.put("begin", startTime);
        params.put("end", endTime);
        params.put("status", Orders.COMPLETED);

        Double totalAmount = orderMapper.sumByMap(params);
        Integer orderCount = orderMapper.countByMap(params);

        //一条 GROUP BY 查询每日营业额/订单数，代替原先逐日 30 次循环（每天 2 次 SQL）
        Map<String, Map<String, Object>> dayRowMap = orderMapper.getDailySalesSummary(startTime, endTime)
                .stream()
                .collect(Collectors.toMap(m -> m.get("date").toString(), m -> m));

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            Map<String, Object> row = dayRowMap.get(current.toString());
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", current.toString());
            dayData.put("amount", row != null ? ((Number) row.get("turnover")).doubleValue() : 0);
            dayData.put("orderCount", row != null ? ((Number) row.get("orderCount")).intValue() : 0);
            dailyTrend.add(dayData);
            current = current.plusDays(1);
        }

        var salesTop = orderMapper.getSalesTop(startTime, endTime);
        List<Map<String, Object>> topProducts = salesTop.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", g.getName());
            m.put("number", g.getNumber());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());
        result.put("totalAmount", totalAmount != null ? totalAmount : 0);
        result.put("totalOrderCount", orderCount != null ? orderCount : 0);
        result.put("topProducts", topProducts);
        result.put("dailyTrend", dailyTrend);
        return result;
    }

    @Override
    public Map<String, Object> getInventoryOverview() {
        List<Product> allProducts = productMapper.list(new Product());

        int totalStock = allProducts.stream()
                .filter(p -> p.getStock() != null)
                .mapToInt(Product::getStock)
                .sum();

        List<LowStockVO> zeroStock = productMapper.getZeroStockList();
        List<LowStockVO> lowStock = productMapper.getLowStockList();

        long totalProducts = allProducts.size();

        Map<Long, String> categoryNameMap = categoryMapper.list().stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        Map<String, Long> categoryDistribution = allProducts.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        p -> categoryNameMap.getOrDefault(p.getCategoryId(), "未分类"),
                        Collectors.counting()
                ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalProducts", totalProducts);
        result.put("totalStock", totalStock);
        result.put("zeroStockCount", zeroStock.size());
        result.put("lowStockCount", lowStock.size());
        result.put("zeroStockList", zeroStock);
        result.put("lowStockList", lowStock);
        result.put("categoryDistribution", categoryDistribution);
        return result;
    }
}
