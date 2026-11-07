package com.mallsen.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockWarningMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long productId;
    private String productName;
    private Integer currentStock;
    private Integer lowStockWarning;
    private LocalDateTime time;
}
