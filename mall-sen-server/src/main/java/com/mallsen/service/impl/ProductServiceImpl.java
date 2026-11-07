package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mallsen.constant.MessageConstant;
import com.mallsen.constant.StatusConstant;
import com.mallsen.dto.ProductDTO;
import com.mallsen.dto.ProductPageQueryDTO;
import com.mallsen.entity.Product;
import com.mallsen.exception.DeletionNotAllowedException;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.result.PageResult;
import com.mallsen.service.ProductService;
import com.mallsen.vo.ProductVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Override
    @Transactional
    public void save(ProductDTO productDTO) {

        Product product = new Product();

        BeanUtils.copyProperties(productDTO, product);
        product.setSales(0);
        productMapper.insert(product);
    }

    @Override
    public PageResult pageQuery(ProductPageQueryDTO productPageQueryDTO) {

        PageHelper.startPage(productPageQueryDTO.getPage(),productPageQueryDTO.getPageSize());

        Page<ProductVO> page = productMapper.pageQuery(productPageQueryDTO);

        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {

        for (Long id : ids) {
            Product product = productMapper.getById(id);
            if (product.getStatus() == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.PRODUCT_ON_SALE);
            }
        }

        for (Long id : ids) {
            productMapper.deleteById(id);
        }

    }

    @Override
    public ProductVO getById(Long id) {
        Product product = productMapper.getById(id);

        ProductVO productVO = new ProductVO();

        BeanUtils.copyProperties(product, productVO);

        return productVO;
    }

    @Override
    public void update(ProductDTO productDTO) {

        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);
        productMapper.update(product);
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        productMapper.update(product);
    }

    @Override
    public List<Product> list(Long categoryId) {
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setStatus(StatusConstant.ENABLE);
        return productMapper.list(product);
    }

}
