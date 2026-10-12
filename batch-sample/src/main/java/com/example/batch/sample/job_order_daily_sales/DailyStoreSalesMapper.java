package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 日次売上集計（app.daily_store_sales）の Mapper。SQL は mapper/DailyStoreSalesMapper.xml に定義する。
 */
@Mapper
public interface DailyStoreSalesMapper {

    /**
     * 対象日の日次売上集計を削除する。
     *
     * @param targetDate 対象日（受注日）
     * @return 削除件数
     */
    int deleteBySalesDate(@Param("targetDate") LocalDate targetDate);

    /**
     * 対象日の受注明細を店舗ごとに集計して登録する。
     *
     * @param targetDate 対象日（受注日）
     * @return 登録件数
     */
    int insertAggregate(@Param("targetDate") LocalDate targetDate);
}
