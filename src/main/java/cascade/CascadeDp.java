package cascade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 两库联合动态规划：时间是阶段，两库水位组合是状态。
 * 状态转移数 O(T * N上² * N下²)，存储 O(T * N上 * N下)。
 * 这里有意保留直观的完整dp表，便于学习和在IDE中观察。
 */
public final class CascadeDp {
    private final CascadeProblem problem;

    public CascadeDp(CascadeProblem problem) {
        if (problem == null) throw new IllegalArgumentException("调度问题不能为空");
        this.problem = problem;
    }

    public DispatchResult solve() {
        Reservoir upper = problem.upstream();
        Reservoir lower = problem.downstream();
        double[] upperLevels = upper.levels();
        double[] lowerLevels = lower.levels();
        int stages = problem.periods().size();
        int nu = upperLevels.length;
        int nd = lowerLevels.length;

        // dp[t][u][d]：运行t期后到达两库水位(u,d)时，累计最大发电量(MWh)。
        // 唯一初始状态以外全部置负无穷；不能以0代表不可达。
        double[][][] dp = new double[stages + 1][nu][nd];
        int[][][] previousUpper = new int[stages + 1][nu][nd];
        int[][][] previousLower = new int[stages + 1][nu][nd];
        for (int t = 0; t <= stages; t++) {
            for (int u = 0; u < nu; u++) {
                Arrays.fill(dp[t][u], Double.NEGATIVE_INFINITY);
                Arrays.fill(previousUpper[t][u], -1);
                Arrays.fill(previousLower[t][u], -1);
            }
        }
        int initialU = upper.indexOf(upper.initialLevel());
        int initialD = lower.indexOf(lower.initialLevel());
        dp[0][initialU][initialD] = 0.0;

        // 正向递推：依次枚举前一阶段联合状态和本期末联合状态。
        for (int t = 1; t <= stages; t++) {
            PeriodInput input = problem.periods().get(t - 1);
            boolean anyReachable = false;
            for (int startU = 0; startU < nu; startU++) {
                for (int startD = 0; startD < nd; startD++) {
                    if (dp[t - 1][startU][startD] == Double.NEGATIVE_INFINITY) continue;
                    for (int endU = 0; endU < nu; endU++) {
                        StationOperation opU = HydroPhysics.evaluate(upper, upperLevels[startU],
                                upperLevels[endU], input.upstreamInflow(), input.days(),
                                input.upstreamLevelLimit());
                        if (opU == null) continue;

                        // 梯级耦合的核心：上游发电流量 + 上游弃水 + 区间来水。
                        double lowerInflow = opU.release() + input.intervalInflow();
                        for (int endD = 0; endD < nd; endD++) {
                            StationOperation opD = HydroPhysics.evaluate(lower, lowerLevels[startD],
                                    lowerLevels[endD], lowerInflow, input.days(), input.downstreamLevelLimit());
                            if (opD == null) continue;
                            double candidate = dp[t - 1][startU][startD] + opU.energyMwh() + opD.energyMwh();
                            if (!Double.isFinite(candidate)) throw new IllegalArgumentException("累计发电量溢出");
                            if (candidate > dp[t][endU][endD]) {
                                dp[t][endU][endD] = candidate;
                                previousUpper[t][endU][endD] = startU;
                                previousLower[t][endU][endD] = startD;
                                anyReachable = true;
                            }
                        }
                    }
                }
            }
            if (!anyReachable) {
                throw new IllegalStateException("第" + t + "期无可达联合状态；请检查来水、泄流能力、"
                        + "水位变幅和汛限（含期初水位）约束，或尝试更细的网格");
            }
        }

        // 两座水库均使用严格指定的期末水位，不在附近另选高收益终点。
        int currentU = upper.indexOf(upper.terminalLevel());
        int currentD = lower.indexOf(lower.terminalLevel());
        double optimum = dp[stages][currentU][currentD];
        if (optimum == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException("指定的两库期末水位不可达；请检查边界、约束和离散步长，"
                    + "程序不会自动放宽期末水位");
        }

        List<CascadePeriodResult> reverse = new ArrayList<>();
        for (int t = stages; t >= 1; t--) {
            int startU = previousUpper[t][currentU][currentD];
            int startD = previousLower[t][currentU][currentD];
            if (startU < 0 || startD < 0) throw new IllegalStateException("DP回溯前驱缺失");
            PeriodInput input = problem.periods().get(t - 1);
            StationOperation opU = HydroPhysics.evaluate(upper, upperLevels[startU], upperLevels[currentU],
                    input.upstreamInflow(), input.days(), input.upstreamLevelLimit());
            StationOperation opD = HydroPhysics.evaluate(lower, lowerLevels[startD], lowerLevels[currentD],
                    opU.release() + input.intervalInflow(), input.days(), input.downstreamLevelLimit());
            reverse.add(new CascadePeriodResult(input, opU, opD));
            currentU = startU;
            currentD = startD;
        }
        Collections.reverse(reverse);
        return new DispatchResult(reverse, optimum);
    }
}
