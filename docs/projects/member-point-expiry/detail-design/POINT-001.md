# 詳細設計書: POINT-001 pointExpiryJob

| 項目 | 内容 |
|---|---|
| ジョブID / ジョブ名（Bean名） | POINT-001 / `pointExpiryJob` |
| 入力（基本設計書） | docs/projects/member-point-expiry/basic-design.md（1.1 版・確定） |
| ステータス | 確定（1.5）。§7 の保留事項5件はユーザー確認済み |

## 1. ジョブ構成

| 項目 | 内容 |
|---|---|
| ジョブパッケージ | `com.example.batch.sample.job_point_expiry`（batch-sample モジュール。architecture.md §2.1 パッケージ・バイ・フィーチャー。案件ジョブのためサンプル番号は付与せず、Job名・Step名・Configクラス名と命名を統一する。基本設計書 §1） |
| Config クラス | `PointExpiryJobConfig`（`JOB_NAME = "pointExpiryJob"` を定数で保持。サンプル各 Config と同様） |
| ジョブパラメータ検証 | `JobParametersValidators.required("targetDate")`（basic-design §2「ジョブパラメータ」） |
| ジョブリスナー | `DuplicateRunPreventingJobExecutionListener`（二重起動防止）／共通 Bean `loggingJobExecutionListener`（標準ログ・警告判定。**他ジョブと同一 Bean をそのまま使用し、継承した専用リスナーは作らない**。理由は §3【設計上の注意】） |
| チャンクサイズ・Skip上限 | **適用外**。Tasklet 1 Step で Skip を行わないため `SampleProperties`（`sample.chunk-size` / `sample.skip-limit`）は使用しない。案件専用の `@ConfigurationProperties` も追加しない |
| 追加依存 | なし（`batch-sample` は `spring-boot-starter-batch-jdbc`・MyBatis Starter・Flyway に依存済み） |
| 処理量・所要時間の見込み | `app.point_grant` 全体で数千〜数万件、1回の失効対象は通常数十〜数百件（基本設計書 §2「処理量とチャンクサイズ」）。`(status, expiry_date)` インデックスで対象を特定した UPDATE ＋ 同数 INSERT のため**最大でも数秒**を見込む。1回の失効対象が概ね10万件を超える場合は基本設計書 §2 の方式見直し（分割コミット）に該当する |
| 起動コマンド（ローカル・ECS 共通の形） | `--spring.batch.job.name=pointExpiryJob targetDate=2026-08-10,java.time.LocalDate`（**型サフィックス `,java.time.LocalDate` を必ず付ける**。理由は §2 入力チェック仕様） |

### Step 一覧・フロー

| # | Step名 | 型 | 概要 | 失敗時 |
|---|---|---|---|---|
| 1 | `pointExpiry.01ExpireStep` | Tasklet | 対象日（`targetDate`）を条件に、期限切れの「有効」な付与を「失効」へ更新し、更新された行をそのまま失効履歴へ登録する（単一 SQL・単一トランザクション） | ジョブ異常終了（終了コード1）。トランザクション全ロールバックのため中途半端な状態は残らない。同一 `targetDate` でリスタート |

- 分岐なし・後続 Step なし。`JobBuilder.start(pointExpiryExpireStep).build()` の単一 Step 構成とする
- Step 追加（件数チェック等の後処理 Step）は**行わない**。失効対象0件が正常に発生しうる業務のため
  `TableCountCheckTasklet`（最小件数チェック）は適用できず、また後続 Step を設けると別トランザクションとなり
  基本設計書 §2 の「1トランザクションで完結させる」方針から外れるため

### Bean 一覧（実装工程への確定指示）

| Bean名 | 型 | スコープ | 備考 |
|---|---|---|---|
| `pointExpiryJob` | `Job` | singleton | Validator・2リスナーを設定し `pointExpiryExpireStep` を start |
| `pointExpiryExpireStep` | `Step` | singleton | `StepBuilder("pointExpiry.01ExpireStep", jobRepository).tasklet(pointExpiryTasklet, transactionManager).listener(loggingStepExecutionListener)` |
| `pointExpiryTasklet` | `PointExpiryTasklet` | **`@StepScope`** | ジョブパラメータ `targetDate` を注入するため Step スコープ。`@Value("#{jobParameters['targetDate']}") LocalDate targetDate` ＋ `PointGrantMapper` をコンストラクタ注入（`AnnouncementNotifyJobConfig` の `@StepScope` Bean と同じ書式。既存サンプルの `#{jobParameters[...]}` はいずれも `String` 受けだが、Spring Batch 6 の `StepContext#getJobParameters()` は `Map<String, Object>` で**変換済みの型付き値**を返すため `LocalDate` として直接受け取れる） |

