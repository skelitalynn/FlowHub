package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result sendCode(String phone, HttpSession session){
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.不符合返回错误
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4，保存验证码到session
        session.setAttribute("code",code);
        // 5.发送验证码
        log.debug(code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        // 1.校验手机号
        String phone = loginForm.getPhone();
        // 2.如果不符合，返回错误信息
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        // 3.校验验证码
        Object cacheCode=session.getAttribute("code");//缓存的code
        String code=loginForm.getCode();//前端的code
        //3.不一致，报错
        if(cacheCode==null||!cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }
        //一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在,不存在，则创建
        if(user==null){
            user=createUserWithPhone(phone);
        }
        //7.保存用户信息到session中
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        return Result.ok();
    }

    @Override
    public Result sign() {
        Long userId= UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId= UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId+keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num=result.get(0);
        if(num==null||num==0){
            return Result.ok(0);
        }
        int count=0;
        while (true){
            //让这个数字与1做与运算。得到数字的最后一个bit位
            //判断这个bit位是否为0
            if((num&1)==0){
                break;
            }else{count++;}
            num>>>=1;
        }

        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }

}
