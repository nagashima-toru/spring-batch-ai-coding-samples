#!/usr/bin/env bash
# 受注CSV生成（ダミーデータ。個人情報なし）
# 使い方: gen_orders.sh <対象日 yyyy-MM-dd> <明細件数> <出力ファイル> [店舗数=10]
# - 受注は 1〜5 明細を持つ。店舗は偏りを持たせる（店舗番号が小さいほど件数が多い）
# - 受注番号は日付を含み、日付ごとに別の値になる（他の日と一意制約が衝突しない）
set -euo pipefail
date_="$1"; rows="$2"; out="$3"; stores="${4:-10}"
awk -v d="$date_" -v rows="$rows" -v stores="$stores" 'BEGIN {
  srand(20261012)
  ymd = d; gsub("-", "", ymd)
  print "order_no,line_no,order_date,store_code,item_code,quantity,unit_price"
  n = 0; o = 0
  while (n < rows) {
    o++
    lines = 1 + int(rand() * 5)
    s = 1 + int(stores * rand() * rand())
    for (l = 1; l <= lines && n < rows; l++) {
      n++
      printf "O%s%07d,%d,%s,S%03d,I%04d,%d,%d\n", ymd, o, l, d, s, 1 + int(rand() * 500), 1 + int(rand() * 10), 100 * (1 + int(rand() * 50))
    }
  }
}' > "$out"
