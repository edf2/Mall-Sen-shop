package com.mallsen.controller.admin;

import com.mallsen.dto.ProductDTO;
import com.mallsen.dto.ProductPageQueryDTO;
import com.mallsen.entity.Product;
import com.mallsen.result.PageResult;
import com.mallsen.result.Result;
import com.mallsen.service.ProductService;
import com.mallsen.vo.ProductVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;



@RestController("adminProductController")
@RequestMapping("/admin/product")
@Tag(name = "商品管理")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping
    @Operation(summary = "新增商品")
    public Result save(@RequestBody ProductDTO productDTO) {
        log.info("新增商品：{}", productDTO);
        productService.save(productDTO);

        String key = "product_" + productDTO.getCategoryId();
        clearCache(key);

        return Result.success();
    }

    @GetMapping("/page")
    @Operation(summary = "商品分页查询")
    public Result<PageResult> page(ProductPageQueryDTO productPageQueryDTO) {
        log.info("商品分页查询：{}", productPageQueryDTO);
        PageResult pageResult = productService.pageQuery(productPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @Operation(summary = "批量删除商品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除商品：{}", ids);
        productService.deleteBatch(ids);

        clearCache("product_*");

        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询商品")
    public Result<ProductVO> getById(@PathVariable Long id) {
        log.info("根据id查询商品：{}", id);
        ProductVO productVO = productService.getById(id);
        return Result.success(productVO);
    }

    @PutMapping
    @Operation(summary = "修改商品")
    public Result update(@RequestBody ProductDTO productDTO) {
        log.info("修改商品：{}", productDTO);
        productService.update(productDTO);

        clearCache("product_*");

        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "修改商品状态")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("修改商品状态：{}，{}", status, id);
        productService.startOrStop(status, id);

        clearCache("product_*");
        clearProductCacheByProductId(id);

        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "根据分类ID查找商品")
    public Result<List<Product>> list(Long categoryId) {
        log.info("根据分类ID查找商品：{}", categoryId);
        List<Product> productList = productService.list(categoryId);
        return Result.success(productList);
    }

    private void clearCache(String pattern) {
        try {
            Set keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("已清除Redis缓存，pattern: {}, 数量: {}, keys: {}", pattern, keys.size(), keys);
            } else {
                log.warn("Redis无匹配缓存，pattern: {}，这可能说明缓存键名与预期不符", pattern);
            }
        } catch (Exception e) {
            log.error("清除Redis缓存失败，pattern: {}", pattern, e);
        }
    }

    private void clearProductCacheByProductId(Long productId) {
        try {
            com.mallsen.vo.ProductVO productVO = productService.getById(productId);
            if (productVO != null && productVO.getCategoryId() != null) {
                String key = "product_" + productVO.getCategoryId();
                redisTemplate.delete(key);
                log.info("已清除商品缓存，key: {}", key);
            }
        } catch (Exception e) {
            log.error("清除商品缓存失败，productId: {}", productId, e);
        }
    }
}
