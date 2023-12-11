package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lnc
 * @since 2023-12-11
 */
public interface IFollowService extends IService<Follow> {

    //是否关注接口
    Result isFollow(Long followUserId);

    //关注接口
    Result follow(Long followUserId, Boolean isFollow);

    //共同关注接口
    Result followCommons(Long id);
}
