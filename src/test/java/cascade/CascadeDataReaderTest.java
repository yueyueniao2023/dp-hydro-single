package cascade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CascadeDataReaderTest {
    private static final String INFLOW_HEADER =
            "period,days,upstream_inflow_m3s,interval_inflow_m3s,upstream_level_limit_m,downstream_level_limit_m\n";

    @TempDir
    Path directory;

    @Test
    void bundledSyntheticYearHasCorrectCalendarAndFeasibleExactTerminalLevels() throws IOException {
        CascadeProblem problem = CascadeDataReader.read(Path.of("data", "cascade"));
        assertEquals(36, problem.periods().size());
        assertEquals(365, problem.periods().stream().mapToDouble(PeriodInput::days).sum(), 1e-12);
        assertEquals(8, problem.periods().get(5).days(), 1e-12);
        assertEquals(11, problem.periods().get(2).days(), 1e-12);
        DispatchResult result = new CascadeDp(problem).solve();
        assertEquals(36, result.periods().size());
        assertTrue(Double.isFinite(result.totalEnergyMwh()));
        assertTrue(result.totalEnergyMwh() > 0);
        CascadePeriodResult last = result.periods().get(35);
        assertEquals(problem.upstream().terminalLevel(), last.upstream().endLevel(), 1e-12);
        assertEquals(problem.downstream().terminalLevel(), last.downstream().endLevel(), 1e-12);
        for (CascadePeriodResult period : result.periods()) {
            assertEquals(period.upstream().release() + period.input().intervalInflow(),
                    period.downstream().inflow(), 1e-9);
            assertTrue(period.upstream().startLevel() <= period.input().upstreamLevelLimit() + 1e-8);
            assertTrue(period.upstream().endLevel() <= period.input().upstreamLevelLimit() + 1e-8);
            assertTrue(period.downstream().startLevel() <= period.input().downstreamLevelLimit() + 1e-8);
            assertTrue(period.downstream().endLevel() <= period.input().downstreamLevelLimit() + 1e-8);
            assertEquals(0, period.upstream().balanceResidualM3(), 1e-5);
            assertEquals(0, period.downstream().balanceResidualM3(), 1e-5);
        }
    }

    @Test
    void nonFiniteCsvDataReportsFileAndLine() throws IOException {
        copyExample();
        Files.writeString(directory.resolve("inflows.csv"),
                INFLOW_HEADER + "1,10,NaN,15,220,110\n", StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class, () -> CascadeDataReader.read(directory));
        assertTrue(error.getMessage().contains("inflows.csv:2:"), error.getMessage());
        assertTrue(error.getMessage().contains("NaN"), error.getMessage());
    }

    @Test
    void repeatedPeriodDoesNotSilentlyOverwriteInput() throws IOException {
        copyExample();
        Files.writeString(directory.resolve("inflows.csv"), INFLOW_HEADER
                + "1,10,80,15,220,110\n1,10,85,16,220,110\n", StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class, () -> CascadeDataReader.read(directory));
        assertTrue(error.getMessage().contains("inflows.csv:3:"), error.getMessage());
    }

    @Test
    void negativeIntervalInflowIsRejected() throws IOException {
        copyExample();
        Files.writeString(directory.resolve("inflows.csv"),
                INFLOW_HEADER + "1,10,80,-15,220,110\n", StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class, () -> CascadeDataReader.read(directory));
        assertTrue(error.getMessage().contains("inflows.csv:2:"), error.getMessage());
    }

    @Test
    void storageMustIncreaseWithWaterLevel() throws IOException {
        copyExample();
        Files.writeString(directory.resolve("upstream-level-storage.csv"),
                "level_m,storage_1e8m3\n200,0.3\n220,0.2\n", StandardCharsets.UTF_8);
        IOException error = assertThrows(IOException.class, () -> CascadeDataReader.read(directory));
        assertTrue(error.getMessage().contains("upstream-level-storage.csv:3:"), error.getMessage());
    }

    @Test
    void duplicateParametersAreReportedInsteadOfLastValueWinning() throws IOException {
        copyExample();
        Files.writeString(directory.resolve("reservoirs.properties"), "\nupstream.levelStep=2\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        IOException error = assertThrows(IOException.class, () -> CascadeDataReader.read(directory));
        assertTrue(error.getMessage().contains("reservoirs.properties:"), error.getMessage());
        assertTrue(error.getMessage().contains("upstream.levelStep"), error.getMessage());
    }

    private void copyExample() throws IOException {
        for (String file : List.of("reservoirs.properties", "inflows.csv",
                "upstream-level-storage.csv", "downstream-level-storage.csv",
                "upstream-release-tailwater.csv", "downstream-release-tailwater.csv")) {
            Files.copy(Path.of("data", "cascade", file), directory.resolve(file));
        }
    }
}
