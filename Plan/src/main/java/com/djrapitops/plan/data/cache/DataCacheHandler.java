package main.java.com.djrapitops.plan.data.cache;

import main.java.com.djrapitops.plan.data.cache.queue.DataCacheGetQueue;
import main.java.com.djrapitops.plan.data.cache.queue.DataCacheSaveQueue;
import main.java.com.djrapitops.plan.data.cache.queue.DataCacheClearQueue;
import main.java.com.djrapitops.plan.utilities.NewPlayerCreator;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import main.java.com.djrapitops.plan.Phrase;
import main.java.com.djrapitops.plan.Plan;
import main.java.com.djrapitops.plan.Settings;
import main.java.com.djrapitops.plan.data.*;
import main.java.com.djrapitops.plan.data.cache.queue.DataCacheProcessQueue;
import main.java.com.djrapitops.plan.data.handling.info.HandlingInfo;
import main.java.com.djrapitops.plan.data.handling.info.ReloadInfo;
import main.java.com.djrapitops.plan.database.Database;
import main.java.com.djrapitops.plan.utilities.comparators.HandlingInfoTimeComparator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import static org.bukkit.plugin.java.JavaPlugin.getPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 *
 * @author Rsl1122
 */
public class DataCacheHandler extends LocationCache {

    // Cache
    private final HashMap<UUID, UserData> dataCache;
    private HashMap<String, Integer> commandUse;

    // Plan
    private final Plan plugin;
    private final Database db;

    // Queues
    private DataCacheSaveQueue saveTask;
    private DataCacheClearQueue clearTask;
    private DataCacheProcessQueue processTask;
    private DataCacheGetQueue getTask;

    // Variables
    private int timesSaved;
    private int maxPlayers;

