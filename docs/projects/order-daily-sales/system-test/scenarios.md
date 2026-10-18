# システムテストシナリオ・実施記録: 受注CSV取込・日次売上集計バッチ（order-daily-sales）

| 項目 | 内容 |
|---|---|
| 対象ジョブ | ORDER-001 `orderDailySalesJob`（`batch-sample/src/main/java/com/example/batch/sample/job_order_daily_sales/`） |
| 入力 | `docs/projects/order-daily-sales/requirements.md`／`basic-design.md`（1.4版）／`detail-design/ORDER-001.md`（1.4版） |
| 実施環境 | ローカル（`docker compose -f docker/compose.yaml up -d` の PostgreSQL 16、`./mvnw -DskipTests package` で作った `batch-sample/target/batch-sample-0.1.0-SNAPSHOT.jar` を `--spring.profiles.active=local` で直接起動。Java 25 Temurin 系 `/opt/jdk-25`）。終了コードはプロセスの終了コード |
| 実施期間 | 2026-10-12（No.1・2・4〜6）、2026-10-18（No.3・7） |
| テストデータ | `docs/projects/order-daily-sales/system-test/data/` のスクリプト（個人情報を含まない生成データ。実行時のファイルはリポジトリ外 `/tmp/claude/P-20261018-order001-restart-redo-T-05/` に置く。No.1・2・4〜6 の証跡のパスは 2026-10-12 の実施時の `/tmp/claude/P-20261012-order-daily-sales-T-05/`）: `env.sh`（共通環境）、`make_scenario_files.sh`（No.1〜5・7 の入力 CSV）、`gen_orders.sh`（件数指定の CSV 生成。No.3/4/6/7）、`run_job.sh`（ジョブ起動）、`expected.sh`（CSV からの期待集計の独立計算）、`actual.sql`・`checksum.sql`・`checksum_all.sql`（DB 側の確認クエリ）、`fail_trigger_on.sql`・`fail_trigger_off.sql`（No.3・7(a) の Step 4 の障害注入）、`fail_step5_on.sql`・`fail_step5_off.sql`（No.7(b) の Step 5 の障害注入）、`hold_lock.sql`（No.4 の実行中状態の保持） |

実施手順の共通部分: `make_scenario_files.sh` で入力を作り、`run_job.sh <ログ> scheduledTime=...`（`--sample.order-daily-sales.input-dir`・`error-dir` を実行時ディレクトリの `in`・`error` に向ける）で起動する。No.1・2・4〜6 は DB が新規（Flyway V1〜V5 適用直後）の状態から No.1 → 6 の順に実施した。No.3・7 は 2026-10-18 に、新規の PostgreSQL 16 コンテナ（`docker compose -f docker/compose.yaml down -v` の後に起動し、Flyway 適用だけ済ませた状態）で No.3 → No.7 の順に実施した（1.4 版の jar を `./mvnw -DskipTests package` で作成。実行時の Java は Temurin ではなく OS 同梱の OpenJDK 25.0.4）。

## シナリオ一覧

| No | 分類 | シナリオ名 | 優先度 | 結果 |
|---|---|---|---|---|
| 1 | 業務 | 日次実行の正常系（EventBridge 想定の起動。省略パラメータの補完・既定パス取込・入力との突合） | 高 | OK |
| 2 | 運用 | 警告終了の運用（不正行 → 終了コード2 → エラーファイル確認 → 訂正ファイルで再実行 → 洗い替え） | 高 | OK |
| 3 | 運用 | 異常終了 → リラン（Step 4 の途中で失敗 → 原因除去 → 同一パラメータで最初から取り込み直して完走） | 高 | OK |
| 4 | 運用 | 二重起動の防止（終了コード1） | 高 | OK |
| 5 | 運用 | 入力不備での異常終了（ファイルなし・ヘッダ不正で終了コード1、本テーブル不変） | 高 | OK |
| 6 | 運用 | 処理時間（10万件） | 高 | OK |
| 7 | 運用 | 別の実行回をまたぐリスタート（Issue #74 の再現。Step 4 で失敗した A・Step 5 で失敗した A を、別の対象日の B の完了後にリスタート） | 高 | OK |

全7シナリオOK。不具合（期待結果と違う動作）は検出されなかった。

No.1・2・4〜6 は 1.3 版の実装で 2026-10-12 に実施した結果。1.4 版の変更（リスタートと Step 3 のエラーファイル削除）の影響は結合テスト（`OrderDailySalesJobIT`）で確認した。No.3・7 は 1.4 版の実装で 2026-10-18 に実施した。

---

## No.1: 日次実行の正常系

