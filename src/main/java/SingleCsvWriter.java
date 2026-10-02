import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SingleCsvWriter {
    private SingleCsvWriter() { }
    public static void write(Path file, List<PeriodResult> results) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("period,days,start_level_m,end_level_m,start_storage_1e8m3,end_storage_1e8m3,inflow_m3s,release_m3s,turbine_flow_m3s,spill_m3s,tailwater_m,head_m,power_mw,energy_mwh,balance_residual_m3");
            out.newLine();
            for (PeriodResult r : results) {
                double release = r.getPowerFlow() + r.getAbandonFlow();
                double energy = r.getPowerGeneration() * 10;
                double residual = (r.getEndCapacity() - r.getStartCapacity()) * 1e8
                        - (r.getInflow() - release) * r.getDays() * 86400;
                double[] values = {r.getDays(), r.getStartLevel(), r.getEndLevel(),
                        r.getStartCapacity(), r.getEndCapacity(), r.getInflow(), release, r.getPowerFlow(),
                        r.getAbandonFlow(), r.getAvgTailLevel(),
                        (r.getStartLevel() + r.getEndLevel()) / 2 - r.getAvgTailLevel(),
                        energy / (r.getDays() * 24), energy, residual};
                StringJoiner row = new StringJoiner(",");
                row.add(Integer.toString(r.getPeriodIndex()));
                for (double value : values) row.add(String.format(Locale.ROOT, "%.12f", value));
                out.write(row.toString());
                out.newLine();
            }
        }
    }
}
