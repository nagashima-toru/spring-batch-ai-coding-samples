---
name: unit-test
description: 実装コードの単体テスト（JUnit 5 + Mockito）を作成する。「単体テストを書いて」「このProcessorのテストを作って」「テストケースを洗い出して」のような発話や、実装完了後の次工程として発動する。境界値・異常系の観点表からのテスト設計にも使用する。
---

# 単体テスト Skill

実装コードの単体テストを JUnit 5 + Mockito + AssertJ で作成する。Spring コンテキストは使わない。

## 入力と出力（工程連鎖）

| | 内容 |
|---|---|
| 入力 | 実装コード＋詳細設計書（`docs/projects/{案件名}/detail-design/{ジョブID}.md` の入力チェック仕様・テスト観点メモ） |
| 参照 | `references/test-perspectives.md`（境界値・異常系の観点表）、既存テスト（`batch-core/src/test`, `batch-sample/src/test`） |
| 出力 | `src/test/java/...{同パッケージ}/{対象クラス}Test.java` |
| 次工程 | `/integration-test`（ジョブ全体の検証はそちらの責務） |

## 対象と非対象

| 単体テストの対象 | 対象外（結合テストの責務） |
|---|---|
| Processor（チェック・変換ロジック） | ジョブ・Step の起動と遷移 |
| Tasklet（モック化した依存で） | DB アクセス（Mapper の SQL） |
| 独自の部品クラス | Reader/Writer と実ファイル・実DBの結合 |

## 進め方

1. 詳細設計書の「入力チェック仕様」「テスト観点メモ」からテストケース表を作る
2. `references/test-perspectives.md` の観点表と突き合わせ、漏れを補完する
3. テストケース表をユーザーに提示してから実装する（観点の合意を先に取る）
4. 実装後 `./mvnw test -pl {module}` で全件成功を確認する

## テストコード規約（coding-standards.md §7 抜粋）

- クラス名 `{対象}Test`、メソッド名は日本語で「条件＿期待結果」
- AAA 構造（Arrange / Act / Assert を空行で区切る）、1テスト1検証観点
- AssertJ の `assertThat` / `assertThatThrownBy` を使用
- Validator が必要な場合は `Validation.buildDefaultValidatorFactory()`（static、@AfterAll で close）
- 例外検証はメッセージの要点まで検証する（`.hasMessageContaining(...)`）
- Spring コンテキスト・実DB・実HTTP を使わない（依存は Mockito でモック化）

## カバレッジ

- JaCoCo 命令カバレッジ 80% 以上（`./mvnw verify` の閾値チェックで強制）
- 数値合わせのためのテストは書かない。観点表に基づく意味のあるケースで到達させる
