package com.example.batch.sample.job08_s3_io;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * サンプル08【骨格】: 入力リソースプロバイダのプロファイル切替設定。
 *
 * <p><b>デモする機能:</b> ローカル開発は AWS 非依存（ファイルシステム）、
 * AWS 環境では S3 という切替を、ジョブ本体のコードを変えずに実現するパターン。</p>
 *
 * <p>ジョブ本体はサンプル01/03 と同一構成になるため骨格では省略し、
 * Reader の resource をこのプロバイダ経由で解決する点のみが差分となる。
 * 組み立て方は docs/sample08-s3-io.md を参照。</p>
 */
@Configuration(proxyBeanMethods = false)
public class Sample08Config {

    /** ローカル開発用（既定）: ファイルシステムから取得。 */
    @Bean
    @Profile("local | default")
    public InputResourceProvider localFileResourceProvider() {
        return new LocalFileResourceProvider();
    }

    /** AWS 環境用: S3 から取得（骨格。実装時は spring-cloud-aws を追加）。 */
    @Bean
    @Profile("dev | prod")
    public InputResourceProvider s3ResourceProvider() {
        return new S3ResourceProvider();
    }
}
