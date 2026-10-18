package com.juanp.custommobs.classes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Clase de cada jugador, guardada en {@code data/player-classes.yml}.
 *
 * <p>Igual que la faccion: es estado de juego, no un permiso, y no depende de LuckPerms. La
 * clase <b>no</b> define alianzas ni enemistades: solo sirve para restringir objetos (y lo que
 * venga). Un jugador puede ser de cualquier clase con cualquier faccion.
 */
public final class PlayerClasses {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, String> entries = new LinkedHashMap<>();

    public PlayerClasses(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/player-classes.yml");
    }

    /** Lee las clases del disco. Un archivo corrupto o una linea mala se ignoran. */
    public void load() {
        this.entries.clear();
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            String value = root.getString(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                this.entries.put(UUID.fromString(key), value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // clave que no es un UUID: se descarta
            }
        }
    }

    public Optional<String> of(UUID playerId) {
        return Optional.ofNullable(this.entries.get(playerId));
    }

    public boolean has(UUID playerId) {
        return this.entries.containsKey(playerId);
    }

    public void set(UUID playerId, String clazz) {
        // Una sola clase por jugador: el mapa guarda un unico valor; esto reemplaza
        // cualquier clase anterior, no la suma.
        this.entries.put(playerId, clazz.trim().toLowerCase(Locale.ROOT));
        this.save();
    }

    public boolean clear(UUID playerId) {
        boolean removed = this.entries.remove(playerId) != null;
        if (removed) {
            this.save();
        }
        return removed;
    }

    public Map<UUID, String> all() {
        return Map.copyOf(this.entries);
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("players");
        for (Map.Entry<UUID, String> entry : this.entries.entrySet()) {
            root.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            File parent = this.file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            cfg.save(this.file);
        } catch (IOException ex) {
            this.plugin.getLogger().warning("No se pudo guardar data/player-classes.yml: "
                    + ex.getMessage());
        }
    }
}
