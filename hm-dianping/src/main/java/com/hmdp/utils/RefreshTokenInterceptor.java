package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author LNC
 * @version 1.0
 * @description 刷新令牌时间
 * @date 2023/12/1 19:47
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    //此类无法使用autowired 以及resource注解 因为此类没有被spring接管 是自己定义的过滤器
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1、获取session
//        HttpSession session = request.getSession();
//        //2、获取session中的用户
//        Object user = session.getAttribute("user");

        //1、 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StringUtils.isBlank(token)){
            return true;
        }
        //2、基于token获取redis用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        //3、判断用户是否存在
        if (userMap.isEmpty()){
            //不存在返回 401
           return true;
        }

//        //5、存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser((UserDTO) user);

        //5、 将查询到的hashs数据转化为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6、存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
        //7、刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        //8、放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
