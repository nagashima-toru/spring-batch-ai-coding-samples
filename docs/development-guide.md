# 開発ガイド

本書は、この雛形を使って案件開発を進めるための手順書である。
開発は Claude Code と工程別 Skills（`.claude/skills/`）を用いた **SDD（仕様駆動開発）** で進める。

## 1. 環境構築

### 1.1 前提ツール

| ツール | バージョン | 備考 |
|---|---|---|
| JDK | 25（Temurin） | `java -version` で確認 |
| Docker | 最新 | Docker Desktop 等。結合テスト・ローカルDB で必須 |
| Git / GitHub CLI | 最新 | `gh auth login` 済みであること |
| Claude Code | 最新 | 標準開発環境 |

Maven はリポジトリ同梱の Maven Wrapper（`./mvnw`、3.10.0 固定）を使用するためインストール不要。

### 1.2 初回セットアップ

```bash
git clone https://github.com/nagashima-toru/spring-batch-ai-coding-samples.git
cd spring-batch-ai-coding-samples

# ローカルDB（PostgreSQL 16）起動
docker compose -f docker/compose.yaml up -d

# ビルド＋全テスト（初回は依存ダウンロードで数分かかる）
./mvnw clean verify
```

### 1.3 よく使うコマンド

```bash
./mvnw clean verify           # ビルド＋単体＋結合（Docker 必須）＋静的解析。batch-sample のカバレッジ80%はここで判定
./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false   # 結合テストを除外（Docker 不要）。カバレッジ不足では止めない
./mvnw clean verify -DskipITs -pl batch-core                 # batch-core のカバレッジ80%を判定
./mvnw test -pl batch-core    # モジュール指定で単体テストのみ
./mvnw checkstyle:check       # 規約チェックのみ

# サンプルジョブのローカル実行（プロファイル local）
./mvnw spring-boot:run -pl batch-sample \
  -Dspring-boot.run.arguments="--spring.batch.job.name=sample01CsvImportJob inputFile=batch-sample/data/in/customers.csv errorFile=batch-sample/data/error/customers_error.txt"
```

### 1.4 クラウド環境での準備

Claude Code on the web などのクラウド環境では、JDK 25 と Docker デーモンが既定で用意されないことがある。環境の setup script かセッションの最初に次を実行する（x64 の Linux を前提にしている）。

```bash
#!/usr/bin/env bash
# JDK 25（Temurin）と Docker デーモンを用意する
set -euo pipefail
if [ ! -x /opt/jdk-25/bin/java ]; then
  mkdir -p /opt/jdk-25
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    | tar -xz -C /opt/jdk-25 --strip-components=1
fi
# ./mvnw は ~/.mavenrc の JAVA_HOME を使う（PATH の java が 25 でなくてもよい）
printf 'JAVA_HOME=/opt/jdk-25\nexport JAVA_HOME\n' > ~/.mavenrc
if ! docker info >/dev/null 2>&1; then
  nohup dockerd >/tmp/dockerd.log 2>&1 &
  for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
fi
./mvnw -v
docker info --format '{{.ServerVersion}}'
```

## 2. 開発の進め方（工程と Skills）

各工程は Claude Code の Skill として定義されている。**前工程の成果物（Markdown）を入力とし、次工程の成果物を生成する**ことでドキュメントが連鎖し、設計書が常に SSoT となる。

### 2.1 工程フロー

```mermaid
flowchart TD
    REQ["/requirements<br>要件定義<br>成果物: docs/projects/{案件}/requirements.md"]
    BD["/basic-design<br>基本設計<br>成果物: docs/projects/{案件}/basic-design.md"]
    DD["/detail-design<br>詳細設計<br>成果物: docs/projects/{案件}/detail-design/{ジョブID}.md"]
    RD["/review-design<br>設計レビュー"]
    IMPL["/implementation<br>実装（基盤部品を利用）"]
    UT["/unit-test<br>単体テスト"]
    IT["/integration-test<br>結合テスト"]
    RC["/review-standards<br>コードレビュー"]
    ST["/system-test<br>システムテスト"]
    PR[PR 作成・マージ]

    REQ --> BD --> DD --> RD
    RD -->|指摘反映| DD
    RD -->|承認| IMPL
    IMPL --> UT --> IT --> RC
    RC -->|指摘反映| IMPL
    RC -->|承認| ST
    ST --> PR
```

`implementation` / `unit-test` / `integration-test` の各工程は、§5 の**検証ループ**（検証→失敗なら修正→再検証）を工程内に内蔵する。

**設計意図: システムテストを PR 作成の前に置く理由**

