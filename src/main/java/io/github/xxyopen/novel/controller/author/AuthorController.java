package io.github.xxyopen.novel.controller.author;

import io.github.xxyopen.novel.core.auth.UserHolder;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.ApiRouterConsts;
import io.github.xxyopen.novel.dto.req.AuthorRegisterReqDto;
import io.github.xxyopen.novel.dto.req.BookAddReqDto;
import io.github.xxyopen.novel.dto.req.ChapterAddReqDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeDetailRespDto;
import io.github.xxyopen.novel.dto.resp.AuthorIncomeRespDto;
import io.github.xxyopen.novel.service.AuthorService;
import io.github.xxyopen.novel.service.BookPayService;
import io.github.xxyopen.novel.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    private final BookPayService bookPayService;

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
     * 作者每日收入明细查询接口
     */
    @GetMapping("income/daily")
    public RestResp<List<AuthorIncomeDetailRespDto>> listDailyIncome(
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return authorService.listDailyIncome(UserHolder.getAuthorId(), bookId, startDate, endDate);
    }

    /**
     * 作者月度结算列表查询接口
     */
    @GetMapping("income/monthly")
    public RestResp<List<AuthorIncomeRespDto>> listMonthlyIncome() {
        return authorService.listMonthlyIncome(UserHolder.getAuthorId());
    }

    /**
     * 退款接口（管理用途）
     */
    @PostMapping("refund")
    public RestResp<Void> refundChapter(@RequestParam Long userId, @RequestParam Long chapterId) {
        return bookPayService.refundChapter(userId, chapterId);
    }

}
