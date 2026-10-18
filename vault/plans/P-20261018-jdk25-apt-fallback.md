---
id: P-20261018-jdk25-apt-fallback
status: approved
---
# ゴール

GitHub Issue #66 と #84（同内容）に対応する。`docs/development-guide.md` §1.4「クラウド環境での準備」の JDK 25 導入スクリプトを、Temurin（api.adoptium.net）の取得に失敗したら apt の `openjdk-25-jdk-headless` にフォールバックする形にする。フォールバック時は JAVA_HOME を `/usr/lib/jvm/java-25-openjdk-amd64` にして `~/.mavenrc` に書く。技術スタックは Temurin 固定なので、フォールバックである旨と、ローカル検証は OpenJDK 25 でよい旨（CI は Temurin）を明記し、環境の Network access で api.adoptium.net を許可する案内も添える。

PR では `Closes #66`・`Closes #84` に使う。

## 分割方針

- 変更は `docs/development-guide.md` の1ファイル（§1.4 のスクリプトと説明文、§6 トラブルシューティングの表への1行追加）に閉じるので、1タスクにする。
- 計画単位のレビュータスクは足さない。T-01 の成果物は `docs/development-guide.md`（拡張子 `.md`）だけで、コードを変更するタスクが0件のため。
- `docs/development-guide.md` は smoke の `(ref-1)` の対象文書ではないので、`(ref-1)` の基準は入れない。

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | doing | 1 | - | 開発ガイド §1.4 の JDK 25 導入を apt の OpenJDK 25 へフォールバックする形にする | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
