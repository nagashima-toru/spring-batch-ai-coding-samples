package com.example.batch.core.exception;

/**
 * 再試行可能なシステム例外。
 *
 * <p><b>用途:</b> ネットワーク瞬断・外部APIのタイムアウト・一時的な 5xx 応答など、
 * 時間をおいて再試行すれば回復しうるエラーを表す。方式設計（docs/architecture.md §5）で
 * <b>Retry 対象</b>と定められている例外である。</p>

 * <p><b>使い方:</b> HTTP Client 基盤（{@code BatchHttpClient}）は 5xx・接続エラーを
 * 自動的にこの例外へ変換する。Chunk Step の Retry 対象に指定する場合は
 * {@code faultTolerant().retry(RetryableSystemException.class).retryLimit(n)} を使用する。
 * リトライが枯渇した場合は異常終了（終了コード1）となる。</p>
 *
 * <p><b>拡張ポイント:</b> 外部システム固有の再試行可能エラーを表す場合は本クラスを継承する。</p>
 */
public class RetryableSystemException extends SystemException {

    public RetryableSystemException(String message) {
        super(message);
    }

    public RetryableSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
