# AWS セットアップ手順（CD・バッチ実行基盤）

CD ワークフロー（`.github/workflows/cd.yml`）と ECS でのバッチ実行に必要な AWS 側の
事前セットアップ手順。案件のアカウント・命名に読み替えて使用する。

前提: AWS CLI 設定済み・管理者相当の権限。以下の変数を読み替える。

```bash
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=ap-northeast-1
export REPO=nagashima-toru/spring-batch-ai-coding-samples   # GitHubリポジトリ
export APP=batch-sample
```

## 1. GitHub OIDC プロバイダ（アカウントに1回だけ）

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com
```

## 2. CD 用 IAM ロール（GitHub Actions が Assume する）

### 信頼ポリシー（trust-policy.json）

`sub` の条件で **このリポジトリの production 環境からのみ** Assume を許可する。

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:nagashima-toru/spring-batch-ai-coding-samples:environment:production"
      }
    }
  }]
}
```

### 権限ポリシー（ECR push ＋ タスク定義登録に最小限）

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrAuth",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "EcrPush",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability", "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart",
        "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-1:123456789012:repository/batch-sample"
    },
    {
      "Sid": "EcsTaskDef",
      "Effect": "Allow",
      "Action": ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"],
      "Resource": "*"
    },
    {
      "Sid": "PassRolesToTaskDef",
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": [
        "arn:aws:iam::123456789012:role/batch-sample-task-execution-role",
        "arn:aws:iam::123456789012:role/batch-sample-task-role"
      ]
    }
  ]
}
```

```bash
aws iam create-role --role-name github-actions-batch-deploy \
  --assume-role-policy-document file://trust-policy.json
aws iam put-role-policy --role-name github-actions-batch-deploy \
  --policy-name batch-deploy --policy-document file://permissions.json
```

## 3. ECR リポジトリ

```bash
aws ecr create-repository --repository-name $APP \
  --image-scanning-configuration scanOnPush=true
```

## 4. ECS（クラスタ・タスクロール・タスク定義の初期登録）

```bash
aws ecs create-cluster --cluster-name batch-cluster
```

- **タスク実行ロール**（`batch-sample-task-execution-role`）: `AmazonECSTaskExecutionRolePolicy`
  ＋ Secrets Manager の対象シークレットへの `secretsmanager:GetSecretValue`
- **タスクロール**（`batch-sample-task-role`）: ジョブが使う AWS リソース
  （S3・SNS 等。サンプル08/09 を実装した場合）への権限

### 初期タスク定義（task-definition.json）

cd.yml は「既存タスク定義のイメージ差し替え」を行うため、初回は手動で登録する。

```json
{
  "family": "batch-sample",
  "requiresCompatibilities": ["FARGATE"],
  "networkMode": "awsvpc",
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::123456789012:role/batch-sample-task-execution-role",
  "taskRoleArn": "arn:aws:iam::123456789012:role/batch-sample-task-role",
  "containerDefinitions": [{
    "name": "batch-sample",
    "image": "123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/batch-sample:latest",
    "essential": true,
    "environment": [
      { "name": "SPRING_PROFILES_ACTIVE", "value": "prod" }
    ],
    "secrets": [
      { "name": "DB_URL",      "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:batch/db:url::" },
      { "name": "DB_USERNAME", "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:batch/db:username::" },
      { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:batch/db:password::" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/batch-sample",
        "awslogs-region": "ap-northeast-1",
        "awslogs-stream-prefix": "batch",
        "awslogs-create-group": "true"
      }
    }
  }]
}
```

```bash
aws ecs register-task-definition --cli-input-json file://task-definition.json
```

ポイント:
- ログは構造化 JSON（prod プロファイル）で標準出力 → awslogs → CloudWatch Logs
- 機密情報は `secrets`（Secrets Manager）で環境変数注入。タスク定義に平文を書かない
- **`command` はタスク定義に固定しない**（起動側で上書きしてジョブを選択するため）

## 5. EventBridge Scheduler からの定期起動

ジョブごとにスケジュールを作成し、`command` 上書きでジョブ名・パラメータを指定する。

```bash
aws scheduler create-schedule \
  --name batch-sample01-daily \
  --schedule-expression "cron(0 2 * * ? *)" \
  --schedule-expression-timezone "Asia/Tokyo" \
  --flexible-time-window Mode=OFF \
  --target '{
    "Arn": "arn:aws:ecs:ap-northeast-1:123456789012:cluster/batch-cluster",
    "RoleArn": "arn:aws:iam::123456789012:role/scheduler-run-task-role",
    "EcsParameters": {
      "TaskDefinitionArn": "arn:aws:ecs:ap-northeast-1:123456789012:task-definition/batch-sample",
      "LaunchType": "FARGATE",
      "NetworkConfiguration": { "awsvpcConfiguration": {
        "Subnets": ["subnet-xxxx"], "SecurityGroups": ["sg-xxxx"], "AssignPublicIp": "DISABLED" } }
    },
    "Input": "{\"containerOverrides\": [{\"name\": \"batch-sample\", \"command\": [\"--spring.batch.job.name=sample01CsvImportJob\", \"inputFile=/data/in/customers.csv\", \"errorFile=/data/error/customers_error.txt\"]}]}",
    "RetryPolicy": { "MaximumRetryAttempts": 0 }
  }'
```

ポイント:
- **`MaximumRetryAttempts: 0`**（スケジューラの自動リトライは使わない。二重起動防止と
  リスタート運用は方式設計 §6 に従い、失敗時は原因除去後に手動または Step Functions でリラン）
- 失敗検知はタスクの**終了コード**（0=正常 / 2=警告 / 1=異常）を EventBridge ルール
  （`ECS Task State Change`・`stopCode`/`exitCode`）で拾い、通知・後続制御につなぐ
- ジョブ間に依存があるフローは Step Functions（`ECS RunTask.sync`）でオーケストレーションする

## 6. GitHub 側の設定（Environments）

リポジトリの Settings → Environments → `production` を作成し、Variables に以下を設定する。

| 変数 | 例 |
|---|---|
| `AWS_REGION` | `ap-northeast-1` |
| `AWS_ROLE_ARN` | `arn:aws:iam::123456789012:role/github-actions-batch-deploy` |
| `ECR_REPOSITORY` | `batch-sample` |
| `ECS_TASK_FAMILY` | `batch-sample` |

以上で `v*` タグを push すると CD（ECR push → タスク定義新リビジョン登録）が動作する。

```bash
git tag v1.0.0 && git push origin v1.0.0
```
