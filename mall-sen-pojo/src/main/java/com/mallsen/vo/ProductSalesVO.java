package com.mallsen.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesVO implements Serializable {
    private Long productId;
    private String productName;
    private Integer currentStock;
    private List<Map<String, Object>> dailySales;
    private BigDecimal avgDailySales;
    private Integer estimatedDaysLeft;
}
