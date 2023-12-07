package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author LNC
 * @version 1.0
 * @description redisson配置 建议手动配置不用spring整合yaml文件
 * @date 2023/12/7 15:43
 */
@Configuration
public class RedissonConfig {

    /**
     * redisson Bean配置
     * @return
     */
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        //设置密码
        config.useSingleServer().setAddress("redis://192.168.101.129:6379").setPassword("redis");
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
