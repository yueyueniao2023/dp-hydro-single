package cascade;

/** 分段线性插值；禁止静默外推，防止曲线覆盖不足被掩盖。 */
public final class LinearCurve {
    private final double[] x;
    private final double[] y;

    public LinearCurve(double[] x, double[] y) {
        if (x == null || y == null || x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("曲线需要至少两个点，且横纵坐标数量相同");
        }
        this.x = x.clone();
        this.y = y.clone();
        for (int i = 0; i < x.length; i++) {
            if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])
                    || (i > 0 && x[i] <= x[i - 1])) {
                throw new IllegalArgumentException("曲线数据必须有限，横坐标必须严格递增");
            }
        }
    }

    public double minX() { return x[0]; }
    public double maxX() { return x[x.length - 1]; }

    public double at(double value) {
        if (!Double.isFinite(value) || value < minX() || value > maxX()) {
            throw new IllegalArgumentException("插值横坐标 " + value + " 超出曲线范围 ["
                    + minX() + ", " + maxX() + "]");
        }
        int index = java.util.Arrays.binarySearch(x, value);
        if (index >= 0) return y[index];
        int right = -index - 1;
        int left = right - 1;
        double fraction = (value - x[left]) / (x[right] - x[left]);
        return y[left] + fraction * (y[right] - y[left]);
    }

    void requireIncreasingValues(boolean strict, String label) {
        for (int i = 1; i < y.length; i++) {
            if (strict ? y[i] <= y[i - 1] : y[i] < y[i - 1]) {
                throw new IllegalArgumentException(label + (strict ? "必须严格递增" : "不能递减"));
            }
        }
    }
}
