package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.UseSpec;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        // Sin flecha cargada: el disparo debe usar municion normal, no la custom que
        // el juego haya elegido por estar primera en el inventario.
        if (!this.useNormalAmmo(player, event, projectile)) {
            return;
        }
        this.arm(player, event.getBow(), projectile, false);
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
                this.impactSkills(catalog.useOf(name).orElse(null)), true);
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
        // Una flecha con marca de catalogo nunca debe poder recogerse. El tridente tambien
        // hereda de AbstractArrow, pero no es municion: sigue por la ruta normal para
        // poder armarlo con sus skills de impacto.
        if (event.getEntity() instanceof AbstractArrow arrow && !(arrow instanceof Trident)) {
            var effects = this.plugin.effectListener();
            if (effects != null && effects.catalogName(arrow.getItemStack()) != null) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (this.arm(player, player.getInventory().getItemInMainHand(), event.getEntity(), true)) {
            return;
        }
        // El tridente tambien puede lanzarse desde la mano secundaria.
        if (event.getEntity() instanceof Trident
                && this.arm(player, player.getInventory().getItemInOffHand(),
                        event.getEntity(), true)) {
            return;
        }
        Hand hand = this.recent.remove(player.getUniqueId());
        if (hand != null && System.currentTimeMillis() - hand.at() < 1_000L) {
            this.arm(player, hand.item(), event.getEntity(), true);
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
        if (ArrowTypes.isArrow(item)) {
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
            this.stampLabel(player, event, item, name);
            this.arrows.load(player, name);
            this.plugin.status().notice(player, "&8Flecha cargada: &f"
                    + this.arrows.displayName(name) + " &8(&f"
                    + this.arrows.count(player, name) + "&8)", BarColor.WHITE);
            return;
        }
        if (name == null || this.impactSkills(item).isEmpty()) {
            return;
        }
        // Se graba lo que hay en cualquiera de las dos manos: el lanzamiento puede
        // consumir el stack antes del ProjectileLaunchEvent.
        this.recent.put(player.getUniqueId(), new Hand(item.clone(), System.currentTimeMillis()));
    }

    /**
     * Pone al stack la etiqueta visible del catalogo si no tiene nombre. Sin nombre, una
     * flecha custom vieja es indistinguible de una normal y el juego las apila.
     */
    private void stampLabel(Player player, PlayerInteractEvent event, ItemStack item, String name) {
        ItemStack template = this.plugin.drops().catalog().get(name).orElse(null);
        if (template == null || !template.hasItemMeta()) {
            return;
        }
        String label = template.getItemMeta().getDisplayName();
        if (label == null || label.isBlank()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || (meta.hasDisplayName() && !meta.getDisplayName().isBlank())) {
            return;
        }
        meta.setDisplayName(label);
        item.setItemMeta(meta);
        if (event.getHand() == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInOffHand(item);
        }
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

    /**
     * Quita del inventario una flecha vanilla del tipo pedido. Se salta las del catalogo:
     * una flecha con efecto lleva datos de pocion y no se puede fabricar con
     * {@code new ItemStack(TIPPED_ARROW)}.
     *
     * @return la flecha quitada, o {@code null} si no habia
     */
    private ItemStack takeVanilla(Player player, org.bukkit.Material type) {
        // Primero del mismo tipo; si no hay, cualquiera de la familia. Un cazador con
        // flechas con efecto y ninguna normal tiene que poder disparar igual.
        ItemStack same = this.takeVanillaOf(player, type);
        if (same != null) {
            return same;
        }
        for (org.bukkit.Material other : ArrowTypes.VANILLA) {
            if (other == type) {
                continue;
            }
            ItemStack found = this.takeVanillaOf(player, other);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ItemStack takeVanillaOf(Player player, org.bukkit.Material type) {
        var effects = this.plugin.effectListener();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != type || stack.getAmount() <= 0) {
                continue;
            }
            if (effects != null && effects.catalogName(stack) != null) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            if (player.getInventory().removeItem(one).isEmpty()) {
                return one;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        LivingEntity hit = event.getHitEntity() instanceof LivingEntity living ? living : null;
        // Paper a veces avisa del impacto del tridente sin la entidad golpeada. Ese caso
        // lo resuelve onTridentDamage; consumirlo aqui perderia el efecto.
        if (hit == null && event.getEntity() instanceof Trident && event.getHitBlock() == null) {
            return;
        }
        this.service.hit(event.getEntity(), event.getEntity().getLocation(), hit);
    }

    /**
     * Respaldo del tridente: al golpear una entidad no siempre llega
     * {@link ProjectileHitEvent}. {@link ProjectileService#hit} es idempotente, asi que
     * si el evento normal ya resolvio la skill esta llamada no hace nada.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTridentDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity hit)) {
            return;
        }
        this.service.hit(trident, trident.getLocation(), hit);
    }

    /**
     * Golpe real de cualquier proyectil sobre una entidad.
     *
     * <p>Con el cuerpo oculto, {@link ProjectileHitEvent} avisa primero de la caja del
     * modelo de BetterModel y el mob recibe el dano un instante despues. Sin este camino,
     * la skill se quedaba en la caja y el mob no se enteraba.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Projectile projectile) || damager instanceof Trident) {
            return;   // el tridente tiene su propio camino
        }
        if (event.getEntity() instanceof LivingEntity hit) {
            this.service.hitEntity(projectile, hit);
        }
    }

    /**
     * Fuerza municion normal cuando no hay flecha cargada. El servidor gasta la flecha
     * antes del evento; si eligio una custom, se devuelve y se gasta una normal.
     *
     * @return {@code false} si no habia flechas normales y el disparo se cancelo
     */
    private boolean useNormalAmmo(Player player, EntityShootBowEvent event,
                                   Projectile projectile) {
        if (!this.isRanger(player)) {
            return true;   // fuera del cazador, el disparo es vanilla
        }
        ItemStack consumed = event.getConsumable();
        var effects = this.plugin.effectListener();
        if (consumed == null || effects == null) {
            return true;
        }
        if (effects.catalogName(consumed) == null) {
            return true;   // ya era una flecha normal
        }
        ItemStack plain = this.takeVanilla(player, consumed.getType());
        if (plain == null) {
            // No hay flechas vanilla de ese tipo: se devuelve la custom y se corta.
            this.refund(player, consumed);
            event.setCancelled(true);
            this.plugin.status().notice(player, "&8No te quedan flechas normales.",
                    BarColor.WHITE);
            return false;
        }
        this.refund(player, consumed);
        // El proyectil se creo con el NBT de la custom. Se convierte en la flecha vanilla
        // que se gasto, con sus datos, para que no se confunda ni se recoja como custom.
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setItemStack(plain);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        }
        return true;
    }

    /**
     * Arma el proyectil con las skills de impacto del objeto, si las tiene.
     *
     * @return {@code true} si el objeto era del catalogo y llevaba skills de impacto
     */
    private boolean arm(Player player, ItemStack item, Projectile projectile, boolean applyCooldown) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        // Las flechas custom son exclusivas de la clase de arqueros.
        if (ArrowTypes.isArrow(item) && !this.isRanger(player)) {
            return false;
        }
        // El objeto tiene que corresponder al proyectil: un tridente custom en la otra
        // mano no debe armar una bola de nieve ni al reves.
        Material type = item.getType();
        if ((type == Material.TRIDENT && !(projectile instanceof Trident))
                || (type == Material.SNOWBALL && !(projectile instanceof Snowball))
                || (type == Material.EGG && !(projectile instanceof Egg))) {
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
        // El tridente se lanza siempre: su cooldown no puede bloquear el uso vanilla.
        // Se guarda para comprobarlo al impactar (ProjectileService).
        boolean trident = projectile instanceof Trident;
        // El cooldown arranca al disparar, no al impactar. En el arco no se aplica:
        // el cooldown es de la flecha, para no bloquear todos los tipos.
        if (applyCooldown && !trident && !effects.tryCooldown(player, name, use, item)) {
            return false;
        }
        if (applyCooldown && !trident && use.hasStartMessage()) {
            this.plugin.status().notice(player, use.startMessage()
                    .replace("{jugador}", player.getName()), BarColor.WHITE);
        }
        this.service.arm(projectile, player, name, skills, ArrowTypes.isArrow(item));
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
