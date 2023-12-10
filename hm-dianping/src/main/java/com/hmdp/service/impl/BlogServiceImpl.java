package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.events.Event;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    UserServiceImpl userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查看笔记、博客
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //1、查询博客
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("笔记不存在");
        }

        //2、查询blog有关的用户
        queryBlogUser(blog);
        //3、查询blog是否被点赞
        isBlokLiked(blog);

        return Result.ok(blog);
    }

    //查询blog是否被点赞
    private void isBlokLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            //用户未登录 无需查询是否点赞
            return;
        }
        //1、获取当前登录用户
        Long id = UserHolder.getUser().getId();
        //2、判断当前登录用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
      //Boolean isMenber = stringRedisTemplate.opsForSet().isMember(key, id.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, id.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 修改点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1、获取登录用户
        Long userId = UserHolder.getUser().getId();
        log.info("当前登录用户id：{}",userId);
        //2、判断当前用户是否已经点赞过
       // Boolean member = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if (score == null){
            //未点赞 可以点赞
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if (isSuccess){
                //点赞成功保存到redis
                //stringRedisTemplate.opsForSet().add(BLOG_LIKED_KEY + id,userId.toString());

                //zadd key value score
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,userId.toString(),System.currentTimeMillis());

            }
        }else {
            //已点赞取消点赞
            boolean isSuccess = update().setSql("liked = liked -1").eq("id", id).update();
            if (isSuccess){
                //stringRedisTemplate.opsForSet().remove(BLOG_LIKED_KEY + id,userId.toString());
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) { // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
       records.forEach(blog -> {
           this.isBlokLiked(blog);
           this.queryBlogUser(blog);
       });
        return Result.ok(records);
    }

    /**
     * 查询博客点赞
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1、查询top5的点赞用户 zrange key 0 4
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (range == null || range.isEmpty()){
            return  Result.ok(Collections.emptyList());
        }
        //2、解析其中的用户id
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //3、根据用户id查询用户
        List<UserDTO> userDtos = userService.query()
                .in("id",ids)
                .last("ORDER BY FIELD (id," + idStr + " )").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDtos);
    }

    //查询博客相关的用户
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

    }
}
