package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.UseSpec;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispara las skills {@code mode: impacto} cuando el proyectil toca algo.
 *
 * <p>Cubre lo que el plan de arranque admite: flecha y ballesta, municion custom,
 * tridente, bola de nieve y huevo. El resto de proyectiles no se engancha a proposito.
 */
public final class ProjectileListener implements Listener {

    private record Hand(ItemStack item, long at) {
    }

    private final CustomMobsPlugin plugin;
    private final ProjectileService service;
    /** Ultimo objeto con skills de impacto en la mano, por si el lanzamiento ya lo consumio. */
    private final Map<UUID, Hand> recent = new ConcurrentHashMap<>();

    public ProjectileListener(CustomMobsPlugin plugin, ProjectileService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** Arco y ballesta: la municion manda; si no lleva skills, las pone el arma. */
    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        if (this.arm(player, event.getConsumable(), projectile)) {
            return;
        }
        this.arm(player, event.getBow(), projectile);
    }

    /** Bola de nieve, huevo y tridente: el objeto puede seguir en la mano o no. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        // Las flechas ya pasan por EntityShootBowEvent.
        if (event.getEntity() instanceof AbstractArrow) {
            return;
        }
        if (this.arm(player, player.getInventory().getItemInMainHand(), event.getEntity())) {
            return;
        }
        Hand hand = this.recent.remove(player.getUniqueId());
        if (hand != null && System.currentTimeMillis() - hand.at() < 1_000L) {
            this.arm(player, hand.item(), event.getEntity());
        }
    }

    /** Lo que el jugador tiene en la mano justo antes de lanzar. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (this.impactSkills(item).isEmpty()) {
            return;
        }
        this.recent.put(event.getPlayer().getUniqueId(),
                new Hand(item.clone(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        LivingEntity hit = event.getHitEntity() instanceof LivingEntity living ? living : null;
        this.service.hit(event.getEntity(), event.getEntity().getLocation(), hit);
    }

    /**
     * Arma el proyectil con las skills de impacto del objeto, si las tiene.
     *
     * @return {@code true} si el objeto era del catalogo y llevaba skills de impacto
     */
    private boolean arm(Player player, ItemStack item, Projectile projectile) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        List<SkillSpec> skills = this.impactSkills(item);
        if (skills.isEmpty()) {
            return false;
        }
        var effects = this.plugin.effectListener();
        if (effects == null) {
            return false;
        }
        String name = effects.catalogName(item);
        if (name == null) {
            return false;
        }
        UseSpec use = effects.useOf(item);
        if (use == null) {
            return false;
        }
        // Mismas restricciones de faccion y clase que al usar el objeto.
        if (!effects.canUse(player, item)) {
            return false;
        }
        // El cooldown arranca al disparar, no al impactar.
        if (!effects.tryCooldown(player, name, use)) {
            return false;
        }
        if (use.hasStartMessage()) {
            player.sendMessage(Texts.color(use.startMessage()
                    .replace("{jugador}", player.getName())));
        }
        this.service.arm(projectile, player, name, skills);
        return true;
    }

    /** Skills de impacto que declara el objeto; vacia si no es del catalogo o no tiene. */
    private List<SkillSpec> impactSkills(ItemStack item) {
        List<SkillSpec> found = new ArrayList<>();
        var effects = this.plugin.effectListener();
        var catalog = this.plugin.skillCatalog();
        if (item == null || item.getType().isAir() || effects == null || catalog == null) {
            return found;
        }
        UseSpec use = effects.useOf(item);
        if (use == null || !use.hasSkills()) {
            return found;
        }
        for (String id : use.skillIds()) {
            SkillSpec skill = catalog.get(id).orElse(null);
            if (skill != null && skill.trigger() == SkillTrigger.IMPACT) {
                found.add(skill);
            }
        }
        return found;
    }
}
