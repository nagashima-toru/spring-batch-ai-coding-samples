package com.example.batch.sample.job05_api_import;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;

/**
 * サンプル05: 入力元による条件分岐 Decider。
 *
 * <p>任意パラメータ {@code inputFile} が指定されていれば {@code FILE}（保存済み JSON から取り込む。
 * 件数取得 Step は実行しない）、未指定・空白のみなら {@code API}（件数取得 Step の後に API から取り込む）を返す。</p>
 */
public class InputSourceDecider implements JobExecutionDecider {

    /** API から取り込む。 */
    public static final String API = "API";

    /** JSON ファイルから取り込む。 */
    public static final String FILE = "FILE";

    private static final Logger log = LoggerFactory.getLogger(InputSourceDecider.class);

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        String status = isFileInput(jobExecution.getJobParameters().getString("inputFile")) ? FILE : API;
        log.info("条件分岐: 入力元 → {}", status);
        return new FlowExecutionStatus(status);
    }

    /**
     * {@code inputFile} の値が JSON ファイルからの取込を指すかを返す。
     *
     * <p>Flow の分岐（本 Decider）と Reader の切り替えで同じ判定を使い、両者が食い違わないようにする。</p>
     *
     * @param inputFile ジョブパラメータ {@code inputFile} の値（null 可）
     * @return 未指定・空白のみでなければ true
     */
    public static boolean isFileInput(String inputFile) {
        return inputFile != null && !inputFile.isBlank();
    }
}
