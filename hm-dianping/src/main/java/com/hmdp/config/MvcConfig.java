package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author LNC
 * @version 1.0
 * @description Mvc配置 实现拦截器效果
 * @date 2023/11/30 18:11
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    //在此注入StringRedisTemplate
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 配置MVC拦截器
     * 一个拦截器拦截所有请求 目的是刷新令牌时间
     * 一个拦截器拦截部分请求 目的是防止用户未登录访问关键资源
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //拦截部分请求  登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code",
                                     "/user/login",
                                     "/shop/**",
                                     "/voucher/**",
                                     "/shop-type/**",
                                     "/upload/**",
                                     "/blog/hot"
                                     ).order(1);
        //order指定哪个拦截器先执行， order权值越大执行顺序越低
        //拦截所有请求 Token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
