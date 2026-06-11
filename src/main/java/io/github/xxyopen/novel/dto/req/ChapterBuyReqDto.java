package io.github.xxyopen.novel.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 章节购买 请求DTO
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@Data
public class ChapterBuyReqDto {

    /**
     * 章节ID
     */
    @NotNull
    private Long chapterId;

}
