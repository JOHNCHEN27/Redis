package com.lncanswer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lncanswer.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author LNC
 * @version 1.0
 * @description StringRedisTemplate
 * @date 2023/11/30 13:33
 */
@SpringBootTest
public class StringRedisTemplateTest {

    //Spring提供的StringRedisTemplate对象可以实现key和vlue的String类型的序列化
    //从而节省redis存储空间
    @Autowired
    private  StringRedisTemplate stringRedisTemplate;

    //spring提供的对象JSON转换对象
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testStringRedisTemplate() throws JsonProcessingException {
        //创建对象
        User user = new User("zhangsan", 22);
        //手动序列化 将对象转化成JSON字符串
        String jsonUser = mapper.writeValueAsString(user);
        stringRedisTemplate.opsForValue().set("user:11",jsonUser);

        //获取数据
        String s = stringRedisTemplate.opsForValue().get("user:11");
        System.out.println("未反序列化的结果：" + s);
        //手动反序列化
        User user1 = mapper.readValue(s, User.class);
        System.out.println("user1 = " + user1);
    }
}
