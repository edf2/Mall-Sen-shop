package com.mallsen.service;

import com.mallsen.dto.ProductDTO;
import com.mallsen.dto.ProductPageQueryDTO;
import com.mallsen.entity.Product;
import com.mallsen.result.PageResult;
import com.mallsen.vo.ProductVO;

import java.util.List;

public interface ProductService {

    void save(ProductDTO productDTO);

    PageResult pageQuery(ProductPageQueryDTO productPageQueryDTO);

    void deleteBatch(List<Long> ids);

    ProductVO getById(Long id);

    void update(ProductDTO productDTO);

    void startOrStop(Integer status, Long id);

    List<Product> list(Long categoryId);
}
