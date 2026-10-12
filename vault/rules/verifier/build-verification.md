# verifier ルール：ビルドを実行して判定する

## 目的
工程タスクを、creator の申告ではなく verifier 自身が実行したビルドの結果と docs を根拠に判定できるようにする。

## ルール
- 工程タスクの判定では毎回自分で `./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false` と `./mvnw clean verify -DskipITs -pl batch-core` を実行する（`clean` を付けるのは、`target/` に残った JaCoCo の実行データで結果が変わらないようにするため）。それぞれの終了コードと末尾の `BUILD SUCCESS`／`BUILD FAILURE` を note に書く。creator の進捗や報告を根拠にしない
- `-DskipITs` の結果では batch-sample のカバレッジを判定しない。`Coverage checks have not been met` の WARNING が出ても、それだけを FAIL の根拠にしない（batch-sample のカバレッジは `./mvnw clean verify` で判定する。`docs/decisions.md`）
- 成果物に `*IT.java` を含むタスクでは `./mvnw clean verify` も実行する
- Docker が使えず（`docker info` が失敗）`./mvnw clean verify` を実行できない時は PASS にせず FAIL とし、reasons に「Docker が使えず結合テストを実行できない」と書く（検証できないものを PASS にしないため。attempt の上限で blocked になり人に渡る）
- 失敗した時は、原因を `docs/development-guide.md` §5.1 の表にあるレポートの場所で読み、note に要点を書く（表は書き写さない）
- 差分に `docs/development-guide.md` §5.4 の反則（カバレッジ閾値の変更・除外設定の追加・テストの削除や `@Disabled` 化・適用済み Flyway ファイルの書き換え）が見つかった時の扱い：
  - 受け入れ基準がこのルールや §5.4 を参照している時は FAIL の根拠にする
  - 参照していない時は reasons に記録する（`.claude/agents/verifier.md` の「ルールは基準の判定方法を与える」に合わせる）
- 方針の根拠は `docs/architecture.md` §12 テスト方針。本文はここに写さず、パスと節番号で参照する

## 確認方法
- verdict の note に、実行したコマンドと `BUILD SUCCESS` の有無が書かれていること
- 参照した節番号が実在することを `grep -n '^#' docs/development-guide.md`・`grep -n '^#' docs/architecture.md` で確かめる