システムテストのシナリオ・テストデータ・実施記録（`docs/projects/{案件}/system-test/`）も設計成果物であり、
実装コードと同じ PR でレビューできる状態にする。運用観点（リラン・異常終了時の運用）の不備が見つかった場合、
マージ前であれば同じ PR 内で実装を直せる（マージ後だと修正が別 PR に分かれ、トレーサビリティが落ちる）。

### 2.2 各工程の入出力

| 工程（Skill） | 入力 | 出力 |
|---|---|---|
| `requirements` | ヒアリング内容・既存資料 | `docs/projects/{案件}/requirements.md` |
| `basic-design` | requirements.md | `docs/projects/{案件}/basic-design.md`（ジョブ一覧・方式・IF・テーブル設計） |
| `detail-design` | basic-design.md | `docs/projects/{案件}/detail-design/{ジョブID}.md`（Step 構成・処理フロー・エラー処理） |
| `review-design` | 設計書＋`docs/architecture.md` | レビュー指摘（設計書へ反映） |
| `implementation` | detail-design/{ジョブID}.md | ジョブ実装コード（基盤部品を利用） |
| `unit-test` | 実装コード＋詳細設計書 | JUnit 5 単体テスト |
| `integration-test` | 実装コード＋詳細設計書 | Testcontainers 結合テスト（`*IT.java`） |
| `review-standards` | 実装＋テスト＋`docs/coding-standards.md` | レビュー指摘（コードへ反映）。チェックリストの確認と組み込みの `/code-review` の両方を行う |
| `system-test` | basic-design.md・運用設計 | テストシナリオ・データ・実施結果 |

**設計意図: `basic-design.md` は案件単位で1ファイル、`detail-design/` はジョブ単位で複数ファイルにしている理由**

基本設計と詳細設計では扱う粒度が異なるため、成果物のファイル分割方針も意図的に変えている。

- **基本設計（`basic-design.md`、単一ファイル）**: ジョブ分割・IF設計・テーブル設計は案件全体を俯瞰して決める内容であり、
  ジョブ間の関係（先行/後続、共有テーブル）も含めて1つの文書で一望できる必要がある。§1「ジョブ一覧」に
  案件内の全ジョブを列挙し、§2「ジョブ方式設計」でジョブごとに節を繰り返す構成にしているのはこのため
  （`templates/basic-design-template.md` 参照。案件のジョブが1本でも複数本でもこの1ファイルにまとめる）。
- **詳細設計（`detail-design/{ジョブID}.md`、ジョブ単位で分割）**: Step構成・処理フロー・エラー処理設計は
  実装可能な粒度までジョブ固有に踏み込むため内容が肥大化しやすく、1ファイルに複数ジョブを詰め込むとレビュー・
  実装時の参照性が落ちる。基本設計のジョブ一覧に載っているジョブの数だけ、`{ジョブID}.md` を作成する
  （`detail-design` Skill は「基本設計書のジョブ**1本**を入力に」実行する設計になっている）。

### 2.3 実行例（新規ジョブを1本作る場合）

```text
# Claude Code 上で対話的に進める
> /requirements 売上データ日次取込バッチの要件を整理したい
> /basic-design docs/projects/sales/requirements.md から基本設計を作成して
> /detail-design SALES-001 のジョブ詳細設計を作成して
> /review-design docs/projects/sales/detail-design/SALES-001.md をレビューして
> /implementation SALES-001 を実装して
> /unit-test SALES-001 の単体テストを作成して
> /integration-test SALES-001 の結合テストを作成して
> /review-standards job_sales_import 配下をレビューして
> /system-test sales 案件のシステムテストのシナリオを設計して実施して
```

要件定義書が確定済みなら、上記の `/basic-design` 以降を `/sdd-pipeline` で一括実行できる
（`.claude/skills/sdd-pipeline/SKILL.md`）。

### 2.4 ハーネスで実装以降を進める場合

ハーネスで進めるのは、詳細設計がレビュー済み（`/review-design` を通った）ジョブの
implementation〜system-test である。要件定義〜設計レビュー（requirements・basic-design・
detail-design・review-design）は従来どおり人と Skills で対話して進める。
`/plan` には次のようにゴールを渡す。

```text
> /plan docs/projects/sales/detail-design/SALES-001.md（レビュー済み）の SALES-001 を implementation〜system-test のタスクに分けて実装・検証する
```

`/sdd-pipeline` は廃止せず併存させる（既存の利用者と手順を壊さないため）。使い分けは次のとおり。

