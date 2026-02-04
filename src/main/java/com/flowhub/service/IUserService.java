package com.flowhub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.flowhub.dto.LoginFormDTO;
import com.flowhub.dto.Result;
import com.flowhub.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User>{

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);

    Result sign();

    Result signCount();
}
