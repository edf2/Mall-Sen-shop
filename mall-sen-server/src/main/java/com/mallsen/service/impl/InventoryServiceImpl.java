package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mallsen.component.ProductCacheEvictor;
import com.mallsen.context.BaseContext;
import com.mallsen.dto.InventoryDTO;
import com.mallsen.dto.InventoryPageQueryDTO;
import com.mallsen.entity.InventoryRecord;
import com.mallsen.entity.Product;
import com.mallsen.exception.InsufficientStockException;
import com.mallsen.exception.ProductNotFoundException;
import com.mallsen.mapper.InventoryMapper;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.message.StockWarningMessage;
import com.mallsen.result.PageResult;
import com.mallsen.service.InventoryService;
import com.mallsen.vo.InventoryVO;
import com.mallsen.vo.LowStockVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ProductCacheEvictor productCacheEvictor;

    @Override
    @Transactional
    public void inbound(InventoryDTO inventoryDTO) {
        Product product = productMapper.getById(inventoryDTO.getProductId());
        if (product == null) {
            throw new ProductNotFoundException("商品不存在");
        }

        int stockBefore = product.getStock();
        int stockAfter = stockBefore + inventoryDTO.getQuantity();

        product.setStock(stockAfter);
        productMapper.update(product);

        saveRecord(inventoryDTO, stockBefore, stockAfter);
        checkAndSendStockWarning(product);
    }

    @Override
    @Transactional
    public void outbound(InventoryDTO inventoryDTO) {
        Product product = productMapper.getById(inventoryDTO.getProductId());
        if (product == null) {
            throw new ProductNotFoundException("商品不存在");
        }

        int stockBefore = product.getStock();
        if (stockBefore < inventoryDTO.getQuantity()) {
            throw new InsufficientStockException("库存不足");
        }

        int stockAfter = stockBefore - inventoryDTO.getQuantity();

        product.setStock(stockAfter);
        productMapper.update(product);

        saveRecord(inventoryDTO, stockBefore, stockAfter);
        checkAndSendStockWarning(product);
    }

    @Override
    @Transactional
    public void adjust(InventoryDTO inventoryDTO) {
        Product product = productMapper.getById(inventoryDTO.getProductId());
        if (product == null) {
            throw new ProductNotFoundException("商品不存在");
        }

        int stockBefore = product.getStock();
        int stockAfter = inventoryDTO.getQuantity();

        product.setStock(stockAfter);
        productMapper.update(product);

        saveRecord(inventoryDTO, stockBefore, stockAfter);
        checkAndSendStockWarning(product);
    }

    @Override
    public InventoryVO getInventoryOverview() {
        List<LowStockVO> zeroStockList = productMapper.getZeroStockList();
        List<LowStockVO> lowStockList = productMapper.getLowStockList();

        return InventoryVO.builder()
                .zeroStockCount(zeroStockList.size())
                .lowStockCount(lowStockList.size())
                .zeroStockList(zeroStockList)
                .lowStockList(lowStockList)
                .build();
    }

    @Override
    public PageResult pageQuery(InventoryPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<InventoryRecord> page = inventoryMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public void clearProductCacheById(Long productId) {
        Product product = productMapper.getById(productId);
        if (product != null) {
            clearProductCache(product.getCategoryId());
        }
    }

    private void saveRecord(InventoryDTO dto, int stockBefore, int stockAfter) {
        InventoryRecord record = InventoryRecord.builder()
                .productId(dto.getProductId())
                .type(dto.getType())
                .quantity(dto.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .remark(dto.getRemark())
                .createTime(LocalDateTime.now())
                .createUser(BaseContext.getCurrentId())
                .build();
        inventoryMapper.insertRecord(record);
    }

    private void clearProductCache(Long categoryId) {
        productCacheEvictor.evictCategoryAfterCommit(categoryId);
    }

    private void checkAndSendStockWarning(Product product) {
        if (product.getLowStockWarning() != null && product.getStock() < product.getLowStockWarning()) {
            StockWarningMessage msg = StockWarningMessage.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .currentStock(product.getStock())
                    .lowStockWarning(product.getLowStockWarning())
                    .time(LocalDateTime.now())
                    .build();
            rabbitTemplate.convertAndSend("stock.warning.exchange", "stock.warning", msg);
            log.info("库存预警消息已发送，商品ID: {}, 当前库存: {}, 预警阈值: {}",
                    product.getId(), product.getStock(), product.getLowStockWarning());
        }
    }
}
