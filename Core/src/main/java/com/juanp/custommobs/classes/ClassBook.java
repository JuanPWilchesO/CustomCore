package com.juanp.custommobs.classes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogo de clases, de {@code classes.yml}.
 *
 * <p>Cada clase tiene al menos un nombre visible. Las clases no tienen relaciones entre si:
 * sirven para restringir objetos, no para alianzas.
 */
public final class ClassBook {

    private final Map<String, String> names = new LinkedHashMap<>();

    public void reload(Plugin plugin) {
        this.names.clear();
        File file = new File(plugin.getDataFolder(), "classes.yml");
        if (!file.exists()) {
            plugin.saveResource("classes.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            this.names.put(key.toLowerCase(Locale.ROOT), section.getString("nombre", key));
        }
        plugin.getLogger().info("Clases cargadas: " + this.names.size() + ".");
    }

    public boolean exists(String id) {
        return id != null && this.names.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Optional<String> nameOf(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(this.names.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<String> known() {
        return List.copyOf(this.names.keySet());
    }
}
