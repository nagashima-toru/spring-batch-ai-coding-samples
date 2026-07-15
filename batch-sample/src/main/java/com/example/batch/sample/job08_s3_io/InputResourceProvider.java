package com.example.batch.sample.job08_s3_io;

import org.springframework.core.io.Resource;

/**
 * サンプル08【骨格】: 入力リソースの取得先を抽象化するプロバイダ。
 *
 * <p>ジョブ本体はこのインターフェースにのみ依存し、実際の取得先
 * （ローカルファイルシステム / S3）はプロファイルで切り替える（AWS 非依存のローカル開発。
 * docs/architecture.md §1.1）。</p>
 */
public interface InputResourceProvider {

    /**
     * 入力リソースを解決する。
     *
     * @param location 取得先（ローカル: ファイルパス / S3: オブジェクトキー）
     * @return 読取用リソース
     */
    Resource resolve(String location);
}
