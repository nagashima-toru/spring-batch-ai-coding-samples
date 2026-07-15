# サンプル09【骨格】: ジョブ異常終了通知（JobExecutionListener 応用）

ジョブ失敗時に Slack / SNS へ通知するパターンの骨格実装。

## 構成

- `NotificationSender` — 通知先の抽象化
- `SlackWebhookNotificationSender` — Slack Incoming Webhook 実装
  （HTTP Client 基盤を利用するため、この部分は動作するコード）
- `SnsNotificationSender` — Amazon SNS（骨格。未実装）
- `JobFailureNotificationListener` — afterJob で失敗を検知して通知するリスナー

## 使い方（Slack通知を有効にする場合）

1. Bean を定義し、通知したいジョブの listener に追加する

```java
@Bean
JobFailureNotificationListener jobFailureNotificationListener(BatchHttpClient httpClient,
        @Value("${sample.job09.slack-webhook-url}") String webhookUrl) {
    return new JobFailureNotificationListener(
            new SlackWebhookNotificationSender(httpClient, webhookUrl));
}
```

2. Webhook URL は機密情報のため Secrets Manager から環境変数注入する（コード直書き禁止）

## SNS 実装手順（骨格の置き換え）

1. `software.amazon.awssdk:sns` を依存に追加
2. `SnsClient#publish` でトピックへ発行する実装へ置き換え
3. ECS タスクロールに `sns:Publish` を付与

## 設計上の注意（重要）

- **通知の失敗でジョブの終了コードを変えない**。リスナー内で通知エラーを catch してログに留める
  （実装済み。通知は運用補助であり、ジョブ本体の成否と分離する）
- 通知はあくまで即時性のための補助手段。**一次的な失敗検知は CloudWatch（終了コード・
  ログのメトリクスフィルタ）と EventBridge 側で行う**多層構成にする
- 全ジョブへ一律に付けたい場合は、ジョブ定義側で共通 listener として追加する
  （auto-configuration 化する場合は batch-core への昇格を検討）

## このパターンを選ぶべき場面 / 選ばない場面

| 選ぶ | 選ばない |
|---|---|
| 失敗を即座にチャットで知りたい（人の初動を早くする） | 監視基盤（CloudWatch Alarm→SNS→チャット連携）が既にある → アプリ内通知は二重実装になるので不要 |
