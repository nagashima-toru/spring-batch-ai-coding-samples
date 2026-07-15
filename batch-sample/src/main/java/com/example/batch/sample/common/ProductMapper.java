package com.example.batch.sample.common;

import org.apache.ibatis.annotations.Mapper;

/**
 * 商品テーブル（app.product）の Mapper。SQL は mapper/ProductMapper.xml に定義する。
 */
@Mapper
public interface ProductMapper {

    /** 商品を登録する（既存キーは更新＝UPSERT。再実行で冪等）。 */
    void upsert(Product product);

    /** 件数を取得する。 */
    long count();
}
