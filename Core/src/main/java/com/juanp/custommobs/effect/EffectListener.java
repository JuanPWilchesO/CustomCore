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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las mecanicas de los efectos: dano (congelacion, caida, robo de vida), beber una pocion,
 * sigilo y paso helado.
 */
public final class EffectListener implements Listener {

    private static final String STEALTH_TEAM = "cm_sigilo";
    private static final long FROST_MILLIS = 5000L;

    private final CustomMobsPlugin plugin;
    private final EffectService service;
    /** Bloques que hemos helado nosotros: clave mundo:x:y:z -> cuando descongelar. */
    private final Map<String, Long> frost = new ConcurrentHashMap<>();

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
            this.stealth(player, true);
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

    /** Se llama cada segundo: caducidades, congelacion, sigilo y paso helado. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String id : this.service.expire(player)) {
                this.service.effect(id).ifPresent(spec -> {
                    if ("sigilo".equals(spec.mechanic())
                            && !this.service.hasMechanic(player, "sigilo")) {
                        this.stealth(player, false);
                    }
                });
            }
            if (this.service.hasMechanic(player, "congelamiento")) {
                player.setFreezeTicks(0);
            }
            if (this.service.hasMechanic(player, "paso_helado")) {
                this.freezeUnder(player);
            }
        }
        this.thaw();
    }

    private void freezeUnder(Player player) {
        Location feet = player.getLocation().subtract(0.0D, 1.0D, 0.0D);
        Block block = feet.getBlock();
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

    /** Sigilo: un equipo de verdad con la placa oculta. Es scoreboard de vanilla, sin paquetes. */
    private void stealth(Player player, boolean on) {
        var manager = this.plugin.getServer().getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard board = manager.getMainScoreboard();
        Team team = board.getTeam(STEALTH_TEAM);
        if (team == null && on) {
            team = board.registerNewTeam(STEALTH_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        if (team == null) {
            return;
        }
        if (on) {
            team.addEntry(player.getName());
        } else {
            team.removeEntry(player.getName());
        }
    }
}
