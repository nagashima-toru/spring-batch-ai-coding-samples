#!/usr/bin/env bash
# CSV から期待する店舗別集計を独立に計算する（ジョブの SQL とは別実装）。
# 使い方: expected.sh <CSV>   出力: store_code|order_count|line_count|total_quantity|total_amount（店舗順）
# 不正行を含まない（全行が正常な）CSV を前提とする。
set -euo pipefail
tail -n +2 "$1" | awk -F, '{
  lines[$4]++; qty[$4] += $6; amt[$4] += $6 * $7
  key = $4 SUBSEP $1
  if (!(key in seen)) { seen[key] = 1; ord[$4]++ }
} END { for (s in lines) printf "%s|%d|%d|%d|%d\n", s, ord[s], lines[s], qty[s], amt[s] }' | LC_ALL=C sort
