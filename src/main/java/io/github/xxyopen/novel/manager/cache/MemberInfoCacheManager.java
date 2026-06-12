package io.github.xxyopen.novel.manager.cache;

import io.github.xxyopen.novel.core.constant.CacheConsts;
import io.github.xxyopen.novel.dao.entity.MemberInfo;
import io.github.xxyopen.novel.dao.mapper.MemberInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 会员信息 缓存管理类
 */
@Component
@RequiredArgsConstructor
public class MemberInfoCacheManager {

    private final MemberInfoMapper memberInfoMapper;

    /**
     * 查询用户有效会员信息，并放入缓存中
     */
    @Cacheable(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
            value = CacheConsts.MEMBER_INFO_CACHE_NAME, unless = "#result == null")
    public MemberInfo getActiveMemberInfo(Long userId) {
        return memberInfoMapper.selectActiveByUserId(userId);
    }

    /**
     * 清除会员信息缓存
     */
    @CacheEvict(cacheManager = CacheConsts.REDIS_CACHE_MANAGER,
            value = CacheConsts.MEMBER_INFO_CACHE_NAME)
    public void evictMemberInfo(Long userId) {
        // 调用此方法自动清除会员信息的缓存
    }

}
