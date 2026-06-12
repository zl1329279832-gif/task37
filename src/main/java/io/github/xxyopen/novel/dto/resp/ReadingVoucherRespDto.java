package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读券 响应DTO
 */
@Data
@Builder
public class ReadingVoucherRespDto {

    private Long id;

    /**
     * 券类型;0-全免券 1-折扣券
     */
    private Integer voucherType;

    /**
     * 抵扣金额
     */
    private Integer discountAmount;

    /**
     * 状态;0-未使用 1-已使用 2-已过期
     */
    private Integer status;

    private LocalDateTime expireTime;

    /**
     * 使用的章节ID
     */
    private Long usedChapterId;

    private LocalDateTime usedTime;

}
