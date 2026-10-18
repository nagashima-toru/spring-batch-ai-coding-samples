#!/usr/bin/env bash
# システムテスト共通の環境変数（source して使う）。実行時ファイルはリポジトリ外に置く。
# Java 25 が必要（CLAUDE.md）。環境に別の JAVA_HOME があっても上書きする。別の場所にある時は ST_JAVA_HOME で指定する
export JAVA_HOME="${ST_JAVA_HOME:-/opt/jdk-25}"
export PATH="$JAVA_HOME/bin:$PATH"
export ST_BASE=/tmp/claude/P-20261018-order001-restart-redo-T-05
export ST_IN="$ST_BASE/in"
export ST_ERR="$ST_BASE/error"
export ST_WORK="$ST_BASE/work"
export PGPASSWORD=batch_admin
export PSQL="psql -h localhost -U batch_admin -d batchdb -v ON_ERROR_STOP=1"
mkdir -p "$ST_IN" "$ST_ERR" "$ST_WORK"
