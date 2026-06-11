package io.github.xxyopen.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 财务相关配置
 *
 * @author xiongxiaoyang
 * @date 2022/05/11
 */
@ConfigurationProperties(prefix = "novel.finance")
@Component
@Data
public class FinanceProperties {

    /**
     * 默认章节价格（屋币）
     */
    private int defaultChapterPrice = 10;

    /**
     * 税率百分比，如 20 表示 20%
     */
    private int taxRate = 20;

    /**
     * 每月结算日
     */
    private int settlementDay = 1;

}
