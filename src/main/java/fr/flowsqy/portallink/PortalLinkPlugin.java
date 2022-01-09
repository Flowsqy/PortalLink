package fr.flowsqy.portallink;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

public class PortalLinkPlugin extends JavaPlugin implements Listener {

    private final Map<String, String> netherMap;
    private final Map<String, String> enderMap;
    private final Set<String> disallowEntities;
    private final Map<UUID, Location> playerRespawnByEndExit;

    public PortalLinkPlugin() {
        super();
        netherMap = new HashMap<>();
        enderMap = new HashMap<>();
        disallowEntities = new HashSet<>();
        playerRespawnByEndExit = new HashMap<>();
    }

    @Override
    public void onEnable() {
        final Logger logger = getLogger();
        final File dataFolder = getDataFolder();

        if (!checkDataFolder(dataFolder)) {
            logger.warning("Can not write in the directory : " + dataFolder.getAbsolutePath());
            logger.warning("Disable the plugin");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        final YamlConfiguration configuration = initFile(dataFolder, "config.yml");
        for(final String key : configuration.getKeys(false)){
            final ConfigurationSection worldSection = configuration.getConfigurationSection(key);
            if(worldSection == null){
                continue;
            }
            final String netherWorld = worldSection.getString("nether");
            if(netherWorld != null && !netherWorld.isEmpty()){
                netherMap.put(key, netherWorld);
            }
            final String enderWorld = worldSection.getString("end");
            if(netherWorld != null && !netherWorld.isEmpty()){
                enderMap.put(key, enderWorld);
            }
            final boolean disableEntitiesPortal = worldSection.getBoolean("disable-entities");
            if(disableEntitiesPortal){
                disallowEntities.add(key);
            }
        }

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private boolean checkDataFolder(File dataFolder) {
        if (dataFolder.exists())
            return dataFolder.canWrite();
        return dataFolder.mkdirs();
    }

    private YamlConfiguration initFile(File dataFolder, String fileName) {
        final File file = new File(dataFolder, fileName);
        if (!file.exists()) {
            try {
                Files.copy(Objects.requireNonNull(getResource(fileName)), file.toPath());
            } catch (IOException ignored) {
            }
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerPortal(PlayerPortalEvent event){
        final String toWorld;
        if(event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL){
            toWorld = netherMap.get(event.getPlayer().getWorld().getName());
        }
        else if(event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL){
            toWorld = enderMap.get(event.getPlayer().getWorld().getName());
        }
        else{
            return;
        }
        if(toWorld == null){
            return;
        }
        event.setCancelled(true);

        final World world = Bukkit.getWorld(toWorld);
        if(world == null){
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> event.getPlayer().teleport(world.getSpawnLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onEntityPortal(EntityPortalEvent event){
        if(disallowEntities.contains(event.getEntity().getWorld().getName())){
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void enterEndPortalInEnd(EntityPortalEnterEvent event){
        if(
                event.getEntity().getWorld().getEnvironment() == World.Environment.THE_END
                        && event.getEntity() instanceof Player
                        && event.getEntity().getWorld().getBlockAt(event.getLocation()).getType() == Material.END_PORTAL
        ){
            final String toWorld = enderMap.get(event.getEntity().getWorld().getName());
            if(toWorld != null){
                final World world = Bukkit.getWorld(toWorld);
                playerRespawnByEndExit.put(event.getEntity().getUniqueId(), world == null ? null : world.getSpawnLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRespawn(PlayerRespawnEvent event){
        final Location location = playerRespawnByEndExit.remove(event.getPlayer().getUniqueId());
        if(location != null){
            event.setRespawnLocation(location);
        }
    }

}