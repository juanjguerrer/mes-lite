package io.github.juanjguerrer.meslite.production;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.juanjguerrer.meslite.common.exception.ConflictException;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;

class ProductionCountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    private final ProductionCountRepository repository = mock(ProductionCountRepository.class);
    private final MachineRepository machineRepository = mock(MachineRepository.class);
    private final Machine machine = mock(Machine.class);
    private final ProductionCountService service = new ProductionCountService(
        repository,
        machineRepository,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    private final CreateProductionCountRequest request = new CreateProductionCountRequest(
        UUID.randomUUID(), 10L, 1L, NOW.minusSeconds(120), NOW.minusSeconds(60), 5L);

    ProductionCountServiceTest() {
        when(machine.getId()).thenReturn(1L);
        when(machine.getCode()).thenReturn("PRS-003");
        when(machineRepository.findByLineCodeAndCode("LINE-01", "PRS-003")).thenReturn(Optional.of(machine));
    }

    @Test
    void sequenceAlreadyUsedByAnotherEventThrowsConflictWithoutSaving() {
        when(repository.existsByMachineIdAndSequence(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.ingest("LINE-01", "PRS-003", request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Sequence 5")
            .hasMessageContaining("PRS-003");

        verify(repository, never()).save(any());
    }

    @Test
    void sequenceRaceLostOnSaveThrowsConflict() {
        var violation = new ConstraintViolationException(
            "duplicate key", new SQLException("duplicate key"), "UQ_PRODUCTION_COUNTS_MACHINE_ID_SEQUENCE");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key", violation));

        assertThatThrownBy(() -> service.ingest("LINE-01", "PRS-003", request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Sequence 5");
    }

    @Test
    void otherIntegrityViolationsAreRethrownUnchanged() {
        var violation = new ConstraintViolationException(
            "fk violation", new SQLException("fk violation"), "fk_production_counts_machine_id");
        var failure = new DataIntegrityViolationException("fk violation", violation);
        when(repository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.ingest("LINE-01", "PRS-003", request))
            .isSameAs(failure);
    }
}
