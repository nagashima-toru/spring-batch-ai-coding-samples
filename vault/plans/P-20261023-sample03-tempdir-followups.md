---
id: P-20261023-sample03-tempdir-followups
status: approved
---
# ゴール
GitHub Issue #110 に対応する。sample03（`Sample03JobConfig`）について、(1) 空白だけの `tempDir` が未指定とは別の JobInstance になる点を、解消するか `batch-sample/docs/sample03-file-export.md` に明記するかを決めて対応する、(2) `format` の空白だけの値の扱い（`isBlank` かどうか）を `tempDir` と揃えるか違いを文書に書く、(3) `Sample03JobConfigTest` に `outputFile` の NUL 文字のテストを足す。直前の計画（PR #109）で足した `tempDir` の検証（`validateParameters`、空白だけは未指定扱い、識別パラメータのまま）と整合させる。

## 分割方針
- 方式の判断（1）：空白だけの値が別の JobInstance になる点は**解消せず、文書に明記する**。理由は次のとおり
  - JobParameters の作成時の正規化は、コマンドライン引数から JobParameters を作る全ジョブ共通の処理（Spring Boot の `JobLauncherApplicationRunner` と `JobParametersConverter`）を差し替えることになり、sample03 の範囲を超えて全ジョブ・基盤の方針（`docs/architecture.md` §3.4）に及ぶ。`JobParametersValidator` は値を検査するだけで書き換えられないので、ジョブ側では正規化できない
  - 他ジョブ（sample05 の `inputFile`、`InputSourceDecider.isFileInput` の `isBlank()`）も空白だけを未指定として扱うだけで、正規化はしていない。sample03 だけ正規化すると扱いが分かれる
  - 影響はリスタートにならず出力 Step から作り直すことだけで、出力ファイルは上書き生成なので結果は変わらない（`docs/architecture.md` §6.2）
  - 補足：コマンドラインから起動する経路では、Spring Boot が `StringUtils.splitArrayElementsIntoProperties` で値の前後の空白を取り除くので、空白だけの値は空文字になる（spring-core 7.0.9 で確認）。空文字も未指定とは別の JobInstance になるので、文書には「空文字・空白だけの値」と書く
- 方式の判断（2）：`format` の空白だけの値は **`tempDir` に揃えて未指定（csv）として扱う**。`validateFormat` の判定を `isEmpty()` から `isBlank()` にする。Writer は `"fixed".equals(format)` 以外を CSV にしているので、Writer は変えない。今は空白だけの `format` が起動時に拒否されるが、空文字は csv になるので、空文字と空白だけの扱いが分かれていた
- 変えるファイルで分ける。T-01 は `Sample03JobConfig.java`（`format` の判定と Javadoc）と単体テスト1件、T-02 は `Sample03JobConfigTest.java` への `outputFile` の NUL 文字のテスト1件、T-03 は `sample03-file-export.md`。T-01 と T-02 は同じテストファイルを変えるので、T-02 は `after` を T-01 にして直列にする。T-03 は別ファイルで、書く内容は T-03 の「決定済み」にあるので T-01 と並行にする
- T-01・T-02 はコードを変更するタスクなので、全タスクの後に計画単位のレビュータスク T-04 を置く
- ジョブ新規作成ではないため `vault/rules/planner/job-task-split.md` の工程5タスクの分割は使わない。ビルドの確認（`-DskipITs -Djacoco.haltOnFailure=false` と `-DskipITs -pl batch-core`、レビュータスクでは `./mvnw clean verify`）だけを同ルールに合わせて入れる。`*IT.java` は変えない

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | sample03 の format の空白だけの値を tempDir と揃えて未指定（csv）として扱う | |
| T-02 | todo | 0 | T-01 | Sample03JobConfigTest に outputFile の NUL 文字を拒否するテストを足す | |
| T-03 | review | 1 | - | sample03-file-export.md に空白だけの値が別の JobInstance になることと format の空白の扱いを書く | |
| T-04 | todo | 0 | T-01,T-02,T-03 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
