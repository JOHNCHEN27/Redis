package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author LNC
 * @version 1.0
 * @description 拦截器实现登录校验
 * @date 2023/11/30 16:38
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、获取session
        HttpSession session = request.getSession();
        //2、获取session中的用户
        Object user = session.getAttribute("user");
        //3、判断用户是否存在
        if (user == null){
            //不存在返回 401
            response.setStatus(401);
            return false;
        }

        //5、存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        //6、放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
