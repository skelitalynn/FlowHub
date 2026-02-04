package com.flowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.flowhub.dto.Result;
import com.flowhub.dto.ScrollResult;
import com.flowhub.dto.UserDTO;
import com.flowhub.entity.Blog;
import com.flowhub.entity.Follow;
import com.flowhub.mapper.BlogMapper;
import com.flowhub.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.flowhub.service.IFollowService;
import com.flowhub.service.IUserService;
import com.flowhub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.flowhub.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.flowhub.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService{
    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;

    @Resource
    private IFollowService followService;
    public BlogServiceImpl(StringRedisTemplate stringRedisTemplate, UserServiceImpl userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog=getById(id);
        if(blog!=null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    public void queryBlogUser(Blog blog) {
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if (BooleanUtil.isFalse(isMember)) {
            //3.如果未点赞，可以点赞
            //3.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2 保存用户到Redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            } else {
                isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            }
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key,0,4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<String> ids=top5.stream().map(String::valueOf).collect(Collectors.toList());
        String idStr= StrUtil.join(",",ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public void isBlogLiked(Blog blog) {
        UserDTO user= UserHolder.getUser();
        if(user==null){
            return;
        }
        Long userId = user.getId();
        String key = "blog:liked" + blog.getId();
        Double score=stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }
    @Override
    public Result saveBlog(Blog blog){
        //1.获取登陆用户
        UserDTO user=UserHolder.getUser();
        //2.保存探店笔记
        blog.setUserId(user.getId());
        boolean isSuccess=save(blog);
        if(isSuccess){
            return Result.fail("新增笔记失败！");
        }
        //3.查询笔记作者的所有粉丝
        List<Follow> follows=followService.query().eq("follower_user_id",user.getId()).list();
        // 4.推送笔记id给所有粉丝
        for(Follow follow:follows){
            Long userId = follow.getUserId();
            String key=FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());

        }
        // 4.1.获取粉丝id
        // 4.2.推送
        // 5.返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key,0,max,offset,2);
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for(ZSetOperations.TypedTuple<String> typedTuple:typedTuples){
            ids.add(Long.valueOf(typedTuple.getValue()));
            long time= typedTuple.getScore().longValue();
            if(time==minTime){
                os++;
            }else{
                minTime=time;
                os=1;
            }
        }
        // 5.根据id查询blog
        String idStr= StrUtil.join(",",ids);
        List<Blog> blogs=query().in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Blog blog:blogs){
            queryBlogUser(blog);
        }
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }
}
