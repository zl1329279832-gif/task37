package io.github.xxyopen.novel.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户消费记录 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
@Builder
public class UserConsumeLogRespDto {

    private Long id;

    /**
     * 消费的的商品ID（章节ID）
     */
    private Long productId;

    /**
     * 消费的商品名（章节名）
     */
    private String producName;

    /**
     * 消费金额（屋币）
     */
    private Integer amount;

    /**
     * 退款状态;0-正常 1-已退款
     */
    private Integer refundStatus;

    /**
     * 消费时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
