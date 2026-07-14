# Spring Batch AI開発雛形プロジェクト

> ⚠️ このファイルは Phase 0 時点の最小版。Phase 5 で完成させる。

## プロジェクト概要

AI駆動開発（Claude Code）を前提とした Spring Batch のリファレンス実装。
複数チーム・複数案件で再利用される雛形であり、SDD（仕様駆動開発）・SSoT（設計ドキュメント＝`docs/` 配下のMarkdownが唯一の正）で開発する。

## モジュール構成

- `batch-core` — 基盤部品（共通ライブラリ）。全ジョブはここの部品を利用する
- `batch-sample` — 基盤部品の使い方を示すサンプルジョブ群
- `docs/` — 方式設計・開発ガイド（SSoT。コードは設計から生成する）
- `.claude/skills/` — 工程別 Skills（要件定義〜レビュー）

## 技術スタック（固定バージョン）

- Java 25 (Temurin) / Spring Boot 4.1.0 / Spring Batch 6.0.4 / Spring Framework 7.x
- Maven 3.9.16（`./mvnw` を使用）/ MyBatis Starter 4.0.1 / Flyway（Boot BOM管理）/ PostgreSQL 16

## ビルド・テスト

```bash
./mvnw verify              # ビルド＋単体＋結合テスト＋静的解析＋カバレッジ
./mvnw verify -DskipITs    # 結合テスト（Docker必須）を除外
```

## 禁止事項

- **Spring Batch 5系のサンプルコード流用禁止**（6系でAPI変更あり。公式6系リファレンス準拠で実装）
- 基盤部品（batch-core）で実現できる処理の独自実装禁止
- 機密情報のコード直書き禁止（Secrets Manager / SSM 経由で環境変数注入）
- 依存バージョンの各モジュール個別指定禁止（親POMで一元管理）

## 規約

- ドキュメント・Javadoc・コミットメッセージは日本語
- コーディング規約: `docs/coding-standards.md`（Checkstyle: `config/checkstyle/checkstyle.xml`）
- ブランチ戦略: GitHub Flow（main + feature、PR必須）
