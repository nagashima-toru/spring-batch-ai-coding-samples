package com.example.batch.sample.common;

/**
 * 顧客エンティティ（app.customer）。
 *
 * <p>エンティティは不変 record で定義する。MyBatis の結果マッピングは
 * {@code arg-name-based-constructor-auto-mapping} によりコンストラクタ経由で行われる。</p>
 *
 * @param customerCode 顧客コード
 * @param name         氏名
 * @param email        メールアドレス
 */
public record Customer(String customerCode, String name, String email) {
}
