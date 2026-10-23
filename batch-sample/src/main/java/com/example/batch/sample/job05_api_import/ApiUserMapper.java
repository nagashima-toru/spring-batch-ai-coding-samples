package com.example.batch.sample.job05_api_import;

import org.apache.ibatis.annotations.Mapper;

/**
 * APIユーザーテーブル（app.api_user）の Mapper。SQL は mapper/ApiUserMapper.xml に定義する。
 */
@Mapper
public interface ApiUserMapper {

    /** ユーザーを登録する（既存キーは更新＝UPSERT。再実行で冪等）。 */
    void upsert(ApiUser user);

    /** 件数を取得する。 */
    long count();
}
