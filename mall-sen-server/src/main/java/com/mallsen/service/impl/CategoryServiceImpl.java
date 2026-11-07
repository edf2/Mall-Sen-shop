package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mallsen.constant.MessageConstant;
import com.mallsen.constant.StatusConstant;
import com.mallsen.dto.CategoryDTO;
import com.mallsen.dto.CategoryPageQueryDTO;
import com.mallsen.entity.Category;
import com.mallsen.exception.DeletionNotAllowedException;
import com.mallsen.mapper.CategoryMapper;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.result.PageResult;
import com.mallsen.service.CategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ProductMapper productMapper;

    /**
     * 新增菜品分类
     * @param categoryDTO
     */
    @Override
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();

        BeanUtils.copyProperties(categoryDTO, category);

        //设置默认状态 1表示启用 0表示禁用
        category.setStatus(StatusConstant.DISABLE);
        categoryMapper.insert(category);
    }

    /**
     * 分页查询分类
     * @param categoryPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(CategoryPageQueryDTO categoryPageQueryDTO) {

        //开启分页
        PageHelper.startPage(categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());

        Page<Category> page = categoryMapper.page(categoryPageQueryDTO);

        Long total = page.getTotal();
        List<Category> records = page.getResult();

        return new PageResult(total, records);
    }

    /**
     * 启用或禁用分类
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Category category = new Category();
        category.setId(id);
        category.setStatus(status);
        categoryMapper.update(category);
    }

    /**
     * 查询所有分类
     * @return
     */
    @Override
    public List<Category> list() {
        return categoryMapper.list();
    }

    /**
     * 修改分类
     * @param categoryDTO
     */
    @Override
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();

        BeanUtils.copyProperties(categoryDTO, category);

        categoryMapper.update(category);
    }

     /**
     * 删除分类
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        //查询当前分类是否关联了商品，如果关联了就抛出业务异常
        Integer count = productMapper.countByCategoryId(id);
        if(count > 0){
            //当前分类下有商品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_PRODUCT);
        }

        categoryMapper.deleteById(id);
    }


}
