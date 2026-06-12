package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.dto.resp.BookContentAboutRespDto;
import io.github.xxyopen.novel.dto.resp.UserConsumeLogRespDto;

import java.util.List;

/**
 * 章节购买 服务接口
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
public interface ChapterPurchaseService {

    /**
     * 购买 VIP 章节
     *
     * @param userId    当前用户ID
     * @param chapterId 章节ID
     * @return 购买后的章节内容信息
     */
    RestResp<BookContentAboutRespDto> purchaseChapter(Long userId, Long chapterId);

    /**
     * 使用阅读券购买 VIP 章节
     *
     * @param userId    当前用户ID
     * @param chapterId 章节ID
     * @param voucherId 阅读券ID
     * @return 购买后的章节内容信息
     */
    RestResp<BookContentAboutRespDto> purchaseChapterWithVoucher(Long userId, Long chapterId, Long voucherId);

    /**
     * 获取章节内容（含 VIP 访问控制）
     * 如果章节是 VIP 且未购买/非限免，返回预览 + 购买提示
     * 如果是免费章节或已购买/限免，返回完整内容
     *
     * @param userId    当前用户ID（可为 null，未登录）
     * @param chapterId 章节ID
     * @return 章节内容信息
     */
    RestResp<BookContentAboutRespDto> getChapterContentWithAccessControl(Long userId, Long chapterId);

    /**
     * 退款
     *
     * @param userId       用户ID
     * @param consumeLogId 消费记录ID
     * @return 操作结果
     */
    RestResp<Void> refund(Long userId, Long consumeLogId);

    /**
     * 查询用户消费记录列表
     *
     * @param userId 用户ID
     * @param bookId 小说ID（null 表示查询全部）
     * @return 消费记录列表
     */
    RestResp<List<UserConsumeLogRespDto>> listConsumeLogs(Long userId, Long bookId);

}
