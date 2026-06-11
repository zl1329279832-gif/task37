package io.github.xxyopen.novel.controller.author;

import io.github.xxyopen.novel.core.auth.UserHolder;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.ApiRouterConsts;
import io.github.xxyopen.novel.dto.req.AuthorIncomeDetailReqDto;
import io.github.xxyopen.novel.dto.req.AuthorRegisterReqDto;
import io.github.xxyopen.novel.dto.req.BookAddReqDto;
import io.github.xxyopen.novel.dto.req.ChapterAddReqDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeRespDto;
import io.github.xxyopen.novel.service.AuthorIncomeService;
import io.github.xxyopen.novel.service.AuthorService;
import io.github.xxyopen.novel.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 作家后台-作家模块 API 控制器
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@RestController
@RequestMapping(ApiRouterConsts.API_AUTHOR_URL_PREFIX)
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    private final BookService bookService;

    private final AuthorIncomeService authorIncomeService;

    /**
     * 作家注册接口
     */
    @PostMapping("register")
    public RestResp<Void> register(@Valid @RequestBody AuthorRegisterReqDto dto) {
        dto.setUserId(UserHolder.getUserId());
        return authorService.register(dto);
    }

    /**
     * 小说发布接口
     */
    @PostMapping("book")
    public RestResp<Void> publishBook(@Valid @RequestBody BookAddReqDto dto) {
        return bookService.saveBook(dto);
    }

    /**
     * 小说章节发布接口
     */
    @PostMapping("book/chapter")
    public RestResp<Void> publishBookChapter(@Valid @RequestBody ChapterAddReqDto dto) {
        return bookService.saveBookChapter(dto);
    }

    /**
     * 作家每日收入明细查询接口
     */
    @GetMapping("income/daily")
    public RestResp<List<AuthorIncomeDetailRespDto>> listDailyIncome(AuthorIncomeDetailReqDto dto) {
        return authorIncomeService.listDailyIncomeDetails(
                UserHolder.getAuthorId(),
                dto.getBookId(),
                dto.getStartDate() != null ? LocalDate.parse(dto.getStartDate()) : null,
                dto.getEndDate() != null ? LocalDate.parse(dto.getEndDate()) : null);
    }

    /**
     * 作家月度结算查询接口
     */
    @GetMapping("income/monthly")
    public RestResp<List<AuthorIncomeRespDto>> listMonthlySettlements(Integer year) {
        return authorIncomeService.listMonthlySettlements(UserHolder.getAuthorId(), year);
    }

    /**
     * 稿费确认接口
     */
    @PostMapping("income/confirm/{incomeId}")
    public RestResp<Void> confirmIncome(@PathVariable Long incomeId) {
        return authorIncomeService.confirmIncome(UserHolder.getAuthorId(), incomeId);
    }

}
