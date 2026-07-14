package com.example.batch.core.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import com.example.batch.core.http.BatchHttpClient;

/**
 * 外部 API コール Tasklet の抽象基底クラス。
 *
 * <p><b>用途:</b> 「API を1回呼び出して結果を処理する」タイプの Step
 * （処理開始通知・トークン取得・結果サマリ送信など）のテンプレート。
 * HTTP 呼出は必ず {@link BatchHttpClient}（タイムアウト・リトライ・ログ内包）経由となる。</p>
 *
 * <p><b>使い方:</b> 継承して {@link #callApi} と {@link #handleResponse} を実装する。</p>
 * <pre>{@code
 * public class NotifyStartTasklet extends AbstractApiCallTasklet<NotifyResult> {
 *     public NotifyStartTasklet(BatchHttpClient httpClient) { super(httpClient); }
 *
 *     protected NotifyResult callApi(BatchHttpClient client) {
 *         return client.post("/batch/start", new StartRequest(...), NotifyResult.class, Map.of());
 *     }
 *
 *     protected void handleResponse(NotifyResult response, StepContribution contribution) {
 *         // 結果検証・ExecutionContext への保存など
 *     }
 * }
 * }</pre>
 *
 * <p><b>拡張ポイント:</b> ページングしながら大量データを取得する場合は本クラスではなく
 * Chunk Step ＋ {@code RestApiPagingItemReader} を使用する（方式設計 §3.1）。</p>
 *
 * @param <R> API レスポンスの型
 */
public abstract class AbstractApiCallTasklet<R> implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AbstractApiCallTasklet.class);

    private final BatchHttpClient httpClient;

    protected AbstractApiCallTasklet(BatchHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public final RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("外部APIコールStepを開始します: tasklet={}", getClass().getSimpleName());
        R response = callApi(httpClient);
        handleResponse(response, contribution);
        log.info("外部APIコールStepが完了しました: tasklet={}", getClass().getSimpleName());
        return RepeatStatus.FINISHED;
    }

    /**
     * API を呼び出す。HTTP エラーの例外変換・リトライは {@link BatchHttpClient} が行う。
     *
     * @param client HTTP Client 基盤
     * @return API レスポンス
     */
    protected abstract R callApi(BatchHttpClient client);

    /**
     * レスポンスを処理する（検証・保存・ExecutionContext への引き渡し等）。
     *
     * @param response     API レスポンス
     * @param contribution Step への貢献（件数カウント等に使用可）
     */
    protected abstract void handleResponse(R response, StepContribution contribution);
}
