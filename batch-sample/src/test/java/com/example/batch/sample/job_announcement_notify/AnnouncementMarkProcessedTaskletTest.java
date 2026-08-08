package com.example.batch.sample.job_announcement_notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class AnnouncementMarkProcessedTaskletTest {

    private final AnnouncementMapper announcementMapper = mock(AnnouncementMapper.class);

    private final AnnouncementMarkProcessedTasklet tasklet = new AnnouncementMarkProcessedTasklet(announcementMapper);

    @Test
    void 処理中のお知らせを処理済みへ一括更新して正常終了する() throws Exception {
        when(announcementMapper.markProcessed()).thenReturn(2);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(announcementMapper, times(1)).markProcessed();
    }
}
