package com.juanp.custommobs.recall;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.config.PluginConfig;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.PlayerMobRegistry;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ventana de abandono: que pasa cuando un jugador deja atras un mob anclado a el.
 *
 * <p>Sin esto, un mob que no puede volver a su dueno —porque esta en otro mundo no
 * habilitado, o porque quedo demasiado lejos— se quedaba huerfano. Con esto recibe un
 * <b>plazo</b>:
 *
 * <ol>
 *   <li>Se pide un ticket de chunk en su posicion y se avisa al dueno por chat. El ticket
 *       es imprescindible: sin el, la chunk se descargaria y no habria forma de destruir
 *       al mob cuando venza el plazo.</li>
 *   <li>A los {@code recall.mob-seconds} el mob se destruye y su vinculo se borra, asi que
 *       su huevo queda inerte.</li>
 *   <li>A los {@code recall.chunk-seconds} se suelta el ticket y la chunk vuelve a lo normal.</li>
 * </ol>
 *
 * <p>Si el dueno vuelve antes del plazo, la cuenta se cancela y el mob se conserva.
 */
public final class RecallService extends BukkitRunnable {

    private final CustomMobsPlugin plugin;
    private final MobService service;
    private final PluginConfig config;

    /** linkId -&gt; cuenta atras en curso. */
    private final Map<UUID, Pending> pending = new HashMap<>();

