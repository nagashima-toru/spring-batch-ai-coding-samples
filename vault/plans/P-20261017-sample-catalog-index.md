---
id: P-20261017-sample-catalog-index
status: approved
---
# ゴール
GitHub Issue #62「基盤部品→実例ジョブの索引を作り、案件ジョブの位置付けをカタログに明記する」を解消する。

## 分割方針
- 成果物は `batch-sample/docs/README.md`（2タスク）と `.claude/skills/implementation/SKILL.md`（1タスク）。
- T-01 で README に「基盤部品別の実例ジョブ索引」節（implementation Skill の最重要ルール表の部品 → 実例ジョブのパッケージ / クラス）を追加する。
- T-02 で同じ README の「案件ジョブについて」節を、案件ジョブ3本の別表（設計書・近いリファレンスサンプル・違い）に拡充し、SDD パイプラインの出力例として位置付ける。T-01 と同じファイルなので after T-01。
- T-03 で implementation Skill の「進め方」手順2から T-01 の索引節を参照させる。参照先の節が先に存在している必要があるので after T-01（T-02 とは独立）。
- コード・architecture.md・ルートの README.md 等は変えない。#61（不足部品の実例追加）は範囲外。

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | サンプルカタログに基盤部品別の実例ジョブ索引を追加する | |
| T-02 | doing | 1 | T-01 | サンプルカタログの案件ジョブ節を別表に拡充する | |
| T-03 | doing | 1 | T-01 | implementation Skill の手順2から実例ジョブ索引を参照させる | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
