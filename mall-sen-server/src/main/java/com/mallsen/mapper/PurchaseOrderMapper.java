package com.mallsen.mapper;

import com.github.pagehelper.Page;
import com.mallsen.dto.PurchaseOrderPageQueryDTO;
import com.mallsen.entity.PurchaseOrder;
import com.mallsen.entity.PurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PurchaseOrderMapper {

    void insert(PurchaseOrder purchaseOrder);

    void insertItems(List<PurchaseOrderItem> items);

    Page<PurchaseOrder> pageQuery(PurchaseOrderPageQueryDTO dto);

    @Select("select * from purchase_order where id = #{id}")
    PurchaseOrder getById(Long id);

    @Select("select * from purchase_order_item where purchase_order_id = #{purchaseOrderId}")
    List<PurchaseOrderItem> getItemsByOrderId(Long purchaseOrderId);

    void update(PurchaseOrder purchaseOrder);

    void deleteById(Long id);

    void deleteItemsByOrderId(Long purchaseOrderId);
}
