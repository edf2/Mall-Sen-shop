package com.mallsen.mapper;

import com.github.pagehelper.Page;
import com.mallsen.dto.InventoryPageQueryDTO;
import com.mallsen.entity.InventoryRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper {

    void insertRecord(InventoryRecord record);

    Page<InventoryRecord> pageQuery(InventoryPageQueryDTO dto);
}
