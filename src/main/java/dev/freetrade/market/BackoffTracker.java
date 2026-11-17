package dev.freetrade.market;

/**
 * Anti "storage in the association" rule. When a player buys back an item they sold within
 * {@code windowMs}, a round trip is counted. Once round trips reach {@code threshold}, the penalty level
 * grows by one per further round trip; the buy price is multiplied by (1 + percent/100 * level).
 * Round trips reset and the level drops by one for every full window without round trips.
 * State is a small value object so it can live in memory and in the database.
 */
public final class BackoffTracker {
    public record Settings(boolean enabled, int threshold, long windowMs, double percentPerLevel, int maxLevel) {}

    public static final class State {
        public long lastSellMs;
        public long lastRoundTripMs;
        public int roundTrips;
        public int level;

        public State() {}

        public State(long lastSellMs, long lastRoundTripMs, int roundTrips, int level) {
            this.lastSellMs = lastSellMs;
            this.lastRoundTripMs = lastRoundTripMs;
            this.roundTrips = roundTrips;
            this.level = level;
        }
    }

    private BackoffTracker() {}

    /** Applies decay for the time passed without round trips. */
    public static void decay(State s, Settings cfg, long now) {
        if (cfg.windowMs() <= 0 || s.lastRoundTripMs == 0) return;
        long idle = now - s.lastRoundTripMs;
        if (idle <= cfg.windowMs()) return;
        long windows = idle / cfg.windowMs();
        s.roundTrips = 0;
        s.level = (int) Math.max(0, s.level - windows);
        s.lastRoundTripMs = s.level == 0 ? 0 : s.lastRoundTripMs + windows * cfg.windowMs();
    }

    public static void onSell(State s, long now) {
        s.lastSellMs = now;
    }

    /** Multiplier to apply to this buy; also records the round trip. */
    public static double onBuy(State s, Settings cfg, long now) {
        if (!cfg.enabled()) return 1.0;
        decay(s, cfg, now);
        if (s.lastSellMs != 0 && now - s.lastSellMs <= cfg.windowMs()) {
            s.roundTrips++;
            s.lastRoundTripMs = now;
            if (s.roundTrips >= cfg.threshold()) s.level = Math.min(cfg.maxLevel(), s.level + 1);
        }
        return multiplier(s, cfg);
    }

    /**
     * Records a trade where rounding to whole U.M. favoured the player (e.g. selling one by one an item worth
     * 1.5 U.M. to get 2 each). It counts like a round trip, so abusing it raises this player's backoff level
     * for this item, which then penalises both selling and buying.
     */
    public static void onRoundingGain(State s, Settings cfg, long now) {
        if (!cfg.enabled()) return;
        decay(s, cfg, now);
        s.roundTrips++;
        s.lastRoundTripMs = now;
        if (s.roundTrips >= cfg.threshold()) s.level = Math.min(cfg.maxLevel(), s.level + 1);
    }

    /** Current multiplier after decay, without predicting the next trade. */
    public static double current(State s, Settings cfg, long now) {
        if (!cfg.enabled()) return 1.0;
        State copy = new State(s.lastSellMs, s.lastRoundTripMs, s.roundTrips, s.level);
        decay(copy, cfg, now);
        return multiplier(copy, cfg);
    }

    /** Multiplier without recording anything (quotes). */
    public static double peek(State s, Settings cfg, long now) {
        if (!cfg.enabled()) return 1.0;
        State copy = new State(s.lastSellMs, s.lastRoundTripMs, s.roundTrips, s.level);
        decay(copy, cfg, now);
        int level = copy.level;
        if (copy.lastSellMs != 0 && now - copy.lastSellMs <= cfg.windowMs() && copy.roundTrips + 1 >= cfg.threshold()) {
            level = Math.min(cfg.maxLevel(), level + 1);
        }
        return 1.0 + cfg.percentPerLevel() / 100.0 * level;
    }

    public static double multiplier(State s, Settings cfg) {
        return 1.0 + cfg.percentPerLevel() / 100.0 * s.level;
    }
}
