package cascade;

public record CascadePeriodResult(PeriodInput input, StationOperation upstream,
                                  StationOperation downstream) {
    public double energyMwh() { return upstream.energyMwh() + downstream.energyMwh(); }
}
