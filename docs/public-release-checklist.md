# public 化の手順チェックリスト

リポジトリを public にする前後に、所有者が上から順に実施するチェックリスト。棚卸しの結果は `docs/public-release-inventory.md` にある。

## 現状の確認結果

ワークフロー等を読んで確かめた事実。この節のチェックは済んでおり、変更は要らない。

- `.github/workflows/ci.yml` は最上位で `permissions: contents: read` を明示し、`pull_request`・main への push・手動実行（`workflow_dispatch`）を契機に動く。シークレットは使わない
- `.github/workflows/cd.yml` は `v*` タグの push だけを契機に、`environment: production` で動く。`permissions` は `contents: read` と `id-token: write` で、AWS へは OIDC で認証する。AWS の値（`AWS_REGION`・`AWS_ROLE_ARN`・`ECR_REPOSITORY`・`ECS_TASK_FAMILY`）は Environments の Variables（`vars.`）から読む
- `pull_request_target` は使っていない。`secrets.` の参照も無い
- `.github/dependabot.yml` は maven と github-actions を毎週月曜に更新する
- 上記のうち GitHub の Settings で決まる項目（権限・承認・保護・セキュリティ機能）は、リポジトリの外にあるため次の節で人が確かめる

## 公開前に人が行う設定

- [ ] **Workflow permissions**：Settings > Actions > General の Workflow permissions を「Read repository contents and packages permissions」にし、「Allow GitHub Actions to create and approve pull requests」を外す
- [ ] **fork PR の承認**：Settings > Actions > General の fork からの PR のワークフロー実行に承認を必須にする（外部のコントリビューター全員に承認を求める設定を選ぶ）
- [ ] **production 環境の保護**：Settings > Environments > production の Deployment branches and tags を `v*` タグに限定し、Required reviewers に所有者を入れる
- [ ] **OIDC の信頼ポリシー**：AWS の IAM ロールの信頼ポリシーの `sub` が `repo:nagashima-toru/spring-batch-ai-coding-samples:environment:production` に限定されていることを確かめる（`docs/aws-setup.md` の手順どおりか）
- [ ] **Secret scanning**：Settings > Code security で Secret scanning と Push protection を有効にする
- [ ] **Dependabot alerts**：Dependabot alerts と Dependabot security updates を有効にする
- [ ] **ブランチ保護**：main にブランチ保護（またはルールセット）を設定し、PR 必須・CI の成功必須・force push 禁止にする
- [ ] **残存ブランチ**：`docs/public-release-inventory.md` の「人への報告事項」を読み、不要なリモートブランチ（`ccr-841df633-5hhfa9`・`ccr-9e7b5503-1u3jk6`・`work/p-20261011-harness-rules`）を人が削除する。PR の ref（`refs/pull/*/head`）は GitHub 側で消せないため、残ることを承知のうえで公開する

## 可視性の切り替え（人が行う）

上の設定がすべて済んでから行う。

- [ ] **可視性の切り替え**：Settings > General の Danger Zone の Change visibility で Public にする（不可逆に近い外部操作のため人が行う。エージェントは行わない）

## 公開後の確認

- リポジトリのトップでライセンスが MIT と表示される
- CI が main で成功する
- Actions のシークレット・Variables が fork の PR から参照できない（fork の PR のワークフローで `vars.` の値が空になる）
