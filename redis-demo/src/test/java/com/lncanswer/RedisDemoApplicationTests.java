package com.lncanswer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lncanswer.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RedisDemoApplicationTests {

    @Autowired
    private RedisTemplate redisTemplate;

    private  static final ObjectMapper mapper = new ObjectMapper();
    @Test
    void contextLoads() {
        redisTemplate.opsForValue().set("name","zhangsan");
        Object name = redisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }

    @Test
    void  testObject() throws JsonProcessingException {
        User user1 = new User("lnc", 22);
        String json = mapper.writeValueAsString(user1);

        redisTemplate.opsForValue().set("user:12",json);

        Object o = redisTemplate.opsForValue().get("user:12");
        System.out.println("user = " + o);
    }



}
