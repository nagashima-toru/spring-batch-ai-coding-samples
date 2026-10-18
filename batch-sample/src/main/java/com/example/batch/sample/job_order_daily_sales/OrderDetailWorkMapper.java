package com.example.batch.sample.job_order_daily_sales;

import org.apache.ibatis.annotations.Mapper;

/**
 * 受注明細ワーク（app.order_detail_work）の Mapper。SQL は mapper/OrderDetailWorkMapper.xml に定義する。
 */
@Mapper
public interface OrderDetailWorkMapper {

    /** ワークを空にする（Tasklet の Tx 内で実行する）。 */
    void truncate();

    /**
     * ワークへ1行登録する（Writer の statementId から呼ばれる）。
     *
     * @param work 登録する行
     * @return 登録件数
     */
    int insert(OrderDetailWork work);
}
