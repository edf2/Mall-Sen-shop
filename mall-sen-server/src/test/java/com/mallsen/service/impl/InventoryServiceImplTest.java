package com.mallsen.service.impl;

import com.github.pagehelper.Page;
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
import com.mallsen.result.PageResult;
import com.mallsen.vo.InventoryVO;
import com.mallsen.vo.LowStockVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ProductCacheEvictor productCacheEvictor;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private InventoryDTO inventoryDTO;
    private Product product;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);

        inventoryDTO = new InventoryDTO();
        inventoryDTO.setProductId(100L);
        inventoryDTO.setType(1);
        inventoryDTO.setQuantity(50);
        inventoryDTO.setRemark("测试入库");

        product = Product.builder()
                .id(100L)
                .name("测试商品")
                .stock(100)
                .categoryId(1L)
                .lowStockWarning(20)
                .build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void inbound_shouldIncreaseStock() {
        when(productMapper.getById(100L)).thenReturn(product);

        inventoryService.inbound(inventoryDTO);

        verify(productMapper).update(argThat(p ->
                p.getId().equals(100L) && p.getStock() == 150
        ));
        verify(inventoryMapper).insertRecord(argThat(r ->
                r.getStockBefore() == 100 &&
                r.getStockAfter() == 150 &&
                r.getQuantity() == 50
        ));
    }

    @Test
    void inbound_shouldThrowWhenProductNotFound() {
        when(productMapper.getById(100L)).thenReturn(null);

        assertThrows(ProductNotFoundException.class,
                () -> inventoryService.inbound(inventoryDTO));
        verify(productMapper, never()).update(any());
    }

    @Test
    void outbound_shouldSendWarningWhenStockBelowThreshold() {
        product.setStock(15);
        product.setLowStockWarning(20);
        inventoryDTO.setQuantity(5);
        when(productMapper.getById(100L)).thenReturn(product);

        inventoryService.outbound(inventoryDTO);

        verify(rabbitTemplate).convertAndSend(eq("stock.warning.exchange"), eq("stock.warning"), any(Object.class));
    }

    @Test
    void outbound_shouldDecreaseStock() {
        when(productMapper.getById(100L)).thenReturn(product);

        inventoryService.outbound(inventoryDTO);

        verify(productMapper).update(argThat(p ->
                p.getId().equals(100L) && p.getStock() == 50
        ));
        verify(inventoryMapper).insertRecord(argThat(r ->
                r.getStockBefore() == 100 &&
                r.getStockAfter() == 50 &&
                r.getQuantity() == 50
        ));
    }

    @Test
    void outbound_shouldThrowWhenInsufficientStock() {
        inventoryDTO.setQuantity(200);
        when(productMapper.getById(100L)).thenReturn(product);

        assertThrows(InsufficientStockException.class,
                () -> inventoryService.outbound(inventoryDTO));
    }

    @Test
    void outbound_shouldThrowWhenProductNotFound() {
        when(productMapper.getById(100L)).thenReturn(null);

        assertThrows(ProductNotFoundException.class,
                () -> inventoryService.outbound(inventoryDTO));
    }

    @Test
    void adjust_shouldSetExactStock() {
        inventoryDTO.setQuantity(200);
        when(productMapper.getById(100L)).thenReturn(product);

        inventoryService.adjust(inventoryDTO);

        verify(productMapper).update(argThat(p ->
                p.getId().equals(100L) && p.getStock() == 200
        ));
    }

    @Test
    void adjust_shouldThrowWhenProductNotFound() {
        when(productMapper.getById(100L)).thenReturn(null);

        assertThrows(ProductNotFoundException.class,
                () -> inventoryService.adjust(inventoryDTO));
    }

    @Test
    void getInventoryOverview_shouldReturnOverview() {
        when(productMapper.getZeroStockList()).thenReturn(List.of(new LowStockVO()));
        when(productMapper.getLowStockList()).thenReturn(List.of(new LowStockVO(), new LowStockVO()));

        InventoryVO result = inventoryService.getInventoryOverview();

        assertEquals(1, result.getZeroStockCount());
        assertEquals(2, result.getLowStockCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    void pageQuery_shouldReturnPageResult() {
        InventoryPageQueryDTO dto = new InventoryPageQueryDTO();
        dto.setPage(1);
        dto.setPageSize(10);

        Page<InventoryRecord> page = new Page<>(1, 10);
        page.add(new InventoryRecord());
        page.setTotal(5L);
        when(inventoryMapper.pageQuery(dto)).thenReturn(page);

        PageResult result = inventoryService.pageQuery(dto);

        assertEquals(5L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void clearProductCacheById_shouldEvictCategoryCache() {
        when(productMapper.getById(100L)).thenReturn(product);

        inventoryService.clearProductCacheById(100L);

        verify(productCacheEvictor).evictCategoryAfterCommit(1L);
    }
}
