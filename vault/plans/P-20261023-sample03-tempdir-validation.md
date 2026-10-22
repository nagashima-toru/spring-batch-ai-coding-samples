---
id: P-20261023-sample03-tempdir-validation
status: approved
---
# ゴール
GitHub Issue #107 に対応する。sample03（`Sample03JobConfig`）の任意パラメータ `tempDir` について、(1) NUL 文字などパスとして解釈できない値を `InvalidJobParametersException` に変換して起動時に拒否する、(2) 空白だけの値を未指定として扱う、(3) `tempDir` を識別パラメータにするかを判断して `batch-sample/docs/sample03-file-export.md` に書く、(4) `TempOutputFileTest` に、空でない `ExecutionContext`（リスタート）の場合に `prepare` が前回の一時ファイルを消さないことを示す単体テストを足す。直前の計画（PR #108）で足した `tempDir` と `TempOutputFile`（dirName 検証・prepare の方針）と整合させる。

## 分割方針
- 変えるファイルで分ける。T-01 は sample03 のジョブ定義と単体テスト（入力検証）、T-02 は batch-core の `TempOutputFileTest`（リスタートのテスト）、T-03 は sample03 の解説文書。3つは別ファイルで、決める内容は各タスク票の「決定済み」に書いたので、互いに `after` を付けず並行にする（同じファイルを2タスクが変えない）
- 識別パラメータの判断は「識別パラメータのままにする」（コードは変えない）。理由は T-03 の「決定済み」に書く。空白だけの値は、sample05 の `inputFile`（`InputSourceDecider.isFileInput` が空白だけを未指定とする）に合わせて未指定として扱う（拒否しない）
- T-01・T-02 はコードを変更するタスクなので、全タスクの後に計画単位のレビュータスク T-04 を置く
- ジョブ新規作成ではないため `vault/rules/planner/job-task-split.md` の工程5タスクの分割は使わない。ビルドの確認（`-DskipITs -Djacoco.haltOnFailure=false` と `-DskipITs -pl batch-core`、`*IT.java` を変える時やレビュータスクでは `./mvnw clean verify`）だけを同ルールに合わせて入れる

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | doing | 1 | - | sample03 の tempDir の不正なパスを起動時に拒否し空白だけを未指定として扱う | |
| T-02 | review | 1 | - | TempOutputFileTest にリスタート時に prepare が一時ファイルを消さないテストを足す | |
| T-03 | review | 1 | - | sample03-file-export.md に tempDir の空白・不正なパスの扱いと識別パラメータの判断を書く | |
| T-04 | todo | 0 | T-01,T-02,T-03 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
