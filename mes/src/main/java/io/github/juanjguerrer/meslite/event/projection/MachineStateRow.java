package io.github.juanjguerrer.meslite.event.projection;

import java.time.Instant;

/**
 * Row of the native "current state per machine" query.
 * Getter names must match the column aliases in the SELECT.
 */
public interface MachineStateRow {
    Long getMachineId();

    String getMachineCode();

    String getState();

    Instant getSince();
}
