package network;

import cascade.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkDpTest {
    private static final double[] BASE = {200, 140, 90, 180};
    private static final double[] SLOPE_M3 = {864000, 864000, 864000, 432000};
    private static final double[] TAIL = {150, 100, 60, 150};
    private static final double[] TURBINE = {60, 90, 100, 30};
    private static final double[] CAPACITY = {25, 32, 25, 10};
    private static final double[] MIN = {5, 8, 10, 2};
    private static final double[] MAX = {200, 350, 400, 120};
    private static final List<RiverNetwork.Edge> EDGES = List.of(
            new RiverNetwork.Edge("A", "B"), new RiverNetwork.Edge("D", "B"), new RiverNetwork.Edge("B", "C"));

    private static List<RiverNetwork.Node> nodes(double terminal, double minReleaseScale) {
        List<RiverNetwork.Node> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String id = NetworkProblem.IDS.get(i);
            Reservoir r = new Reservoir(id, BASE[i], BASE[i] + 1, 1, BASE[i], BASE[i] + terminal,
                    MIN[i] * minReleaseScale, MAX[i], TURBINE[i], CAPACITY[i], 8.829, 0, 1,
                    new LinearCurve(new double[]{BASE[i], BASE[i] + 1}, new double[]{0.1, 0.1 + SLOPE_M3[i] / 1e8}),
                    new LinearCurve(new double[]{0, MAX[i]}, new double[]{TAIL[i], TAIL[i]}));
            nodes.add(new RiverNetwork.Node(id, i == 3 ? "tributary" : "mainstem", r));
        }
        return nodes;
    }

    private static NetworkProblem small(double[] days) {
        double[] a = {20, 80, 20}, d = {10, 45, 10};
        List<NetworkProblem.Input> inputs = new ArrayList<>();
        for (int t = 0; t < days.length; t++)
            inputs.add(new NetworkProblem.Input(t + 1, days[t], Map.of("A", a[t], "B", 5.0, "C", 3.0, "D", d[t])));
        return new NetworkProblem(new RiverNetwork(nodes(0, 1), EDGES), inputs);
    }

    @Test
    void jointDpMatchesIndependentCompletePathsIncludingVariableDurations() {
        for (double[] days : List.of(new double[]{1, 1, 1}, new double[]{1, 0.5, 2})) {
            NetworkProblem problem = small(days);
            // 2^4=16个状态，两处中间边界，16^2=256条完整联合路径。
            // 不调用HydroPhysics/NetworkDp.evaluate，不复用DP递推或前驱。
            double best = Double.NEGATIVE_INFINITY;
            int feasible = 0;
            for (int first = 0; first < 16; first++) {
                for (int second = 0; second < 16; second++) {
                    int[] path = {0, first, second, 0};
                    double energy = independentPathEnergy(problem, path);
                    if (Double.isFinite(energy)) { feasible++; best = Math.max(best, energy); }
                }
            }
            assertTrue(feasible > 0);
            NetworkDp.Result result = new NetworkDp(problem).solve();
            assertEquals(best, result.totalEnergyMwh(), 1e-8);
            System.out.printf(Locale.ROOT, "Network oracle days=%s paths=256 feasible=%d optimum=%.12f MWh%n",
                    Arrays.toString(days), feasible, best);
        }
    }

    private static double independentPathEnergy(NetworkProblem p, int[] path) {
        double sum = 0;
        for (int t = 0; t < 3; t++) {
            double[] release = new double[4];
            // 独立手写本题拓扑 A,D,B,C；不能让生产拓扑错误与测试相互抵消。
            for (int i : new int[]{0, 3, 1, 2}) {
                double z0 = BASE[i] + ((path[t] >> i) & 1);
                double z1 = BASE[i] + ((path[t + 1] >> i) & 1);
                double inflow = p.periods().get(t).localInflow().get(NetworkProblem.IDS.get(i));
                if (i == 1) inflow += release[0] + release[3];
                if (i == 2) inflow += release[1];
                release[i] = inflow - (z1 - z0) * SLOPE_M3[i] / (p.periods().get(t).days() * 86400);
                if (release[i] < MIN[i] || release[i] > MAX[i]) return Double.NEGATIVE_INFINITY;
                double head = (z0 + z1) / 2 - TAIL[i];
                double turbine = Math.min(release[i], Math.min(TURBINE[i], CAPACITY[i] * 1000 / (8.829 * head)));
                sum += 8.829 * turbine * head / 1000 * p.periods().get(t).days() * 24;
            }
        }
        return sum;
    }

    @Test
    void shippedExampleConservesWaterMeetsAllBoundsAndSumsEnergy() throws Exception {
        NetworkProblem p = NetworkDataReader.read(Path.of("data/network"));
        NetworkDp dp = new NetworkDp(p);
        assertEquals(81, dp.stateCount());
        assertEquals(39366, dp.candidateUpperBound());
        NetworkDp.Result result = dp.solve();
        double total = 0, spillA = 0, spillD = 0, maxResidual = 0;
        for (int t = 0; t < result.periods().size(); t++) {
            NetworkDp.Stage stage = result.periods().get(t);
            Map<String, StationOperation> ops = stage.operations();
            assertEquals(ops.get("A").release() + ops.get("D").release() + stage.input().localInflow().get("B"), ops.get("B").inflow(), 1e-10);
            assertEquals(ops.get("B").release() + stage.input().localInflow().get("C"), ops.get("C").inflow(), 1e-10);
            assertEquals(stage.input().localInflow().get("A"), ops.get("A").inflow(), 1e-10);
            assertEquals(stage.input().localInflow().get("D"), ops.get("D").inflow(), 1e-10);
            double deltaVolume = 0;
            for (String id : NetworkProblem.IDS) {
                StationOperation op = ops.get(id);
                Reservoir r = p.network().node(id).reservoir();
                double residual = (op.endStorage() - op.startStorage()) * 1e8
                        - (op.inflow() - op.release()) * stage.input().days() * 86400;
                maxResidual = Math.max(maxResidual, Math.abs(residual));
                assertEquals(0, residual, 1e-6);
                assertEquals(op.release(), op.turbineFlow() + op.spill(), 1e-10);
                assertTrue(op.release() >= r.minRelease() && op.release() <= r.maxRelease());
                assertTrue(op.turbineFlow() >= 0 && op.turbineFlow() <= r.maxTurbineFlow() + 1e-10);
                assertTrue(op.spill() >= 0 && op.powerMw() <= r.installedPowerMw() + 1e-10);
                assertTrue(op.startLevel() >= r.minLevel() && op.startLevel() <= r.maxLevel());
                assertTrue(op.endLevel() >= r.minLevel() && op.endLevel() <= r.maxLevel());
                assertEquals(op.powerMw(), 9.81 * 0.9 * op.turbineFlow() * op.head() / 1000, 1e-10);
                assertEquals(op.energyMwh(), op.powerMw() * stage.input().days() * 24, 1e-9);
                assertEquals(t == 0 ? r.initialLevel() : result.periods().get(t - 1).operations().get(id).endLevel(), op.startLevel(), 0);
                if (t == result.periods().size() - 1) assertEquals(r.terminalLevel(), op.endLevel(), 0);
                total += op.energyMwh();
                deltaVolume += (op.endStorage() - op.startStorage()) * 1e8;
            }
            double external = stage.input().localInflow().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(deltaVolume, (external - ops.get("C").release()) * stage.input().days() * 86400, 1e-6);
            spillA += ops.get("A").spill();
            spillD += ops.get("D").spill();
        }
        assertTrue(spillA > 0 && spillD > 0, "两个上游都应出现弃水，用于验证汇流");
        assertEquals(total, result.totalEnergyMwh(), 1e-8);
        assertEquals(8697.303120, total, 1e-6);
        System.out.printf(Locale.ROOT, "Network default total=%.9f MWh max balance residual=%.3e m3%n", total, maxResidual);
    }

    @Test
    void topologyIsIndependentOfNodeOrderAndRiverLabels() {
        NetworkProblem base = small(new double[]{1, 1, 1});
        List<RiverNetwork.Node> reversed = new ArrayList<>();
        for (RiverNetwork.Node n : nodes(0, 1)) reversed.add(new RiverNetwork.Node(n.id(), "same-label", n.reservoir()));
        Collections.reverse(reversed);
        NetworkProblem changed = new NetworkProblem(new RiverNetwork(reversed, EDGES), base.periods());
        assertEquals(new NetworkDp(base).solve().totalEnergyMwh(), new NetworkDp(changed).solve().totalEnergyMwh(), 1e-9);
        assertEquals(List.of("B"), changed.network().upstreamOf("C"));
        assertEquals(Set.of("A", "D"), new HashSet<>(changed.network().upstreamOf("B")));
    }

    @Test
    void changingConnectionsActuallyChangesInflow() {
        NetworkProblem base = small(new double[]{1});
        RiverNetwork alternate = new RiverNetwork(nodes(0, 1), List.of(
                new RiverNetwork.Edge("A", "B"), new RiverNetwork.Edge("B", "C"), new RiverNetwork.Edge("D", "C")));
        NetworkDp dp = new NetworkDp(new NetworkProblem(alternate, base.periods()));
        NetworkDp.State state = new NetworkDp.State(200, 140, 90, 180);
        NetworkDp.Stage stage = dp.evaluate(0, state, state);
        assertEquals(25, stage.operations().get("B").inflow(), 0);
        assertEquals(38, stage.operations().get("C").inflow(), 0);
    }

    @Test
    void rejectsCyclesUnknownDuplicateAndSplittingConnections() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new RiverNetwork(nodes(0, 1),
                List.of(new RiverNetwork.Edge("A", "B"), new RiverNetwork.Edge("B", "A")))).getMessage().contains("环"));
        assertThrows(IllegalArgumentException.class, () -> new RiverNetwork(nodes(0, 1), List.of(new RiverNetwork.Edge("A", "A"))));
        assertThrows(IllegalArgumentException.class, () -> new RiverNetwork(nodes(0, 1), List.of(new RiverNetwork.Edge("X", "B"))));
        assertThrows(IllegalArgumentException.class, () -> new RiverNetwork(nodes(0, 1),
                List.of(new RiverNetwork.Edge("A", "B"), new RiverNetwork.Edge("A", "B"))));
        assertThrows(IllegalArgumentException.class, () -> new RiverNetwork(nodes(0, 1),
                List.of(new RiverNetwork.Edge("A", "B"), new RiverNetwork.Edge("A", "C"))));
    }

    @Test
    void unreachableStageAndStrictTerminalGiveClearMessages() {
        List<NetworkProblem.Input> dry = List.of(new NetworkProblem.Input(1, 1,
                Map.of("A", 0.0, "B", 0.0, "C", 0.0, "D", 0.0)));
        NetworkProblem stageFailure = new NetworkProblem(new RiverNetwork(nodes(0, 1), EDGES), dry);
        assertTrue(assertThrows(IllegalStateException.class, () -> new NetworkDp(stageFailure).solve()).getMessage().contains("无可行"));
        NetworkProblem terminalFailure = new NetworkProblem(new RiverNetwork(nodes(1, 0), EDGES), dry);
        assertTrue(assertThrows(IllegalStateException.class, () -> new NetworkDp(terminalFailure).solve()).getMessage().contains("目标末水位"));
    }

    @Test
    void rejectsNegativeOrMissingExternalInflowAndInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkProblem.Input(1, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new NetworkProblem.Input(1, 1, Map.of("A", -1.0)));
        assertThrows(IllegalArgumentException.class, () -> new NetworkProblem(new RiverNetwork(nodes(0, 1), EDGES),
                List.of(new NetworkProblem.Input(1, 1, Map.of("A", 10.0)))));
    }

    @Test
    void csvContainsAllTwentyFourStationPeriods(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        NetworkDp.Result result = new NetworkDp(NetworkDataReader.read(Path.of("data/network"))).solve();
        Path output = temp.resolve("nested/dispatch.csv");
        NetworkCsvWriter.write(output, result);
        List<String> rows = Files.readAllLines(output);
        assertEquals(25, rows.size());
        double sum = 0;
        for (String row : rows.subList(1, rows.size())) {
            String[] cells = row.split(",");
            assertEquals(17, cells.length);
            sum += Double.parseDouble(cells[15]);
        }
        assertEquals(result.totalEnergyMwh(), sum, 1e-8);
    }
}
