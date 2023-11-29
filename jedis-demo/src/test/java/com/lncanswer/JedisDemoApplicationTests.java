package com.lncanswer;

import com.lncanswer.config.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;

@SpringBootTest
class JedisDemoApplicationTests {

    @Test
    void contextLoads() {
    }

    private Jedis jedis;

    @BeforeEach
    void setUp(){
        //建立连接
      //  jedis = new Jedis("192.168.101.129",6379);

        jedis = JedisConnectionFactory.getJedis();
        //设置密码
        jedis.auth("redis");
        jedis.select(0);
    }

    @Test
    public void testJedis(){
        //插入数据
       // String result = jedis.set("name", "张三");
        //System.out.println("result = " + result);

        //获取数据
        String name = jedis.get("name");
        System.out.println("name = " + name);

    }

    @AfterEach
    void tearDown(){
        //释放资源
        if (jedis != null){
            jedis.close();
        }
    }

}
