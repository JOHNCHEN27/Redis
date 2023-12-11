package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author lnc
 * @since 2023-12-11
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService iFollowService;

    /**
     * 关注用户接口
     * @param followUserId 关注用户id
     * @param isFollow 是否关注
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow (@PathVariable("id") Long followUserId, @PathVariable("isFollow")Boolean isFollow){

        return iFollowService.follow(followUserId,isFollow);
    }

    /**
     * 是否关注接口
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable("id") Long followUserId){

        return iFollowService.isFollow(followUserId);
    }

    /**
     * 共同关注接口
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable ("id") Long id){
        return iFollowService.followCommons(id);
    }
}
