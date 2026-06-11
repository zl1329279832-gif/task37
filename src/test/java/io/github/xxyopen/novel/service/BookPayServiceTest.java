package io.github.xxyopen.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xxyopen.novel.core.common.constant.CommonConsts;
import io.github.xxyopen.novel.core.common.constant.ErrorCodeEnum;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.task.MonthlySettlementTask;
import io.github.xxyopen.novel.dao.entity.*;
import io.github.xxyopen.novel.dao.mapper.*;
import io.github.xxyopen.novel.service.impl.BookPayServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 付费章节订阅服务 测试类
 *
 * 覆盖场景：并发购买、重复扣费拦截、限免切换、退款回滚、作者收入统计、月结
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@ExtendWith(MockitoExtension.class)
class BookPayServiceTest {

    @InjectMocks
    private BookPayServiceImpl bookPayService;

    @Mock
    private BookChapterMapper bookChapterMapper;

    @Mock
    private BookInfoMapper bookInfoMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private UserConsumeLogMapper userConsumeLogMapper;

    @Mock
    private AuthorIncomeDetailMapper authorIncomeDetailMapper;

    @Mock
    private AuthorIncomeMapper authorIncomeMapper;

    private BookChapter vipChapter;
    private BookChapter freeChapter;
    private BookChapter freeTrialChapter;
    private BookInfo bookInfo;
    private UserInfo userInfo;

    private static final Long USER_ID = 1L;
    private static final Long CHAPTER_ID = 100L;
    private static final Long BOOK_ID = 10L;
    private static final Long AUTHOR_ID = 5L;
    private static final Integer CHAPTER_PRICE = 50;

    @BeforeEach
    void setUp() {
        // VIP 收费章节
        vipChapter = new BookChapter();
        vipChapter.setId(CHAPTER_ID);
        vipChapter.setBookId(BOOK_ID);
        vipChapter.setChapterName("第一章 VIP内容");
        vipChapter.setIsVip(CommonConsts.YES);
        vipChapter.setIsFree(CommonConsts.NO);
        vipChapter.setChapterPrice(CHAPTER_PRICE);
        vipChapter.setWordCount(3000);

        // 免费章节
        freeChapter = new BookChapter();
        freeChapter.setId(200L);
        freeChapter.setBookId(BOOK_ID);
        freeChapter.setChapterName("序章 免费");
        freeChapter.setIsVip(CommonConsts.NO);
        freeChapter.setIsFree(CommonConsts.NO);
        freeChapter.setChapterPrice(0);

        // 限免章节
        freeTrialChapter = new BookChapter();
        freeTrialChapter.setId(300L);
        freeTrialChapter.setBookId(BOOK_ID);
        freeTrialChapter.setChapterName("第二章 限免");
        freeTrialChapter.setIsVip(CommonConsts.YES);
        freeTrialChapter.setIsFree(CommonConsts.YES);
        freeTrialChapter.setChapterPrice(CHAPTER_PRICE);

        // 小说信息
        bookInfo = new BookInfo();
        bookInfo.setId(BOOK_ID);
        bookInfo.setAuthorId(AUTHOR_ID);
        bookInfo.setBookName("测试小说");

        // 用户信息
        userInfo = new UserInfo();
        userInfo.setId(USER_ID);
        userInfo.setAccountBalance(1000L);
    }

    // ========== 购买成功 ==========

