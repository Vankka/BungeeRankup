package eu.beyondthebeast.bungeerankup;

import java.io.*;
import java.util.concurrent.TimeUnit;
import lu.r3flexi0n.bungeeonlinetime.BungeeOnlineTime;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class BungeeRankup extends Plugin {
    private static BungeeRankup instance;
    private Configuration configuration;
    private ScheduledTask task;

    static BungeeRankup getInstance() { return instance; }

    public void onEnable() {
        instance = this;
        File configFile = new File(getDataFolder(), "config.yaml");

        try {
            if (!getDataFolder().exists())
                getDataFolder().mkdir();
            if (!configFile.exists()) {
                configFile.createNewFile();
                InputStream inputStream = getResourceAsStream("config.yaml");
                OutputStream outputStream = new FileOutputStream(configFile);

                int bit;
                while ((bit = inputStream.read()) != -1)
                    outputStream.write(bit);

                inputStream.close();
                outputStream.close();
            }

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        startScheduler();
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new BungeeRankupCommand());
    }

    void reloadConfig() {
        try {
            File configFile = new File(BungeeRankup.getInstance().getDataFolder(), "config.yaml");
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void startScheduler() {
        task = ProxyServer.getInstance().getScheduler().schedule(this, new Thread(this::check), 0, configuration.getLong("sync_delay"), TimeUnit.MINUTES);
    }

    void stopScheduler() {
        getProxy().getScheduler().cancel(task);
    }

    void check() {
        try {
            long startTime = System.currentTimeMillis();

            if (ProxyServer.getInstance().getPlayers().size() == 0) {
                if (configuration.getBoolean("log"))
                    ProxyServer.getInstance().getLogger().info("No players online, not checking");
                return;
            }

            if (ProxyServer.getInstance().getPlayers().size() != 0) {
                for (ProxiedPlayer proxiedPlayer : ProxyServer.getInstance().getPlayers()) {
                    for (String rank : configuration.getSection("ranks").getKeys()) {
                        Configuration sec = configuration.getSection("ranks").getSection(rank);

                        long time = BungeeOnlineTime.mysql.getOnlineTime(proxiedPlayer.getUniqueId(), 0L);

                        boolean proceed;
                        int posAmount = 0;
                        int negAmount = 0;
                        for (String str : sec.getStringList("postivePermissions")) {
                            if (proxiedPlayer.hasPermission(str))
                                posAmount++;
                        }

                        for (String str : sec.getStringList("negativePermissions")) {
                            if (!proxiedPlayer.hasPermission(str))
                                negAmount++;
                        }

                        if (sec.getBoolean("requireAllPositivePermissions")) {
                            proceed = sec.getStringList("positivePermissions").size() == posAmount;
                        } else {
                            proceed = posAmount > 0;
                        }

                        if (!proceed)
                            continue;

                        if (sec.getBoolean("requireAllNegativePermissions")) {
                            proceed = sec.getStringList("negativePermissions").size() == negAmount;
                        } else {
                            proceed = negAmount > 0;
                        }

                        if (!proceed)
                            continue;

                        if (time >= sec.getDouble("timeRequired")) {
                            if (configuration.getBoolean("log"))
                                ProxyServer.getInstance().getLogger().info(proxiedPlayer.getName() + " met the conditions. Time for a rankup!");
                            for (String str : sec.getStringList("commands")) {
                                ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), str.replace("%name%", proxiedPlayer.getName()));
                                ProxyServer.getInstance().getLogger().info(str.replace("%name%", proxiedPlayer.getName()));
                            }
                        }
                    }
                }
            }

            if (configuration.getBoolean("log"))
                ProxyServer.getInstance().getLogger().info("Rankup check completed in; " + (System.currentTimeMillis() - startTime) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
