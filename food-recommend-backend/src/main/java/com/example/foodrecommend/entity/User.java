package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体（服务员/老板）
 */
@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    /** 角色：WAITER / OWNER */
    private String role;
    private String phone;
    /** 状态：1启用，0禁用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
