package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * 用户余额 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
@Builder
public class UserBalanceRespDto {

    /**
     * 账户余额（屋币）
     */
    private Long accountBalance;

}
