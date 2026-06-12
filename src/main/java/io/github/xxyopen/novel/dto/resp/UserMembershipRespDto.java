package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户会员信息 响应DTO
 */
@Data
@Builder
public class UserMembershipRespDto {

    private Long id;

    /**
     * 会员等级;1-基础 2-高级 3-至尊
     */
    private Integer membershipLevel;

    /**
     * 会员等级名称
     */
    private String membershipLevelName;

    private LocalDateTime startTime;

    private LocalDateTime expireTime;

    /**
     * 每月免费章节配额
     */
    private Integer freeChapterQuota;

    /**
     * 本月已用免费章节数
     */
    private Integer freeChapterUsed;

    /**
     * 折扣比例
     */
    private Integer discountRate;

    /**
     * 状态;0-生效中 1-已过期
     */
    private Integer status;

}
