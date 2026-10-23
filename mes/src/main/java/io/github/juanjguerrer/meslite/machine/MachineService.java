package io.github.juanjguerrer.meslite.machine;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.juanjguerrer.meslite.common.CodeRules;
import io.github.juanjguerrer.meslite.common.LogicalOperator;
import io.github.juanjguerrer.meslite.common.NotFoundException;
import io.github.juanjguerrer.meslite.machine.dto.CreateMachineRequest;
import io.github.juanjguerrer.meslite.machine.dto.MachineResponse;
import io.github.juanjguerrer.meslite.machine.dto.UpdateMachineRequest;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.line.ProductionLine;

@Service
public class MachineService {
    private final MachineRepository repository;
    private final ProductionLineRepository lineRepository;

    public MachineService(MachineRepository repository, ProductionLineRepository lineRepository) {
        this.repository = repository;
        this.lineRepository = lineRepository;
    }

    @Transactional
    public MachineResponse create(CreateMachineRequest request, Long lineId) {
        ProductionLine line = lineRepository.findById(lineId)
            .orElseThrow(() -> NotFoundException.byId("Production line", lineId));
        String code = CodeRules.normalizeCode(request.code());
        Integer cycleMs = CycleTimeRules.toMillis(request.idealCycleTime(), request.idealCycleTimeUnit());
        Machine machine = new Machine(request.name(), code, request.description(), cycleMs, line);
        return MachineResponse.from(repository.save(machine));
    }

    @Transactional(readOnly = true)
    public MachineResponse findById(Long id) {
        return repository.findById(id).map(MachineResponse::from)
            .orElseThrow(() -> NotFoundException.byId("Machine", id));
    }

    @Transactional
    public MachineResponse update(Long id, UpdateMachineRequest request) {
        Machine machine = repository.findById(id)
            .orElseThrow(() -> NotFoundException.byId("Machine", id));
        Integer cycleMs = CycleTimeRules.toMillis(request.idealCycleTime(), request.idealCycleTimeUnit());
        machine.setName(request.name());
        machine.setDescription(request.description());
        machine.setIdealCycleTimeMs(cycleMs);
        return MachineResponse.from(repository.saveAndFlush(machine));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw NotFoundException.byId("Machine", id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<MachineResponse> listByLine(Pageable pageable, Long lineId) {
        lineRepository.findById(lineId)
            .orElseThrow(() -> NotFoundException.byId("Production line", lineId));
        Specification<Machine> spec = MachineSpecifications.lineEquals(lineId);
        return repository.findAll(spec, pageable).map(MachineResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<MachineResponse> list(
        Pageable pageable,
        String code,
        String name,
        LogicalOperator operator
    ) {
        List<Specification<Machine>> specs = new ArrayList<>();

        if (code != null && !code.isBlank()) {
            specs.add(MachineSpecifications.codeContains(code));
        }

        if (name != null && !name.isBlank()) {
            specs.add(MachineSpecifications.nameContains(name));
        }
        if (specs.isEmpty()) {
            return repository.findAll(pageable).map(MachineResponse::from);
        }

        Specification<Machine> spec = operator == LogicalOperator.OR
            ? Specification.anyOf(specs)
            : Specification.allOf(specs);
        return repository.findAll(spec, pageable).map(MachineResponse::from);
    }
}
