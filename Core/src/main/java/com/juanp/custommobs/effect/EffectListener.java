package com.juanp.custommobs.effect;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.ItemFactionLock;
import com.juanp.custommobs.item.UseSpec;
import com.juanp.custommobs.mob.Texts;
import com.juanp.custommobs.region.RegionGate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las mecanicas de los efectos: dano (congelacion, caida, robo de vida), beber una pocion,
 * usar un objeto del catalogo, sigilo y paso helado.
 *
 * <p>El sigilo corta la deteccion de los mobs: no pueden fijarlo como objetivo ni
 * mantenerlo. La parte visual (invisibilidad y equipo) vive en {@link SigiloService}.
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
    /** Enfriamiento de los objetos con {@code usar:}: jugador:objeto -> cuando se puede usar. */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private int ticks;

    public EffectListener(CustomMobsPlugin plugin, EffectService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // ------------------------------------------------------------------ pociones y objetos

    /** Comer o beber: pocion del plugin, o comida del catalogo con {@code usar:}. */
    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (this.blockedByCc(event.getPlayer(), item)) {
            event.setCancelled(true);
            return;
        }
        PotionSpec potion = this.service.potionOf(item).flatMap(this.service::potion).orElse(null);
        if (potion != null) {
            this.apply(event.getPlayer(), potion);
            return;
        }
        // Comida: el juego ya la consume, asi que aqui solo se aplica el efecto.
        this.useCatalog(event.getPlayer(), item, false);
    }

    /**
     * Uso temprano: corre antes de los plugins que cancelan el clic derecho al aire.
     * Solo actua si 'items.ignore-cancelled-use' esta activo y el objeto es del catalogo
     * con 'usar:'. Respeta las restricciones de clase/faccion.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onUseEarly(PlayerInteractEvent event) {
        if (!this.plugin.config().itemIgnoreCancelledUse()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!this.hasCatalogTag(item) || item.getType().isEdible()) {
            return;
        }
        boolean drinkable = item.getType() == Material.POTION
                || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION;
        if (drinkable) {
            return;
        }
        if (!this.canUse(event.getPlayer(), item)) {
            return;
        }
        if (this.useCatalog(event.getPlayer(), item, true)) {
            event.setCancelled(true);
        }
    }

    /** Clic derecho: pocion que no se bebe, o cualquier objeto del catalogo con {@code usar:}. */
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
        if (item == null) {
            return;
        }
        boolean drinkable = item.getType() == Material.POTION
                || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION;
        if (!drinkable) {
            PotionSpec potion = this.service.potionOf(item).flatMap(this.service::potion).orElse(null);
            if (potion != null) {
                event.setCancelled(true);
                if (this.apply(event.getPlayer(), potion)) {
                    item.setAmount(item.getAmount() - 1);
                }
                return;
            }
        }
        if (item.getType().isEdible()) {
            return; // se maneja al comer
        }
        if (!this.canUse(event.getPlayer(), item)) {
            return;
        }
        // Objeto del catalogo con 'usar:'.
        if (!drinkable && this.useCatalog(event.getPlayer(), item, true)) {
            event.setCancelled(true);
        }
    }

    /**
     * Clic derecho sobre una entidad. Bukkit manda {@link PlayerInteractEntityEvent}, no
     * {@link PlayerInteractEvent}; sin este handler, apuntar a un mob y clicar sobre el
     * no disparaba el {@code usar:} del objeto.
     */
    @EventHandler(ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || item.getType().isEdible()) {
            return;
        }
        boolean drinkable = item.getType() == Material.POTION
                || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION;
        if (drinkable) {
            return;
        }
        if (!this.canUse(player, item)) {
            return;
        }
        // Solo se cancela si el objeto era del catalogo y tenia 'usar:'. Asi no se
        // rompen interacciones normales (comerciar, montar, etc.).
        if (this.useCatalog(player, item, true)) {
            event.setCancelled(true);
        }
    }

    /** {@code true} si el objeto lleva la marca de catalogo. */
    private boolean hasCatalogTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(
                this.plugin.drops().catalog().nameKey(), PersistentDataType.STRING);
    }

    /**
     * Un aturdimiento o silencio impide usar objetos, salvo los marcados con
     * {@code stun-free: true}. Se comprueba dentro del uso, no solo en el evento.
     */
    private boolean blockedByCc(Player player, ItemStack item) {
        var cc = this.plugin.cc();
        if (cc == null || item == null) {
            return false;
        }
        var id = player.getUniqueId();
        if (!cc.stunned(id) && !cc.silenced(id)) {
            return false;
        }
        return !this.plugin.drops().catalog().stunFree(item);
    }

    /**
     * {@code true} si el objeto no tiene restriccion o el jugador la cumple.
     *
     * <p>Tambien mira las {@code deny-regions} del objeto: {@link #onUseEarly} corre aunque
     * otro plugin cancele el clic, asi que sin esto un objeto del catalogo se podia usar dentro
     * de una zona protegida.
     */
    public boolean canUse(Player player, ItemStack item) {
        ItemFactionLock lock = this.plugin.itemLock();
        // Si el lock no esta listo, se falla cerrado: mejor no usar que saltarse la regla.
        if (lock == null || lock.requirement(player, item) != null) {
            return false;
        }
        return this.regionAllows(player, item);
    }

    /**
     * {@code true} si las regiones vetadas en el {@code usar:} del objeto dejan usarlo ahi.
     *
     * <p>Se usa el mismo cerco que los mobs ({@link RegionGate}); sin WorldGuard esta abierto.
     * Los objetos sin regiones declaradas no pagan ninguna consulta.
     */
    private boolean regionAllows(Player player, ItemStack item) {
        UseSpec use = this.useOf(item);
        if (use == null || use.deniedRegions().isEmpty()) {
            return true;
        }
        return RegionGate.create(this.plugin).allows(player.getLocation(), "",
                use.deniedRegions());
    }

    /** @return {@code false} si la faccion de la pocion no deja usarla */
    private boolean apply(Player player, PotionSpec spec) {
        EffectSpec effect = this.service.effect(spec.effectId()).orElse(null);
        if (effect == null || !this.canApply(player, spec)) {
            return false;
        }
        return this.applyEffect(player, effect, spec.duration());
    }

    /**
     * Restricciones de faccion y clase de una pocion. El admin pasa siempre, y la faccion
     * puede admitir aliados si el servidor lo configura asi.
     */
    public boolean canApply(Player player, PotionSpec spec) {
        boolean admin = player.hasPermission(this.plugin.config().bypassPermission());
        if (!admin && spec.faction() != null) {
            String own = this.plugin.mobs().factionOf(player);
            boolean allowed = own != null && own.equalsIgnoreCase(spec.faction());
            if (!allowed && own != null && this.plugin.config().itemFactionAllies()) {
                allowed = this.plugin.factions().isAlly(
                        spec.faction().toLowerCase(java.util.Locale.ROOT),
                        own.toLowerCase(java.util.Locale.ROOT));
            }
            if (!allowed) {
                player.sendMessage(Texts.color("&cEsa pocion es de la faccion &f"
                        + spec.faction() + "&c: no es la tuya."));
                return false;
            }
        }
        if (!admin && spec.clazz() != null) {
            String own = this.plugin.playerClasses().of(player.getUniqueId()).orElse(null);
            if (own == null || !own.equalsIgnoreCase(spec.clazz())) {
                player.sendMessage(Texts.color("&cEsa pocion es de la clase &f"
                        + spec.clazz() + "&c: no es la tuya."));
                return false;
            }
        }
        return true;
    }

    /**
     * Poción arrojadiza o persistente: aplica el efecto del plugin al area del impacto.
     *
     * <p>El efecto se aplica una vez, no por tick: los efectos del plugin son estados con
     * duracion, y reaplicarlos cada tick reiniciaria el contador sin parar.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }
        PotionSpec spec = this.service.potionOf(event.getPotion().getItem())
                .flatMap(this.service::potion).orElse(null);
        if (spec == null || !spec.throwable()) {
            return;
        }
        EffectSpec effect = this.service.effect(spec.effectId()).orElse(null);
        if (effect == null) {
            return;
        }
        // La restriccion de faccion o clase se comprueba al lanzar, no por victima.
        if (!this.canApply(thrower, spec)) {
            event.setCancelled(true);
            return;
        }
        int seconds = spec.duration() > 0 ? spec.duration() : effect.duration();
        Location center = event.getEntity().getLocation();
        double radius = spec.impactRadius();
        for (var candidate : center.getWorld()
                .getNearbyEntities(center, radius, radius, radius)) {
            if (!(candidate instanceof Player target)) {
                continue;
            }
            if (!this.bandAllows(thrower, target, effect, spec)) {
                continue;
            }
            this.applyEffect(target, effect, seconds, null, null);
        }
    }

    /** Bando por defecto segun el tipo del efecto. */
    private static String defaultBand(EffectSpec effect) {
        String type = effect.type() == null ? "" : effect.type().toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "negativo", "negative", "malo" -> "enemigos";
            case "neutro", "neutral" -> "todos";
            default -> "aliados";
        };
    }

    /** {@code true} si el bando de la pocion deja alcanzar a ese jugador. */
    private boolean bandAllows(Player thrower, Player target, EffectSpec effect, PotionSpec spec) {
        String band = spec.band() == null ? defaultBand(effect) : spec.band();
        boolean self = thrower.getUniqueId().equals(target.getUniqueId());
        return switch (band) {
            case "enemigos", "enemies" -> !self && !this.isAlly(thrower, target);
            case "todos", "all" -> true;
            default -> self || this.isAlly(thrower, target);
        };
    }

    /** Aliado: mismo team, misma faccion o faccion aliada. */
    private boolean isAlly(Player first, Player second) {
        if (first.getUniqueId().equals(second.getUniqueId())) {
            return true;
        }
        if (this.plugin.mobs().teamLink().isAlly(first.getUniqueId(), second.getUniqueId())) {
            return true;
        }
        return this.plugin.mobs().sameSide(
                this.plugin.mobs().factionOf(first), this.plugin.mobs().factionOf(second));
    }

    /**
     * El objeto del catalogo, si su yml trae {@code usar:}.
     *
     * @param spend si hay que gastar una unidad al usarlo
     * @return {@code true} si el objeto era del catalogo y tenia uso
     */
    private boolean useCatalog(Player player, ItemStack item, boolean spend) {
        if (item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        // El uso de objetos del catalogo se resuelve aqui, no solo cancelando el evento:
        // 'onUseEarly' corre aunque otro plugin cancele el clic. Sin esta puerta, un
        // aturdido podia seguir usando sus objetos custom.
        if (this.blockedByCc(player, item)) {
            return false;
        }
        // Vale para todas las vias (clic, entidad, comer): una sola puerta de entrada.
        if (!this.canUse(player, item)) {
            return false;
        }
        var catalog = this.plugin.drops().catalog();
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(catalog.nameKey(), PersistentDataType.STRING);
        if (name == null) {
            return false;
        }
        var use = catalog.useOf(name).orElse(null);
        if (use == null) {
            return false;
        }
        // Un objeto puede lanzar un efecto suelto o una lista de skills. Las skills se
        // resuelven al usarlo, para que /cm reload las actualice sin recrear el item.
        EffectSpec effect = null;
        if (!use.hasSkills()) {
            effect = this.service.effect(use.effectId()).orElse(null);
            if (effect == null) {
                return false;
            }
        }
        // Si todas las skills del objeto son de impacto, el objeto no se "usa": se dispara.
        // El cooldown y el aviso de inicio los pone el proyectil al salir.
        if (use.hasSkills() && this.allImpact(use)) {
            return false;
        }
        if (!this.tryCooldown(player, name, use, item)) {
            return true;   // en espera: el clic se consume igual
        }
        if (use.hasStartMessage()) {
            this.plugin.status().notice(player, use.startMessage()
                    .replace("{jugador}", player.getName()), BarColor.WHITE);
        }
        if (use.hasSkills()) {
            if (this.plugin.skillService() == null) {
                return false;
            }
            this.plugin.skillService().cast(player, use.skillIds());
        } else {
            // El aviso de inicio del item, si lo hay, ya se mando arriba: no se repite
            // el nombre del efecto.
            this.applyEffect(player, effect, use.duration(), null, use.endMessage());
        }
        if (spend && use.consume()) {
            item.setAmount(item.getAmount() - 1);
        }
        return true;
    }

    /** Nombre de catalogo del objeto, o {@code null} si no lo es. */
    public String catalogName(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(this.plugin.drops().catalog().nameKey(), PersistentDataType.STRING);
    }

    /** Lo que el objeto hace al usarse, segun su yml. {@code null} si no es del catalogo. */
    public UseSpec useOf(ItemStack item) {
        String name = this.catalogName(item);
        return name == null ? null : this.plugin.drops().catalog().useOf(name).orElse(null);
    }

    /** {@code true} si todas las skills del objeto son de impacto. */
    private boolean allImpact(UseSpec use) {
        if (!use.hasSkills() || this.plugin.skillCatalog() == null) {
            return false;
        }
        boolean any = false;
        for (String id : use.skillIds()) {
            var skill = this.plugin.skillCatalog().get(id).orElse(null);
            if (skill == null || skill.mode() != com.juanp.custommobs.skill.SkillMode.IMPACT) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /** Cooldown efectivo del objeto: el mayor entre el suyo y el de sus skills. */
    private int effectiveCooldown(UseSpec use) {
        int cooldown = use.cooldownSeconds();
        if (use.hasSkills() && this.plugin.skillCatalog() != null) {
            for (String id : use.skillIds()) {
                var skill = this.plugin.skillCatalog().get(id).orElse(null);
                if (skill != null) {
                    cooldown = Math.max(cooldown, skill.cooldownSeconds());
                }
            }
        }
        return cooldown;
    }

    /** {@code true} si el objeto sigue en espera; no aplica ni avisa nada. */
    public boolean onCooldown(Player player, String name) {
        Long until = this.cooldowns.get(player.getUniqueId() + ":" + name);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Cooldown para objetos cuyo uso no puede bloquearse, como el tridente: se lanza
     * siempre y lo que espera es el efecto. Se guarda en el mismo mapa que
     * {@link #tryCooldown} y se avisa solo en boss bar, nunca con el barrido vanilla,
     * que impediria volver a lanzarlo.
     *
     * @return {@code false} si todavia esta en espera; ya dejo el aviso
     */
    public boolean tryImpactCooldown(Player player, String name, UseSpec use) {
        int cooldown = this.effectiveCooldown(use);
        String key = player.getUniqueId() + ":" + name;
        long now = System.currentTimeMillis();
        Long until = this.cooldowns.get(key);
        if (until != null && until > now) {
            this.showImpactCooldown(player, name, (until - now + 999L) / 1000L);
            return false;
        }
        if (cooldown > 0) {
            this.cooldowns.put(key, now + cooldown * 1000L);
            this.showImpactCooldown(player, name, cooldown);
        }
        return true;
    }

    /** Aviso de cooldown sin barrido vanilla: solo boss bar. */
    private void showImpactCooldown(Player player, String name, long seconds) {
        if (seconds <= 0L) {
            return;
        }
        String label = name;
        ItemStack template = this.plugin.drops().catalog().get(name).orElse(null);
        if (template != null && template.hasItemMeta()
                && template.getItemMeta().hasDisplayName()) {
            label = org.bukkit.ChatColor.stripColor(template.getItemMeta().getDisplayName());
        }
        this.plugin.status().cooldown(player, name, "&8" + label + " &7(" + seconds + "s)");
    }

    public boolean tryCooldown(Player player, String name, UseSpec use, ItemStack item) {
        int cooldown = this.effectiveCooldown(use);
        String key = player.getUniqueId() + ":" + name;
        long now = System.currentTimeMillis();
        Long until = this.cooldowns.get(key);
        if (until != null && until > now) {
            this.showCooldown(player, name, item, (until - now + 999L) / 1000L);
            return false;
        }
        if (cooldown > 0) {
            this.cooldowns.put(key, now + cooldown * 1000L);
            this.showCooldown(player, name, item, cooldown);
        }
        return true;
    }

    /**
     * Cooldown visual. Si el material lo comparten varios items del catalogo, el barrido
     * vanilla bloquearia los otros: se usa la boss bar y se deja el cooldown en el servidor.
     */
    private void showCooldown(Player player, String name, ItemStack item, long seconds) {
        if (item == null || item.getType().isAir() || seconds <= 0L) {
            return;
        }
        if (this.plugin.drops().catalog().materialShared(item.getType(), name)) {
            String label = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName())
                    : name;
            this.plugin.status().cooldown(player, name,
                    "&8" + label + " &7(" + seconds + "s)");
        } else {
            player.setCooldown(item.getType(),
                    (int) Math.min(Integer.MAX_VALUE, seconds * 20L));
        }
    }

    /** Aplica un efecto ya resuelto: mensaje y, si toca, las acciones de fuera. */
    private boolean applyEffect(Player player, EffectSpec effect, int seconds) {
        return this.applyEffect(player, effect, seconds, null, null);
    }

    /**
     * Aplica un efecto con avisos opcionales.
     *
     * @param startMessage aviso al aplicarlo; {@code null} deja el nombre del efecto
     * @param endMessage   aviso cuando caduque; {@code null} para no avisar
     */
    private boolean applyEffect(Player player, EffectSpec effect, int seconds,
                                String startMessage, String endMessage) {
        this.service.apply(player, effect, seconds, player.getUniqueId(), endMessage);
        String label = startMessage == null || startMessage.isBlank()
                ? effect.name()
                : startMessage.replace("{jugador}", player.getName());
        if ("quitar_positivos".equals(effect.mechanic())
                || "quitar_negativos".equals(effect.mechanic())) {
            // Purga instantanea: aviso puntual, no barra con duracion.
            this.plugin.status().notice(player, label, BarColor.WHITE);
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

    /** Invulnerabilidad: cancela todo el dano mientras el efecto este activo. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvulnerable(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && this.service.hasMechanic(living, "invulnerabilidad")) {
            event.setCancelled(true);
        }
    }

    /** Robo de vida: te curas un % del dano que haces. Vale para jugadores y para mobs. */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)
                || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        double power = this.service.powerOf(attacker, "robo_vida");
        if (power <= 0.0D) {
            return;
        }
        AttributeInstance maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0D : maxHealth.getValue();
        attacker.setHealth(Math.min(max, attacker.getHealth() + event.getFinalDamage() * power));
    }

    /** Golpes con efecto: mientras dure, cada golpe aplica una pocion al objetivo. */
    @EventHandler(ignoreCancelled = true)
    public void onHitApplyEffect(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        var hit = this.service.hitOf(attacker);
        if (hit.isEmpty()) {
            return;
        }
        PotionEffectType type = Registry.EFFECT.get(
                NamespacedKey.minecraft(hit.get().potion()));
        if (type == null) {
            return;
        }
        target.addPotionEffect(new PotionEffect(type,
                Math.max(1, hit.get().durationSeconds()) * 20,
                Math.max(0, hit.get().level() - 1),
                false, true, true));
    }

    // ------------------------------------------------------------------ tarea

    /** Corre cada tick: congelacion y paso helado. Lo caro, cada 20. */
    public void tick() {
        this.ticks++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.service.hasMechanic(player, "congelamiento")) {
                player.setFreezeTicks(0);
                if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                }
            }
            if (this.service.hasMechanic(player, "paso_helado")) {
                this.freezeUnder(player);
            }
        }
        if (this.ticks % 10 == 0) {
            this.clearSigiloTargets();
        }
        if (this.ticks % 5 == 0) {
            this.plugin.sigilo().refresh();
        }
        if (this.ticks % 20 != 0) {
            return;
        }
        // Limpia los efectos de mobs que ya no estan.
        for (UUID id : this.service.activeIds()) {
            if (Bukkit.getPlayer(id) == null) {
                this.service.expire(id);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.service.expire(player);
        }
        this.thaw();
        this.cooldowns.entrySet().removeIf(entry -> entry.getValue() < System.currentTimeMillis());
    }

    /** Hiela el bloque de debajo y los de delante. */
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

    /** Suelta a los mobs que ya tuvieran a ese jugador como objetivo (al entrar en sigilo). */
    public void releaseMobs(Player player) {
        this.clearTargets(player);
    }

    /**
     * El sigilo tambien vale contra mobs: en cuanto intentan fijarte como objetivo,
     * se cancela la adquisicion. Los que ya te tenian de objetivo se limpian aqui y
     * en {@link #clearSigiloTargets()} cada 10 ticks.
     *
     * <p>Sin comprobar {@link Mob}: un mob de otro plugin puede no implementarlo y aun asi
     * fijar objetivos. Lo que se mira es la entidad y su objetivo.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSigiloTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)
                || !this.service.hasMechanic(player, "sigilo")) {
            return;
        }
        event.setCancelled(true);
        // Cancelar impide fijarlo, pero si ya lo tenia se suelta en el acto.
        if (event.getEntity() instanceof Mob mob && mob.getTarget() != null
                && mob.getTarget().getUniqueId().equals(player.getUniqueId())) {
            mob.setTarget(null);
        }
    }

    /** Suelta a los mobs cercanos que esten apuntando a un jugador en sigilo. */
    private void clearSigiloTargets() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.service.hasMechanic(player, "sigilo")) {
                this.clearTargets(player);
            }
        }
    }

    /**
     * Quita el objetivo a los mobs en 64 bloques que apunten a ese jugador.
     *
     * <p>Se recorre la entidad entera y no solo {@link Mob}: un mob custom de otro plugin
     * puede no implementar esa interfaz. El objetivo se limpia cuando la entidad expone
     * {@link Mob}; si no, al menos el sigilo ya no le deja volver a fijarlo.
     */
    private void clearTargets(Player player) {
        for (var entity : player.getWorld().getNearbyEntities(
                player.getLocation(), 64.0D, 64.0D, 64.0D)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (living instanceof Mob mob && mob.getTarget() != null
                    && mob.getTarget().getUniqueId().equals(player.getUniqueId())) {
                mob.setTarget(null);
            }
        }
    }
}
