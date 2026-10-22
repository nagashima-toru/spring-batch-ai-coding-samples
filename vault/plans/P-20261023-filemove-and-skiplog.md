---
id: P-20261023-filemove-and-skiplog
status: approved
---
# ゴール

GitHub Issue #72 と #100 をまとめて対応する。

- #72: batch-core に一時出力→配置の一時ファイルのパスを決めて一時ディレクトリを作る部品を足し（単体テスト付き）、sample03（`Sample03JobConfig`）の手書きの処理（`tempFileOf`・`outputDirOf`・一時ディレクトリの作成）をその部品の利用に置き換える
- #100: `ErrorRecordSkipListener` の `onSkipInProcess`・`onSkipInWrite` と、`FlatFileParseException` 以外の読込時の Skip の WARN ログに `t.getMessage()` をそのまま出さず、#97（PR #101）の `onSkipInRead` と揃えて原因の種類だけを出す

## 分割方針

### #72 の方式：(B) 一時ファイルのパスを決めてディレクトリを作るヘルパーを batch-core に足す
- 足す部品は `batch-core/src/main/java/com/example/batch/core/tasklet/TempOutputFile.java`（final クラス。`inSubdirectory`（配置先の親ディレクトリ配下の名前を指定）と `inDirectory`（一時ディレクトリを指定。配置先の外にも置ける）の2つの生成メソッドと、`tempFile()`・`tempDir()`・`outputDir()`・`prepare()`（一時ディレクトリを作って一時ファイルのパスを返す））。`FileMoveTasklet` は変えない
- (B) を選んだ理由
  - (A)（`FileMoveTasklet` の移動先をファイルのパスでも受け取る）だけでは、一時ファイルのパスの決定と一時ディレクトリの作成がジョブごとに残り、問題1（ジョブごとのコピー）が解けない。sample03 が `Files.createDirectories` を直接呼ぶ状態も残る（docs/coding-standards.md §4.2 の「ファイル存在チェック・移動・退避（Files API 直接使用禁止 → 共通 Tasklet）」に反する）
  - 一時ファイルは別のディレクトリに同名で置くので、別名への移動（A）は要らない。`FileMoveTasklet` のコンストラクタにディレクトリとファイルの2つの意味を持たせると、どちらを渡したかが呼び出し側から読めなくなる
  - (B) では問題3（配置先の中に `tmp/` が残る）を `inDirectory` で一時ディレクトリを配置先の外に置くことで避けられ、問題2（一時ディレクトリ名と出力ファイル名の衝突）は部品が一般的な検査（一時ディレクトリが配置先そのもの、または配置先の親ディレクトリと同じなら `SystemException`）として持つ
- sample03 の一時ファイルの場所（`<outputFile の親ディレクトリ>/tmp/<ファイル名>`）と、ファイル名 `tmp` を起動時に拒否する検証は変えない。配置先の外に置くには環境ごとの置き場所（設定かジョブパラメータ）と同じファイルシステムであることの前提が要り、サンプルの仕様変更になって Issue の範囲（部品の利用に置き換える）を超えるため。結合テスト `Sample03FileExportJobIT` は一時ファイルの場所を確かめているので、場所を変えないことで変えずに済む
- この環境では Docker のデーモンが動いておらず結合テスト（`./mvnw clean verify`）を実行できないため、受け入れ基準のビルドは `-DskipITs` の形にする。結合テストは PR の CI（`.github/workflows/ci.yml` の `./mvnw verify`）で確かめる
- T-01（部品と単体テスト）→ T-02（sample03 の置き換えと文書）の順につなぐ

### #100 の方式：`BusinessException` ならメッセージ、それ以外は例外のクラス名だけを出す
- 加工時・書込時の Skip と `FlatFileParseException` 以外の読込時の Skip の WARN ログの `reason` を `cause=<原因>` にする。`<原因>` は #97 の `onSkipInRead` と同じ規則（`BusinessException`（派生を含む）ならそのメッセージ、それ以外は例外のクラスの単純名）。`FlatFileParseException` の時の `line=<n> cause=<原因>` は変えない
- 位置情報は足さない。`SkipListener` の加工時・書込時の通知はレコードと例外だけを受け取り、件番号を持たないため。レコードの内容はログに出さず、エラーレコードファイル（`元レコード<TAB>エラー理由`。方式設計 §5.2）にだけ残す（ファイルの中身は変えない）
- `BusinessException` のメッセージを出してよいかの確認結果：Skip の中で送出される batch-core の部品（`ValidatingItemProcessorBase`・`ByteLengthLineTokenizer`・`ByteLengthLineAggregator`・`StrictCharsetEncoder`）はメッセージに値を含めない方針で Javadoc に書かれている（PR #95）。batch-sample の `OrderItemProcessor` は受注番号・明細番号・受注日（業務キーと日付で、個人情報・機密情報ではない）をメッセージに含め、`docs/projects/order-daily-sales/system-test/scenarios.md` の実施記録にもその形で残っているので変えない。今後の業務例外がこの方針を外れないよう、`BusinessException` の Javadoc にメッセージが WARN ログへそのまま出ることと個人情報・機密情報を含めないことを書く
- `BusinessSkipPolicy` が Skip するのは `BusinessException` と `FlatFileParseException` だけなので、標準の構成ではメッセージが出る。独自の Skip ポリシーで DB の制約違反などを Skip した時は、値を含みうるメッセージの代わりにクラス名だけが出る
- T-03 は T-01・T-02 と成果物のファイルが重ならないので、依存させず並行で進める

### 計画単位のレビュータスク
- T-01〜T-03 は `.java` を成果物に持つコードを変更するタスクなので、最後に計画単位のレビュータスク T-04 を置く

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | 一時出力→配置の一時ファイルのパスを決める TempOutputFile を batch-core に足す（#72） | |
| T-02 | todo | 0 | T-01 | sample03 の一時ファイルのパスの決定と一時ディレクトリの作成を TempOutputFile に置き換える（#72） | |
| T-03 | doing | 1 | - | 加工時・書込時などの Skip の WARN ログに例外のメッセージをそのまま出さない（#100） | |
| T-04 | todo | 0 | T-01,T-02,T-03 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である

## 次フェーズの候補
- sample03 に一時ディレクトリを指定する任意のジョブパラメータ（または設定）を足し、`TempOutputFile.inDirectory` で配置先の外に置く例を示す（同じファイルシステムである前提を文書に書く）
