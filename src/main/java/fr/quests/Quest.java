package fr.quests;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Quest {

    public enum Type {
        KILL_MOB("Tuer", "Tuer des mobs", Material.IRON_SWORD),
        BREAK_BLOCK("Miner", "Casser des blocs", Material.IRON_PICKAXE),
        PLACE_BLOCK("Poser", "Poser des blocs", Material.GRASS_BLOCK),
        FISH("Pêcher", "Pêcher", Material.FISHING_ROD);

        public final String verb;
        public final String label;
        public final Material icon;

        Type(String verb, String label, Material icon) {
            this.verb = verb;
            this.label = label;
            this.icon = icon;
        }

        public Type next() { return values()[(ordinal() + 1) % values().length]; }
        public Type prev() { return values()[(ordinal() + values().length - 1) % values().length]; }
    }

    public enum Category {
        DAILY("Quotidiennes", "Quêtes quotidiennes", "<yellow>", Material.CLOCK,
                "Se réinitialisent chaque jour"),
        WEEKLY("Hebdomadaires", "Quêtes hebdomadaires", "<aqua>", Material.ENCHANTED_BOOK,
                "Se réinitialisent chaque semaine"),
        TOWN("Ville", "Quêtes de la ville", "<green>", Material.BELL,
                "Quêtes permanentes, sans limite de temps");

        public final String label;
        public final String title;
        public final String color;
        public final Material icon;
        public final String description;

        Category(String label, String title, String color, Material icon, String description) {
            this.label = label;
            this.title = title;
            this.color = color;
            this.icon = icon;
            this.description = description;
        }

        public Category next() { return values()[(ordinal() + 1) % values().length]; }
        public Category prev() { return values()[(ordinal() + values().length - 1) % values().length]; }
    }

    // Alias français -> identifiant Minecraft
    private static final Map<String, String> FR = Map.ofEntries(
            Map.entry("mouton", "sheep"), Map.entry("vache", "cow"), Map.entry("cochon", "pig"),
            Map.entry("poulet", "chicken"), Map.entry("poule", "chicken"),
            Map.entry("squelette", "skeleton"), Map.entry("araignee", "spider"),
            Map.entry("araignée", "spider"), Map.entry("loup", "wolf"), Map.entry("cheval", "horse"),
            Map.entry("lapin", "rabbit"), Map.entry("villageois", "villager"),
            Map.entry("sorciere", "witch"), Map.entry("sorcière", "witch"),
            Map.entry("pierre", "stone"), Map.entry("terre", "dirt"), Map.entry("diamant", "diamond_ore"),
            Map.entry("charbon", "coal_ore"), Map.entry("fer", "iron_ore"), Map.entry("or", "gold_ore"),
            Map.entry("ble", "wheat"), Map.entry("blé", "wheat"), Map.entry("carotte", "carrots"),
            Map.entry("citrouille", "pumpkin"), Map.entry("poisson", "cod"), Map.entry("saumon", "salmon"),
            Map.entry("bois", "oak_log"), Map.entry("chene", "oak_log"), Map.entry("chêne", "oak_log")
    );

    public final String id;
    public String name = "Nouvelle quête";
    public Type type = Type.KILL_MOB;
    public String target = "sheep";   // clé minecraft sans namespace, ou "*" pour tout
    public int amount = 10;
    public Category category = Category.DAILY;
    public boolean enabled = true;
    public int xp = 0;
    public List<ItemStack> items = new ArrayList<>();
    public List<String> commands = new ArrayList<>();

    public Quest(String id) {
        this.id = id;
    }

    public void copyFrom(Quest o) {
        name = o.name;
        type = o.type;
        target = o.target;
        amount = o.amount;
        category = o.category;
        enabled = o.enabled;
        xp = o.xp;
        items = new ArrayList<>();
        for (ItemStack it : o.items) items.add(it.clone());
        commands = new ArrayList<>(o.commands);
    }

    public boolean matches(String key) {
        return "*".equals(target) || target.equals(key);
    }

    /** Convertit une saisie (mouton, SHEEP, minecraft:sheep, *) en clé valide, ou null si invalide. */
    public static String normalizeTarget(Type type, String input) {
        String s = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length());
        if (s.equals("*") || s.equals("tout") || s.equals("any")) return "*";
        s = FR.getOrDefault(s, s);
        if (type == Type.KILL_MOB) {
            return entityOf(s) != null ? s : null;
        }
        Material m = Material.matchMaterial(s);
        return m != null ? m.getKey().getKey() : null;
    }

    public static EntityType entityOf(String key) {
        NamespacedKey nk = NamespacedKey.fromString("minecraft:" + key);
        return nk == null ? null : Registry.ENTITY_TYPE.get(nk);
    }

    public Component targetName() {
        if ("*".equals(target)) {
            return Component.text(switch (type) {
                case KILL_MOB -> "n'importe quel mob";
                case FISH -> "n'importe quel objet";
                default -> "n'importe quel bloc";
            });
        }
        if (type == Type.KILL_MOB) {
            EntityType et = entityOf(target);
            if (et != null) return Component.translatable(et.translationKey());
        } else {
            Material m = Material.matchMaterial(target);
            if (m != null) return Component.translatable(m.translationKey());
        }
        return Component.text(target);
    }

    public Component describe() {
        return Component.text(type.verb + " " + amount + " ", net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .append(targetName());
    }

    public Material icon() {
        if (!"*".equals(target)) {
            if (type == Type.KILL_MOB) {
                Material egg = Material.matchMaterial(target + "_spawn_egg");
                if (egg != null) return egg;
            } else {
                Material m = Material.matchMaterial(target);
                if (m != null && m.isItem()) return m;
            }
        }
        return type.icon;
    }
}
