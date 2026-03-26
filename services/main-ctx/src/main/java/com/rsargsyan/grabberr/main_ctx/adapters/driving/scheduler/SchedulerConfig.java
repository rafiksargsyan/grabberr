package com.rsargsyan.grabberr.main_ctx.adapters.driving.scheduler;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class SchedulerConfig {

  @Value("${grabberr.metadata-poll-interval:PT30S}")
  private Duration metadataPollInterval;

  @Value("${grabberr.download-poll-interval:PT60S}")
  private Duration downloadPollInterval;

  @Bean
  public RecurringTask<Void> metadataPollingTask(MetadataPollingJob job) {
    return Tasks.recurring("metadata-polling", FixedDelay.of(metadataPollInterval))
        .execute((instance, ctx) -> job.poll());
  }

  @Bean
  public RecurringTask<Void> fileDownloadPollingTask(FileDownloadPollingJob job) {
    return Tasks.recurring("file-download-polling", FixedDelay.of(downloadPollInterval))
        .execute((instance, ctx) -> job.poll());
  }
}
