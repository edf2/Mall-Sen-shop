package com.mallsen.service;

import com.mallsen.dto.InventoryDTO;
import com.mallsen.dto.InventoryPageQueryDTO;
import com.mallsen.result.PageResult;
import com.mallsen.vo.InventoryVO;

public interface InventoryService {

    void inbound(InventoryDTO inventoryDTO);

    void outbound(InventoryDTO inventoryDTO);

    void adjust(InventoryDTO inventoryDTO);

    void clearProductCacheById(Long productId);

    InventoryVO getInventoryOverview();

    PageResult pageQuery(InventoryPageQueryDTO dto);
}
