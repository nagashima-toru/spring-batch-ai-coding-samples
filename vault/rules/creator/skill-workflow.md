# creator ルール：工程 Skill に従い確認手順を読み替える

## 目的
工程タスク（implementation〜system-test）を既存の工程 Skill の手順に沿って進め、ユーザー確認の手順に当たった時は読み替え表に従い、独断できない判断は blocked で返せるようにする。

## ルール
- タスクの title・目的に書かれた工程（implementation・unit-test・integration-test・review-code・system-test）の `.claude/skills/<工程>/SKILL.md` を着手前に読み、その「進め方」に従う
- SKILL.md の「ユーザーに確認・合意する」手順に当たったら、`.claude/skills/sdd-pipeline/SKILL.md` の「委譲先Skillの記述を上書きする点」の表にある「パイプライン実行時の扱い」に読み替える（表の中身は書き写さない）
- 読み替え表で「主エージェントに提示・報告」とある成果（unit-test のテストケース表、system-test のシナリオ一覧）は、タスク票の「進捗」に書く（完了報告には経緯を書かないため）
- `[Ask]` に当たる指摘や、読み替え表に無い確認が要る時は独断せず、完了報告を `blocked: <質問文>` にして返す。`[Ask]` は blocked にする
- review-code のタスクは `/review-code` の手順でレビューし、[Must]/[Should] を自分で修正したうえで、受け入れ基準のビルドコマンドを通す。[Ask] が残ったら blocked にする
- system-test のタスクで、外部依存などでローカル実施できないシナリオがある時は blocked にする
- integration-test のタスクで `docker info` が失敗する（Docker が使えない）時は blocked にする
- 設計書と異なる実装が必要と分かった時は、設計書を自分で直さず blocked にする（`docs/development-guide.md` §5.5）
- 検証を弱めて通さない。反則の禁止は `docs/development-guide.md` §5.4 に従う
- モデル昇格は使わない（`docs/development-guide.md` §5.3）。同じエラーが2ラウンド続いて解消しない時は、昇格の代わりに §5.3 の1〜3を質問文に含めて `blocked:` で返す。再試行は attempt で run が行う
- 基盤部品（batch-core）で実現できる処理を独自実装しない（`.claude/skills/implementation/SKILL.md` の対応表）

## 確認方法
- 完了報告の前に、受け入れ基準のビルドコマンド（`./mvnw clean verify -DskipITs -Djacoco.haltOnFailure=false` など）を自分で実行して通す
- 「進捗」に、従った工程 Skill と、読み替えた確認手順（あれば）が残っているか確認する
