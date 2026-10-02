import cascade.*;
import java.util.*;

/**
 * 原单库DP的修正版：保留水位离散、逐期枚举和前驱回溯。
 * 月内极差依赖历史，状态必须包含本月已出现的旬末最低/最高水位。
 */
public class DynamicProgramming {
    private record State(int level, int monthMin, int monthMax) { }
    private record Node(State state, double value, Node previous, StationOperation operation) { }
    private final Reservoir reservoir;
    private final List<PeriodBasicData> periods;
    private final double monthRange;
    private double optimumMwh;

    public DynamicProgramming(ExcelReader reader) {
        this(reader.reservoir(), reader.getPeriodBasicDataList(), 30);
    }

    // 小网格构造入口供独立穷举测试使用；主入口继续读取原Excel。
    DynamicProgramming(Reservoir reservoir, List<PeriodBasicData> periods, double monthRange) {
        if (reservoir == null || periods == null || periods.isEmpty()
                || !Double.isFinite(monthRange) || monthRange < 0)
            throw new IllegalArgumentException("单库参数不合法");
        this.reservoir = reservoir;
        this.periods = List.copyOf(periods);
        this.monthRange = monthRange;
        for (int t = 0; t < periods.size(); t++) {
            PeriodBasicData p = periods.get(t);
            if (p.getPeriodIndex() != t + 1 || p.getMonth() < 1 || p.getMonth() > 12
                    || !Double.isFinite(p.getNaturalInFlow()) || p.getNaturalInFlow() < 0
                    || !Double.isFinite(p.getDays()) || p.getDays() <= 0
                    || (t > 0 && p.getMonth() < periods.get(t - 1).getMonth()))
                throw new IllegalArgumentException("单库时段、月份或来水无效");
        }
    }

    public List<PeriodResult> solve() {
        double[] levels = reservoir.levels();
        int initial = reservoir.indexOf(reservoir.initialLevel());
        State start = new State(initial, initial, initial);
        Map<State, Node> current = new LinkedHashMap<>();
        current.put(start, new Node(start, 0, null, null));
        for (int t = 0; t < periods.size(); t++) {
            PeriodBasicData input = periods.get(t);
            boolean firstInMonth = t == 0 || input.getMonth() != periods.get(t - 1).getMonth();
            boolean lastInMonth = t == periods.size() - 1 || input.getMonth() != periods.get(t + 1).getMonth();
            // 物理量只依赖本期期初/期末水位，不依赖本月历史；每期预计算一次。
            StationOperation[][] transitions = new StationOperation[levels.length][levels.length];
            double limit = (input.getMonth() == 7 || input.getMonth() == 8)
                    ? Math.min(842, reservoir.maxLevel()) : reservoir.maxLevel();
            for (int i = 0; i < levels.length; i++) {
                for (int j = 0; j < levels.length; j++) {
                    transitions[i][j] = HydroPhysics.evaluate(reservoir, levels[i], levels[j],
                            input.getNaturalInFlow(), input.getDays(), limit);
                }
            }
            Map<State, Node> next = new LinkedHashMap<>();
            for (Node before : current.values()) {
                for (int end = 0; end < levels.length; end++) {
                    StationOperation op = transitions[before.state().level()][end];
                    if (op == null) continue;
                    int min = firstInMonth ? end : Math.min(before.state().monthMin(), end);
                    int max = firstInMonth ? end : Math.max(before.state().monthMax(), end);
                    if (levels[max] - levels[min] > monthRange) continue;
                    // 月末检查完极差后，下一月不依赖本月极值，可以安全合并。
                    State state = lastInMonth ? new State(end, end, end) : new State(end, min, max);
                    double value = before.value() + op.energyMwh();
                    if (!Double.isFinite(value)) throw new IllegalArgumentException("累计发电量溢出");
                    Node old = next.get(state);
                    if (old == null || value > old.value())
                        next.put(state, new Node(state, value, before, op));
                }
            }
            if (next.isEmpty()) throw new IllegalStateException("第" + (t + 1) + "期无可行单库状态");
            current = next;
        }
        int terminal = reservoir.indexOf(reservoir.terminalLevel());
        Node best = current.get(new State(terminal, terminal, terminal));
        if (best == null) throw new IllegalStateException("严格目标末水位无可行解，程序不会放宽终点");
        optimumMwh = best.value();
        List<StationOperation> reverse = new ArrayList<>();
        for (Node node = best; node.previous() != null; node = node.previous()) reverse.add(node.operation());
        Collections.reverse(reverse);
        List<PeriodResult> result = new ArrayList<>();
        for (int t = 0; t < reverse.size(); t++) result.add(toResult(t, reverse.get(t)));
        return result;
    }

    public double optimumMwh() { return optimumMwh; }

    private PeriodResult toResult(int t, StationOperation op) {
        PeriodResult r = new PeriodResult();
        r.setPeriodIndex(t + 1);
        r.setDays(periods.get(t).getDays());
        r.setInflow(op.inflow());
        r.setStartLevel(op.startLevel());
        r.setEndLevel(op.endLevel());
        r.setStartCapacity(op.startStorage());
        r.setEndCapacity(op.endStorage());
        r.setPowerFlow(op.turbineFlow());
        r.setAbandonFlow(op.spill());
        r.setAvgTailLevel(op.tailwater());
        // 保留旧Excel接口的万kWh：1万kWh=10MWh。
        r.setPowerGeneration(op.energyMwh() / 10);
        r.setActualPowerGen(op.energyMwh() / 10);
        return r;
    }
}