- `PointGrantMapper` は `@Mapper` インタフェースのため MyBatis Starter が自動登録する（Config で `@Bean` 定義しない）
- `loggingJobExecutionListener` / `loggingStepExecutionListener` / `duplicateRunPreventingJobExecutionListener` は
  `BatchCoreAutoConfiguration` が登録済みの共通 Bean を注入して使う（新規定義しない）

## 2. Step 詳細設計

### Step 1: pointExpiry.01ExpireStep

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Tasklet | `PointExpiryTasklet`（新規。`Tasklet` を直接実装し、Mapper の単発 SQL を1回呼ぶ。サンプル04 `SalesAggregateTasklet`・`AnnouncementPickupTasklet` と同型） | なし（基盤部品に「任意の一括更新 SQL を実行する汎用 Tasklet」は存在しないため業務側で実装する。`FileExistenceCheckTasklet` / `FileMoveTasklet` / `TableCountCheckTasklet` / `AbstractApiCallTasklet` はいずれも用途が異なる） |
| Reader / Processor / Writer | なし（Tasklet のため。データを Java 側へ持ち出さず DB 内で完結させる。architecture.md §3.1・§9.2） | - |
| Skip / Retry | **設定しない**（`faultTolerant()` を呼ばない）。業務エラー（Skip 対象）が存在しないため（基本設計書 §2・architecture.md §5.2） | - |
| リスナー | `loggingStepExecutionListener`（共通 Bean） | `LoggingStepExecutionListener` |
| トランザクション | Tasklet 全体で1トランザクション（`StepBuilder#tasklet(tasklet, transactionManager)` の既定。architecture.md §4） | - |

#### 処理フロー

1. **パラメータ取得**: Spring が `@StepScope` の `PointExpiryTasklet` を生成する際に、ジョブパラメータ
   `targetDate`（`java.time.LocalDate`）をコンストラクタへ注入する。Tasklet 内で `LocalDate.now()` 等の
   実行時刻からの導出は**行わない**（冪等性のため。基本設計書 §2 リラン設計）
2. **失効＋履歴登録（本処理）**: `PointGrantMapper#expireAndRecordHistory(targetDate)` を1回だけ呼び出す。
   この1メソッド＝1 SQL 文（データ変更 CTE）で、`app.point_grant` の失効更新と
   `app.point_expiry_history` への履歴登録が**原子的に**実行される（SQL 仕様は §4）。
   戻り値は SQL の affected rows＝外側 INSERT の行数＝**失効させた付与件数**
3. **件数の記録**: 失効件数を `contribution.incrementWriteCount(件数)` で `StepContribution` へ反映する
   （コードレビュー §7 参照。ユーザー確認のうえ反映する方針で確定）。これにより
   `BATCH_STEP_EXECUTION.WRITE_COUNT` および共通リスナー（`LoggingStepExecutionListener`）のログからも
   失効件数を確認できる。加えて、従来どおり INFO ログへも出力する
   （例: `ポイントを失効しました: 対象日={} 失効件数={}`）。運用監視はいずれの手段でも件数を確認できる。
   **0件でも WARN にせず INFO・正常終了**とする（対象なしの日が通常運用で発生するため）
4. **終了**: `RepeatStatus.FINISHED` を返す。Step 正常終了時に Spring Batch がトランザクションをコミットする
   （Tasklet 内で `commit` / `rollback` を手動実行してはならない。architecture.md §4）

- **例外時**: 2 で例外が発生した場合は握りつぶさずそのまま送出する。Spring Batch が Step を FAILED とし、
  トランザクションはロールバックされる（§3）
- **副作用**: Tasklet はファイル出力・外部 API 呼出を行わない（DB 更新のみ）

#### 入力チェック仕様

