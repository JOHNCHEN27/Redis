package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
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

//        //3、保存验证码到session
//        session.setAttribute("code",code);

        //3、优化 -- 保存验证码到redis 并设置验证码的过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);


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
//        //2、检验验证码
//        Object Cachecode = session.getAttribute("code");
//        String code = loginForm.getCode();
//        if (Cachecode == null || !Cachecode.toString().equals(code)){
//            //不符合返回错误信息
//            return Result.fail("验证码错误");
//        }

        String phone = loginForm.getPhone();

        //2、从redis中校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)){
            //不一致报错
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

//        //5、保存用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

        //5、 保存用户信息到redis
        //5.1 随机生成token 作为登录令牌 TODO 后续可以优化 用JWT作登录令牌
        String token = UUID.randomUUID().toString();
        //5.2 将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将UserDto所有字段类型转化成String类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((filedName,filedValue)->
                            filedValue.toString()));
        //5.3 存储到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY +token,userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.SECONDS);

        return Result.ok(token);
    }

    /**
     * 退出登录
     * @returnt
     */
    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    /**
     * 签到功能
     * @return
     */
    @Override
    public Result sing() {
        //1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2、获取日期
        LocalDateTime now = LocalDateTime.now();
        //3、拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId+keySuffix;
        //4、获取今天是第几天
        int dayOfMonth = now.getDayOfMonth();
        //5、写入redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok();
    }

    /**
     * 统计月份连续签到次数
     * @return
     */
    @Override
    public Result signCount() {
        //1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2、获取日期
        LocalDateTime now = LocalDateTime.now();
        //3、拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4、获取今天是第几天
        int dayOfMonth = now.getDayOfMonth();
        //5、获取本月截止今天为止的所有签到记录 --返回一个十进制的数字 BITFIELD sign:5:202312 get u12 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).
                        valueAt(0));
        if (result == null || result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num ==0){
            return Result.ok(0);
        }
        //6、循环遍历
        int count = 0;
        while (true){
            //让当前这个数字和1 做运算 得到数字的最后一个bit位 //判断这个bit位是否为0
            if ((num &1) ==0){
                //为0 说明未签到
                break;
            }else {
                //不为0已签到 count++
                count++;
            }
            //把数字右移一位 抛弃最后一个bit位 继续对下一个bit位进行运算
            num >>>=1;
        }
        return Result.ok(count);
    }
}
