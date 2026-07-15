package com.example.batch.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * サンプルバッチアプリケーションのエントリポイント。
 *
 * <p>全サンプルジョブを同梱し、起動時に {@code spring.batch.job.name} プロパティで
 * 実行するジョブを1つ選択する。ECS ではタスク定義のコマンド上書きで指定する。</p>
 *
 * <pre>{@code
 * java -jar batch-sample.jar --spring.batch.job.name=sample01CsvImportJob importDate=2026-07-14
 * }</pre>
 *
 * <p>ジョブの終了コード（0=正常 / 2=警告 / 1=異常）はプロセスの終了コードとして
 * ECS タスクへ伝播される（{@link SpringApplication#exit} を使用）。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BatchSampleApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(BatchSampleApplication.class, args)));
    }
}
