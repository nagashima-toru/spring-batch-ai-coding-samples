package com.example.batch.core.exception;

/**
 * システム例外。
 *
 * <p><b>用途:</b> DB接続断・設定不備・外部システムの恒常的な異常など、
 * 環境を修正しないと解消しないエラーを表す。方式設計（docs/architecture.md §5）で
 * <b>即時異常終了</b>（Skip・Retry 対象外）と定められている例外である。</p>
 *
 * <p><b>使い方:</b> 環境起因のエラーを検出した箇所でこの例外を送出する。
 * ジョブは異常終了（終了コード1）し、原因除去後に同一パラメータでリスタートする運用となる。</p>
 *
 * <p><b>拡張ポイント:</b> 一時的で再試行により回復しうるエラーは本クラスではなく
 * {@link RetryableSystemException} を使用する。</p>
 */
public class SystemException extends RuntimeException {

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
