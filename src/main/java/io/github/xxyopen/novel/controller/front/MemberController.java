package io.github.xxyopen.novel.controller.front;

import io.github.xxyopen.novel.core.auth.UserHolder;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.ApiRouterConsts;
import io.github.xxyopen.novel.dto.req.MemberPurchaseReqDto;
import io.github.xxyopen.novel.dto.req.PurchaseChapterWithCouponReqDto;
import io.github.xxyopen.novel.dto.resp.BookContentAboutRespDto;
import io.github.xxyopen.novel.dto.resp.MemberInfoRespDto;
import io.github.xxyopen.novel.dto.resp.ReadingCouponRespDto;
import io.github.xxyopen.novel.service.ChapterPurchaseService;
import io.github.xxyopen.novel.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台门户-VIP会员模块 API 控制器
 */
@RestController
@RequestMapping(ApiRouterConsts.API_FRONT_MEMBER_URL_PREFIX)
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    private final ChapterPurchaseService chapterPurchaseService;

    /**
     * 购买会员
     */
    @PostMapping("purchase")
    public RestResp<Void> purchaseMembership(@Valid @RequestBody MemberPurchaseReqDto dto) {
        return memberService.purchaseMembership(UserHolder.getUserId(), dto.getMemberLevel());
    }

    /**
     * 获取会员信息
     */
    @GetMapping("info")
    public RestResp<MemberInfoRespDto> getMemberInfo() {
        return memberService.getMemberInfo(UserHolder.getUserId());
    }

    /**
     * 获取可用阅读券列表
     */
    @GetMapping("coupons")
    public RestResp<List<ReadingCouponRespDto>> listCoupons() {
        return memberService.listAvailableCoupons(UserHolder.getUserId());
    }

    /**
     * 使用阅读券购买章节
     */
    @PostMapping("chapter/purchase_with_coupon")
    public RestResp<BookContentAboutRespDto> purchaseWithCoupon(
            @Valid @RequestBody PurchaseChapterWithCouponReqDto dto) {
        return chapterPurchaseService.purchaseChapterWithCoupon(
                UserHolder.getUserId(), dto.getChapterId(), dto.getCouponId());
    }

}
