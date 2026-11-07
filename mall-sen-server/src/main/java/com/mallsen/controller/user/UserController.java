package com.mallsen.controller.user;

import com.mallsen.constant.JwtClaimsConstant;
import com.mallsen.dto.UserDTO;
import com.mallsen.dto.UserLoginDTO;
import com.mallsen.entity.User;
import com.mallsen.properties.JwtProperties;
import com.mallsen.result.Result;
import com.mallsen.service.UserService;
import com.mallsen.utils.JwtUtil;
import com.mallsen.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Tag(name = "客户端用户管理")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("微信用户登录:{}", userLoginDTO);
        User user = userService.wxLogin(userLoginDTO);

        Map<String, Object> clalms = new HashMap<>();
        clalms.put(JwtClaimsConstant.USER_ID, user.getId());
        //为微信用户生成JWT令牌
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), clalms);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .nickName(user.getName())
                .avatarUrl(user.getAvatar())
                .build();

        return Result.success(userLoginVO);
    }

    @PutMapping("/profile")
    @Operation(summary = "更新用户信息")
    public Result updateProfile(@RequestBody UserDTO userDTO) {
        log.info("更新用户信息: {}", userDTO);
        userService.updateProfile(userDTO);
        return Result.success();
    }
}
