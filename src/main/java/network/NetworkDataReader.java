package network;

import cascade.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 简单UTF-8 CSV，无引号/嵌入逗号；首行为固定表头，# 开头为注释。 */
public final class NetworkDataReader {
    private NetworkDataReader() { }
    public static NetworkProblem read(Path directory) throws IOException {
        List<RiverNetwork.Node> nodes = new ArrayList<>();
        for (String[] row : rows(directory.resolve("reservoirs.csv"),
                "id,course,min_level_m,max_level_m,step_m,initial_m,terminal_m,min_storage_1e8m3,storage_slope_1e8m3_per_m,min_release_m3s,max_release_m3s,max_turbine_m3s,installed_mw,efficiency,tailwater_m")) {
            double min = number(row[2]), max = number(row[3]);
            double volume = number(row[7]), slope = number(row[8]), efficiency = number(row[13]);
            if (slope <= 0 || efficiency <= 0 || efficiency > 1)
                throw new IllegalArgumentException("库容斜率须为正，效率须在(0,1]内");
            double maxRelease = number(row[10]), tailwater = number(row[14]);
            Reservoir reservoir = new Reservoir(row[0], min, max, number(row[4]), number(row[5]), number(row[6]),
                    number(row[9]), maxRelease, number(row[11]), number(row[12]), 9.81 * efficiency,
                    0, max - min, new LinearCurve(new double[]{min, max},
                    new double[]{volume, volume + slope * (max - min)}),
                    new LinearCurve(new double[]{0, maxRelease}, new double[]{tailwater, tailwater}));
            nodes.add(new RiverNetwork.Node(row[0], row[1], reservoir));
        }
        List<RiverNetwork.Edge> edges = new ArrayList<>();
        for (String[] row : rows(directory.resolve("connections.csv"), "from,to"))
            edges.add(new RiverNetwork.Edge(row[0], row[1]));
        RiverNetwork network = new RiverNetwork(nodes, edges);
        List<NetworkProblem.Input> inputs = new ArrayList<>();
        for (String[] row : rows(directory.resolve("inflows.csv"), "period,days,A_local_m3s,B_local_m3s,C_local_m3s,D_local_m3s")) {
            Map<String, Double> flow = new LinkedHashMap<>();
            for (int i = 0; i < 4; i++) flow.put(NetworkProblem.IDS.get(i), number(row[i + 2]));
            inputs.add(new NetworkProblem.Input(Integer.parseInt(row[0]), number(row[1]), flow));
        }
        return new NetworkProblem(network, inputs);
    }

    private static double number(String value) {
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result)) throw new IllegalArgumentException("数值须为有限数：" + value);
        return result;
    }

    private static List<String[]> rows(Path file, String header) throws IOException {
        List<String[]> result = new ArrayList<>();
        boolean seenHeader = false;
        int columns = header.split(",").length;
        int lineNumber = 0;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!seenHeader) {
                if (!line.equals(header)) throw new IllegalArgumentException(file + "：表头不匹配");
                seenHeader = true;
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length != columns) throw new IllegalArgumentException(file + "第" + lineNumber + "行列数错误");
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].strip();
            result.add(cells);
        }
        if (!seenHeader || result.isEmpty()) throw new IllegalArgumentException(file + "：缺少数据");
        return result;
    }
}
