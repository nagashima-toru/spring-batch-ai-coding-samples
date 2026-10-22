package com.example.batch.core.exception;

/**
 * 業務例外。
 *
 * <p><b>用途:</b> 入力データ不正・業務ルール違反など、対象データを修正すれば解消するエラーを表す。
 * 方式設計（docs/architecture.md §5）で <b>Skip 対象</b> と定められている例外である。</p>
 *
 * <p>メッセージは Skip 時に {@code ErrorRecordSkipListener} の
 * WARN ログへそのまま出るため、個人情報・機密情報（入力レコードの値など）を含めない（docs/architecture.md §8）。</p>
 *
 * <p><b>使い方:</b> ItemProcessor や Tasklet の業務チェックで検出したエラーをこの例外で送出する。
 * Chunk Step では {@link BusinessSkipPolicy} と組み合わせることで該当レコードのみ Skip され、
 * ジョブは処理を継続する。</p>
 *
 * <p><b>拡張ポイント:</b> 業務固有のエラー種別を細分化したい場合は本クラスを継承する
 * （例: {@code InvalidCustomerException extends BusinessException}）。
 * 継承クラスも自動的に Skip 対象となる。</p>
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
