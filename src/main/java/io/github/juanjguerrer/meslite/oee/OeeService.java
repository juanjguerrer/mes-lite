package io.github.juanjguerrer.meslite.oee;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.BusinessRuleException;
import io.github.juanjguerrer.meslite.common.InvalidRequestException;
import io.github.juanjguerrer.meslite.common.NotFoundException;
import io.github.juanjguerrer.meslite.common.PlantProperties;
import io.github.juanjguerrer.meslite.common.TimeRanges;
import io.github.juanjguerrer.meslite.event.MachineStateEventService;
import io.github.juanjguerrer.meslite.event.StateDurations;
import io.github.juanjguerrer.meslite.event.dto.StateDurationsResponse;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.oee.OeeCalculator.OeeResult;
import io.github.juanjguerrer.meslite.oee.dto.OeeResponse;
import io.github.juanjguerrer.meslite.production.ProductionCountService;
import io.github.juanjguerrer.meslite.production.dto.ProductionTotalsResponse;
import io.github.juanjguerrer.meslite.shift.ShiftRepository;
import io.github.juanjguerrer.meslite.shift.ShiftWindow;
import io.github.juanjguerrer.meslite.shift.ShiftWindow.Window;

@Service
public class OeeService {

    private final MachineRepository machineRepository;
    private final ShiftRepository shiftRepository;
    private final PlantProperties plantProperties;
    private final MachineStateEventService machineStateEventService;
    private final ProductionCountService productionCountService;
    private final ProductionLineRepository lineRepository;
    private final Clock clock;

    public OeeService(
        MachineRepository machineRepository,
        Clock clock,
        ShiftRepository shiftRepository,
        PlantProperties plantProperties,
        MachineStateEventService machineStateEventService,
        ProductionCountService productionCountService,
        ProductionLineRepository lineRepository
    ) {
        this.machineRepository = machineRepository;
        this.clock = clock;
        this.shiftRepository = shiftRepository;
        this.plantProperties = plantProperties;
        this.machineStateEventService = machineStateEventService;
        this.productionCountService = productionCountService;
        this.lineRepository = lineRepository;
    }

    @Transactional(readOnly = true)
    public OeeResponse machineOeeFromLine(Long lineId, Instant from, Instant to, String shiftCode, LocalDate date) {
        var line = lineRepository.findById(lineId)
            .orElseThrow(() -> NotFoundException.byId("Production line", lineId));
        if (line.getReferenceMachine() == null) {
            throw new BusinessRuleException("Line " + line.getCode() + " does not have a reference machine. Cannot compute OEE.");
        }
        var machine = line.getReferenceMachine();
        return machineOee(machine.getId(), from, to, shiftCode, date);
    }

    @Transactional(readOnly = true)
    public OeeResponse machineOee(Long machineId, Instant from, Instant to, String shiftCode, LocalDate date) {
        var machine = machineRepository.findById(machineId)
            .orElseThrow(() -> NotFoundException.byId("Machine", machineId));
        boolean isFromToMode = from != null && to != null;
        boolean isShiftDateMode = shiftCode != null && date != null;
        boolean isFromToPartiallySpecified = (from != null && to == null) || (from == null && to != null);
        boolean isShiftDatePartiallySpecified = (shiftCode != null && date == null) || (shiftCode == null && date != null);
        if (isFromToPartiallySpecified || isShiftDatePartiallySpecified) {
            throw new InvalidRequestException("Both from/to or shiftCode/date must be specified together. Please provide both values.");
        }
        if (isFromToMode && isShiftDateMode) {
            throw new InvalidRequestException("Cannot specify both from/to and shiftCode/date. Please choose one.");
        }
        if (!isFromToMode && !isShiftDateMode) {
            throw new InvalidRequestException("Either from/to or shiftCode/date must be specified. Please provide one of the two.");
        }
        shiftCode = shiftCode != null ? shiftCode.trim().toUpperCase(Locale.ROOT) : null;
        var range = isFromToMode ? getRangeByToAndFrom(from, to) : getRangeByShiftAndDate(machine.getLine().getId(), shiftCode, date);
        StateDurationsResponse durations = machineStateEventService.stateDurations(machineId, range.from(), range.to());
        StateDurations.Result durationsResult = new StateDurations.Result(durations.durationsMs(), durations.unknownMs());
        ProductionTotalsResponse totals = productionCountService.totals(machineId, range.from(), range.to());
        OeeResult result = OeeCalculator.calculate(durationsResult, totals.goodCount(), totals.scrapCount(), machine.getIdealCycleTimeMs());
        return OeeResponse.from(machineId, range.from(), range.to(), shiftCode, result);
    }

    private TimeRanges.Range getRangeByToAndFrom(
        Instant from,
        Instant to
    ) {
        return TimeRanges.validateAndAdjust(from, to, clock);
    }

    private TimeRanges.Range getRangeByShiftAndDate(
        Long lineId,
        String shiftCode,
        LocalDate date
    ) {
        var shift = shiftRepository.findByLineIdAndCode(lineId, shiftCode)
            .orElseThrow(() -> NotFoundException.shiftByLineIdAndCode(lineId, shiftCode));
        Window window = ShiftWindow.resolve(shift.getStartTime(), shift.getEndTime(), date, plantProperties.zoneId());
        return TimeRanges.validateAndAdjust(window.from(), window.to(), clock);
    }
}
