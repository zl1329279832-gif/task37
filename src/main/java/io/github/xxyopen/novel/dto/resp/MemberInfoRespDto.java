package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员信息 响应DTO
 */
@Data
@Builder
public class MemberInfoRespDto {

    /** 会员等级 */
    private Integer memberLevel;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 每月免费阅读配额 */
    private Integer freeReadQuota;

    /** 当月已使用 */
    private Integer usedFreeRead;

    /** 剩余免费阅读次数 */
    private Integer remainingFreeRead;

    /** 折扣率 */
    private Integer discountRate;

    /** 状态 */
    private Integer status;

}
