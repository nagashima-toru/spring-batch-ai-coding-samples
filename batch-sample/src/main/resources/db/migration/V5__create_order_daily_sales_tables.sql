-- 受注CSV取込・日次売上集計バッチ（ORDER-001）業務テーブル
-- docs/projects/order-daily-sales/basic-design.md §4 準拠

-- 受注明細ワーク（取込中の一時置き場。Step 3 で毎回空にする）
-- order_detail と同じ列構成に、入力ファイル上の行番号 line_seq を加える（リスタート時の重複検出の復元用）
CREATE TABLE app.order_detail_work (
    order_no   VARCHAR(20) NOT NULL,
    line_no    INTEGER     NOT NULL,
    order_date DATE        NOT NULL,
    store_code VARCHAR(10) NOT NULL,
    item_code  VARCHAR(20) NOT NULL,
    quantity   INTEGER     NOT NULL,
    unit_price INTEGER     NOT NULL,
    amount     BIGINT      NOT NULL,
    line_seq   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_no, line_no)
);

-- 受注明細（受注CSVの1行=1レコード）
-- 店舗・商品マスタは本案件スコープ外のため FK なし
CREATE TABLE app.order_detail (
    order_no   VARCHAR(20) NOT NULL,
    line_no    INTEGER     NOT NULL,
    order_date DATE        NOT NULL,
    store_code VARCHAR(10) NOT NULL,
    item_code  VARCHAR(20) NOT NULL,
    quantity   INTEGER     NOT NULL,
    unit_price INTEGER     NOT NULL,
    amount     BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_no, line_no)
);

-- 対象日の削除と集計用
CREATE INDEX idx_order_detail_order_date_store_code ON app.order_detail (order_date, store_code);

-- 日次売上集計（受注日×店舗）
CREATE TABLE app.daily_store_sales (
    sales_date     DATE        NOT NULL,
    store_code     VARCHAR(10) NOT NULL,
    order_count    INTEGER     NOT NULL,
    line_count     INTEGER     NOT NULL,
    total_quantity BIGINT      NOT NULL,
    total_amount   BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sales_date, store_code)
);
