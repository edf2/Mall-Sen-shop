package com.mallsen.service.impl;

import com.mallsen.context.BaseContext;
import com.mallsen.dto.ShoppingCartDTO;
import com.mallsen.entity.Product;
import com.mallsen.entity.ShoppingCart;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.mapper.ShoppingCartMapper;
import com.mallsen.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //判断当前加入购物车的商品是否存在
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //如果存在，只需数量加一
        if (list != null && list.size() > 0){
             ShoppingCart cart = list.get(0);
             cart.setNumber(cart.getNumber() + 1);

             shoppingCartMapper.updateNumberById(cart);
        } else {
            //如果不存在，需要插入一条购物车数据

            //添加到购物车的是商品
            Long productId = shoppingCartDTO.getProductId();
            Product product = productMapper.getById(productId);
            shoppingCart.setName(product.getName());
            shoppingCart.setImage(product.getImage());
            shoppingCart.setAmount(product.getPrice());
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 查看购物车
     * @return
     */
    @Override
    public List<ShoppingCart> list() {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空购物车
     */
    @Override
    public void clean() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deletByUserId(userId);
    }

    /**
     * 减少购物车
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //先获取购物车信息
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //获取购物车中当前商品的数量
        ShoppingCart cart = list.get(0);
        Integer number = cart.getNumber();

        //如果数量为一就直接删除商品
        if (number == 1){
            shoppingCartMapper.deleteById(cart.getId());
        } else {
            //如果数量不为一就减少
            cart.setNumber(number - 1);
            shoppingCartMapper.updateNumberById(cart);
        }
    }
}
