---
id: P-20261020-size-s-issues
status: approved
---
# ゴール
GitHub の open な Issue のうち size: S ラベルの4件（#77・#75・#73・#63）をすべて解決する。1 Issue = 1 タスクを基本とする。

## 分割方針
- 1 Issue = 1 タスク。T-01 が #77（CsvHeaderCheckTasklet の BOM）、T-02 が #75（ORDER-001 の実行時パラメータを @JobScope Bean で1回だけ解決）、T-03 が #73（ByteLengthLineAggregator の表せない文字）、T-04 が #63（コアサンプル03/04/05 の単体テスト）。最後に計画単位のレビュータスク T-05 を足す（T-01〜T-04 はすべてコードを変更するタスク）
- T-01 と T-02 はどちらも詳細設計書 `docs/projects/order-daily-sales/detail-design/ORDER-001.md` を改訂する（設計書と異なる実装は禁止のため、実装と同じタスクで設計書を直す）。変更履歴の表の末尾に行を足すので、並行させるとマージが衝突する。T-02 の `after` を T-01 にして直列にする（版は T-01 が 1.5、T-02 が 1.6）
- T-03・T-04 はほかのタスクと同じファイルを変えないので、T-01 と並行できる
- 検証は `./mvnw` で行う。全タスクに `-DskipITs` の2つのビルド（`vault/rules/verifier/build-verification.md`）を入れ、結合テスト（`*IT.java`）を成果物に持つ T-02 と、計画単位のレビュータスク T-05 にだけ Docker が要る `./mvnw clean verify` を入れる
- ビルドの成否は `grep -c 'BUILD SUCCESS'` が `1` になることで確かめる（`vault/rules/planner/job-task-split.md` の `^\[INFO\] BUILD SUCCESS$` の形は、確認コマンドの grep パターンにシェルの特殊文字を含めない指針に合わせて短くした）
- 前提：creator・verifier の実行環境に Java 25（Temurin）と Docker（デーモンが動いていること）が要る。planner の環境（Java 21・Docker デーモン無し・Temurin の取得が egress で拒否）では `./mvnw` の確認コマンドを試走できていない。grep・ls の確認コマンドは現状のファイルで期待値を確かめた

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | review | 1 | - | #77 CsvHeaderCheckTasklet が先頭の BOM を除いてヘッダを比べる | |
| T-02 | todo | 0 | T-01 | #75 ORDER-001 の実行時パラメータを @JobScope Bean で1回だけ解決する | |
| T-03 | doing | 1 | - | #73 ByteLengthLineAggregator・Tokenizer が表せない文字で BusinessException を投げる | |
| T-04 | doing | 1 | - | #63 コアサンプル03/04/05 の単体テストを追加する | |
| T-05 | todo | 0 | T-01,T-02,T-03,T-04 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
- Issue #77・#75・#73・#63 の提案がそれぞれ T-01〜T-04 で満たされ、T-05 で `./mvnw clean verify` が成功する
