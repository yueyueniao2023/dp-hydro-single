package cascade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinearCurveTest {
    @Test
    void interpolatesWithinEachSegmentAndIncludesEndpoints() {
        LinearCurve curve = new LinearCurve(new double[]{0, 10, 30}, new double[]{2, 12, 52});
        assertEquals(0, curve.minX());
        assertEquals(30, curve.maxX());
        assertEquals(2, curve.at(0));
        assertEquals(12, curve.at(10));
        assertEquals(52, curve.at(30));
        assertEquals(7, curve.at(5), 1e-12);
        assertEquals(32, curve.at(20), 1e-12);
    }

    @Test
    void refusesExtrapolationAndInvalidCurveData() {
        LinearCurve curve = new LinearCurve(new double[]{0, 10}, new double[]{2, 12});
        assertThrows(IllegalArgumentException.class, () -> curve.at(-0.01));
        assertThrows(IllegalArgumentException.class, () -> curve.at(10.01));
        assertThrows(IllegalArgumentException.class, () -> curve.at(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearCurve(new double[]{0}, new double[]{2}));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearCurve(new double[]{0, 10}, new double[]{2}));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearCurve(new double[]{0, 0}, new double[]{2, 12}));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearCurve(new double[]{10, 0}, new double[]{2, 12}));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearCurve(new double[]{0, Double.NaN}, new double[]{2, 12}));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearCurve(new double[]{0, 10}, new double[]{2, Double.POSITIVE_INFINITY}));
    }

    @Test
    void constructorOwnsItsInputArrays() {
        double[] x = {0, 10};
        double[] y = {2, 12};
        LinearCurve curve = new LinearCurve(x, y);
        x[1] = 100;
        y[1] = 1000;
        assertEquals(7, curve.at(5), 1e-12);
    }
}
