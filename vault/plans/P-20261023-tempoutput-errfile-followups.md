---
id: P-20261023-tempoutput-errfile-followups
status: approved
---
# ゴール
GitHub Issue #102・#104・#105・#106 をまとめて対応する（PR #103 の `/code-review` の指摘の後始末）。
- #102 sample03 に一時ディレクトリを指定する任意のジョブパラメータを足し、`TempOutputFile.inDirectory` で配置先の外に置く例を示す。同じファイルシステムの前提・シンボリックリンクの限界・同じ出力ファイル名で並行実行しない前提を文書に書く
- #104 `TempOutputFile.inSubdirectory` の `dirName` の検証を強め、重なり検査を祖先・子孫まで見る
- #105 `ErrorRecordSkipListener` がエラーレコードファイルへ書くエラー理由の改行・タブを置き換え、方式設計 §5.2 に書式を書く
- #106 `TempOutputFile.prepare` が前回の失敗で残った一時ファイルをどう扱うかの方針を決め、Javadoc・sample03 の文書・単体テストに反映する

## 分割方針
- `TempOutputFile.java`・`TempOutputFileTest.java` を変える #104（T-01）と #106（T-02）は同じファイルが重なるので、T-01 → T-02 の直列にする。`Sample03JobConfig` と `batch-sample/docs/sample03-file-export.md` を変える #102（T-03）は、T-01 の新しい検証と T-02 の方針を文書に書くので、両方の後に置く。sample03 の文書の変更（#102 のコメントの3点と #106 の方針）は T-03 にまとめ、T-01・T-02 では sample03 の文書を変えない
- #105（T-04）は `ErrorRecordSkipListener` と `docs/architecture.md` だけで、T-01〜T-03 と変えるファイルが重ならないので並行にする（`after` は `-`）
- コードを変更するタスク（T-01〜T-04）があるので、最後に計画単位のレビュータスク（T-05）を置く
- 方式の決定と理由：
  - #104 の検証仕様：`dirName` は単一のディレクトリ名に限る（null・空文字・`.`・`..`・`/` か `\` を含む文字列を拒否）。`inSubdirectory` は「配置先の親ディレクトリ配下」と名前で約束しているので、そこから外へ出る値を通す理由が無く、外に置きたい時は `inDirectory` がある。重なり検査は「配置先ファイルが一時ディレクトリの配下か同じ（一時ディレクトリが配置先の祖先・親・同じパス）」と「一時ディレクトリが配置先ファイルの配下」を拒否する形にする。配置先ディレクトリの配下の別ディレクトリ（`inSubdirectory` の既定の置き方）は許す。シンボリックリンクは解決しない（`toRealPath` は存在するパスにしか使えず、一時ディレクトリは生成時点でまだ無いことが多いため）。限界として Javadoc と sample03 の文書に書く
  - #106 の方針：`prepare` は残った一時ファイルを消さず、Writer の上書きに任せる（今の動作を変えず、方針として明文化する）。batch-core の `CsvItemWriterFactory`・`FixedLengthItemWriterFactory` は `shouldDeleteIfExists(true)` で、リスタートでない実行では Writer が開く時に既存のファイルを消して書き直す。Writer が `saveState=true` でリスタートする時は、Spring Batch が保存した位置から書き継ぐので、`prepare` で消すとリスタートを壊す。残っていれば失敗させる方式は、失敗後の再実行（一時ファイルが残るのが通常）を止めてしまう。sample03 は Writer も Reader も `saveState=false` なので、リスタートでも一時ファイルを最初から書き直す（方式設計 §6.2「抽出ジョブは出力ファイルを上書き生成する」）
  - #102 の一時ディレクトリの指定：ジョブパラメータ `tempDir`（任意）にする。既存の任意パラメータ `format` と同じ置き方で、設定（`SampleProperties`）にすると実行ごとに選べないため。起動時のパラメータ検証で `TempOutputFile` を組み立て、重なりの `SystemException` を `InvalidJobParametersException` に変える（ファイル名 `tmp` の拒否もこれに含まれる）
  - #105 の置き換え：エラー理由の `\r`・`\n`・タブをそれぞれ半角空白1文字に置き換える。元レコードは置き換えない（Issue の範囲がエラー理由だけで、元レコードは手動リカバリでそのまま使う値のため）。WARN ログの reason（`cause=...`）は変えない

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | done | 1 | - | TempOutputFile の dirName を単一のディレクトリ名に限り、重なり検査を祖先・子孫まで見る（#104） | |
| T-02 | done | 1 | T-01 | TempOutputFile.prepare が残った一時ファイルを消さず Writer の上書きに任せる方針を Javadoc と単体テストで示す（#106） | |
| T-03 | done | 1 | T-01,T-02 | sample03 に任意のジョブパラメータ tempDir を足し、inDirectory の例と前提を文書に書く（#102） | |
| T-04 | done | 1 | - | ErrorRecordSkipListener がエラー理由の改行・タブを空白に置き換えて1行1レコードを保つ（#105） | |
| T-05 | done | 1 | T-01,T-02,T-03,T-04 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
