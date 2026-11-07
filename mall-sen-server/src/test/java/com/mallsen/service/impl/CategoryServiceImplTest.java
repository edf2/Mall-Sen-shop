package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.mallsen.constant.MessageConstant;
import com.mallsen.constant.StatusConstant;
import com.mallsen.dto.CategoryDTO;
import com.mallsen.dto.CategoryPageQueryDTO;
import com.mallsen.entity.Category;
import com.mallsen.exception.DeletionNotAllowedException;
import com.mallsen.mapper.CategoryMapper;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CategoryDTO categoryDTO;
    private Category category;

    @BeforeEach
    void setUp() {
        categoryDTO = new CategoryDTO();
        categoryDTO.setName("测试分类");
        categoryDTO.setSort(1);

        category = Category.builder()
                .id(1L)
                .name("测试分类")
                .sort(1)
                .status(StatusConstant.ENABLE)
                .build();
    }

    @Test
    void save_shouldSetDefaultStatusAndInsert() {
        categoryService.save(categoryDTO);

        verify(categoryMapper).insert(argThat(c ->
                c.getName().equals("测试分类") &&
                c.getStatus().equals(StatusConstant.DISABLE)
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void page_shouldReturnPageResult() {
        CategoryPageQueryDTO queryDTO = new CategoryPageQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setPageSize(10);

        Page<Category> page = new Page<>(1, 10);
        page.add(category);
        page.add(category);
        page.setTotal(2L);
        when(categoryMapper.page(queryDTO)).thenReturn(page);

        PageResult result = categoryService.page(queryDTO);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    void startOrStop_shouldUpdateStatus() {
        categoryService.startOrStop(StatusConstant.DISABLE, 1L);

        verify(categoryMapper).update(argThat(c ->
                c.getId().equals(1L) &&
                c.getStatus().equals(StatusConstant.DISABLE)
        ));
    }

    @Test
    void list_shouldReturnAllCategories() {
        when(categoryMapper.list()).thenReturn(List.of(category));

        List<Category> result = categoryService.list();

        assertEquals(1, result.size());
        assertEquals("测试分类", result.get(0).getName());
    }

    @Test
    void update_shouldCopyAndUpdate() {
        categoryService.update(categoryDTO);

        verify(categoryMapper).update(argThat(c ->
                c.getName().equals("测试分类") &&
                c.getSort() == 1
        ));
    }

    @Test
    void deleteById_shouldThrowWhenCategoryHasProducts() {
        when(productMapper.countByCategoryId(1L)).thenReturn(5);

        DeletionNotAllowedException ex = assertThrows(DeletionNotAllowedException.class,
                () -> categoryService.deleteById(1L));
        assertEquals(MessageConstant.CATEGORY_BE_RELATED_BY_PRODUCT, ex.getMessage());
        verify(categoryMapper, never()).deleteById(any());
    }

    @Test
    void deleteById_shouldDeleteWhenNoProducts() {
        when(productMapper.countByCategoryId(1L)).thenReturn(0);

        categoryService.deleteById(1L);

        verify(categoryMapper).deleteById(1L);
    }
}
