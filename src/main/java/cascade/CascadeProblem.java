package cascade;

import java.util.List;

/** 一个确定性、无传播时滞的两库梯级调度问题。 */
public record CascadeProblem(Reservoir upstream, Reservoir downstream, List<PeriodInput> periods) {
    public CascadeProblem {
        if (upstream == null || downstream == null || periods == null || periods.isEmpty()) {
            throw new IllegalArgumentException("两库参数和时段输入不能为空");
        }
        if (periods.stream().anyMatch(p -> p == null)) {
            throw new IllegalArgumentException("时段输入不能包含空项");
        }
        periods = List.copyOf(periods);
        for (int i = 0; i < periods.size(); i++) {
            PeriodInput p = periods.get(i);
            if (p.period() != i + 1) throw new IllegalArgumentException("时段编号必须从1开始连续递增");
            if (p.upstreamLevelLimit() < upstream.minLevel() || p.upstreamLevelLimit() > upstream.maxLevel()
                    || p.downstreamLevelLimit() < downstream.minLevel()
                    || p.downstreamLevelLimit() > downstream.maxLevel()) {
                throw new IllegalArgumentException("第" + p.period() + "期水位上限超出水库允许范围");
            }
        }
    }
}
