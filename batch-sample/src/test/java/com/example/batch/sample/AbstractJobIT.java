package com.example.batch.sample;

import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ジョブ結合テストの基底クラス。
 *
 * <p>本番同一バージョンの PostgreSQL 16 コンテナ（Testcontainers）を全テストで共有し、
 * Flyway によるマイグレーション済みの実DBに対してジョブを起動する。
 * ジョブの自動起動は無効化し、各テストが {@code JobOperator} で明示的に起動する。</p>
 *
 * <p>テストデータは各テストが自前で投入し、{@link #truncateAppTable(String)} で
 * 事前に初期化する（docs/architecture.md §12）。</p>
 */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
public abstract class AbstractJobIT {

    /** 本番同一バージョンの PostgreSQL。全テストクラスで共有する（起動コスト削減）。 */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 業務テーブルを初期化する。 */
    protected void truncateAppTable(String table) {
        jdbcTemplate.execute("TRUNCATE TABLE app." + table + " CASCADE");
    }

    /** 一意な識別パラメータ付きの JobParametersBuilder を作成する（JobInstance 重複を回避）。 */
    protected JobParametersBuilder uniqueParameters() {
        return new JobParametersBuilder().addLong("testRunId", System.nanoTime());
    }
}
