package io.github.xxyopen.novel.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 作者收入明细 响应DTO
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@Data
@Builder
public class AuthorIncomeDetailRespDto {

    /**
     * 小说ID
     */
    private Long bookId;

    /**
     * 小说名称
     */
    private String bookName;

    /**
     * 收入日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate incomeDate;

    /**
     * 订阅总额
     */
    private Integer incomeAccount;

    /**
     * 订阅次数
     */
    private Integer incomeCount;

    /**
     * 订阅人数
     */
    private Integer incomeNumber;

}
