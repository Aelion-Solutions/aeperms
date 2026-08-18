package sh.aelion.aeperm.bench;

import sh.aelion.aeperm.api.AepermAPI;
import sh.aelion.aeperm.api.CalculatedUser;
import sh.aelion.aeperm.api.ContextSet;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class BenchRunner {

    public static final int DEFAULT_ITERS = 100_000;
    public static final int MAX_ITERS = 1_000_000;
    public static final int DEFAULT_RECALC_ITERS = 200;
    public static final int MAX_RECALC_ITERS = 1_000;
    public static final int WARMUP = 10_000;
    public static final int DEFAULT_PER_TICK = 5_000;
    public static final int MAX_PER_TICK = 50_000;
    public static final int DEFAULT_TICK_SECONDS = 10;
    public static final int MAX_TICK_SECONDS = 60;

    private BenchRunner() {
    }

    public record Result(String name, int iterations, long nanos, String extra) {
        public String format() {
            double ms = nanos / 1_000_000.0;
            double nsOp = iterations == 0 ? 0.0 : (double) nanos / (double) iterations;
            double ops = nanos == 0 ? 0.0 : iterations * 1_000_000_000.0 / (double) nanos;
            String body = String.format(
                    Locale.ROOT,
                    "%s  iters=%d  total=%.2fms  ns/op=%.1f  ops/s=%.0f",
                    name,
                    iterations,
                    ms,
                    nsOp,
                    ops
            );
            if (extra == null || extra.isBlank()) {
                return body;
            }
            return body + "  " + extra;
        }
    }

    public static int clampIters(int iters) {
        return Math.max(1, Math.min(iters, MAX_ITERS));
    }

    public static int clampRecalcIters(int iters) {
        return Math.max(1, Math.min(iters, MAX_RECALC_ITERS));
    }

    public static List<Result> runAll(Player player, AepermAPI api, int iters) {
        int hot = clampIters(iters);
        int recalc = clampRecalcIters(Math.min(iters, DEFAULT_RECALC_ITERS));
        List<Result> results = new ArrayList<>();
        results.addAll(bukkit(player, hot));
        results.add(api(player, api, hot));
        results.add(lookup(player, api, hot));
        results.add(recalc(player, api, recalc));
        return results;
    }

    public static List<Result> bukkit(Player player, int iters) {
        int n = clampIters(iters);
        return List.of(
                time("bukkit/exact", n, () -> player.hasPermission(SeedData.exactNode())),
                time("bukkit/wildcard", n, () -> player.hasPermission(SeedData.WILD_CHECK)),
                time("bukkit/miss", n, () -> player.hasPermission(SeedData.MISS_NODE))
        );
    }

    public static Result api(Player player, AepermAPI api, int iters) {
        int n = clampIters(iters);
        UUID uuid = player.getUniqueId();
        api.has(uuid, SeedData.exactNode());
        return time("api/has", n, () -> api.has(uuid, SeedData.exactNode()));
    }

    public static Result lookup(Player player, AepermAPI api, int iters) {
        int n = clampIters(iters);
        CalculatedUser user = api.user(player.getUniqueId()).orElseThrow();
        return time("lookup/calculated", n, () -> user.has(SeedData.WILD_CHECK));
    }

    public static Result recalc(Player player, AepermAPI api, int iters) {
        int n = clampRecalcIters(iters);
        UUID uuid = player.getUniqueId();
        CalculatedUser before = api.user(uuid).orElseThrow();
        String extra = "nodes=" + before.permissions().size() + " groups=" + before.groups().size();
        int warmup = Math.min(20, n);
        for (int i = 0; i < warmup; i++) {
            mutateAndLoad(api, uuid);
        }
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            mutateAndLoad(api, uuid);
        }
        return new Result("recalc/user", n, System.nanoTime() - start, extra);
    }

    private static void mutateAndLoad(AepermAPI api, UUID uuid) {
        api.userAdd(uuid, SeedData.RECALC_NODE, ContextSet.empty(), null);
        api.userRemove(uuid, SeedData.RECALC_NODE, ContextSet.empty());
        api.user(uuid);
    }

    public static Result time(String name, int iterations, BooleanSupplier op) {
        int warmup = Math.min(WARMUP, Math.max(0, iterations / 10));
        for (int i = 0; i < warmup; i++) {
            op.getAsBoolean();
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            op.getAsBoolean();
        }
        return new Result(name, iterations, System.nanoTime() - start, "");
    }
}
