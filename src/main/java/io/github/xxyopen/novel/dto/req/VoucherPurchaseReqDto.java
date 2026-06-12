package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 使用阅读券购买章节 请求DTO
 */
@Data
public class VoucherPurchaseReqDto {

    /**
     * 章节ID
     */
    @NotNull
    private Long chapterId;

    /**
     * 阅读券ID
     */
    @NotNull
    private Long voucherId;

}
