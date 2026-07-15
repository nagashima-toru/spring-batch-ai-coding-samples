package com.example.batch.sample.common;

import org.apache.ibatis.annotations.Mapper;

/**
 * ワーク・サマリテーブルの Mapper（サンプル06）。SQL は mapper/WorkMapper.xml に定義する。
 */
@Mapper
public interface WorkMapper {

    /** ワークレコードを登録する（既存キーは更新＝UPSERT。リスタート再実行で冪等）。 */
    void upsert(WorkRecord record);

    /** ワークテーブルの件数を取得する。 */
    long countWork();

    /** サマリテーブルを空にする。 */
    void truncateSummary();

    /** ワークテーブルからサマリを作成する。 */
    int insertSummaryFromWork();

    /** サマリテーブルの件数を取得する。 */
    long countSummary();
}
