package com.example.batch.sample.job07_partitioning;

import java.util.HashMap;
import java.util.Map;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * サンプル07【骨格】: ID範囲による Partitioner。
 *
 * <p>処理対象のID範囲を gridSize 個に分割し、各パーティションの ExecutionContext に
 * {@code minId} / {@code maxId} を設定する。Worker Step は {@code @StepScope} で
 * この値を受け取り、自分の担当範囲のみを処理する。</p>
 *
 * <p>実務では対象テーブルの MIN(id)/MAX(id) を起動時に取得して分割する。
 * 設計の考え方は docs/sample07-partitioning.md を参照。</p>
 */
public class SalesIdRangePartitioner implements Partitioner {

    private final long minId;
    private final long maxId;

    public SalesIdRangePartitioner(long minId, long maxId) {
        this.minId = minId;
        this.maxId = maxId;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        long total = maxId - minId + 1;
        long sizePerPartition = Math.max(1, (total + gridSize - 1) / gridSize);
        long start = minId;
        int index = 0;
        while (start <= maxId) {
            long end = Math.min(start + sizePerPartition - 1, maxId);
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", start);
            context.putLong("maxId", end);
            partitions.put("partition" + index, context);
            start = end + 1;
            index++;
        }
        return partitions;
    }
}
