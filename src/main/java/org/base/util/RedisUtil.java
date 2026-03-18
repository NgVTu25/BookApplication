package org.base.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisUtil {
    private static JedisPool jedisPool;

    public static void init(String host, int port) {
        if (jedisPool == null) {
            jedisPool = new JedisPool(host, port);
        }
    }

    public static Jedis getConnection() {
        if (jedisPool == null) {
            throw new IllegalStateException("Redis chưa được khởi tạo.");
        }
        return jedisPool.getResource();
    }

    public static void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
