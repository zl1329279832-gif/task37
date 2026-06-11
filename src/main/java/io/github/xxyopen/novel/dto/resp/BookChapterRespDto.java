package io.github.xxyopen.novel.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 小说章节 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/5/15
 */
@Data
@Builder
public class BookChapterRespDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 章节ID
     * */
    private Long id;

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 章节号
     */
    private Integer chapterNum;

    /**
     * 章节名
     */
    private String chapterName;

    /**
     * 章节字数
     */
    private Integer chapterWordCount;

    /**
     * 是否收费;1-收费 0-免费
     */
    private Integer isVip;

    /**
     * 章节价格;单位：屋币
     */
    private Integer chapterPrice;

    /**
     * 是否限免;0-非限免 1-限免
     */
    private Integer isFree;

    /**
     * 章节更新时间
     */
    @JsonFormat(pattern = "yyyy/MM/dd HH:dd")
    private LocalDateTime chapterUpdateTime;

}
