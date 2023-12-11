package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lnc
 * @since 2023-12-11
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    /**
     * 是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        //1、查询是否关注
        LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<Follow>().
                eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId);
        //Follow follow = getOne(queryWrapper);
        int count = count(queryWrapper);

        return Result.ok(count > 0);
    }

    /**
     * 关注
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1、获取登录用户
        Long userId = UserHolder.getUser().getId();

        String key =FOLLOW_KEY + userId;
        //2、判断是否关注
        if (isFollow){
            //关注 添加数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean save = save(follow);
            if (save){
                //将当前用户关注的笔记博主加入到redis集合中
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //取关直接删除
            boolean remove = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId));
            if (remove){
                //从redis中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }

        }
        return Result.ok();
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1、查询登录用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;
        //2、求交集
        String key2 = FOLLOW_KEY  +id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3、解析id集合 转化成List
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4、查询用户 转化为list集合
        List<UserDTO> users = userService.listByIds(collect).stream().
                map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
