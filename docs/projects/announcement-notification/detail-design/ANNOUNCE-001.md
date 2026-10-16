# 詳細設計書: ANNOUNCE-001 announcementNotifyJob

| 項目 | 内容 |
|---|---|
| ジョブID / ジョブ名（Bean名） | ANNOUNCE-001 / announcementNotifyJob |
| 入力（基本設計書） | docs/projects/announcement-notification/basic-design.md（1.2 版） |
| ステータス | レビュー済み（2026-08-09 設計レビュー・コードレビュー指摘反映済み） |

## 1. ジョブ構成

| 項目 | 内容 |
|---|---|
| ジョブパッケージ | `com.example.batch.sample.job_announcement_notify`（batch-sample モジュール。§2.1 パッケージ・バイ・フィーチャー。案件ジョブのためサンプル番号は付与しない。Job名・Step名・Configクラス名と命名を統一） |
| Config クラス | `AnnouncementNotifyJobConfig` |
| ジョブパラメータ検証 | `JobParametersValidators.required("scheduledTime", "errorFile")` |
| ジョブリスナー | `DuplicateRunPreventingJobExecutionListener`（二重起動防止）／共通 Bean `loggingJobExecutionListener`（標準ログ・Skip発生時の警告判定。他ジョブと同一 Bean をそのまま使用）／`RegionErrorWarningJobExecutionListener`（本ジョブ専用の追加リスナー。区域展開不能による警告判定のみを担当。詳細は §3・§5） |
| チャンクサイズ・Skip上限 | 既存の `SampleProperties`（`sample.chunk-size` / `sample.skip-limit`）を再利用する（batch-sample モジュール共通設定。案件専用のプロパティクラスは追加しない） |

### Step 一覧・フロー

| # | Step名 | 型 | 概要 | 失敗時 |
|---|---|---|---|---|
| 1 | announcementNotify.01PickupStep | Tasklet | `status='0'`（未処理）→`'1'`（処理中）へ一括更新し対象を確定する | ジョブ異常終了。リスタートで再実行（`WHERE status='0'` のため冪等） |
| 2 | announcementNotify.02RegionCheckStep | Tasklet | 通知区域が郵便番号マスタで1件も展開できない `処理中` お知らせを `'9'`（エラー）へ一括更新する | ジョブ異常終了。リスタートで再実行（`WHERE status='1'` のため冪等） |
| 3 | announcementNotify.03RegisterMailStep | Chunk | `処理中` お知らせの通知対象ユーザーを抽出し `app.mail_send` へ登録する | リスタート対象（処理済みチャンクの続きから再開） |
| 4 | announcementNotify.04MarkProcessedStep | Tasklet | `処理中` お知らせを `'2'`（処理済み）へ一括更新する | ジョブ異常終了。リスタートで再実行（`WHERE status='1'` のため冪等） |

分岐なし。`JobBuilder.start(step1).next(step2).next(step3).next(step4)` の線形フローとする（Step2 で区域展開不能なお知らせをエラー化しても、他のお知らせの処理を止める必要がないため Flow 分岐は不要）。

## 2. Step 詳細設計

### Step 1: announcementNotify.01PickupStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `AnnouncementPickupTasklet`（新規、`SalesAggregateTasklet` と同様に単発 SQL 実行を行う独自 Tasklet） | なし（`Tasklet` を直接実装。基盤部品に汎用の一括UPDATE Taskletは無いため業務側で実装） |

#### 処理フロー

1. `AnnouncementMapper#pickupUnprocessed` を実行し、`app.announcement` の `status='0'` の行を `'1'` へ一括更新する（更新件数を戻り値で受け取る）
2. 更新件数を INFO ログ出力する（0件でも異常ではなく「対象なし」として正常終了する）

#### 入力チェック仕様

なし（DB の一括更新のみで、レコード単位の入力チェックは発生しない）

### Step 2: announcementNotify.02RegionCheckStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `AnnouncementRegionCheckTasklet`（新規） | なし（独自 Tasklet） |

#### 処理フロー

