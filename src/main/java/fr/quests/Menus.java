package fr.quests;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class Menus {

    /** Cases centrales (comme les objets du menu "Classements Joueurs"). */
    public static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    public static final class Draft {
        public String name = "Nouvelle quête";
        public Quest.Type type = Quest.Type.KILL_MOB;
        public String target = "sheep";
        public int amount = 10;
        public Quest.Category category = Quest.Category.DAILY;
        public List<ItemStack> items = new ArrayList<>();
        public int xp = 0;
        public List<String> commands = new ArrayList<>();
    }

    public record Prompt(Function<String, String> handler) {}

    private final QuestPlugin plugin;
    public final Map<UUID, Draft> drafts = new HashMap<>();
    public final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public Menus(QuestPlugin plugin) {
        this.plugin = plugin;
    }

    private QuestManager qm() { return plugin.manager(); }

    public static int indexOf(int slot) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) return i;
        return -1;
    }

    private Inventory create(MenuHolder.Kind kind, Quest.Category cat, int page, int size, String title) {
        MenuHolder h = new MenuHolder(kind, cat, page);
        Inventory inv = Bukkit.createInventory(h, size, Items.c("<dark_gray>" + title));
        h.set(inv);
        if (size == 54) Items.border(inv);
        return inv;
    }

    public Draft draft(Player p) {
        return drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft());
    }

    // ================================================================== MENUS JOUEUR

    public void openMain(Player p) {
        Inventory inv = create(MenuHolder.Kind.MAIN, null, 0, 54, "Quêtes");
        int claim = 0;
        for (Quest.Category c : Quest.Category.values()) claim += qm().claimable(p, c);

        inv.setItem(4, Items.make(Material.GOLD_BLOCK, "<gold><bold>Mes quêtes",
                "<gray>Choisis une catégorie ci-dessous.", "",
                claim > 0 ? "<green>" + claim + " récompense(s) à récupérer !" : "<gray>Aucune récompense en attente"));

        int[] slots = {20, 22, 24};
        Quest.Category[] cats = Quest.Category.values();
        for (int i = 0; i < cats.length; i++) {
            Quest.Category c = cats[i];
            int total = qm().byCategory(c).size();
            int c2 = qm().claimable(p, c);
            inv.setItem(slots[i], Items.make(c.icon, c.color + "<bold>" + c.label,
                    "<gray>" + c.description, "",
                    "<gray>Terminées : <white>" + qm().completed(p, c) + "<gray>/<white>" + total,
                    c2 > 0 ? "<green>" + c2 + " à récupérer !" : "<dark_gray>Rien à récupérer", "",
                    "<yellow>▶ Clique pour ouvrir"));
        }
        inv.setItem(49, Items.make(Material.BARRIER, "<red>Fermer"));
        p.openInventory(inv);
    }

    public void openList(Player p, Quest.Category cat, int page) {
        List<Quest> list = qm().byCategory(cat);
        int pages = Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = create(MenuHolder.Kind.LIST, cat, page, 54, cat.title);
        inv.setItem(4, Items.make(cat.icon, cat.color + "<bold>" + cat.title,
                "<gray>" + cat.description, "", "<gray>Page <white>" + (page + 1) + "<gray>/<white>" + pages));

        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            if (idx >= list.size()) break;
            Quest q = list.get(idx);
            inv.setItem(SLOTS[i], questItem(q, qm().progress(p.getUniqueId(), q), null));
        }
        if (list.isEmpty()) {
            inv.setItem(22, Items.make(Material.BARRIER, "<red>Aucune quête", "<gray>Reviens plus tard !"));
        }
        nav(inv, page, pages);
        p.openInventory(inv);
    }

    private void nav(Inventory inv, int page, int pages) {
        inv.setItem(45, Items.make(Material.ARROW, "<yellow>Retour"));
        inv.setItem(49, Items.make(Material.BARRIER, "<red>Fermer"));
        if (page > 0) inv.setItem(48, Items.make(Material.SPECTRAL_ARROW, "<yellow>Page précédente"));
        if (page < pages - 1) inv.setItem(50, Items.make(Material.SPECTRAL_ARROW, "<yellow>Page suivante"));
    }

    public List<Component> rewardLines(Quest q) {
        List<Component> l = new ArrayList<>();
        for (ItemStack it : q.items) {
            ItemMeta m = it.getItemMeta();
            Component name = (m != null && m.hasDisplayName())
                    ? m.displayName()
                    : Component.translatable(it.getType().translationKey());
            l.add(Items.c("<gray> • <white>" + it.getAmount() + "x ").append(name));
        }
        if (q.xp > 0) l.add(Items.c("<gray> • <green>" + q.xp + " XP"));
        if (!q.commands.isEmpty()) l.add(Items.c("<gray> • <light_purple>Bonus spécial"));
        if (l.isEmpty()) l.add(Items.c("<gray> • <dark_gray>Aucune"));
        return l;
    }

    /**
     * @param pr    progression du joueur (null = vue admin, sans progression)
     * @param extra lignes supplémentaires ajoutées en bas (vue admin)
     */
    public ItemStack questItem(Quest q, QuestManager.Progress pr, List<Component> extra) {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.c("<gray>Objectif : ").append(q.describe()));
        lore.add(Component.empty());

        boolean done = false, claimed = false;
        if (pr != null) {
            int cur = Math.min(pr.amount, q.amount);
            done = pr.amount >= q.amount;
            claimed = pr.claimed;
            lore.add(Items.c("<gray>Progression : <white>" + cur + "<gray>/<white>" + q.amount
                    + " <dark_gray>(" + (cur * 100 / q.amount) + "%)"));
            lore.add(Items.c(Items.bar(cur, q.amount)));
            lore.add(Component.empty());
        }

        lore.add(Items.c("<gold>Récompenses :"));
        lore.addAll(rewardLines(q));

        if (pr != null) {
            lore.add(Component.empty());
            if (claimed) lore.add(Items.c("<dark_gray>✔ Récompense récupérée"));
            else if (done) lore.add(Items.c("<green>▶ Clique pour récupérer !"));
            else lore.add(Items.c("<yellow>En cours..."));
        }
        if (extra != null) {
            lore.add(Component.empty());
            lore.addAll(extra);
        }

        ItemStack it = new ItemStack(q.icon());
        ItemMeta m = it.getItemMeta();
        m.displayName(Items.c(q.category.color + "<bold>" + q.name));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (done && !claimed) m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    // ================================================================== PANEL ADMIN

    public void openAdmin(Player p) {
        Inventory inv = create(MenuHolder.Kind.ADMIN, null, 0, 54, "Panel administrateur");
        inv.setItem(4, Items.make(Material.GOLD_BLOCK, "<gold><bold>Panel administrateur",
                "<gray>Quêtes existantes : <white>" + qm().all().size()));
        inv.setItem(20, Items.make(Material.EMERALD, "<green><bold>Créer une quête",
                "<gray>Ouvre l'assistant de création.", "", "<yellow>▶ Clique pour commencer"));
        inv.setItem(22, Items.make(Material.BOOK, "<aqua><bold>Gérer les quêtes",
                "<gray>Activer, désactiver ou supprimer.", "", "<yellow>▶ Clique pour ouvrir"));
        inv.setItem(24, Items.make(Material.REDSTONE, "<red><bold>Recharger",
                "<gray>Relit quests.yml et data.yml."));
        inv.setItem(49, Items.make(Material.BARRIER, "<red>Fermer"));
        p.openInventory(inv);
    }

    public void openAdminList(Player p, int page) {
        List<Quest> list = new ArrayList<>(qm().all());
        int pages = Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = create(MenuHolder.Kind.ADMIN_LIST, null, page, 54, "Gérer les quêtes");
        inv.setItem(4, Items.make(Material.BOOK, "<aqua><bold>Toutes les quêtes",
                "<gray>Page <white>" + (page + 1) + "<gray>/<white>" + pages));

        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            if (idx >= list.size()) break;
            inv.setItem(SLOTS[i], adminItem(list.get(idx)));
        }
        if (list.isEmpty()) inv.setItem(22, Items.make(Material.BARRIER, "<red>Aucune quête créée"));
        nav(inv, page, pages);
        p.openInventory(inv);
    }

    public ItemStack adminItem(Quest q) {
        List<Component> extra = new ArrayList<>();
        extra.add(Items.c("<gray>Catégorie : " + q.category.color + q.category.label));
        extra.add(Items.c("<gray>ID : <white>" + q.id));
        extra.add(Items.c("<gray>Statut : " + (q.enabled ? "<green>Active" : "<red>Désactivée")));
        extra.add(Component.empty());
        extra.add(Items.c("<yellow>Clic gauche : <white>activer / désactiver"));
        extra.add(Items.c("<red>Shift + clic droit : <white>supprimer"));
        return questItem(q, null, extra);
    }

    // ================================================================== ASSISTANT DE CRÉATION

    public Quest toQuest(Draft d) {
        Quest q = new Quest("preview");
        q.name = d.name;
        q.type = d.type;
        q.target = d.target;
        q.amount = d.amount;
        q.category = d.category;
        q.xp = d.xp;
        q.items = d.items;
        q.commands = d.commands;
        return q;
    }

    public void openBuilder(Player p) {
        Draft d = draft(p);
        Quest q = toQuest(d);
        Inventory inv = create(MenuHolder.Kind.BUILDER, null, 0, 54, "Créer une quête");

        inv.setItem(4, questItem(q, new QuestManager.Progress(), null));

        inv.setItem(10, Items.make(Material.NAME_TAG, "<yellow><bold>Nom",
                "<gray>Actuel : <white>" + d.name, "", "<yellow>▶ Clique puis écris dans le chat"));
        inv.setItem(12, Items.make(d.type.icon, "<yellow><bold>Type",
                "<gray>Actuel : <white>" + d.type.label, "",
                "<yellow>▶ Clic gauche : suivant", "<yellow>▶ Clic droit : précédent"));
        inv.setItem(14, Items.make(q.icon(), "<yellow><bold>Cible",
                "<gray>Actuelle : <white>" + d.target, "",
                "<gray>Ex : mouton, zombie, diamond_ore, cod",
                "<gray>Mets <white>*<gray> pour « n'importe lequel »", "",
                "<yellow>▶ Clique puis écris dans le chat"));
        inv.setItem(16, Items.make(Material.PAPER, "<yellow><bold>Quantité",
                "<gray>Actuelle : <white>" + d.amount, "", "<yellow>▶ Clique puis écris un nombre"));

        inv.setItem(20, Items.make(d.category.icon, "<yellow><bold>Catégorie",
                "<gray>Actuelle : " + d.category.color + d.category.label, "",
                "<yellow>▶ Clic gauche : suivante", "<yellow>▶ Clic droit : précédente"));
        inv.setItem(22, Items.make(Material.CHEST, "<yellow><bold>Récompenses (objets)",
                "<gray>Objets : <white>" + d.items.size(), "", "<yellow>▶ Clique pour déposer des objets"));
        inv.setItem(24, Items.make(Material.EXPERIENCE_BOTTLE, "<yellow><bold>Récompense XP",
                "<gray>Actuelle : <white>" + d.xp + " XP", "", "<yellow>▶ Clique puis écris un nombre"));
        inv.setItem(31, Items.make(Material.COMMAND_BLOCK, "<yellow><bold>Commandes de récompense",
                "<gray>Actuelles : <white>" + (d.commands.isEmpty() ? "aucune" : String.join(" ; ", d.commands)),
                "", "<gray>Utilise <white>%player%<gray>, sépare par <white>;",
                "<gray>Ex : <white>eco give %player% 500", "<gray>Écris <white>aucune<gray> pour effacer", "",
                "<yellow>▶ Clique puis écris dans le chat"));

        inv.setItem(45, Items.make(Material.ARROW, "<yellow>Retour"));
        inv.setItem(49, Items.make(Material.EMERALD_BLOCK, "<green><bold>Créer la quête",
                "<gray>Valide et ajoute la quête."));
        inv.setItem(53, Items.make(Material.TNT, "<red>Réinitialiser le brouillon"));
        p.openInventory(inv);
    }

    public void openRewards(Player p) {
        Draft d = draft(p);
        Inventory inv = create(MenuHolder.Kind.REWARDS, null, 0, 27, "Dépose les récompenses");
        for (ItemStack it : d.items) inv.addItem(it.clone());
        p.openInventory(inv);
    }

    /** Ferme l'inventaire et attend une saisie dans le chat. */
    public void prompt(Player p, String message, Function<String, String> handler) {
        prompts.put(p.getUniqueId(), new Prompt(handler));
        p.closeInventory();
        p.sendMessage(Items.c(message + " <dark_gray>(écris <red>annuler<dark_gray> pour annuler)"));
    }
}
