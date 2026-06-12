package io.github.xxyopen.novel.service;

import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.config.FinanceProperties;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.dto.resp.BookChapterRespDto;
import io.github.xxyopen.novel.dto.resp.BookContentAboutRespDto;
import io.github.xxyopen.novel.dto.resp.BookInfoRespDto;
import io.github.xxyopen.novel.manager.cache.BookChapterCacheManager;
import io.github.xxyopen.novel.manager.cache.BookContentCacheManager;
import io.github.xxyopen.novel.manager.cache.BookInfoCacheManager;
import io.github.xxyopen.novel.manager.cache.MemberInfoCacheManager;
import io.github.xxyopen.novel.manager.redis.RedisDistributedLockManager;
import io.github.xxyopen.novel.service.impl.ChapterPurchaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 增强限免切换测试
 * 场景：限免取消后会员免费读接管、会员配额访问控制、会员过期行为
 */
@ExtendWith(MockitoExtension.class)
class FreeLimitToggleEnhancedTest {

    @InjectMocks
    private ChapterPurchaseServiceImpl chapterPurchaseService;

    @Mock private BookChapterMapper bookChapterMapper;
    @Mock private BookInfoMapper bookInfoMapper;
    @Mock private BookContentMapper bookContentMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private UserConsumeLogMapper userConsumeLogMapper;
    @Mock private AuthorIncomeDetailMapper authorIncomeDetailMapper;
    @Mock private AuthorIncomeMapper authorIncomeMapper;
    @Mock private AuthorInfoMapper authorInfoMapper;
    @Mock private BookChapterCacheManager bookChapterCacheManager;
    @Mock private BookInfoCacheManager bookInfoCacheManager;
    @Mock private BookContentCacheManager bookContentCacheManager;
    @Mock private RedisDistributedLockManager lockManager;
    @Mock private MemberInfoCacheManager memberInfoCacheManager;
    @Mock private MemberInfoMapper memberInfoMapper;
    @Mock private ReadingCouponMapper readingCouponMapper;
    @Mock private MemberBenefitsSnapshotMapper memberBenefitsSnapshotMapper;
    @Mock private SettlementService settlementService;
    @Mock private RefundFreezeMapper refundFreezeMapper;

    private final Long userId = 1L;
    private final Long chapterId = 100L;
    private final Long bookId = 10L;

    @BeforeEach
    void setUp() throws Exception {
        FinanceProperties props = new FinanceProperties();
        props.setDefaultChapterPrice(10);
        var field = ChapterPurchaseServiceImpl.class.getDeclaredField("financeProperties");
        field.setAccessible(true);
        field.set(chapterPurchaseService, props);

        lenient().when(bookChapterCacheManager.getChapter(chapterId)).thenReturn(
                BookChapterRespDto.builder().id(chapterId).bookId(bookId).chapterName("VIP章节").build());
        lenient().when(bookInfoCacheManager.getBookInfo(bookId)).thenReturn(
                BookInfoRespDto.builder().id(bookId).bookName("测试小说").build());
        lenient().when(bookContentCacheManager.getBookContent(chapterId)).thenReturn("VIP章节的完整内容这是一段测试文本");
    }

    private BookChapter buildVipChapter() {
        BookChapter ch = new BookChapter();
        ch.setId(chapterId);
        ch.setBookId(bookId);
        ch.setIsVip(1);
        ch.setIsFreeLimit(0);
        ch.setChapterPrice(10);
        ch.setChapterName("VIP章节");
        return ch;
    }

    @Test
    void testFreeLimitToggled_memberFallback() {
        // 章节限免已关闭，但用户是会员有配额 → 应返回会员标记的完整内容
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(0);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        // 用户未购买
        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        // 会员有配额
        MemberInfo member = new MemberInfo();
        member.setUserId(userId);
        member.setMemberLevel(2);
        member.setExpireTime(LocalDateTime.now().plusDays(30));
        member.setFreeReadQuota(30);
        member.setUsedFreeRead(5);
        member.setDiscountRate(80);
        member.setStatus(0);
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(member);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        assertNotNull(result.getData().getIsMemberFreeRead());
        assertTrue(result.getData().getIsMemberFreeRead());
        assertNotNull(result.getData().getBookContent());
    }

    @Test
    void testAccessControl_memberQuota() {
        // VIP章节，未购买，但有会员配额
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        MemberInfo member = new MemberInfo();
        member.setUserId(userId);
        member.setFreeReadQuota(30);
        member.setUsedFreeRead(10);
        member.setStatus(0);
        member.setExpireTime(LocalDateTime.now().plusDays(10));
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(member);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        // 会员有配额时返回完整内容
        assertFalse(result.getData().getNeedPurchase());
    }

    @Test
    void testAccessControl_memberExpired() {
        // VIP章节，未购买，会员已过期
        BookChapter chapter = buildVipChapter();
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookId);
        bookInfo.setIsFreeLimit(0);
        when(bookInfoMapper.selectById(bookId)).thenReturn(bookInfo);

        when(userConsumeLogMapper.selectCount(any())).thenReturn(0L);

        // 会员缓存返回null（已过期）
        when(memberInfoCacheManager.getActiveMemberInfo(userId)).thenReturn(null);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        // 非会员应返回预览
        assertTrue(result.getData().getNeedPurchase());
        assertNotNull(result.getData().getPreviewContent());
    }

    @Test
    void testAccessControl_freeLimitOverridesMember() {
        // 限免优先于会员检查
        BookChapter chapter = buildVipChapter();
        chapter.setIsFreeLimit(1);
        when(bookChapterMapper.selectById(chapterId)).thenReturn(chapter);

        RestResp<BookContentAboutRespDto> result =
                chapterPurchaseService.getChapterContentWithAccessControl(userId, chapterId);

        assertTrue(result.isOk());
        // 限免直接返回，不检查会员
        assertTrue(result.getData().getIsFreeLimit());
        verify(memberInfoCacheManager, never()).getActiveMemberInfo(anyLong());
    }

}
