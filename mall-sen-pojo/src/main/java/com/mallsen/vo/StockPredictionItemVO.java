package com.mallsen.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPredictionItemVO implements Serializable {
    private Long productId;
    private String productName;
    private Integer currentStock;
    private BigDecimal dailySales;
    private Integer estimatedDaysLeft;
}
