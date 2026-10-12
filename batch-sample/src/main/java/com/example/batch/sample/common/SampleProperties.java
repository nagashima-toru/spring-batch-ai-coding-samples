package com.example.batch.sample.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * サンプルアプリケーションの外部設定（application.yml の {@code sample.*}）。
 *
 * <p>チャンクサイズ・Skip 上限等の定数はコードに埋め込まず本クラスで外部化する
 * （docs/architecture.md §10）。</p>
 *
 * @param chunkSize チャンクサイズ（性能測定に基づき環境別に調整する）
 * @param skipLimit Skip 上限件数
 * @param job05           サンプル05（API連携）の設定
 * @param orderDailySales 受注CSV取込・日次売上集計（ORDER-001）の設定
 */
@ConfigurationProperties("sample")
public record SampleProperties(
        @DefaultValue("100") int chunkSize,
        @DefaultValue("10") int skipLimit,
        Job05 job05,
        OrderDailySales orderDailySales) {

    /**
     * 受注CSV取込・日次売上集計（ORDER-001）のファイル配置設定。
     *
     * @param inputDir 受注CSVの既定ディレクトリ
     * @param errorDir エラーファイルの既定ディレクトリ
     */
    public record OrderDailySales(String inputDir, String errorDir) {
    }

    /**
     * サンプル05の外部API設定。
     *
     * @param api API接続設定
     */
    public record Job05(Api api) {
        /**
         * @param baseUrl  API のベースURL
         * @param pageSize ページング取得のページサイズ
         * @param token    認証トークン（本番は Secrets Manager から環境変数注入）
         */
        public record Api(String baseUrl, @DefaultValue("100") int pageSize, String token) {
        }
    }
}
