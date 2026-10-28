package io.github.juanjguerrer.meslite.production.projection;

import java.time.Instant;

/**
 * Row of the native "production per hour" query.
 * Getter names must match the column aliases in the SELECT.
 */
public interface HourlyProductionRow {
    Instant getBucketStart();

    Long getGoodCount();

    Long getScrapCount();
}
