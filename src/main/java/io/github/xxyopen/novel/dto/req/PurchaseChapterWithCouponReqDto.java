package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 使用阅读券购买章节 请求DTO
 */
@Data
public class PurchaseChapterWithCouponReqDto {

    /** 章节ID */
    @NotNull(message = "章节ID不能为空")
    private Long chapterId;

    /** 阅读券ID（可选） */
    private Long couponId;

}
