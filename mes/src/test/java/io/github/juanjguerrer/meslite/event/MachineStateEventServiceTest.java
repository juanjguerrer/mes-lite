package io.github.juanjguerrer.meslite.event;

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
import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

class MachineStateEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    private final MachineStateEventRepository repository = mock(MachineStateEventRepository.class);
    private final MachineRepository machineRepository = mock(MachineRepository.class);
    private final Machine machine = mock(Machine.class);
    private final MachineStateEventService service = new MachineStateEventService(
        repository,
        machineRepository,
        Clock.fixed(NOW, ZoneOffset.UTC),
        mock(ProductionLineRepository.class)
    );

    private final CreateStateEventRequest request = new CreateStateEventRequest(
        UUID.randomUUID(), MachineState.RUNNING, NOW.minusSeconds(60), null, 5L);

    MachineStateEventServiceTest() {
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
            "duplicate key", new SQLException("duplicate key"), "UQ_MACHINE_STATE_EVENTS_MACHINE_ID_SEQUENCE");
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key", violation));

        assertThatThrownBy(() -> service.ingest("LINE-01", "PRS-003", request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Sequence 5");
    }

    @Test
    void otherIntegrityViolationsAreRethrownUnchanged() {
        var violation = new ConstraintViolationException(
            "fk violation", new SQLException("fk violation"), "fk_machine_state_events_machine_id");
        var failure = new DataIntegrityViolationException("fk violation", violation);
        when(repository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.ingest("LINE-01", "PRS-003", request))
            .isSameAs(failure);
    }
}
