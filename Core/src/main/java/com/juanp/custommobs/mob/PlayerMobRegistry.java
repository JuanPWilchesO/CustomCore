package com.juanp.custommobs.mob;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.store.AtomicYaml;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Vinculos entre un jugador, sus mobs y los huevos que los representan.
 *
 * <p>Un <b>vinculo</b> tiene un id estable —el que llevan el huevo y la entidad— y dos
 * estados:
 *
 * <ul>
 *   <li><b>Desplegado</b>: el mob esta en el mundo. {@code entity} apunta a el.</li>
 *   <li><b>Guardado</b>: el mob no esta en el mundo; el huevo lo conserva y puede
 *       volver a colocarlo. {@code entity} es {@code null}.</li>
 * </ul>
 *
 * <p>Cuando el mob muere (o se destruye por abandono), el vinculo <b>se borra</b>: el huevo
 * queda apuntando a la nada y por tanto inutil, sin necesidad de ir a buscarlo por el mundo.
 *
 * <p>Vive en disco porque un mob guardado no tiene entidad que consultar, y porque un mob
 * desplegado puede estar en una chunk descargada, donde no hay API que lo enumere.
 */
public final class PlayerMobRegistry {

    /**
     * Un vinculo: a quien pertenece, que mob es, si esta desplegado y donde se le vio.
     *
     * <p>La posicion se guarda a proposito: cuando el mob queda en una chunk descargada
     * no hay entidad que consultar, y sin saber donde estaba no se puede decidir si su
     * dueno lo abandono.
     */
    public record MobLink(UUID linkId, UUID owner, String definitionId, UUID entityId,
                          String world, double x, double y, double z, MobState state) {

        public boolean deployed() {
            return this.entityId != null;
        }

        /** Ultima posicion conocida, o {@code null} si nunca se le vio. */
        public Location location() {
            World target = this.world == null ? null : Bukkit.getWorld(this.world);
            return target == null ? null : new Location(target, this.x, this.y, this.z);
        }

        public MobLink withEntity(UUID entity) {
            return new MobLink(this.linkId, this.owner, this.definitionId, entity,
                    this.world, this.x, this.y, this.z, this.state);
        }

        public MobLink moved(String world, double x, double y, double z) {
            return new MobLink(this.linkId, this.owner, this.definitionId, this.entityId,
                    world, x, y, z, this.state);
        }

        /** Estado guardado del mob; {@code null} si nunca se recogio. */
        public MobLink withState(MobState state) {
            return new MobLink(this.linkId, this.owner, this.definitionId, this.entityId,
                    this.world, this.x, this.y, this.z, state);
        }
    }

    private final CustomMobsPlugin plugin;
    private final File file;

    private final Map<UUID, MobLink> byLink = new LinkedHashMap<>();
    /** Indice entidad -> vinculo, para resolver desde el mob. */
    private final Map<UUID, UUID> linkOfEntity = new HashMap<>();
    private boolean dirty;
    /**
     * El archivo existia pero no se pudo leer: pudo quedar truncado. Mientras esto sea
     * {@code true} no se guarda, porque la lista en memoria estaria vacia o incompleta y
     * pisaria los vinculos de los mobs que solo viven dentro de su huevo.
     */
    private boolean loadFailed;
    private boolean backupWarned;

    public PlayerMobRegistry(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-mobs.yml");
    }

    public void load() {
        this.byLink.clear();
        this.linkOfEntity.clear();
        this.loadFailed = false;
        this.backupWarned = false;
        if (!this.file.exists()) {
            return;
        }
        // Sobra de una escritura interrumpida: el archivo bueno sigue siendo el principal.
        AtomicYaml.cleanTemp(this.file);
        YamlConfiguration yaml = this.read(this.file);
        if (yaml == null) {
            this.recover();
            return;
        }
        ConfigurationSection links = yaml.getConfigurationSection("links");
        if (links == null) {
            if (yaml.getKeys(false).isEmpty()) {
                // Un YAML vacio con el archivo presente es sospechoso: no se pisa.
                this.plugin.getLogger().warning(
                        "player-mobs.yml esta vacio; se deja intacto hasta el proximo arranque.");
                this.loadFailed = true;
            }
            return;
        }
        this.readLinks(links);
        if (!this.byLink.isEmpty()) {
            this.plugin.getLogger().info("Vinculos de mobs cargados: " + this.byLink.size()
                    + " (" + this.deployedTotal() + " desplegados).");
        }
    }

    /** Vuelca la seccion {@code links} en los indices en memoria. */
    private void readLinks(ConfigurationSection links) {
        for (String key : links.getKeys(false)) {
            UUID linkId = parse(key);
            if (linkId == null) {
                continue;
            }
            UUID owner = parse(links.getString(key + ".owner"));
            String definition = links.getString(key + ".definition");
            if (owner == null || definition == null) {
                continue;
            }
            UUID entity = parse(links.getString(key + ".entity"));
            MobLink link = new MobLink(linkId, owner, definition, entity,
                    links.getString(key + ".world"),
                    links.getDouble(key + ".x"), links.getDouble(key + ".y"), links.getDouble(key + ".z"),
                    readState(links, key));
            this.byLink.put(linkId, link);
            if (entity != null) {
                this.linkOfEntity.put(entity, linkId);
            }
        }
    }

