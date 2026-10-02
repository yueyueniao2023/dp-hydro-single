package network;

import cascade.*;
import java.util.*;

/** 四库精确联合DP；精确仅指所选离散模型，不是连续问题的精确最优。 */
public final class NetworkDp {
    /** 数组下标只是状态编号，状态本身始终是四库水位。 */
    public record State(double a, double b, double c, double d) {
        public double level(String id) {
            return switch (id) {
                case "A" -> a; case "B" -> b; case "C" -> c; case "D" -> d;
                default -> throw new IllegalArgumentException("未知水库：" + id);
            };
        }
    }
    public record Stage(NetworkProblem.Input input, Map<String, StationOperation> operations) {
        public Stage { operations = Map.copyOf(operations); }
        public double energyMwh() {
            double sum = 0;
            for (String id : NetworkProblem.IDS) sum += operations.get(id).energyMwh();
            return sum;
        }
    }
    public record Result(List<Stage> periods, double totalEnergyMwh) {
        public Result { periods = List.copyOf(periods); }
    }

    private final NetworkProblem problem;
    private final List<State> states = new ArrayList<>();

    public NetworkDp(NetworkProblem problem) {
        this.problem = Objects.requireNonNull(problem);
        long count = 1;
        for (String id : NetworkProblem.IDS) {
            count *= (long) reservoir(id).indexOf(reservoir(id).maxLevel()) + 1;
            if (count > 10_000) throw new IllegalArgumentException("状态过多，请先减少水位网格点");
        }
        // 上限防止初学者误设极细网格；仍用完整枚举，不换成近似算法。
        if ((double) problem.periods().size() * count * count > 20_000_000)
            throw new IllegalArgumentException("候选转移超过2000万，请减少时段或水位网格点");
        for (double a : reservoir("A").levels())
            for (double b : reservoir("B").levels())
                for (double c : reservoir("C").levels())
                    for (double d : reservoir("D").levels()) states.add(new State(a, b, c, d));
    }
    private Reservoir reservoir(String id) { return problem.network().node(id).reservoir(); }
    public int stateCount() { return states.size(); }
    public long candidateUpperBound() { return (long) problem.periods().size() * states.size() * states.size(); }

    /** 一次候选联合转移：只累加直接上游的总出库流量（包含弃水）。 */
    public Stage evaluate(int t, State start, State end) {
        NetworkProblem.Input input = problem.periods().get(t);
        Map<String, StationOperation> operations = new LinkedHashMap<>();
        for (String id : problem.network().topologicalOrder()) {
            double inflow = input.localInflow().get(id);
            for (String upstream : problem.network().upstreamOf(id)) {
                inflow += operations.get(upstream).release();
            }
            Reservoir r = reservoir(id);
            StationOperation op = HydroPhysics.evaluate(r, start.level(id), end.level(id),
                    inflow, input.days(), r.maxLevel());
            if (op == null) return null; // 任一库不可行，则整个联合转移不可行。
            operations.put(id, op);
        }
        return new Stage(input, operations);
    }

    public Result solve() {
        int stages = problem.periods().size();
        int n = states.size();
        double[][] value = new double[stages + 1][n];
        int[][] previous = new int[stages + 1][n];
        for (int t = 0; t <= stages; t++) {
            Arrays.fill(value[t], Double.NEGATIVE_INFINITY);
            Arrays.fill(previous[t], -1);
        }
        int initial = boundaryIndex(true);
        int terminal = boundaryIndex(false);
        value[0][initial] = 0;
        // F[t+1][end] = max_start {F[t][start] + 四库本期发电量}。
        for (int t = 0; t < stages; t++) {
            boolean reachable = false;
            for (int start = 0; start < n; start++) {
                if (value[t][start] == Double.NEGATIVE_INFINITY) continue;
                for (int end = 0; end < n; end++) {
                    Stage transition = evaluate(t, states.get(start), states.get(end));
                    if (transition == null) continue;
                    double candidate = value[t][start] + transition.energyMwh();
                    if (!Double.isFinite(candidate)) throw new IllegalArgumentException("累计发电量溢出");
                    if (candidate > value[t + 1][end]) {
                        value[t + 1][end] = candidate;
                        previous[t + 1][end] = start;
                        reachable = true;
                    }
                }
            }
            if (!reachable) throw new IllegalStateException("第" + (t + 1) + "期无可行联合状态；请检查来水、边界和泄流能力");
        }
        if (value[stages][terminal] == Double.NEGATIVE_INFINITY)
            throw new IllegalStateException("指定四库目标末水位无可行解；不会自动放宽末状态");
        List<Stage> path = new ArrayList<>();
        int current = terminal;
        for (int t = stages; t > 0; t--) {
            int before = previous[t][current];
            if (before < 0) throw new IllegalStateException("回溯前驱缺失");
            path.add(evaluate(t - 1, states.get(before), states.get(current)));
            current = before;
        }
        Collections.reverse(path);
        return new Result(path, value[stages][terminal]);
    }

    private int boundaryIndex(boolean initial) {
        double[] level = new double[4];
        for (int i = 0; i < 4; i++) {
            Reservoir r = reservoir(NetworkProblem.IDS.get(i));
            // 使用网格的实际浮点值，避免0.1步长的端点表示误差。
            level[i] = r.levels()[r.indexOf(initial ? r.initialLevel() : r.terminalLevel())];
        }
        return states.indexOf(new State(level[0], level[1], level[2], level[3]));
    }
}
