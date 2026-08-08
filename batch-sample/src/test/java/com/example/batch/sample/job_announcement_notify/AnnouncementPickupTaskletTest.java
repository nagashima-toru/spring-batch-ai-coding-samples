package com.example.batch.sample.job_announcement_notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class AnnouncementPickupTaskletTest {

    private final AnnouncementMapper announcementMapper = mock(AnnouncementMapper.class);

    private final AnnouncementPickupTasklet tasklet = new AnnouncementPickupTasklet(announcementMapper);

    @Test
    void 未処理のお知らせがある場合は一括更新して正常終了する() throws Exception {
        when(announcementMapper.pickupUnprocessed()).thenReturn(3);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(announcementMapper, times(1)).pickupUnprocessed();
    }

    @Test
    void 未処理のお知らせが0件でも異常にせず正常終了する() throws Exception {
        when(announcementMapper.pickupUnprocessed()).thenReturn(0);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }
}
