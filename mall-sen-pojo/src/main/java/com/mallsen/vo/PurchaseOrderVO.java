package com.mallsen.vo;

import com.mallsen.entity.PurchaseOrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNumber;

    private String status;

    private BigDecimal totalAmount;

    private String notes;

    private LocalDateTime createTime;

    private Long createUser;

    private List<PurchaseOrderItem> items;
}
