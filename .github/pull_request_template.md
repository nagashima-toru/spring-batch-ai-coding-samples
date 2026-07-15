## 概要

（何を・なぜ。Closes #issue番号）

## 設計書リンク（SSoT）

- 基本設計書: docs/projects/{案件}/basic-design.md
- 詳細設計書: docs/projects/{案件}/detail-design/{ジョブID}.md

※ 設計変更を伴う場合は、コードより先に設計書が更新されていること

## セルフチェック

- [ ] `./mvnw verify` がローカルで成功する（単体＋結合＋静的解析＋カバレッジ）
- [ ] `/review-design` を実施し、指摘を反映した（設計変更がある場合）
- [ ] `/review-code` を実施し、指摘を反映した
- [ ] 基盤部品（batch-core）で実現できる処理を独自実装していない
- [ ] リスタート動作を確認した（結合テストまたは手動。取込・更新系ジョブの場合）
- [ ] Flyway は新規ファイル追加のみ（適用済みファイルを変更していない）

## レビュー実施記録

（review-design / review-code の指摘件数と対応状況を記載）
