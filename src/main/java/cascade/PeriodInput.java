package cascade;

/** 一个时段的外部输入：下库只输入区间来水，不预先固定下库总入流。 */
public record PeriodInput(int period, double days, double upstreamInflow,
                          double intervalInflow, double upstreamLevelLimit,
                          double downstreamLevelLimit) {
    public PeriodInput {
        if (period < 1 || !Double.isFinite(days) || days <= 0
                || !Double.isFinite(days * 86400.0)
                || !Double.isFinite(upstreamInflow) || upstreamInflow < 0
                || !Double.isFinite(intervalInflow) || intervalInflow < 0
                || !Double.isFinite(upstreamLevelLimit) || !Double.isFinite(downstreamLevelLimit)) {
            throw new IllegalArgumentException("时段编号、天数、来水或水位上限不合法");
        }
    }

    public double seconds() { return days * 86400.0; }
    public double hours() { return days * 24.0; }
}
