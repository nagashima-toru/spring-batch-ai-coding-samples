---
id: P-20261012-make-repo-public
status: approved
---
# ゴール
このリポジトリ（nagashima-toru/spring-batch-ai-coding-samples、現在 private）を public にするための、リポジトリ側の準備を整える。可視性の切り替え（GitHub の Settings での操作）そのものは不可逆な外部操作なのでタスクにせず、人が PR マージ後に行う手順として公開手順チェックリストに書くにとどめる。

## 分割方針
- 「公開してよいか」の判断材料（棚卸し）と「公開に必要な付帯物」（LICENSE・README）と「GitHub 側の設定・手順」（チェックリスト）を別タスクにする
- T-01（棚卸しレポート）と T-02（LICENSE）は互いに独立なので並行できる
- T-03（README）は LICENSE へのリンクを書くので T-02 の後にする
- T-04（公開手順チェックリスト）は棚卸しの結果（残存ブランチ・履歴の扱い）を手順に取り込むので T-01 の後にする
- 履歴の書き換え・リモートブランチの削除・可視性の変更・GitHub の Settings の変更は、破壊的または外部への操作なのでどのタスクでも行わない。必要なものはチェックリストに「人が行う作業」として書く
- smoke の `(ref-1)` の対象文書のうち成果物になるのは `README.md`（T-03）だけ。README には計画の中で新しく作る docs のパスを書かない（T-03 の決定済み）ので、ほかのタスクとの順序の矛盾は生じない

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | 機密・個人・内部情報の棚卸しレポート（作業ツリーと git 履歴・リモートの ref） | |
| T-02 | done | 1 | - | LICENSE（MIT License）を追加する | |
| T-03 | done | 1 | T-02 | README に ライセンス・コントリビュート・注意事項 の節を追記する | |
| T-04 | review | 1 | T-01 | GitHub 設定・CI の確認結果を含む公開手順チェックリストを docs に作る | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## マージ後に人が行う作業
- `docs/public-release-checklist.md`（T-04 で作る）の「可視性の切り替え（人が行う）」までの項目を順に実施し、最後に GitHub の Settings で可視性を Public に切り替える

## 次フェーズの候補（票は起こさない）
- `pom.xml` の `<licenses>` 要素の追加（Maven のメタデータにライセンスを載せる。今回のゴールには不要なので含めない）
- `SECURITY.md`・`CONTRIBUTING.md`・Issue テンプレートの整備（README のコントリビュート節で足りなくなった時に起こす）

## 人への質問（回答済み）
1. ライセンス種別は Apache License 2.0 でよいか → 回答：MIT License にする（T-02・T-03 を MIT に書き換えた）
2. 著作権者の表記は `Copyright 2026 Toru Nagashima` でよいか → 回答：OK（MIT の表記として `Copyright (c) 2026 Toru Nagashima`）
3. コントリビュート方針は「Issue での不具合報告・質問を受け付ける。Pull Request は事前に Issue で相談したうえで受け付ける。対応や回答は保証しない」でよいか → 回答：OK
4. git の履歴は書き換えない前提でよいか → 回答：機微な情報が無ければ OK（T-01 の決定済みに、機微な情報が見つかった時は blocked にする条件を書いた）
