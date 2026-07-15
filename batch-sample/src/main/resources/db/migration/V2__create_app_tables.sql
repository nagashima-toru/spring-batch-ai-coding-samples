-- 業務テーブル（app スキーマ）
-- サンプルジョブ 01〜09 が使用する。案件ではこのファイルを差し替える。

-- サンプル01: CSV取込先の顧客テーブル
CREATE TABLE app.customer (
    customer_code VARCHAR(10)  NOT NULL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- サンプル02: 固定長取込先の商品テーブル
CREATE TABLE app.product (
    product_code VARCHAR(8)    NOT NULL PRIMARY KEY,
    product_name VARCHAR(100)  NOT NULL,
    price        NUMERIC(12)   NOT NULL,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- サンプル03(抽出元)・04(集計元): 売上明細テーブル
CREATE TABLE app.sales_transaction (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_code VARCHAR(10)  NOT NULL,
    amount        NUMERIC(12)  NOT NULL,
    sold_at       DATE         NOT NULL
);

-- サンプル04: 集計先テーブル（truncate-insert 洗い替え）
CREATE TABLE app.sales_summary (
    customer_code VARCHAR(10)  NOT NULL PRIMARY KEY,
    total_amount  NUMERIC(14)  NOT NULL,
    sales_count   BIGINT       NOT NULL,
    aggregated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- サンプル05: API取込先のユーザーテーブル
CREATE TABLE app.api_user (
    user_id    VARCHAR(20)  NOT NULL PRIMARY KEY,
    user_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- サンプル06: 複合ジョブのワークテーブルとサマリテーブル
CREATE TABLE app.work_import (
    code       VARCHAR(10) NOT NULL PRIMARY KEY,
    amount     NUMERIC(12) NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app.work_summary (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    total_count  BIGINT      NOT NULL,
    total_amount NUMERIC(14) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
