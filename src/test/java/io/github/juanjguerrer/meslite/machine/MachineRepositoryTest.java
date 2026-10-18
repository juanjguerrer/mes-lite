package io.github.juanjguerrer.meslite.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MachineRepositoryTest {

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ProductionLineRepository lineRepository;

    private ProductionLine line1;
    private ProductionLine line2;

    @BeforeEach
    void setUp() {
        line1 = lineRepository.saveAndFlush(new ProductionLine("Assembly 1", "LINE-01", null));
        line2 = lineRepository.saveAndFlush(new ProductionLine("Assembly 2", "LINE-02", null));
    }

    @Test
    void savesAndReadsAMachine() {
        Machine saved = machineRepository.saveAndFlush(new Machine("Press 3", "PRS-003", null, 12000, line1));

        Machine found = machineRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getCode()).isEqualTo("PRS-003");
        assertThat(found.getIdealCycleTimeMs()).isEqualTo(12000);
        assertThat(found.getLine().getId()).isEqualTo(line1.getId());
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void allowsNullIdealCycleTime() {
        Machine saved = machineRepository.saveAndFlush(new Machine("Manual station", "MAN-001", null, null, line1));

        assertThat(saved.getIdealCycleTimeMs()).isNull();
    }

    @Test
    void rejectsNonPositiveIdealCycleTime() {
        assertThatThrownBy(() -> machineRepository.saveAndFlush(new Machine("Press 3", "PRS-003", null, 0, line1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameCodeOnDifferentLines() {
        machineRepository.saveAndFlush(new Machine("Press 3", "PRS-003", null, 12000, line1));
        Machine other = machineRepository.saveAndFlush(new Machine("Press 3", "PRS-003", null, 12000, line2));

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void rejectsDuplicateCodeOnSameLine() {
        machineRepository.saveAndFlush(new Machine("Press 3", "PRS-003", null, 12000, line1));

        assertThatThrownBy(() -> machineRepository.saveAndFlush(new Machine("Press 3 bis", "PRS-003", null, 12000, line1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