レコード単位の入力チェックは**なし**（外部ファイル・API を入力に持たず、処理対象は自システムの DB 行のみ。
Bean Validation 対象のドメインクラスも作らない）。検証はジョブパラメータのみを対象とする。

| 項目 | チェック内容 | 実装 | エラー時 |
|---|---|---|---|
| `targetDate` | 指定されていること（必須） | `JobParametersValidators.required("targetDate")`（Job の `.validator(...)`） | `JobParametersInvalidException` → Step 実行前にジョブ失敗（終了コード1）。§3 #2 |
| `targetDate` | `java.time.LocalDate` として解釈できること | Spring Batch のジョブパラメータ変換（起動引数で `targetDate=2026-08-10,java.time.LocalDate` と型を明示する）。アプリ側の追加実装は不要 | 起動時のパラメータ変換で失敗（終了コード1）。§3 #3 |

- **型サフィックスを省略した場合（`targetDate=2026-08-10`）の挙動**（システムテストで実機検証・2026-08-27に訂正）:
  パラメータは `String` として登録されるため `required("targetDate")` の必須検証は通過し、
  `@StepScope` の `PointExpiryTasklet` を生成する際に `String` → `LocalDate` への型変換が行われる。
  値が **ISO-8601 形式（`yyyy-MM-dd`）など `LocalDate` として解釈可能な文字列であれば、Spring Boot が
  登録する既定 ConversionService（JSR-310 対応）が暗黙変換に成功し、ジョブは正常完了する**
  （型サフィックスを付けた場合と同じ結果になる。誤りやすい点として、型サフィックス省略＝即失敗ではない）。
  一方、`LocalDate` として**解釈不能な文字列**（例: `not-a-date`）を渡した場合は Step 実行時の型変換で失敗し、
  終了コード1・`JobExecution` は FAILED として記録される（この場合のみ §3 #3 と異なり起動時ではなく Step 実行時の失敗）。
  機能的な実害はない（誤った日付として処理されることはない）ため実装は変更しないが、
  型サフィックスは運用上のケアレスミス防止のため引き続き必ず付けること（§1 起動コマンド）
- `JobParametersValidators.required(...)` は `DefaultJobParametersValidator(required, {})` を生成する。
  Spring Batch 6 の `DefaultJobParametersValidator#validate` は **optionalKeys が空の場合は未知キーの検査自体を行わず**
  （optionalKeys を指定した場合でも未知キーは WARN ログのみで例外にならない）、**必須キーの欠落のみ**を
  `InvalidJobParametersException` とする。したがって `targetDate` 以外のパラメータが混在しても検証は通る
  （結合テストが `testRunId` を付与して `JobInstance` を分けられるのはこの挙動による）。
  ただし余計なパラメータは `JobInstance` の同一性を変えてしまうため、
  スケジューラ（EventBridge）から渡す引数は `targetDate` のみとすること
- 業務的な妥当日付チェック（未来日の禁止等）は**行わない**。要件定義書・基本設計書に制約がなく、
  任意日での再処理（基本設計書 §2）を妨げないため

## 3. エラー処理設計

