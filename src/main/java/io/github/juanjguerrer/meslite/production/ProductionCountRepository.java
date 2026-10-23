package io.github.juanjguerrer.meslite.production;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    Optional<ProductionCount> findByEventId(UUID eventId);
}
