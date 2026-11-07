package com.mallsen.controller.user;

import com.mallsen.entity.Product;
import com.mallsen.result.Result;
import com.mallsen.service.ProductService;
import com.mallsen.vo.ProductVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.redis.serializer.SerializationException;
import java.util.ArrayList;
import java.util.List;

@RestController("userProductController")
@RequestMapping("/user/product")
@Tag(name = "C端-商品接口")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/list")
    @Operation(summary = "根据分类ID查找商品")
    public Result<List<ProductVO>> list(Long categoryId) {

        String key = "product_" + categoryId;

        List<ProductVO> list = null;
        try {
            list = (List<ProductVO>) redisTemplate.opsForValue().get(key);
        } catch (SerializationException e) {
            log.warn("Redis缓存反序列化失败，key: {}，将清除缓存并重新查询", key, e);
            redisTemplate.delete(key);
        }

        if (list != null && list.size() > 0) {
            //从Redis有序集合中获取实时销量并合并
            mergeSalesFromZSet(list);
            return Result.success(list);
        }

        log.info("根据分类ID查找商品：{}", categoryId);
        List<Product> productList = productService.list(categoryId);
        List<ProductVO> productVOList = new ArrayList<>();
        for (Product p : productList) {
            ProductVO productVO = new ProductVO();
            BeanUtils.copyProperties(p, productVO);
            productVOList.add(productVO);
        }
        redisTemplate.opsForValue().set(key, productVOList);

        //从Redis有序集合中获取实时销量并合并
        mergeSalesFromZSet(productVOList);

        return Result.success(productVOList);
    }

    /**
     * 从Redis有序集合 product:sales 中合并实时销量到商品列表
     */
    private void mergeSalesFromZSet(List<ProductVO> productVOList) {
        String salesKey = "product:sales";
        for (ProductVO vo : productVOList) {
            Double score = redisTemplate.opsForZSet().score(salesKey, vo.getId());
            if (score != null) {
                vo.setSales(score.intValue());
            } else {
                //首次访问，将数据库中的销量同步到Redis
                if (vo.getSales() != null && vo.getSales() > 0) {
                    redisTemplate.opsForZSet().add(salesKey, vo.getId(), vo.getSales());
                }
            }
        }
    }
}