| # | エラー事象 | 分類 | 例外 | 動作 | 退避・出力先 |
|---|---|---|---|---|---|
| 1 | DB 接続断・テーブル/カラム不存在・SQL 文法エラー | システム | MyBatis/DataSource の例外（`org.springframework.dao.DataAccessException` 系。基盤部品の `SystemException` 相当の扱い） | Step 実行中に即時異常終了。トランザクション全ロールバック（失効・履歴とも未適用） | - |
| 2 | ジョブパラメータ `targetDate` 未指定 | システム（起動・設定不備） | `JobParametersInvalidException` | **Step 実行前**にジョブ失敗（DB は一切変更されない） | - |
| 3 | ジョブパラメータ `targetDate` の型・書式不正（`LocalDate` へ変換できない） | システム（起動・設定不備） | ジョブ起動時のパラメータ変換例外（Validator ではなく変換段階で失敗する） | **ジョブ起動時**に失敗 | - |
| 4 | 行ロック競合によるデッドロック（ポイント付与処理との同時実行） | システム | `DeadlockLoserDataAccessException` 等（`DataAccessException` 系） | 即時異常終了。全ロールバック。未失効分は次回実行（`expiry_date < targetDate`）でまとめて回収されるため取りこぼしなし。**デッドロックに至らない単純なロック待ちは下記補足・§7 #1 を参照** | - |
| 5 | 失効履歴の一意制約違反（`point_grant_id` 重複） | システム（設計上発生しない想定の二重防御） | `DuplicateKeyException`（`DataAccessException` 系） | 即時異常終了。全ロールバック。**リスタート前にデータ調査が必要**（既に履歴を持つ付与が `status='1'` に戻っていないか） | - |
| 6 | 二重起動（同一ジョブが実行中） | システム（運用） | `DuplicateRunPreventingJobExecutionListener` が送出 | ジョブ開始前に失敗 | - |
| 7 | 成功済みジョブの同一 `targetDate` 再実行 | 運用（正しい挙動） | `JobInstanceAlreadyCompleteException` | 起動失敗（誤操作防止。architecture.md §6.1） | - |
| 8 | ジョブパラメータ `targetDate` の**型サフィックス省略**かつ値が `LocalDate` として**解釈不能**（`String` として渡り型変換に失敗） | システム（起動・設定不備） | `@StepScope` Bean 生成時の型変換例外（`ConversionFailedException` を含む `BeanCreationException`） | **Step 実行時**に失敗。DB は一切変更されない（本処理前のため）。#3 と異なり `JobExecution` は FAILED として記録される | - |
| 9 | 前回実行がプロセス強制終了（ECS タスク停止・OOM 等）し、`BATCH_JOB_EXECUTION` が `STARTED` のまま残った状態での起動 | システム（運用） | `DuplicateRunPreventingJobExecutionListener` が送出（`JobRepository#findRunningJobExecutions` が残骸を実行中と判定するため） | ジョブ開始前に失敗。**リスタート前にメタデータの是正が必要**（§5） | - |

- **#4 の補足（ロック待ちの上限）**: デッドロックは PostgreSQL が検出して一方を中断するため #4 のとおり異常終了するが、
  単なる**ロック待ち**には上限がない（`lock_timeout` / `statement_timeout` の既定は無制限）。この場合ジョブは
  待ち続けて終了しない。上限は設けない方針で確定した（§7 #1。日次深夜実行のため競合可能性は低いと割り切る）
- **業務エラー（`BusinessException`）は発生しない**。外部入力を持たず、失効判定が `status` と `expiry_date` の
  比較のみで完結するため、レコード単位で「データを直せば解消する」種類のエラーが存在しない
  （要件定義書 §3.5・基本設計書 §2）
- **Skip は使用しない**（`skipPolicy` / `skipLimit` の設定対象なし）。したがって
  `BusinessSkipPolicy` / `ErrorRecordSkipListener` は使用せず、**`errorFile` ジョブパラメータも設けない**
- **警告終了（終了コード2）は発生しない**。共通 `loggingJobExecutionListener` の警告判定は「Skip 件数 > 0」であり、
  Skip を行わない本ジョブでは成立しない。終了コード規約（0/2/1）自体は変更しない（architecture.md §7）
- 想定される終了コードは **0（正常。失効0件を含む）** と **1（異常）** の2種類のみ
- Tasklet 内で例外を捕捉して `SystemException` へ包み直す実装は**行わない**。Spring の `DataAccessException`
  階層がそのまま「即時異常終了」の扱いになり、包み直しても挙動・終了コードが変わらず、
  元例外の情報を隠すだけになるため（基盤部品の例外階層 architecture.md §5.1 は Skip/Retry の判定に用いるものであり、
  本ジョブは Skip/Retry を使わない）

【設計上の注意（共通リスナーを継承しない）】: 警告判定を変えたい場合でも `LoggingJobExecutionListener` を
**継承した Bean を定義してはならない**。本プロジェクトは Uber-jar 方式（architecture.md §1.2）で全ジョブ Config が
単一 Spring コンテキストに同居し、`batch-core` の共通 Bean は `@ConditionalOnMissingBean`（戻り値型
`LoggingJobExecutionListener`）で定義されているため、サブクラス Bean を登録すると共通 Bean が登録されなくなり
他ジョブの注入が壊れる（ANNOUNCE-001 詳細設計 §3 で判明した既知の落とし穴）。本ジョブは共通 Bean をそのまま使うため
この問題は発生しないが、実装時に独自リスナーを追加しないこと。

