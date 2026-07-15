# サンプル08【骨格】: S3 入出力（プロファイル切替）

ファイル入出力の取得先を「ローカル: ファイルシステム / AWS: S3」でプロファイル切替する
パターンの骨格実装。**ローカル開発を AWS 非依存に保つ**（architecture.md §1.1）ための構成。

## 構成

- `InputResourceProvider` — 取得先を抽象化するインターフェース（ジョブはこれにのみ依存）
- `LocalFileResourceProvider` — `local` プロファイル用（`FileSystemResource`）
- `S3ResourceProvider` — `dev`/`prod` プロファイル用（骨格。未実装）

## S3 実装手順

1. 依存を追加する（バージョンは Spring Cloud AWS の BOM で管理）

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-s3</artifactId>
</dependency>
```

2. `S3ResourceProvider` を `S3Resource`（`s3://bucket/key`）を返す実装へ置き換える
   （`ResourceLoader` に `s3://` プレフィックスを渡すだけでよい）
3. ECS タスクロールに対象バケットの `s3:GetObject` / `s3:PutObject` を付与する
4. サンプル01/03 の Reader/Writer の `FileSystemResource` を
   `InputResourceProvider#resolve` 経由に変更する（ジョブ本体のロジックは無変更）

## 設計上の注意

- S3 は**書込途中の状態が見えない**（PUT完了までオブジェクトが存在しない）ため、
  出力側はローカル一時ファイルへ書いてから PUT する方式も検討する（大容量はマルチパート）
- `FlatFileItemReader` は `Resource` を受けるため S3Resource をそのまま使えるが、
  リスタート時は同一オブジェクトが再取得できること（上書きされないこと）が前提
- LocalStack を使う場合は `spring.cloud.aws.endpoint` をローカルへ向ける（任意）

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| AWS 上でファイル連携するバッチ（S3 が標準的な受け渡し場所） | オンプレ・コンテナ内で完結するファイル処理 |
| ローカル開発・CI を AWS 非依存にしたい | 全環境で常に S3 を使う方針なら抽象化は過剰（直接 S3Resource を使う） |
