package com.example.batch.sample.common;

import org.apache.ibatis.annotations.Mapper;

/**
 * 売上明細・集計テーブルの Mapper。SQL は mapper/SalesMapper.xml に定義する。
 */
@Mapper
public interface SalesMapper {

    /** 売上明細を登録する（テストデータ投入・サンプル用）。 */
    void insertTransaction(SalesTransaction transaction);

    /** 売上明細の件数を取得する。 */
    long countTransactions();

    /** 集計テーブルを空にする（洗い替えの前段。同一トランザクションで insert とセットで呼ぶ）。 */
    void truncateSummary();

    /** 売上明細から顧客別集計を投入する（集計は SQL で行い Java 側でループしない）。 */
    int insertSummaryFromTransactions();

    /** 集計テーブルの件数を取得する。 */
    long countSummary();
}
