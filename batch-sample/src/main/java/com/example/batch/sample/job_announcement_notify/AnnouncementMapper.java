package com.example.batch.sample.job_announcement_notify;

import org.apache.ibatis.annotations.Mapper;

/**
 * お知らせ（app.announcement）のステータス遷移を扱う Mapper。
 * SQL は mapper/AnnouncementMapper.xml に定義する。
 */
@Mapper
public interface AnnouncementMapper {

    /** 未処理（status='0'）のお知らせを処理中（'1'）へ一括更新する。戻り値は更新件数。 */
    int pickupUnprocessed();

    /** 処理中のお知らせのうち、通知区域が郵便番号マスタに1件も展開できないものをエラー（'9'）へ一括更新する。戻り値は更新件数。 */
    int markRegionUnresolved();

    /** 処理中のお知らせを処理済み（'2'）へ一括更新する。戻り値は更新件数。 */
    int markProcessed();
}
