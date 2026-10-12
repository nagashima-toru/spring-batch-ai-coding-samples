---
id: P-20261012-harness-build-criteria
status: approved
---
# ゴール

GitHub Issue #42 と #44 を解消する。

- #42：batch-core の `JobParametersValidators` の Javadoc（未知のキーは拒否）と Spring Batch 6 の実際の動作（WARN のみ）の食い違いをなくす
- #44：ハーネスの受け入れ基準 `./mvnw verify -DskipITs` で batch-sample のカバレッジが必ず落ち、`target/` に残った実行データで判定がぶれる問題をなくす（ビルド基準の書き方・clean 付きの実行・クラウド環境の準備例）

## 分割方針

- #42 は Issue の提案2（Javadoc を実態に合わせ、拒否が必要なジョブは個別の Validator で行う方針を architecture.md に書く）で進める（理由は T-01 の「決定済み」。提案1にするかは「## 人への質問」で確認する）。SSoT の順に、方針（docs）の T-01 → コードの Javadoc とテストの T-02 を `after` でつなぐ
- #44 は Issue の提案1〜3を、planner ルール（T-03）・verifier と creator のルール（T-04）・開発ガイド（T-05）に分ける。ビルドのコマンドは T-03〜T-05 の「決定済み」に同じ文字列で書いてあるので並行できるが、`vault/rules/` 全体に旧形式が残っていないことの確認を T-04 で行うため、T-04 は T-03 の後にする
- 各タスクの成果物のファイルは重ならない（T-01：architecture.md・ORDER-001.md、T-02：batch-core の Validator とテスト・ORDER-001 の Validator、T-03：planner ルール・decisions.md、T-04：verifier・creator ルール、T-05：development-guide.md）

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | 方式設計書にジョブパラメータ検証の方針（未知のキーは個別 Validator で拒否）を書き、ORDER-001 の記述を直す | |
| T-02 | done | 1 | T-01 | JobParametersValidators の Javadoc を実際の動作に合わせ、未知のキーを通す単体テストを足す | |
| T-03 | done | 1 | - | planner ルールのビルド基準を clean 付き・batch-sample カバレッジ非判定の2コマンドに直し、決定を decisions.md に残す | |
| T-04 | review | 1 | T-03 | verifier・creator ルールのビルド実行を clean 付きの2コマンドに直す | |
| T-05 | done | 1 | - | 開発ガイドにクラウド環境の準備スクリプト例を足し、ハーネスの検証コマンドの記述を直す | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 次フェーズの候補（票は起こさない）
- 工程 Skill（`.claude/skills/unit-test/SKILL.md` の `./mvnw verify -DskipITs -pl {module} -am` によるカバレッジ充足ループ、`integration-test`・`review-code`・`sdd-pipeline` の `./mvnw verify`）にも、batch-sample のカバレッジと `target/` の残留データ（JaCoCo の `append` 既定 true）の問題が同じように当たる。sdd-pipeline 側の流れでありハーネスの受け入れ基準ではないので本計画では扱わない
- `.github/pull_request_template.md`・`.github/PULL_REQUEST_TEMPLATE/implementation.md` の `./mvnw verify` も clean 付きにするか

## 人への質問（回答済み）
1. #42 は提案2（実装は変えず、Javadoc を「未知のキーは拒否しない（WARN のみ）」に直し、拒否が必要なジョブは個別の Validator で行う方針を `docs/architecture.md` に書く）で計画しました。提案1（`JobParametersValidators` を未知のキーを拒否する実装に直す）にしない理由は、既存の結合テストが `AbstractJobIT#uniqueParameters()` で `testRunId` を付けて `JobInstance` を分けており（`docs/projects/member-point-expiry/detail-design/POINT-001.md` §2 もこの挙動を前提に書いている）、拒否にすると全ジョブの結合テストと設計書の改訂が要るためです。提案2で進めてよいですか。提案1にする場合は T-01・T-02 を書き直します
   - 回答：案2で進める（人の回答 2026-10-12）
