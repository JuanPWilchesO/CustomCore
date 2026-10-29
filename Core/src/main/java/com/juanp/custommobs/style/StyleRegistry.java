package com.juanp.custommobs.style;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.store.AtomicYaml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Estilos elegidos, persistidos en {@code styles.yml}.
 *
 * <p>Se guardan por jugador y por team. Cuando un jugador esta en un team, manda el estilo
 * del team — asi todos los player mobs de sus miembros se ven iguales, y solo el jefe puede
 * cambiarlo (esa comprobacion vive en el comando, no aqui).
 */
public final class StyleRegistry {

    private final CustomMobsPlugin plugin;
    private final File file;
    private final Map<UUID, MobStyle> players = new HashMap<>();
    private final Map<UUID, MobStyle> teams = new HashMap<>();
    private boolean dirty;
    /** El archivo no se pudo leer al arrancar: no se guarda encima hasta repararlo. */
    private boolean loadFailed;

    public StyleRegistry(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "styles.yml");
    }

    public void load() {
        this.players.clear();
        this.teams.clear();
        this.loadFailed = false;
        if (!this.file.exists()) {
            return;
        }
        AtomicYaml.cleanTemp(this.file);
        if (AtomicYaml.unreadable(this.file)) {
            this.plugin.getLogger().warning("styles.yml no se puede leer: no se guardara encima.");
            this.loadFailed = true;
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        this.read(yaml.getConfigurationSection("players"), this.players);
        this.read(yaml.getConfigurationSection("teams"), this.teams);
        if (!this.players.isEmpty() || !this.teams.isEmpty()) {
            this.plugin.getLogger().info("Estilos cargados: " + this.players.size()
                    + " jugadores, " + this.teams.size() + " teams.");
        }
    }

    public void save() {
        if (this.loadFailed) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        this.write(yaml, "players", this.players);
        this.write(yaml, "teams", this.teams);
        if (AtomicYaml.save(yaml, this.file)) {
            this.dirty = false;
        } else {
            this.plugin.getLogger().warning("No se pudieron guardar los estilos: "
                    + "no se pudo escribir styles.yml (el anterior queda intacto).");
        }
    }

    public MobStyle playerStyle(UUID playerId) {
        return this.players.get(playerId);
    }

    public MobStyle teamStyle(UUID teamId) {
        return this.teams.get(teamId);
    }

    /** Cambia un campo del estilo de un jugador. {@code field} es {@code name} o {@code glow}. */
    public void setPlayer(UUID playerId, String field, String value) {
        this.players.put(playerId, this.with(this.players.get(playerId), field, value));
        this.dirty = true;
    }

    /** Cambia un campo del estilo de un team. */
    public void setTeam(UUID teamId, String field, String value) {
        this.teams.put(teamId, this.with(this.teams.get(teamId), field, value));
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    private MobStyle with(MobStyle current, String field, String value) {
        MobStyle base = current != null ? current : MobStyle.EMPTY;
        String normalized = value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
        if ("glow".equalsIgnoreCase(field)) {
            return new MobStyle(base.nameColor(), normalized);
        }
        return new MobStyle(normalized, base.glowColor());
    }

    private void read(ConfigurationSection root, Map<UUID, MobStyle> target) {
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id = parse(key);
            if (id == null) {
                continue;
            }
            String name = root.getString(key + ".name");
            String glow = root.getString(key + ".glow");
            if (name != null || glow != null) {
                target.put(id, new MobStyle(name, glow));
            }
        }
    }

    private void write(YamlConfiguration yaml, String root, Map<UUID, MobStyle> source) {
        for (Map.Entry<UUID, MobStyle> entry : source.entrySet()) {
            MobStyle style = entry.getValue();
            if (style.isEmpty()) {
                continue;
            }
            if (style.nameColor() != null) {
                yaml.set(root + "." + entry.getKey() + ".name", style.nameColor());
            }
            if (style.glowColor() != null) {
                yaml.set(root + "." + entry.getKey() + ".glow", style.glowColor());
            }
        }
    }

    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