1. `AnnouncementMapper#markRegionUnresolved` を実行し、`status='1'` のお知らせのうち、通知区域（`app.announcement_region`）が `app.zip_code_master` で1件も郵便番号に展開できないものを `status='9'` へ一括更新する（更新件数を戻り値で受け取る）
2. 更新件数を **Job レベルの `ExecutionContext`**（`chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()`）へキー `regionErrorCount`（int、加算方式）として記録する。Step レベルの `ExecutionContext` ではなく Job レベルに保持する理由は §3・§5 のリラン設計を参照
3. 更新件数を INFO（0件）または WARN（1件以上）ログ出力する

#### 入力チェック仕様

なし（DB の一括更新のみ）

### Step 3: announcementNotify.03RegisterMailStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Reader | `notifyTargetReader`（`MyBatisCursorItemReader<NotifyCandidate>`、queryId=`NotifyTargetMapper.selectNotifyTargets`） | `MyBatisCursorItemReader`（カーソルフェッチ。§9.2。job03 と同様の構成） |
| Processor | `NotifyCandidateItemProcessor`（新規、`ValidatingItemProcessorBase<NotifyCandidate, MailSend>` を継承） | `ValidatingItemProcessorBase` |
| Writer | `announcementNotifyMailWriter`（`MyBatisBatchItemWriter<MailSend>`、statementId=`MailSendMapper.insert`、`assertUpdates(false)`） | `MyBatisBatchItemWriter`（ON CONFLICT DO NOTHING により更新件数0が正常発生するため job01 と同様 `assertUpdates(false)`） |
| Skip/Retry | `BusinessSkipPolicy(properties.skipLimit())` ／ `ErrorRecordSkipListener<NotifyCandidate, MailSend>` | `BusinessSkipPolicy`、`ErrorRecordSkipListener` |
| チャンクサイズ | `properties.chunkSize()`（`SampleProperties`。既定100） | `SampleProperties` |

#### 処理フロー

1. **Reader**: `app.announcement`（`status='1'`）に紐づく通知対象ユーザーを、後述の SQL（§4）で1件ずつカーソル取得する。抽出時点で以下は SQL の `WHERE`/`JOIN` 条件により対象から除外済み（Skip ではなく最初から対象外）:
   - メールアドレス未設定（`app_user.email IS NULL`）のユーザー
   - 通知区域に該当しないユーザー（本人条件・家族条件のいずれも満たさない）
   - 同一お知らせ×同一ユーザーの重複（`DISTINCT` により1件化）
2. **Processor**: 取得した `NotifyCandidate` の `email` を Bean Validation（`@Email`）で検証する
   - 不正なら `BusinessException` を送出（`ValidatingItemProcessorBase` が自動送出）→ Skip 対象
   - 正常なら `MailSend`（`announcementId` / `userId` / `email` / `subject`=お知らせの `title` / `body`=お知らせの `body` / `sendStatus='0'`）へ変換する
3. **Writer**: `MailSend` を `app.mail_send` へバッチ INSERT する（一意制約 `(announcement_id, user_id)` ＋ `ON CONFLICT DO NOTHING` により再実行時の重複登録を防止）

#### 入力チェック仕様

| 項目 | チェック内容 | 実装（Bean Validation / 業務チェック） | エラー時 |
|---|---|---|---|
| email | メールアドレスとして正しい形式か（空文字も不正とする） | Bean Validation `@NotBlank @Email`（`NotifyCandidate.email` に付与。`@Email` 単体は空文字を許容するため併記。コードレビューで指摘） | `BusinessException` → Skip（`skipLimit` 到達で異常終了） |

※ 未設定（null）チェックは Reader の SQL で除外済みのため Processor では行わない（§3 の分類「業務上の対象外」）。`announcementId` / `userId` / `subject` / `body` は自テーブル起因の値であり入力チェック対象としない。

### Step 4: announcementNotify.04MarkProcessedStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `AnnouncementMarkProcessedTasklet`（新規） | なし（独自 Tasklet） |

#### 処理フロー

1. `AnnouncementMapper#markProcessed` を実行し、`status='1'` のお知らせを `status='2'`・`processed_at=CURRENT_TIMESTAMP`（SQL側で設定。Java側の時刻を渡さない）へ一括更新する
2. 更新件数を INFO ログ出力する

