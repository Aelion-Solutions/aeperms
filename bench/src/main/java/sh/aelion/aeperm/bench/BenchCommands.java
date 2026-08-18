package sh.aelion.aeperm.bench;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public final class BenchCommands {

    private BenchCommands() {
    }

    public static void register(AepermBenchPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralCommandNode<CommandSourceStack> root = Commands.literal("apbench")
                    .requires(stack -> stack.getSender().hasPermission("aeperm.admin"))
                    .executes(ctx -> usage(ctx.getSource().getSender()))
                    .then(Commands.literal("seed")
                            .executes(ctx -> seed(plugin, ctx, SeedData.DEFAULT_NODES))
                            .then(Commands.argument("nodes", IntegerArgumentType.integer(1, SeedData.MAX_NODES))
                                    .executes(ctx -> seed(plugin, ctx, IntegerArgumentType.getInteger(ctx, "nodes")))))
                    .then(Commands.literal("run")
                            .executes(ctx -> playerBench(plugin, ctx, BenchRunner.DEFAULT_ITERS, (player, iters) -> {
                                warnMainThread(player);
                                return BenchRunner.runAll(player, plugin.api(), iters);
                            }))
                            .then(Commands.argument("iters", IntegerArgumentType.integer(1, BenchRunner.MAX_ITERS))
                                    .executes(ctx -> playerBench(plugin, ctx, IntegerArgumentType.getInteger(ctx, "iters"), (player, iters) -> {
                                        warnMainThread(player);
                                        return BenchRunner.runAll(player, plugin.api(), iters);
                                    }))))
                    .then(Commands.literal("bukkit")
                            .executes(ctx -> playerBench(plugin, ctx, BenchRunner.DEFAULT_ITERS, (player, iters) -> {
                                warnMainThread(player);
                                return BenchRunner.bukkit(player, iters);
                            }))
                            .then(Commands.argument("iters", IntegerArgumentType.integer(1, BenchRunner.MAX_ITERS))
                                    .executes(ctx -> playerBench(plugin, ctx, IntegerArgumentType.getInteger(ctx, "iters"), (player, iters) -> {
                                        warnMainThread(player);
                                        return BenchRunner.bukkit(player, iters);
                                    }))))
                    .then(Commands.literal("api")
                            .executes(ctx -> playerBench(plugin, ctx, BenchRunner.DEFAULT_ITERS,
                                    (player, iters) -> List.of(BenchRunner.api(player, plugin.api(), iters))))
                            .then(Commands.argument("iters", IntegerArgumentType.integer(1, BenchRunner.MAX_ITERS))
                                    .executes(ctx -> playerBench(plugin, ctx, IntegerArgumentType.getInteger(ctx, "iters"),
                                            (player, iters) -> List.of(BenchRunner.api(player, plugin.api(), iters))))))
                    .then(Commands.literal("lookup")
                            .executes(ctx -> playerBench(plugin, ctx, BenchRunner.DEFAULT_ITERS,
                                    (player, iters) -> List.of(BenchRunner.lookup(player, plugin.api(), iters))))
                            .then(Commands.argument("iters", IntegerArgumentType.integer(1, BenchRunner.MAX_ITERS))
                                    .executes(ctx -> playerBench(plugin, ctx, IntegerArgumentType.getInteger(ctx, "iters"),
                                            (player, iters) -> List.of(BenchRunner.lookup(player, plugin.api(), iters))))))
                    .then(Commands.literal("recalc")
                            .executes(ctx -> playerBench(plugin, ctx, BenchRunner.DEFAULT_RECALC_ITERS,
                                    (player, iters) -> List.of(BenchRunner.recalc(player, plugin.api(), iters))))
                            .then(Commands.argument("iters", IntegerArgumentType.integer(1, BenchRunner.MAX_RECALC_ITERS))
                                    .executes(ctx -> playerBench(plugin, ctx, IntegerArgumentType.getInteger(ctx, "iters"),
                                            (player, iters) -> List.of(BenchRunner.recalc(player, plugin.api(), iters))))))
                    .then(Commands.literal("tick")
                            .executes(ctx -> tick(plugin, ctx, BenchRunner.DEFAULT_PER_TICK, BenchRunner.DEFAULT_TICK_SECONDS))
                            .then(Commands.argument("perTick", IntegerArgumentType.integer(1, BenchRunner.MAX_PER_TICK))
                                    .executes(ctx -> tick(plugin, ctx, IntegerArgumentType.getInteger(ctx, "perTick"), BenchRunner.DEFAULT_TICK_SECONDS))
                                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, BenchRunner.MAX_TICK_SECONDS))
                                            .executes(ctx -> tick(
                                                    plugin,
                                                    ctx,
                                                    IntegerArgumentType.getInteger(ctx, "perTick"),
                                                    IntegerArgumentType.getInteger(ctx, "seconds"))))))
                    .then(Commands.literal("stop")
                            .executes(ctx -> stop(plugin, ctx.getSource().getSender())))
                    .then(Commands.literal("cleanup")
                            .executes(ctx -> cleanup(plugin, ctx)))
                    .build();
            event.registrar().register(root, "AePerm bench", List.of("aepermbench"));
        });
    }

    private static int usage(CommandSender sender) {
        sender.sendMessage(info("AePerm bench (test servers only). Mutates storage."));
        sender.sendMessage(info("/apbench seed [nodes]"));
        sender.sendMessage(info("/apbench run [iters] | bukkit | api | lookup | recalc"));
        sender.sendMessage(info("/apbench tick [perTick] [seconds] | stop"));
        sender.sendMessage(info("/apbench cleanup"));
        return 1;
    }

    private static int seed(AepermBenchPlugin plugin, CommandContext<CommandSourceStack> ctx, int nodes) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        player.sendMessage(info("Seeding " + nodes + " user nodes. This mutates AePerm storage."));
        java.util.UUID uuid = player.getUniqueId();
        String world = player.getWorld().getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SeedData.Report report = plugin.seed().seed(uuid, nodes, world);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String line = String.format(
                            Locale.ROOT,
                            "Seeded userNodes=%d groups=%d effectiveNodes=%d",
                            report.userNodes(),
                            report.groups(),
                            report.effectiveNodes()
                    );
                    tell(plugin, player, line, NamedTextColor.GREEN);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> tell(plugin, player, "Seed failed: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("Seed failed: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int cleanup(AepermBenchPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        player.sendMessage(info("Cleaning bench groups and aeperm.bench.* nodes."));
        java.util.UUID uuid = player.getUniqueId();
        String world = player.getWorld().getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                SeedData.Report report = plugin.seed().cleanup(uuid, world);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String line = String.format(
                            Locale.ROOT,
                            "Cleanup done. removedGroups=%d remainingEffective=%d",
                            report.groups(),
                            report.effectiveNodes()
                    );
                    tell(plugin, player, line, NamedTextColor.GREEN);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> tell(plugin, player, "Cleanup failed: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("Cleanup failed: " + e.getMessage());
            }
        });
        return 1;
    }

    private static int playerBench(
            AepermBenchPlugin plugin,
            CommandContext<CommandSourceStack> ctx,
            int iters,
            BiFunction<Player, Integer, List<BenchRunner.Result>> run
    ) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        if (!player.hasPermission(SeedData.exactNode())) {
            player.sendMessage(info("Hint: /apbench seed first for exact-hit numbers."));
        }
        List<BenchRunner.Result> results = run.apply(player, iters);
        for (BenchRunner.Result result : results) {
            tell(plugin, player, result.format(), NamedTextColor.GREEN);
        }
        return 1;
    }

    private static int tick(AepermBenchPlugin plugin, CommandContext<CommandSourceStack> ctx, int perTick, int seconds) {
        Player player = player(ctx);
        if (player == null) {
            return 0;
        }
        if (plugin.tickRunning()) {
            player.sendMessage(err("Tick bench already running. /apbench stop"));
            return 0;
        }
        warnMainThread(player);
        int checks = Math.max(1, Math.min(perTick, BenchRunner.MAX_PER_TICK));
        int duration = Math.max(1, Math.min(seconds, BenchRunner.MAX_TICK_SECONDS));
        int ticks = duration * 20;
        AtomicLong completed = new AtomicLong();
        double[] startTps = Bukkit.getTPS().clone();
        double startMspt = Bukkit.getServer().getAverageTickTime();
        player.sendMessage(info("Tick stress " + checks + "/tick for " + duration + "s. /apbench stop to cancel."));
        plugin.startTick(Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int remaining = ticks;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    plugin.stopTick();
                    return;
                }
                for (int i = 0; i < checks; i++) {
                    player.hasPermission(SeedData.exactNode());
                }
                completed.addAndGet(checks);
                remaining--;
                if (remaining <= 0) {
                    plugin.stopTick();
                    reportTick(plugin, player, completed.get(), startTps, startMspt);
                }
            }
        }, 1L, 1L));
        return 1;
    }

    private static int stop(AepermBenchPlugin plugin, CommandSender sender) {
        if (!plugin.tickRunning()) {
            sender.sendMessage(info("No tick bench running."));
            return 1;
        }
        plugin.stopTick();
        tell(plugin, sender, "Tick bench stopped.", NamedTextColor.GREEN);
        return 1;
    }

    private static void reportTick(AepermBenchPlugin plugin, Player player, long checks, double[] startTps, double startMspt) {
        double[] tps = Bukkit.getTPS();
        double mspt = Bukkit.getServer().getAverageTickTime();
        String line = String.format(
                Locale.ROOT,
                "tick  checks=%d  tps=%.2f->%.2f  mspt=%.2f->%.2f",
                checks,
                startTps[0],
                tps[0],
                startMspt,
                mspt
        );
        tell(plugin, player, line, NamedTextColor.GREEN);
    }

    private static void warnMainThread(Player player) {
        player.sendMessage(info("Main-thread bench: the server may hitch on purpose."));
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(err("Player only."));
        return null;
    }

    private static void tell(AepermBenchPlugin plugin, CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text("[apbench] ", NamedTextColor.GRAY).append(Component.text(text, color)));
        plugin.getLogger().info("[apbench] " + text);
    }

    private static Component info(String text) {
        return Component.text("[apbench] ", NamedTextColor.GRAY).append(Component.text(text, NamedTextColor.WHITE));
    }

    private static Component err(String text) {
        return Component.text("[apbench] ", NamedTextColor.GRAY).append(Component.text(text, NamedTextColor.RED));
    }
}
