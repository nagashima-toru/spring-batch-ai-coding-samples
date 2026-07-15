# サンプル02: 固定長ファイル → DB 取込（Chunk）

レガシーシステムから連携される Shift_JIS 固定長ファイルを `app.product` へ取り込む。

## デモする機能

- **バイト長ベース**の固定長パース（`FixedLengthItemReaderFactory` / `ByteLengthLineTokenizer`）
  - 全角文字（Shift_JIS で2バイト）混在レイアウトに対応
  - フィールド境界がマルチバイト文字を分断するデータはエラー検出
- パースエラー行の Skip・エラーレコード退避（不正行があっても処理継続）
- ゼロ埋め数字文字列 → 数値変換（Processor での業務変換）

## レイアウト定義（バイト数）

| フィールド | バイト長 | 例 |
|---|---|---|
| productCode | 8 | `P0000001` |
| productName | 20 | `りんご　　　　　　　`（全角・全角スペース埋め） |
| price | 8 | `00001000`（右詰めゼロ埋め） |

## 実行方法

```bash
./mvnw -q spring-boot:run -pl batch-sample -Dspring-boot.run.arguments="\
  --spring.batch.job.name=sample02FixedLengthImportJob \
  inputFile=batch-sample/data/in/products.dat \
  errorFile=batch-sample/data/error/products_error.txt"
```

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| メインフレーム・レガシー間連携の固定長ファイル（バイト長定義） | 新規設計のファイルIF → CSV/JSON を選ぶ（サンプル01） |
| 文字コードが Shift_JIS 等マルチバイト | 文字数ベースの固定長（標準の FixedLengthTokenizer で可） |

## 実装ポイント

- Spring Batch 標準の `FixedLengthTokenizer` は**文字数**ベースのため使用しない（規約）
- パディング除去は基盤部品が行う（半角・全角スペースとも除去。変更は `stripPadding` 拡張）
