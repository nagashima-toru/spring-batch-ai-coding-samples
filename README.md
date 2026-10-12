# Spring Batch AI開発雛形プロジェクト

AI駆動開発（Claude Code）を前提とした Spring Batch のリファレンス実装・プロジェクト雛形です。
SDD（仕様駆動開発）・SSoT（設計ドキュメントを唯一の正とする）の開発スタイルと、
実務パターンを網羅したサンプル・基盤部品・工程別 Skills を提供します。

## 技術スタック

Java 25 / Spring Boot 4.1 / Spring Batch 6.0 / Maven / MyBatis / Flyway / PostgreSQL 16 /
Testcontainers / ECS Fargate + EventBridge（AWS）/ GitHub Actions（OIDC）

## クイックスタート

```bash
# 前提: JDK 25 (Temurin), Docker
git clone https://github.com/nagashima-toru/spring-batch-ai-coding-samples.git
cd spring-batch-ai-coding-samples

docker compose -f docker/compose.yaml up -d   # ローカルDB（PostgreSQL 16）
./mvnw clean verify                            # ビルド＋全テスト＋静的解析＋カバレッジ

# サンプルジョブ実行（CSV取込）
java -jar batch-sample/target/batch-sample-*.jar \
  --spring.batch.job.name=sample01CsvImportJob \
  inputFile=batch-sample/data/in/customers.csv \
  errorFile=batch-sample/data/error/customers_error.txt
echo $?   # 0=正常 / 2=警告(Skipあり) / 1=異常
```

**まず試すべきデモ**: [サンプル06のリスタート手順](batch-sample/docs/sample06-flow-job.md)
（途中失敗 → データ修正 → 同一パラメータ再実行で「続きから再開」を体験できます）

## ドキュメント

| ドキュメント | 内容 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 方式設計書（SSoT）: 方式選定基準・エラー処理・リラン設計・命名規約 |
| [docs/development-guide.md](docs/development-guide.md) | 開発手順: 環境構築〜Skills を使った工程フロー〜PR |
| [docs/coding-standards.md](docs/coding-standards.md) | コーディング規約 |
| [batch-sample/docs/](batch-sample/docs/README.md) | サンプル9本の解説（パターン別の選定基準つき） |
| [CLAUDE.md](CLAUDE.md) | Claude Code 向けプロジェクトガイド（規約・禁止事項・ハマりどころ） |

## 構成

```
├── batch-core/        # 基盤部品（HTTP基盤・共通Tasklet・Reader/Writer・例外・リスナー）
├── batch-sample/      # サンプルジョブ9本＋結合テスト（Testcontainers）
├── docs/              # 方式設計・開発ガイド（SSoT）
├── .claude/skills/    # 工程別Skills（要件定義→設計→実装→テスト→レビュー）
├── .github/workflows/ # CI（PR時の品質ゲート）/ CD（タグpush→ECR→ECSタスク定義更新）
└── docker/            # ローカル用PostgreSQL
```

## 案件で使うとき

1. このリポジトリをテンプレートとして新規リポジトリを作成
2. `docs/architecture.md` の前提（実行基盤・DB・命名）を案件に合わせて改訂
3. `batch-sample` を参考に業務モジュールを作成（サンプルは削除してよい。基盤部品と docs は残す）
4. Claude Code で `/requirements` から工程を開始（[開発ガイド](docs/development-guide.md)参照）

## CD（デプロイ）の前提設定

タグ `v*` の push で ECR push＋ECS タスク定義更新が動きます。AWS 側（OIDC・IAM・ECR・
ECS・EventBridge）と GitHub Environments の設定手順は **[docs/aws-setup.md](docs/aws-setup.md)**
を参照してください。

## ライセンス

[MIT License](LICENSE) の下で公開しています。Copyright (c) 2026 Toru Nagashima

## コントリビュート

- 不具合報告・質問は Issue で受け付けます
- Pull Request は事前に Issue で相談したうえで受け付けます
- 対応や回答は保証しません（個人で保守している雛形のためです）

## 注意事項

- `docs/aws-setup.md` の AWS アカウント ID（`123456789012`）・ARN は例示値です。実際の値に置き換えて使ってください
- 本リポジトリは学習・雛形用のサンプルで、無保証です（LICENSE のとおり）
- ローカル用の DB パスワード（`docker/compose.yaml`・`application-local.yml`）はローカル専用の固定値です。本番系は Secrets Manager / SSM から環境変数で注入してください
- `vault/` は AI 協働ハーネスの作業領域（計画票・タスク票など）で、雛形の利用には不要です