- **目的**: EventBridge Scheduler 想定の起動（`scheduledTime` に UTC のみを渡し、`targetDate`・`inputFile`・`errorFile` を省略）で、前日分が既定パスから取り込まれ、明細・集計が入力 CSV と突合して一致し、終了コード0になることを確認する（詳細設計書 §1.1・§7「システムテスト」2点目）
- **事前条件**: DB は Flyway 適用直後で `app.order_detail`・`app.daily_store_sales` が空。`make_scenario_files.sh` で `in/orders_20261011.csv`（3店舗・12明細・受注8件。数量・単価の境界値 `999999`×`999999999`、単価0を含む）を既定ディレクトリに配置済み。`error/` は空
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `run_job.sh no1.log scheduledTime=2026-10-11T17:00:00Z`（JST では 2026-10-12 02:00） | 終了コード0 | OK（exit=0。executionId=1 COMPLETED） |
| 2 | ログの「実行時パラメータを確定しました」を確認 | targetDate=2026-10-11（UTC の日付ではなく JST の前日）、inputFile=`in/orders_20261011.csv`、errorFile=`error/orders_20261011_20261012020000_error.txt` | OK（下記ログ抜粋のとおり） |
| 3 | `app.order_detail`（対象日）の件数と受注件数 | 12明細・受注8件 | OK（12／8） |
| 4 | `app.daily_store_sales` と、CSV から `expected.sh` で独立に計算した期待値を diff | 店舗別の受注件数・明細件数・数量合計・金額合計が一致 | OK（diff なし。S001 の金額合計 999998999004001 の桁あふれなし） |
| 5 | `error/` の内容 | エラーファイルは作られない | OK（空のまま） |

- **確認ポイント**: 省略パラメータの補完（targetDate は `scheduledTime` を JST に変換した日の前日）、既定パスからの取込、入力 CSV との件数・数量・金額の突合、受注件数が受注番号の重複を除いた数であること、エラーなしで終了コード0、エラーファイルが作られないこと
- **実施結果**: OK
- **証跡**:
```
$ run_job.sh no1.log scheduledTime=2026-10-11T17:00:00Z        -> exit=0
OrderDailySalesJobConfig : 実行時パラメータを確定しました: targetDate=2026-10-11
  inputFile=/tmp/claude/P-20261012-order-daily-sales-T-05/in/orders_20261011.csv
  errorFile=/tmp/claude/P-20261012-order-daily-sales-T-05/error/orders_20261011_20261012020000_error.txt
Step終了: stepName=orderDailySales.04ImportStep status=COMPLETED readCount=12 writeCount=12 skipCount=0 commitCount=1
OrderDailySalesReplaceTasklet : 日次売上を入れ替えました: 対象日=2026-10-11 明細削除=0 集計削除=0 明細登録=12 集計登録=3
ジョブ終了: jobName=orderDailySalesJob executionId=1 status=COMPLETED exitCode=COMPLETED readCount=12 writeCount=24 skipCount=0

-- 期待値（expected.sh）と actual.sql の出力（store_code|受注件数|明細件数|数量合計|金額合計）。diff の結果は差分なし
S001|3|6|1000017|999998999004001
S002|3|4|12|6600
S003|2|2|6|6500
-- select count(*), count(distinct order_no) from app.order_detail where order_date='2026-10-11'
12|8
-- ls error/ （空）
```

---

## No.2: 警告終了の運用

- **目的**: 不正行を含むファイルで警告終了（終了コード2）となり、エラーファイルで原因を確認し、訂正ファイルを新しい `scheduledTime`・同じ `targetDate` で再実行すると、洗い替えで訂正後の内容になり、エラーファイルが前回とは別名になることを確認する（詳細設計書 §5「成功後の再処理」）
- **事前条件**: No.1 実施後の状態。`in/orders_20261012.csv` に不正行3件（数量が文字列＝形式不正、受注日が対象日と不一致、受注番号＋明細番号の重複）を含む6明細のファイルを配置（`work/orders_20261012_bad.csv` のコピー）
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `run_job.sh no2a.log scheduledTime=2026-10-12T17:00:00Z`（targetDate は 2026-10-12 に補完される） | 終了コード2。正常3明細だけ取り込まれる | OK（exit=2。executionId=2、exitCode=COMPLETED_WITH_WARNING、readCount=6 writeCount=3 skipCount=3） |
| 2 | `error/orders_20261012_20261013020000_error.txt` を確認 | 3件が元の行番号（`line=`）と理由付きで出力される | OK（line=3 形式不正／line=4 対象日不一致／line=6 重複。下記） |
| 3 | `app.order_detail` の 2026-10-12 分 | B0001-1、B0003-1、B0004-1 の3明細のみ | OK（3明細を確認） |
| 4 | 訂正ファイル（`work/orders_20261012_fixed.csv`）を `in/orders_20261012.csv` に置き換え、`run_job.sh no2b.log scheduledTime=2026-10-12T20:00:00Z`（新しい scheduledTime、targetDate は同じ 2026-10-12 に補完） | 終了コード0。errorFile の既定名が前回と別（`..._20261013050000_error.txt`） | OK（exit=0。ログの errorFile=`orders_20261012_20261013050000_error.txt`） |
| 5 | `app.order_detail`・`app.daily_store_sales` の 2026-10-12 分と、訂正ファイルからの期待集計 | 洗い替えで訂正後の6明細・3店舗の集計になり、前回の内容は残らない。他の日（2026-10-11）は不変 | OK（6明細、期待値と diff なし、2026-10-11 は 12明細のまま） |
| 6 | `error/` の内容 | 前回のエラーファイルは変更されず残り、2回目は新しいエラーファイルを作らない | OK（前回分1ファイルのみ・更新時刻不変） |