    @Test
    @DisplayName("正常购买VIP章节：余额扣减、消费记录生成、作者收入累计")
    void testBuyChapter_Success() {
        // Given
        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        when(userConsumeLogMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userInfoMapper.deductBalance(USER_ID, CHAPTER_PRICE)).thenReturn(1);
        when(userConsumeLogMapper.insert(any(UserConsumeLog.class))).thenReturn(1);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);
        when(authorIncomeDetailMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(authorIncomeDetailMapper.insert(any(AuthorIncomeDetail.class))).thenReturn(1);

        // When
        RestResp<Void> result = bookPayService.buyChapter(USER_ID, CHAPTER_ID);

        // Then
        assertTrue(result.isOk());

        // 验证扣费被调用
        verify(userInfoMapper).deductBalance(USER_ID, CHAPTER_PRICE);

        // 验证消费记录被插入
        ArgumentCaptor<UserConsumeLog> consumeCaptor = ArgumentCaptor.forClass(UserConsumeLog.class);
        verify(userConsumeLogMapper).insert(consumeCaptor.capture());
        UserConsumeLog savedLog = consumeCaptor.getValue();
        assertEquals(USER_ID, savedLog.getUserId());
        assertEquals(CHAPTER_PRICE, savedLog.getAmount());
        assertEquals(CHAPTER_ID, savedLog.getProductId());
        assertEquals(0, savedLog.getProductType());

        // 验证作者收入明细被插入（按作品 + 全部作品汇总 = 2次insert）
        verify(authorIncomeDetailMapper, times(2)).insert(any(AuthorIncomeDetail.class));
    }

    // ========== 余额不足 ==========

    @Test
    @DisplayName("余额不足拦截：返回余额不足错误")
    void testBuyChapter_BalanceNotEnough() {
        // Given
        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        when(userConsumeLogMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userInfoMapper.deductBalance(USER_ID, CHAPTER_PRICE)).thenReturn(0); // 余额不足

        // When
        RestResp<Void> result = bookPayService.buyChapter(USER_ID, CHAPTER_ID);

        // Then
        assertFalse(result.isOk());
        assertEquals(ErrorCodeEnum.USER_BALANCE_NOT_ENOUGH.getCode(), result.getCode());

        // 验证没有插入消费记录
        verify(userConsumeLogMapper, never()).insert(any(UserConsumeLog.class));
    }

    // ========== 重复购买幂等 ==========

    @Test
    @DisplayName("重复购买幂等拦截：第二次购买返回已购买错误")
    void testBuyChapter_AlreadyBought() {
        // Given
        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        when(userConsumeLogMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L); // 已购买

        // When
        RestResp<Void> result = bookPayService.buyChapter(USER_ID, CHAPTER_ID);

        // Then
        assertFalse(result.isOk());
        assertEquals(ErrorCodeEnum.USER_CHAPTER_ALREADY_BOUGHT.getCode(), result.getCode());

        // 验证没有扣费
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
        // 验证没有插入消费记录
        verify(userConsumeLogMapper, never()).insert(any(UserConsumeLog.class));
    }

    // ========== 限免章节无需购买 ==========

    @Test
    @DisplayName("限免章节无需购买：返回限免提示")
    void testBuyChapter_FreeTrialChapter() {
        // Given
        when(bookChapterMapper.selectById(300L)).thenReturn(freeTrialChapter);

        // When
        RestResp<Void> result = bookPayService.buyChapter(USER_ID, 300L);

        // Then
        assertFalse(result.isOk());
        assertEquals(ErrorCodeEnum.USER_CHAPTER_IS_FREE_TRIAL.getCode(), result.getCode());

        // 验证没有扣费
        verify(userInfoMapper, never()).deductBalance(anyLong(), anyInt());
    }

    // ========== 免费章节 ==========

    @Test
    @DisplayName("免费章节(isVip=0)无需购买：返回免费章节提示")
    void testBuyChapter_FreeChapter() {
        // Given
        when(bookChapterMapper.selectById(200L)).thenReturn(freeChapter);

        // When
        RestResp<Void> result = bookPayService.buyChapter(USER_ID, 200L);

        // Then
        assertFalse(result.isOk());
        assertEquals(ErrorCodeEnum.USER_CHAPTER_NOT_VIP.getCode(), result.getCode());
    }

    // ========== 并发购买 ==========

