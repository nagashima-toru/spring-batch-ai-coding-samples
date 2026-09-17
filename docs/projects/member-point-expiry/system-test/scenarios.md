# システムテストシナリオ・実施記録: 会員ポイント失効バッチ（member-point-expiry）

| 項目 | 内容 |
|---|---|
| 対象ジョブ | POINT-001 `pointExpiryJob`（`batch-sample/src/main/java/com/example/batch/sample/job_point_expiry/`） |
| 入力 | `docs/projects/member-point-expiry/basic-design.md`（1.2版）／`docs/projects/member-point-expiry/detail-design/POINT-001.md`（実施時1.4版→No.3の結果を受けて1.5版へ改訂） |
| 実施環境 | ローカル（`docker/compose.yaml` の PostgreSQL 16、`batch-sample-0.1.0-SNAPSHOT.jar` を直接起動） |
| 実施期間 | 2026-09-17 |
| テストデータ | `docs/projects/member-point-expiry/system-test/data/seed_scenario1_2.sql`、`seed_scenario6.sql`（SQL直接投入。要件定義書の前提どおり。個人情報は使用しないダミーデータ） |

## シナリオ一覧

| No | 分類 | シナリオ名 | 優先度 | 結果 |
|---|---|---|---|---|
| 1 | 業務 | 日次サイクル正常系（投入→実行→確認→同一パラメータ再実行の拒否確認） | 高 | OK |
| 2 | 業務 | 自己回復（境界値保護されていた付与・複数日分の期限切れが後続実行でまとめて回収される） | 高 | OK |
| 3 | 運用 | 起動引数の型サフィックス（`,java.time.LocalDate`）省略時の挙動 | 高 | NG→ユーザー確認済み・現状維持で解決（設計書 POINT-001.md を1.5版へ訂正。詳細は§4参照） |
| 4 | 運用 | ジョブパラメータ `targetDate` 未指定時の起動失敗 | 高 | OK |
| 5 | 運用 | プロセス強制終了残骸（`STARTED`のまま）による二重起動防止→是正→リスタート | 高 | OK |
| 6 | 運用 | 失効履歴の一意制約違反によるリラン手順（データ調査→是正→リスタート、原子性の確認） | 高 | OK |
| 7 | 運用 | 終了コードは 0（正常）／1（異常）のみで 2（警告）が発生しないこと | 中 | OK |

全7シナリオ中6件OK、1件（No.3）で設計書の記述と実装の実際の挙動に相違を検出したが、ユーザーに確認のうえ
「機能的な実害はないため実装は現状維持し、設計書の記述のみ実態に合わせて訂正する」ことで解決済み（後述。
POINT-001.md を1.5版へ改訂）。それ以外の不具合は検出されなかった。

---

## No.1: 日次サイクル正常系

- **目的**: 通常運用の日次実行フロー（テストデータ投入→ジョブ実行→失効・履歴の確認→同一パラメータでの再実行が正しく拒否されること）を確認する
- **事前条件**: `app.point_grant` / `app.point_expiry_history` が空（Flyway V4 適用直後）。`data/seed_scenario1_2.sql` を投入済み（910001〜910007、詳細は同ファイルのコメント参照）
- **手順と結果**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `seed_scenario1_2.sql` を投入 | 7件登録される | OK（`INSERT 0 7`） |
| 2 | `java -jar batch-sample-*.jar --spring.batch.job.name=pointExpiryJob targetDate=2026-09-20,java.time.LocalDate` | 終了コード0、失効件数2件（910001・910002） | OK（exit=0、ログ「失効件数=2」、job_execution_id=22 COMPLETED） |
| 3 | `app.point_grant` / `app.point_expiry_history` を確認 | 910001・910002 が `status='2'` `expired_date='2026-09-20'`、履歴2件がスナップショット一致。910003（期限当日=境界値）は `status='1'` のまま。910006（既失効）は不変 | OK（下記証跡） |
| 4 | 同一パラメータ（`targetDate=2026-09-20,java.time.LocalDate`）で再実行 | `JobInstanceAlreadyCompleteException` により起動失敗、終了コード1（成功済みジョブの同一パラメータ再実行はエラーになるのが正しい挙動＝基本設計書§2・詳細設計書§5） | OK（exit=1、`A job instance already exists and is complete for identifying parameters=...targetDate=2026-09-20...`） |