- **確認ポイント**: Skip 発生で終了コード2（警告）になること、エラーファイルの行番号・理由、正常行だけが取り込まれること、新しい `scheduledTime` による再実行が別ジョブインスタンスとして実行できること、洗い替えで訂正後の内容になること、他の日のデータに影響しないこと
- **実施結果**: OK
- **証跡**:
```
$ run_job.sh no2a.log scheduledTime=2026-10-12T17:00:00Z        -> exit=2
Step終了: stepName=orderDailySales.04ImportStep status=COMPLETED readCount=6 writeCount=3 skipCount=3 commitCount=1
ジョブ終了: jobName=orderDailySalesJob executionId=2 status=COMPLETED exitCode=COMPLETED_WITH_WARNING readCount=6 writeCount=6 skipCount=3

-- error/orders_20261012_20261013020000_error.txt
line=3,B0001,2,2026-10-12,S001,I0002,abc,1200	入力チェックエラー: quantity must match "[1-9][0-9]{0,5}"
line=4,B0002,1,2026-10-11,S002,I0001,3,500	受注日が対象日と一致しません: orderDate=2026-10-11 targetDate=2026-10-12
line=6,B0003,1,2026-10-12,S002,I0004,1,90	受注番号＋明細番号が重複しています: orderNo=B0003 lineNo=1 先頭の行=5

-- 1回目後 select order_no,line_no,quantity from app.order_detail where order_date='2026-10-12'
B0001|1|2
B0003|1|4
B0004|1|2

$ run_job.sh no2b.log scheduledTime=2026-10-12T20:00:00Z        -> exit=0
実行時パラメータを確定しました: targetDate=2026-10-12 ... errorFile=.../error/orders_20261012_20261013050000_error.txt

-- 2回目後（訂正後の内容）
B0001|1|2   B0001|2|7   B0002|1|3   B0003|1|4   B0003|2|1   B0004|1|2
-- expected.sh（訂正ファイル）と actual.sql は同一（差分なし）
S001|1|2|9|9400
S002|2|3|8|1910
S003|1|1|2|1400
-- 2026-10-11 分の明細件数: 12
-- ls -l error/: orders_20261012_20261013020000_error.txt のみ（376 bytes）
```

---

## No.3: 異常終了 → リラン

- **目的**: Step 4 の途中（後半のチャンク）で失敗した後、原因を除いて同一パラメータでリスタートすると、Step 1〜4 も再実行されてワークを作り直し、Step 4 が入力ファイルの先頭から取り込み直して完走し、1回で成功した場合と同じ結果になることを確認する（詳細設計書 §5）。1.4 版で書き直したシナリオ（2026-10-18 に再実施）
- **事前条件**: `in/orders_20261013.csv`（500明細・5店舗、チャンクサイズ100で5チャンク。不正行なし）を既定パスに配置。DB は Flyway 適用直後で `app.order_detail`・`app.daily_store_sales` が空。`fail_trigger_on.sql` を適用し、`app.order_detail_work` への INSERT が `line_seq >= 350` の行で失敗するようにする（障害注入）
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `fail_trigger_on.sql` を適用し、`run_job.sh no3a.log scheduledTime=2026-10-13T17:00:00Z` | 終了コード1。Step 4 の4チャンク目で失敗し、本テーブルは不変 | OK（exit=1。executionId=2 FAILED、`forced failure at line_seq=350`） |
| 2 | ワークと本テーブルの状態 | ワークにコミット済みの3チャンク（300行）が残り、対象日の本テーブルは0件 | OK（work=300件・最大 line_seq=301、order_detail=0件） |
| 3 | `fail_trigger_off.sql` で障害を除去し、同一パラメータ（`scheduledTime=2026-10-13T17:00:00Z`）で再実行 | 終了コード0。Step 1〜4 も再実行され、Step 4 はファイルの先頭から取り込み直す。エラーファイルに同じ行が重複しない。最終結果が1回で成功した場合と同じ | OK（exit=0。executionId=3 で Step 1〜5 がすべて実行され、Step 4 は read=500 write=500 commit=5。ワークの初期化のログあり。エラーファイルは作られていない） |
| 4 | 本テーブルのチェックサム（`checksum.sql`）と、CSV からの期待集計（`expected.sh`）を比較 | 500明細・5店舗の集計が入力と一致 | OK（期待値と diff なし） |
| 5 | 参照として、障害なしで新しい `scheduledTime=2026-10-13T20:00:00Z` により洗い替えを1回で実行し、チェックサムを比較 | リスタート後のチェックサムと同一（1回で成功した場合と同じ結果） | OK（exit=0。`order_detail` 500件・`daily_store_sales` 5件とも md5 が一致） |

