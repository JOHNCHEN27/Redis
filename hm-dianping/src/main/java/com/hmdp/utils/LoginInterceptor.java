package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * @author LNC
 * @version 1.0
 * @description 拦截器实现登录校验
 * @date 2023/11/30 16:38
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1、获取session
//        HttpSession session = request.getSession();
//        //2、获取session中的用户
//        Object user = session.getAttribute("user");

       //此拦截器只需要判断是否拦截 判断ThreadLocal中是否有用户
        if (UserHolder.getUser() == null){
            //thread无用户 直接拦截
            response.setStatus(401);
            return false;
        }
        //有用户信息 放行
        return true;
    }

}
