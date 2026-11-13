package com.gcmb.backend.finance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringExpenseJob {
    private static final Logger log=LoggerFactory.getLogger(RecurringExpenseJob.class);
    private final RecurringExpenseService recurring;

    public RecurringExpenseJob(RecurringExpenseService recurring) { this.recurring=recurring; }

    // JOB-03: tạo kỳ đến hạn và nhắc trước hạn, đúng hạn, quá hạn một lần mỗi ngày.
    @Scheduled(cron="0 5 2 * * *",zone="Asia/Ho_Chi_Minh")
    public void runDaily() {
        try { recurring.processDaily(); }
        catch(RuntimeException failure) { log.error("JOB-03: xử lý kỳ chi định kỳ thất bại; sẽ thử lại ở lịch chạy tiếp theo.",failure); }
    }
}
