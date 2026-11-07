package com.mallsen.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseOrderDTO implements Serializable {

    private List<PurchaseOrderItemDTO> items;

    private String notes;

    @Data
    public static class PurchaseOrderItemDTO implements Serializable {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
