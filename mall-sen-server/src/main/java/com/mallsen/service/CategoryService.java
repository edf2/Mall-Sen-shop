package com.mallsen.service;

import com.mallsen.dto.CategoryDTO;
import com.mallsen.dto.CategoryPageQueryDTO;
import com.mallsen.entity.Category;
import com.mallsen.result.PageResult;

import java.util.List;

public interface CategoryService {

    /**
     * 新增菜品分类
     * @param categoryDTO
     */
    void save(CategoryDTO categoryDTO);

    /**
     * 分类分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    PageResult page(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 启用或禁用分类
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 查询所有分类
     * @return
     */
    List<Category> list();

    /**
     * 修改分类
     * @param categoryDTO
     */
    void update(CategoryDTO categoryDTO);

    /**
     * 根据id删除分类
     * @param id
     */
    void deleteById(Long id);
}
