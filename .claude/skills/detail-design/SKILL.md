---
name: detail-design
description: 基本設計書を入力にジョブ単位の詳細設計書（Step構成・処理フロー・エラー処理設計）を作成する。「SALES-001 の詳細設計をして」「詳細設計書を書いて」「Step構成を設計して」のような発話や、基本設計の承認後の次工程として発動する。
---

# 詳細設計 Skill

基本設計書のジョブ1本を入力に、実装可能な粒度の詳細設計書を作成する。

## 入力と出力（工程連鎖）

| | 内容 |
|---|---|
| 入力 | `docs/projects/{案件名}/basic-design.md`（対象ジョブIDの節） |
| 参照 | `docs/architecture.md`、batch-core の基盤部品 Javadoc、類似サンプル（`batch-sample/`） |
| 出力 | `docs/projects/{案件名}/detail-design/{ジョブID}.md`（テンプレート: `templates/detail-design-template.md`） |
| 次工程 | `/review-design` でレビュー後、`/implementation` がこの成果物を入力とする |

## 進め方

1. 基本設計書の対象ジョブの節を読み、方式・IF・テーブル定義を把握する
2. 類似のサンプルジョブ（`batch-sample/src/main/java/.../jobNN_*`）を特定し、構成の参考にする
3. **Step 設計**: Step ごとに Reader/Processor/Writer（または Tasklet）と使用する基盤部品を決める
   - 使用する基盤部品はクラス名まで特定する（例: `CsvItemReaderFactory`、`BusinessSkipPolicy`）
   - 基盤部品で実現できない処理が必要な場合は、その旨を設計書に明記し batch-core 拡張を提案する
4. **処理フロー**: Step 内の処理を入力→チェック→変換→出力の順に日本語で記述する
5. **エラー処理設計**: エラー事象ごとに 分類（業務/システム）・動作（Skip/失敗）・出力先を表にする
6. **SQL設計**: Mapper XML に書くSQLの方針（UPSERT・集計SQL・カーソル抽出）を記述する

## 設計粒度の基準

- 実装者（または implementation Skill）が**設計書だけを見て実装できる**こと
- クラス名・Bean名・Step名は命名規約（architecture.md §11）どおりに確定させる
- バリデーション項目は「項目 × チェック内容 × エラー時の扱い」の表で網羅する
- ただしコード断片は書かない（コードは実装工程の成果物。二重管理を避ける）

## 禁止事項

- 基本設計と矛盾する設計（矛盾に気付いた場合は基本設計書の改訂を先に提案する）
- 基盤部品にある機能の独自実装を前提とした設計
- リスタート安全性の未考慮（Chunk Step の Reader は状態保存対応か、Processor は副作用なしか）
