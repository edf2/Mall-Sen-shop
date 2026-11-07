package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.mallsen.constant.MessageConstant;
import com.mallsen.constant.StatusConstant;
import com.mallsen.dto.ProductDTO;
import com.mallsen.dto.ProductPageQueryDTO;
import com.mallsen.entity.Product;
import com.mallsen.exception.DeletionNotAllowedException;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.result.PageResult;
import com.mallsen.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductDTO productDTO;
    private Product product;

    @BeforeEach
    void setUp() {
        productDTO = new ProductDTO();
        productDTO.setName("测试商品");
        productDTO.setCategoryId(1L);
        productDTO.setPrice(new BigDecimal("99.99"));
        productDTO.setImage("test.jpg");
        productDTO.setDescription("测试描述");
        productDTO.setStatus(StatusConstant.ENABLE);
        productDTO.setStock(100);

        product = Product.builder()
                .id(1L)
                .name("测试商品")
                .categoryId(1L)
                .price(new BigDecimal("99.99"))
                .image("test.jpg")
                .description("测试描述")
                .status(StatusConstant.ENABLE)
                .stock(100)
                .sales(50)
                .build();
    }

    @Test
    void save_shouldSetSalesToZeroAndInsert() {
        productService.save(productDTO);

        verify(productMapper).insert(argThat(p ->
                p.getName().equals("测试商品") &&
                p.getSales() == 0 &&
                p.getCategoryId().equals(1L) &&
                p.getStatus().equals(StatusConstant.ENABLE)
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void pageQuery_shouldReturnPageResult() {
        ProductPageQueryDTO queryDTO = new ProductPageQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setPageSize(10);

        Page<ProductVO> page = new Page<>(1, 10);
        page.add(new ProductVO());
        page.setTotal(1L);
        when(productMapper.pageQuery(queryDTO)).thenReturn(page);

        PageResult result = productService.pageQuery(queryDTO);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void deleteBatch_shouldThrowWhenProductOnSale() {
        product.setStatus(StatusConstant.ENABLE);
        when(productMapper.getById(1L)).thenReturn(product);

        DeletionNotAllowedException ex = assertThrows(DeletionNotAllowedException.class,
                () -> productService.deleteBatch(List.of(1L)));
        assertEquals(MessageConstant.PRODUCT_ON_SALE, ex.getMessage());
        verify(productMapper, never()).deleteById(any());
    }

    @Test
    void deleteBatch_shouldDeleteAllWhenProductsOffShelf() {
        product.setStatus(StatusConstant.DISABLE);
        when(productMapper.getById(1L)).thenReturn(product);

        productService.deleteBatch(List.of(1L));

        verify(productMapper).deleteById(1L);
    }

    @Test
    void getById_shouldReturnProductVO() {
        when(productMapper.getById(1L)).thenReturn(product);

        ProductVO result = productService.getById(1L);

        assertNotNull(result);
        assertEquals("测试商品", result.getName());
        assertEquals(new BigDecimal("99.99"), result.getPrice());
    }

    @Test
    void update_shouldCopyPropertiesAndUpdate() {
        productService.update(productDTO);

        verify(productMapper).update(argThat(p ->
                p.getName().equals("测试商品") &&
                p.getStock() == 100
        ));
    }

    @Test
    void startOrStop_shouldSetStatusAndUpdate() {
        productService.startOrStop(StatusConstant.DISABLE, 1L);

        verify(productMapper).update(argThat(p ->
                p.getId().equals(1L) &&
                p.getStatus().equals(StatusConstant.DISABLE)
        ));
    }

    @Test
    void list_shouldFilterByCategoryAndEnabledStatus() {
        when(productMapper.list(any(Product.class))).thenReturn(List.of(product));

        List<Product> result = productService.list(1L);

        assertEquals(1, result.size());
        verify(productMapper).list(argThat(p ->
                p.getCategoryId().equals(1L) &&
                p.getStatus().equals(StatusConstant.ENABLE)
        ));
    }
}
