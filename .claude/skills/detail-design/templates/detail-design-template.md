# 詳細設計書: {ジョブID} {ジョブ名}

| 項目 | 内容 |
|---|---|
| ジョブID / ジョブ名（Bean名） | |
| 入力（基本設計書） | docs/projects/{案件名}/basic-design.md（版） |
| ステータス | 作成中 / レビュー済み |

## 1. ジョブ構成

| 項目 | 内容 |
|---|---|
| ジョブパッケージ | com.example.batch.xxx.job_{内容} |
| Config クラス | {Xxx}JobConfig |
| ジョブパラメータ検証 | JobParametersValidators.required("...") |
| ジョブリスナー | DuplicateRunPreventingJobExecutionListener / LoggingJobExecutionListener |

### Step 一覧・フロー

| # | Step名 | 型 | 概要 | 失敗時 |
|---|---|---|---|---|
| 1 | {job}.01XxxStep | Tasklet | 前処理チェック | ジョブ異常終了 |
| 2 | {job}.02XxxStep | Chunk | 本処理 | リスタート対象 |

（分岐がある場合は Mermaid でフロー図を記載）

## 2. Step 詳細設計（Step ごとに記載）

### Step {n}: {Step名}

| 構成要素 | 実装 | 使用する基盤部品 |
|---|---|---|
| Reader | | CsvItemReaderFactory 等 |
| Processor | | ValidatingItemProcessorBase 継承 |
| Writer | | MyBatisBatchItemWriter（statementId） |
| Skip/Retry | | BusinessSkipPolicy(上限N) / ErrorRecordSkipListener |
| チャンクサイズ | | SampleProperties から注入 |

#### 処理フロー

1. （入力 → チェック → 変換 → 出力 の順で日本語で記述）

#### 入力チェック仕様

| 項目 | チェック内容 | 実装（Bean Validation / 業務チェック） | エラー時 |
|---|---|---|---|

## 3. エラー処理設計

| # | エラー事象 | 分類 | 例外 | 動作 | 退避・出力先 |
|---|---|---|---|---|---|
| 1 | 入力ファイルなし | 業務 | BusinessException | ジョブ異常終了 | - |
| 2 | 形式不正レコード | 業務 | BusinessException | Skip（上限N） | エラーレコードファイル |
| 3 | DB接続断 | システム | （SQL例外） | 即時異常終了→リスタート | - |

## 4. SQL設計（Mapper XML 方針）

| Mapper#メソッド | 種別 | SQL方針 |
|---|---|---|
| XxxMapper#upsert | INSERT | ON CONFLICT DO UPDATE（冪等） |

## 5. リラン設計

- 途中失敗時: （同一パラメータでリスタート → 続きから再開 / 最初から再実行）
- 冪等性: （UPSERT / 洗い替え / 出力上書き）
- 成功後の再処理: （業務パラメータを変えて実行）

## 6. テスト観点メモ（テスト工程への引き継ぎ）

- （このジョブ固有の境界値・異常系で必ず検証すべきこと）
