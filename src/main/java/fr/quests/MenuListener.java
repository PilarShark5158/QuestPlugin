package fr.quests;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MenuListener implements Listener {

    private final QuestPlugin plugin;
    private final QuestManager qm;
    private final Menus menus;

    public MenuListener(QuestPlugin plugin) {
        this.plugin = plugin;
        this.qm = plugin.manager();
        this.menus = plugin.menus();
    }

    // ------------------------------------------------------------------ clics

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder h)) return;
        if (h.kind == MenuHolder.Kind.REWARDS) return; // zone de dépôt libre
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;

        int slot = e.getRawSlot();
        switch (h.kind) {
            case MAIN -> clickMain(p, slot);
            case LIST -> clickList(p, h, top, slot);
            case ADMIN, ADMIN_LIST, BUILDER -> {
                if (!p.hasPermission("quetes.admin")) {
                    p.closeInventory();
                    return;
                }
                switch (h.kind) {
                    case ADMIN -> clickAdmin(p, slot);
                    case ADMIN_LIST -> clickAdminList(p, h, top, slot, e.getClick());
                    default -> clickBuilder(p, slot, e.getClick());
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder h
                && h.kind != MenuHolder.Kind.REWARDS) {
            e.setCancelled(true);
        }
    }

    private void clickMain(Player p, int slot) {
        switch (slot) {
            case 20 -> menus.openList(p, Quest.Category.DAILY, 0);
            case 22 -> menus.openList(p, Quest.Category.WEEKLY, 0);
            case 24 -> menus.openList(p, Quest.Category.TOWN, 0);
            case 49 -> p.closeInventory();
            default -> { }
        }
    }

    private void clickList(Player p, MenuHolder h, Inventory top, int slot) {
        switch (slot) {
            case 45 -> { menus.openMain(p); return; }
            case 49 -> { p.closeInventory(); return; }
            case 48 -> { if (h.page > 0) menus.openList(p, h.category, h.page - 1); return; }
            case 50 -> { menus.openList(p, h.category, h.page + 1); return; }
            default -> { }
        }
        int idx = Menus.indexOf(slot);
        if (idx < 0) return;
        List<Quest> list = qm.byCategory(h.category);
        int real = h.page * Menus.SLOTS.length + idx;
        if (real >= list.size()) return;
        Quest q = list.get(real);
        if (qm.claim(p, q)) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            p.sendMessage(Items.c("<green>Récompense de <white>" + q.name + " <green>récupérée !"));
            top.setItem(slot, menus.questItem(q, qm.progress(p.getUniqueId(), q), null));
        }
    }

    private void clickAdmin(Player p, int slot) {
        switch (slot) {
            case 20 -> menus.openBuilder(p);
            case 22 -> menus.openAdminList(p, 0);
            case 24 -> {
                qm.saveData();
                qm.load();
                p.sendMessage(Items.c("<green>Quêtes rechargées."));
            }
            case 49 -> p.closeInventory();
            default -> { }
        }
    }

    private void clickAdminList(Player p, MenuHolder h, Inventory top, int slot, ClickType click) {
        switch (slot) {
            case 45 -> { menus.openAdmin(p); return; }
            case 49 -> { p.closeInventory(); return; }
            case 48 -> { if (h.page > 0) menus.openAdminList(p, h.page - 1); return; }
            case 50 -> { menus.openAdminList(p, h.page + 1); return; }
            default -> { }
        }
        int idx = Menus.indexOf(slot);
        if (idx < 0) return;
        List<Quest> list = new ArrayList<>(qm.all());
        int real = h.page * Menus.SLOTS.length + idx;
        if (real >= list.size()) return;
        Quest q = list.get(real);

        if (click == ClickType.LEFT) {
            q.enabled = !q.enabled;
            qm.saveQuests();
            top.setItem(slot, menus.adminItem(q));
        } else if (click == ClickType.SHIFT_RIGHT) {
            qm.remove(q);
            p.sendMessage(Items.c("<red>Quête supprimée : <white>" + q.name));
            menus.openAdminList(p, h.page);
        }
    }

    private void clickBuilder(Player p, int slot, ClickType click) {
        Menus.Draft d = menus.draft(p);
        switch (slot) {
            case 10 -> menus.prompt(p, "<yellow>Écris le nom de la quête :", s -> {
                d.name = s;
                return null;
            });
            case 12 -> {
                d.type = click.isRightClick() ? d.type.prev() : d.type.next();
                d.target = "*";
                menus.openBuilder(p);
            }
            case 14 -> menus.prompt(p, "<yellow>Écris la cible (ex : mouton, zombie, diamond_ore) ou * pour tout :", s -> {
                String t = Quest.normalizeTarget(d.type, s);
                if (t == null) return "Cible invalide pour le type « " + d.type.label + " ».";
                d.target = t;
                return null;
            });
            case 16 -> menus.prompt(p, "<yellow>Écris la quantité à atteindre (ex : 205) :", s -> {
                try {
                    int n = Integer.parseInt(s.trim());
                    if (n < 1 || n > 1_000_000) return "La quantité doit être entre 1 et 1 000 000.";
                    d.amount = n;
                    return null;
                } catch (NumberFormatException ex) {
                    return "Nombre invalide.";
                }
            });
            case 20 -> {
                d.category = click.isRightClick() ? d.category.prev() : d.category.next();
                menus.openBuilder(p);
            }
            case 22 -> menus.openRewards(p);
            case 24 -> menus.prompt(p, "<yellow>Écris la récompense en XP (0 = aucune) :", s -> {
                try {
                    int n = Integer.parseInt(s.trim());
                    if (n < 0 || n > 1_000_000) return "Valeur invalide.";
                    d.xp = n;
                    return null;
                } catch (NumberFormatException ex) {
                    return "Nombre invalide.";
                }
            });
            case 31 -> menus.prompt(p, "<yellow>Écris les commandes (séparées par ;) ou <white>aucune<yellow> :", s -> {
                d.commands.clear();
                if (!s.equalsIgnoreCase("aucune")) {
                    for (String part : s.split(";")) {
                        String c = part.trim();
                        if (c.startsWith("/")) c = c.substring(1);
                        if (!c.isEmpty()) d.commands.add(c);
                    }
                }
                return null;
            });
            case 45 -> menus.openAdmin(p);
            case 49 -> {
                if (d.amount < 1) return;
                Quest q = qm.create(menus.toQuest(d));
                menus.drafts.remove(p.getUniqueId());
                p.sendMessage(Items.c("<green>Quête créée : <white>" + q.name + " <gray>(" + q.category.label + ")"));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                menus.openAdmin(p);
            }
            case 53 -> {
                menus.drafts.remove(p.getUniqueId());
                menus.openBuilder(p);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ récompenses (objets)

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h) || h.kind != MenuHolder.Kind.REWARDS) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Menus.Draft d = menus.draft(p);
        d.items.clear();
        for (ItemStack it : e.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) d.items.add(it.clone());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) menus.openBuilder(p);
        });
    }

    // ------------------------------------------------------------------ saisie dans le chat

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Menus.Prompt pr = menus.prompts.remove(p.getUniqueId());
        if (pr == null) return;
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!msg.equalsIgnoreCase("annuler")) {
                String err = pr.handler().apply(msg);
                if (err != null) p.sendMessage(Items.c("<red>" + err));
            }
            if (p.isOnline()) menus.openBuilder(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        menus.prompts.remove(e.getPlayer().getUniqueId());
        menus.drafts.remove(e.getPlayer().getUniqueId());
    }
}
