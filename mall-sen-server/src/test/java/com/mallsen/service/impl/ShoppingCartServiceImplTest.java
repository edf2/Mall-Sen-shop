package com.mallsen.service.impl;

import com.mallsen.context.BaseContext;
import com.mallsen.dto.ShoppingCartDTO;
import com.mallsen.entity.Product;
import com.mallsen.entity.ShoppingCart;
import com.mallsen.mapper.ProductMapper;
import com.mallsen.mapper.ShoppingCartMapper;
import org.junit.jupiter.api.AfterEach;
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
class ShoppingCartServiceImplTest {

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ShoppingCartServiceImpl shoppingCartService;

    private ShoppingCartDTO shoppingCartDTO;
    private Product product;
    private ShoppingCart existingCart;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);

        shoppingCartDTO = new ShoppingCartDTO();
        shoppingCartDTO.setProductId(100L);
        shoppingCartDTO.setProductSpec("默认");

        product = Product.builder()
                .id(100L)
                .name("测试商品")
                .price(new BigDecimal("29.99"))
                .image("img.jpg")
                .build();

        existingCart = ShoppingCart.builder()
                .id(10L)
                .userId(1L)
                .productId(100L)
                .productSpec("默认")
                .number(1)
                .amount(new BigDecimal("29.99"))
                .name("测试商品")
                .image("img.jpg")
                .build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void addShoppingCart_shouldIncrementQuantityWhenProductExists() {
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(existingCart));

        shoppingCartService.addShoppingCart(shoppingCartDTO);

        verify(shoppingCartMapper).updateNumberById(argThat(cart ->
                cart.getId().equals(10L) && cart.getNumber() == 2
        ));
        verify(productMapper, never()).getById(any());
    }

    @Test
    void addShoppingCart_shouldInsertNewWhenProductNotInCart() {
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of());
        when(productMapper.getById(100L)).thenReturn(product);

        shoppingCartService.addShoppingCart(shoppingCartDTO);

        verify(shoppingCartMapper).insert(argThat(cart ->
                cart.getUserId().equals(1L) &&
                cart.getProductId().equals(100L) &&
                cart.getNumber() == 1 &&
                cart.getName().equals("测试商品") &&
                cart.getAmount().equals(new BigDecimal("29.99"))
        ));
    }

    @Test
    void list_shouldReturnCartItems() {
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(existingCart));

        List<ShoppingCart> result = shoppingCartService.list();

        assertEquals(1, result.size());
        assertEquals(100L, result.get(0).getProductId());
    }

    @Test
    void clean_shouldDeleteByUserId() {
        shoppingCartService.clean();

        verify(shoppingCartMapper).deletByUserId(1L);
    }

    @Test
    void subShoppingCart_shouldDeleteItemWhenQuantityIsOne() {
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(existingCart));

        shoppingCartService.subShoppingCart(shoppingCartDTO);

        verify(shoppingCartMapper).deleteById(10L);
        verify(shoppingCartMapper, never()).updateNumberById(any());
    }

    @Test
    void subShoppingCart_shouldDecrementQuantityWhenMoreThanOne() {
        existingCart.setNumber(3);
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(List.of(existingCart));

        shoppingCartService.subShoppingCart(shoppingCartDTO);

        verify(shoppingCartMapper).updateNumberById(argThat(cart ->
                cart.getId().equals(10L) && cart.getNumber() == 2
        ));
        verify(shoppingCartMapper, never()).deleteById(any());
    }
}
