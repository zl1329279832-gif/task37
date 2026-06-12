package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员购买 请求DTO
 */
@Data
public class MemberPurchaseReqDto {

    /** 会员等级;1-月度 2-季度 3-年度 */
    @NotNull(message = "会员等级不能为空")
    private Integer memberLevel;

}
