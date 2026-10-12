package com.juanp.custommobs.faction;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Relaciones entre facciones de los mobs de categoria {@code server}.
 *
 * <p>Se cargan de {@code plugins/CustomMobs/factions.yml}. Las relaciones son
 * simetricas: si {@code orcos} declara a {@code humanos} como enemigo, la enemistad
 * vale tambien en el sentido contrario. Lo que no se declara es neutral.
 */
public final class FactionBook {

    private final Map<String, Set<String>> allies = new LinkedHashMap<>();
    private final Map<String, Set<String>> enemies = new LinkedHashMap<>();
    /** Color del nombre de los mobs de esa faccion, tal como se escribio. */
    private final Map<String, String> nameColors = new LinkedHashMap<>();
    /** Color del brillo de los mobs de esa faccion, tal como se escribio. */
    private final Map<String, String> glowColors = new LinkedHashMap<>();

    /** Recarga las relaciones desde {@code factions.yml}. */
    public void reload(Plugin plugin) {
        this.allies.clear();
        this.enemies.clear();
        this.nameColors.clear();
        this.glowColors.clear();

        File file = new File(plugin.getDataFolder(), "factions.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String rawFaction : cfg.getKeys(false)) {
            String faction = normalize(rawFaction);
            if (faction == null) {
                continue;
            }
            this.allies.put(faction, normalize(cfg.getStringList(rawFaction + ".allies"), faction));
            this.enemies.put(faction, normalize(cfg.getStringList(rawFaction + ".enemies"), faction));
            String name = cfg.getString(rawFaction + ".color");
            if (name != null && !name.isBlank()) {
                this.nameColors.put(faction, name.trim());
            }
            String glow = cfg.getString(rawFaction + ".glow");
            if (glow != null && !glow.isBlank()) {
                this.glowColors.put(faction, glow.trim());
            }
        }
        plugin.getLogger().info("Facciones cargadas: " + this.allies.size() + ".");
    }

    /** {@code true} si ambas facciones existen y estan declaradas como aliadas. */
    public boolean isAlly(String first, String second) {
        return this.declared(this.allies, first, second);
    }

    /** {@code true} si ambas facciones existen y estan declaradas como enemigas. */
    public boolean isEnemy(String first, String second) {
        return this.declared(this.enemies, first, second);
    }

    /** Color del nombre de los mobs de esa faccion, si lo declara. */
    public java.util.Optional<String> nameColor(String faction) {
        String key = normalize(faction);
        return key == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(this.nameColors.get(key));
    }

    /** Color del brillo de los mobs de esa faccion, si lo declara. */
    public java.util.Optional<String> glowColor(String faction) {
        String key = normalize(faction);
        return key == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(this.glowColors.get(key));
    }

    /** Facciones conocidas (las que aparecen en {@code factions.yml}). */
    public Collection<String> known() {
        return List.copyOf(this.allies.keySet());
    }

    public int size() {
        return this.allies.size();
    }

    private boolean declared(Map<String, Set<String>> table, String first, String second) {
        String a = normalize(first);
        String b = normalize(second);
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return table.getOrDefault(a, Set.of()).contains(b)
                || table.getOrDefault(b, Set.of()).contains(a);
    }

    private static Set<String> normalize(List<String> raw, String self) {
        Set<String> out = new LinkedHashSet<>();
        for (String value : raw) {
            String item = normalize(value);
            if (item != null && !item.equals(self)) {
                out.add(item);
            }
        }
        return out;
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.toLowerCase(Locale.ROOT).trim();
    }
}
