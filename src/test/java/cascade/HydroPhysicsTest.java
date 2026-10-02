package cascade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HydroPhysicsTest {
    @Test
    void neverClampsEvenSmallNegativeOrOutOfBoundsRelease() {
        Reservoir normal = reservoir(0, 200, 100, 1000, 0, 2, 90);
        double tooLittle = 1_000_000.0 / 86400 - 1e-10;
        assertNull(HydroPhysics.evaluate(normal, 100, 101, tooLittle, 1, 102));
        assertNull(HydroPhysics.evaluate(normal, 101, 101, 200 + 1e-10, 1, 102));
    }
    private static Reservoir reservoir(double minRelease, double maxRelease,
                                       double turbineCapacity, double installedPower,
                                       double headLoss, double maxChange, double tailwater) {
        return new Reservoir("test", 100, 102, 1, 101, 101,
                minRelease, maxRelease, turbineCapacity, installedPower,
                8.5, headLoss, maxChange,
                new LinearCurve(new double[]{100, 102}, new double[]{0, 0.02}),
                new LinearCurve(new double[]{0, 200}, new double[]{tailwater, tailwater}));
    }

    @Test
    void handCalculatedBalanceHeadPowerAndEnergyUseExplicitUnits() {
        Reservoir reservoir = new Reservoir("sloped tailwater", 100, 102, 1, 101, 101,
                0, 200, 100, 1000, 8.5, 1, 2,
                new LinearCurve(new double[]{100, 102}, new double[]{0, 0.02}),
                new LinearCurve(new double[]{0, 200}, new double[]{90, 92}));
        StationOperation operation = HydroPhysics.evaluate(reservoir, 101, 102, 40, 2, 102);
        assertNotNull(operation);
        double release = 40 - 1_000_000.0 / (2 * 86_400);
        double tailwater = 90 + 0.01 * release;
        double head = 101.5 - tailwater - 1;
        double power = 8.5 * release * head / 1000;
        assertEquals(0.01, operation.startStorage(), 1e-12);
        assertEquals(0.02, operation.endStorage(), 1e-12);
        assertEquals(release, operation.release(), 1e-10);
        assertEquals(tailwater, operation.tailwater(), 1e-10);
        assertEquals(head, operation.head(), 1e-10);
        assertEquals(power, operation.powerMw(), 1e-10);
        assertEquals(power * 48, operation.energyMwh(), 1e-10);
        assertEquals(0, operation.spill(), 1e-10);
        assertEquals(0, operation.balanceResidualM3(), 1e-6);
    }

    @Test
    void turbineFlowLimitCanCauseSpillBelowInstalledPower() {
        Reservoir reservoir = reservoir(0, 200, 20, 1000, 0, 2, 90);
        StationOperation operation = HydroPhysics.evaluate(reservoir, 101, 101, 50, 1, 102);
        assertNotNull(operation);
        assertEquals(50, operation.release(), 1e-12);
        assertEquals(20, operation.turbineFlow(), 1e-12);
        assertEquals(30, operation.spill(), 1e-12);
        assertEquals(1.87, operation.powerMw(), 1e-12);
        assertTrue(operation.powerMw() < reservoir.installedPowerMw());
    }

    @Test
    void installedPowerLimitReducesTurbineFlowAndIncreasesSpill() {
        Reservoir reservoir = reservoir(0, 200, 100, 0.935, 0, 2, 90);
        StationOperation operation = HydroPhysics.evaluate(reservoir, 101, 101, 50, 1, 102);
        assertNotNull(operation);
        // 0.935 MW / (8.5 * 11 / 1000) = 10 m3/s.
        assertEquals(0.935, operation.powerMw(), 1e-12);
        assertEquals(10, operation.turbineFlow(), 1e-10);
        assertEquals(40, operation.spill(), 1e-10);
        assertEquals(operation.release(), operation.turbineFlow() + operation.spill(), 1e-10);
    }

    @Test
    void tailwaterDependsOnTotalReleaseIncludingSpill() {
        Reservoir reservoir = new Reservoir("spill tailwater", 100, 102, 1, 101, 101,
                0, 200, 20, 1000, 8.5, 0, 2,
                new LinearCurve(new double[]{100, 102}, new double[]{0, 0.02}),
                new LinearCurve(new double[]{0, 200}, new double[]{90, 100}));
        StationOperation operation = HydroPhysics.evaluate(reservoir, 101, 101, 50, 1, 102);
        assertNotNull(operation);
        assertEquals(92.5, operation.tailwater(), 1e-12);
        assertEquals(8.5, operation.head(), 1e-12);
        assertEquals(1.445, operation.powerMw(), 1e-12);
    }

    @Test
    void durationChangesReleaseForTheSameStorageChangeAndScalesEnergy() {
        Reservoir reservoir = reservoir(0, 200, 100, 1000, 0, 2, 90);
        StationOperation oneDay = HydroPhysics.evaluate(reservoir, 101, 102, 40, 1, 102);
        StationOperation twoDays = HydroPhysics.evaluate(reservoir, 101, 102, 40, 2, 102);
        assertNotNull(oneDay);
        assertNotNull(twoDays);
        assertEquals(40 - 1_000_000.0 / 86_400, oneDay.release(), 1e-10);
        assertEquals(40 - 1_000_000.0 / 172_800, twoDays.release(), 1e-10);
        assertEquals(twoDays.powerMw() * 48, twoDays.energyMwh(), 1e-10);
        StationOperation steadyOne = HydroPhysics.evaluate(reservoir, 101, 101, 40, 1, 102);
        StationOperation steadyTwo = HydroPhysics.evaluate(reservoir, 101, 101, 40, 2, 102);
        assertEquals(steadyOne.energyMwh() * 2, steadyTwo.energyMwh(), 1e-10);
    }

    @Test
    void infeasibleReleasesFloodLimitsAndLevelChangesRejectTransition() {
        Reservoir normal = reservoir(0, 200, 100, 1000, 0, 2, 90);
        assertNull(HydroPhysics.evaluate(normal, 100, 102, 0, 1, 102));
        Reservoir releaseBounds = reservoir(10, 80, 100, 1000, 0, 2, 90);
        assertNull(HydroPhysics.evaluate(releaseBounds, 101, 101, 9, 1, 102));
        assertNull(HydroPhysics.evaluate(releaseBounds, 101, 101, 81, 1, 102));
        assertNotNull(HydroPhysics.evaluate(releaseBounds, 101, 101, 10, 1, 102));
        assertNotNull(HydroPhysics.evaluate(releaseBounds, 101, 101, 80, 1, 102));
        // 汛限必须同时约束时段初、末；不能只检查期末。
        assertNull(HydroPhysics.evaluate(normal, 102, 101, 40, 1, 101));
        assertNull(HydroPhysics.evaluate(normal, 101, 102, 40, 1, 101));
        Reservoir limitedChange = reservoir(0, 200, 100, 1000, 0, 1, 90);
        assertNull(HydroPhysics.evaluate(limitedChange, 100, 102, 40, 1, 102));
        assertNotNull(HydroPhysics.evaluate(limitedChange, 101, 102, 40, 1, 102));
    }

    @Test
    void zeroAndNegativeNetHeadStillReleaseAllWaterWithoutGeneration() {
        for (double headLoss : new double[]{11, 15}) {
            StationOperation operation = HydroPhysics.evaluate(
                    reservoir(0, 200, 100, 1000, headLoss, 2, 90),
                    101, 101, 40, 1, 102);
            assertNotNull(operation);
            assertEquals(40, operation.release(), 1e-12);
            assertEquals(0, operation.turbineFlow(), 1e-12);
            assertEquals(40, operation.spill(), 1e-12);
            assertEquals(0, operation.powerMw(), 1e-12);
            assertEquals(0, operation.energyMwh(), 1e-12);
            assertEquals(0, operation.balanceResidualM3(), 1e-6);
        }
    }

    @Test
    void positiveReleaseRequiresPositiveGrossHeadWithoutPumping() {
        for (double tailwater : new double[]{101, 105}) {
            assertNull(HydroPhysics.evaluate(
                    reservoir(0, 200, 100, 1000, 0, 2, tailwater),
                    101, 101, 40, 1, 102));
        }
    }
}