- **確認ポイント**: 失敗時の終了コード1、コミット済みチャンクがワークに残ること、リスタートが同一パラメータで Step 1 から全 Step を実行し Step 4 が入力ファイルの先頭（500行すべて）から取り込むこと（実行回の step 実行履歴で確認）、Step 3 がワークを空にしてから取り込み直すこと（取込件数が失敗時の続き分ではなく500であること）、最終結果が1回成功と同じになること、障害注入は SQL（トリガ）だけで本番コードに手を入れていないこと。入力に不正行を含めていないため、エラーファイルはどちらの実行でも作られない（Skip 行のエラーファイルが重複しないことは結合テストで確認している）
- **実施結果**: OK
- **証跡**:
```
$ psql -f fail_trigger_on.sql                                  -> CREATE FUNCTION / CREATE TRIGGER
$ run_job.sh no3a.log scheduledTime=2026-10-13T17:00:00Z        -> exit=1
ジョブ終了: jobName=orderDailySalesJob executionId=2 status=FAILED exitCode=FAILED readCount=400 writeCount=300 skipCount=0
Caused by: org.postgresql.util.PSQLException: ERROR: system-test: forced failure at line_seq=350
-- select 'work',count(*),max(line_seq) from app.order_detail_work ; select 'detail',count(*) from app.order_detail where order_date='2026-10-13'
work|300|301
detail|0
-- ls error/ （空）

$ psql -f fail_trigger_off.sql                                 -> DROP TRIGGER / DROP FUNCTION
$ run_job.sh no3b.log scheduledTime=2026-10-13T17:00:00Z        -> exit=0
Step終了: stepName=orderDailySales.01FileCheckStep status=COMPLETED ... commitCount=1
Step終了: stepName=orderDailySales.02HeaderCheckStep status=COMPLETED ... commitCount=1
OrderDetailWorkClearTasklet : 受注明細ワークを初期化しました: errorFile=.../error/orders_20261013_20261014020000_error.txt 削除=false
Step終了: stepName=orderDailySales.04ImportStep status=COMPLETED readCount=500 writeCount=500 skipCount=0 commitCount=5
OrderDailySalesReplaceTasklet : 日次売上を入れ替えました: 対象日=2026-10-13 明細削除=0 集計削除=0 明細登録=500 集計登録=5
ジョブ終了: jobName=orderDailySalesJob executionId=3 status=COMPLETED exitCode=COMPLETED readCount=500 writeCount=1000 skipCount=0

-- batch.batch_step_execution（job_execution_id 1 は DB 初期化用の入力ファイルなし起動。2 が失敗した実行、3 がリスタート）: job_execution_id|step|status|read|write|commit
2|orderDailySales.01FileCheckStep|COMPLETED|0|0|1
2|orderDailySales.02HeaderCheckStep|COMPLETED|0|0|1
2|orderDailySales.03ClearWorkStep|COMPLETED|0|0|1
2|orderDailySales.04ImportStep|FAILED|400|300|3
3|orderDailySales.01FileCheckStep|COMPLETED|0|0|1
3|orderDailySales.02HeaderCheckStep|COMPLETED|0|0|1
3|orderDailySales.03ClearWorkStep|COMPLETED|0|0|1
3|orderDailySales.04ImportStep|COMPLETED|500|500|5
3|orderDailySales.05ReplaceStep|COMPLETED|0|500|1

-- checksum.sql（リスタート後）                       -- checksum.sql（参照: 障害なし1回の洗い替え後、executionId=4）
order_detail|500|42bdb4030870edaa6183f381911ed69c        order_detail|500|42bdb4030870edaa6183f381911ed69c
daily_store_sales|5|73ba74ea1123277bf70156e535898560     daily_store_sales|5|73ba74ea1123277bf70156e535898560
-- expected.sh と actual.sql の diff: 差分なし（EXPECTED_SAME）
S001|86|255|1355|3487700
S002|38|121|677|1619900
S003|22|61|290|849700
S004|16|57|323|927500
S005|2|6|21|74700
```

---

## No.4: 二重起動の防止

- **目的**: ジョブの実行中に同一ジョブを起動すると、二重起動として検知され終了コード1で中止されること、先に動いていたジョブには影響しないことを確認する（詳細設計書 §3 #3）
- **事前条件**: `in/orders_20261014.csv`（30,000明細）を既定パスに配置。`hold_lock.sql`（`psql -v sec=45`）で `app.order_detail_work` を排他ロックし、先に起動するジョブを Step 3（TRUNCATE）で待たせて実行中（STARTED）の状態を保つ
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `hold_lock.sql` を実行（バックグラウンド）、続けて `run_job.sh no4_first.log scheduledTime=2026-10-14T19:00:00Z` を起動 | 先のジョブが Step 3 で待機し、`batch_job_execution` が STARTED のまま | OK（executionId=9 が STARTED） |
| 2 | 実行中に別の `scheduledTime`（`2026-10-14T19:05:00Z`）で `run_job.sh no4_second.log` を起動 | 二重起動として検知され、終了コード1。Step は実行されない | OK（exit=1。executionId=10 FAILED、「二重起動を検知しました」） |
| 3 | ロック解放後、先のジョブの終了を待つ | 先のジョブは影響を受けず正常に完走（終了コード0）し、30,000明細が取り込まれる | OK（exit=0。executionId=9 COMPLETED、elapsedMs=41517 のうち約40秒はロック待ち、order_detail の 2026-10-14 分=30000件） |

