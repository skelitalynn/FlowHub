package com.flowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flowhub.dto.Result;
import com.flowhub.dto.UserDTO;
import com.flowhub.entity.Follow;
import com.flowhub.mapper.FollowMapper;
import com.flowhub.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flowhub.service.IUserService;
import com.flowhub.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId=UserHolder.getUser().getId();
        String key="follows:"+userId;
        if(isFollow){
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(userId);
            boolean isSuccess=save(follow);
            if(isSuccess){
                // 把关注用户的id，放入redis的set集合 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            // 3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess=remove(new QueryWrapper<Follow>().eq("user_id",userId).eq("follow_user_id",followUserId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId= UserHolder.getUser().getId();
        Integer count= query().eq("user_id",userId).eq("follow_user_id",followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId=UserHolder.getUser().getId();
        String key="follows:"+userId;
        String key2="follows:"+id;
        Set<String> intersect=stringRedisTemplate.opsForSet().intersect(key,key2);
        if(intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> ids=intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users=userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
