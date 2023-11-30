package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    UserMapper  userMapper;
    /**
     * 发送验证码
     * @param phone 手机号
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1、校验手机号 利用正则表达式工具类
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2、生成验证码 调用hutool工具类生成六位数随机验证码
        String code = RandomUtil.randomNumbers(6);
        //3、保存验证码到session
        session.setAttribute("code",code);
        //发送验证码 TODO 假设发送成功
        log.debug("发送短信验证码成功，验证码:{}",code);
        return Result.ok();
    }

    /**
     * 手机号登录
     * @param loginForm 封装前端传过来的phone和code以及password
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1、校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2、检验验证码
        Object Cachecode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (Cachecode == null || !Cachecode.toString().equals(code)){
            //不符合返回错误信息
            return Result.fail("验证码错误");
        }
        //3、判断用户是否存在 根据手机号查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, loginForm.getPhone()));
        if (user == null){
            //4、不存在 创建用户并保存
            user = new User();
            user.setPhone(loginForm.getPhone());
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            //利用hutool工具包生成随机字符串拼接昵称
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            userMapper.insert(user);
        }
        //5、保存用户信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        return Result.ok();
    }
}