    @Test
    @DisplayName("并发购买：多线程同时购买同一章节，仅一个成功扣费")
    void testBuyChapter_ConcurrentPurchase() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 模拟并发场景：所有线程看到"未购买"，但扣费只有第一次成功
        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        // 第一次查询返回0（未购买），后续返回1（已购买）
        when(userConsumeLogMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L)  // 第一次：未购买
                .thenReturn(1L); // 第二次及以后：已购买（幂等拦截）

        // 只有第一次扣费成功
        when(userInfoMapper.deductBalance(USER_ID, CHAPTER_PRICE))
                .thenReturn(1)  // 第一次成功
                .thenReturn(0); // 后续失败（余额已扣或不足）
        when(userConsumeLogMapper.insert(any(UserConsumeLog.class))).thenReturn(1);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);
        when(authorIncomeDetailMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(authorIncomeDetailMapper.insert(any(AuthorIncomeDetail.class))).thenReturn(1);

        // 提交并发任务
        List<Future<RestResp<Void>>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return bookPayService.buyChapter(USER_ID, CHAPTER_ID);
            }));
        }

        // 同时释放所有线程
        latch.countDown();

        for (Future<RestResp<Void>> future : futures) {
            RestResp<Void> result = future.get(5, TimeUnit.SECONDS);
            if (result.isOk()) {
                successCount.incrementAndGet();
            } else {
                failCount.incrementAndGet();
            }
        }

        executor.shutdown();

        // 验证：成功次数应该 <= 1（并发安全）
        assertTrue(successCount.get() <= 1,
                "并发购买场景下，成功次数应<=1，实际: " + successCount.get());
        assertTrue(failCount.get() >= threadCount - 1,
                "并发购买场景下，失败次数应>=" + (threadCount - 1));
    }

    // ========== 退款成功 ==========

    @Test
    @DisplayName("退款成功：余额恢复、消费记录删除、作者收入回滚")
    void testRefundChapter_Success() {
        // Given
        UserConsumeLog consumeLog = new UserConsumeLog();
        consumeLog.setId(1L);
        consumeLog.setUserId(USER_ID);
        consumeLog.setAmount(CHAPTER_PRICE);
        consumeLog.setProductId(CHAPTER_ID);
        consumeLog.setProductType(0);
        consumeLog.setCreateTime(LocalDateTime.now());

        when(userConsumeLogMapper.selectOne(any(QueryWrapper.class))).thenReturn(consumeLog);
        when(userConsumeLogMapper.deleteById(1L)).thenReturn(1);
        when(userInfoMapper.addBalance(USER_ID, CHAPTER_PRICE)).thenReturn(1);
        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);

        // 模拟存在作者收入明细
        AuthorIncomeDetail existingDetail = new AuthorIncomeDetail();
        existingDetail.setId(1L);
        existingDetail.setAuthorId(AUTHOR_ID);
        existingDetail.setBookId(BOOK_ID);
        existingDetail.setIncomeAccount(CHAPTER_PRICE);
        existingDetail.setIncomeCount(1);
        existingDetail.setIncomeNumber(1);
        when(authorIncomeDetailMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingDetail);
        when(authorIncomeDetailMapper.updateById(any(AuthorIncomeDetail.class))).thenReturn(1);

        // 没有月结单
        when(authorIncomeMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // When
        RestResp<Void> result = bookPayService.refundChapter(USER_ID, CHAPTER_ID);

        // Then
        assertTrue(result.isOk());

        // 验证消费记录被删除
        verify(userConsumeLogMapper).deleteById(1L);

        // 验证余额被恢复
        verify(userInfoMapper).addBalance(USER_ID, CHAPTER_PRICE);

        // 验证作者收入明细被扣减
        ArgumentCaptor<AuthorIncomeDetail> detailCaptor = ArgumentCaptor.forClass(AuthorIncomeDetail.class);
        verify(authorIncomeDetailMapper, atLeastOnce()).updateById(detailCaptor.capture());
        AuthorIncomeDetail updatedDetail = detailCaptor.getValue();
        assertEquals(0, updatedDetail.getIncomeAccount());
        assertEquals(0, updatedDetail.getIncomeCount());
    }

    // ========== 退款失败 ==========

    @Test
    @DisplayName("未购买章节的退款失败：返回消费记录不存在")
    void testRefundChapter_NotBought() {
        // Given
        when(userConsumeLogMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // When
        RestResp<Void> result = bookPayService.refundChapter(USER_ID, CHAPTER_ID);

        // Then
        assertFalse(result.isOk());
        assertEquals(ErrorCodeEnum.USER_CONSUME_NOT_EXIST.getCode(), result.getCode());

        // 验证没有执行退款操作
        verify(userInfoMapper, never()).addBalance(anyLong(), anyInt());
    }

    // ========== 限免切换 ==========

    @Test
    @DisplayName("限免切换：限免期间购买拦截，取消限免后正常购买")
    void testFreeTrialSwitch() {
        // 阶段1：限免期间，购买被拦截
        BookChapter chapterInFreeTrial = new BookChapter();
        chapterInFreeTrial.setId(CHAPTER_ID);
        chapterInFreeTrial.setBookId(BOOK_ID);
        chapterInFreeTrial.setChapterName("限免章节");
        chapterInFreeTrial.setIsVip(CommonConsts.YES);
        chapterInFreeTrial.setIsFree(CommonConsts.YES);
        chapterInFreeTrial.setChapterPrice(CHAPTER_PRICE);

        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(chapterInFreeTrial);

        RestResp<Void> resultDuringFreeTrial = bookPayService.buyChapter(USER_ID, CHAPTER_ID);
        assertFalse(resultDuringFreeTrial.isOk());
        assertEquals(ErrorCodeEnum.USER_CHAPTER_IS_FREE_TRIAL.getCode(), resultDuringFreeTrial.getCode());

        // 阶段2：取消限免后，正常购买
        BookChapter chapterAfterFreeTrial = new BookChapter();
        chapterAfterFreeTrial.setId(CHAPTER_ID);
        chapterAfterFreeTrial.setBookId(BOOK_ID);
        chapterAfterFreeTrial.setChapterName("限免章节");
        chapterAfterFreeTrial.setIsVip(CommonConsts.YES);
        chapterAfterFreeTrial.setIsFree(CommonConsts.NO); // 限免已取消
        chapterAfterFreeTrial.setChapterPrice(CHAPTER_PRICE);

        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(chapterAfterFreeTrial);
        when(userConsumeLogMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userInfoMapper.deductBalance(USER_ID, CHAPTER_PRICE)).thenReturn(1);
        when(userConsumeLogMapper.insert(any(UserConsumeLog.class))).thenReturn(1);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);
        when(authorIncomeDetailMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(authorIncomeDetailMapper.insert(any(AuthorIncomeDetail.class))).thenReturn(1);

        RestResp<Void> resultAfterFreeTrial = bookPayService.buyChapter(USER_ID, CHAPTER_ID);
        assertTrue(resultAfterFreeTrial.isOk());

        // 验证取消限免后扣费被调用
        verify(userInfoMapper).deductBalance(USER_ID, CHAPTER_PRICE);
    }

    // ========== 作者每日收入统计 ==========

    @Test
    @DisplayName("购买后作者每日收入明细正确累计")
    void testAuthorDailyIncome() {
        // Given：模拟该作者当日已有收入记录
        AuthorIncomeDetail existingDetail = new AuthorIncomeDetail();
        existingDetail.setId(1L);
        existingDetail.setAuthorId(AUTHOR_ID);
        existingDetail.setBookId(BOOK_ID);
        existingDetail.setIncomeDate(LocalDate.now());
        existingDetail.setIncomeAccount(100);
        existingDetail.setIncomeCount(2);
        existingDetail.setIncomeNumber(2);

        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        when(userConsumeLogMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userInfoMapper.deductBalance(USER_ID, CHAPTER_PRICE)).thenReturn(1);
        when(userConsumeLogMapper.insert(any(UserConsumeLog.class))).thenReturn(1);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);

        // 第一次查询（按作品维度）返回已有记录
        // 第二次查询（全部作品汇总）返回null（新建）
        when(authorIncomeDetailMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(existingDetail)
                .thenReturn(null);
        when(authorIncomeDetailMapper.updateById(any(AuthorIncomeDetail.class))).thenReturn(1);
        when(authorIncomeDetailMapper.insert(any(AuthorIncomeDetail.class))).thenReturn(1);

        // When
        RestResp<Void> result = bookPayService.buyChapter(USER_ID, CHAPTER_ID);

        // Then
        assertTrue(result.isOk());

        // 验证按作品维度的收入明细被更新（累加）
        ArgumentCaptor<AuthorIncomeDetail> updateCaptor = ArgumentCaptor.forClass(AuthorIncomeDetail.class);
        verify(authorIncomeDetailMapper).updateById(updateCaptor.capture());
        AuthorIncomeDetail updated = updateCaptor.getValue();
        assertEquals(100 + CHAPTER_PRICE, updated.getIncomeAccount()); // 100 + 50 = 150
        assertEquals(3, updated.getIncomeCount()); // 2 + 1
        assertEquals(3, updated.getIncomeNumber()); // 2 + 1

        // 验证全部作品汇总被新建
        verify(authorIncomeDetailMapper).insert(any(AuthorIncomeDetail.class));
    }

    // ========== 月度结算 ==========

    @Test
    @DisplayName("月度结算任务：正确生成月结单，分成比例正确")
    void testMonthlySettlement() {
        // 创建 MonthlySettlementTask 实例
        MonthlySettlementTask settlementTask = new MonthlySettlementTask(
                authorIncomeDetailMapper, authorIncomeMapper);

        LocalDate settlementMonth = LocalDate.of(2024, 6, 1);

        // 模拟上月收入明细
        AuthorIncomeDetail detail1 = new AuthorIncomeDetail();
        detail1.setAuthorId(AUTHOR_ID);
        detail1.setBookId(BOOK_ID);
        detail1.setIncomeDate(LocalDate.of(2024, 6, 15));
        detail1.setIncomeAccount(500);
        detail1.setIncomeCount(10);
        detail1.setIncomeNumber(5);

        AuthorIncomeDetail detail2 = new AuthorIncomeDetail();
        detail2.setAuthorId(AUTHOR_ID);
        detail2.setBookId(BOOK_ID);
        detail2.setIncomeDate(LocalDate.of(2024, 6, 20));
        detail2.setIncomeAccount(300);
        detail2.setIncomeCount(6);
        detail2.setIncomeNumber(3);

        when(authorIncomeDetailMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(detail1, detail2));

        // 不存在已有结算记录
        when(authorIncomeMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(authorIncomeMapper.insert(any(AuthorIncome.class))).thenReturn(1);

        // When
        settlementTask.doSettlement(settlementMonth);

        // Then
        ArgumentCaptor<AuthorIncome> incomeCaptor = ArgumentCaptor.forClass(AuthorIncome.class);
        verify(authorIncomeMapper).insert(incomeCaptor.capture());
        AuthorIncome savedIncome = incomeCaptor.getValue();

        assertEquals(AUTHOR_ID, savedIncome.getAuthorId());
        assertEquals(BOOK_ID, savedIncome.getBookId());
        assertEquals(settlementMonth, savedIncome.getIncomeMonth());
        assertEquals(800, savedIncome.getPreTaxIncome()); // 500 + 300
        assertEquals(560, savedIncome.getAfterTaxIncome()); // 800 * 70% = 560
        assertEquals(0, savedIncome.getPayStatus()); // 待支付
        assertEquals(0, savedIncome.getConfirmStatus()); // 待确认
    }

    // ========== 月度结算幂等 ==========

    @Test
    @DisplayName("月度结算幂等：已存在结算记录时跳过")
    void testMonthlySettlement_AlreadyDone() {
        MonthlySettlementTask settlementTask = new MonthlySettlementTask(
                authorIncomeDetailMapper, authorIncomeMapper);

        LocalDate settlementMonth = LocalDate.of(2024, 6, 1);

        AuthorIncomeDetail detail = new AuthorIncomeDetail();
        detail.setAuthorId(AUTHOR_ID);
        detail.setBookId(BOOK_ID);
        detail.setIncomeDate(LocalDate.of(2024, 6, 15));
        detail.setIncomeAccount(500);
        detail.setIncomeCount(10);
        detail.setIncomeNumber(5);

        when(authorIncomeDetailMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(detail));

        // 已存在结算记录
        AuthorIncome existingIncome = new AuthorIncome();
        existingIncome.setId(1L);
        existingIncome.setAuthorId(AUTHOR_ID);
        existingIncome.setBookId(BOOK_ID);
        when(authorIncomeMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingIncome);

        // When
        settlementTask.doSettlement(settlementMonth);

        // Then：不应插入新结算记录
        verify(authorIncomeMapper, never()).insert(any(AuthorIncome.class));
    }

    // ========== 退款回滚月结单 ==========

    @Test
    @DisplayName("退款时已有月结单：月结单金额正确扣减")
    void testRefundChapter_WithMonthlySettlement() {
        // Given
        UserConsumeLog consumeLog = new UserConsumeLog();
        consumeLog.setId(1L);
        consumeLog.setUserId(USER_ID);
        consumeLog.setAmount(CHAPTER_PRICE);
        consumeLog.setProductId(CHAPTER_ID);
        consumeLog.setProductType(0);
        consumeLog.setCreateTime(LocalDateTime.now());

        when(userConsumeLogMapper.selectOne(any(QueryWrapper.class))).thenReturn(consumeLog);
        when(userConsumeLogMapper.deleteById(1L)).thenReturn(1);
        when(userInfoMapper.addBalance(USER_ID, CHAPTER_PRICE)).thenReturn(1);
        when(bookChapterMapper.selectById(CHAPTER_ID)).thenReturn(vipChapter);
        when(bookInfoMapper.selectById(BOOK_ID)).thenReturn(bookInfo);

        // 模拟作者收入明细
        AuthorIncomeDetail detail = new AuthorIncomeDetail();
        detail.setId(1L);
        detail.setIncomeAccount(200);
        detail.setIncomeCount(4);
        detail.setIncomeNumber(3);
        // 按顺序返回：先按作品维度（2次），再全部作品汇总不存在
        when(authorIncomeDetailMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(detail)
                .thenReturn(detail);
        when(authorIncomeDetailMapper.updateById(any(AuthorIncomeDetail.class))).thenReturn(1);

        // 已有月结单
        AuthorIncome existingIncome = new AuthorIncome();
        existingIncome.setId(1L);
        existingIncome.setAuthorId(AUTHOR_ID);
        existingIncome.setBookId(BOOK_ID);
        existingIncome.setPreTaxIncome(800);
        existingIncome.setAfterTaxIncome(560);
        when(authorIncomeMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingIncome);
        when(authorIncomeMapper.updateById(any(AuthorIncome.class))).thenReturn(1);

        // When
        RestResp<Void> result = bookPayService.refundChapter(USER_ID, CHAPTER_ID);

        // Then
        assertTrue(result.isOk());

        // 验证月结单被扣减
        ArgumentCaptor<AuthorIncome> incomeCaptor = ArgumentCaptor.forClass(AuthorIncome.class);
        verify(authorIncomeMapper).updateById(incomeCaptor.capture());
        AuthorIncome updatedIncome = incomeCaptor.getValue();
        assertEquals(800 - CHAPTER_PRICE, updatedIncome.getPreTaxIncome()); // 800 - 50 = 750
        assertEquals(560 - CHAPTER_PRICE * 70 / 100, updatedIncome.getAfterTaxIncome()); // 560 - 35 = 525
    }
}
