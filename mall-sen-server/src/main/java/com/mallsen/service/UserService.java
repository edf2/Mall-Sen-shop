package com.mallsen.service;

import com.mallsen.dto.UserDTO;
import com.mallsen.dto.UserLoginDTO;
import com.mallsen.entity.User;

public interface UserService {


    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 更新用户信息（头像、昵称等）
     * @param userDTO
     */
    void updateProfile(UserDTO userDTO);
}
