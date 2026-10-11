---
id: D-20261011-spring-batch-adapt
---
# ゴール
導入済みの ai-harness を spring-batch-ai-coding-samples の開発工程に合わせる。詳細設計がレビュー済みのジョブを `/plan` に渡すと、implementation〜system-test を creator が既存 Skills に沿って作り、verifier がビルドと docs を根拠に判定できる状態にする。あわせて、既存の `sdd-pipeline` とハーネスの使い分けを docs に書く。

## 決定事項
人に聞いて確定した回答。以降のフェーズはこれを前提にする。1件1行。

| 決めたこと | なぜ |
|---|---|
| 要件定義〜設計レビュー（`requirements`・`basic-design`・`detail-design`・`review-design`）は従来どおり人と Skills で対話して進め、ハーネスには載せない | 設計は人の判断と `[Ask]` の確認が多く、着手後に質問しないハーネスの規律と合わないため |
| 詳細設計がレビュー済みのジョブを `/plan` に渡し、`implementation`〜`system-test` を creator が担い、verifier が判定する | 作業系の工程は受け入れ基準を真偽で書け、作成と検証を分ける効果が大きいため |
| `sdd-pipeline` は廃止せず残し、ハーネスと併存させる | 既存の利用者と手順を壊さないため（ai-harness の原則8） |
| 全タスクの受け入れ基準に `./mvnw verify -DskipITs` の成功を入れる。結合テスト（`*IT.java`）を成果物に含むタスクだけ `./mvnw verify` の成功も入れる | Docker が無い環境でも大半のタスクを判定でき、結合テストは要るタスクでだけ確かめれば足りるため |
| `vault/rules/` は docs を正本にした薄いルールにし、どの docs を読むかと要点だけを書く。docs の本文は書き写さない | このリポジトリは `docs/` を SSoT にしており、ルールとの二重管理を避けるため |
| `.claude/settings.json` の `permissions.allow` に `Bash(./mvnw *)`・`Bash(docker compose *)`・`Bash(docker info*)` を足す | 無人実行（`claude -p`）でビルドと DB 起動が拒否されないようにするため |
| creator が Skill の「ユーザーに確認・合意する」手順に当たった時は、`sdd-pipeline` の「委譲先Skillの記述を上書きする点」の表に従って読み替える。`[Ask]` に当たるものは独断せず blocked にする | creator はユーザーに質問できず、既にパイプライン用の読み替えが定義済みのため |
| `development-guide.md` §5.3 のモデル昇格（Sonnet→Opus）はハーネスでは使わない。代わりに attempt の再試行と、上限超過時の blocked で扱う | ハーネスは creator のモデルを固定し、再試行と人への引き継ぎを仕組みで持っているため |
| `docs/decisions.md` はこのリポジトリに無いので、最終フェーズで新規作成する | ハーネスの設計文書の運用（最終フェーズで決定事項を docs に残す）に合わせるため |

## 依存関係
- フェーズ1 → フェーズ2 の順。並行しない
- フェーズ1 はルールと設定だけ、フェーズ2 は人向けの docs だけを変える。どちらも単体で main にマージしてよい

---

## フェーズ1 ハーネスのルールと許可設定を整える

### ゴール文
spring-batch-ai-coding-samples で、詳細設計がレビュー済みのジョブを `/plan` に渡せば implementation〜system-test の工程をタスクに分けて実行・検証できるように、`vault/rules/` の各役割（common・planner・creator・verifier）に docs を参照する薄いルールを置き、`.claude/settings.json` の `permissions.allow` にビルドと Docker の許可を足す。

