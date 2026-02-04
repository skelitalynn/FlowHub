package com.flowhub.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowhub.dto.LoginFormDTO;
import com.flowhub.dto.Result;
import com.flowhub.dto.UserDTO;
import com.flowhub.entity.Blog;
import com.flowhub.entity.User;
import com.flowhub.entity.UserInfo;
import com.flowhub.service.IBlogService;
import com.flowhub.service.IUserInfoService;
import com.flowhub.service.IUserService;
import com.flowhub.utils.RegexUtils;
import com.flowhub.utils.SystemConstants;
import com.flowhub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

import static com.flowhub.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        // TODO 实现登录功能
        return userService.login(loginForm);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token){
        if(StrUtil.isBlank(token)){
            return Result.ok();
        }
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        //获取当前登录的用户并返回
        UserDTO userDTO= UserHolder.getUser();
        return Result.ok(userDTO);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        User user=userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO= BeanUtil.copyProperties(user,UserDTO.class);
        return Result.ok(userDTO);
    }
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id){
        Page<Blog> page=blogService.query().eq("user_id",id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogList=page.getRecords();
        return Result.ok(blogList);
    }
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
