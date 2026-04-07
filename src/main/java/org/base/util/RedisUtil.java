package org.base.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisUtil {
    private static volatile JedisPool jedisPool;

    public static void init(String host, int port, int database) {
        if (jedisPool == null) {
            synchronized (RedisUtil.class) {
                if (jedisPool == null) {
                    // 1. Cấu hình Pool
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    poolConfig.setMaxTotal(128);
                    poolConfig.setMaxIdle(64);

                    int timeout = 2000;
                    jedisPool = new JedisPool(poolConfig, host, port, timeout, null, database);

                    System.out.println("RedisPool đã khởi tạo thành công ở Database: " + database);
                }
            }
        }
    }

    public static Jedis getConnection() {
        if (jedisPool == null) {
            throw new IllegalStateException("Redis chưa được khởi tạo. Hãy gọi RedisUtil.init() trước.");
        }
        return jedisPool.getResource();
    }

    public static void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}