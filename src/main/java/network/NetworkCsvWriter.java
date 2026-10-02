package network;

import cascade.StationOperation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class NetworkCsvWriter {
    private NetworkCsvWriter() { }
    public static void write(Path file, NetworkDp.Result result) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("period,days,reservoir,local_inflow_m3s,start_level_m,end_level_m,start_storage_1e8m3,end_storage_1e8m3,inflow_m3s,release_m3s,turbine_flow_m3s,spill_m3s,tailwater_m,head_m,power_mw,energy_mwh,balance_residual_m3");
            out.newLine();
            for (NetworkDp.Stage stage : result.periods()) {
                for (String id : NetworkProblem.IDS) {
                    StationOperation op = stage.operations().get(id);
                    StringJoiner row = new StringJoiner(",");
                    row.add(Integer.toString(stage.input().period()));
                    row.add(Double.toString(stage.input().days())).add(id);
                    double[] values = {stage.input().localInflow().get(id), op.startLevel(), op.endLevel(),
                            op.startStorage(), op.endStorage(), op.inflow(), op.release(), op.turbineFlow(),
                            op.spill(), op.tailwater(), op.head(), op.powerMw(), op.energyMwh(), op.balanceResidualM3()};
                    for (double value : values) row.add(String.format(Locale.ROOT, "%.12f", value));
                    out.write(row.toString());
                    out.newLine();
                }
            }
        }
    }
}