#### 入力チェック仕様

なし

## 3. エラー処理設計

| # | エラー事象 | 分類 | 例外 | 動作 | 退避・出力先 |
|---|---|---|---|---|---|
| 1 | メールアドレス形式不正 | 業務 | `BusinessException`（`NotifyCandidateItemProcessor` が Bean Validation 経由で送出） | Skip（`BusinessSkipPolicy`、上限 `properties.skipLimit()` = 既定10） | エラーレコードファイル（`errorFile` パラメータ。`ErrorRecordSkipListener`）。1件以上でジョブは共通 `loggingJobExecutionListener` の既定判定（Skip件数>0）により警告終了 |
| 2 | メールアドレス未設定 | 業務上の対象外（エラーではない） | なし | Step3 Reader の SQL 抽出条件で除外（Skip・エラーファイル記録の対象にしない） | - |
| 3 | 通知区域が郵便番号マスタに1件も展開できない | 業務（お知らせ単位） | なし（Tasklet 内の SQL 判定） | Step2 で該当お知らせを `status='9'` に更新し処理継続。件数を Job レベル `ExecutionContext.regionErrorCount` に記録 | `app.announcement.status='9'`（データとして残る。ファイル退避なし） |
| 4 | DB 接続断・マスタ/業務テーブル不存在・設定不備 | システム | `SystemException`（基盤部品の DataSource/MyBatis 例外を通じて Step が失敗） | 即時異常終了（Skip しない） | - |

**警告終了（終了コード2）の判定**: 2つの独立したリスナーを**併用**する（設計レビューにより、`LoggingJobExecutionListener` を継承する実装は不採用とした。理由は下記【設計上の注意】参照）。

- **共通 Bean `loggingJobExecutionListener`**（`batch-core` 提供、他ジョブと同一 Bean をそのまま使用）: 標準ログ出力＋ Skip 発生時（#1）の警告終了判定（既定挙動のまま）
- **`RegionErrorWarningJobExecutionListener`**（新規、`JobExecutionListener` を直接実装。`LoggingJobExecutionListener` は継承しない）: `afterJob` で Job レベル `ExecutionContext` の `regionErrorCount`（無ければ0）を読み、1件以上かつ現在の `ExitStatus` が `COMPLETED`（＝異常終了ではない）であれば `BatchExitCodes.COMPLETED_WITH_WARNING` へ更新する（#3 のケース）。両リスナーとも Job の `.listener()` に追加する

【設計上の注意（不採用案とその理由）】: 当初 `LoggingJobExecutionListener` を継承した専用リスナーで `isWarning()` をオーバーライドする案を検討したが、以下の理由で不採用とした。
- 本プロジェクトは Uber-jar 方式（architecture.md §1.2）で全ジョブ Config が単一 Spring コンテキストに同居する。`batch-core` の共通 Bean `loggingJobExecutionListener()` は `@ConditionalOnMissingBean`（戻り値型 `LoggingJobExecutionListener`）で定義されており、そのサブクラスの Bean が先に登録されると「型一致する Bean が既に存在する」と判定され、共通 Bean が登録されなくなる。結果、他の全サンプルジョブの `loggingJobExecutionListener` 型注入が解決できずアプリ全体が起動不能になるおそれがある。継承ではなく独立クラスとして実装することでこの型衝突を回避する

## 4. SQL設計（Mapper XML 方針）

