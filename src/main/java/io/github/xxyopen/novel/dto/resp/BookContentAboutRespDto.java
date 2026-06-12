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
     * 是否需要购买
     */
    private Boolean needPurchase;

    /**
     * 章节价格（屋币）
     */
    private Integer chapterPrice;

    /**
     * 已购买
     */
    private Boolean isPurchased;

    /**
     * 限免中
     */
    private Boolean isFreeLimit;

    /**
     * 章节内容预览（前200字，仅在 needPurchase=true 时返回）
     */
    private String previewContent;

    /**
     * 是否会员免费阅读
     */
    private Boolean isMemberFreeRead;

    /**
     * 会员折扣价(屋币)
     */
    private Integer memberDiscountPrice;

}
