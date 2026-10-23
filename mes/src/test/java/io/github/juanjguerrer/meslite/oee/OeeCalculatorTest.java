package io.github.juanjguerrer.meslite.oee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.juanjguerrer.meslite.event.MachineState;
import io.github.juanjguerrer.meslite.event.StateDurations;
import io.github.juanjguerrer.meslite.oee.OeeCalculator.OeeResult;

class OeeCalculatorTest {

    private static final double TOLERANCE = 0.0001;
    private static final int PRESS_CYCLE_MS = 12_000;

    @Test
    void pressExampleFromTheGuide() {
        // 8 h shift: 30 min planned stop, 360 min running, 90 min of losses.
        StateDurations.Result durations = durations(0,
            MachineState.RUNNING, 360,
            MachineState.PLANNED_STOP, 30,
            MachineState.DOWN, 50,
            MachineState.SETUP, 25,
            MachineState.IDLE, 15);

        OeeResult result = OeeCalculator.calculate(durations, 1470, 30, PRESS_CYCLE_MS);

        assertThat(result.plannedMs()).isEqualTo(minutes(450));
        assertThat(result.runningMs()).isEqualTo(minutes(360));
        assertThat(result.availability()).isCloseTo(0.8, within(TOLERANCE));
        assertThat(result.performance()).isCloseTo(0.8333, within(TOLERANCE));
        assertThat(result.quality()).isCloseTo(0.98, within(TOLERANCE));
        assertThat(result.oee()).isCloseTo(0.6533, within(TOLERANCE));
        assertThat(result.dataCoverage()).isEqualTo(1.0);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void scrapCountsForPerformanceButOnlyGoodPiecesForQuality() {
        // 100 pieces at 12 s = 20 min of ideal work in 20 min running: 100% performance even with scrap.
        StateDurations.Result durations = durations(0, MachineState.RUNNING, 20);

        OeeResult result = OeeCalculator.calculate(durations, 90, 10, PRESS_CYCLE_MS);

        assertThat(result.performance()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.quality()).isCloseTo(0.9, within(TOLERANCE));
        assertThat(result.oee()).isCloseTo(0.9, within(TOLERANCE));
    }

    @Test
    void lossStatesReduceAvailabilityButPlannedStopDoesNot() {
        StateDurations.Result withLoss = durations(0, MachineState.RUNNING, 60, MachineState.SETUP, 60);
        StateDurations.Result withPlannedStop = durations(0, MachineState.RUNNING, 60, MachineState.PLANNED_STOP, 60);

        assertThat(OeeCalculator.calculate(withLoss, 0, 0, PRESS_CYCLE_MS).availability()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(OeeCalculator.calculate(withPlannedStop, 0, 0, PRESS_CYCLE_MS).availability()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void unknownTimeIsExcludedFromPlannedTimeAndLowersCoverage() {
        // 480 min range, the first 120 min without data.
        StateDurations.Result durations = durations(120, MachineState.RUNNING, 300, MachineState.DOWN, 60);

        OeeResult result = OeeCalculator.calculate(durations, 1500, 0, PRESS_CYCLE_MS);

        assertThat(result.plannedMs()).isEqualTo(minutes(360));
        assertThat(result.unknownMs()).isEqualTo(minutes(120));
        assertThat(result.availability()).isCloseTo(0.8333, within(TOLERANCE));
        assertThat(result.dataCoverage()).isCloseTo(0.75, within(TOLERANCE));
    }

    @Test
    void machineWithoutIdealCycleHasNoPerformanceNorOee() {
        StateDurations.Result durations = durations(0, MachineState.RUNNING, 360, MachineState.DOWN, 90);

        OeeResult result = OeeCalculator.calculate(durations, 1470, 30, null);

        assertThat(result.availability()).isCloseTo(0.8, within(TOLERANCE));
        assertThat(result.quality()).isCloseTo(0.98, within(TOLERANCE));
        assertThat(result.performance()).isNull();
        assertThat(result.oee()).isNull();
        assertThat(result.warnings()).anyMatch(w -> w.contains("ideal cycle time"));
    }

    @Test
    void performanceAboveOneIsNotCappedButWarned() {
        // 200 pieces at 12 s = 40 min of ideal work in only 20 min running.
        StateDurations.Result durations = durations(0, MachineState.RUNNING, 20);

        OeeResult result = OeeCalculator.calculate(durations, 200, 0, PRESS_CYCLE_MS);

        assertThat(result.performance()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(result.oee()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(result.warnings()).anyMatch(w -> w.contains("exceeds 100%"));
    }

    @Test
    void noPiecesMeansNoQualityAndNoOee() {
        StateDurations.Result durations = durations(0, MachineState.RUNNING, 60);

        OeeResult result = OeeCalculator.calculate(durations, 0, 0, PRESS_CYCLE_MS);

        assertThat(result.performance()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.quality()).isNull();
        assertThat(result.oee()).isNull();
        assertThat(result.warnings()).anyMatch(w -> w.contains("no pieces"));
    }

    @Test
    void rangeThatIsAllPlannedStopHasNoAvailabilityNorPerformance() {
        StateDurations.Result durations = durations(0, MachineState.PLANNED_STOP, 480);

        OeeResult result = OeeCalculator.calculate(durations, 0, 0, PRESS_CYCLE_MS);

        assertThat(result.plannedMs()).isZero();
        assertThat(result.availability()).isNull();
        assertThat(result.performance()).isNull();
        assertThat(result.oee()).isNull();
        assertThat(result.dataCoverage()).isEqualTo(1.0);
    }

    @Test
    void machineDownTheWholeRangeHasZeroAvailability() {
        StateDurations.Result durations = durations(0, MachineState.DOWN, 480);

        OeeResult result = OeeCalculator.calculate(durations, 0, 0, PRESS_CYCLE_MS);

        assertThat(result.availability()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.performance()).isNull();
    }

    @Test
    void rangeWithoutAnyDataHasZeroCoverage() {
        StateDurations.Result durations = durations(480);

        OeeResult result = OeeCalculator.calculate(durations, 0, 0, PRESS_CYCLE_MS);

        assertThat(result.plannedMs()).isZero();
        assertThat(result.dataCoverage()).isZero();
        assertThat(result.availability()).isNull();
    }

    /** Builds a StateDurations.Result in minutes: durations(unknownMinutes, STATE, minutes, STATE, minutes, ...). */
    private static StateDurations.Result durations(long unknownMinutes, Object... stateMinutes) {
        Map<MachineState, Long> map = new EnumMap<>(MachineState.class);
        for (MachineState state : MachineState.values()) {
            map.put(state, 0L);
        }
        for (int i = 0; i < stateMinutes.length; i += 2) {
            map.put((MachineState) stateMinutes[i], minutes(((Number) stateMinutes[i + 1]).longValue()));
        }
        return new StateDurations.Result(map, minutes(unknownMinutes));
    }

    private static long minutes(long minutes) {
        return Duration.ofMinutes(minutes).toMillis();
    }
}
