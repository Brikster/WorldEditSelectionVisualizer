package com.rojel.wesv;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.logging.Level;

public class WorldEditHelper extends BukkitRunnable {

    private final WorldEditSelectionVisualizer plugin;

    private Field wandItemField;
    private boolean useOffHand;

    public WorldEditHelper(WorldEditSelectionVisualizer plugin) {
        this.plugin = plugin;

        runTaskTimer(plugin, 0, plugin.getCustomConfig().getUpdateSelectionInterval());

        try {
            PlayerInventory.class.getDeclaredMethod("getItemInOffHand");
            useOffHand = true;  // 1.9+ server
        } catch (NoSuchMethodException e) {
            useOffHand = false; // 1.7-1.8 server
        }

        try {
            wandItemField = LocalConfiguration.class.getField("wandItem");
        } catch (NoSuchFieldException e) {
            plugin.getLogger().warning("No field wandItem in LocalConfiguration");
        }
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.getStorageManager().isEnabled(player)) {
                continue;
            }

            Region currentRegion = getSelectedRegion(player);

            if (!compareRegion(plugin.getLastSelectedRegions().get(player.getUniqueId()), currentRegion)) {
                if (currentRegion != null) {
                    plugin.getLastSelectedRegions().put(player.getUniqueId(), currentRegion.clone());
                } else {
                    plugin.getLastSelectedRegions().remove(player.getUniqueId());
                }

                plugin.getServer().getPluginManager().callEvent(new WorldEditSelectionChangeEvent(player, currentRegion));

                if (plugin.isSelectionShown(player)) {
                    plugin.showSelection(player);
                }
            }
        }
    }

    public Region getSelectedRegion(Player player) {
        LocalSession session = WorldEdit.getInstance().getSession(player.getName());

        if (session != null && session.getSelectionWorld() != null) {
            RegionSelector selector = session.getRegionSelector(session.getSelectionWorld());

            if (selector.isDefined()) {
                try {
                    return selector.getRegion();
                } catch (IncompleteRegionException e) {
                    plugin.getLogger().warning("Region still incomplete.");
                }
            }
        }
        return null;
    }

    public boolean compareRegion(Region region1, Region region2) {
        if (Objects.equals(region1, region2)) {
            return true;
        }

        if (region1 == null || region2 == null || !Objects.equals(region1.getWorld(), region2.getWorld())) {
            return false;
        }

        return plugin.wrapRegion(region1).regionEquals(region2);
    }

    @SuppressWarnings("deprecation")
    public boolean isHoldingSelectionItem(Player player) {
        ItemStack item = player.getItemInHand();
        ItemStack offHandItem = useOffHand ? player.getInventory().getItemInOffHand() : null;

        return isSelectionItem(item) || isSelectionItem(offHandItem);
    }

    @SuppressWarnings("deprecation")
    public boolean isSelectionItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        try {
            if (wandItemField.getType() == int.class) {

                return item.getType().getId() == wandItemField.getInt(WorldEdit.getInstance().getConfiguration());
            } else {
                plugin.getLogger().warning("Unknown type for wandItemField: " + wandItemField.getType().getName());
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "An error occurred on isHoldingSelectionItem", e);
        }
        return true;
    }
}
