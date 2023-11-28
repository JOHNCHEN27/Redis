##### **步骤1：拉取镜像** 

```
docker pull redis:6.2.7    拉取6.2.7版本的redis

docker pull redis       不指定默认为latest 最新版本
```



##### 步骤2:创建目录用来挂载redis容器数据和配置文件

```
mkdir -p /root/redis/data
mkdir -p /root/redis/redis.conf 
```

ps：创建的目录可以自己指定 所需要的配置文件按照官方的来，也可以自己更改内容

##### 步骤3：创建容器并运行：

```
docker run -p 6379:6379 --name redis -v /root/redis/data:/data -v /root/redis/redis.conf:/etc/redis/redis.conf
-d redis:6.2.7 redis-server /etc/redis/redis.conf --appendonly yes --requirepass redis
```

ps:redis-server 必须在指定镜像redis:6.2.7之后，不然报错找不到redis-server
conf文件中的requirepass密码和这里指定为一致

参数`-p 6379:6379`表示将Docker容器的6379端口映射到宿主机的6379端口。

参数`--name redis`表示给容器起个名字叫redis，方便管理。

参数`-v /root/redis/data:/data`表示将宿主机的/root/redis/data目录挂载到容器的/data目录上，这样可以实现数据持久化。

参数**-v /root/redis/redis.conf:/etc/redis/redis.conf** 表示将宿主机的/root/redis/redis.conf文件挂在到容器内/etc/redis/redis.conf文件上

-d redis:6.2.7后台持续运行 指定镜像为redis6.2.7

**redis-server /etc/redis/redis.conf**
以配置文件启动redis，加载容器内的conf文件，最终找到的是挂载的目录 /etc/redis/redis.conf
也就是liunx下的/root/redis/redis.conf

参数`redis-server --appendonly yes`表示开启Redis的数据持久化功能。

参数`requirepass redis`表示设置redis密码为redis。

##### 步骤4：设置redis开机自启：

```
docker update --restart=always redis
```


文章参考链接：https://blog.csdn.net/budaoweng0609/article/details/129004595
