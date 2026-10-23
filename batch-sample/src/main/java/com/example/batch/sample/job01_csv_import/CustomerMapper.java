package com.example.batch.sample.job01_csv_import;

import org.apache.ibatis.annotations.Mapper;

/**
 * 顧客テーブル（app.customer）の Mapper。SQL は mapper/CustomerMapper.xml に定義する。
 */
@Mapper
public interface CustomerMapper {

    /** 顧客を登録する（既存キーは更新＝UPSERT。再実行で冪等）。 */
    void upsert(Customer customer);

    /** 件数を取得する。 */
    long count();
}
