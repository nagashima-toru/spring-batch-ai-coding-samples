---
id: P-20261024-move-single-job-mappers
status: approved
---
# ゴール
GitHub Issue #64 に対応する。`batch-sample/src/main/java/com/example/batch/sample/common/` にある、1ジョブからしか使われないエンティティと Mapper を、そのジョブのパッケージ（`jobNN_`）へ移す。対象は `Customer`・`CustomerMapper`（job01）、`Product`・`ProductMapper`（job02）、`ApiUser`・`ApiUserMapper`（job05）、`WorkRecord`・`WorkMapper`（job06）。`SalesTransaction`・`SalesMapper`（job03・job04 で共有）と `SampleProperties` は `common/` に残す。あわせて `.claude/skills/implementation/SKILL.md` の Mapper IF の置き場所に、判断基準（単一ジョブ専用は同パッケージ、複数ジョブで共有するものは `common/`）を明記する。

## 分割方針
- 既存コードで確認した事実（planner が grep で確認済み）
  - 利用ジョブは Issue の表のとおり。`Customer`・`CustomerMapper` は job01、`Product`・`ProductMapper` は job02、`ApiUser`・`ApiUserMapper` は job05、`WorkRecord`・`WorkMapper` は job06 のパッケージ（main・test）からだけ参照される。`SalesMapper`・`SalesTransaction` は job03・job04 から、`SampleProperties` は複数ジョブから参照される
  - Mapper の登録は各 IF の `@Mapper` を mybatis-spring-boot の自動スキャン（`@SpringBootApplication` の基底パッケージ `com.example.batch.sample` 配下）で拾う方式で、`@MapperScan` は無い。XML は `application.yml` の `mybatis.mapper-locations: classpath:mapper/*.xml` で読むので、XML の置き場所はパッケージに依存しない。案件ジョブ（`job_order_daily_sales` など）も Mapper IF を自パッケージに置き、XML を `mapper/` に置いている
  - パッケージ名が入っているのは、Java の `package` 宣言・`import`、Mapper XML の `namespace`・`parameterType`、JobConfig の `statementId(...)` の文字列。`statementId` と `namespace` の食い違いは結合テスト（Testcontainers）でしか分からない
  - ArchUnit などパッケージ構成を検査するテストは無い。Checkstyle（`config/checkstyle/checkstyle.xml`）はパッケージ名の形式と import（未使用・冗長）だけを見る。JaCoCo・SpotBugs の除外設定に対象クラスは無い
  - docs（`batch-sample/docs/`・`docs/architecture.md`・`README.md`・`docs/projects/`）に旧パッケージ名（`sample.common.Customer` など）の参照は無い。`docs/architecture.md` §2.1 の `common/` のコメントは既に「複数ジョブで共有するエンティティ・Mapper」で、移動後の構成と合う
- ジョブ単位に4タスク（T-01 job01・T-02 job02・T-03 job05・T-04 job06）に切る。変えるファイルはジョブごとに重ならない（Mapper XML もジョブごとに1ファイル）ので、`after` は付けず並行させる
- `.claude/skills/implementation/SKILL.md` の更新は T-05 に分ける（同じファイルを2タスクが変えないため。コードに依存しないので並行可）
- ジョブのタスクの基準のビルドは `./mvnw clean verify`（結合テスト込み）にする。Mapper の登録の壊れは結合テストでしか分からず、成果物に `*IT.java` を含むため verifier ルール（`vault/rules/verifier/build-verification.md`）でも実行される
- T-01〜T-04 の成果物にコード（`.java`・`.xml`）があるので、全タスクの後に計画単位のレビュータスク T-06 を置く

## タスク表（状態の正本）
| id | status | attempt | after | title | question |
|---|---|---|---|---|---|
| T-01 | todo | 0 | - | job01 の Customer・CustomerMapper を job01_csv_import へ移す | |
| T-02 | todo | 0 | - | job02 の Product・ProductMapper を job02_fixedlength_import へ移す | |
| T-03 | todo | 0 | - | job05 の ApiUser・ApiUserMapper を job05_api_import へ移す | |
| T-04 | todo | 0 | - | job06 の WorkRecord・WorkMapper を job06_flow_job へ移す | |
| T-05 | todo | 0 | - | implementation SKILL.md に Mapper IF の置き場所の判断基準を明記する | |
| T-06 | todo | 0 | T-01,T-02,T-03,T-04,T-05 | 計画の差分が `/code-review` を通過する | |

## 計画の受け入れ基準
- 各タスクに成果物と受け入れ基準が1つずつある
- 依存に循環がない
- 1タスクが1コンテキストで終わる粒度である
