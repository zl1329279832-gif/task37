package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员购买 请求DTO
 */
@Data
public class MembershipPurchaseReqDto {

    /**
     * 会员等级;1-基础 2-高级 3-至尊
     */
    @NotNull
    private Integer membershipLevel;

}
