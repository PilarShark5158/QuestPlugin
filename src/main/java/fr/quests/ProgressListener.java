package fr.quests;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

public final class ProgressListener implements Listener {

    private final QuestManager qm;

    public ProgressListener(QuestPlugin plugin) {
        this.qm = plugin.manager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        qm.add(killer, Quest.Type.KILL_MOB, e.getEntityType().getKey().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        qm.add(e.getPlayer(), Quest.Type.BREAK_BLOCK, e.getBlock().getType().getKey().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        qm.add(e.getPlayer(), Quest.Type.PLACE_BLOCK, e.getBlockPlaced().getType().getKey().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (e.getCaught() instanceof Item item) {
            qm.add(e.getPlayer(), Quest.Type.FISH, item.getItemStack().getType().getKey().getKey());
        }
    }
}
