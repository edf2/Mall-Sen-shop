package com.mallsen.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mallsen.constant.MessageConstant;
import com.mallsen.dto.UserDTO;
import com.mallsen.dto.UserLoginDTO;
import com.mallsen.entity.User;
import com.mallsen.context.BaseContext;
import com.mallsen.exception.LoginFailedException;
import com.mallsen.mapper.UserMapper;
import com.mallsen.properties.WeChatProperties;
import com.mallsen.service.UserService;
import com.mallsen.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String WX_ACCESS_TOKEN = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String WX_USER_INFO = "https://api.weixin.qq.com/cgi-bin/user/info";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * wx登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {

        //调用微信接口服务。获得当前微信用户的openid
        String openid = getOpenid(userLoginDTO);

        //判断openid是否为空，如果为空表示登录失败抛出异常
        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //判断当前用户是否为新用户如果是新用户自动完成注册
        User user = userMapper.selectByOpenid(openid);
        if (user == null) {
            User user1 = new User();
            user1.setOpenid(openid);
            user1.setCreateTime(LocalDateTime.now());

            // 尝试获取微信用户信息并保存
            JSONObject wxUserInfo = getWxUserInfo(openid);
            if (wxUserInfo != null) {
                user1.setName(wxUserInfo.getString("nickname"));
                user1.setAvatar(wxUserInfo.getString("headimgurl"));
                user1.setSex(String.valueOf(wxUserInfo.getIntValue("sex")));
            }

            userMapper.insert(user1);
            user = user1;
        } else {
            // 老用户也更新一下微信信息（昵称可能改了）
            JSONObject wxUserInfo = getWxUserInfo(openid);
            if (wxUserInfo != null) {
                String nickname = wxUserInfo.getString("nickname");
                String headimgurl = wxUserInfo.getString("headimgurl");
                boolean needUpdate = false;
                if (nickname != null && !nickname.isEmpty() && (user.getName() == null || user.getName().isEmpty())) {
                    user.setName(nickname);
                    needUpdate = true;
                }
                if (headimgurl != null && !headimgurl.isEmpty() && (user.getAvatar() == null || user.getAvatar().isEmpty())) {
                    user.setAvatar(headimgurl);
                    needUpdate = true;
                }
                if (needUpdate) {
                    userMapper.update(user);
                }
            }
        }
        //放回用户对象

        return user;
    }

    /**
     * 获取微信用户信息（昵称、头像）
     * @param openid 微信用户唯一标识
     * @return 包含昵称和头像URL的JSONObject
     */
    private JSONObject getWxUserInfo(String openid) {
        try {
            // 获取access_token
            Map<String, String> tokenParams = new HashMap<>();
            tokenParams.put("appid", weChatProperties.getAppid());
            tokenParams.put("secret", weChatProperties.getSecret());
            tokenParams.put("grant_type", "client_credential");

            String tokenJson = HttpClientUtil.doGet(WX_ACCESS_TOKEN, tokenParams);
            JSONObject tokenObj = JSON.parseObject(tokenJson);
            String accessToken = tokenObj.getString("access_token");

            if (accessToken == null) {
                return null;
            }

            // 获取用户信息
            Map<String, String> infoParams = new HashMap<>();
            infoParams.put("access_token", accessToken);
            infoParams.put("openid", openid);
            infoParams.put("lang", "zh_CN");

            String infoJson = HttpClientUtil.doGet(WX_USER_INFO, infoParams);
            return JSON.parseObject(infoJson);
        } catch (Exception e) {
            log.error("获取微信用户信息失败, openid: {}", openid, e);
            return null;
        }
    }

    /**
    * 获取微信用户的OpenID
    * @param userLoginDTO 包含微信登录所需参数的DTO对象
    * @return 微信用户的OpenID
     */
    private String getOpenid(UserLoginDTO userLoginDTO) {
        // 创建一个HashMap用于存储请求微信API所需的参数
        Map<String,String> map = new HashMap<>();
        // 添加微信小程序的AppID
        map.put("appid",weChatProperties.getAppid());
        // 添加微信小程序的AppSecret
        map.put("secret",weChatProperties.getSecret());
        // 添加前端传过来的登录凭证code
        map.put("js_code", userLoginDTO.getCode());
        // 添加授权类型，此值固定为authorization_code
        map.put("grant_type","authorization_code");

        // 发送HTTP GET请求到微信登录接口，获取返回的JSON数据
        String json = HttpClientUtil.doGet(WX_LOGIN,map);

        // 将返回的JSON字符串转换为JSONObject对象
        JSONObject jsonObject = JSON.parseObject(json);
        // 从JSON对象中获取openid
        String openid = jsonObject.getString("openid");
        // 返回获取到的openid
        return openid;
    }

    /**
     * 更新用户信息（头像、昵称等）
     * @param userDTO
     */
    @Override
    public void updateProfile(UserDTO userDTO) {
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        if (userDTO.getNickName() != null) {
            user.setName(userDTO.getNickName());
        }
        if (userDTO.getAvatarUrl() != null) {
            user.setAvatar(userDTO.getAvatarUrl());
        }
        if (userDTO.getGender() != null) {
            user.setSex(String.valueOf(userDTO.getGender()));
        }

        userMapper.update(user);
    }
}