| Mapper#メソッド | 種別 | SQL方針 |
|---|---|---|
| `AnnouncementMapper#pickupUnprocessed` | UPDATE | `UPDATE app.announcement SET status='1' WHERE status='0'`。戻り値 int（更新件数） |
| `AnnouncementMapper#markRegionUnresolved` | UPDATE | `UPDATE app.announcement SET status='9' WHERE status='1' AND NOT EXISTS (` お知らせに紐づく `announcement_region` のいずれかが `zip_code_master` と結合できる行 `)`。結合条件は「`city_code` 指定時は `zip_code_master.city_code` と一致」「`city_code` が NULL（都道府県全域）の場合は `zip_code_master.prefecture_code` と一致」の OR。戻り値 int（更新件数） |
| `AnnouncementMapper#markProcessed` | UPDATE | `UPDATE app.announcement SET status='2', processed_at=CURRENT_TIMESTAMP WHERE status='1'`。戻り値 int（更新件数） |
| `NotifyTargetMapper#selectNotifyTargets` | SELECT（カーソル。`MyBatisCursorItemReader` から使用） | 性能上の理由（下記【設計上の注意】）により、単一クエリの `OR` 結合ではなく **`UNION` で2本のクエリを結合**する。①「本人クエリ」: `app.announcement`（`status='1'`）と `announcement_region`・`zip_code_master`（展開条件は §4 冒頭のUPDATE系と同じ「`city_code` 一致 or 都道府県全域一致」の OR）・`app_user` を結合し、`app_user.zip_code = zip_code_master.zip_code` で本人を抽出する。②「家族クエリ」: 同様に区域を展開した `zip_code_master` に対し、`app_user.zip_code IS NULL` のユーザーを `family_relation`（`fr.user_id`＝候補ユーザー、`fr.family_user_id`＝住所を持つ家族）経由で `family_user.zip_code = zip_code_master.zip_code` として抽出する。両クエリとも `app_user.email IS NOT NULL` を必須条件とする。①②を `UNION`（自動で重複排除）して `(announcement_id, user_id)` の重複を1件化し、リスタート時のカーソル再開位置を安定させるため外側に **`ORDER BY announcement_id, user_id` を必須**とする。取得列: `announcement_id, title AS subject, body, user_id, email` |
| `MailSendMapper#insert` | INSERT | `INSERT INTO app.mail_send (announcement_id, user_id, email, subject, body, send_status) VALUES (..., '0') ON CONFLICT (announcement_id, user_id) DO NOTHING`（冪等）。`created_at` は列の `DEFAULT now()` に委ねる（実装時に Flyway の `V3__create_announcement_notification_tables.sql` へ `DEFAULT now()` を定義する） |

【設計上の注意（性能）】: 本人条件・家族条件を単一クエリの `OR` で結合すると、`app_user`・`zip_code_master`・`family_relation`（最大数十万〜数万件規模）を跨ぐ結合でインデックスが効きにくく、PostgreSQL のプランナが全表結合に倒れやすい。`UNION` で2本の単純結合クエリに分割することで、それぞれが `zip_code_master`／`app_user.zip_code`／`family_relation` の等値結合インデックスを使える形にする（設計レビューにより反映。処理時間の制約は要件定義書で未確定〈No.4〉のため、システムテストで実測し必要なら §2「処理量とチャンクサイズ」記載のパーティショニング検討へつなげる）。

【設計上の注意（カーソルフェッチ）】: PostgreSQL JDBC ドライバは `Statement#setFetchSize(n>0)` を明示しない限り、サーバーサイドカーソルを使わず結果セット全体をクライアントへ読み込む（`MyBatisCursorItemReader` を使っていても、ドライバレベルでは全件メモリロードになり得る）。これは architecture.md §3.3 の「全件メモリロードを禁止する」に反するため、`mybatis.configuration.default-fetch-size`（`application.yml`、既定100＝チャンクサイズ既定値に合わせる）をアプリ全体で明示する（コードレビューで指摘。本ジョブ専用の設定ではなく既存のカーソル読取ジョブ（サンプル03）にも波及する是正）。

## 5. リラン設計

- **前提**: `spring-boot-starter-batch-jdbc` により JDBC ジョブリポジトリを使用する（Boot 4 既定のインメモリ実装は不可。§9.1）
- **途中失敗時**: 同一 `scheduledTime` パラメータでリスタートすると、失敗した Step から再開する
  - Step1・Step2・Step4（Tasklet）: いずれも `WHERE status='...'` による条件付き一括 UPDATE のため、Step 単位で最初から再実行しても結果は変わらない（冪等）
  - Step3（Chunk）: `MyBatisCursorItemReader` が処理済みチャンクの読取件数を `ExecutionContext` に保存し、リスタート時はカーソルを再オープンしてその件数分スキップしてから再開する。`ORDER BY announcement_id, user_id` により毎回同じ順序でカーソルが得られることが前提（§4）
