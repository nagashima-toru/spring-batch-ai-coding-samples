package com.example.batch.sample.job_point_expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.sample.AbstractJobIT;

/**
 * POINT-001（会員ポイント失効バッチ）の結合テスト。
 *
 * <p>詳細設計書 docs/projects/member-point-expiry/detail-design/POINT-001.md §6
 * 「結合テスト（PointExpiryJobIT）」の観点に基づく。本ジョブは Tasklet 1 Step・
 * 単一 SQL（データ変更 CTE）で失効更新と履歴登録を原子的に行う構成のため、
 * 特に重要な回帰観点は<b>一意制約違反時の全ロールバック（原子性）</b>と、
 * <b>リスタート時に失効・履歴が正しく1回だけ適用されること</b>である。</p>
 */
class PointExpiryJobIT extends AbstractJobIT {

    /** テスト全体で使う対象日（システム時刻＝今日日付とは異なる値にして expired_date の由来を区別できるようにする）。 */
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 10);

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("pointExpiryJob")
    private Job job;

    @BeforeEach
    void setUp() {
        // point_expiry_history は point_grant を参照するため子テーブルから truncate する
        truncateAppTable("point_expiry_history");
        truncateAppTable("point_grant");
    }

    private JobParameters parameters(LocalDate targetDate) {
        return uniqueParameters().addLocalDate("targetDate", targetDate).toJobParameters();
    }

    private void insertGrant(long id, long memberId, int points, LocalDate grantedDate, LocalDate expiryDate,
            String status) {
        jdbcTemplate.update(
                "INSERT INTO app.point_grant (point_grant_id, member_id, points, granted_date, expiry_date, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, memberId, points, grantedDate, expiryDate, status);
    }

    /** 既に失効済み（履歴あり）の付与を仕込む。一意制約違反の再現・原子性検証に使う。 */
    private void insertAlreadyExpiredGrantWithHistory(long id, long memberId, int points, LocalDate grantedDate,
            LocalDate expiryDate, LocalDate expiredDate) {
        insertGrant(id, memberId, points, grantedDate, expiryDate, "2");
        jdbcTemplate.update("UPDATE app.point_grant SET expired_date = ? WHERE point_grant_id = ?", expiredDate, id);
        jdbcTemplate.update(
                "INSERT INTO app.point_expiry_history "
                        + "(point_grant_id, member_id, points, granted_date, expiry_date, expired_date) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, memberId, points, grantedDate, expiryDate, expiredDate);
    }

    private String grantStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.point_grant WHERE point_grant_id = ?", String.class, id);
    }

    private long historyCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app.point_expiry_history", Long.class);
    }

    private long historyCountByGrantId(long grantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.point_expiry_history WHERE point_grant_id = ?", Long.class, grantId);
    }

    @Test
    void 期限切れかつ有効な付与のみ失効し失効履歴が1対1で対応する() throws Exception {
        // 失効対象: 1〜3日前が期限切れ（複数日分まとめての自己回復も兼ねる）
        insertGrant(1, 901, 100, LocalDate.of(2026, 7, 1), TARGET_DATE.minusDays(1), "1");
        insertGrant(2, 902, 200, LocalDate.of(2026, 7, 2), TARGET_DATE.minusDays(2), "1");
        insertGrant(3, 903, 300, LocalDate.of(2026, 7, 3), TARGET_DATE.minusDays(3), "1");
        // 境界値: 期限当日は失効しない
        insertGrant(4, 904, 400, LocalDate.of(2026, 7, 4), TARGET_DATE, "1");
        // 対象外: 期限が未来
        insertGrant(5, 905, 500, LocalDate.of(2026, 7, 5), TARGET_DATE.plusDays(1), "1");
        // 対象外: 既に失効済み（再度対象にならないこと）
        insertAlreadyExpiredGrantWithHistory(6, 906, 600, LocalDate.of(2026, 6, 1), TARGET_DATE.minusDays(10),
                TARGET_DATE.minusDays(5));

        JobExecution execution = jobOperator.start(job, parameters(TARGET_DATE));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // 期限切れ3件が失効し、境界値・未来・既失効は変化しない
        assertThat(grantStatus(1)).isEqualTo("2");
        assertThat(grantStatus(2)).isEqualTo("2");
        assertThat(grantStatus(3)).isEqualTo("2");
        assertThat(grantStatus(4)).isEqualTo("1"); // 境界値: 期限当日は失効しない
        assertThat(grantStatus(5)).isEqualTo("1"); // 未来の期限は対象外
        assertThat(grantStatus(6)).isEqualTo("2"); // 既失効はそのまま（再処理されない）

        // 失効させた付与と履歴が1対1（新規3件のみ。既失効分の履歴は増えない）
        assertThat(historyCount()).isEqualTo(4); // 新規3件 + 事前投入済み1件(id=6)
        assertThat(historyCountByGrantId(1)).isEqualTo(1);
        assertThat(historyCountByGrantId(2)).isEqualTo(1);
        assertThat(historyCountByGrantId(3)).isEqualTo(1);

        // 履歴の内容が付与のスナップショットと一致し、expired_date はジョブパラメータの targetDate（実行日ではない）
        Map<String, Object> history1 = jdbcTemplate.queryForMap(
                "SELECT member_id, points FROM app.point_expiry_history WHERE point_grant_id = 1");
        assertThat(history1.get("member_id")).isEqualTo(901L);
        assertThat(history1.get("points")).isEqualTo(100);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT granted_date FROM app.point_expiry_history WHERE point_grant_id = 1", LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT expiry_date FROM app.point_expiry_history WHERE point_grant_id = 1", LocalDate.class))
                .isEqualTo(TARGET_DATE.minusDays(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT expired_date FROM app.point_expiry_history WHERE point_grant_id = 1", LocalDate.class))
                .isEqualTo(TARGET_DATE);

        // point_grant 側の expired_date もジョブパラメータの targetDate
        LocalDate grant1ExpiredDate = jdbcTemplate.queryForObject(
                "SELECT expired_date FROM app.point_grant WHERE point_grant_id = 1", LocalDate.class);
        assertThat(grant1ExpiredDate).isEqualTo(TARGET_DATE);
    }

    @Test
    void 失効対象が0件でも正常終了する() throws Exception {
        // 境界値・未来のみ（失効対象なし）
        insertGrant(1, 901, 100, LocalDate.of(2026, 7, 1), TARGET_DATE, "1");
        insertGrant(2, 902, 200, LocalDate.of(2026, 7, 2), TARGET_DATE.plusDays(1), "1");

        JobExecution execution = jobOperator.start(job, parameters(TARGET_DATE));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(historyCount()).isZero();
        assertThat(grantStatus(1)).isEqualTo("1");
        assertThat(grantStatus(2)).isEqualTo("1");
    }

    @Test
    void 同一targetDateで別のJobInstanceとして再実行しても履歴が二重登録されない() throws Exception {
        insertGrant(1, 901, 100, LocalDate.of(2026, 7, 1), TARGET_DATE.minusDays(1), "1");

        JobExecution first = jobOperator.start(job, parameters(TARGET_DATE));
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(grantStatus(1)).isEqualTo("2");
        assertThat(historyCount()).isEqualTo(1);

        // 別パラメータ（testRunId違い）で新しい JobInstance として同一 targetDate を再実行
        JobExecution second = jobOperator.start(job, parameters(TARGET_DATE));

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        // status='1' の対象がもう無いため affected rows = 0 となり、履歴も増えない
        assertThat(grantStatus(1)).isEqualTo("2");
        assertThat(historyCount()).isEqualTo(1);
    }

    @Test
    void 履歴の一意制約違反時は文全体がロールバックされpoint_grantも更新されない() throws Exception {
        // 正常に失効するはずの付与
        insertGrant(1, 901, 100, LocalDate.of(2026, 7, 1), TARGET_DATE.minusDays(1), "1");
        // 既に履歴を持つ付与を誤って status='1' に戻したデータ（一意制約違反を誘発）
        insertAlreadyExpiredGrantWithHistory(2, 902, 200, LocalDate.of(2026, 6, 1), TARGET_DATE.minusDays(10),
                TARGET_DATE.minusDays(5));
        jdbcTemplate.update("UPDATE app.point_grant SET status = '1' WHERE point_grant_id = 2");

        JobExecution execution = jobOperator.start(job, parameters(TARGET_DATE));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        // 終了コード1（異常）となり、警告終了（COMPLETED_WITH_WARNING＝終了コード2）は発生しない（詳細設計書§3）
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        // 文全体がロールバックされているため、本来失効するはずだった付与1も更新されていない
        assertThat(grantStatus(1)).isEqualTo("1");
        assertThat(grantStatus(2)).isEqualTo("1");
        // 履歴も増えない（事前投入済みの1件のみ）
        assertThat(historyCount()).isEqualTo(1);
    }

    @Test
    void 一意制約違反で失敗後にデータを是正して同一パラメータでリスタートすると完走し失効履歴が1回だけ適用される()
            throws Exception {
        insertGrant(1, 901, 100, LocalDate.of(2026, 7, 1), TARGET_DATE.minusDays(1), "1");
        insertAlreadyExpiredGrantWithHistory(2, 902, 200, LocalDate.of(2026, 6, 1), TARGET_DATE.minusDays(10),
                TARGET_DATE.minusDays(5));
        jdbcTemplate.update("UPDATE app.point_grant SET status = '1' WHERE point_grant_id = 2");
        JobParameters params = parameters(TARGET_DATE);

        // 1回目: 一意制約違反で異常終了（部分適用が残っていないことを確認）
        JobExecution failed = jobOperator.start(job, params);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(grantStatus(1)).isEqualTo("1");
        assertThat(historyCount()).isEqualTo(1);

        // データ是正: 誤って戻した status を本来の失効済みへ戻す（詳細設計書§5のリラン前是正手順）
        jdbcTemplate.update("UPDATE app.point_grant SET status = '2' WHERE point_grant_id = 2");

        // 2回目: 同一パラメータでリスタート（Tasklet Step は最初から再実行される）
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(grantStatus(1)).isEqualTo("2");
        // 失効・履歴が正しく1回だけ適用される（付与1の履歴が重複していないこと）
        assertThat(historyCountByGrantId(1)).isEqualTo(1);
        assertThat(historyCount()).isEqualTo(2); // 是正前から存在する付与2の履歴1件 + 付与1の新規履歴1件
    }

    @Test
    void targetDate未指定の場合はジョブパラメータ検証エラーで起動失敗する() {
        JobParameters params = uniqueParameters().toJobParameters(); // targetDate を含めない

        assertThatThrownBy(() -> jobOperator.start(job, params))
                .isInstanceOf(InvalidJobParametersException.class);

        // DB は一切変更されない（Step 実行前に失敗するため）
        assertThat(historyCount()).isZero();
    }
}
