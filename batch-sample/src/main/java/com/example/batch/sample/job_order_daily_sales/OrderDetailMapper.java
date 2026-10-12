package com.example.batch.sample.job_order_daily_sales;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 受注明細（app.order_detail）の Mapper。SQL は mapper/OrderDetailMapper.xml に定義する。
 */
@Mapper
public interface OrderDetailMapper {

    /**
     * 対象日の受注明細を削除する。
     *
     * @param targetDate 対象日（受注日）
     * @return 削除件数
     */
    int deleteByOrderDate(@Param("targetDate") LocalDate targetDate);

    /**
     * ワークの全行を受注明細へ登録する。
     *
     * @return 登録件数
     */
    int insertFromWork();
}