- **冪等性**: `app.mail_send` の一意制約 `(announcement_id, user_id)` ＋ `ON CONFLICT DO NOTHING` により、リスタート・再実行いずれでも同一ユーザーへの二重登録は発生しない
- **成功後の再処理**: 本ジョブは業務パラメータを持たず「未処理お知らせの有無」で処理対象が決まるため、成功済み `scheduledTime` の再実行はできない（架空の再実行禁止は正しい挙動）。次回の定期実行（15分後）が新たな `scheduledTime` で自動的に未処理分を処理する
- **リスタートしない場合の自己回復**: Step1 が `処理中` の残骸（前回異常終了分）も対象に含めるため、リスタートせず次回の定期ポーリング実行に委ねても取りこぼしは発生しない（基本設計書 §5 運用設計）
- **Step2 完了後にリスタートが発生した場合の警告判定**: Tasklet Step（Step1・Step2・Step4）は一度 `COMPLETED` すると Spring Batch の既定挙動でリスタート時に再実行されない。そのため Step2 が成功して `regionErrorCount` を記録した後、Step3 または Step4 が失敗してリスタートが発生すると、そのリスタート実行の `JobExecution` には Step2 の `StepExecution` は含まれない。`regionErrorCount` を **Step レベルではなく Job レベルの `ExecutionContext`** に保持するのはこのためであり、Job レベル `ExecutionContext` は Spring Batch がリスタート時に前回実行分から引き継ぐため、Step2 が再実行されなくても `RegionErrorWarningJobExecutionListener` が正しく警告判定できる（§2 Step2・§3 参照。設計レビューで検出・反映）

## 6. テスト観点メモ（テスト工程への引き継ぎ）

- **Step1**: `status` が `未処理(0)` 以外（処理中/処理済み/エラー）のお知らせが対象に含まれないこと。対象0件でも正常終了すること
- **Step2**: 通知区域が市区町村指定（`city_code` あり）・都道府県全域指定（`city_code` NULL）の両パターンで正しく郵便番号へ展開されること。マスタに1件も一致しない場合に `status='9'` へ更新され、ジョブが警告終了（終了コード2）すること。1件も展開不能がない場合は警告にならないこと
- **Step3（抽出ルール）**:
  - 住所（郵便番号）が区域内に一致する本人が対象になること
  - 住所未登録（`zip_code IS NULL`）のユーザーが、区域内に住む家族の家族関係経由で対象になること
  - 区域外に住む別居家族（自身の `zip_code` を持ち区域外）が対象に**ならない**こと
  - 同一お知らせ×同一ユーザーが複数の経路（本人条件と家族条件の両方に該当等）で重複しても1件のみ登録されること
  - メールアドレス未設定のユーザーが最初から対象外であり、Skip・エラーファイルのいずれにも現れないこと
  - メールアドレス形式不正のユーザーが Skip され、エラーファイルへ `announcement_id, user_id, email` の形式で退避されること。ジョブが警告終了すること
  - Skip が `skipLimit`（既定10）を超えるとジョブが異常終了（終了コード1）すること
- **冪等性・リラン**: 同一お知らせに対してジョブを再実行（または Step3 途中失敗からのリスタート）しても `app.mail_send` に重複行が登録されないこと
- **リスタート時の警告判定（重要・回帰確認）**: Step2 で区域展開不能が発生し正常に完走した後、Step3 または Step4 を意図的に失敗させて同一 `scheduledTime` でリスタートし、最終的に完走した際の終了コードが **2（警告）のまま維持される**こと（Step2 が再実行されず `regionErrorCount` が Job レベル `ExecutionContext` 経由で引き継がれることの確認。設計レビューで判明した不具合の再発防止）
- **アプリケーションコンテキストの健全性**: 本ジョブ追加後も `SpringBootTest` で全ジョブ Config を含むコンテキストが正常に起動し、他サンプルジョブ（sample01〜09）が `loggingJobExecutionListener` を問題なく注入できること（Bean型衝突の再発防止）
- **終了コード**: 正常時0、Skip発生または区域展開不能発生時2、システムエラー時1になること（複合発生時も2または1の優先順位が規約どおりであること）
