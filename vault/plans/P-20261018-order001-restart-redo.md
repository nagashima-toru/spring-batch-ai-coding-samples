---
id: P-20261018-order001-restart-redo
status: approved
---
# ゴール
docs/projects/order-daily-sales/detail-design/ORDER-001.md（レビュー済み。1.4 版。main にマージ済み・PR #85）の 1.4 版の改訂（GitHub Issue #74「ORDER-001: リスタート時に作業テーブルへ別日付の行が混ざり、主キー違反・重複の誤判定になる」）を implementation〜system-test のタスクに分けて実装・検証する。

## 分割方針
- 前提：詳細設計書 ORDER-001.md はヘッダの「ステータス」が「レビュー済み」で、1.4 版の変更履歴に設計レビューの指摘の反映が記録されている（`vault/rules/planner/job-task-split.md` の前提を満たす）
- `vault/rules/planner/job-task-split.md` に従い、ジョブ1本（ORDER-001）を implementation → unit-test → integration-test → review-standards → system-test の5タスクに分けて `after` で直列につなぐ
- T-01 は本体の削除・シグネチャ変更（`OrderKey`・`selectKeys`・`beforeStep` の削除、コンストラクタの変更）でテストのコンパイルが通らなくなるため、既存の単体テスト2本のコンパイルと既存観点の維持に要る最小の修正だけを T-01 に含める。新しい観点の単体テストは T-02 で足す
- 既存の結合テストのリスタートの観点（続きから取り込む）は T-01 の後に失敗するが、T-01・T-02 の基準は `-DskipITs` のため通る。T-03 で 1.4 版の期待に書き直す
- コードを変更するタスク（T-01〜T-04 の成果物に `.java`・`.xml`、T-05 に `vault/` 外のディレクトリ）があるため、`vault/rules/planner/code-review-criterion.md` に従い、最後に計画単位のレビュータスク T-06 を足す（計6タスク）
- スキーマ・Flyway・batch-core は変更しない（詳細設計書 1.4 版の改訂範囲外）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | ORDER-001 1.4 版のリスタート方式（Step 1〜4 のやり直し・エラーファイル削除・既出キー復元の廃止）を実装する | |
| T-02 | done | 1 | T-01 | ORDER-001 1.4 版の単体テスト（OrderDetailWorkClearTasklet のエラーファイル削除・Processor の既出キー）を追加する | |
| T-03 | done | 1 | T-02 | ORDER-001 1.4 版の結合テスト（最初からのリスタート・Issue #74 の再現）を OrderDailySalesJobIT に書く | |
| T-04 | done | 1 | T-03 | ORDER-001 1.4 版の差分を /review-standards の手順1〜3でレビューし指摘を反映する | |
| T-05 | doing | 1 | T-04 | ORDER-001 のシステムテスト（No.3 の改訂・別の実行回をまたぐリスタートの追加）を実施し scenarios.md に記録する | |
| T-06 | todo | 0 | T-01,T-02,T-03,T-04,T-05 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