- **確認ポイント**: 二重起動の検知ログと終了コード1、検知されたジョブが業務データに触れないこと、先行ジョブが無傷で完走すること
- **実施結果**: OK
- **証跡**:
```
-- 手順2直前: select job_execution_id, status from batch.batch_job_execution order by 1 desc limit 2
8|COMPLETED
7|COMPLETED                  （この時点で executionId=9 が起動し Step 3 のロック待ち）

$ run_job.sh no4_second.log scheduledTime=2026-10-14T19:05:00Z   -> exit=1
DuplicateRunPreventingJobExecutionListener : 二重起動を検知しました: jobName=orderDailySalesJob 実行中executionIds=[9, 10]
SystemException: ジョブ orderDailySalesJob は既に実行中のため起動を中止します（二重起動防止）
ジョブ終了: jobName=orderDailySalesJob executionId=10 status=FAILED exitCode=FAILED readCount=0 writeCount=0 skipCount=0 elapsedMs=41

-- 手順2直後
10|FAILED|FAILED
9|STARTED|UNKNOWN

-- ロック解放後（no4_first.log、exit=0）
ジョブ終了: jobName=orderDailySalesJob executionId=9 status=COMPLETED exitCode=COMPLETED readCount=30000 writeCount=60000 skipCount=0 elapsedMs=41517
10|FAILED|FAILED
9|COMPLETED|COMPLETED
8|COMPLETED|COMPLETED
-- select count(*) from app.order_detail where order_date='2026-10-14'
30000
```

---

## No.5: 入力不備での異常終了

- **目的**: 入力ファイルがない場合・ヘッダが不正な場合に、終了コード1で異常終了し、本テーブル（受注明細・日次売上集計）が不変であることを確認する（詳細設計書 §3 #4・#5）
- **事前条件**: No.1〜4 実施後の状態（`app.order_detail`=30,518件、`app.daily_store_sales`=21件）。`checksum_all.sql` で全件のチェックサムを取得しておく。ヘッダ不正ファイル（`quantity` 列が `qty`）を `in/orders_20261015.csv` に配置
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `run_job.sh no5a.log scheduledTime=2026-10-15T17:00:00Z inputFile=<存在しないパス>` | 終了コード1。Step 1 で「入力ファイルが存在しません」 | OK（exit=1。executionId=11、`01FileCheckStep` FAILED） |
| 2 | `run_job.sh no5b.log scheduledTime=2026-10-15T17:00:00Z`（既定パスのヘッダ不正ファイル） | 終了コード1。Step 1 は成功、Step 2 で「ヘッダ行が想定と異なります」 | OK（exit=1。executionId=12、`02HeaderCheckStep` FAILED） |
| 3 | 本テーブルのチェックサムを実行前と比較 | 件数・チェックサムとも不変 | OK（実行前後とも 30518 件／21 件で md5 同一） |

- **確認ポイント**: ファイルなしとヘッダ不正のどちらも終了コード1、失敗した Step 以降（取込・本テーブル更新）が実行されないこと、メッセージに原因（パス・期待ヘッダと実際のヘッダ）が含まれること
- **実施結果**: OK
- **証跡**:
```
-- 実行前 psql -At -f checksum_all.sql
order_detail|30518|caa2d73ce26d6ae993613d75b305e082
daily_store_sales|21|91a2a52ad03b345973c0c920ba682896

$ run_job.sh no5a.log scheduledTime=2026-10-15T17:00:00Z inputFile=.../in/not_exist.csv   -> exit=1
BusinessException: 入力ファイルが存在しません: /tmp/claude/P-20261012-order-daily-sales-T-05/in/not_exist.csv
Step終了: stepName=orderDailySales.01FileCheckStep status=FAILED ... rollbackCount=1
ジョブ終了: jobName=orderDailySalesJob executionId=11 status=FAILED exitCode=FAILED

$ run_job.sh no5b.log scheduledTime=2026-10-15T17:00:00Z                                   -> exit=1
Step終了: stepName=orderDailySales.01FileCheckStep status=COMPLETED
BusinessException: ヘッダ行が想定と異なります: file=.../in/orders_20261015.csv 期待=order_no,line_no,order_date,store_code,item_code,quantity,unit_price 実際=order_no,line_no,order_date,store_code,item_code,qty,unit_price
Step終了: stepName=orderDailySales.02HeaderCheckStep status=FAILED ... rollbackCount=1
ジョブ終了: jobName=orderDailySalesJob executionId=12 status=FAILED exitCode=FAILED

-- 実行後 psql -At -f checksum_all.sql（実行前と同一）
order_detail|30518|caa2d73ce26d6ae993613d75b305e082
daily_store_sales|21|91a2a52ad03b345973c0c920ba682896
```

---

## No.6: 処理時間（10万件）