    /**
     * Class Constructor.
     *
     * Creates the set of Handlers that will be used to modify UserData. Gets
     * the Database from the plugin. Registers Asyncronous Periodic Save Task
     *
     * @param plugin Current instance of Plan
     */
    public DataCacheHandler(Plan plugin) {
        super();
        this.plugin = plugin;
        db = plugin.getDB();
        dataCache = new HashMap<>();

        getTask = new DataCacheGetQueue(plugin);
        clearTask = new DataCacheClearQueue(plugin, this);
        processTask = new DataCacheProcessQueue(plugin, this);
        saveTask = new DataCacheSaveQueue(plugin);

        timesSaved = 0;
        maxPlayers = plugin.getServer().getMaxPlayers();

        try {
            commandUse = db.getCommandUse();
        } catch (SQLException e) {
            plugin.toLog(this.getClass().getName(), e);
            plugin.logError(Phrase.DB_FAILURE_DISABLE + "");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        startAsyncPeriodicSaveTask();
    }

    public void startAsyncPeriodicSaveTask() throws IllegalArgumentException, IllegalStateException {
        int minutes = Settings.SAVE_CACHE_MIN.getNumber();
        if (minutes <= 0) {
            minutes = 5;
        }
        final int clearAfterXsaves;
        int configValue = Settings.CLEAR_CACHE_X_SAVES.getNumber();
        if (configValue <= 1) {
            clearAfterXsaves = 2;
        } else {
            clearAfterXsaves = configValue;
        }
        BukkitTask asyncPeriodicCacheSaveTask = (new BukkitRunnable() {
            @Override
            public void run() {
                DataCacheHandler handler = getPlugin(Plan.class).getHandler();
                handler.saveHandlerDataToCache();
                handler.saveCachedUserData();
                if (timesSaved % clearAfterXsaves == 0) {
                    handler.clearCache();
                }
                saveCommandUse();
                timesSaved++;
            }
        }).runTaskTimerAsynchronously(plugin, 60 * 20 * minutes, 60 * 20 * minutes);
    }

    /**
     * Uses Database to retrieve the UserData of a matching player
     *
     * Caches the data to the HashMap if cache: true
     *
     * @param processor DBCallableProcessor Object used to process the data
     * after it was retrieved
     * @param uuid Player's UUID
     * @param cache Wether or not the UserData will be Cached in this instance
     * of DataCacheHandler
     */
    public void getUserDataForProcessing(DBCallableProcessor processor, UUID uuid, boolean cache) {
        UserData uData = dataCache.get(uuid);
        if (uData == null) {
            if (cache) {
                DBCallableProcessor cacher = new DBCallableProcessor() {
                    @Override
                    public void process(UserData data) {

                        dataCache.put(uuid, data);
                        plugin.log(Phrase.CACHE_ADD.parse(uuid.toString()));

                    }
                };
                getTask.scheduleForGet(uuid, cacher, processor);
            } else {
                getTask.scheduleForGet(uuid, processor);
            }
        } else {
            processor.process(uData);
        }
    }

    /**
     ** Uses Database to retrieve the UserData of a matching player Caches the
     * data to the HashMap
     *
     * @param processor DBCallableProcessor Object used to process the data
     * after it was retrieved
     * @param uuid Player's UUID
     */
    public void getUserDataForProcessing(DBCallableProcessor processor, UUID uuid) {
        getUserDataForProcessing(processor, uuid, true);
    }

    /**
     * Saves all data in the cache to Database. Should only be called from Async
     * thread
     */
    public void saveCachedUserData() {
        List<UserData> data = new ArrayList<>();
        data.addAll(dataCache.values());
        try {
            db.saveMultipleUserData(data);
        } catch (SQLException ex) {
            plugin.toLog(this.getClass().getName(), ex);
        }
    }

    public void addToPool(HandlingInfo i) {
        processTask.addToPool(i);
    }

    /**
     * Saves all data in the cache to Database and closes the database down.
     * Closes save clear and get tasks.
     */
    public void saveCacheOnDisable() {
        saveTask.stop();
        getTask.stop();
        clearTask.stop();
        List<HandlingInfo> toProcess = processTask.stop();
        Collections.sort(toProcess, new HandlingInfoTimeComparator());
        for (HandlingInfo i : toProcess) {
            UserData uData = dataCache.get(i.getUuid());
            if (uData == null) {
                DBCallableProcessor p = new DBCallableProcessor() {
                    @Override
                    public void process(UserData data) {
                        i.process(data);
                    }
                };
                try {
                    db.giveUserDataToProcessors(i.getUuid(), p);
                } catch (SQLException ex) {
                    plugin.toLog(this.getClass().getName(), ex);
                }
            } else {
                i.process(uData);
            }
        }
        List<UserData> data = new ArrayList<>();

        data.addAll(dataCache.values());
        data.parallelStream()
                .forEach((userData) -> {
                    addSession(userData);
                });
        try {
            db.saveMultipleUserData(data);
            db.saveCommandUse(commandUse);
            db.close();
        } catch (SQLException e) {
            plugin.toLog(this.getClass().getName(), e);
        }
    }

    /**
     * Saves the cached data of matching Player if it is in the cache
     *
     * @param uuid Player's UUID
     */
    public void saveCachedData(UUID uuid) {
        DBCallableProcessor saveProcessor = new DBCallableProcessor() {
            @Override
            public void process(UserData data) {
                data.addLocations(getLocationsForSaving(uuid));
                clearLocations(uuid);
                addSession(data);
                saveTask.scheduleForSave(data);
                scheludeForClear(uuid);
            }
        };
        getTask.scheduleForGet(uuid, saveProcessor);
    }

    /**
     * Scheludes the cached CommandUsage to be saved.
     *
     */
    public void saveCommandUse() {
        try {
            db.saveCommandUse(commandUse);
        } catch (SQLException | NullPointerException e) {
            plugin.toLog(this.getClass().getName(), e);
        }
    }

    public void saveHandlerDataToCache() {
        Bukkit.getServer().getOnlinePlayers().parallelStream().forEach((p) -> {
            saveHandlerDataToCache(p);
        });
    }

    private void saveHandlerDataToCache(Player player) {
        long time = new Date().getTime();
        UUID uuid = player.getUniqueId();
//        plugin.getInfoPoolProcessor().
        addToPool(new ReloadInfo(uuid, time, player.getAddress().getAddress(), player.isBanned(), player.getDisplayName(), player.getGameMode()));

    }

    /**
     * Clears all UserData from the HashMap
     */
    public void clearCache() {
        clearTask.scheduleForClear(dataCache.keySet());
    }

    /**
     * Clears the matching UserData from the HashMap
     *
     * @param uuid Player's UUID
     */
    public void clearFromCache(UUID uuid) {
        dataCache.remove(uuid);
        plugin.log(Phrase.CACHE_REMOVE.parse(uuid.toString()));
    }

    /**
     *
     * @param uuid
     */
    public void scheludeForClear(UUID uuid) {
        clearTask.scheduleForClear(uuid);
    }

    /**
     *
     * @param uuid
     * @return
     */
    public boolean isDataAccessed(UUID uuid) {
        UserData userData = dataCache.get(uuid);
        if (userData != null) {
            if (userData.isAccessed()) {
                return true;
            }
            if (saveTask.containsUUID(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a new UserData instance and saves it to the Database
     *
     * @param player Player the new UserData is created for
     */
    public void newPlayer(Player player) {
        newPlayer(NewPlayerCreator.createNewPlayer(player));
    }

    /**
     *
     * @param player
     */
    public void newPlayer(OfflinePlayer player) {
        newPlayer(NewPlayerCreator.createNewPlayer(player));
    }
    
    public void newPlayer(UserData data) {
        saveTask.scheduleNewPlayer(data);
    }

    /**
     * @return The HashMap containing all Cached UserData
     */
    public HashMap<UUID, UserData> getDataCache() {
        return dataCache;
    }

    /**
     * @return Current instance of the LocationHandler
     */
    public LocationCache getLocationHandler() {
        return this;
    }

    /**
     *
     * @return
     */
    public HashMap<String, Integer> getCommandUse() {
        return commandUse;
    }

    /**
     *
     * @return
     */
    public SessionCache getSessionCache() {
        return this;
    }

    /**
     * If /reload is run this treats every online player as a new login.
     *
     * Calls all the methods that are ran when PlayerJoinEvent is fired
     */
    public void handleReload() {
        BukkitTask asyncReloadCacheUpdateTask = (new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    boolean isNewPlayer = !db.wasSeenBefore(uuid);
                    if (isNewPlayer) {
                        newPlayer(player);
                    }
                    saveHandlerDataToCache(player);
                }
                this.cancel();
            }
        }).runTaskAsynchronously(plugin);
    }

    /**
     * Used by Analysis for Player activity graphs.
     *
     * @return Maximum number of players defined in server.properties.
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void handleCommand(String command) {
        if (!commandUse.containsKey(command)) {
            commandUse.put(command, 0);
        }
        commandUse.put(command, commandUse.get(command) + 1);
    }
}
