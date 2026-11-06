package com.niuma.admin.merchant;

/**
 * 商户接口业务异常（带契约错误码）
 */
public class MerchantException extends RuntimeException {

    private final int code;

    public MerchantException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 1001 商户不存在或被禁用 */
    public static MerchantException merchantInvalid() {
        return new MerchantException(1001, "merchant invalid");
    }

    /** 1002 签名错误 */
    public static MerchantException badSign() {
        return new MerchantException(1002, "bad sign");
    }

    /** 1003 txnId 冲突（参数不同） */
    public static MerchantException txnConflict() {
        return new MerchantException(1003, "txnId conflict");
    }

    /** 1004 余额不足 */
    public static MerchantException insufficient() {
        return new MerchantException(1004, "insufficient balance");
    }

    /** 1005 玩家在局中，禁止转出 */
    public static MerchantException inGame() {
        return new MerchantException(1005, "player is in game");
    }

    /** 1006 玩家不存在 */
    public static MerchantException playerNotFound() {
        return new MerchantException(1006, "player not found");
    }

    /** 1008 参数错误 */
    public static MerchantException badRequest(String msg) {
        return new MerchantException(1008, msg);
    }
}
