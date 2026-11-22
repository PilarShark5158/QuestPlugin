package fr.quests;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class QuestPlugin extends JavaPlugin {

    private QuestManager manager;
    private Menus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new QuestManager(this);
        manager.load();
        manager.checkResets();
        menus = new Menus(this);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ProgressListener(this), this);

        // Vérifie les réinitialisations chaque minute, sauvegarde la progression toutes les 5 minutes
        getServer().getScheduler().runTaskTimer(this, manager::checkResets, 20L * 60, 20L * 60);
        getServer().getScheduler().runTaskTimer(this, manager::saveData, 20L * 300, 20L * 300);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveQuests();
            manager.saveData();
        }
    }

    public QuestManager manager() { return manager; }

    public Menus menus() { return menus; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "quete" -> menus.openMain(p);
            case "queteadmin" -> {
                if (!p.hasPermission("quetes.admin")) {
                    p.sendMessage(Items.c("<red>Tu n'as pas la permission."));
                    return true;
                }
                menus.openAdmin(p);
            }
            default -> { }
        }
        return true;
    }
}
