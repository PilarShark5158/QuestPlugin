package fr.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class Items {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Items() {}

    /** MiniMessage sans italique (comportement par défaut des items). */
    public static Component c(String s) {
        return MM.deserialize("<!italic>" + s);
    }

    public static ItemStack make(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(s.isEmpty() ? Component.empty() : c(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        it.setItemMeta(meta);
        return it;
    }

    /** Bordure de vitres grises, comme sur le menu "Classements Joueurs". */
    public static void border(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == inv.getSize() / 9 - 1 || col == 0 || col == 8) inv.setItem(i, pane());
        }
    }

    public static String bar(int cur, int max) {
        int filled = (int) Math.round(20.0 * Math.min(cur, max) / Math.max(1, max));
        return "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(20 - filled);
    }
}