| 観点 | /sdd-pipeline | ハーネス（/plan・/run） |
|---|---|---|
| 対象工程 | basic-design〜system-test と PR 作成 | implementation〜system-test（要件定義〜設計レビューは人と Skills で対話して進める） |
| 開始の前提 | 確定済みの requirements.md | レビュー済みの詳細設計書 |
| 判断が要る時 | 主エージェントへの報告とモデル昇格（§5.3） | creator が `[Ask]` などを blocked にし、人が `/plan unblock` で答える |
| 検証のしかた | 各工程内の検証ループ（§5） | creator と別の verifier が受け入れ基準（`./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false` と `./mvnw clean verify -DskipITs -pl batch-core`、結合テストを含むタスクは `./mvnw clean verify`）で判定する。`-DskipITs` の基準では batch-sample のカバレッジを判定しない |

ハーネスでは1計画で1ジョブを扱い、implementation・unit-test・integration-test・review-standards・
system-test の5工程のタスクに分ける。分け方と各工程のルールは `vault/rules/`
（`vault/rules/planner/job-task-split.md`・`vault/rules/creator/skill-workflow.md`・
`vault/rules/verifier/build-verification.md`）にある。

## 3. ブランチ運用・PR

**GitHub Flow** を採用する。

1. `main` から feature ブランチを切る（`feature/{issue番号}-{内容}`。対応する Issue が無い場合は
   `feature/{案件名}`）
2. 実装・テスト。コミットメッセージは日本語で「何を・なぜ」を書く
3. `./mvnw clean verify` がローカルで通ることを確認（システムテストまで完了した最終状態で通すこと。§2.1）
4. PR を作成（テンプレートに従い、**設計書へのリンク**と**レビュー Skill 実行結果**を記載）

PR テンプレートは PR の種類で使い分ける。設計 PR 用と実装 PR 用は同じ置き場（`.github/PULL_REQUEST_TEMPLATE/`）に
同じ命名規則で置き、既定のテンプレート（`.github/pull_request_template.md`）は設計・実装のチェックを持たない汎用のものにする
（設計と実装で観点が異なり、どちらかを既定にすると取り違えるため）。

| PR の種類 | テンプレート | 確認する観点 |
|---|---|---|
| 設計 PR（要件定義書・基本設計書・詳細設計書のみ） | `.github/PULL_REQUEST_TEMPLATE/design.md` | 要件の確定、`/review-design`、architecture.md への準拠、トレーサビリティ、未確定事項、batch-core 拡張の要否 |
| 実装 PR（コード・テスト・システムテスト記録） | `.github/PULL_REQUEST_TEMPLATE/implementation.md` | 設計書どおりの実装、`./mvnw clean verify`、`/review-standards`、基盤部品の利用、テスト観点の網羅、リスタート、Flyway、システムテスト |
| 上記以外（開発プロセス・ドキュメント・設定・依存更新など） | `.github/pull_request_template.md`（既定） | 変更に応じた確認、参照箇所の追従 |

- GitHub の画面で PR を作る時、設計 PR・実装 PR は PR 作成画面の URL に `?template=design.md` または
  `?template=implementation.md` を付ける（例: `.../compare/main...{ブランチ}?template=implementation.md`）。
  付けなければ既定の汎用テンプレートが入る
- 設計 PR・実装 PR のテンプレートは、レビュー Skill（設計 PR は `/review-design`、実装 PR は `/review-standards`）を
  **[Must]・[Should] の指摘が 0 件になるまで繰り返し実行した**ことを確認し、回ごとの指摘件数をレビュー実施記録に書く
- `gh api`・GitHub MCP・ハーネス（`scripts/vcs_finish.sh`）で PR を作る時はテンプレートが自動では入らない。
  本文を渡す側が該当テンプレートの見出しに沿って書く
- 設計と実装を別 PR にする場合（ハーネスで実装以降を進める場合など。§2.4）は、設計 PR をマージしてから
  main で `/plan` を実行する
- 設計と実装を1つの PR にする場合（`/sdd-pipeline`）は、実装 PR 用テンプレートを基本とし、設計 PR 用テンプレートの
  セルフチェックとレビュー実施記録（`/review-design` の回ごとの件数）も本文に含める

```mermaid
flowchart LR
    A[Issue 起票] --> B[feature ブランチ作成]
    B --> C[Skills で設計→実装→テスト→システムテスト]
    C --> D[./mvnw clean verify]
    D --> E[PR 作成<br>設計書リンク・レビュー実施記録]
    E --> F[CI 成功＋レビュー承認]
    F --> G[main へマージ]
```

## 4. 新しいジョブを追加するときのチェックリスト

