package com.mallsen.mapper;

import com.mallsen.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查找用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User selectByOpenid(String openid);

    /**
     * 新建用户对象
     * @param user1
     */
    void insert(User user1);

    /**
     * 根据id查找用户
     * @param id
     * @return
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 根据条件查询用户数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 更新用户信息
     * @param user
     */
    void update(User user);

    /**
     * 按天分组统计新增用户数（报表用，一条 GROUP BY 代替逐日查询）
     *
     * @return [{date, cnt}]
     */
    List<Map<String, Object>> getNewUserByDate(@Param("begin") LocalDateTime begin,
                                               @Param("end") LocalDateTime end);
}
