package io.github.xxyopen.novel.core.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举类。
 *
 * 错误码为字符串类型，共 5 位，分成两个部分：错误产生来源+四位数字编号。
 * 错误产生来源分为 A/B/C， A 表示错误来源于用户，比如参数错误，用户安装版本过低，用户支付
 * 超时等问题； B 表示错误来源于当前系统，往往是业务逻辑出错，或程序健壮性差等问题； C 表示错误来源
 * 于第三方服务，比如 CDN 服务出错，消息投递超时等问题；四位数字编号从 0001 到 9999，大类之间的
 * 步长间距预留 100。
 *
 * 错误码分为一级宏观错误码、二级宏观错误码、三级宏观错误码。
 * 在无法更加具体确定的错误场景中，可以直接使用一级宏观错误码。
 *
 * @author xiongxiaoyang
 * @date 2022/5/11
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    /**
     * 正确执行后的返回
     * */
    OK("00000","一切 ok"),

    /**
     * 一级宏观错误码，用户端错误
     * */
    USER_ERROR("A0001","用户端错误"),

    /**
     * 二级宏观错误码，用户注册错误
     * */
    USER_REGISTER_ERROR("A0100","用户注册错误"),

    /**
     * 用户未同意隐私协议
     * */
    USER_NO_AGREE_PRIVATE_ERROR("A0101","用户未同意隐私协议"),

    /**
     * 注册国家或地区受限
     * */
    USER_REGISTER_AREA_LIMIT_ERROR("A0102","注册国家或地区受限"),

    /**
     * 用户验证码错误
     * */
    USER_VERIFY_CODE_ERROR("A0240","用户验证码错误"),

    /**
     * 用户名已存在
     * */
    USER_NAME_EXIST("A0111","用户名已存在"),

    /**
     * 用户账号不存在
     * */
    USER_ACCOUNT_NOT_EXIST("A0201","用户账号不存在"),

    /**
     * 用户密码错误
     * */
    USER_PASSWORD_ERROR("A0210","用户密码错误"),

    /**
     * 二级宏观错误码，用户请求参数错误
     * */
    USER_REQUEST_PARAM_ERROR("A0400","用户请求参数错误"),

    /**
     * 用户登录已过期
     * */
    USER_LOGIN_EXPIRED("A0230","用户登录已过期"),

    /**
     * 访问未授权
     * */
    USER_UN_AUTH("A0301","访问未授权"),

    /**
     * 用户评论异常
     * */
    USER_COMMENT("A2000","用户评论异常"),

    /**
     * 用户评论异常
     * */
    USER_COMMENTED("A2001","用户已发表评论"),

    /**
     * 用户上传文件异常
     * */
    USER_UPLOAD_FILE_ERROR("A0700","用户上传文件异常"),

    /**
     * 用户上传文件类型不匹配
     * */
    USER_UPLOAD_FILE_TYPE_NOT_MATCH("A0701","用户上传文件类型不匹配"),

    /**
     * 一级宏观错误码，系统执行出错
     * */
    SYSTEM_ERROR("B0001","系统执行出错"),

    /**
     * 二级宏观错误码，系统执行超时
     * */
    SYSTEM_TIMEOUT_ERROR("B0100","系统执行超时"),

    /**
     * 一级宏观错误码，调用第三方服务出错
     * */
    THIRD_SERVICE_ERROR("C0001","调用第三方服务出错"),

    /**
     * 一级宏观错误码，中间件服务出错
     * */
    MIDDLEWARE_SERVICE_ERROR("C0100","中间件服务出错"),

    /**
     * 用户余额不足
     * */
    USER_BALANCE_INSUFFICIENT("A0501","用户余额不足"),

    /**
     * 章节已购买
     * */
    USER_CHAPTER_ALREADY_PURCHASED("A0502","章节已购买"),

    /**
     * 章节不存在
     * */
    USER_CHAPTER_NOT_EXIST("A0503","章节不存在"),

    /**
     * 小说不存在
     * */
    USER_BOOK_NOT_EXIST("A0504","小说不存在"),

    /**
     * 不能购买自己的章节
     * */
    USER_PURCHASE_OWN_CHAPTER("A0505","不能购买自己的章节"),

    /**
     * 不符合退款条件
     * */
    USER_REFUND_NOT_ALLOWED("A0506","不符合退款条件"),

    /**
     * 稿费已确认无法回滚
     * */
    USER_SETTLEMENT_CONFIRMED("A0507","稿费已确认无法回滚"),

    /**
     * 系统繁忙请稍后重试
     * */
    SYSTEM_PURCHASE_LOCK_FAILED("B0201","系统繁忙请稍后重试"),

    /**
     * 结算系统异常
     * */
    SYSTEM_SETTLEMENT_ERROR("B0202","结算系统异常")
    ;

    /**
     * 错误码
     * */
    private String code;

    /**
     * 中文描述
     * */
    private String message;

}
