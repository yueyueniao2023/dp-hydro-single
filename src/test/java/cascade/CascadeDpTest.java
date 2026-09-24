package cascade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CascadeDpTest {
    private static Reservoir reservoir(String name, double minLevel, double initialLevel,
                                       double terminalLevel, double turbineFlow,
                                       double maxLevelChange, double tailwater) {
        return new Reservoir(name, minLevel, minLevel + 2, 1, initialLevel, terminalLevel,
                0, 200, turbineFlow, 1000, 8.5, 0, maxLevelChange,
                new LinearCurve(new double[]{minLevel, minLevel + 2}, new double[]{0, 0.02}),
                new LinearCurve(new double[]{0, 200}, new double[]{tailwater, tailwater}));
    }

    private static CascadeProblem example(double[] days) {
        double[] inflows = {20, 80, 20};
        List<PeriodInput> periods = new ArrayList<>();
        for (int t = 0; t < days.length; t++) {
            periods.add(new PeriodInput(t + 1, days[t], inflows[t], 3, 102, 52));
        }
        return new CascadeProblem(
                reservoir("upstream", 100, 101, 101, 100, 2, 90),
                reservoir("downstream", 50, 51, 51, 35, 2, 0), periods);
    }

    @Test
    void dynamicProgrammingMatchesIndependentCompletePathEnumeration() {
        CascadeProblem problem = example(new double[]{1, 1, 1});
        List<Candidate> enumerated = enumerateByHand(problem.periods());
        Candidate expected = enumerated.stream().max(Comparator.comparingDouble(Candidate::totalEnergy)).orElseThrow();
        DispatchResult result = new CascadeDp(problem).solve();
        // 9 条上库路径 × 9 条下库路径，全部为完整路径而非 DP 递推。
        assertEquals(81, allPaths(100, 3).size() * allPaths(50, 3).size());
        assertEquals(64, enumerated.size()); // 剔除水量不足导致负泄流的完整路径。
        assertEquals(1367.5121111111112, expected.totalEnergy(), 1e-8);
        assertEquals(expected.totalEnergy(), result.totalEnergyMwh(), 1e-8);
        assertEquals(3, result.periods().size());
        assertEquals(101, result.periods().get(0).upstream().startLevel(), 1e-12);
        assertEquals(51, result.periods().get(0).downstream().startLevel(), 1e-12);
        assertEquals(101, result.periods().get(2).upstream().endLevel(), 1e-12);
        assertEquals(51, result.periods().get(2).downstream().endLevel(), 1e-12);
        assertEquals(result.totalEnergyMwh(),
                result.periods().stream().mapToDouble(CascadePeriodResult::energyMwh).sum(), 1e-8);
        assertCascadeWaterBalance(problem, result);
    }

    @Test
    void jointOptimizationCanOutperformOptimizingUpstreamFirst() {
        CascadeProblem problem = example(new double[]{1, 1, 1});
        List<Candidate> enumerated = enumerateByHand(problem.periods());
        double bestUpstreamAlone = enumerated.stream().mapToDouble(Candidate::upstreamEnergy).max().orElseThrow();
        // 即使给上库单独最优的所有并列方案最好的下库配合，仍比联合优化差。
        double sequentialBest = enumerated.stream()
                .filter(candidate -> Math.abs(candidate.upstreamEnergy() - bestUpstreamAlone) < 1e-8)
                .mapToDouble(Candidate::totalEnergy).max().orElseThrow();
        double jointBest = new CascadeDp(problem).solve().totalEnergyMwh();
        assertEquals(1258.476, sequentialBest, 1e-8);
        assertTrue(jointBest > sequentialBest + 100,
                "联合调度需要允许上库牺牲局部收益，减少下库在洪峰时段的弃水");
    }

    @Test
    void variableDurationOptimizationMatchesIndependentOracleAndConservesSystemWater() {
        CascadeProblem problem = example(new double[]{0.5, 2, 1.25});
        double expected = enumerateByHand(problem.periods()).stream()
                .mapToDouble(Candidate::totalEnergy).max().orElseThrow();
        DispatchResult result = new CascadeDp(problem).solve();
        assertEquals(expected, result.totalEnergyMwh(), 1e-8);
        assertCascadeWaterBalance(problem, result);
        assertEquals(43_200, problem.periods().get(0).seconds(), 1e-12);
        assertEquals(48, problem.periods().get(1).hours(), 1e-12);
        assertEquals(30, problem.periods().get(2).hours(), 1e-12);
    }

    @Test
    void upstreamSpillAlsoArrivesAtDownstreamReservoir() {
        CascadeProblem problem = new CascadeProblem(
                reservoir("upstream", 100, 101, 101, 20, 2, 90),
                reservoir("downstream", 50, 51, 51, 200, 2, 0),
                List.of(new PeriodInput(1, 1, 100, 7, 102, 52)));
        CascadePeriodResult period = new CascadeDp(problem).solve().periods().get(0);
        assertEquals(20, period.upstream().turbineFlow(), 1e-12);
        assertEquals(80, period.upstream().spill(), 1e-12);
        assertEquals(100, period.upstream().release(), 1e-12);
        assertEquals(107, period.downstream().inflow(), 1e-12);
        assertEquals(107, period.downstream().release(), 1e-12);
        assertEquals(107, period.downstream().turbineFlow(), 1e-12);
    }

    @Test
    void unreachableExactTerminalStateFailsInsteadOfSilentlyChoosingAnotherState() {
        CascadeProblem problem = new CascadeProblem(
                reservoir("upstream", 100, 100, 102, 100, 1, 90),
                reservoir("downstream", 50, 51, 51, 100, 2, 0),
                List.of(new PeriodInput(1, 1, 40, 3, 102, 52)));
        assertThrows(IllegalStateException.class, () -> new CascadeDp(problem).solve());
    }

    @Test
    void initialAndTerminalLevelsMustBeOnGrid() {
        assertThrows(IllegalArgumentException.class,
                () -> reservoir("off-grid start", 100, 100.5, 101, 100, 2, 90));
        assertThrows(IllegalArgumentException.class,
                () -> reservoir("off-grid finish", 100, 101, 101.5, 100, 2, 90));
        Reservoir valid = reservoir("valid", 100, 101, 101, 100, 2, 90);
        assertArrayEquals(new double[]{100, 101, 102}, valid.levels(), 1e-12);
        assertEquals(1, valid.indexOf(101));
    }

    @Test
    void solveCanBeRepeatedWithoutRetainingOldDecisions() {
        CascadeDp solver = new CascadeDp(example(new double[]{1, 1, 1}));
        DispatchResult first = solver.solve();
        DispatchResult second = solver.solve();
        assertEquals(first, second);
    }

    @Test
    void rejectsNonPositiveDurationAndInvalidInflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodInput(1, 0, 20, 3, 102, 52));
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodInput(1, -1, 20, 3, 102, 52));
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodInput(1, Double.NaN, 20, 3, 102, 52));
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodInput(1, 1, -20, 3, 102, 52));
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodInput(1, 1, 20, Double.POSITIVE_INFINITY, 102, 52));
    }

    /**
     * 独立测试 oracle：不调用 HydroPhysics、不读取生产代码的状态价值。
     * 两库每米库容变化均为 1,000,000 m3，尾水分别固定为 90 m / 0 m。
     * 按完整轨迹直接手算 R = Q - ΔV/Δt、P = 8.5 min(R,Qtmax) H /1000。
     */
    private static List<Candidate> enumerateByHand(List<PeriodInput> periods) {
        List<Candidate> candidates = new ArrayList<>();
        for (double[] upper : allPaths(100, periods.size())) {
            for (double[] lower : allPaths(50, periods.size())) {
                double upperEnergy = 0;
                double lowerEnergy = 0;
                boolean feasible = true;
                for (int t = 0; t < periods.size(); t++) {
                    PeriodInput input = periods.get(t);
                    double seconds = input.days() * 86_400;
                    double upperRelease = input.upstreamInflow()
                            - (upper[t + 1] - upper[t]) * 1_000_000 / seconds;
                    double lowerRelease = upperRelease + input.intervalInflow()
                            - (lower[t + 1] - lower[t]) * 1_000_000 / seconds;
                    if (upperRelease < 0 || upperRelease > 200 || lowerRelease < 0 || lowerRelease > 200) {
                        feasible = false;
                        break;
                    }
                    double upperHead = (upper[t] + upper[t + 1]) / 2 - 90;
                    double lowerHead = (lower[t] + lower[t + 1]) / 2;
                    upperEnergy += 8.5 * Math.min(upperRelease, 100) * upperHead / 1000 * input.days() * 24;
                    lowerEnergy += 8.5 * Math.min(lowerRelease, 35) * lowerHead / 1000 * input.days() * 24;
                }
                if (feasible) {
                    candidates.add(new Candidate(upperEnergy + lowerEnergy, upperEnergy));
                }
            }
        }
        return candidates;
    }

    private static List<double[]> allPaths(double minLevel, int periods) {
        List<double[]> paths = new ArrayList<>();
        double[] path = new double[periods + 1];
        path[0] = minLevel + 1;
        path[periods] = minLevel + 1;
        enumerateIntermediateLevels(path, 1, minLevel, paths);
        return paths;
    }

    private static void enumerateIntermediateLevels(double[] path, int position, double minLevel,
                                                   List<double[]> paths) {
        if (position == path.length - 1) {
            paths.add(path.clone());
            return;
        }
        for (int index = 0; index < 3; index++) {
            path[position] = minLevel + index;
            enumerateIntermediateLevels(path, position + 1, minLevel, paths);
        }
    }

    private static void assertCascadeWaterBalance(CascadeProblem problem, DispatchResult result) {
        double externalInVolume = 0;
        double externalOutVolume = 0;
        for (int t = 0; t < result.periods().size(); t++) {
            CascadePeriodResult period = result.periods().get(t);
            StationOperation upper = period.upstream();
            StationOperation lower = period.downstream();
            double seconds = period.input().days() * 86_400;
            assertEquals(upper.release() + period.input().intervalInflow(), lower.inflow(), 1e-10);
            assertEquals(upper.release(), upper.turbineFlow() + upper.spill(), 1e-10);
            assertEquals(lower.release(), lower.turbineFlow() + lower.spill(), 1e-10);
            assertEquals((upper.inflow() - upper.release()) * seconds,
                    (upper.endStorage() - upper.startStorage()) * 1e8, 1e-6);
            assertEquals((lower.inflow() - lower.release()) * seconds,
                    (lower.endStorage() - lower.startStorage()) * 1e8, 1e-6);
            double systemStorageChange = (upper.endStorage() + lower.endStorage()
                    - upper.startStorage() - lower.startStorage()) * 1e8;
            assertEquals((period.input().upstreamInflow() + period.input().intervalInflow()
                    - lower.release()) * seconds, systemStorageChange, 1e-6);
            if (t > 0) {
                assertEquals(result.periods().get(t - 1).upstream().endLevel(), upper.startLevel(), 1e-12);
                assertEquals(result.periods().get(t - 1).downstream().endLevel(), lower.startLevel(), 1e-12);
            }
            externalInVolume += (period.input().upstreamInflow() + period.input().intervalInflow()) * seconds;
            externalOutVolume += lower.release() * seconds;
        }
        // 本例两库初末水位相等，因此全调度期外部来水量等于下库总出流量。
        assertEquals(externalInVolume, externalOutVolume, 1e-6);
        assertEquals(problem.periods().size(), result.periods().size());
    }

    private record Candidate(double totalEnergy, double upstreamEnergy) { }
}
