#!/usr/bin/env bash
# ジョブ起動（ローカル）。使い方: run_job.sh <ログファイル> [ジョブパラメータ/追加引数...]
# 終了コードはプロセスの終了コードをそのまま返し、標準エラーに "exit=<n>" も出す。
# 既定ディレクトリは env.sh の入出力ディレクトリ（リポジトリ外）に向ける。
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
source "$here/env.sh"
log="$1"; shift
jar=$(ls "$here"/../../../../../batch-sample/target/batch-sample-*.jar | grep -v original | head -1)
java -jar "$jar" --spring.profiles.active=local --spring.batch.job.name=orderDailySalesJob \
  --sample.order-daily-sales.input-dir="$ST_IN" --sample.order-daily-sales.error-dir="$ST_ERR" \
  "$@" > "$log" 2>&1
rc=$?
echo "exit=$rc" >&2
exit $rc