## 4. SQL設計（Mapper XML 方針）

Mapper インタフェース: `com.example.batch.sample.job_point_expiry.PointGrantMapper`（`@Mapper`）
Mapper XML: `batch-sample/src/main/resources/mapper/PointGrantMapper.xml`（namespace は上記 FQCN）

| Mapper#メソッド | 種別 | SQL方針 |
|---|---|---|
| `PointGrantMapper#expireAndRecordHistory(@Param("targetDate") LocalDate targetDate)` → `int` | INSERT（データ変更 CTE） | 基本設計書 §2 の SQL をそのまま実装する。`WITH expired AS ( UPDATE app.point_grant SET status='2', expired_date=#{targetDate}, updated_at=now() WHERE status='1' AND expiry_date < #{targetDate} RETURNING point_grant_id, member_id, points, granted_date, expiry_date ) INSERT INTO app.point_expiry_history (point_grant_id, member_id, points, granted_date, expiry_date, expired_date, created_at) SELECT point_grant_id, member_id, points, granted_date, expiry_date, #{targetDate}, now() FROM expired`。戻り値は外側 INSERT の行数＝失効件数 |

- **MyBatis 要素は `<insert>` を使う**（`<update>` ではない）。XML の要素名は「更新系の実行方式」を指定するもので
  SQL の先頭語（`WITH`）とは無関係であり、`executeUpdate` の戻り値＝外側 INSERT の行数が Java へ返る。
  `useGeneratedKeys` は使用しない（採番値を Java 側で使わないため）
- `#{targetDate}` は同一 SQL 内に3箇所現れる。`@Param("targetDate")` を付与し、
  MyBatis 標準の `LocalDate` 型ハンドラでバインドする（アノテーション SQL は使わない。architecture.md §9.2）
- SQL は**スキーマ修飾必須**（`app.point_grant` / `app.point_expiry_history`）
- `expired_date` は履歴・付与とも**ジョブパラメータの `targetDate`** を格納する（実行時刻を使わない＝リラン安全）。
  監査列 `updated_at` / `created_at` のみ `now()`（実処理時刻）を使う
- Java 側でのループ・件数集計は行わない（DB 内完結。architecture.md §9.2）

### Flyway マイグレーション

ファイル: `batch-sample/src/main/resources/db/migration/V4__create_member_point_expiry_tables.sql`
（既存 V1〜V3 は変更禁止。`spring.flyway.locations=classpath:db/migration` のフラット配置に従う）

| オブジェクト | 定義方針（基本設計書 §4.2 準拠） |
|---|---|
| `app.point_grant` | `point_grant_id BIGINT PK`（本バッチは採番しない＝IDENTITY にしない）／`member_id BIGINT NOT NULL`（会員マスタはスコープ外のため FK なし）／`points INTEGER NOT NULL`／`granted_date DATE NOT NULL`／`expiry_date DATE NOT NULL`／`status VARCHAR(1) NOT NULL`（1=有効 / 2=失効）／`expired_date DATE NULL`／`created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`／`updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `app.point_expiry_history` | `point_expiry_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`／`point_grant_id BIGINT NOT NULL UNIQUE REFERENCES app.point_grant(point_grant_id)`／`member_id BIGINT NOT NULL`／`points INTEGER NOT NULL`／`granted_date DATE NOT NULL`／`expiry_date DATE NOT NULL`／`expired_date DATE NOT NULL`／`created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| インデックス | `idx_point_grant_status_expiry_date ON app.point_grant (status, expiry_date)`（**等値条件の `status` が先頭・範囲条件の `expiry_date` が後続**）／`idx_point_grant_member_id ON app.point_grant (member_id)`／`idx_point_expiry_history_member_id_expired_date ON app.point_expiry_history (member_id, expired_date)` |

- `created_at` / `updated_at` に `DEFAULT CURRENT_TIMESTAMP` を付ける理由: ポイント付与処理は本案件スコープ外で
  テスト・ローカルでは SQL 直接投入となるため（基本設計書 §4.1）。先例 `app.mail_send.created_at`（V3）と同じ方針。
  本バッチの UPDATE は `updated_at = now()` を明示指定するため DEFAULT に依存しない
