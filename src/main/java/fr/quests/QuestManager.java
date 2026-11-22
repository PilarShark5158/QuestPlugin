package fr.quests;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

public final class QuestManager {

    public static final class Progress {
        public int amount;
        public boolean claimed;
    }

    private final QuestPlugin plugin;
    private final File questFile;
    private final File dataFile;

    private final Map<String, Quest> quests = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Progress>> data = new HashMap<>();
    private int nextId = 1;
    private String lastDaily;
    private String lastWeekly;

    public QuestManager(QuestPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.questFile = new File(plugin.getDataFolder(), "quests.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ chargement / sauvegarde

    public void load() {
        quests.clear();
        data.clear();

        YamlConfiguration qc = YamlConfiguration.loadConfiguration(questFile);
        nextId = qc.getInt("next-id", 1);
        ConfigurationSection qs = qc.getConfigurationSection("quests");
        if (qs != null) {
            for (String id : qs.getKeys(false)) {
                ConfigurationSection s = qs.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    Quest q = new Quest(id);
                    q.name = s.getString("name", "Quête");
                    q.type = Quest.Type.valueOf(s.getString("type", "KILL_MOB"));
                    q.target = s.getString("target", "*");
                    q.amount = Math.max(1, s.getInt("amount", 1));
                    q.category = Quest.Category.valueOf(s.getString("category", "DAILY"));
                    q.enabled = s.getBoolean("enabled", true);
                    q.xp = s.getInt("xp", 0);
                    q.commands = new ArrayList<>(s.getStringList("commands"));
                    for (Object o : s.getList("items", List.of())) {
                        if (o instanceof ItemStack it) q.items.add(it);
                    }
                    quests.put(id, q);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Quête invalide ignorée : " + id + " (" + ex.getMessage() + ")");
                }
            }
        }

        YamlConfiguration dc = YamlConfiguration.loadConfiguration(dataFile);
        lastDaily = dc.getString("reset.daily");
        lastWeekly = dc.getString("reset.weekly");
        ConfigurationSection ps = dc.getConfigurationSection("players");
        if (ps != null) {
            for (String uuid : ps.getKeys(false)) {
                ConfigurationSection pq = ps.getConfigurationSection(uuid);
                if (pq == null) continue;
                try {
                    Map<String, Progress> map = new HashMap<>();
                    for (String qid : pq.getKeys(false)) {
                        Progress p = new Progress();
                        p.amount = pq.getInt(qid + ".amount");
                        p.claimed = pq.getBoolean(qid + ".claimed");
                        map.put(qid, p);
                    }
                    data.put(UUID.fromString(uuid), map);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void saveQuests() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("next-id", nextId);
        for (Quest q : quests.values()) {
            String p = "quests." + q.id + ".";
            c.set(p + "name", q.name);
            c.set(p + "type", q.type.name());
            c.set(p + "target", q.target);
            c.set(p + "amount", q.amount);
            c.set(p + "category", q.category.name());
            c.set(p + "enabled", q.enabled);
            c.set(p + "xp", q.xp);
            c.set(p + "commands", q.commands);
            c.set(p + "items", q.items);
        }
        try {
            c.save(questFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder quests.yml : " + e.getMessage());
        }
    }

    public void saveData() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("reset.daily", lastDaily);
        c.set("reset.weekly", lastWeekly);
        for (Map.Entry<UUID, Map<String, Progress>> e : data.entrySet()) {
            for (Map.Entry<String, Progress> q : e.getValue().entrySet()) {
                String p = "players." + e.getKey() + "." + q.getKey() + ".";
                c.set(p + "amount", q.getValue().amount);
                c.set(p + "claimed", q.getValue().claimed);
            }
        }
        try {
            c.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ accès

    public Collection<Quest> all() {
        return quests.values();
    }

    public List<Quest> byCategory(Quest.Category cat) {
        return quests.values().stream().filter(q -> q.enabled && q.category == cat).collect(Collectors.toList());
    }

    public Quest create(Quest template) {
        Quest q = new Quest("q" + (nextId++));
        q.copyFrom(template);
        quests.put(q.id, q);
        saveQuests();
        return q;
    }

    public void remove(Quest q) {
        quests.remove(q.id);
        data.values().forEach(m -> m.remove(q.id));
        saveQuests();
    }

    public Progress progress(UUID uuid, Quest q) {
        return data.computeIfAbsent(uuid, k -> new HashMap<>()).computeIfAbsent(q.id, k -> new Progress());
    }

    public int completed(Player p, Quest.Category cat) {
        int n = 0;
        for (Quest q : byCategory(cat)) if (progress(p.getUniqueId(), q).amount >= q.amount) n++;
        return n;
    }

    public int claimable(Player p, Quest.Category cat) {
        int n = 0;
        for (Quest q : byCategory(cat)) {
            Progress pr = progress(p.getUniqueId(), q);
            if (pr.amount >= q.amount && !pr.claimed) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ progression

    public void add(Player p, Quest.Type type, String key) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        for (Quest q : quests.values()) {
            if (!q.enabled || q.type != type || !q.matches(key)) continue;
            Progress pr = progress(p.getUniqueId(), q);
            if (pr.claimed || pr.amount >= q.amount) continue;
            pr.amount++;
            p.sendActionBar(Items.c(q.category.color + q.name + " <gray>: <white>" + pr.amount
                    + "<gray>/<white>" + q.amount));
            if (pr.amount >= q.amount) {
                p.sendMessage(Items.c("<green>✔ Quête terminée : <white>" + q.name
                        + " <gray>- ouvre <yellow>/quete <gray>pour récupérer ta récompense."));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
        }
    }

    public boolean claim(Player p, Quest q) {
        Progress pr = progress(p.getUniqueId(), q);
        if (pr.claimed || pr.amount < q.amount) return false;
        pr.claimed = true;
        for (ItemStack it : q.items) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
            left.values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
        }
        if (q.xp > 0) p.giveExp(q.xp);
        for (String cmd : q.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        return true;
    }

    // ------------------------------------------------------------------ réinitialisation

    public void checkResets() {
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("timezone", "Europe/Paris"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        LocalDate today = LocalDate.now(zone);
        String d = today.toString();
        String w = today.get(IsoFields.WEEK_BASED_YEAR) + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        if (lastDaily != null && !d.equals(lastDaily)) reset(Quest.Category.DAILY);
        if (lastWeekly != null && !w.equals(lastWeekly)) reset(Quest.Category.WEEKLY);
        lastDaily = d;
        lastWeekly = w;
    }

    private void reset(Quest.Category cat) {
        Set<String> ids = quests.values().stream().filter(q -> q.category == cat)
                .map(q -> q.id).collect(Collectors.toSet());
        data.values().forEach(m -> m.keySet().removeAll(ids));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Items.c("<gold>Les quêtes <white>" + cat.label.toLowerCase()
                    + " <gold>ont été réinitialisées !"));
        }
        saveData();
    }
}
