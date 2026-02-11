package com.flowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flowhub.dto.LoginFormDTO;
import com.flowhub.dto.Result;
import com.flowhub.dto.UserDTO;
import com.flowhub.entity.User;
import com.flowhub.mapper.UserMapper;
import com.flowhub.service.IUserService;
import com.flowhub.utils.RegexUtils;
import com.flowhub.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static com.flowhub.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.flowhub.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.flowhub.utils.RedisConstants.LOGIN_USER_KEY;
import static com.flowhub.utils.RedisConstants.LOGIN_USER_TTL;
import static com.flowhub.utils.RedisConstants.USER_SIGN_KEY;
import static com.flowhub.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //发送验证码
    @Override
    public Result sendCode(String phone){
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.返回ok
        return Result.ok(code);
    }

    //登录
    @Override
    public Result login(LoginFormDTO loginForm){
        //1.校验手机号
        String phone = loginForm.getPhone();
        //2.校验验证码
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //3.从redis中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        //4.查询用户
        User user = query().eq("phone", phone).one();
        //5.不存在则创建用户
        if(user == null){
            user = createUserWithPhone(phone);
        }
        // 6.生成token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString())
        );
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
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