- `point_expiry_history.point_grant_id` の一意制約は**単一列のため列定義にインラインで `UNIQUE`** と書く
  （V3 `app.mail_send` の `UNIQUE (announcement_id, user_id)` は複合列のため表制約で書いており、
  記法が異なるのは列数の違いによる。制約としての意味は同じ）。
  PostgreSQL が一意インデックスを自動生成するため、別途 `CREATE INDEX` は不要

### 設計検証（PostgreSQL 16 で実機確認済み・2026-08-09）

`docker/compose.yaml` の PostgreSQL 16 上に一時スキーマを作り、上記 DDL と SQL を実行して以下を確認した。

| # | 検証内容 | 結果 |
|---|---|---|
| 1 | 期限切れ（`expiry_date < targetDate`）かつ `status='1'` の付与のみが失効し、履歴が同数登録される（1対1） | OK（失効2件＝履歴2件） |
| 2 | **境界値**: `expiry_date = targetDate`（期限当日）の付与は失効**しない** | OK |
| 3 | `status='2'`（失効済み）の付与は再度対象にならない | OK |
| 4 | **冪等性**: 同一 `targetDate` で再実行すると affected rows = 0、履歴も増えない | OK |
| 5 | **自己回復**: 翌日 `targetDate` で実行すると、前日は期限当日だった付与が失効する | OK |
| 6 | **一意制約の防御**: 履歴を持つ付与を `status='1'` に戻して再実行すると `duplicate key ... point_grant_id` で失敗し、**文全体がロールバック**されて `point_grant` も更新されない | OK（§3 #5 の設計どおり） |
| 7 | **インデックス利用**: 5万件・失効対象200件（通常運用の選択率）で `Bitmap Index Scan on idx_point_grant_status_expiry_date` が選択される（UPDATE 側も同じプラン） | OK |

【設計上の注意（プランの選択率依存）】: 検証 7 と同じ条件でも、**対象行が表の半数以上**を占める場合は
PostgreSQL が Seq Scan を選ぶ（実測で確認済み）。これはプランナの正しい判断であり設計不備ではないが、
初回導入時など「既存の期限切れが一度に大量に失効する」実行では所要時間・トランザクションサイズが
通常運用と大きく異なる。基本設計書 §2 の方式見直し閾値（1回の失効対象が概ね10万件超）に該当しないかを
初回実行前に `SELECT count(*) FROM app.point_grant WHERE status='1' AND expiry_date < :targetDate` で確認すること。
**初回導入時の一括失効は対応不要と確定した（§7 #2。試走案件はテストデータ0件から開始する前提のため発生しない）。**

## 5. リラン設計

- **前提**: `spring-boot-starter-batch-jdbc` による JDBC ジョブリポジトリを使用する
  （Boot 4 既定のインメモリ実装ではリスタート不可。architecture.md §9.1。`batch-sample` は依存済み）
- **途中失敗時**: 同一 `targetDate` でリスタートする。Tasklet Step は Step 単位で**最初から**再実行されるが、
  失敗時にトランザクションが全ロールバックされているため中途半端な状態は残らない
  （Chunk のような「一部だけ失効済み」は原理的に発生しない）
- **冪等性**: 抽出条件 `status='1'` により、既に失効済みの付与は再実行時の対象に入らない（architecture.md §6.2）。
  履歴の `expired_date` にジョブパラメータの `targetDate` を格納するため、リラン時刻がずれても結果は不変。
  監査列（`point_grant.updated_at` / `point_expiry_history.created_at`）は実処理時刻のため実行ごとに変わるが、
  業務的な結果（失効状態・履歴の内容）は不変であるという意味での冪等性である（基本設計書 §2）
- **リスタートせず次回実行に委ねた場合の自己回復**: 抽出条件が不等号（`expiry_date < targetDate`）のため、
  数日実行できなかった場合でも次回実行が未失効分をまとめて回収する（取りこぼしなし。要件定義書 §3.4）
- **成功後の再処理**: 成功済み `targetDate` の再実行はエラーになるのが正しい挙動（architecture.md §6.1）。
  意図的な再処理は `targetDate` を変えて実行する
- **一意制約違反（§3 #5）で失敗した場合のみ例外**: 原因データを是正しない限りリスタートしても再発するため、
  `app.point_expiry_history` に履歴を持つ付与が `status='1'` に戻っていないか等のデータ調査を先に行う
  （基本設計書 §5 運用設計）