### 受け入れ基準の候補
- `vault/rules/common/` に、正本の docs（`docs/architecture.md`・`docs/coding-standards.md`・`docs/development-guide.md`・`CLAUDE.md` の禁止事項）を役割ごとに案内するルールがある（`bash scripts/rules.sh creator` の出力に含まれる）
- `vault/rules/planner/` に、ジョブ1本を implementation → unit-test → integration-test → review-code → system-test のタスクに分けて `after` で順につなぐことと、入力の詳細設計書がレビュー済みであることを計画の前提にすることが書かれている
- `vault/rules/creator/` に、各タスクで対応する `.claude/skills/<工程>/SKILL.md` に従うこと、ユーザー確認の手順は `sdd-pipeline` の読み替え表に従い `[Ask]` は blocked にすること、`development-guide.md` §5.4 の反則を禁じることが書かれている
- `vault/rules/verifier/` に、`./mvnw verify -DskipITs` を必ず実行し、結合テストを成果物に含むタスクでは `./mvnw verify` も実行して判定することが書かれている
- `.claude/settings.json` の `permissions.allow` に `Bash(./mvnw *)`・`Bash(docker compose *)`・`Bash(docker info*)` があり、JSON として読める（`python3 -c "import json;json.load(open('.claude/settings.json'))"`）
- `bash scripts/smoke.sh` の最終行が `fail=0` である
- 各ルールファイルが100行以内で、docs の本文を書き写していない（参照先のパスと節番号で示している）

### 決定済み
- ルールは `vault/templates/rule.md` の見出し（目的・ルール・確認方法）で書く。ファイル名は小文字ケバブケース
- docs の本文は書き写さない。「`docs/architecture.md` §6 リラン設計に従う」のように、パスと節番号で参照する
- `sdd-pipeline` の SKILL.md・既存の工程 Skills・docs は、このフェーズでは変更しない
- review-code のタスクは、creator が `/review-code` の手順でレビューし、[Must]/[Should] を自分で修正したうえで `./mvnw verify -DskipITs` を通す。[Ask] が残ったら blocked にする
- system-test のタスクで、外部依存などでローカル実施できないシナリオがある時は blocked にする
- モデル昇格（`development-guide.md` §5.3）は使わない。attempt の再試行と blocked で扱う
- `permissions.allow` は既存の項目を消さず、足すだけにする

### 依存
-

---

## フェーズ2 ハーネスの使い方を docs に書き、設計文書を片づける

### ゴール文
spring-batch-ai-coding-samples の利用者が、`sdd-pipeline` とハーネス（`/plan`・`/run`）のどちらを使えばよいか docs から分かるように、`docs/development-guide.md` にハーネスを使う工程の節を足し、`CLAUDE.md` の Skills の説明から辿れるようにする。最後に、決定事項を `docs/decisions.md` に残して設計文書 `vault/designs/D-20261011-spring-batch-adapt.md` を削除する。

### 受け入れ基準の候補
- `docs/development-guide.md` に、ハーネスで進める工程（詳細設計レビュー後の implementation〜system-test）、`/plan` に渡すゴール文の例、`sdd-pipeline` との使い分けを書いた節がある
- `docs/development-guide.md` §5 の検証ループの節に、ハーネスではモデル昇格の代わりに attempt の再試行と blocked で扱うことが追記されている
- `CLAUDE.md` の Skills の節（ハーネスのマーカーブロックの外）から、追加した `development-guide.md` の節へのリンクがある
- `CLAUDE.md` のハーネスのマーカーブロック（`<!-- ai-harness:begin v1 -->`〜`<!-- ai-harness:end -->`）は変更されていない（`git diff -- CLAUDE.md` で当該行に差分が無い）
- 決定事項のうち docs に無いものを `docs/decisions.md` に追記し、設計文書を削除する（`test ! -e vault/designs/D-20261011-spring-batch-adapt.md`）

### 決定済み
- `docs/decisions.md` はこのリポジトリに無いので新規作成する。1件1行で「決めたこと／なぜ／出典（D-20261011-spring-batch-adapt）」を書く
- 設計文書の削除は creator がタスクの成果物として行う（成果物に `vault/designs/` を宣言する。差分ゲートの禁止対象ではない）
- 設計文書の削除タスクは、設計文書を読む他のタスクすべての後に `after` で依存させて置く
- ハーネスのマーカーブロックは `install.sh --update` が書き換えるので、手で編集しない
- docs は日本語で書く（CLAUDE.md の規約サマリ）

### 依存
フェーズ1

---

## 今回やらないこと
- 実案件のジョブでの試走（`/plan` で1本通してみる）。このフェーズ群が終わった後、別途行う
- 設計工程（`basic-design`・`detail-design`・`review-design`）のハーネス化
- `sdd-pipeline` の廃止や、ハーネスを呼ぶように書き換えること
- CI（`.github/workflows/`）の変更
- creator・verifier のモデルの変更（既定の sonnet のまま）
