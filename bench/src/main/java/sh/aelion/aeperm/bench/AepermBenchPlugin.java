package sh.aelion.aeperm.bench;

import sh.aelion.aeperm.api.AepermAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AepermBenchPlugin extends JavaPlugin {

    private AepermAPI api;
    private SeedData seed;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<AepermAPI> registration = Bukkit.getServicesManager().getRegistration(AepermAPI.class);
        AepermAPI loaded = registration == null ? null : registration.getProvider();
        if (loaded == null) {
            getLogger().severe("AepermAPI is not registered. Install AePerm and restart. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        api = loaded;
        seed = new SeedData(api);
        BenchCommands.register(this);
        getLogger().warning("AePerm-Bench is for test servers only. /apbench seed mutates permission storage.");
    }

    @Override
    public void onDisable() {
        stopTick();
    }

    public AepermAPI api() {
        return api;
    }

    public SeedData seed() {
        return seed;
    }

    public boolean tickRunning() {
        return tickTask != null && !tickTask.isCancelled();
    }

    public void startTick(BukkitTask task) {
        stopTick();
        tickTask = task;
    }

    public void stopTick() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }
}
