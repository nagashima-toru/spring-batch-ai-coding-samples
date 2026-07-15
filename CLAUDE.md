# Spring Batch AI開発雛形プロジェクト

AI駆動開発（Claude Code）を前提とした Spring Batch のリファレンス実装。
複数チーム・複数案件で再利用される雛形であり、**SDD（仕様駆動開発）・SSoT
（`docs/` 配下の設計ドキュメントが唯一の正。コードは設計から生成する）** で開発する。

## モジュール・ディレクトリ構成

| パス | 役割 |
|---|---|
| `batch-core/` | 基盤部品（共通ライブラリ）。**全ジョブはここの部品を利用する** |
| `batch-sample/` | サンプルジョブ9本（コア6本フル実装＋拡張3本骨格）。解説は `batch-sample/docs/` |
| `docs/architecture.md` | **方式設計書（SSoT）**。全設計・実装はこれに準拠する |
| `docs/development-guide.md` | 開発手順（Skills を使った工程フロー・PR作成まで） |
| `docs/coding-standards.md` | コーディング規約（Checkstyle: `config/checkstyle/`） |
| `docs/projects/{案件}/` | 案件別の要件定義書・設計書の格納先（Skills が生成） |
| `.claude/skills/` | 工程別 Skills（下表） |
| `docker/compose.yaml` | ローカル用 PostgreSQL 16 |

## 技術スタック（固定バージョン）

- Java 25 (Temurin) / Spring Boot 4.1.0 / Spring Batch 6.0.4 / Spring Framework 7.x
- Maven 3.9.16（`./mvnw` を使用）/ MyBatis Starter 4.0.1 / Flyway（Boot BOM）/ PostgreSQL 16
- Testcontainers 2系（Boot BOM 管理。アーティファクト名は `testcontainers-postgresql`）
- 実行基盤: ECS Fargate + EventBridge Scheduler（イメージビルドは Jib）

## ビルド・実行コマンド

```bash
./mvnw verify              # ビルド＋単体＋結合(要Docker)＋静的解析＋カバレッジ(80%)
./mvnw verify -DskipITs    # 結合テストを除外
docker compose -f docker/compose.yaml up -d   # ローカルDB起動

# ジョブのローカル実行例（詳細は batch-sample/docs/）
java -jar batch-sample/target/batch-sample-*.jar \
  --spring.batch.job.name=sample01CsvImportJob \
  inputFile=batch-sample/data/in/customers.csv errorFile=batch-sample/data/error/e.txt
```

## Skills（開発工程）

要件定義から順に、前工程の成果物が次工程の入力になる（詳細は docs/development-guide.md）。

| Skill | 工程 | 出力 |
|---|---|---|
| `/requirements` | 要件定義 | `docs/projects/{案件}/requirements.md` |
| `/basic-design` | 基本設計 | `docs/projects/{案件}/basic-design.md` |
| `/detail-design` | 詳細設計 | `docs/projects/{案件}/detail-design/{ジョブID}.md` |
| `/review-design` | 設計レビュー | 指摘一覧（チェックリスト準拠） |
| `/implementation` | 実装 | ジョブコード＋Flyway（基盤部品を必ず利用） |
| `/unit-test` | 単体テスト | `*Test.java`（観点表準拠） |
| `/integration-test` | 結合テスト | `*IT.java`（Testcontainers） |
| `/review-code` | コードレビュー | 指摘一覧（チェックリスト準拠） |
| `/system-test` | システムテスト | STシナリオ・実施記録 |

## 禁止事項

- **Spring Batch 5系のサンプルコード流用禁止**（6系でAPI・パッケージが変更。
  部品は `org.springframework.batch.infrastructure.item.*`。公式6系リファレンス準拠）
- **基盤部品（batch-core）で実現できる処理の独自実装禁止**（implementation Skill の対応表参照）
- 機密情報のコード直書き禁止（Secrets Manager / SSM から環境変数注入）
- 依存バージョンの各モジュール個別指定禁止（親POMで一元管理）
- Flyway 適用済みマイグレーションファイルの変更禁止（新規Vファイル追加）
- 設計書と異なる実装（先に設計書を改訂してから実装する）

## ハマりどころ（Boot 4 / Batch 6 固有）

1. **Boot 4 の既定 JobRepository はインメモリ（Resourceless）**。
   メタデータ永続化・リスタートには `spring-boot-starter-batch-jdbc` が必須
2. Flyway の自動構成は `spring-boot-starter-flyway` が必要（flyway-core 単体では動かない）
3. 新チャンクモデルのリスナー登録で `(Object)` キャスト禁止
   （アノテーション走査のみになりインターフェース実装が無視される）
4. Testcontainers 2系はアーティファクト名・パッケージが1系と異なる
   （`org.testcontainers.postgresql.PostgreSQLContainer`、junit-jupiter モジュール廃止）
5. バッチ用スターターに Jackson は含まれない（batch-core が `tools.jackson` を提供済み）

## 規約サマリ

- ドキュメント・Javadoc・コミットメッセージは日本語。テストメソッド名は日本語（条件＿期待結果）
- 終了コード: 0=正常 / 2=警告(Skip発生) / 1=異常（変更禁止。architecture.md §7）
- スキーマ: `batch`=ジョブメタデータ / `app`=業務（SQL はスキーマ修飾必須）
- ブランチ: GitHub Flow（main + feature、PR必須。テンプレートのチェック欄を埋める）