- **確認ポイント**: 件数突合（対象2件＝履歴2件）、境界値（期限当日は失効しない）、除外対象（既失効は再対象化されない）、終了コード、再実行拒否
- **実施結果**: OK
- **証跡**:
```
-- 手順2実行後
 point_grant_id | status | expiry_date | expired_date
----------------+--------+-------------+--------------
         910001 | 2      | 2026-09-18  | 2026-09-20
         910002 | 2      | 2026-09-19  | 2026-09-20
         910003 | 1      | 2026-09-20  |
         910006 | 2      | 2026-08-01  | 2026-08-01
         910007 | 1      | 2026-09-23  |

 point_grant_id | member_id | points | granted_date | expiry_date | expired_date
----------------+-----------+--------+--------------+-------------+--------------
         910001 |     91001 |    100 | 2026-08-01   | 2026-09-18  | 2026-09-20
         910002 |     91002 |    200 | 2026-08-05   | 2026-09-19  | 2026-09-20

-- 手順4（再実行）ログ抜粋
Caused by: org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException:
  A job instance already exists and is complete for identifying parameters=
  {JobParameter{name='targetDate', value=2026-09-20, type=class java.time.LocalDate, identifying=true}}.
  If you want to run this job again, change the parameters.
```

---

## No.2: 自己回復

- **目的**: 数日分の期限切れ（境界値で保護されていたものを含む）が、後続の1回の実行でまとめて回収されることを確認する（要件定義書§3.4・基本設計書§2リラン設計）
- **事前条件**: No.1 実施後の状態（910003〜910005・910007が`status='1'`のまま残っている）
- **手順と結果**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `targetDate=2026-09-23,java.time.LocalDate` で実行 | 終了コード0。910003（前回は境界値で保護）・910004・910005 の3件が失効。910007（期限=対象日と同日の境界値）は失効しない | OK（exit=0、ログ「失効件数=3」、job_execution_id=23 COMPLETED） |

- **確認ポイント**: 前回境界値保護されていた910003が今回は正しく失効すること（自己回復）、新規境界値910007が保護されること
- **実施結果**: OK
- **証跡**:
```
 point_grant_id | status | expiry_date | expired_date
----------------+--------+-------------+--------------
         910001 | 2      | 2026-09-18  | 2026-09-20
         910002 | 2      | 2026-09-19  | 2026-09-20
         910003 | 2      | 2026-09-20  | 2026-09-23   <- 自己回復で失効
         910004 | 2      | 2026-09-21  | 2026-09-23
         910005 | 2      | 2026-09-22  | 2026-09-23
         910006 | 2      | 2026-08-01  | 2026-08-01
         910007 | 1      | 2026-09-23  |              <- 境界値、失効しない
```

---

## No.3: 起動引数の型サフィックス省略時の挙動

- **目的**: 詳細設計書§2・§3#8「型サフィックス（`,java.time.LocalDate`）を省略すると、必須検証は通過し `@StepScope` Bean 生成＝Step実行時に型変換で失敗し終了コード1となる」という設計記述どおりの挙動になることを確認する
- **事前条件**: なし（未使用の `targetDate` 値を使用）
- **手順と結果**:

| # | 操作 | 期待結果（設計書の記述） | 実施結果 |
|---|---|---|---|
| 1 | `targetDate=2026-09-01`（型サフィックスなし、ISO8601形式の妥当な日付） | Step実行時に型変換で失敗し終了コード1 | **NG**: 終了コード**0**でジョブが**正常完了**した（job_execution_id=20 COMPLETED、ログ「対象日=2026-09-01 失効件数=0」） |
| 2 | `targetDate=not-a-date`（型サフィックスなし、日付として解釈不能な文字列） | Step実行時に型変換で失敗し終了コード1 | OK: 終了コード1、`ConversionFailedException`／`DateTimeParseException` により Step FAILED（job_execution_id=21） |

- **確認ポイント**: 型サフィックス省略時の実際の挙動は「文字列がISO-8601形式として解釈可能な場合は暗黙に`LocalDate`へ変換されジョブが正常完了し、解釈不能な場合のみ失敗する」というものであり、**設計書が前提とする「型サフィックス省略＝常に失敗する」という安全網は成立しない**
- **実施結果**: 実施は完了（設計書の記述と実際の挙動が相違）。ユーザーに確認した結果、機能的な実害はない
  （誤った日付として処理されることはない）ため**実装は変更せず現状維持**、設計書側の記述のみ実態に合わせて
  訂正する方針で解決した。`docs/projects/member-point-expiry/detail-design/POINT-001.md` を1.5版へ改訂し、
  §2・§3#8・§6・§7（保留事項#5）を修正済み
