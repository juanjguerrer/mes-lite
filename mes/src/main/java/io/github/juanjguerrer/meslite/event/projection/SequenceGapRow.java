package io.github.juanjguerrer.meslite.event.projection;

public interface SequenceGapRow {
    Long getFromSequence();

    Long getToSequence();

    Long getMissing();
}
