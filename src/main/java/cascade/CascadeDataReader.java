package cascade;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 只负责 UTF-8 文本输入；水文计算和动态规划分别放在其它类中。 */
public final class CascadeDataReader {
    private static final String[] RESERVOIR_FIELDS = {
            "name", "minLevel", "maxLevel", "levelStep", "initialLevel", "terminalLevel",
            "minRelease", "maxRelease", "maxTurbineFlow", "installedPowerMw",
            "powerCoefficient", "headLoss", "maxLevelChange"
    };

    private CascadeDataReader() { }

    public static CascadeProblem read(Path directory) throws IOException {
        Path parameterFile = directory.resolve("reservoirs.properties");
        Parameters parameters = readParameters(parameterFile);
        Reservoir upstream = readReservoir(directory, "upstream", parameters);
        Reservoir downstream = readReservoir(directory, "downstream", parameters);
        Path inflowFile = directory.resolve("inflows.csv");
        List<CsvRow> rows = readCsv(inflowFile,
                "period,days,upstream_inflow_m3s,interval_inflow_m3s,upstream_level_limit_m,downstream_level_limit_m");
        List<PeriodInput> periods = new ArrayList<>();
        for (CsvRow row : rows) {
            int period;
            try {
                period = Integer.parseInt(row.values()[0].trim());
            } catch (NumberFormatException e) {
                throw failure(inflowFile, row.line(), "period 必须是整数", e);
            }
            if (period != periods.size() + 1) {
                throw failure(inflowFile, row.line(), "period 必须从 1 开始连续递增", null);
            }
            double[] value = numbers(inflowFile, row);
            if (value[1] <= 0 || value[2] < 0 || value[3] < 0) {
                throw failure(inflowFile, row.line(), "days 必须大于 0，天然来水和区间来水必须非负", null);
            }
            checkLimit(inflowFile, row.line(), value[4], upstream, "上游");
            checkLimit(inflowFile, row.line(), value[5], downstream, "下游");
            try {
                periods.add(new PeriodInput(period, value[1], value[2], value[3], value[4], value[5]));
            } catch (IllegalArgumentException e) {
                throw failure(inflowFile, row.line(), e.getMessage(), e);
            }
        }
        try {
            return new CascadeProblem(upstream, downstream, periods);
        } catch (IllegalArgumentException e) {
            throw failure(inflowFile, 1, e.getMessage(), e);
        }
    }

    private static void checkLimit(Path file, int line, double limit, Reservoir reservoir,
                                   String name) throws IOException {
        if (limit < reservoir.minLevel() || limit > reservoir.maxLevel()) {
            throw failure(file, line, name + "水位上限必须位于库水位范围内", null);
        }
    }

    private static Reservoir readReservoir(Path directory, String prefix, Parameters p) throws IOException {
        LinearCurve storage = readCurve(directory.resolve(prefix + "-level-storage.csv"),
                "level_m,storage_1e8m3");
        LinearCurve tailwater = readCurve(directory.resolve(prefix + "-release-tailwater.csv"),
                "release_m3s,tailwater_m");
        try {
            return new Reservoir(p.text(prefix + ".name"),
                    p.number(prefix + ".minLevel"), p.number(prefix + ".maxLevel"),
                    p.number(prefix + ".levelStep"), p.number(prefix + ".initialLevel"),
                    p.number(prefix + ".terminalLevel"), p.number(prefix + ".minRelease"),
                    p.number(prefix + ".maxRelease"), p.number(prefix + ".maxTurbineFlow"),
                    p.number(prefix + ".installedPowerMw"), p.number(prefix + ".powerCoefficient"),
                    p.number(prefix + ".headLoss"), p.number(prefix + ".maxLevelChange"),
                    storage, tailwater);
        } catch (IllegalArgumentException e) {
            throw failure(p.file(), p.lines().get(prefix + ".name"),
                    prefix + " 参数组合无效：" + e.getMessage(), e);
        }
    }

