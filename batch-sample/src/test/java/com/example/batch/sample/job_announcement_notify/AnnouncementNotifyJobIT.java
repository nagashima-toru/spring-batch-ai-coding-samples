package com.example.batch.sample.job_announcement_notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.batch.core.execution.BatchExitCodes;
import com.example.batch.sample.AbstractJobIT;

/**
 * ANNOUNCE-001（お知らせ通知）の結合テスト。
 *
 * <p>詳細設計書 §6 のテスト観点メモに基づく。特に重要な回帰観点は
 * 「Step2 で区域展開不能が発生した後に Step3 が失敗し、リスタートで完走した場合でも
 * 警告終了（COMPLETED_WITH_WARNING）が維持されること」（Job レベル ExecutionContext による
 * 警告集計の実機検証。設計レビューで検出した不具合の再発防止）。</p>
 */
class AnnouncementNotifyJobIT extends AbstractJobIT {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("announcementNotifyJob")
    private Job job;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        truncateAppTable("mail_send");
        truncateAppTable("family_relation");
        truncateAppTable("announcement_region");
        truncateAppTable("announcement");
        truncateAppTable("app_user");
        truncateAppTable("zip_code_master");
        // 郵便番号マスタのシードデータ（東京都13: 千代田区13101・中央区13102 / 大阪府27: 大阪市北区27100）
        insertZip("1000001", "13", "13101");
        insertZip("1000002", "13", "13101");
        insertZip("1040031", "13", "13102");
        insertZip("5300001", "27", "27100");
    }

    private JobParameters parameters(Path errorFile) {
        return uniqueParameters()
                .addLocalDateTime("scheduledTime", LocalDateTime.now())
                .addString("errorFile", errorFile.toString())
                .toJobParameters();
    }

    private void insertZip(String zipCode, String prefectureCode, String cityCode) {
        jdbcTemplate.update(
                "INSERT INTO app.zip_code_master (zip_code, prefecture_code, city_code) VALUES (?, ?, ?)",
                zipCode, prefectureCode, cityCode);
    }

    private void insertAnnouncement(long id, String title, String body) {
        jdbcTemplate.update("INSERT INTO app.announcement (announcement_id, title, body, status, created_at) "
                + "VALUES (?, ?, ?, '0', CURRENT_TIMESTAMP)", id, title, body);
    }

    private void insertRegion(long announcementId, int seq, String prefectureCode, String cityCode) {
        jdbcTemplate.update("INSERT INTO app.announcement_region "
                + "(announcement_id, region_seq, prefecture_code, city_code) VALUES (?, ?, ?, ?)",
                announcementId, seq, prefectureCode, cityCode);
    }

    private void insertUser(long id, String name, String email, String zipCode) {
        jdbcTemplate.update("INSERT INTO app.app_user (user_id, user_name, email, zip_code) VALUES (?, ?, ?, ?)",
                id, name, email, zipCode);
    }

    /** 家族関係を双方向2行で登録する（業務システムの登録仕様と同じ形）。 */
    private void insertFamily(long userId, long familyUserId) {
        jdbcTemplate.update("INSERT INTO app.family_relation (user_id, family_user_id) VALUES (?, ?)",
                userId, familyUserId);
        jdbcTemplate.update("INSERT INTO app.family_relation (user_id, family_user_id) VALUES (?, ?)",
                familyUserId, userId);
    }

    private long mailCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app.mail_send", Long.class);
    }

    private String announcementStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM app.announcement WHERE announcement_id = ?", String.class, id);
    }

    @Test
    void 区域内の本人と同居家族へメール送信依頼を登録し正常終了する() throws Exception {
        insertAnnouncement(1, "防災訓練のお知らせ", "9月1日に実施します");
        insertRegion(1, 1, "13", "13101");
        insertUser(101, "区域内本人", "taro@example.com", "1000001");
        insertUser(102, "同居家族_住所なし", "hanako@example.com", null);
        insertUser(103, "別居家族_区域外", "jiro@example.com", "5300001");
        insertUser(104, "区域内_メール未設定", null, "1000002");
        insertFamily(102, 101); // 住所なし102は区域内101の家族 → 同居とみなし対象
        insertFamily(103, 101); // 区域外に自宅を持つ103は対象外
        Path errorFile = tempDir.resolve("announce_error.txt");

        JobExecution execution = jobOperator.start(job, parameters(errorFile));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        // 対象は本人101と同居家族102のみ（103は区域外、104はメール未設定のため対象外）
        assertThat(mailCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT user_id FROM app.mail_send ORDER BY user_id", Long.class)).containsExactly(101L, 102L);
        // お知らせ内容のスナップショットが登録時点の値で保存される
        Map<String, Object> mail = jdbcTemplate.queryForMap(
                "SELECT email, subject, body, send_status FROM app.mail_send WHERE user_id = 101");
        assertThat(mail.get("email")).isEqualTo("taro@example.com");
        assertThat(mail.get("subject")).isEqualTo("防災訓練のお知らせ");
        assertThat(mail.get("body")).isEqualTo("9月1日に実施します");
        assertThat(mail.get("send_status")).isEqualTo("0");
        // お知らせは処理済みになり処理日時が記録される
        assertThat(announcementStatus(1)).isEqualTo("2");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app.announcement "
                + "WHERE announcement_id = 1 AND processed_at IS NOT NULL", Long.class)).isEqualTo(1);
        // メール未設定ユーザーは「対象外」でありエラーではない（エラーファイルなし・警告なし）
        assertThat(errorFile).doesNotExist();
    }

    @Test
    void 都道府県全域指定の区域は都道府県内の全市区町村のユーザーを対象とする() throws Exception {
        insertAnnouncement(1, "東京都全域のお知らせ", "本文");
        insertRegion(1, 1, "13", null); // city_code NULL = 都道府県全域
        insertUser(101, "千代田区民", "chiyoda@example.com", "1000001");
        insertUser(102, "中央区民", "chuo@example.com", "1040031");
        insertUser(103, "大阪市民", "osaka@example.com", "5300001");

        JobExecution execution = jobOperator.start(job, parameters(tempDir.resolve("e.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForList(
                "SELECT user_id FROM app.mail_send ORDER BY user_id", Long.class)).containsExactly(101L, 102L);
    }

    @Test
    void メール形式不正のユーザーはSkipしてエラーファイルへ退避し警告終了する() throws Exception {
        insertAnnouncement(1, "お知らせ", "本文");
        insertRegion(1, 1, "13", "13101");
        insertUser(101, "正常ユーザー", "taro@example.com", "1000001");
        insertUser(102, "形式不正ユーザー", "invalid-mail", "1000001");
        Path errorFile = tempDir.resolve("announce_error.txt");

        JobExecution execution = jobOperator.start(job, parameters(errorFile));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        // 正常ユーザーのみ登録され、Skip レコードはエラーファイルへ退避される
        assertThat(jdbcTemplate.queryForList(
                "SELECT user_id FROM app.mail_send", Long.class)).containsExactly(101L);
        assertThat(Files.readString(errorFile)).contains("1,102,invalid-mail");
        // Skip があってもお知らせは処理済みになる（後日、データ修正のうえ手動リカバリする運用）
        assertThat(announcementStatus(1)).isEqualTo("2");
    }

    @Test
    void 区域が郵便番号に展開できないお知らせはエラー化し他のお知らせは処理して警告終了する() throws Exception {
        insertAnnouncement(1, "正常なお知らせ", "本文A");
        insertRegion(1, 1, "13", "13101");
        insertAnnouncement(2, "区域不正なお知らせ", "本文B");
        insertRegion(2, 1, "99", "99999"); // 郵便番号マスタに存在しない区域
        insertUser(101, "区域内本人", "taro@example.com", "1000001");

        JobExecution execution = jobOperator.start(job, parameters(tempDir.resolve("e.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        // 展開不能なお知らせのみエラー化し、正常なお知らせは処理済みになる
        assertThat(announcementStatus(1)).isEqualTo("2");
        assertThat(announcementStatus(2)).isEqualTo("9");
        assertThat(jdbcTemplate.queryForList("SELECT announcement_id FROM app.mail_send", Long.class))
                .containsExactly(1L);
    }

    @Test
    void 未処理のお知らせが0件でも正常終了する() throws Exception {
        JobExecution execution = jobOperator.start(job, parameters(tempDir.resolve("e.txt")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(mailCount()).isZero();
    }

    @Test
    void Skip上限超過で異常終了しデータ修正後のリスタートでも区域エラーの警告が維持される() throws Exception {
        // 区域展開不能のお知らせ（Step2 で警告条件が成立する）
        insertAnnouncement(2, "区域不正なお知らせ", "本文B");
        insertRegion(2, 1, "99", "99999");
        // 正常区域のお知らせに skipLimit(10) を超える11件の形式不正ユーザー → Step3 が異常終了する
        insertAnnouncement(1, "正常なお知らせ", "本文A");
        insertRegion(1, 1, "13", "13101");
        for (long userId = 101; userId <= 111; userId++) {
            insertUser(userId, "形式不正" + userId, "bad-mail-" + userId, "1000001");
        }
        insertUser(200, "正常ユーザー", "ok@example.com", "1000001");
        JobParameters params = parameters(tempDir.resolve("announce_error.txt"));

        // 1回目: Step2 は完了（お知らせ2をエラー化）、Step3 が Skip 上限超過で失敗
        JobExecution failed = jobOperator.start(job, params);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(announcementStatus(2)).isEqualTo("9");
        assertThat(announcementStatus(1)).isEqualTo("1"); // 処理中のまま（Step4 未到達）

        // データ修正（メールアドレスを正しい形式へ）
        jdbcTemplate.update(
                "UPDATE app.app_user SET email = user_id || '@example.com' WHERE email LIKE 'bad-mail-%'");

        // 2回目: リスタート。Step2 は再実行されないが、Job レベル ExecutionContext に引き継がれた
        // 区域エラー件数により警告終了(2)が維持されること（詳細設計書 §5 の回帰観点）
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getExitStatus().getExitCode())
                .isEqualTo(BatchExitCodes.COMPLETED_WITH_WARNING.getExitCode());
        assertThat(mailCount()).isEqualTo(12); // 修正済み11件＋正常1件
        assertThat(announcementStatus(1)).isEqualTo("2");
        assertThat(announcementStatus(2)).isEqualTo("9"); // エラーのまま（業務側の修正待ち）
    }

    @Test
    void 処理済みのお知らせを未処理に戻して再実行しても重複登録されない() throws Exception {
        insertAnnouncement(1, "お知らせ", "本文");
        insertRegion(1, 1, "13", "13101");
        insertUser(101, "区域内本人", "taro@example.com", "1000001");

        JobExecution first = jobOperator.start(job, parameters(tempDir.resolve("e1.txt")));
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(mailCount()).isEqualTo(1);

        // 業務側の再処理を想定してお知らせを未処理へ戻し、別パラメータで再実行する
        jdbcTemplate.update("UPDATE app.announcement SET status = '0' WHERE announcement_id = 1");
        JobExecution second = jobOperator.start(job, parameters(tempDir.resolve("e2.txt")));

        // 一意制約 + ON CONFLICT DO NOTHING により二重通知は発生しない（冪等）
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
        assertThat(mailCount()).isEqualTo(1);
        assertThat(announcementStatus(1)).isEqualTo("2");
    }
}