- **目的**: 要件の上限である10万件のファイルで、ジョブ全体と Step 5（本テーブルの入れ替え＋集計）の所要時間が制約（05:00 までに完了＝10万件で3時間以内）に収まることを確認する（詳細設計書 §7「システムテスト」1点目、基本設計書 §2 の見込み）
- **事前条件**: `gen_orders.sh 2026-10-16 100000 in/orders_20261016.csv 50` で10万明細・50店舗（店舗番号が小さいほど件数が多い偏り。S001 は9,948明細）のファイルを作成。DB は No.5 実施後の状態（ローカルの PostgreSQL 16 コンテナ、チャンクサイズ既定100）
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | `run_job.sh no6.log scheduledTime=2026-10-16T17:00:00Z`（初回取込） | 終了コード0。ジョブ全体が3時間以内 | OK（exit=0。ジョブ全体 7.62 秒、Step 4=6.53 秒、Step 5=0.87 秒） |
| 2 | 件数・集計を入力と突合 | 100,000明細・50店舗の集計が `expected.sh` と一致 | OK（明細登録=100000、集計登録=50、diff なし） |
| 3 | 同じ対象日を新しい `scheduledTime=2026-10-16T20:00:00Z` で再実行（Step 5 が10万件の削除＋10万件の登録を行う最大負荷の場合） | 終了コード0。同様に3時間以内。結果は同一 | OK（exit=0。ジョブ全体 8.09 秒、Step 5=1.40 秒、起動から終了までの実時間 12.4 秒、集計は期待値と diff なし） |

- **確認ポイント**: 実測は初回取込でジョブ全体 7.62 秒・Step 5 が 0.87 秒、洗い替え（10万件削除＋10万件登録）で全体 8.09 秒・Step 5 が 1.40 秒。要件の制約（10万件で3時間以内）に対し十分な余裕があり、チャンクサイズ（100）の調整は不要。基本設計書の見込み（全体で十数分以内・Step 5 は数十秒以内）も下回っている（所見: 見込みより大幅に短い。ローカルの同一ホスト上の DB で測っているため、本番相当の Aurora ではネットワーク往復分の増加を見込むが、3時間の制約との差は3桁以上ある）。所要時間は `batch.batch_job_execution`・`batch_step_execution` の開始・終了時刻から取った
- **実施結果**: OK
- **証跡**:
```
$ gen_orders.sh 2026-10-16 100000 in/orders_20261016.csv 50     -> 100001 行（ヘッダ含む）
$ run_job.sh no6.log scheduledTime=2026-10-16T17:00:00Z          -> exit=0
Step終了: stepName=orderDailySales.04ImportStep status=COMPLETED readCount=100000 writeCount=100000 skipCount=0 commitCount=1000 elapsedMs=6531
OrderDailySalesReplaceTasklet : 日次売上を入れ替えました: 対象日=2026-10-16 明細削除=0 集計削除=0 明細登録=100000 集計登録=50
Step終了: stepName=orderDailySales.05ReplaceStep status=COMPLETED writeCount=100000 commitCount=1 elapsedMs=870
ジョブ終了: jobName=orderDailySalesJob executionId=13 status=COMPLETED exitCode=COMPLETED readCount=100000 writeCount=200000 skipCount=0 elapsedMs=7615

-- batch.batch_job_execution / batch_step_execution（execution 13: 初回取込）
job|13|COMPLETED|03:09:07.930|03:09:15.545|7.62
orderDailySales.01FileCheckStep|COMPLETED|03:09:07.963|03:09:08.012|0.05
orderDailySales.02HeaderCheckStep|COMPLETED|03:09:08.031|03:09:08.043|0.01
orderDailySales.03ClearWorkStep|COMPLETED|03:09:08.058|03:09:08.099|0.04
orderDailySales.04ImportStep|COMPLETED|03:09:08.117|03:09:14.648|6.53
orderDailySales.05ReplaceStep|COMPLETED|03:09:14.664|03:09:15.535|0.87
-- expected.sh と actual.sql の diff: 差分なし（50店舗。先頭 S001|3306|9948|54657|139234000）

$ time run_job.sh no6b.log scheduledTime=2026-10-16T20:00:00Z    -> exit=0（real 0m12.400s）
日次売上を入れ替えました: 対象日=2026-10-16 明細削除=100000 集計削除=50 明細登録=100000 集計登録=50
-- execution 14: job 8.09 秒 / 01=0.05 02=0.01 03=0.03 04=6.48 05=1.40（秒）。集計は期待値と diff なし
```

---

## No.7: 別の実行回をまたぐリスタート

- **目的**: リスタートまでの間に別の対象日の実行 B が完了してワークを入れ替えていても、失敗した実行 A のリスタートが A 自身の入力ファイルからワークを作り直し、A・B の対象日の明細・集計がそれぞれの入力どおりになることを確認する（Issue #74 の再現。詳細設計書 §5、§7「システムテスト」）。A を Step 4 で失敗させる場合 (a) と、Step 5 で失敗させる場合 (b) の2つを1シナリオに含める
- **事前条件**: No.3 実施後の状態（`app.order_detail`=500件）。入力は `make_scenario_files.sh` が作る (a) A=`orders_20261017.csv`（500明細）・B=`orders_20261018.csv`（200明細）、(b) A=`orders_20261019.csv`（200明細）・B=`orders_20261020.csv`（150明細）。受注番号は `gen_orders.sh` が日付を含めるため A と B で重ならない（`app.order_detail` の主キーが受注日を含まないため）。(a) の Step 4 の失敗は `fail_trigger_on.sql`（ワークへの INSERT が `line_seq >= 350` で失敗）、(b) の Step 5 の失敗は `fail_step5_on.sql`（`app.order_detail` への INSERT で例外）で SQL（トリガ）だけで注入する
- **手順**:

| # | 操作 | 期待結果 | 実施結果 |
|---|---|---|---|
| 1 | (a) `fail_trigger_on.sql` を適用し、実行 A `run_job.sh no7a_A1.log scheduledTime=2026-10-17T17:00:00Z`（targetDate=2026-10-17） | 終了コード1。Step 4 の4チャンク目で失敗し、10/17 の本テーブルは0件 | OK（exit=1。executionId=5 FAILED、work=300件、10/17 の order_detail=0件） |
| 2 | (a) `fail_trigger_off.sql` で障害を除去し、実行 B `run_job.sh no7a_B.log scheduledTime=2026-10-18T17:00:00Z`（targetDate=2026-10-18） | 終了コード0。ワークは B の200行（受注日=10/18）に入れ替わっている | OK（exit=0。executionId=6 COMPLETED。取込後のワーク=200件・受注日は 10/18 のみ。10/18 の明細=200・集計=4） |
| 3 | (a) A を同一パラメータ（`scheduledTime=2026-10-17T17:00:00Z`）でリスタート | 終了コード0。A の入力500行をすべて取り込み、ワークに B の行が混ざらない | OK（exit=0。executionId=7 COMPLETED、Step 4 read=500 write=500 commit=5、明細登録=500・集計登録=5） |
| 4 | (a) 10/17 の明細・集計を A の入力（`expected.sh`）と、10/18 を B の入力および手順2直後のチェックサムと比較 | 10/17 は A の入力どおり。10/18 は B の入力どおりで、リスタート前後でチェックサムが変わらない | OK（10/17: 500明細・166受注・5店舗の集計が期待値と diff なし。10/18: 200明細・4店舗が期待値と diff なし、チェックサム同一） |
| 5 | (b) `fail_step5_on.sql` を適用し、実行 A `run_job.sh no7b_A1.log scheduledTime=2026-10-19T17:00:00Z`（targetDate=2026-10-19） | 終了コード1。Step 1〜4 は成功し Step 5 で失敗、10/19 の本テーブルは0件 | OK（exit=1。executionId=8 FAILED。Step 4 は read=200 write=200 で COMPLETED、Step 5 が FAILED（`forced failure on app.order_detail insert`）、ワーク=200件・10/19 の order_detail=0件） |
| 6 | (b) `fail_step5_off.sql` で障害を除去し、実行 B `run_job.sh no7b_B.log scheduledTime=2026-10-20T17:00:00Z`（targetDate=2026-10-20） | 終了コード0。ワークは B の150行（受注日=10/20）に入れ替わっている | OK（exit=0。executionId=9 COMPLETED。取込後のワーク=150件・受注日は 10/20 のみ。10/20 の明細=150・集計=3） |
| 7 | (b) A を同一パラメータ（`scheduledTime=2026-10-19T17:00:00Z`）でリスタートし、10/19 の明細・集計を A の入力と、10/20 を B の入力および手順6直後のチェックサムと比較 | 終了コード0。10/19 の明細は A の入力どおり（0件でない）。10/20 は B のまま変わらない | OK（exit=0。executionId=10 COMPLETED、明細登録=200・集計登録=5。10/19: 200明細・68受注が期待値と diff なし。10/20: 150明細が期待値と diff なし、チェックサム同一） |

