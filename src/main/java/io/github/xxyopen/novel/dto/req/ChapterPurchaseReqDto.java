package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 章节购买 请求DTO
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
public class ChapterPurchaseReqDto {

    /**
     * 章节ID
     */
    @NotNull
    private Long chapterId;

}
