package com.juanp.custommobs.mob;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.combat.AggroRegistry;
import com.juanp.custommobs.combat.TargetPolicy;
import com.juanp.custommobs.combat.VillageFolk;
import com.juanp.custommobs.config.PluginConfig;
import com.juanp.custommobs.disguise.DisguiseLink;
import com.juanp.custommobs.faction.FactionBook;
import com.juanp.custommobs.group.GroupLink;
import com.juanp.custommobs.item.Attributes;
import com.juanp.custommobs.item.EquipmentApplier;
import com.juanp.custommobs.spawner.SpawnerEntry;
import com.juanp.custommobs.style.StyleService;
import com.juanp.custommobs.spawner.SpawnerRegistry;
import com.juanp.custommobs.team.TeamLink;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ArmoredHorseInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nucleo del plugin: invoca, marca, restaura y rastrea los mobs custom.
 *
 * <p>El vinculo (dueno, team, definicion, punto de aparicion) vive en el PersistentDataContainer
 * de la entidad, por eso sobrevive reinicios sin necesidad de un archivo aparte. La unica
 * excepcion son los spawners: su punto debe sobrevivir a la muerte del mob, asi que se guardan
 * en {@code data/spawners.yml}.
 */
public final class MobService implements Listener {

    private final CustomMobsPlugin plugin;
    private final PluginConfig config;
    private final MobRegistry registry;
    private final TeamLink teamLink;
    private final PlayerMobRegistry playerMobs;
    private final MobKeys keys;
    private final TargetPolicy policy;
    private final AggroRegistry aggro;
    private final FactionBook factions;
    private final DisguiseLink disguiseLink;
    private final GroupLink groupLink;
    private final SpawnerRegistry spawners;
    private final Map<UUID, CustomMob> active = new ConcurrentHashMap<>();
    /** Entidades que aparecieron al azar: cuentan para los topes de aparicion. */
    private final Set<UUID> natural = ConcurrentHashMap.newKeySet();

    private StyleService styles;

    private boolean paperGoals = true;

