package io.github.juanjguerrer.meslite.production;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.juanjguerrer.meslite.production.projection.HourlyProductionRow;

public interface ProductionCountRepository extends JpaRepository<ProductionCount, Long>,
    JpaSpecificationExecutor<ProductionCount> {
        @Query("""
            SELECT COALESCE(SUM(c.goodCount), 0) AS good, COALESCE(SUM(c.scrapCount), 0) AS scrap
            FROM ProductionCount c
            WHERE c.machine.id = :machineId
            AND c.intervalEnd > :from
            AND c.intervalEnd <= :to
            """)
        CountTotals sumByMachineAndRange(@Param("machineId") Long machineId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    // A count belongs to the hour in which it ends: subtracting one microsecond before
    // truncating sends an interval_end exactly on the hour to the previous bucket.
    // Hours without counts are kept (zeros) by joining against generate_series.
    @Query(value = """
        WITH buckets AS (
            SELECT generate_series(
                date_trunc('hour', CAST(:from AS timestamptz), CAST(:zone AS text)),
                date_trunc('hour', CAST(:to AS timestamptz) - INTERVAL '1 microsecond', CAST(:zone AS text)),
                INTERVAL '1 hour'
            ) AS bucket_start
        ),
        sums AS (
            SELECT
                date_trunc('hour', interval_end - INTERVAL '1 microsecond', CAST(:zone AS text)) AS bucket_start,
                SUM(good_count) AS good,
                SUM(scrap_count) AS scrap
            FROM production_counts
            WHERE machine_id = :machineId
            AND interval_end > CAST(:from AS timestamptz)
            AND interval_end <= CAST(:to AS timestamptz)
            GROUP BY 1
        )
        SELECT
            b.bucket_start AS "bucketStart",
            CAST(COALESCE(s.good, 0) AS BIGINT) AS "goodCount",
            CAST(COALESCE(s.scrap, 0) AS BIGINT) AS "scrapCount"
        FROM buckets b
        LEFT JOIN sums s ON s.bucket_start = b.bucket_start
        ORDER BY b.bucket_start
        """, nativeQuery = true)
    List<HourlyProductionRow> sumHourlyByMachineAndRange(@Param("machineId") Long machineId,
                                                         @Param("from") Instant from,
                                                         @Param("to") Instant to,
                                                         @Param("zone") String zone);

    Optional<ProductionCount> findByEventId(UUID eventId);
    boolean existsByMachineIdAndSequence(Long machineId, Long sequence);
}
