package com.mallsen.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class InventoryDTO implements Serializable {

    private Long productId;

    private Integer type;

    private Integer quantity;

    private String remark;
}
