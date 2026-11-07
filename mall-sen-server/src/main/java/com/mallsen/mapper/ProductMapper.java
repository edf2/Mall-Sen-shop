package com.mallsen.mapper;

import com.github.pagehelper.Page;
import com.mallsen.annotation.AutoFill;
import com.mallsen.dto.ProductPageQueryDTO;
import com.mallsen.entity.Product;
import com.mallsen.enumeration.OperationType;
import com.mallsen.vo.LowStockVO;
import com.mallsen.vo.ProductVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper {

    @Select("select count(id) from product where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    @AutoFill(value = OperationType.INSERT)
    void insert(Product product);

    Page<ProductVO> pageQuery(ProductPageQueryDTO productPageQueryDTO);

    @Select("select * from product where id = #{id}")
    Product getById(Long id);

    @Delete("delete from product where id = #{id}")
    void deleteById(Long id);

    @AutoFill(value = OperationType.UPDATE)
    void update(Product product);

    List<Product> list(Product product);

    Integer countByMap(Map map);

    List<LowStockVO> getZeroStockList();

    List<LowStockVO> getLowStockList();

    /**
     * 下单预占库存：条件扣减，stock >= quantity 才允许扣减，防止并发超卖
     * @param id 商品id
     * @param quantity 扣减数量
     * @return 影响行数，0 表示库存不足
     */
    @Update("update product set stock = stock - #{quantity}, update_time = now() where id = #{id} and stock >= #{quantity}")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 支付成功后确认销量（库存已在下单时预占扣减）
     * @param id 商品id
     * @param quantity 销量增加数量
     */
    @Update("update product set sales = sales + #{quantity}, update_time = now() where id = #{id}")
    int updateProductSales(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 归还已预占但未支付的库存
     * @param id 商品id
     * @param quantity 归还数量
     */
    @Update("update product set stock = stock + #{quantity}, update_time = now() where id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("update product set stock = stock + #{quantity}, sales = sales - #{quantity}, update_time = now() where id = #{id}")
    int restoreStockAndSales(@Param("id") Long id, @Param("quantity") Integer quantity);

    List<Map<String, Object>> getCategoryInventoryDistribution();

    @Select("select count(*) from product where stock = 0")
    Integer countZeroStock();

    @Select("select count(*) from product where stock > 0 and stock <= low_stock_warning")
    Integer countLowStock();

    @Select("select coalesce(sum(stock), 0) from product")
    Integer sumTotalStock();
}
