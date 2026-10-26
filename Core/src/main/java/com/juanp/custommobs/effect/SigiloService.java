package com.juanp.custommobs.effect;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El sigilo de verdad: el jugador sigue existiendo para el cliente (asi conserva la
 * colision, la tab list y el dano normal), pero se oculta lo que se ve.
 *
 * <p>El cuerpo se oculta con {@link PotionEffectType#INVISIBILITY} sin particulas. La
 * armadura y los objetos en mano se ocultan con {@link Player#sendEquipmentChange} vacio,
 * que es un paquete por espectador: no hace falta sacar la entidad del cliente. La placa
 * de nombre la oculta la propia invisibilidad; no se tocan los equipos de scoreboard para
 * no chocar con TAB ni con el resto de plugins de equipos.
 *
 * <p>El sprint se limpia en {@link SigiloPacketListener}: sin el flag de sprint el cliente
 * no dibuja las particulas de correr.
 */
public final class SigiloService implements Listener {

    private static final EquipmentSlot[] ARMOR_AND_HANDS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final CustomMobsPlugin plugin;
    /** Jugadores en sigilo. */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    /** Ids de entidad ocultos, para el filtro de metadatos. */
    private final Set<Integer> hiddenEntities = ConcurrentHashMap.newKeySet();
    /** Id de entidad actual de cada oculto (cambia al reaparecer). */
    private final Map<UUID, Integer> entityIds = new ConcurrentHashMap<>();
    /** Id del efecto de {@code effects.yml} que concede el sigilo. */
    private final Map<UUID, String> sourceId = new ConcurrentHashMap<>();
    /** Si al atacar deben revelarse (lo pone el yml de la skill). */
    private final Map<UUID, Boolean> revealOnAttack = new ConcurrentHashMap<>();
    /** Cuando caduca el sigilo, en millis. Sirve para reponer la invisibilidad. */
    private final Map<UUID, Long> expiresAt = new ConcurrentHashMap<>();
    /** Si la invisibilidad la puso este servicio (para no quitar la de otro). */
    private final Set<UUID> ownInvisibility = ConcurrentHashMap.newKeySet();

    public SigiloService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isHidden(UUID id) {
        return this.hidden.contains(id);
    }

    /** Para el filtro de metadatos: {@code true} si esa entidad es un jugador oculto. */
    public boolean isHiddenEntity(int entityId) {
        return this.hiddenEntities.contains(entityId);
    }

    /**
     * Activa el sigilo.
     *
     * @param effectId       id del efecto de {@code effects.yml} que lo concede
     * @param seconds        duracion del efecto que lo concede
     * @param revealOnAttack si el jugador debe mostrarse al hacer dano
     */
    public void hide(Player player, String effectId, int seconds, boolean revealOnAttack) {
        UUID id = player.getUniqueId();
        this.sourceId.put(id, effectId);
        this.revealOnAttack.put(id, revealOnAttack);
        this.expiresAt.put(id, System.currentTimeMillis() + Math.max(1, seconds) * 1000L);
        this.hidden.add(id);
        this.syncEntityId(player);
        var listener = this.plugin.effectListener();
        if (listener != null) {
            listener.releaseMobs(player);
        }
        this.ensureInvisible(player);
        this.sendEmptyEquipment(player);
    }

    /** Devuelve al jugador a la normalidad. */
    public void show(Player player) {
        UUID id = player.getUniqueId();
        if (!this.hidden.remove(id)) {
            return;
        }
        this.hiddenEntities.remove(player.getEntityId());
        this.entityIds.remove(id);
        this.sourceId.remove(id);
        this.revealOnAttack.remove(id);
        this.expiresAt.remove(id);
        if (this.ownInvisibility.remove(id)
                && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        this.sendRealEquipment(player);
    }

    /** Para {@code onDisable}: nadie se queda invisible si se apaga el plugin. */
    public void showAll() {
        for (UUID id : Set.copyOf(this.hidden)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                this.show(player);
            }
        }
        this.hidden.clear();
        this.hiddenEntities.clear();
        this.entityIds.clear();
        this.sourceId.clear();
        this.revealOnAttack.clear();
        this.expiresAt.clear();
        this.ownInvisibility.clear();
    }

    /** Cada pocos ticks: repone invisibilidad y reenvia el equipo vacio. */
    public void refresh() {
        for (UUID id : this.hidden) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            Long until = this.expiresAt.get(id);
            if (until == null || until <= System.currentTimeMillis()) {
                this.show(player);
                continue;
            }
            this.ensureInvisible(player);
            this.sendEmptyEquipment(player);
        }
    }

    private void ensureInvisible(Player player) {
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }
        long remaining = this.expiresAt.getOrDefault(player.getUniqueId(),
                System.currentTimeMillis() + 1000L) - System.currentTimeMillis();
        int ticks = (int) Math.max(20L, (remaining + 999L) / 1000L * 20L);
        // particles = false: el remolino de la pocion tambien delataria.
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                ticks, 0, false, false, false));
        this.ownInvisibility.add(player.getUniqueId());
    }

    private void sendEmptyEquipment(Player player) {
        Map<EquipmentSlot, ItemStack> empty = emptyEquipment();
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, empty);
            }
        }
    }

    private void sendRealEquipment(Player player) {
        var equipment = player.getEquipment();
        if (equipment == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        real.put(EquipmentSlot.HAND, item(equipment.getItemInMainHand()));
        real.put(EquipmentSlot.OFF_HAND, item(equipment.getItemInOffHand()));
        real.put(EquipmentSlot.HEAD, item(equipment.getHelmet()));
        real.put(EquipmentSlot.CHEST, item(equipment.getChestplate()));
        real.put(EquipmentSlot.LEGS, item(equipment.getLeggings()));
        real.put(EquipmentSlot.FEET, item(equipment.getBoots()));
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, real);
            }
        }
    }

    private static Map<EquipmentSlot, ItemStack> emptyEquipment() {
        Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_AND_HANDS) {
            empty.put(slot, new ItemStack(Material.AIR));
        }
        return empty;
    }

    private static ItemStack item(ItemStack stack) {
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    /** Mantiene al dia el id de entidad del oculto (cambia al reaparecer). */
    private void syncEntityId(Player player) {
        UUID id = player.getUniqueId();
        Integer old = this.entityIds.put(id, player.getEntityId());
        if (old != null && old != player.getEntityId()) {
            this.hiddenEntities.remove(old);
        }
        this.hiddenEntities.add(player.getEntityId());
    }

    /** El que entra o cambia de mundo no debe ver a los ocultos que ya estan en su mundo. */
    private void hideFromViewer(Player viewer) {
        Map<EquipmentSlot, ItemStack> empty = emptyEquipment();
        for (UUID id : this.hidden) {
            Player hiddenPlayer = Bukkit.getPlayer(id);
            if (hiddenPlayer != null && !hiddenPlayer.equals(viewer)
                    && hiddenPlayer.getWorld().equals(viewer.getWorld())) {
                viewer.sendEquipmentChange(hiddenPlayer, empty);
            }
        }
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        // Un tick despues: el cliente tiene que haber recibido la entidad antes del equipo.
        this.later(() -> {
            if (this.hidden.contains(joined.getUniqueId())) {
                this.ensureInvisible(joined);
                this.sendEmptyEquipment(joined);
            }
            this.hideFromViewer(joined);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        this.later(() -> {
            if (this.hidden.contains(player.getUniqueId())) {
                this.syncEntityId(player);
                this.ensureInvisible(player);
                this.sendEmptyEquipment(player);
            }
            this.hideFromViewer(player);
        });
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        this.later(() -> {
            if (this.hidden.contains(player.getUniqueId())) {
                this.syncEntityId(player);
                this.ensureInvisible(player);
                this.sendEmptyEquipment(player);
            }
            this.hideFromViewer(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (this.hidden.contains(id)) {
            // Que la invisibilidad no se guarde en los datos del jugador y reaparezca al volver.
            if (this.ownInvisibility.contains(id)
                    && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
        this.hiddenEntities.remove(player.getEntityId());
        this.entityIds.remove(id);
        this.hidden.remove(id);
        this.sourceId.remove(id);
        this.revealOnAttack.remove(id);
        this.expiresAt.remove(id);
        this.ownInvisibility.remove(id);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!this.hidden.contains(player.getUniqueId())) {
            return;
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, EquipmentSlot.HAND,
                        new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!this.hidden.contains(player.getUniqueId())) {
            return;
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, EquipmentSlot.HAND,
                        new ItemStack(Material.AIR));
                viewer.sendEquipmentChange(player, EquipmentSlot.OFF_HAND,
                        new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        if (!this.hidden.contains(player.getUniqueId())) {
            return;
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, event.getSlot(),
                        new ItemStack(Material.AIR));
            }
        }
    }

    /** Si el jugador oculto ataca (melee o proyectil) y toca revelarse, se revela. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = this.attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(event.getEntity())) {
            return;
        }
        UUID id = attacker.getUniqueId();
        if (!this.hidden.contains(id) || !Boolean.TRUE.equals(this.revealOnAttack.get(id))) {
            return;
        }
        var effects = this.plugin.effects();
        String effectId = this.sourceId.get(id);
        if (effects != null && effectId != null) {
            effects.remove(attacker, effectId);
        }
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private void later(Runnable task) {
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(this.plugin, task);
        } else {
            task.run();
        }
    }
}
