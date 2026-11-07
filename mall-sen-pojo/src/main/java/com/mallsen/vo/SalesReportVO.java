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
public class SalesReportVO implements Serializable {
    private BigDecimal totalAmount;
    private Integer totalQuantity;
    private Integer orderCount;
    private List<Map<String, Object>> topProducts;
    private String startDate;
    private String endDate;
    private List<Map<String, Object>> dailyTrend;
}
