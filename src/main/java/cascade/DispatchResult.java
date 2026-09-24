package cascade;

import java.util.List;

public record DispatchResult(List<CascadePeriodResult> periods, double totalEnergyMwh) {
    public DispatchResult {
        periods = List.copyOf(periods);
    }
}
