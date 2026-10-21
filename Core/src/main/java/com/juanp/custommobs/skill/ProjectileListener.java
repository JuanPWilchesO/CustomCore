package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.UseSpec;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
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
    private final ArrowSelection arrows;
    /** Ultimo objeto con skills de impacto en la mano, por si el lanzamiento ya lo consumio. */
    private final Map<UUID, Hand> recent = new ConcurrentHashMap<>();

    public ProjectileListener(CustomMobsPlugin plugin, ProjectileService service) {
        this.plugin = plugin;
        this.service = service;
        this.arrows = new ArrowSelection(plugin);
    }

    /** Arco y ballesta: la municion manda; si no lleva skills, las pone el arma. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        if (this.shootLoaded(player, event, projectile)) {
            return;
        }
        // Sin flecha cargada: la municion normal tambien respeta su cooldown.
        if (this.blockedByCooldown(player, event.getConsumable())) {
            this.refund(player, event.getConsumable());
            event.setCancelled(true);
            return;
        }
        if (this.arm(player, event.getConsumable(), projectile)) {
            return;
        }
        if (this.blockedByCooldown(player, event.getBow())) {
            this.refund(player, event.getConsumable());
            event.setCancelled(true);
            return;
        }
        this.arm(player, event.getBow(), projectile);
    }

    /**
     * Dispara la flecha que el jugador tiene cargada.
     *
     * @return {@code true} si habia una cargada y se gestiono aqui (aunque se cancelara)
     */
    private boolean shootLoaded(Player player, EntityShootBowEvent event, Projectile projectile) {
        String name = this.arrows.selected(player).orElse(null);
        if (name == null) {
            return false;
        }
        if (!this.isRanger(player)) {
            event.setCancelled(true);
            this.arrows.clear(player);
            player.sendMessage(Texts.color("&8Solo la clase &f"
                    + this.plugin.config().arrowClass() + "&8 puede usar flechas cargadas."));
            return true;
        }
        var catalog = this.plugin.drops().catalog();
        ItemStack template = catalog.get(name).orElse(null);
        if (template == null) {
            this.arrows.clear(player);
            this.plugin.status().notice(player,
                    "&8La flecha cargada ya no existe; se descargo.", BarColor.WHITE);
            return false;
        }
        // La restriccion de clase o faccion se comprueba tambien al disparar: el jugador
        // pudo cambiar de clase despues de cargar.
        var lock = this.plugin.itemLock();
        String denied = lock == null ? null : lock.requirement(player, template);
        if (denied != null) {
            event.setCancelled(true);
            this.arrows.clear(player);
            player.sendMessage(Texts.color("&c" + denied));
            return true;
        }
        // Cooldown del item de la flecha: se aplica al disparar y bloquea el disparo.
        var effects = this.plugin.effectListener();
        UseSpec use = effects == null ? null : effects.useOf(template);
        if (use != null && !effects.tryCooldown(player, name, use, template)) {
            // El juego ya gasto la municion antes del evento: se devuelve.
            this.refund(player, event.getConsumable());
            event.setCancelled(true);
            return true;
        }
        boolean infinity = event.getBow() != null
                && event.getBow().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.INFINITY) > 0;
        // El servidor gasta la municion ANTES de este evento, dentro de 'draw'. Los flags
        // de consumo llegan tarde: se mira que flecha se gasto y se compensa.
        ItemStack consumed = infinity ? null : event.getConsumable();
        String consumedName = consumed == null || effects == null
                ? null : effects.catalogName(consumed);
        boolean tookSelected = name.equals(consumedName);
        if (!infinity) {
            int available = this.arrows.count(player, name) + (tookSelected ? 1 : 0);
            if (available <= 0) {
                // Sin flechas de ese tipo: se descarga y el disparo sigue con las normales.
                this.arrows.clear(player);
                this.plugin.status().notice(player, "&8Se acabaron las flechas de &f"
                        + this.arrows.displayName(name) + "&8; sigues con normales.",
                        BarColor.WHITE);
                return true;
            }
            if (!tookSelected) {
                // Se devuelve la que gasto el juego (normal u otro tipo) y se gasta la elegida.
                this.refund(player, consumed);
                this.arrows.consume(player, name);
            }
        }
        this.service.arm(projectile, player, name,
                this.impactSkills(catalog.useOf(name).orElse(null)));
        if (!infinity && this.arrows.count(player, name) == 0) {
            // Se gasto la ultima: la proxima ya sale con normales.
            this.arrows.clear(player);
            this.plugin.status().notice(player, "&8Se acabaron las flechas de &f"
                    + this.arrows.displayName(name) + "&8; sigues con normales.",
                    BarColor.WHITE);
        }
        return true;
    }

    /** Devuelve una unidad de la flecha que el servidor gasto antes del evento. */
    private void refund(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemStack back = item.clone();
        back.setAmount(1);
        var leftovers = player.getInventory().addItem(back);
        for (ItemStack left : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
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
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        Player player = event.getPlayer();
        var effects = this.plugin.effectListener();
        if (effects == null) {
            return;
        }
        String name = effects.catalogName(item);
        if (isArrow(item)) {
            if (!this.isRanger(player)) {
                player.sendMessage(Texts.color("&8Solo la clase &f"
                        + this.plugin.config().arrowClass() + "&8 puede cargar flechas."));
                return;
            }
            if (name == null) {
                // Flecha normal: descarga la custom y vuelve a disparar normales.
                this.arrows.clear(player);
                this.plugin.status().notice(player,
                        "&8Flecha cargada: &fnormales&8.", BarColor.WHITE);
                return;
            }
            if (this.impactSkills(item).isEmpty()) {
                return;
            }
            // Clic derecho con una flecha del plugin: se carga en el arco.
            var lock = this.plugin.itemLock();
            String denied = lock == null ? null : lock.requirement(player, item);
            if (denied != null) {
                player.sendMessage(Texts.color("&c" + denied));
                return;
            }
            this.arrows.load(player, name);
            this.plugin.status().notice(player, "&8Flecha cargada: &f"
                    + this.arrows.displayName(name) + " &8(&f"
                    + this.arrows.count(player, name) + "&8)", BarColor.WHITE);
            return;
        }
        if (name == null || this.impactSkills(item).isEmpty()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        this.recent.put(player.getUniqueId(), new Hand(item.clone(), System.currentTimeMillis()));
    }

    /** La carga de flechas es de una clase: por defecto, el cazador. */
    private boolean isRanger(Player player) {
        if (player.hasPermission(this.plugin.config().bypassPermission())) {
            return true;
        }
        String expected = this.plugin.config().arrowClass();
        return this.plugin.playerClasses().of(player.getUniqueId())
                .map(id -> id.equalsIgnoreCase(expected))
                .orElse(false);
    }

    private static boolean isArrow(ItemStack item) {
        return item.getType() == org.bukkit.Material.ARROW
                || item.getType() == org.bukkit.Material.SPECTRAL_ARROW
                || item.getType() == org.bukkit.Material.TIPPED_ARROW;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        LivingEntity hit = event.getHitEntity() instanceof LivingEntity living ? living : null;
        this.service.hit(event.getEntity(), event.getEntity().getLocation(), hit);
    }

    /** {@code true} si el objeto de la mano esta en cooldown. */
    private boolean blockedByCooldown(Player player, ItemStack item) {
        var effects = this.plugin.effectListener();
        if (effects == null || item == null || item.getType().isAir()) {
            return false;
        }
        String name = effects.catalogName(item);
        return name != null && effects.onCooldown(player, name);
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
        if (!effects.tryCooldown(player, name, use, item)) {
            return false;
        }
        if (use.hasStartMessage()) {
            this.plugin.status().notice(player, use.startMessage()
                    .replace("{jugador}", player.getName()), BarColor.WHITE);
        }
        this.service.arm(projectile, player, name, skills);
        return true;
    }

    /** Skills de impacto que declara el objeto; vacia si no es del catalogo o no tiene. */
    private List<SkillSpec> impactSkills(ItemStack item) {
        var effects = this.plugin.effectListener();
        if (item == null || item.getType().isAir() || effects == null) {
            return new ArrayList<>();
        }
        return this.impactSkills(effects.useOf(item));
    }

    /** Skills de impacto de un bloque {@code usar:} ya resuelto. */
    private List<SkillSpec> impactSkills(UseSpec use) {
        List<SkillSpec> found = new ArrayList<>();
        var catalog = this.plugin.skillCatalog();
        if (use == null || !use.hasSkills() || catalog == null) {
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