- **証跡**:
```
-- 手順1（2026-09-01, サフィックスなし）
JobParameter{name='targetDate', value=2026-09-01, type=class java.lang.String, identifying=true}
...
PointExpiryTasklet : ポイントを失効しました: 対象日=2026-09-01 失効件数=0
...
TaskExecutorJobLauncher : Job: [SimpleJob: [name=pointExpiryJob]] completed ... status: [COMPLETED] in 63ms
（終了コード: 0）

-- 手順2（not-a-date, サフィックスなし）
Caused by: org.springframework.core.convert.ConversionFailedException: Failed to convert from type
  [java.lang.String] to type [@org.springframework.beans.factory.annotation.Value java.time.LocalDate]
  for value [not-a-date]
Caused by: java.time.format.DateTimeParseException: Text 'not-a-date' could not be parsed at index 0
（終了コード: 1）
```
- **原因分析（参考）**: Spring Boot がビーンファクトリに登録する既定の `ConversionService`（`ApplicationConversionService` 系、JSR-310 の `StringToLocalDateConverter` を含む）が `@Value("#{jobParameters['targetDate']}") LocalDate targetDate` の型変換に適用され、ISO-8601形式の文字列は正常に `LocalDate` へ変換される。設計書§2の「Spring の既定 ConversionService は `LocalDate.parse` を自動適用しない」という記述は本アプリケーションの実際の構成では成立していない（`PointExpiryTaskletTest`・`PointExpiryJobIT` のいずれにもこのケースの直接検証はなく、単体・結合テストでは検出されていなかった）

---

## No.4: ジョブパラメータ `targetDate` 未指定時の起動失敗

- **目的**: 詳細設計書§3#2「`targetDate` 未指定はStep実行前にジョブ失敗、終了コード1」を確認する
- **手順と結果**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `--spring.batch.job.name=pointExpiryJob`（`targetDate` を渡さない） | `JobParametersInvalidException`（`InvalidJobParametersException`）によりStep実行前に失敗、終了コード1 | OK（exit=1、`InvalidJobParametersException: The JobParameters do not contain required keys: [targetDate]`） |

- **実施結果**: OK（この起動で Flyway V4マイグレーションも初回適用され、`app.point_grant`・`app.point_expiry_history` が作成されたことも確認した）

---

## No.5: プロセス強制終了残骸（`STARTED`）による二重起動防止→是正→リスタート

- **目的**: 詳細設計書§3#9・§5「ECSタスク強制終了等で`BATCH_JOB_EXECUTION`が`STARTED`のまま残った場合、二重起動防止で新規実行がブロックされ、`FAILED`/`ABANDONED`へ是正すればリスタートできる」ことを確認する
- **事前条件**: No.2完了後の job_execution_id=23（`targetDate=2026-09-23`、本来は`COMPLETED`）
- **手順と結果**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `UPDATE batch.batch_job_execution SET status='STARTED', exit_code='EXECUTING', end_time=NULL ... WHERE job_execution_id=23`（`batch_step_execution`も同様）でプロセス強制終了の残骸を模擬 | 該当行が`STARTED`になる | OK |
| 2 | 未使用の`targetDate=2026-09-25,java.time.LocalDate`で新規起動 | `DuplicateRunPreventingJobExecutionListener`が`SystemException`を送出し起動前に失敗、終了コード1 | OK（exit=1、ログ「二重起動を検知しました: jobName=pointExpiryJob 実行中executionIds=[23, 24]」） |
| 3 | `UPDATE batch.batch_job_execution SET status='FAILED', exit_code='FAILED', end_time=now() ... WHERE job_execution_id=23`（`batch_step_execution`も同様）で是正 | 該当行が`FAILED`になる | OK |
| 4 | 同じ`targetDate=2026-09-25,java.time.LocalDate`で再起動 | 二重起動防止に引っかからず正常完了、終了コード0 | OK（exit=0、失効件数1件＝910007。前回`STARTED`扱いで未処理だった910007を正しく回収） |

- **確認ポイント**: 残骸がある間は**新しい`targetDate`でも**起動できないこと（ジョブ名単位のチェックであり対象日単位ではない）／是正後は正常にリスタートできること
- **実施結果**: OK
- **証跡**: 上表のログ抜粋のとおり。是正操作は運用手順書相当のSQLとして本文中に明記（`docs/projects/member-point-expiry/system-test/scenarios.md`本節）

---

## No.6: 失効履歴の一意制約違反によるリラン手順

