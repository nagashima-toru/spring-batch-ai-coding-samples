package com.example.batch.sample.job_point_expiry;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ポイント付与（app.point_grant）の失効処理を扱う Mapper。
 * SQL は mapper/PointGrantMapper.xml に定義する。
 */
@Mapper
public interface PointGrantMapper {

    /**
     * 有効期限が {@code targetDate} より前の「有効」な付与を「失効」へ更新し、
     * 更新した行をそのまま失効履歴（app.point_expiry_history）へ登録する。
     * 失効更新と履歴登録はデータ変更 CTE により単一 SQL 文で原子的に実行される。
     *
     * @param targetDate 失効判定の対象日（ジョブパラメータ由来。実行時刻からは導出しない）
     * @return 失効させた付与件数（外側 INSERT の行数）
     */
    int expireAndRecordHistory(@Param("targetDate") LocalDate targetDate);
}
