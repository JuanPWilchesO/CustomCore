package com.juanp.custommobs.effect;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las mecanicas de los efectos: dano (congelacion, caida, robo de vida), beber una pocion,
 * sigilo y paso helado.
 *
 * <p>La tarea corre <b>cada tick</b>: la congelacion hay que limpiarla todo el rato (si no,
 * entre un reseteo y el siguiente el hielo agarra) y el paso helado tiene que ir por delante
 * del jugador, no detras. Lo caro (caducidades) va cada 20 ticks.
 */
public final class EffectListener implements Listener {

    private static final long FROST_MILLIS = 5000L;

    private final CustomMobsPlugin plugin;
    private final EffectService service;
    /** Bloques que hemos helado nosotros: clave mundo:x:y:z -> cuando descongelar. */
    private final Map<String, Long> frost = new ConcurrentHashMap<>();
    /** Jugadores en sigilo (tipo vanish): ocultos para los demas. */
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private int ticks;

    public EffectListener(CustomMobsPlugin plugin, EffectService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // ------------------------------------------------------------------ pociones

    /** Beber una pocion: se aplica el efecto (el item lo consume el juego). */
    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        this.service.potionOf(event.getItem())
                .flatMap(this.service::potion)
                .ifPresent(spec -> this.apply(event.getPlayer(), spec));
    }

    /** Items que no se beben: clic derecho y se gastan. */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.POTION
                || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION) {
            return;
        }
        this.service.potionOf(item).flatMap(this.service::potion).ifPresent(spec -> {
            event.setCancelled(true);
            if (!this.apply(event.getPlayer(), spec)) {
                return;
            }
            item.setAmount(item.getAmount() - 1);
        });
    }

    /** @return {@code false} si la faccion de la pocion no deja usarla */
    private boolean apply(Player player, PotionSpec spec) {
        EffectSpec effect = this.service.effect(spec.effectId()).orElse(null);
        if (effect == null) {
            return false;
        }
        if (spec.faction() != null) {
            String own = this.plugin.mobs().factionOf(player);
            if (own == null || !own.equalsIgnoreCase(spec.faction())) {
                player.sendMessage(Texts.color("&cEsa pocion es de la faccion &f"
                        + spec.faction() + "&c: no es la tuya."));
                return false;
            }
        }
        this.service.apply(player, effect, spec.duration());
        player.sendMessage(Texts.color("&8[&cCustomCore&8] &7" + effect.name()));
        if ("sigilo".equals(effect.mechanic())) {
            this.vanish(player, true);
            // Invisibilidad de vanilla ademas de ocultarse para los demas: asi el propio
            // jugador ve desaparecer su cuerpo en F5 (como el vanish de Essentials). La
            // armadura la sigue dibujando el cliente para uno mismo, igual que en Essentials;
            // para los DEMAS no se ve nada, armadura incluida (hidePlayer).
            int seconds = spec.duration() > 0 ? spec.duration() : effect.duration();
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    PotionEffectType.INVISIBILITY, seconds * 20, 0, false, false, false));
        }
        return true;
    }

    // ------------------------------------------------------------------ dano

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        switch (event.getCause()) {
            case FREEZE -> {
                if (this.service.hasMechanic(player, "congelamiento")) {
                    event.setCancelled(true);
                }
            }
            case FALL -> {
                if (this.service.hasMechanic(player, "caida")) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /** Robo de vida: te curas un % del dano que haces. */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        double power = this.service.powerOf(player, "robo_vida");
        if (power <= 0.0D) {
            return;
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0D : maxHealth.getValue();
        player.setHealth(Math.min(max, player.getHealth() + event.getFinalDamage() * power));
    }

    // ------------------------------------------------------------------ tarea

    /** Corre cada tick: congelacion y paso helado. Lo caro, cada 20. */
    public void tick() {
        this.ticks++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.service.hasMechanic(player, "congelamiento")) {
                // Limpiar el hielo TODO el rato: si no, entre tick y tick el ice_aspect agarra.
                player.setFreezeTicks(0);
                if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                }
            }
            if (this.service.hasMechanic(player, "paso_helado")) {
                this.freezeUnder(player);
            }
        }
        if (this.ticks % 20 != 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String id : this.service.expire(player)) {
                this.service.effect(id).ifPresent(spec -> {
                    if ("sigilo".equals(spec.mechanic())
                            && !this.service.hasMechanic(player, "sigilo")) {
                        this.vanish(player, false);
                    }
                });
            }
        }
        this.thaw();
    }

    /**
     * Hiela el bloque de debajo y los de delante: con solo el de abajo el jugador se hundia
     * antes de que llegara el siguiente.
     */
    private void freezeUnder(Player player) {
        Location origin = player.getLocation().subtract(0.0D, 1.0D, 0.0D);
        this.freezeBlock(origin.getBlock());
        Location facing = player.getLocation();
        double dx = facing.getDirection().getX();
        double dz = facing.getDirection().getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01D) {
            return;
        }
        dx /= length;
        dz /= length;
        for (int step = 1; step <= 3; step++) {
            this.freezeBlock(origin.clone().add(dx * step, 0.0D, dz * step).getBlock());
        }
    }

    private void freezeBlock(Block block) {
        if (block.getType() != Material.WATER) {
            return;
        }
        block.setType(Material.ICE, false);
        this.frost.put(key(block), System.currentTimeMillis() + FROST_MILLIS);
    }

    private void thaw() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : this.frost.entrySet()) {
            if (entry.getValue() > now) {
                continue;
            }
            Block block = this.blockOf(entry.getKey());
            if (block != null && block.getType() == Material.ICE) {
                block.setType(Material.WATER, false);
            }
            this.frost.remove(entry.getKey());
        }
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Block blockOf(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        org.bukkit.World world = this.plugin.getServer().getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Sigilo tipo vanish: el jugador desaparece para los demas, con armadura y equipo.
     *
     * <p>Se usa la API de jugador oculto de Bukkit, no paquetes propios: es la misma via que
     * usa Essentials para su vanish, y no toca el protocolo a mano.
     */
    private void vanish(Player player, boolean on) {
        if (on) {
            this.vanished.add(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.hidePlayer(this.plugin, player);
                }
            }
        } else {
            this.vanished.remove(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(this.plugin, player);
            }
        }
    }

    /**
     * El sigilo aguanta las reconexiones, en los dos sentidos: si entra alguien nuevo no debe
     * ver a los que estan en sigilo, y si el que reconecta es el que estaba en sigilo, debe
     * seguir oculto para todos (si no, al volver a entrar la entidad se re-manda y reaparece).
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (this.vanished.contains(joined.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(joined)) {
                    other.hidePlayer(this.plugin, joined);
                }
            }
        }
        for (UUID id : this.vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !hidden.equals(joined)) {
                joined.hidePlayer(this.plugin, hidden);
            }
        }
    }
}