    /**
     * Lee un YAML sin colapsar ante un error.
     *
     * <p>{@code loadConfiguration} devuelve una config vacia cuando el archivo no se puede
     * interpretar, que es exactamente el caso que hay que distinguir. Por eso se lee a mano
     * y se capturan los dos fallos posibles.
     *
     * @return la config, o {@code null} si el archivo existe pero no se pudo leer
     */
    private YamlConfiguration read(File source) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(source);
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            this.plugin.getLogger().warning("No se pudo leer " + source.getName() + ": "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Intenta salvar los vinculos desde el {@code .bak} y, si no, bloquea el guardado.
     *
     * <p>Se prefiere el respaldo a arrancar en vacio: en vacio, el primer guardado borraria
     * todos los huevos.
     */
    private void recover() {
        File backup = new File(this.file.getParentFile(), this.file.getName() + ".bak");
        YamlConfiguration yaml = backup.exists() ? this.read(backup) : null;
        ConfigurationSection links = yaml == null ? null : yaml.getConfigurationSection("links");
        if (links == null) {
            this.plugin.getLogger().severe("player-mobs.yml no se puede leer y no hay respaldo"
                    + " util. Se conserva el archivo y NO se guardara encima hasta que haya uno"
                    + " legible: revisalo a mano.");
            this.loadFailed = true;
            return;
        }
        this.plugin.getLogger().warning("player-mobs.yml estaba danado: se recuperan los vinculos"
                + " desde el respaldo .bak (" + links.getKeys(false).size() + ").");
        this.readLinks(links);
        this.file.delete();
    }

    public void save() {
        if (this.loadFailed) {
            // Guardar aqui sustituiria los vinculos buenos por la lista parcial en memoria.
            if (!this.backupWarned) {
                this.plugin.getLogger().severe("Se cancela el guardado de los vinculos de mobs:"
                        + " player-mobs.yml no se pudo leer al arrancar. Repara o retira el"
                        + " archivo y reinicia.");
                this.backupWarned = true;
            }
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (MobLink link : this.byLink.values()) {
            String base = "links." + link.linkId() + ".";
            yaml.set(base + "owner", link.owner().toString());
            yaml.set(base + "definition", link.definitionId());
            if (link.entityId() != null) {
                yaml.set(base + "entity", link.entityId().toString());
            }
            if (link.world() != null) {
                yaml.set(base + "world", link.world());
                yaml.set(base + "x", link.x());
                yaml.set(base + "y", link.y());
                yaml.set(base + "z", link.z());
            }
            MobState state = link.state();
            if (state != null) {
                String stateBase = base + "state.";
                yaml.set(stateBase + "health", state.health());
                yaml.set(stateBase + "absorption", state.absorption());
                yaml.set(stateBase + "fire-ticks", state.fireTicks());
                yaml.set(stateBase + "remaining-air", state.remainingAir());
                yaml.set(stateBase + "glowing", state.glowing());
                yaml.set(stateBase + "invisible", state.invisible());
                yaml.set(stateBase + "silent", state.silent());
                for (Map.Entry<String, Double> entry : state.attributes().entrySet()) {
                    yaml.set(stateBase + "attributes." + entry.getKey(), entry.getValue());
                }
                yaml.set(stateBase + "effects", state.potionEffects());
            }
        }
        if (AtomicYaml.save(yaml, this.file)) {
            this.dirty = false;
        } else {
            this.plugin.getLogger().warning("No se pudieron guardar los vinculos de mobs:"
                    + " no se pudo escribir " + this.file.getName() + " (el anterior queda intacto).");
        }
    }

    /** Estado guardado en el disco, o {@code null} si ese vinculo no lo tiene. */
    private static MobState readState(ConfigurationSection links, String key) {
        ConfigurationSection state = links.getConfigurationSection(key + ".state");
        if (state == null) {
            return null;
        }
        return new MobState(
                state.getDouble("health", 20.0D),
                state.getDouble("absorption", 0.0D),
                state.getInt("fire-ticks", 0),
                state.getInt("remaining-air", 300),
                state.getBoolean("glowing", false),
                state.getBoolean("invisible", false),
                state.getBoolean("silent", false),
                readAttributes(state),
                List.copyOf(state.getStringList("effects")));
    }

    /** Atributos desviados guardados en el disco. */
    private static Map<String, Double> readAttributes(ConfigurationSection state) {
        ConfigurationSection attributes = state.getConfigurationSection("attributes");
        if (attributes == null) {
            return Map.of();
        }
        Map<String, Double> found = new LinkedHashMap<>();
        for (String key : attributes.getKeys(false)) {
            found.put(key, attributes.getDouble(key));
        }
        return Map.copyOf(found);
    }

    /** Anota el estado del mob al recogerlo en su huevo. */
    public void saveState(UUID linkId, MobState state) {
        MobLink link = this.byLink.get(linkId);
        if (link == null || state == null) {
            return;
        }
        this.byLink.put(linkId, link.withState(state));
        this.dirty = true;
    }

    /** Olvida el estado: ya se devolvio al mob al volver a desplegarlo. */
    public void clearState(UUID linkId) {
        MobLink link = this.byLink.get(linkId);
        if (link == null || link.state() == null) {
            return;
        }
        this.byLink.put(linkId, link.withState(null));
        this.dirty = true;
    }

    /** Crea un vinculo nuevo, sin entidad todavia. */
    public MobLink create(UUID owner, String definitionId) {
        MobLink link = new MobLink(UUID.randomUUID(), owner, definitionId, null, null, 0, 0, 0, null);
        this.byLink.put(link.linkId(), link);
        this.dirty = true;
        return link;
    }

    /**
     * Da de alta un vinculo con un id concreto, o lo actualiza si ya existe.
     *
     * <p>Sirve para recuperarse al cargar una entidad de disco: el mob trae su id de
     * vinculo en el PDC, y con esto el registro vuelve a saber de quien es aunque el
     * archivo se hubiera perdido.
     */
    public MobLink ensure(UUID linkId, UUID owner, String definitionId, UUID entityId) {
        MobLink existing = this.byLink.get(linkId);
        MobLink link = existing != null
                ? existing.withEntity(entityId)
                : new MobLink(linkId, owner, definitionId, entityId, null, 0, 0, 0, null);
        this.byLink.put(linkId, link);
        if (entityId != null) {
            this.linkOfEntity.put(entityId, linkId);
        }
        this.dirty = true;
        return link;
    }

    /** Marca el vinculo como desplegado sobre esa entidad. */
    public void deploy(UUID linkId, UUID entityId) {
        MobLink link = this.byLink.get(linkId);
        if (link == null) {
            return;
        }
        if (link.entityId() != null) {
            this.linkOfEntity.remove(link.entityId());
        }
        this.byLink.put(linkId, link.withEntity(entityId));
        this.linkOfEntity.put(entityId, linkId);
        this.dirty = true;
    }

    /** Apunta donde se le vio por ultima vez: hace falta cuando su chunk se descarga. */
    public void move(UUID linkId, Location location) {
        MobLink link = this.byLink.get(linkId);
        if (link == null || location == null || location.getWorld() == null) {
            return;
        }
        this.byLink.put(linkId, link.moved(location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ()));
        this.dirty = true;
    }

    /** Marca el vinculo como guardado: el mob ya no esta en el mundo. */
    public void store(UUID linkId) {
        MobLink link = this.byLink.get(linkId);
        if (link == null || link.entityId() == null) {
            return;
        }
        this.linkOfEntity.remove(link.entityId());
        this.byLink.put(linkId, link.withEntity(null));
        this.dirty = true;
    }

    /** Borra el vinculo. El huevo que lo lleve queda inutil para siempre. */
    public void removeLink(UUID linkId) {
        MobLink link = this.byLink.remove(linkId);
        if (link == null) {
            return;
        }
        if (link.entityId() != null) {
            this.linkOfEntity.remove(link.entityId());
        }
        this.dirty = true;
    }

    public Optional<MobLink> byLink(UUID linkId) {
        return Optional.ofNullable(this.byLink.get(linkId));
    }

    /** Vinculo al que pertenece esa entidad, si es un mob nuestro. */
    public Optional<MobLink> byEntity(UUID entityId) {
        UUID linkId = this.linkOfEntity.get(entityId);
        return linkId == null ? Optional.empty() : Optional.ofNullable(this.byLink.get(linkId));
    }

    /** Mobs del jugador que estan ahora mismo en el mundo: lo que ocupa cupo. */
    public int deployedCount(UUID owner) {
        int count = 0;
        for (MobLink link : this.byLink.values()) {
            if (owner != null && owner.equals(link.owner()) && link.deployed()) {
                count++;
            }
        }
        return count;
    }

    /** Todos los vinculos de ese jugador, desplegados o guardados. */
    public Set<UUID> linksOf(UUID owner) {
        Set<UUID> found = new HashSet<>();
        for (MobLink link : this.byLink.values()) {
            if (owner != null && owner.equals(link.owner())) {
                found.add(link.linkId());
            }
        }
        return Set.copyOf(found);
    }

    /** Borra todos los vinculos del jugador. Devuelve cuantos eran. */
    public int resetOwner(UUID owner) {
        Set<UUID> links = this.linksOf(owner);
        for (UUID linkId : links) {
            this.removeLink(linkId);
        }
        return links.size();
    }

    public int total() {
        return this.byLink.size();
    }

    public int deployedTotal() {
        int count = 0;
        for (MobLink link : this.byLink.values()) {
            if (link.deployed()) {
                count++;
            }
        }
        return count;
    }

    public List<MobLink> all() {
        return List.copyOf(this.byLink.values());
    }

    public boolean isDirty() {
        return this.dirty;
    }

    private static UUID parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
