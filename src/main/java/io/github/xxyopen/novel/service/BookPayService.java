package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;

import java.util.List;

/**
 * 付费章节订阅 服务类
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
public interface BookPayService {

    /**
     * 购买VIP章节
     *
     * @param userId    用户ID
     * @param chapterId 章节ID
     * @return void
     */
    RestResp<Void> buyChapter(Long userId, Long chapterId);

    /**
     * 退款（回滚消费、恢复余额、扣减作者收入）
     *
     * @param userId    用户ID
     * @param chapterId 章节ID
     * @return void
     */
    RestResp<Void> refundChapter(Long userId, Long chapterId);

    /**
     * 查询用户是否已购买某章节
     *
     * @param userId    用户ID
     * @param chapterId 章节ID
     * @return 0-未购买 1-已购买
     */
    RestResp<Integer> isChapterBought(Long userId, Long chapterId);

    /**
     * 查询某本书用户已购章节ID列表
     *
     * @param userId 用户ID
     * @param bookId 小说ID
     * @return 已购章节ID列表
     */
    RestResp<List<Long>> listBoughtChapterIds(Long userId, Long bookId);
}