    public MobService(CustomMobsPlugin plugin, PluginConfig config, MobRegistry registry, TeamLink teamLink,
                      FactionBook factions, DisguiseLink disguiseLink, GroupLink groupLink,
                      SpawnerRegistry spawners) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.teamLink = teamLink;
        this.factions = factions;
        this.disguiseLink = disguiseLink;
        this.groupLink = groupLink;
        this.spawners = spawners;
        this.playerMobs = new PlayerMobRegistry(plugin);
        this.playerMobs.load();
        this.keys = new MobKeys(plugin);
        this.aggro = new AggroRegistry();
        this.policy = new TargetPolicy(this, config.targetRadius());
    }

    public CustomMobsPlugin plugin() {
        return this.plugin;
    }

    /** Cupo persistente de mobs de jugador. */
    public PlayerMobRegistry playerMobs() {
        return this.playerMobs;
    }

    public MobKeys keys() {
        return this.keys;
    }

    public TargetPolicy policy() {
        return this.policy;
    }

    public TeamLink teamLink() {
        return this.teamLink;
    }

    public FactionBook factions() {
        return this.factions;
    }

    public DisguiseLink disguiseLink() {
        return this.disguiseLink;
    }

    public SpawnerRegistry spawners() {
        return this.spawners;
    }

    public PluginConfig config() {
        return this.config;
    }

    /** {@code true} si la IA se gestiona con la API MobGoals de Paper. */
    public boolean usesGoals() {
        return this.paperGoals;
    }

    public Collection<CustomMob> active() {
        return List.copyOf(this.active.values());
    }

    /**
     * Mobs de jugador vivos que pertenecen a este jugador.
     *
     * <p>Sale del registro persistente, no de las entidades cargadas: un mob anclado a un
     * bloque fijo puede estar en un chunk descargado y aun asi ocupar cupo.
     */
    public int countPlayerMobs(UUID ownerId) {
        // Solo los desplegados ocupan cupo: un mob guardado en su huevo no esta en el mundo.
        return this.playerMobs.deployedCount(ownerId);
    }

    /**
     * Cuenta de cupo a la que pertenece un mob de jugador. Son <b>tres cuentas
     * independientes</b>: llenar una no consume las otras.
     */
    public enum Account {
        /** Sigue al dueno. */
        OWNER,
        /** Fijo a un bloque. */
        POINT,
        /** Montura. */
        MOUNT
    }

    /** Cuenta de cupo a la que va esa definicion. */
    public static Account accountOf(MobDefinition definition) {
        if (definition.mountable()) {
            return Account.MOUNT;
        }
        return definition.leash().anchoredToPoint() ? Account.POINT : Account.OWNER;
    }

    /** Mobs desplegados de una cuenta concreta. */
    public int countPlayerMobs(UUID ownerId, Account account) {
        int count = 0;
        for (PlayerMobRegistry.MobLink link : this.playerMobs.all()) {
            if (ownerId == null || !link.deployed() || !ownerId.equals(link.owner())) {
                continue;
            }
            MobDefinition definition = this.registry.get(link.definitionId()).orElse(null);
            if (definition != null && accountOf(definition) == account) {
                count++;
            }
        }
        return count;
    }

    /** Atajo para las dos cuentas de siempre: al dueno o a un bloque. */
    public int countPlayerMobs(UUID ownerId, boolean pointAnchored) {
        return this.countPlayerMobs(ownerId, pointAnchored ? Account.POINT : Account.OWNER);
    }

    /**
     * Engancha el servicio de estilos. Se hace despues de construir porque el estilo
     * necesita al servicio de mobs, y el servicio de mobs necesita al estilo para
     * aplicar el nombre: seria un ciclo en el constructor.
     */
    public void attachStyles(StyleService styles) {
        this.styles = styles;
    }

    /**
     * {@code true} si el plugin funciona en ese mundo.
     *
     * <p>La lista vacia significa "todos". Los mundos se identifican por nombre, asi que
     * sirve cualquier mundo de Bukkit — incluidos los que crea Multiverse-Core.
     */
    public boolean worldEnabled(World world) {
        if (world == null) {
            return false;
        }
        List<String> enabled = this.config.enabledWorlds();
        return enabled.isEmpty() || enabled.contains(world.getName().toLowerCase(java.util.Locale.ROOT));
    }

    /** Lee los grupos del jugador (LuckPerms, o el respaldo sin el). */
    public GroupLink groupLink() {
        return this.groupLink;
    }

    /**
     * Cupo del jugador, segun su grupo de LuckPerms.
     *
     * <p>Si pertenece a varios grupos gana el cupo mas alto: es lo menos sorprendente
     * cuando un rango hereda de otro. Si ninguno tiene cupo mapeado en el config, o no
     * se pudieron leer sus grupos, se usa el cupo por defecto.
     */
    public int limitOf(UUID ownerId) {
        return this.limitOf(ownerId, Account.OWNER);
    }

    /** Cupo de mobs anclados a un bloque, segun el grupo de LuckPerms. */
    public int pointLimitOf(UUID ownerId) {
        return this.limitOf(ownerId, Account.POINT);
    }

    /** Cupo de monturas, segun el grupo de LuckPerms. */
    public int mountLimitOf(UUID ownerId) {
        return this.limitOf(ownerId, Account.MOUNT);
    }

    /** Cupo de una cuenta: el del grupo mas alto que tenga mapeado, o el de por defecto. */
    public int limitOf(UUID ownerId, Account account) {
        Map<String, Integer> byGroup = switch (account) {
            case MOUNT -> this.config.mountGroupLimits();
            case POINT -> this.config.pointGroupLimits();
            case OWNER -> this.config.groupLimits();
        };
        int fallback = switch (account) {
            case MOUNT -> this.config.defaultMountMobs();
            case POINT -> this.config.defaultPointMobs();
            case OWNER -> this.config.defaultPlayerMobs();
        };
        int best = -1;
        if (ownerId != null) {
            for (String group : this.groupLink.groupsOf(ownerId)) {
                Integer limit = byGroup.get(group);
                if (limit != null && limit > best) {
                    best = limit;
                }
            }
        }
        return best >= 0 ? best : fallback;
    }

    /** {@code true} si el jugador ya alcanzo su maximo en esa cuenta. */
    public boolean atLimit(UUID ownerId, Account account) {
        int max = this.limitOf(ownerId, account);
        return max > 0 && this.countPlayerMobs(ownerId, account) >= max;
    }

    /** {@code true} si el jugador ya alcanzo su maximo de mobs anclados al dueno. */
    public boolean atPlayerLimit(UUID ownerId) {
        return this.atLimit(ownerId, Account.OWNER);
    }

    /** {@code true} si el jugador ya alcanzo su maximo de mobs anclados a un bloque. */
    public boolean atPointLimit(UUID ownerId) {
        return this.atLimit(ownerId, Account.POINT);
    }

    /** {@code true} si el jugador ya alcanzo su maximo de monturas. */
    public boolean atMountLimit(UUID ownerId) {
        return this.atLimit(ownerId, Account.MOUNT);
    }

    /** Resultado de vaciar el cupo de un jugador. */
    public record PurgeResult(int removed, int unreachable) {
    }

    /**
     * Rehace el cupo de un jugador sin tocar sus mobs.
     *
     * <p>Deja en la cuenta solo los que se pueden ver ahora mismo. Una entrada de un mob
     * que ya no existe (murio sin avisar, lo borro otro plugin) desaparece y no vuelve;
     * una de un mob real que esta en un chunk descargado volvera a sumarse cuando su
     * chunk cargue. Sirve para corregir cuentas infladas sin matar nada.
     *
     * @return cuantas entradas fantasma se descartaron
     */
    public int resyncPlayerMobs(UUID ownerId) {
        if (ownerId == null) {
            return 0;
        }
        int before = this.playerMobs.linksOf(ownerId).size();
        // Un vinculo que dice estar desplegado pero cuya entidad esta cargada y muerta
        // es fantasma: se borra. Los que estan en chunks descargados no se tocan.
        int ghosts = 0;
        for (UUID linkId : this.playerMobs.linksOf(ownerId)) {
            var link = this.playerMobs.byLink(linkId).orElse(null);
            if (link == null || !link.deployed()) {
                continue;
            }
            LivingEntity entity = this.active.containsKey(link.entityId())
                    ? this.active.get(link.entityId()).entity() : null;
            if (entity != null && (entity.isDead() || !entity.isValid())) {
                this.playerMobs.removeLink(linkId);
                this.active.remove(link.entityId());
                ghosts++;
            }
        }
        this.playerMobs.save();
        return Math.max(0, ghosts);
    }

    /**
     * Retira los mobs de un jugador y deja su cupo a cero.
     *
     * <p>Solo alcanza los que estan cargados. Un mob anclado a un bloque en un chunk
     * descargado no se puede tocar hasta que ese chunk cargue; cuando lo haga, volvera a
     * contar solo, porque el registro se repuebla al cargar la entidad.
     *
     * <p>Es destructivo: los mobs que alcanza desaparecen del mundo.
     *
     * @return cuantos se retiraron y cuantos quedaron fuera de alcance
     */
    public PurgeResult purgePlayerMobs(UUID ownerId) {
        if (ownerId == null) {
            return new PurgeResult(0, 0);
        }
        List<LivingEntity> doomed = new ArrayList<>();
        for (CustomMob customMob : this.active.values()) {
            if (ownerId.equals(customMob.ownerId())) {
                doomed.add(customMob.entity());
            }
        }
        for (LivingEntity entity : doomed) {
            this.active.remove(entity.getUniqueId());
            entity.remove();
        }
        // Borra tambien los vinculos: los huevos que los llevaban quedan inertes.
        int forgotten = this.playerMobs.resetOwner(ownerId);
        this.playerMobs.save();
        return new PurgeResult(doomed.size(), Math.max(0, forgotten - doomed.size()));
    }

    /**
     * Destruye mobs de un jugador: los desplegados y los que tenga guardados.
     *
     * <p>Es la via del propio jugador para deshacerse de los suyos, ya que el plugin le
     * impide matarlos a golpes. Los huevos que los representaban quedan inertes, porque
     * el vinculo se borra.
     *
     * @param definitionId definicion a destruir, o {@code null} para todas
     * @return cuantos vinculos se borraron
     */
    public int dismiss(UUID ownerId, String definitionId) {
        int removed = 0;
        for (UUID linkId : Set.copyOf(this.playerMobs.linksOf(ownerId))) {
            PlayerMobRegistry.MobLink link = this.playerMobs.byLink(linkId).orElse(null);
            if (link == null
                    || (definitionId != null && !definitionId.equalsIgnoreCase(link.definitionId()))) {
                continue;
            }
            CustomMob customMob = link.deployed() ? this.active.get(link.entityId()) : null;
            if (customMob != null) {
                // Quita la entidad y, de paso, el vinculo.
                this.despawn(customMob);
            } else {
                this.playerMobs.removeLink(linkId);
            }
            removed++;
        }
        this.playerMobs.save();
        return removed;
    }

    /** Cupo restante del jugador; {@code -1} si no hay limite configurado. */
    public int remainingPlayerMobs(UUID ownerId) {
        return this.remainingPlayerMobs(ownerId, false);
    }

    /** Cupo restante de una cuenta; {@code -1} si no hay limite configurado. */
    public int remainingPlayerMobs(UUID ownerId, Account account) {
        int max = this.limitOf(ownerId, account);
        return max <= 0 ? -1 : Math.max(0, max - this.countPlayerMobs(ownerId, account));
    }

    /** Cupo restante de las dos cuentas de siempre; {@code -1} si no hay limite. */
    public int remainingPlayerMobs(UUID ownerId, boolean pointAnchored) {
        return this.remainingPlayerMobs(ownerId, pointAnchored ? Account.POINT : Account.OWNER);
    }

    /**
     * Trae la entidad de un vinculo aunque su chunk este descargada.
     *
     * <p>Se pide un ticket en su ultima posicion conocida, se busca la entidad y se suelta
     * el ticket. Sin esto, un mob que quedo en una chunk descargada no lo alcanza ningun
     * comando: no se le ve, no se le mata, y sin embargo sigue ocupando cupo. El vinculo ya
     * guardaba donde se le vio; esto es usarlo.
     */
    public Optional<LivingEntity> reach(UUID linkId) {
        PlayerMobRegistry.MobLink link = this.playerMobs.byLink(linkId).orElse(null);
        if (link == null || !link.deployed()) {
            return Optional.empty();
        }
        LivingEntity loaded = this.active.containsKey(link.entityId())
                ? this.active.get(link.entityId()).entity() : null;
        if (loaded != null && loaded.isValid()) {
            return Optional.of(loaded);
        }
        Location point = link.location();
        if (point == null) {
            return Optional.empty();
        }
        World world = point.getWorld();
        int chunkX = point.getBlockX() >> 4;
        int chunkZ = point.getBlockZ() >> 4;
        world.addPluginChunkTicket(chunkX, chunkZ, this.plugin);
        try {
            Entity entity = Bukkit.getEntity(link.entityId());
            return entity instanceof LivingEntity living && living.isValid()
                    ? Optional.of(living) : Optional.empty();
        } finally {
            world.removePluginChunkTicket(chunkX, chunkZ, this.plugin);
        }
    }

    /**
     * Mata todos los mobs de una definicion, tambien los que estan en chunks descargadas.
     *
     * <p>Los de una chunk descargada se alcanzan por su vinculo: se carga su chunk un
     * momento, se les mata y se suelta. Es lo que permite limpiar un mob perdido sin saber
     * donde esta ni tener que ir a buscarlo.
     */
    public int killDefinition(String definitionId) {
        int killed = 0;
        Set<UUID> seen = new HashSet<>();
        for (CustomMob customMob : this.active()) {
            if (!customMob.definition().id().equals(definitionId)) {
                continue;
            }
            seen.add(customMob.entity().getUniqueId());
            customMob.entity().setHealth(0.0D);
            killed++;
        }
        for (PlayerMobRegistry.MobLink link : this.playerMobs.all()) {
            if (!link.deployed() || !link.definitionId().equals(definitionId)
                    || seen.contains(link.entityId())) {
                continue;
            }
            LivingEntity reached = this.reach(link.linkId()).orElse(null);
            if (reached != null) {
                reached.setHealth(0.0D);
                killed++;
            }
        }
        return killed;
    }

    public Optional<CustomMob> find(UUID entityId) {
        return Optional.ofNullable(this.active.get(entityId));
    }

    /** Comprueba una sola vez si el servidor expone la API MobGoals. */
    public void probeGoals() {
        try {
            Bukkit.getMobGoals();
            this.paperGoals = true;
        } catch (Throwable throwable) {
            this.paperGoals = false;
            this.plugin.getLogger().info("API MobGoals no disponible. Se usara el modo por eventos.");
        }
    }

    /** Invoca un mob. Los mobs de servidor ignoran el dueno aunque se pase uno. */
    public LivingEntity spawn(MobDefinition definition, Location location, Player owner) {
        if (owner == null || definition.server()) {
            return this.spawn(definition, location, null, null, null, false);
        }
        // Un mob de jugador nace siempre vinculado a un huevo: es lo que lo representa
        // despues, y lo que permite guardarlo y volver a desplegarlo.
        return this.deploy(definition, location, owner, null)
                .map(link -> this.active.get(link.entityId()))
                .map(CustomMob::entity)
                .orElse(null);
    }

    /**
     * Despliega un mob de jugador desde un huevo.
     *
     * @param linkId vinculo ya existente (huevo ya vinculado), o {@code null} para crear uno
     * @return el vinculo usado; vacio si no se pudo desplegar
     */
    public Optional<PlayerMobRegistry.MobLink> deploy(MobDefinition definition, Location location,
                                                     Player owner, UUID linkId) {
        if (owner == null || definition.server() || !this.worldEnabled(location.getWorld())
                || this.atLimit(owner.getUniqueId(), accountOf(definition))) {
            return Optional.empty();
        }
        PlayerMobRegistry.MobLink link = linkId == null ? null
                : this.playerMobs.byLink(linkId).orElse(null);
        boolean created = link == null;
        // Estado guardado al recogerlo, si lo hay: el mob vuelve como estaba.
        MobState state = created ? null : link.state();
        if (created) {
            link = this.playerMobs.create(owner.getUniqueId(), definition.id());
        }

        LivingEntity entity = this.spawn(definition, location, owner, null, link.linkId(), false);
        if (entity == null) {
            if (created) {
                this.playerMobs.removeLink(link.linkId());
            }
            return Optional.empty();
        }
        this.playerMobs.deploy(link.linkId(), entity.getUniqueId());
        if (state != null) {
            // Se devuelve el estado y se olvida: solo vale mientras el mob esta guardado.
            state.applyTo(entity);
            this.playerMobs.clearState(link.linkId());
        }
        // Se anota donde nacio: para un mob anclado a un bloque, ese punto ES su ancla, y
        // hace falta saberlo aunque su chunk se descargue.
        this.playerMobs.move(link.linkId(), entity.getLocation());
        this.playerMobs.save();
        return this.playerMobs.byLink(link.linkId());
    }

    /** Invoca un mob sin dueno: mobs de servidor y pruebas por consola. */
    public LivingEntity spawn(MobDefinition definition, Location location) {
        return this.spawn(definition, location, null, null, null, false);
    }

    /**
     * Invoca un mob <b>natural</b>: de una sola vida y sin dejar spawner.
     *
     * <p>La diferencia con {@link #spawn(MobDefinition, Location)} es justo esa: un mob
     * natural no escribe en {@code data/spawners.yml}. Si lo hiciera, cada aparicion al azar
     * dejaria un punto que reaparece para siempre, que es lo contrario de una sola vida.
     *
     * <p>Ademas no es persistente: el propio Minecraft lo retira cuando no tiene a nadie
     * cerca, como a cualquier bicho del mundo.
     */
    public LivingEntity spawnNatural(MobDefinition definition, Location location) {
        LivingEntity entity = this.spawn(definition, location, null, null, null, true);
        if (entity != null) {
            this.natural.add(entity.getUniqueId());
        }
        return entity;
    }

    /** Mobs naturales vivos ahora mismo, de todos o de una definicion. */
    public int naturalCount() {
        return this.natural.size();
    }

    public int naturalCount(String definitionId) {
        int count = 0;
        for (UUID id : this.natural) {
            CustomMob customMob = this.active.get(id);
            if (customMob != null && customMob.definition().id().equals(definitionId)) {
                count++;
            }
        }
        return count;
    }

    private LivingEntity spawn(MobDefinition definition, Location location, Player owner,
                               UUID knownSpawnerId, UUID linkId, boolean natural) {
        World world = location.getWorld();
        if (world == null || !this.worldEnabled(world)) {
            return null;
        }
        Class<? extends Entity> type = definition.entityType().getEntityClass();
        if (type == null) {
            return null;
        }
        UUID ownerId = owner != null && !definition.server() ? owner.getUniqueId() : null;

        // El disfraz se encola antes de crear la entidad: si no, se ve el mob base un instante.
        this.disguiseLink.applyNext(definition);
        // La configuracion va dentro del consumidor de aparicion: asi el nombre ya esta
        // puesto cuando otros plugins (MobHealth, por ejemplo) ven la entidad por primera vez.
        Entity spawned = world.spawn(location, type, entity -> {
            if (entity instanceof Mob mob) {
                this.configure(mob, definition, ownerId, knownSpawnerId, location, linkId, natural);
            }
        });
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            return null;
        }
        this.register(mob);
        return mob;
    }

    /** Configura una entidad ya existente y la registra. */
    public void prepare(Mob mob, MobDefinition definition, UUID ownerId) {
        this.configure(mob, definition, ownerId, null, mob.getLocation(), null, false);
        this.register(mob);
    }

    /**
     * Marca la entidad como mob custom. Se invoca desde el consumidor de aparicion de
     * Paper, antes de que la entidad entre al mundo.
     */
    private void configure(Mob mob, MobDefinition definition, UUID ownerId, UUID knownSpawnerId,
                           Location spawn, UUID linkId, boolean natural) {
        // Un mob natural se comporta como cualquier bicho del mundo: no es persistente y el
        // servidor lo retira cuando nadie lo tiene cerca. Los demas son contenido del
        // jugador o del servidor y no deben irse solos.
        mob.setPersistent(!natural);
        mob.setRemoveWhenFarAway(natural);
        mob.setCanPickupItems(false);
        mob.setCustomName(definition.displayName());
        mob.setCustomNameVisible(true);
        if (definition.glow()) {
            mob.setGlowing(true);
        }

        this.applyAttributes(mob, definition);
        if (definition.mount() != null) {
            // Una montura no lleva el equipo normal: lo suyo es la silla y la armadura.
            this.configureMount(mob, definition.mount(), ownerId);
        } else {
            EquipmentApplier.apply(mob, definition, this.plugin.drops().catalog(),
                    problem -> this.plugin.getLogger().warning(problem + " en " + definition.id()));
        }

        // El mob aparece como dice la definicion y nada mas. El juego sortea rasgos
        // visibles al crear ciertas entidades —la bandera de jefe de patrulla, por
        // ejemplo— y esos no los controla el servidor: se apagan de raiz.
        if (mob instanceof Raider raider) {
            raider.setPatrolLeader(false);
            raider.setCanJoinRaid(false);
            raider.setPatrolTarget(null);
            raider.setCelebrating(false);
        }

        UUID teamId = ownerId != null ? this.teamLink.teamOf(ownerId).orElse(null) : null;
        var container = mob.getPersistentDataContainer();
        container.set(this.keys.definition(), PersistentDataType.STRING, definition.id());
        if (linkId != null) {
            container.set(this.keys.link(), PersistentDataType.STRING, linkId.toString());
        }
        if (ownerId != null) {
            container.set(this.keys.owner(), PersistentDataType.STRING, ownerId.toString());
        }
        if (teamId != null) {
            container.set(this.keys.team(), PersistentDataType.STRING, teamId.toString());
        }

        // Punto de aparicion: ancla del leash y origen de la reaparicion.
        World spawnWorld = spawn.getWorld();
        if (spawnWorld != null) {
            container.set(this.keys.spawnWorld(), PersistentDataType.STRING, spawnWorld.getName());
            container.set(this.keys.spawnX(), PersistentDataType.DOUBLE, spawn.getX());
            container.set(this.keys.spawnY(), PersistentDataType.DOUBLE, spawn.getY());
            container.set(this.keys.spawnZ(), PersistentDataType.DOUBLE, spawn.getZ());
        }
        UUID spawnerId = knownSpawnerId;
        // Un mob natural NUNCA deja spawner: es de una sola vida.
        if (spawnerId == null && !natural && definition.server()
                && definition.respawnSeconds() > 0 && spawnWorld != null) {
            spawnerId = UUID.randomUUID();
            this.spawners.put(new SpawnerEntry(spawnerId, definition.id(), spawnWorld.getName(),
                    spawn.getX(), spawn.getY(), spawn.getZ(), definition.respawnSeconds(), 0L));
            this.spawners.save();
        }
        if (spawnerId != null) {
            container.set(this.keys.spawner(), PersistentDataType.STRING, spawnerId.toString());
        }
    }

    /**
     * Deja la montura lista para montarse: domesticada, con dueno, silla y variante.
     *
     * <p>Sin esto el jugador no podria subirse: un caballo sin domesticar lo tira al
     * suelo, y sin silla no acepta jinete.
     */
    private void configureMount(Mob mob, MountSpec spec, UUID ownerId) {
        if (!(mob instanceof AbstractHorse horse)) {
            // La categoria no encaja con el tipo: se ignora sin romper la aparicion.
            return;
        }
        if (spec.tamed()) {
            horse.setTamed(true);
        }
        if (ownerId != null) {
            horse.setOwner(Bukkit.getOfflinePlayer(ownerId));
        }
        if (spec.saddled()) {
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
        if (horse instanceof Horse real) {
            // La variante se fija SIEMPRE. Sin esto el caballo sale de un color al azar,
            // asi que al recogerlo y volver a colocarlo parece otro: es justo lo que no
            // debe pasar con una cosa visible que el servidor no controla.
            Horse.Color color = colorOf(spec.color());
            real.setColor(color != null ? color : Horse.Color.WHITE);
            Horse.Style style = styleOf(spec.style());
            real.setStyle(style != null ? style : Horse.Style.NONE);
        }
        if (spec.armor() != null
                && horse.getInventory() instanceof ArmoredHorseInventory armored) {
            armored.setArmor(new ItemStack(spec.armor()));
        }
    }

    /** Variante de color del caballo, o {@code null} si el texto no vale. */
    private static Horse.Color colorOf(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Horse.Color.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Marcas del caballo, o {@code null} si el texto no vale. */
    private static Horse.Style styleOf(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Horse.Style.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void applyAttributes(Mob mob, MobDefinition definition) {
        for (Map.Entry<String, Double> entry : definition.attributes().entrySet()) {
            Attribute attribute = Attributes.resolve(entry.getKey());
            if (attribute == null) {
                this.plugin.getLogger().warning("Atributo desconocido '" + entry.getKey() + "' en " + definition.id());
                continue;
            }
            AttributeInstance instance = mob.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.setBaseValue(entry.getValue());
        }

        AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
        double max = health != null ? health.getBaseValue() : 20.0D;
        mob.setHealth(Math.max(1.0D, max));
    }

    /** Registra la entidad si lleva la marca de CustomMobs y aun no estaba registrada. */
    public void register(LivingEntity entity) {
        if (!this.worldEnabled(entity.getWorld())) {
            // Mundo fuera de la lista: el mob no se gestiona, asi que no cuenta cupo ni IA.
            return;
        }
        UUID entityId = entity.getUniqueId();
        if (this.active.containsKey(entityId)) {
            return;
        }
        // Un vinculo representa UN mob: si otra entidad viva lo reclama, esta es una copia
        // huerfana —un recogido que no llego a borrar la entidad— y se retira.
        UUID claimed = parseUuid(entity.getPersistentDataContainer()
                .get(this.keys.link(), PersistentDataType.STRING));
        if (claimed != null && this.isDuplicateLink(claimed, entityId)) {
            this.plugin.getLogger().warning("Se retira un mob duplicado del vinculo " + claimed + ".");
            entity.remove();
            return;
        }
        this.read(entity).ifPresent(customMob -> {
            this.active.put(entityId, customMob);
            // Reengancha el vinculo: la entidad trae su id en el PDC, asi que aunque el
            // registro se hubiera perdido, aqui se vuelve a saber a quien pertenece.
            if (customMob.ownerId() != null) {
                UUID linkId = parseUuid(entity.getPersistentDataContainer()
                        .get(this.keys.link(), PersistentDataType.STRING));
                if (linkId == null) {
                    linkId = UUID.randomUUID();
                    entity.getPersistentDataContainer()
                            .set(this.keys.link(), PersistentDataType.STRING, linkId.toString());
                }
                this.playerMobs.ensure(linkId, customMob.ownerId(), customMob.definition().id(), entityId);
            }
            // La definicion manda: reafirmamos el nombre al cargar. Un mob guardado con el
            // nombre que le impuso otro plugin lo conservaria para siempre si no. Encima va
            // el estilo que haya elegido el dueno (color del nombre y brillo).
            if (this.styles != null) {
                this.styles.applyName(entity, customMob.definition(), customMob.ownerId(), customMob.teamId());
                this.styles.applyGlow(entity, customMob.definition(), customMob.ownerId(), customMob.teamId());
            } else {
                entity.setCustomName(customMob.definition().displayName());
                entity.setCustomNameVisible(true);
            }
            this.disguiseLink.apply(entity, customMob.definition());
            // Un mob de rol con 'ai: false' se queda quieto en su puesto.
            if (!customMob.definition().usesAi() && entity instanceof Mob mob) {
                mob.setAI(false);
            }
            // Una montura no busca objetivos: la lleva su jinete.
            if (!customMob.definition().mountable()) {
                this.applyBehaviour(entity);
            }
        });
    }

    /** Reconstruye el vinculo leyendo el PersistentDataContainer. */
    public Optional<CustomMob> read(LivingEntity entity) {
        var container = entity.getPersistentDataContainer();
        String definitionId = container.get(this.keys.definition(), PersistentDataType.STRING);
        if (definitionId == null) {
            return Optional.empty();
        }
        Optional<MobDefinition> definition = this.registry.get(definitionId);
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        UUID ownerId = parseUuid(container.get(this.keys.owner(), PersistentDataType.STRING));
        UUID teamId = parseUuid(container.get(this.keys.team(), PersistentDataType.STRING));
        return Optional.of(new CustomMob(entity, definition.get(), ownerId, teamId));
    }

    private void applyBehaviour(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !this.paperGoals) {
            return;
        }
        try {
            PaperGoalsApplier.apply(this, mob);
        } catch (Throwable throwable) {
            this.paperGoals = false;
            this.plugin.getLogger().info("API MobGoals no disponible ("
                    + throwable.getClass().getSimpleName() + "). Se usara el modo por eventos.");
        }
    }

    /** Restaura los mobs ya cargados al arrancar el plugin. */
    public void restoreAll() {
        int restored = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                int before = this.active.size();
                this.register(living);
                if (this.active.size() > before) {
                    restored++;
                }
            }
        }
        this.plugin.getLogger().info("Mobs custom restaurados: " + restored);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity living) {
                this.register(living);
            }
        }
    }

    /** Quita del registro los mobs muertos o descargados. */
    /** {@code true} si ese vinculo ya lo reclama otra entidad viva. */
    private boolean isDuplicateLink(UUID linkId, UUID entityId) {
        PlayerMobRegistry.MobLink link = this.playerMobs.byLink(linkId).orElse(null);
        if (link == null || !link.deployed() || entityId.equals(link.entityId())) {
            return false;
        }
        Entity current = Bukkit.getEntity(link.entityId());
        return current != null && current.isValid();
    }

    public void prune() {
        // Un natural que ya no existe deja de ocupar tope.
        this.natural.removeIf(id -> !this.active.containsKey(id));
        this.active.values().removeIf(customMob -> {
            LivingEntity entity = customMob.entity();
            return !entity.isValid() || entity.isDead();
        });
        // El cupo se toca al invocar y al morir: se guarda al vuelo, no en cada cambio.
        if (this.playerMobs.isDirty()) {
            this.playerMobs.save();
        }
        if (this.styles != null && this.styles.registry().isDirty()) {
            this.styles.registry().save();
        }
    }

    // ------------------------------------------------------------------ ancla y spawner

    /** Punto de aparicion guardado en la entidad, si lo tiene. */
    public Optional<Location> spawnPointOf(CustomMob customMob) {
        var container = customMob.entity().getPersistentDataContainer();
        String worldName = container.get(this.keys.spawnWorld(), PersistentDataType.STRING);
        Double x = container.get(this.keys.spawnX(), PersistentDataType.DOUBLE);
        Double y = container.get(this.keys.spawnY(), PersistentDataType.DOUBLE);
        Double z = container.get(this.keys.spawnZ(), PersistentDataType.DOUBLE);
        if (worldName == null || x == null || y == null || z == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x, y, z));
    }

    /** Identificador del spawner de una entidad, o {@code null} si no lo es. */
    public UUID spawnerIdOf(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(this.keys.spawner(), PersistentDataType.STRING);
        return parseUuid(raw);
    }

    /** Punto de referencia del mob: su dueno, o el bloque donde aparecio. */
    public Location anchorOf(CustomMob customMob) {
        if (!customMob.definition().leash().anchoredToPoint()) {
            Player owner = this.onlineOwner(customMob);
            return owner != null ? owner.getLocation() : null;
        }
        return this.spawnPointOf(customMob).orElse(null);
    }

    /** {@code true} si el mob esta mas alla del radio maximo respecto a su ancla. */
    public boolean isBeyondLeash(CustomMob customMob, Mob mob) {
        LeashSettings leash = customMob.definition().leash();
        if (!leash.enabled()) {
            return false;
        }
        Location anchor = this.anchorOf(customMob);
        if (anchor == null) {
            return false;
        }
        if (!anchor.getWorld().getName().equals(mob.getWorld().getName())) {
            return true;
        }
        return mob.getLocation().distance(anchor) > leash.maxDistance();
    }

    /**
     * Devuelve al mob hacia su ancla cuando se aleja mas del radio configurado.
     * Si supera la distancia de teletransporte, se reubica directamente.
     */
    public void enforceLeash(CustomMob customMob, Mob mob) {
        LeashSettings leash = customMob.definition().leash();
        if (!leash.enabled()) {
            return;
        }
        // Una montura NO se teletransporta detras de su dueno. Si la dejas atras se queda
        // donde esta, y si no vuelves a por ella o la recoges, la ventana de abandono se
        // la lleva. Aparecer de golpe a la espalda era lo contrario de montar.
        if (customMob.definition().mountable()) {
            return;
        }
        Location anchor = this.anchorOf(customMob);
        if (anchor == null) {
            return;
        }

        if (!anchor.getWorld().getName().equals(mob.getWorld().getName())) {
            // Solo sigue a su dueno a un mundo habilitado. A uno excluido no puede ir, y
            // forzarlo lo sacaria de la gestion del plugin: se deja donde esta y el
            // servicio de abandono decide su suerte.
            if (leash.teleportDistance() > 0.0D && this.worldEnabled(anchor.getWorld())) {
                mob.teleport(anchor);
                // Al cambiar de mundo el cliente rehace las entidades: hay que reenviar el
                // disfraz y el estilo, o el mob vuelve a verse con su forma base.
                this.refreshAppearance(customMob, mob);
            }
            return;
        }

        double distance = mob.getLocation().distance(anchor);
        if (distance <= leash.maxDistance()) {
            return;
        }

        // Fuera de radio: suelta el objetivo y vuelve al ancla.
        mob.setTarget(null);

        if (leash.teleportDistance() > 0.0D && distance > leash.teleportDistance()) {
            mob.teleport(anchor);
            return;
        }

        try {
            mob.getPathfinder().moveTo(anchor, leash.returnSpeed());
        } catch (Throwable ignored) {
            // Servidor sin la API Pathfinder de Paper.
        }
    }

    /**
     * Reenvia la apariencia cuando el mob cambia de mundo, lo haga quien lo haga: el
     * teletransporte del leash, un portal, o el de otro plugin.
     *
     * <p>El cliente rehace las entidades al entrar en un mundo nuevo, asi que el disfraz y
     * el estilo se quedan por el camino si no se mandan otra vez.
     */
    @EventHandler(ignoreCancelled = true)
    public void onWorldChange(EntityTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || from.getWorld().equals(to.getWorld())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        CustomMob customMob = this.active.get(living.getUniqueId());
        if (customMob == null) {
            return;
        }
        // El evento llega ANTES del salto: se reintenta despues. Dos intentos porque un
        // portal no viaja necesariamente en el mismo tick.
        for (long delay : new long[]{2L, 20L}) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                if (living.isValid() && living instanceof Mob mob
                        && to.getWorld().equals(mob.getWorld())) {
                    this.refreshAppearance(customMob, mob);
                }
            }, delay);
        }
    }

    /** Reenvia nombre, brillo y disfraz a una entidad viva. */
    private void refreshAppearance(CustomMob customMob, Mob mob) {
        if (this.styles != null) {
            this.styles.applyName(mob, customMob.definition(), customMob.ownerId(), customMob.teamId());
            this.styles.applyGlow(mob, customMob.definition(), customMob.ownerId(), customMob.teamId());
        }
        this.disguiseLink.refresh(mob, customMob.definition());
    }

    /** Un mob de jugador que muere deja libre su cupo y limpia su brillo. */
    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (this.styles != null) {
            this.styles.clear(event.getEntity());
        }
        // El mob murio: se borra su vinculo, asi que el huevo que lo llevaba queda inerte.
        this.playerMobs.byEntity(event.getEntity().getUniqueId())
                .ifPresent(link -> this.playerMobs.removeLink(link.linkId()));
    }

    /**
     * Guarda el mob de ese vinculo: sale del mundo y queda dentro de su huevo.
     *
     * <p>El vinculo NO se borra —eso es lo que lo distingue de {@link #despawn}—, asi que
     * el huevo sigue sirviendo para volver a desplegarlo.
     */
    public boolean store(UUID linkId) {
        PlayerMobRegistry.MobLink link = this.playerMobs.byLink(linkId).orElse(null);
        if (link == null || !link.deployed()) {
            return false;
        }
        CustomMob customMob = this.active.get(link.entityId());
        if (customMob != null) {
            // El estado se lee ANTES de quitar la entidad: es lo unico que quedara de ella.
            this.playerMobs.saveState(linkId, MobState.capture(customMob.entity(), customMob.definition()));
            if (this.styles != null) {
                this.styles.clear(customMob.entity());
            }
            this.active.remove(link.entityId());
            customMob.entity().remove();
        } else {
            // El vinculo decia "desplegado" pero la entidad no estaba en la lista de
            // activos. Antes se dejaba viva y quedaba un mob huerfano que su huevo ya no
            // podia recoger; ahora se quita igual.
            Entity orphan = Bukkit.getEntity(link.entityId());
            if (orphan instanceof LivingEntity living) {
                MobDefinition definition = this.registry.get(link.definitionId()).orElse(null);
                this.playerMobs.saveState(linkId, MobState.capture(living, definition));
                if (this.styles != null) {
                    this.styles.clear(living);
                }
                orphan.remove();
            }
        }
        this.playerMobs.store(linkId);
        this.playerMobs.save();
        return true;
    }

    /** Elimina un mob y, si era un spawner, deja de reanimarlo. */
    public void despawn(CustomMob customMob) {
        if (this.styles != null) {
            this.styles.clear(customMob.entity());
        }
        this.playerMobs.byEntity(customMob.entity().getUniqueId())
                .ifPresent(link -> this.playerMobs.removeLink(link.linkId()));
        UUID spawnerId = this.spawnerIdOf(customMob.entity());
        if (spawnerId != null) {
            this.spawners.remove(spawnerId);
            this.spawners.save();
        }
        this.active.remove(customMob.entity().getUniqueId());
        customMob.entity().remove();
    }

    /** Si el mob pertenecia a un spawner, programa su reaparicion. */
    public void handleSpawnerDeath(LivingEntity entity) {
        UUID spawnerId = this.spawnerIdOf(entity);
        if (spawnerId == null) {
            return;
        }
        Optional<SpawnerEntry> entry = this.spawners.get(spawnerId);
        if (entry.isEmpty()) {
            return;
        }
        if (!entry.get().respawns()) {
            this.spawners.remove(spawnerId);
            this.spawners.save();
            return;
        }
        SpawnerEntry pending = entry.get().awaiting(
                System.currentTimeMillis() + entry.get().respawnSeconds() * 1000L);
        this.spawners.put(pending);
        this.spawners.save();
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
                () -> this.respawnNow(pending), entry.get().respawnSeconds() * 20L);
    }

    /**
     * Reanuda los spawners que quedaron esperando reaparicion. Los que no tienen marca
     * se dejan en paz: su mob sigue en el mundo (aunque el chunk este descargado).
     */
    public void resumeSpawners() {
        long now = System.currentTimeMillis();
        int resumed = 0;
        int scheduled = 0;
        for (SpawnerEntry entry : this.spawners.all()) {
            if (!entry.awaitingRespawn()) {
                continue;
            }
            long remaining = entry.respawnAt() - now;
            if (remaining <= 0L) {
                if (this.respawnNow(entry)) {
                    resumed++;
                }
            } else {
                this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
                        () -> this.respawnNow(entry), Math.max(1L, remaining / 50L));
                scheduled++;
            }
        }
        if (resumed > 0 || scheduled > 0) {
            this.plugin.getLogger().info("Spawners pendientes: " + resumed + " reaparecidos, "
                    + scheduled + " programados.");
        }
    }

    private boolean respawnNow(SpawnerEntry entry) {
        MobDefinition definition = this.registry.get(entry.definitionId()).orElse(null);
        World world = Bukkit.getWorld(entry.world());
        if (definition == null || world == null) {
            this.plugin.getLogger().warning("Spawner sin definicion o mundo validos: " + entry.id());
            return false;
        }
        LivingEntity spawned = this.spawn(definition, new Location(world, entry.x(), entry.y(), entry.z()),
                null, entry.id(), null, false);
        if (spawned != null) {
            this.spawners.put(entry.alive());
            this.spawners.save();
            this.plugin.getLogger().info("Spawner reactivado: " + definition.id() + " en " + entry.world()
                    + " (" + (int) entry.x() + ", " + (int) entry.y() + ", " + (int) entry.z() + ")");
        }
        return spawned != null;
    }

    // ------------------------------------------------------------------ bando protegido

    public AggroRegistry aggro() {
        return this.aggro;
    }

    /**
     * {@code true} si la entidad agredida forma parte del bando protegido por el mob:
     * el propio mob, su dueno, un companero o aliado suyo, o un mob del mismo bando.
     */
    public boolean isProtectedSide(CustomMob customMob, LivingEntity candidate) {
        // Defensa propia: si lo agreden a el, reacciona siempre, sin depender
        // de 'aggro-on-friendly-mobs'.
        if (customMob.entity().getUniqueId().equals(candidate.getUniqueId())) {
            return true;
        }
        if (customMob.isOwner(candidate.getUniqueId())) {
            return true;
        }
        if (customMob.definition().server()) {
            return this.isProtectedFromServerMob(customMob, candidate);
        }
        if (candidate instanceof Player player) {
            if (this.teamLink.isAlly(customMob.ownerId(), player.getUniqueId())) {
                return true;
            }
            // Y si comparten faccion —o son aliadas— tambien son de los suyos, aunque no
            // compartan team: un mob de jugador es una extension de su dueno.
            String owner = customMob.ownerId() == null ? null
                    : this.plugin.playerFactions().of(customMob.ownerId()).orElse(null);
            String theirs = this.plugin.playerFactions().of(player.getUniqueId()).orElse(null);
            return owner != null && theirs != null
                    && (owner.equals(theirs) || this.factions.isAlly(owner, theirs));
        }
        Optional<CustomMob> other = this.find(candidate.getUniqueId());
        if (other.isEmpty() || !this.config.aggroOnFriendlyMobs()) {
            return false;
        }
        return this.isSameSide(customMob, other.get());
    }

    /**
     * Bando de un mob de servidor: los jugadores si es defensor, y los mobs de su
     * misma faccion o de una aliada.
     */
    /**
     * La faccion que cuenta para un mob: la suya, o —si es un mob de jugador— <b>la de su
     * dueno</b>. Un mob de jugador es una extension del jugador, y su bando es el de el.
     *
     * @return la faccion, o {@code null} si no tiene ninguna
     */
    public String factionOf(CustomMob customMob) {
        String own = customMob.definition().faction();
        if (own != null && !own.isBlank()) {
            return own;
        }
        return customMob.ownerId() == null ? null
                : this.plugin.playerFactions().of(customMob.ownerId()).orElse(null);
    }

    /** La faccion de un jugador, o {@code null} si no tiene. */
    public String factionOf(Player player) {
        return this.plugin.playerFactions().of(player.getUniqueId()).orElse(null);
    }

    /** {@code true} si dos facciones son la misma o aliadas. */
    public boolean sameSide(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return first.equals(second) || this.factions.isAlly(first, second);
    }

    private boolean isProtectedFromServerMob(CustomMob customMob, LivingEntity candidate) {
        String mine = customMob.definition().faction();
        if (candidate instanceof Player player) {
            if (customMob.definition().attitude() == Attitude.DEFENDER) {
                return true;
            }
            // Un jugador de la misma faccion —o de una aliada— es de los suyos: ni el ni
            // sus mobs le hacen dano.
            Optional<String> theirs = this.plugin.playerFactions().of(player.getUniqueId());
            if (mine != null && theirs.isPresent()) {
                String other = theirs.get();
                return mine.equals(other) || this.factions.isAlly(mine, other);
            }
            return false;
        }
        Optional<CustomMob> other = this.find(candidate.getUniqueId());
        if (other.isEmpty()) {
            return false;
        }
        if (mine == null) {
            return false;
        }
        String theirs = other.get().definition().faction();
        if (mine.equals(theirs)) {
            return true;
        }
        return theirs != null && this.factions.isAlly(mine, theirs);
    }

    private boolean isSameSide(CustomMob first, CustomMob second) {
        if (first.ownerId() == null || second.ownerId() == null) {
            return first.teamId() != null && first.teamId().equals(second.teamId());
        }
        if (first.isOwner(second.ownerId())) {
            return true;
        }
        return this.teamLink.isAlly(first.ownerId(), second.ownerId());
    }

    private Player onlineOwner(CustomMob customMob) {
        if (customMob.ownerId() == null) {
            return null;
        }
        Player owner = this.plugin.getServer().getPlayer(customMob.ownerId());
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return null;
        }
        return owner;
    }

    public void clear() {
        this.active.clear();
    }

    private static UUID parseUuid(String raw) {
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