- **二重起動防止**: `DuplicateRunPreventingJobExecutionListener` ＋ EventBridge 側の同時実行抑止（多層防御）
- **プロセス強制終了（ECS タスク停止・OOM 等）後のリラン**: JVM が正常終了しなかった場合、
  `BATCH_JOB_EXECUTION` のステータスが `STARTED` のまま残る。本ジョブは
  `DuplicateRunPreventingJobExecutionListener`（内部で `JobRepository#findRunningJobExecutions` を参照）を
  登録しているため、**この残骸がある限り次回起動は二重起動と判定されて失敗する**（§3 #9）。
  リラン前に当該 `JobExecution` を実行中でない状態（`FAILED` / `ABANDONED`）へ是正する運用手順が必要。
  なお DB トランザクションはプロセス終了時点で自動ロールバックされるため、業務データの後始末は不要

## 6. テスト観点メモ（テスト工程への引き継ぎ）

### 単体テスト（`PointExpiryTaskletTest`、JUnit 5 + Mockito）

- `PointGrantMapper#expireAndRecordHistory` に**注入された `targetDate` がそのまま渡る**こと
  （実行時刻から導出していないこと。冪等性の要）
- Mapper の戻り値が 0 でも例外にせず `RepeatStatus.FINISHED` を返すこと（対象0件＝正常）
- Mapper が例外を送出した場合、Tasklet が握りつぶさずそのまま送出すること（§3 の即時異常終了）
- Mapper 呼出が**1回のみ**であること（ループ・分割実行をしていないこと）

### 結合テスト（`PointExpiryJobIT`、`@SpringBatchTest` + Testcontainers PostgreSQL 16）

- **境界値**: `expiry_date < targetDate` は失効し、`expiry_date = targetDate`（期限当日）は失効しないこと
- **対象外**: `status='2'`（失効済み）の付与が再度対象にならないこと。`expiry_date` が未来の付与が対象外であること
- **1対1整合性**: 失効した付与の件数＝登録された履歴の件数であり、履歴の各列
  （`member_id` / `points` / `granted_date` / `expiry_date`）が付与のスナップショットと一致すること
- **`expired_date`**: 付与・履歴の双方に**ジョブパラメータの `targetDate`** が入ること（実行日ではないこと）
- **0件時**: 失効対象0件でもジョブが `COMPLETED`（終了コード0）で終了すること
- **冪等性**: 同一 `targetDate` で `JobInstance` を作り直して再実行しても履歴が二重登録されないこと
  （成功済み同一パラメータの再実行は起動エラーになるため、テストではメタデータをクリアするか
  失敗→リスタートの形で検証する）
- **リスタート**: Step を意図的に失敗させた後、同一 `targetDate` でリスタートして完走し、
  失効・履歴が正しく1回だけ適用されること（失敗時に部分適用が残っていないことも確認）
- **原子性（重要）**: 履歴 INSERT が失敗するようなデータ（履歴を持つ付与を `status='1'` に戻す）を仕込んで実行し、
  ジョブが `FAILED`（終了コード1）となり、かつ `app.point_grant` の `status` が**更新されていない**こと
  （データ変更 CTE の原子性の回帰確認。§4 検証6）
- **パラメータ検証**: `targetDate` 未指定でジョブ起動が失敗すること（`JobParametersInvalidException`）
- **終了コード**: 正常時0・異常時1のみが発生し、**2（警告）が発生しないこと**
- **自己回復**: 数日空けた `targetDate`（例: 期限が3日前・2日前・1日前の付与が混在）で1回実行すると
  すべてまとめて失効すること

### アプリケーション全体

- 本ジョブ追加後も `SpringBootTest` で全ジョブ Config を含むコンテキストが起動し、
  他ジョブの共通リスナー Bean 注入が壊れないこと（Bean 型衝突の予防）

### システムテスト観点（運用手順の確認。§3 #8・#9）

- 起動引数の型サフィックス（`,java.time.LocalDate`）を省略した場合、ISO-8601形式の値なら暗黙変換されジョブは正常完了し、
  `LocalDate` として解釈不能な値の場合のみ Step 実行時に失敗し終了コード1となること（§2 参照。システムテストで実機検証済み）
