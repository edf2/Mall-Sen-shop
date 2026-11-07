package com.mallsen.service;

import com.mallsen.dto.PurchaseOrderDTO;
import com.mallsen.dto.PurchaseOrderPageQueryDTO;
import com.mallsen.result.PageResult;
import com.mallsen.vo.PurchaseOrderVO;

public interface PurchaseOrderService {

    PurchaseOrderVO create(PurchaseOrderDTO dto);

    void updateStatus(Long id, String status);

    void delete(Long id);

    PurchaseOrderVO getById(Long id);

    PageResult pageQuery(PurchaseOrderPageQueryDTO dto);
}
