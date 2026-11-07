package com.mallsen.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class InventoryPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    private Long productId;

    private Integer type;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