- `BATCH_JOB_EXECUTION` が `STARTED` のまま残った状態では二重起動防止で起動できず、
  当該実行を是正してからリスタートできること

## 7. 保留事項（ユーザー確認済み）

本詳細設計の作成・レビュー中に判明した事項。いずれもユーザーに確認のうえ以下のとおり確定した（2026-08-09）。

| # | 事項 | 決定内容 |
|---|---|---|
| 1 | ロック待ちの上限設定（`lock_timeout` / `statement_timeout`） | **現状維持で確定**。設定しない（PostgreSQL 既定＝無制限のまま）。日次深夜実行のため付与処理との競合可能性は低いと割り切る（基本設計書 §2 の判断を踏襲）。実案件化する場合は競合実績を見て `SET LOCAL lock_timeout` 等の追加を再検討する |
| 2 | 初回導入時の一括失効の扱い | **対応不要で確定**。試走案件はテストデータ0件から開始する前提のため発生しない。§4 に記載した確認手順（`SELECT count(*) ...`）は実案件化する場合の参考として残す |
| 3 | Flyway のマイグレーション配置が方式設計書の記載と異なる | **architecture.md §9.1 を実態（フラット配置）に合わせて改訂済み**（本パイプライン内で対応）。本詳細設計（`V4__...` をフラット配置）は改訂後の §9.1 と整合する |
| 4 | 失効件数を `StepExecution.writeCount` に反映するか（コードレビュー指摘） | **反映する方針で確定**。`contribution.incrementWriteCount(件数)` を追加し、`BATCH_STEP_EXECUTION.WRITE_COUNT`・共通リスナーのログからも件数を確認できるようにした（業務ログ出力は維持）。§1・§2 を修正 |
| 5 | 型サフィックス省略時、ISO-8601形式なら暗黙変換されジョブが正常完了する（システムテストで判明。§2・§3#8 の従来記述は誤り） | **現状維持（実装変更なし）で確定**。機能的な実害はないため、パラメータ検証ロジックの追加は行わず、§2・§3#8・§6 の記述のみ実態に合わせて訂正した。型サフィックスは運用上のケアレスミス防止のため引き続き必須とする |

## 8. 変更履歴

| 日付 | 版 | 変更内容 |
|---|---|---|
| 2026-08-09 | 1.0 | 初版作成（基本設計書 1.1 版に基づく。DDL・SQL を PostgreSQL 16 で実機検証） |
| 2026-08-09 | 1.1 | 設計レビュー指摘反映: 処理量・所要時間の見込みと起動コマンド（型サフィックス必須）を §1 に追記、ジョブパラメータの型サフィックス省略時の挙動を §2・§3 #8 に追記、プロセス強制終了で `STARTED` が残った場合の二重起動判定とリラン前の是正手順を §3 #9・§5 に追記、`UNIQUE` 制約の記法に関する先例参照を訂正（V3 は複合列のため表制約）、システムテスト観点を追記、保留事項（ロック待ち上限・初回一括失効・Flyway 配置）を §7 として明記、ステータスをレビュー済みへ更新 |
| 2026-08-09 | 1.2 | §7 の保留事項3件をユーザーに確認し確定（ロック待ち上限＝現状維持／初回一括失効＝対応不要／Flyway配置＝architecture.md §9.1 を実態に合わせて改訂）。ステータスを確定へ更新 |
| 2026-08-18 | 1.3 | コードレビュー指摘反映: §2 の `DefaultJobParametersValidator` の挙動記述を訂正（optionalKeys 空の場合は未知キーを検査せず、必須キー欠落のみを例外とする。「未知キーを許容しない」は誤り） |
| 2026-08-18 | 1.4 | コードレビュー指摘へのユーザー回答を反映: 失効件数を `contribution.incrementWriteCount(件数)` で `StepContribution` へ反映する実装に変更し、§1・§2 の記述を更新。§7 に保留事項 #4 として追記 |
| 2026-08-27 | 1.5 | システムテストで判明した事実（型サフィックス省略でもISO-8601形式なら暗黙変換され正常完了する）へのユーザー回答を反映: §2・§3#8・§6 の記述を実態に合わせて訂正（実装変更なし）。§7 に保留事項 #5 として追記 |
