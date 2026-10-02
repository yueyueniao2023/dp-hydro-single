package network;

import java.util.*;

/** 外部来水仅指各库新增集水区；A、D为天然来水，B、C为区间来水。 */
public record NetworkProblem(RiverNetwork network, List<Input> periods) {
    public static final List<String> IDS = List.of("A", "B", "C", "D");

    public record Input(int period, double days, Map<String, Double> localInflow) {
        public Input {
            if (period < 1 || !Double.isFinite(days) || days <= 0
                    || !Double.isFinite(days * 86400) || localInflow == null)
                throw new IllegalArgumentException("时段编号、时长或来水无效");
            localInflow = Map.copyOf(localInflow);
            for (double flow : localInflow.values())
                if (!Double.isFinite(flow) || flow < 0) throw new IllegalArgumentException("外部来水须为有限非负值");
        }
    }

    public NetworkProblem {
        if (network == null || !network.ids().equals(new HashSet<>(IDS)))
            throw new IllegalArgumentException("本例联合状态固定包含 A、B、C、D 四库");
        if (periods == null || periods.isEmpty()) throw new IllegalArgumentException("调度时段不能为空");
        periods = List.copyOf(periods);
        for (int t = 0; t < periods.size(); t++) {
            Input input = periods.get(t);
            if (input.period() != t + 1 || !input.localInflow().keySet().equals(network.ids()))
                throw new IllegalArgumentException("时段必须从1连续编号，且包含四库各自的外部来水");
        }
    }
}
