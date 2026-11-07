package com.mallsen.controller.admin;

import com.mallsen.dto.InventoryDTO;
import com.mallsen.dto.InventoryPageQueryDTO;
import com.mallsen.result.PageResult;
import com.mallsen.result.Result;
import com.mallsen.service.InventoryService;
import com.mallsen.vo.InventoryVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/inventory")
@Tag(name = "库存管理")
@Slf4j
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    @PutMapping("/inbound")
    @Operation(summary = "商品入库")
    public Result inbound(@RequestBody InventoryDTO inventoryDTO) {
        log.info("商品入库：{}", inventoryDTO);
        inventoryDTO.setType(1);
        inventoryService.inbound(inventoryDTO);
        inventoryService.clearProductCacheById(inventoryDTO.getProductId());
        return Result.success();
    }

    @PutMapping("/outbound")
    @Operation(summary = "商品出库")
    public Result outbound(@RequestBody InventoryDTO inventoryDTO) {
        log.info("商品出库：{}", inventoryDTO);
        inventoryDTO.setType(2);
        inventoryService.outbound(inventoryDTO);
        inventoryService.clearProductCacheById(inventoryDTO.getProductId());
        return Result.success();
    }

    @PutMapping("/adjust")
    @Operation(summary = "库存盘点调整")
    public Result adjust(@RequestBody InventoryDTO inventoryDTO) {
        log.info("库存调整：{}", inventoryDTO);
        inventoryDTO.setType(3);
        inventoryService.adjust(inventoryDTO);
        inventoryService.clearProductCacheById(inventoryDTO.getProductId());
        return Result.success();
    }

    @GetMapping("/overview")
    @Operation(summary = "库存概览（工作台用）")
    public Result<InventoryVO> overview() {
        log.info("查询库存概览");
        InventoryVO inventoryVO = inventoryService.getInventoryOverview();
        return Result.success(inventoryVO);
    }

    @GetMapping("/records")
    @Operation(summary = "库存变动记录分页查询")
    public Result<PageResult> records(InventoryPageQueryDTO dto) {
        log.info("库存变动记录查询：{}", dto);
        PageResult pageResult = inventoryService.pageQuery(dto);
        return Result.success(pageResult);
    }
}
