package com.example.batch.sample.job_order_daily_sales;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.ResultHandler;

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

    /**
     * ワークの全行のキーを1行ずつ {@code handler} へ渡す（全件をリストに載せない）。
     *
     * @param handler 1行ごとの受け取り先
     */
    void selectKeys(ResultHandler<OrderKey> handler);
}
