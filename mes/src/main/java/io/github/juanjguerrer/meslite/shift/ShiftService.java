package io.github.juanjguerrer.meslite.shift;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.CodeRules;
import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;
import io.github.juanjguerrer.meslite.common.exception.NotFoundException;
import io.github.juanjguerrer.meslite.common.query.LogicalOperator;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.shift.dto.CreateShiftRequest;
import io.github.juanjguerrer.meslite.shift.dto.ShiftResponse;
import io.github.juanjguerrer.meslite.shift.dto.UpdateShiftRequest;

@Service
public class ShiftService {
    private final ShiftRepository repository;
    private final ProductionLineRepository lineRepository;

    public ShiftService(ShiftRepository repository, ProductionLineRepository lineRepository) {
        this.repository = repository;
        this.lineRepository = lineRepository;
    }

    @Transactional
    public ShiftResponse create(CreateShiftRequest request, Long lineId) {
        ProductionLine line = lineRepository.findById(lineId)
            .orElseThrow(() -> NotFoundException.byId("Production line", lineId));
        String code = CodeRules.normalizeCode(request.code());
        if (request.startTime().equals(request.endTime())) {
            throw new BusinessRuleException("Shift start time and end time cannot be the same");
        }
        List<Shift> existingShifts = repository.findByLineIdOrderByStartTime(lineId);
        for (Shift existingShift : existingShifts) {
            if (ShiftRules.overlaps(request.startTime(), request.endTime(), existingShift.getStartTime(), existingShift.getEndTime())) {
                throw new BusinessRuleException("Shift overlaps with shift " + existingShift.getCode() + " (" + existingShift.getStartTime() + " - " + existingShift.getEndTime() + ")");
            }
        }
        Shift shift = new Shift(line, code, request.name(), request.startTime(), request.endTime());
        return ShiftResponse.from(repository.save(shift));
    }

    @Transactional(readOnly = true)
    public ShiftResponse findById(Long id) {
        return repository.findById(id).map(ShiftResponse::from)
            .orElseThrow(() -> NotFoundException.byId("Shift", id));
    }

    @Transactional
    public ShiftResponse update(Long id, UpdateShiftRequest request){
        Shift shift = repository.findById(id)
            .orElseThrow(() -> NotFoundException.byId("Shift", id));
        if (request.startTime().equals(request.endTime())) {
            throw new BusinessRuleException("Shift start time and end time cannot be the same");
        }
        List<Shift> existingShifts = repository.findByLineIdOrderByStartTime(shift.getLine().getId());
        for (Shift existingShift : existingShifts) {
            if (existingShift.getId().equals(id)) {
                continue;
            }
            if (ShiftRules.overlaps(request.startTime(), request.endTime(), existingShift.getStartTime(), existingShift.getEndTime())) {
                throw new BusinessRuleException("Shift overlaps with shift " + existingShift.getCode() + " (" + existingShift.getStartTime() + " - " + existingShift.getEndTime() + ")");
            }
        }
        shift.setName(request.name());
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        return ShiftResponse.from(repository.saveAndFlush(shift));
    }

    @Transactional
    public void delete (Long id) {
        Shift shift = repository.findById(id)
            .orElseThrow(() -> NotFoundException.byId("Shift", id));
        repository.delete(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listByLine(Long lineId) {
        lineRepository.findById(lineId)
            .orElseThrow(() -> NotFoundException.byId("Production line", lineId));
        Specification<Shift> spec = ShiftSpecifications.lineEquals(lineId);
        return repository.findAll(spec)
            .stream()
            .map(ShiftResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<ShiftResponse> list(
        Pageable pageable,
        String code,
        String name,
        LogicalOperator operator
    ) {
        List<Specification<Shift>> specs = new ArrayList<>();
        if (code != null && !code.isBlank()) {
            specs.add(ShiftSpecifications.codeContains(code));
        }

        if (name != null && !name.isBlank()) {
            specs.add(ShiftSpecifications.nameContains(name));
        }

        if (specs.isEmpty()) {
            return repository.findAll(pageable).map(ShiftResponse::from);
        }
        Specification<Shift> spec = operator == LogicalOperator.OR
            ? Specification.anyOf(specs)
            : Specification.allOf(specs);
        return repository.findAll(spec, pageable).map(ShiftResponse::from);
    }
}