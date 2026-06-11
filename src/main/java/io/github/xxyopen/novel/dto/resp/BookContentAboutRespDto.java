package io.github.xxyopen.novel.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * 小说内容相关 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/5/15
 */
@Data
@Builder
public class BookContentAboutRespDto {

    /**
     * 小说信息
     */
    private BookInfoRespDto bookInfo;

    /**
     * 章节信息
     */
    private BookChapterRespDto chapterInfo;

    /**
     * 章节内容
     */
    private String bookContent;

    /**
     * 是否VIP章节;1-是 0-否
     */
    private Integer isVip;

    /**
     * 是否已购买;1-已购买 0-未购买
     */
    private Integer isBought;

    /**
     * 章节价格;单位：屋币
     */
    private Integer chapterPrice;

}