    private static LinearCurve readCurve(Path file, String header) throws IOException {
        List<CsvRow> rows = readCsv(file, header);
        if (rows.size() < 2) {
            throw failure(file, 1, "曲线至少需要 2 个数据点", null);
        }
        double[] x = new double[rows.size()];
        double[] y = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            CsvRow row = rows.get(i);
            double[] values = numbers(file, row);
            x[i] = values[0];
            y[i] = values[1];
            if (i > 0 && x[i] <= x[i - 1]) {
                throw failure(file, row.line(), "曲线第一列必须严格递增，不能重复", null);
            }
            if (header.equals("level_m,storage_1e8m3") && (y[i] < 0 || (i > 0 && y[i] <= y[i - 1]))) {
                throw failure(file, row.line(), "库容必须非负且随水位严格递增", null);
            }
            if (header.equals("release_m3s,tailwater_m") && (x[i] < 0 || (i > 0 && y[i] < y[i - 1]))) {
                throw failure(file, row.line(), "泄水流量必须非负，尾水位不能随流量下降", null);
            }
        }
        try {
            return new LinearCurve(x, y);
        } catch (IllegalArgumentException e) {
            throw failure(file, 1, e.getMessage(), e);
        }
    }

    private static List<CsvRow> readCsv(Path file, String header) throws IOException {
        List<String> lines = readLines(file);
        int columns = header.split(",", -1).length;
        boolean sawHeader = false;
        List<CsvRow> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!sawHeader) {
                if (!line.equals(header)) {
                    throw failure(file, i + 1, "CSV 表头必须精确为 " + header, null);
                }
                sawHeader = true;
                continue;
            }
            String[] values = line.split(",", -1);
            if (values.length != columns) {
                throw failure(file, i + 1, "需要 " + columns + " 列，实际为 " + values.length + " 列", null);
            }
            result.add(new CsvRow(i + 1, values));
        }
        if (!sawHeader || result.isEmpty()) {
            throw failure(file, Math.max(1, lines.size()), "缺少表头或数据行", null);
        }
        return result;
    }

    private static double[] numbers(Path file, CsvRow row) throws IOException {
        double[] result = new double[row.values().length];
        for (int i = 0; i < result.length; i++) {
            result[i] = finiteNumber(file, row.line(), row.values()[i], "第 " + (i + 1) + " 列");
        }
        return result;
    }

    private static double finiteNumber(Path file, int line, String raw, String name) throws IOException {
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite");
            }
            return value;
        } catch (NumberFormatException e) {
            throw failure(file, line, name + " 必须是有限数值，实际为 '" + raw + "'", e);
        }
    }

    /** 为便于定位错误，仅接受示例所用的单行 key=value 写法，不接受续行或转义。 */
    private static Parameters readParameters(Path file) throws IOException {
        Properties values = new Properties();
        Map<String, Integer> lineNumbers = new HashMap<>();
        Set<String> allowed = new HashSet<>();
        for (String prefix : List.of("upstream", "downstream")) {
            for (String field : RESERVOIR_FIELDS) {
                allowed.add(prefix + "." + field);
            }
        }
        List<String> lines = readLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || line.indexOf('\\') >= 0) {
                throw failure(file, i + 1, "参数采用单行 key=value 格式，不支持反斜杠转义或续行", null);
            }
            String key = line.substring(0, separator).trim();
            if (!allowed.contains(key)) {
                throw failure(file, i + 1, "未知参数 " + key, null);
            }
            if (lineNumbers.putIfAbsent(key, i + 1) != null) {
                throw failure(file, i + 1, "重复参数 " + key, null);
            }
            values.load(new StringReader(line));
            if (values.getProperty(key, "").trim().isEmpty()) {
                throw failure(file, i + 1, "参数 " + key + " 不能为空", null);
            }
        }
        for (String key : allowed) {
            if (!values.containsKey(key)) {
                throw failure(file, 1, "缺少必填参数 " + key, null);
            }
        }
        return new Parameters(file, values, lineNumbers);
    }

    private static List<String> readLines(Path file) throws IOException {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
                lines.set(0, lines.get(0).substring(1));
            }
            return lines;
        } catch (IOException e) {
            throw failure(file, 1, "无法读取 UTF-8 文件：" + e.getMessage(), e);
        }
    }

    private static IOException failure(Path file, int line, String message, Throwable cause) {
        return new IOException(file + ":" + line + ": " + message, cause);
    }

    private record CsvRow(int line, String[] values) { }

    private record Parameters(Path file, Properties values, Map<String, Integer> lines) {
        private String text(String key) {
            return values.getProperty(key).trim();
        }

        private double number(String key) throws IOException {
            return finiteNumber(file, lines.get(key), text(key), key);
        }
    }
}
