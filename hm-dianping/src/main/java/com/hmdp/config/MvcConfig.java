package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author LNC
 * @version 1.0
 * @description Mvc配置 实现拦截器效果
 * @date 2023/11/30 18:11
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /**
     * 配置MVC拦截器
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code",
                                     "/user/login",
                                     "/shop/**",
                                     "/voucher/**",
                                     "/shop-type/**",
                                     "/upload/**",
                                     "/blog/hot"
                                     );
    }
}
