package io.github.xxyopen.novel.manager.redis;

import io.github.xxyopen.novel.core.constant.CacheConsts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 分布式锁管理器
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Component
@RequiredArgsConstructor
public class RedisDistributedLockManager {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取分布式锁
     *
     * @param key        锁键
     * @param value      锁值（UUID，防止其他线程误释放）
     * @param ttlSeconds 锁自动过期时间（秒）
     * @return true-获取成功 false-获取失败
     */
    public boolean tryLock(String key, String value, long ttlSeconds) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(CacheConsts.REDIS_CACHE_PREFIX + key, value,
                        Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放分布式锁（Lua 脚本原子操作，仅锁持有者可释放）
     *
     * @param key   锁键
     * @param value 锁值（必须与加锁时一致）
     */
    public void releaseLock(String key, String value) {
        String luaScript = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(luaScript, Long.class),
                List.of(CacheConsts.REDIS_CACHE_PREFIX + key),
                value
        );
    }

}
