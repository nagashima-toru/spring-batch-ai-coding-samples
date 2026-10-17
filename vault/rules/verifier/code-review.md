# verifier ルール：/code-review の行を review-standards の区分に合わせて判定する

## 目的
受け入れ基準の `/code-review` の行を、規約レビューの区分（Must/Should/Ask）と同じ物差しで判定する。

## ルール
- このルールは、受け入れ基準の行がこのファイル（`vault/rules/verifier/code-review.md`）を参照している時だけ判定に使う
- 範囲・呼び方・接頭辞などの判定の本体は `.claude/agents/verifier.md` の「コードレビューを参照する基準の判定」節に従い、ここには写さない
- 区分の定義は `.claude/skills/review-standards/SKILL.md` の区分の表の下の [Must]・[Should]・[Ask] に従う
- [Must] 相当の指摘（差分で変えた行へのクラッシュ・データ破損・誤った結果）が残っていれば、その行を `ok: false` にする
- [Ask] 相当の指摘（直すのに設計・仕様の改訂が要る）は、その行を `ok: true` のまま、`人の判断が必要: ` で始まる要素を reasons に書く
- [Should] 相当の指摘（性能・保守性・テスト不足などの致命的でないもの）は FAIL の根拠にしない
- 差分の外（既存コード）への指摘は、[Must] 相当でも FAIL の根拠にせず、`対象外（既存コード）: ` で始まる要素を note と reasons に書く
- 規約（`.claude/skills/review-standards/references/code-review-checklist.md`）の指摘は、受け入れ基準の行がチェックリストを参照している時だけ判定に使い、`/code-review` の行の判定には混ぜない
- review-standards のタスクでも、`/code-review` の行の範囲はそのタスクの差分（計画ブランチとの分岐点から）で、review-standards の手順4の範囲（ジョブで変えた差分）に広げない

## 確認方法
- verdict の `/code-review` の行の note に、実行した範囲と区分ごとの件数が書かれている
