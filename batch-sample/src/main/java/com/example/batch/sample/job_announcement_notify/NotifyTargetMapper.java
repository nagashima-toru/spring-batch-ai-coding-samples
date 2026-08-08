package com.example.batch.sample.job_announcement_notify;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.cursor.Cursor;

/**
 * 通知対象ユーザー抽出（Step3 Reader）用の Mapper。SQL は mapper/NotifyTargetMapper.xml に定義する。
 *
 * <p>{@link #selectNotifyTargets()} はカーソル読取専用のクエリのため
 * {@code MyBatisCursorItemReaderBuilder} から {@code queryId} 文字列で直接参照する
 * （このメソッド自体は直接呼び出されない。クエリの所在を明示するための宣言）。</p>
 */
@Mapper
public interface NotifyTargetMapper {

    /** 処理中のお知らせの通知対象ユーザーをカーソルで抽出する（本人条件・家族条件を UNION）。 */
    Cursor<NotifyCandidate> selectNotifyTargets();
}