    public RecallService(CustomMobsPlugin plugin, MobService service, PluginConfig config) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
    }

    /** Una cuenta atras: a quien pertenece el mob, donde esta y cuando empezo. */
    private static final class Pending {
        private final UUID ownerId;
        private final UUID worldId;
        private final int chunkX;
        private final int chunkZ;
        private final long startedAt;
        private boolean destroyed;

        private Pending(UUID ownerId, UUID worldId, int chunkX, int chunkZ, long startedAt) {
            this.ownerId = ownerId;
            this.worldId = worldId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.startedAt = startedAt;
        }
    }

    @Override
    public void run() {
        if (!this.config.recallEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.advance(now);
        this.scan(now);
    }

    /** Cierra las cuentas atras vencidas o ya resueltas. */
    private void advance(long now) {
        long mobMillis = this.config.recallMobSeconds() * 1000L;
        long chunkMillis = this.config.recallChunkSeconds() * 1000L;
        Iterator<Map.Entry<UUID, Pending>> iterator = this.pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next();
            Pending state = entry.getValue();
            PlayerMobRegistry.MobLink link = this.service.playerMobs()
                    .byLink(entry.getKey()).orElse(null);

            // El mob ya no esta desplegado: murio, lo recogieron o lo retiraron.
            if (link == null || !link.deployed()) {
                iterator.remove();
                this.releaseTicket(state);
                continue;
            }
            // El dueno volvio a su lado: la cuenta se cancela y el mob se conserva.
            if (!state.destroyed && this.recovered(state)) {
                iterator.remove();
                this.releaseTicket(state);
                continue;
            }
            if (!state.destroyed && now - state.startedAt >= mobMillis) {
                this.destroy(link);
                state.destroyed = true;
                this.notifyOwner(state, "&cTu " + this.nameOf(link.definitionId())
                        + " &cdesaparecio: lo dejaste atras.");
            }
            if (now - state.startedAt >= chunkMillis) {
                iterator.remove();
                this.releaseTicket(state);
            }
        }
    }

    /** Busca mobs de jugador que su dueno haya dejado atras. */
    private void scan(long now) {
        for (CustomMob customMob : this.service.active()) {
            if (!(customMob.entity() instanceof Mob mob) || !mob.isValid()) {
                continue;
            }
            if (customMob.definition().server() || customMob.ownerId() == null) {
                continue;
            }
            // Los mobs anclados a un punto no siguen a nadie: no se abandonan.
            if (customMob.definition().leash().anchoredToPoint()) {
                continue;
            }
            PlayerMobRegistry.MobLink link = this.service.playerMobs()
                    .byEntity(mob.getUniqueId()).orElse(null);
            if (link == null || !link.deployed() || this.pending.containsKey(link.linkId())) {
                continue;
            }
            // Se anota donde se le vio: si su chunk se descargara, hace falta saberlo.
            this.service.playerMobs().move(link.linkId(), mob.getLocation());

            if (this.abandoned(link.owner(), mob)) {
                this.start(link, mob, now);
            }
        }
    }

    /** {@code true} si el dueno ya no puede tener ese mob a su lado. */
    private boolean abandoned(UUID ownerId, Mob mob) {
        Player owner = Bukkit.getPlayer(ownerId);
        // Desconectado no es abandono: al volver, el mob sigue donde estaba.
        if (owner == null) {
            return false;
        }
        if (!owner.getWorld().equals(mob.getWorld())) {
            return true;
        }
        return owner.getLocation().distance(mob.getLocation()) > this.radiusOf(mob.getWorld());
    }

    /** {@code true} si el dueno volvio al lado del mob antes de que se destruya. */
    private boolean recovered(Pending state) {
        Player owner = Bukkit.getPlayer(state.ownerId);
        if (owner == null) {
            return false;
        }
        World world = Bukkit.getWorld(state.worldId);
        if (world == null || !owner.getWorld().equals(world)) {
            return false;
        }
        Location center = new Location(world, state.chunkX * 16.0D + 8.0D,
                owner.getLocation().getY(), state.chunkZ * 16.0D + 8.0D);
        return owner.getLocation().distance(center) <= this.radiusOf(world);
    }

    private void start(PlayerMobRegistry.MobLink link, Mob mob, long now) {
        Location location = mob.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        // El ticket mantiene la chunk cargada: sin el, el mob quedaria fuera de juego y no
        // habria forma de destruirlo cuando venza el plazo.
        world.addPluginChunkTicket(chunkX, chunkZ, this.plugin);
        Pending state = new Pending(link.owner(), world.getUID(), chunkX, chunkZ, now);
        this.pending.put(link.linkId(), state);
        this.notifyOwner(state, "&eTu " + this.nameOf(link.definitionId())
                + " &equedo atras. Desaparecera en " + this.config.recallMobSeconds()
                + " s; vuelve a acercarte para conservarlo.");
    }

    private void destroy(PlayerMobRegistry.MobLink link) {
        Optional<LivingEntity> entity = Optional.ofNullable(Bukkit.getEntity(link.entityId()))
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast);
        if (entity.isPresent()) {
            Optional<CustomMob> customMob = this.service.read(entity.get());
            if (customMob.isPresent()) {
                this.service.despawn(customMob.get());
                return;
            }
        }
        // No esta cargado (no deberia pasar con el ticket): al menos se corta el vinculo.
        this.service.playerMobs().removeLink(link.linkId());
        this.service.playerMobs().save();
    }

    private void notifyOwner(Pending state, String message) {
        Player owner = Bukkit.getPlayer(state.ownerId);
        if (owner != null) {
            this.plugin.status().title(owner, message, null);
        }
    }

    /** Suelta el ticket, salvo que otra cuenta atras siga necesitando esa misma chunk. */
    private void releaseTicket(Pending state) {
        for (Pending other : this.pending.values()) {
            if (other.worldId.equals(state.worldId)
                    && other.chunkX == state.chunkX && other.chunkZ == state.chunkZ) {
                return;
            }
        }
        World world = Bukkit.getWorld(state.worldId);
        if (world != null) {
            world.removePluginChunkTicket(state.chunkX, state.chunkZ, this.plugin);
        }
    }

    /** Radio de abandono: el configurado, o el derivado de la simulation-distance. */
    private double radiusOf(World world) {
        double configured = this.config.recallRadius();
        if (configured > 0.0D) {
            return configured;
        }
        return Math.max(16.0D, world.getSimulationDistance() * 16.0D);
    }

    private String nameOf(String definitionId) {
        return this.plugin.registry().get(definitionId)
                .map(MobDefinition::displayName)
                .orElse(definitionId);
    }

    /** Suelta todos los tickets y olvida las cuentas atras en curso. */
    public void shutdown() {
        this.pending.clear();
        for (World world : Bukkit.getWorlds()) {
            world.removePluginChunkTickets(this.plugin);
        }
    }
}