- **確認ポイント**: リスタートがワークを自分の入力ファイルから作り直すこと（A の取込件数が B の行数ではなく A の入力の行数であること）、Issue #74 で起きていた「A の対象日の明細が0件になる（B の行が受注日で絞られて捨てられる）」「A の対象日に B の行が混ざる」が起きないこと、B の対象日のデータが A のリスタートで変わらないこと、障害注入は SQL（トリガ）だけで本番コードに手を入れていないこと、実施後にトリガが残っていないこと
- **実施結果**: OK
- **証跡**:
```
== (a) ==
$ psql -f fail_trigger_on.sql                                  -> CREATE FUNCTION / CREATE TRIGGER
$ run_job.sh no7a_A1.log scheduledTime=2026-10-17T17:00:00Z     -> exit=1
ジョブ終了: jobName=orderDailySalesJob executionId=5 status=FAILED exitCode=FAILED readCount=400 writeCount=300 skipCount=0
Caused by: ... ERROR: system-test: forced failure at line_seq=350
work|300|301
detail(10/17)|0

$ psql -f fail_trigger_off.sql                                 -> DROP TRIGGER / DROP FUNCTION
$ run_job.sh no7a_B.log scheduledTime=2026-10-18T17:00:00Z      -> exit=0
日次売上を入れ替えました: 対象日=2026-10-18 明細削除=0 集計削除=0 明細登録=200 集計登録=4
ジョブ終了: jobName=orderDailySalesJob executionId=6 status=COMPLETED exitCode=COMPLETED readCount=200 writeCount=400
work(B 取込後)|200|2026-10-18|2026-10-18
-- checksum.sql（10/18、A リスタート前）
order_detail|200|0b97388f9ca03d7a29cbe9cfdc4e371a
daily_store_sales|4|cc7a1fbaa06d00533f76b7e5a5d1df7b

$ run_job.sh no7a_A2.log scheduledTime=2026-10-17T17:00:00Z     -> exit=0（A のリスタート）
OrderDetailWorkClearTasklet : 受注明細ワークを初期化しました: errorFile=.../error/orders_20261017_20261018020000_error.txt 削除=false
日次売上を入れ替えました: 対象日=2026-10-17 明細削除=0 集計削除=0 明細登録=500 集計登録=5
ジョブ終了: jobName=orderDailySalesJob executionId=7 status=COMPLETED exitCode=COMPLETED readCount=500 writeCount=1000
-- batch.batch_step_execution（job_execution_id|step|status|read|write|commit。5 が A の失敗、6 が B、7 が A のリスタート）
5|orderDailySales.01FileCheckStep〜03ClearWorkStep|COMPLETED
5|orderDailySales.04ImportStep|FAILED|400|300|3
6|orderDailySales.04ImportStep|COMPLETED|200|200|2
6|orderDailySales.05ReplaceStep|COMPLETED|0|200|1
7|orderDailySales.01FileCheckStep〜03ClearWorkStep|COMPLETED
7|orderDailySales.04ImportStep|COMPLETED|500|500|5
7|orderDailySales.05ReplaceStep|COMPLETED|0|500|1
-- A（10/17）: expected.sh と actual.sql の diff なし（A_EXPECTED_SAME）。入力は500明細・166受注、DB は detail(10/17)|500|166
S001|90|264|1365|3315800
S002|40|121|655|1811400
S003|23|75|413|1008100
S004|9|31|148|405600
S005|4|9|55|93800
-- B（10/18）: diff なし（B_EXPECTED_SAME）。checksum.sql はリスタート後も上と同一（B_UNCHANGED）。detail(10/18)|200（入力200明細）

== (b) ==
$ psql -f fail_step5_on.sql                                    -> CREATE FUNCTION / CREATE TRIGGER
$ run_job.sh no7b_A1.log scheduledTime=2026-10-19T17:00:00Z     -> exit=1
Step終了: 01〜04 status=COMPLETED（04ImportStep readCount=200）、05ReplaceStep status=FAILED
ジョブ終了: jobName=orderDailySalesJob executionId=8 status=FAILED exitCode=FAILED
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: system-test: forced failure on app.order_detail insert (order_no=O202610190000001)
work|200|2026-10-19|2026-10-19
detail(10/19)|0

$ psql -f fail_step5_off.sql                                   -> DROP TRIGGER / DROP FUNCTION
$ run_job.sh no7b_B.log scheduledTime=2026-10-20T17:00:00Z      -> exit=0
日次売上を入れ替えました: 対象日=2026-10-20 明細削除=0 集計削除=0 明細登録=150 集計登録=3
ジョブ終了: jobName=orderDailySalesJob executionId=9 status=COMPLETED exitCode=COMPLETED readCount=150 writeCount=300
work(B 取込後)|150|2026-10-20|2026-10-20
-- checksum.sql（10/20、A リスタート前）
order_detail|150|9529abad5e09ecc750a3eb1edb077208
daily_store_sales|3|e9be01ab2f14235f22bf4c619c8fc0a5

$ run_job.sh no7b_A2.log scheduledTime=2026-10-19T17:00:00Z     -> exit=0（A のリスタート）
OrderDetailWorkClearTasklet : 受注明細ワークを初期化しました: errorFile=.../error/orders_20261019_20261020020000_error.txt 削除=false
日次売上を入れ替えました: 対象日=2026-10-19 明細削除=0 集計削除=0 明細登録=200 集計登録=5
ジョブ終了: jobName=orderDailySalesJob executionId=10 status=COMPLETED exitCode=COMPLETED readCount=200 writeCount=400
-- batch.batch_step_execution（8 が A の失敗、9 が B、10 が A のリスタート）
8|orderDailySales.04ImportStep|COMPLETED|200|200|2
8|orderDailySales.05ReplaceStep|FAILED|0|0|0
9|orderDailySales.05ReplaceStep|COMPLETED|0|150|1
10|orderDailySales.04ImportStep|COMPLETED|200|200|2
10|orderDailySales.05ReplaceStep|COMPLETED|0|200|1
-- A（10/19）: diff なし（A_EXPECTED_SAME）。入力は200明細・68受注、DB は detail(10/19)|200|68
S001|36|99|486|1118500
S002|16|46|233|621000
S003|11|44|239|658500
S004|3|8|47|114300
S005|2|3|6|8300
-- B（10/20）: diff なし（B_EXPECTED_SAME）。checksum.sql はリスタート後も上と同一（B_UNCHANGED）。detail(10/20)|150（入力150明細）
-- select count(*) from pg_trigger where tgname like 'st\_fail%'  -> 0（トリガ残存なし）
```
