package io.github.xxyopen.novel.dto;

import lombok.Data;

/**
 * 作家-作品收入对（内部 DTO，用于月度结算）
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@Data
public class AuthorBookIncomePair {

    private Long authorId;

    private Long bookId;

}
