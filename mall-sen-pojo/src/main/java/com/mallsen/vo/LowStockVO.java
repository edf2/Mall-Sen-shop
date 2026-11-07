package com.mallsen.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockVO implements Serializable {

    private Long id;

    private String name;

    private String categoryName;

    private Integer stock;

    private Integer lowStockWarning;

    private Integer status;
}
