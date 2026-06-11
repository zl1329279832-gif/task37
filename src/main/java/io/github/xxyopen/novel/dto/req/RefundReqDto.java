package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 退款 请求DTO
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
public class RefundReqDto {

    /**
     * 消费记录ID
     */
    @NotNull
    private Long consumeLogId;

}
