package fr.quests;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MenuHolder implements InventoryHolder {

    public enum Kind { MAIN, LIST, ADMIN, ADMIN_LIST, BUILDER, REWARDS }

    public final Kind kind;
    public final Quest.Category category;
    public final int page;
    private Inventory inventory;

    public MenuHolder(Kind kind, Quest.Category category, int page) {
        this.kind = kind;
        this.category = category;
        this.page = page;
    }

    public void set(Inventory inv) { this.inventory = inv; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
