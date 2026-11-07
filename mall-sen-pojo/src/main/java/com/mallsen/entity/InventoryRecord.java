package com.mallsen.entity;

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
public class InventoryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    private Integer type;

    private Integer quantity;

    private Integer stockBefore;

    private Integer stockAfter;

    private String remark;

    private LocalDateTime createTime;

    private Long createUser;
}
