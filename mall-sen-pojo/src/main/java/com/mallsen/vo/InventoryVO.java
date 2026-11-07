package com.mallsen.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryVO implements Serializable {

    private Integer zeroStockCount;

    private Integer lowStockCount;

    private List<LowStockVO> zeroStockList;

    private List<LowStockVO> lowStockList;
}
