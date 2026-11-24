package com.fincore.settlement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "fincore.settlement.jobs.enabled", havingValue = "true")
public class SettlementJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementJobScheduler.class);

    private final SettlementJobs jobs;
    private final Path inbox;

    public SettlementJobScheduler(SettlementJobs jobs,
                                  @Value("${fincore.settlement.reports.inbox}") String inbox) {
        this.jobs = jobs;
        this.inbox = Path.of(inbox);
    }

    @Scheduled(cron = "${fincore.settlement.jobs.batch-cron}", zone = "UTC")
    public void dailyBatches() {
        jobs.createDueBatches(LocalDate.now(ZoneOffset.UTC));
    }

    @Scheduled(fixedDelayString = "${fincore.settlement.jobs.reconciliation-poll-ms}")
    public void reconciliationInbox() {
        try {
            Files.createDirectories(inbox);
            jobs.importInbox(inbox);
        } catch (IOException e) {
            log.warn("Processor report inbox {} unavailable: {}", inbox, e.getMessage());
        }
    }
}
