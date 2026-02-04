package com.flowhub.service;

import com.flowhub.dto.Result;
import com.flowhub.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    Result queryBlogById(Long id);

    void queryBlogUser(Blog blog);
    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);
    void isBlogLiked(Blog blog);
    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
