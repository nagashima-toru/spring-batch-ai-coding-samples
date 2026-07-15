package com.example.batch.sample.job08_s3_io;

import org.springframework.core.io.Resource;

/**
 * サンプル08【骨格】: S3 から入力を取得するプロバイダ（未実装）。
 *
 * <p>プロファイル {@code dev} / {@code prod} で使用する想定。実装する場合は
 * {@code io.awspring.cloud:spring-cloud-aws-starter-s3} を依存に追加し、
 * {@code S3Resource}（{@code s3://bucket/key} 形式）を返すよう置き換える。
 * 手順・設計方針は docs/sample08-s3-io.md を参照。</p>
 */
public class S3ResourceProvider implements InputResourceProvider {

    @Override
    public Resource resolve(String location) {
        throw new UnsupportedOperationException(
                "S3ResourceProvider は骨格のみ。spring-cloud-aws-starter-s3 を追加して実装すること"
                        + "（docs/sample08-s3-io.md 参照）: location=" + location);
    }
}