- **目的**: 詳細設計書§3#5・§5「一意制約違反で異常終了した場合は、リスタート前にデータ調査→是正が必要」という運用手順、および原子性（一部のみコミットされないこと）を確認する
- **事前条件**: `data/seed_scenario6.sql` のステップAを投入済み（920001, expiry_date=2026-09-05, status='1'）
- **手順と結果**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `targetDate=2026-09-27,java.time.LocalDate` で実行 | 終了コード0、920001が失効（履歴登録） | OK（exit=0、失効件数1） |
| 2 | `seed_scenario6.sql` ステップB相当を実行: 920001の`status`を誤って`'1'`へ戻す（履歴は残存）＋対照用の正当な新規データ920002（expiry_date=2026-09-06, status='1'）を投入 | データ破損状態を再現 | OK |
| 3 | 新しい`targetDate=2026-09-28,java.time.LocalDate`で実行 | `app.point_expiry_history`の一意制約（`point_grant_id`）違反により`DuplicateKeyException`で異常終了、終了コード1。**920001だけでなく同一トランザクションに含まれる920002も未コミットのまま**（原子性） | OK（exit=1、`DuplicateKeyException: ... duplicate key value violates unique constraint "point_expiry_history_point_grant_id_key"`。920001・920002とも`status='1'`のまま変化なし、920002の履歴も0件で全ロールバックを確認） |
| 4 | データ調査（`seed_scenario6.sql`のコメントSQL）を実行 | 履歴を持つのに`status='1'`の付与＝920001が特定できる | OK（クエリ結果: `920001, status=1, history_expired_date=2026-09-27`） |
| 5 | 是正: 920001の`status`を履歴の内容（`expired_date=2026-09-27`）に合わせて`'2'`へ復元 | 920001が正しい状態に戻る | OK |
| 6 | 同一`targetDate=2026-09-28,java.time.LocalDate`でリスタート | 終了コード0。920002のみが失効し、920001は不変（履歴も重複せず1件のまま） | OK（exit=0、失効件数1。最終状態: 920001=`status=2,expired_date=2026-09-27`（不変）、920002=`status=2,expired_date=2026-09-28`） |

- **確認ポイント**: 一意制約違反時の異常終了・全ロールバック（原子性）、データ調査手順の有効性、是正後のリスタート成功
- **実施結果**: OK
- **証跡**: 上表のとおり。詳細SQLは `docs/projects/member-point-expiry/system-test/data/seed_scenario6.sql` に手順コメント付きで記録

---

## No.7: 終了コードは0/1のみで2（警告）が発生しないこと

- **目的**: 基本設計書§5・詳細設計書§3「本ジョブはSkipを行わないため警告終了（終了コード2）は発生しない」ことを、実施した全実行を通じて確認する
- **手順**: No.1〜No.6および事前確認で実施した`pointExpiryJob`の全実行（job_execution_id=20〜28）について、`batch.batch_job_execution`の`status`／`exit_code`を確認する
- **実施結果**: OK

| job_execution_id | targetDate | status | exit_code |
|---|---|---|---|
| 20 | 2026-09-01 | COMPLETED | COMPLETED |
| 21 | not-a-date | FAILED | FAILED |
| 22 | 2026-09-20 | COMPLETED | COMPLETED |
| 23 | 2026-09-23 | FAILED（No.5で是正のため意図的に更新） | FAILED |
| 24 | 2026-09-25 | FAILED | FAILED |
| 25 | 2026-09-25 | COMPLETED | COMPLETED |
| 26 | 2026-09-27 | COMPLETED | COMPLETED |
| 27 | 2026-09-28 | FAILED | FAILED |
| 28 | 2026-09-28 | COMPLETED | COMPLETED |

- **確認ポイント**: `pointExpiryJob`の実行記録に`COMPLETED_WITH_WARNING`（プロセス終了コード2に対応）が一件も存在しないこと（他ジョブの過去実行には存在するが本ジョブでは発生しない）
- **証跡**: 上表（`batch.batch_job_execution` / `batch.batch_job_execution_params` の実データ）

---

## 総括

- No.1・2・4・5・6・7は設計書どおりの挙動を確認できた（OK）
- No.3は設計書の記述と実装の実際の挙動が相違していたが、ユーザーに確認のうえ「実装は現状維持し、
  設計書の記述のみ実態に合わせて訂正する」ことで解決済み（POINT-001.md 1.5版）
- 上記以外に不具合（Issue化が必要な事象）は検出されなかった
