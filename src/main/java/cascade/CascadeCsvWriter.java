package cascade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.StringJoiner;

/** 输出每期水量平衡和发电结果，列名携带单位，便于 Excel/Python 二次检查。 */
public final class CascadeCsvWriter {
    private static final String[] OPERATION_COLUMNS = {
            "start_level_m", "end_level_m", "start_storage_1e8m3", "end_storage_1e8m3",
            "inflow_m3s", "release_m3s", "turbine_flow_m3s", "spill_m3s", "tailwater_m",
            "head_m", "power_mw", "energy_mwh", "balance_residual_m3"
    };

    private CascadeCsvWriter() { }

    public static void write(Path file, DispatchResult result) throws IOException {
        Path absoluteFile = file.toAbsolutePath().normalize();
        Files.createDirectories(absoluteFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(absoluteFile, StandardCharsets.UTF_8)) {
            StringJoiner header = new StringJoiner(",");
            header.add("period").add("days").add("upstream_natural_inflow_m3s")
                    .add("interval_inflow_m3s").add("upstream_level_limit_m").add("downstream_level_limit_m");
            for (String prefix : new String[]{"upstream_", "downstream_"}) {
                for (String column : OPERATION_COLUMNS) {
                    header.add(prefix + column);
                }
            }
            header.add("period_total_energy_mwh").add("cumulative_total_energy_mwh");
            writer.write(header.toString());
            writer.newLine();
            double cumulative = 0;
            for (CascadePeriodResult period : result.periods()) {
                PeriodInput input = period.input();
                StringJoiner row = new StringJoiner(",");
                row.add(Integer.toString(input.period()));
                add(row, input.days(), input.upstreamInflow(), input.intervalInflow(),
                        input.upstreamLevelLimit(), input.downstreamLevelLimit());
                addOperation(row, period.upstream());
                addOperation(row, period.downstream());
                cumulative += period.energyMwh();
                add(row, period.energyMwh(), cumulative);
                writer.write(row.toString());
                writer.newLine();
            }
        }
    }

    private static void addOperation(StringJoiner row, StationOperation op) {
        add(row, op.startLevel(), op.endLevel(), op.startStorage(), op.endStorage(), op.inflow(),
                op.release(), op.turbineFlow(), op.spill(), op.tailwater(), op.head(), op.powerMw(),
                op.energyMwh(), op.balanceResidualM3());
    }

    private static void add(StringJoiner row, double... values) {
        for (double value : values) {
            row.add(String.format(Locale.ROOT, "%.9f", value));
        }
    }
}
