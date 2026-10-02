import cascade.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SingleDpTest {
    private static Reservoir smallReservoir(double terminal) {
        return new Reservoir("test", 790, 830, 10, 810, terminal, 0, 200, 12, 2, 8.5, 0, 30,
                new LinearCurve(new double[]{790, 830}, new double[]{0.1, 0.13456}),
                new LinearCurve(new double[]{0, 200}, new double[]{780, 780}));
    }

    private static List<PeriodBasicData> inputs() {
        double[] inflow = {25, 7, 3, 23, 2, 10};
        List<PeriodBasicData> list = new ArrayList<>();
        for (int t = 0; t < inflow.length; t++) list.add(new PeriodBasicData(t + 1, inflow[t], t / 3 + 1, 1));
        return list;
    }

    @Test
    void monthlyExpandedStateMatchesIndependentWholePathEnumeration() {
        List<PeriodBasicData> inputs = inputs();
        for (double range : new double[]{0, 10, 20, 30}) {
            double best = Double.NEGATIVE_INFINITY;
            int feasible = 0;
            // 5个水位、6期、严格初末810m：5^5=3125条完整路径。
            for (int code = 0; code < 3125; code++) {
                double[] z = new double[7];
                z[0] = z[6] = 810;
                int remaining = code;
                for (int t = 1; t <= 5; t++) { z[t] = 790 + 10 * (remaining % 5); remaining /= 5; }
                double energy = 0;
                boolean valid = true;
                for (int t = 0; t < 6; t++) {
                    // 独立物理公式；不调用 HydroPhysics 或生产DP。
                    double release = inputs.get(t).getNaturalInFlow() - (z[t + 1] - z[t]);
                    if (release < 0 || release > 200 || Math.abs(z[t + 1] - z[t]) > 30) { valid = false; break; }
                    double head = (z[t + 1] + z[t]) / 2 - 780;
                    double turbine = Math.min(release, Math.min(12, 2000 / (8.5 * head)));
                    energy += 8.5 * turbine * head / 1000 * 24;
                }
                for (int first : new int[]{1, 4}) {
                    double min = Math.min(z[first], Math.min(z[first + 1], z[first + 2]));
                    double max = Math.max(z[first], Math.max(z[first + 1], z[first + 2]));
                    if (max - min > range) valid = false;
                }
                if (valid) { feasible++; best = Math.max(best, energy); }
            }
            DynamicProgramming dp = new DynamicProgramming(smallReservoir(810), inputs, range);
            List<PeriodResult> result = dp.solve();
            assertEquals(best, dp.optimumMwh(), 1e-8);
            assertEquals(best, result.stream().mapToDouble(r -> r.getPowerGeneration() * 10).sum(), 1e-8);
            System.out.printf(Locale.ROOT, "Single oracle range=%.0f paths=3125 feasible=%d optimum=%.9f MWh%n", range, feasible, best);
        }
    }

    @Test
    void originalWorkbookResultMeetsPhysicalAndMonthlyConstraints() throws Exception {
        ExcelReader reader = new ExcelReader();
        reader.init();
        DynamicProgramming dp = new DynamicProgramming(reader);
        List<PeriodResult> rows = dp.solve();
        assertEquals(36, rows.size());
        assertEquals(365, rows.stream().mapToDouble(PeriodResult::getDays).sum(), 0);
        assertEquals(830, rows.get(0).getStartLevel(), 0);
        assertEquals(840, rows.get(35).getEndLevel(), 0);
        double total = 0, maxResidual = 0;
        for (int t = 0; t < rows.size(); t++) {
            PeriodResult r = rows.get(t);
            double release = r.getPowerFlow() + r.getAbandonFlow();
            double residual = (r.getEndCapacity() - r.getStartCapacity()) * 1e8
                    - (r.getInflow() - release) * r.getDays() * 86400;
            maxResidual = Math.max(maxResidual, Math.abs(residual));
            assertEquals(0, residual, 1e-5);
            assertTrue(release >= 188 && release <= 5000);
            assertTrue(r.getPowerFlow() >= 0 && r.getPowerFlow() <= 5000 && r.getAbandonFlow() >= 0);
            double mw = r.getPowerGeneration() * 10 / (r.getDays() * 24);
            assertTrue(mw >= 0 && mw <= 3600 + 1e-8);
            assertEquals(8.5 * r.getPowerFlow() * ((r.getStartLevel() + r.getEndLevel()) / 2 - r.getAvgTailLevel()) / 1000, mw, 1e-8);
            assertTrue(new ConstraintChecker().checkSinglePeriodConstraint(t / 3 + 1, release, r.getStartLevel(), r.getEndLevel()));
            if (t > 0) assertEquals(rows.get(t - 1).getEndLevel(), r.getStartLevel(), 0);
            if (t % 3 == 2) assertTrue(new ConstraintChecker().checkMonthLevelChange(new double[]{
                    rows.get(t - 2).getEndLevel(), rows.get(t - 1).getEndLevel(), r.getEndLevel()}));
            total += r.getPowerGeneration() * 10;
        }
        assertEquals(dp.optimumMwh(), total, 1e-6);
        System.out.printf(Locale.ROOT, "Single default total=%.9f MWh max balance residual=%.3e m3%n", total, maxResidual);
    }

    @Test
    void missingStrictTerminalOrDryStageFails() {
        Reservoir r = smallReservoir(830);
        DynamicProgramming dp = new DynamicProgramming(r, List.of(new PeriodBasicData(1, 0, 1, 1)), 30);
        assertTrue(assertThrows(IllegalStateException.class, dp::solve).getMessage().contains("末水位"));
        Reservoir dry = new Reservoir("dry", 790, 830, 10, 790, 790, 5, 200, 12, 2, 8.5, 0, 30,
                r.levelStorage(), r.releaseTailwater());
        assertTrue(assertThrows(IllegalStateException.class,
                () -> new DynamicProgramming(dry, List.of(new PeriodBasicData(1, 0, 1, 1)), 30).solve())
                .getMessage().contains("无可行"));
    }

    @Test
    void originalTailwaterFirstRowAndExplicitTeachingExtensionAreRead() throws Exception {
        ExcelReader reader = new ExcelReader();
        reader.init();
        assertEquals(667.3, reader.getTailLevelByFlow(350), 1e-10);
        assertEquals(666.6, reader.getTailLevelByFlow(188), 1e-10);
        assertThrows(IllegalArgumentException.class, () -> reader.getTailLevelByFlow(187));
        assertEquals(8, new PeriodBasicData(6, 100, 2).getDays(), 0);
        assertEquals(11, new PeriodBasicData(3, 100, 1).getDays(), 0);
    }

    @Test
    void legacyPowerInterfaceUsesActualDurationAndCapacity() throws Exception {
        ExcelReader reader = new ExcelReader();
        reader.init();
        PowerCalculator calc = new PowerCalculator(reader);
        double[] ten = calc.calculatePowerWithAbandon(830, 830, 5000, 10);
        double[] eleven = calc.calculatePowerWithAbandon(830, 830, 5000, 11);
        assertTrue(ten[1] > 0);
        assertEquals(ten[2] * 1.1, eleven[2], 1e-8);
        assertEquals(3600 * 11 * 24 / 10, eleven[2], 1e-8);
    }
}