- [ ] 詳細設計書（`docs/projects/{案件}/detail-design/`）が存在し、レビュー済みである
- [ ] ジョブパッケージ名・Bean 名が命名規約（`docs/architecture.md` §11）に従っている
- [ ] Reader/Writer/Tasklet は batch-core の基盤部品を利用している（独自実装していない）
- [ ] Skip/Retry・エラーレコード退避が方式設計 §5 に従っている
- [ ] `JobParametersValidator` でパラメータ検証している
- [ ] Flyway マイグレーションを追加した（既存ファイルの変更は禁止）
- [ ] 単体テスト＋結合テストがあり、`./mvnw clean verify` が通る
- [ ] リスタート動作（途中失敗→同一パラメータ再実行）を結合テストまたは手動で確認した

## 5. 検証ループ（自律検証の共通ルール）

`implementation` / `unit-test` / `integration-test` の各 Skill は、成果物を生成したら
**検証コマンドを実行し、失敗したら修正して再検証するループ**を工程内で回す。
本節がループ運用の共通ルール（SSoT）であり、各 Skill は本節を参照する。

### 5.1 ループの基本形

```text
検証コマンド実行 → 失敗（exit code 非0）
  → レポート/ログを読んで原因を特定 → 修正 → 再実行
```

失敗の詳細は以下を読む（標準出力のスタックトレースだけで判断しない）。

| 検証 | レポートの場所 |
|---|---|
| 単体テスト | `{module}/target/surefire-reports/` |
| 結合テスト | `{module}/target/failsafe-reports/` |
| カバレッジ | `{module}/target/site/jacoco/` |
| Checkstyle / SpotBugs | `{module}/target/site` 配下のレポート |

### 5.2 上限と終了条件

- 成功（exit code 0）で終了する。
- 1工程あたり**最大3ラウンド**（カバレッジ充足ループのみ最大2ラウンド）。

### 5.3 早期エスカレーション

同一のエラー（同じテストの失敗・同じ違反）が**2ラウンド連続で解消しない**場合は、
上限に達していなくてもループを停止し、以下を添えてユーザーに報告する。

1. エラーログの要点（該当テスト名・違反内容・スタックトレースの核心）
2. 試した修正の内容
3. 原因の仮説

**モデル昇格（サブエージェント実行時の例外）**: 下位モデル（Sonnet 等）のサブエージェントが工程を
実行していて上記の停止条件に達した場合は、ユーザーへ報告する代わりに、同じ入力で**上位モデル（Opus）の
サブエージェントへ昇格して再試行**してよい（推論不足が原因のケースを救うため）。ただし
**昇格は1回まで・昇格後は最大2ラウンド**とし、それでも解消しなければ上記1〜3を添えてユーザーに報告する。
主エージェント自身が実行している場合、および昇格後も解消しない場合は、昇格せずに報告する。

**ハーネスで実行する場合**: ハーネス（`/plan`・`/run`）ではモデル昇格（Sonnet→Opus）を使わない。verifier が FAIL にしたタスクは attempt を上げて再試行し、上限（`HARNESS_MAX_ATTEMPTS`、既定3）を超えたら blocked にして人に引き継ぐ。

### 5.4 反則の禁止（ガードレール）

**ループを通すために検証自体を弱めてはならない。** 以下は指摘・依頼がない限り禁止。

- カバレッジ閾値（親 pom の `jacoco.coverage.minimum`）の変更
- Checkstyle / SpotBugs の除外設定追加による回避（正当な抑止はユーザー了承後に理由コメント付きで）
- テストの削除・`@Disabled` 化・アサーション弱体化による回避
- 適用済み Flyway マイグレーションファイルの書き換えによる回避

### 5.5 SSoT との関係

修正の過程で**設計書と異なる実装が必要**と判明したら、ループを中断して先に設計書の
改訂を提案する（設計書と異なる実装の禁止。CLAUDE.md・§2 の原則）。

## 6. トラブルシューティング

| 事象 | 対処 |
|---|---|
| 結合テストが `Could not find a valid Docker environment` で失敗 | Docker Desktop を起動する。急ぎのビルドは `-DskipITs` |
| 「A job instance already exists and is complete」 | 成功済みジョブと同一パラメータで再実行している（`docs/architecture.md` §6.1 の正常挙動）。業務パラメータを変えるか、テストなら DB を初期化する |
| ローカルDB の状態を初期化したい | `docker compose -f docker/compose.yaml down -v && docker compose -f docker/compose.yaml up -d` |
| Checkstyle/SpotBugs 違反でビルド失敗 | `target/site` のレポートを確認し修正する。抑止が妥当な場合のみ理由コメント付きで除外設定へ追加 |
| クラウド環境で `./mvnw` が JDK 25 以外で動く、または `docker info` が失敗する | §1.4 の準備スクリプトを実行する |
