package com.juanp.custommobs.skill;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evalua periodicamente las skills de los mobs custom.
 *
 * <p>Los objetivos se resuelven reutilizando la politica de combate del plugin, de modo
 * que "enemigo" y "aliado" significan lo mismo para las skills que para la IA normal.
 */
public final class SkillService extends BukkitRunnable {

    /** Radio por defecto cuando una skill de MESSAGE no especifica uno util. */
    private static final double DEFAULT_SPEAK_RANGE = 20.0D;

    /** Tope de invocados por uso, para que un yml descuidado no suelte una horda. */
    private static final int MAX_SUMMON = 10;

    private final MobService service;
    private final Random random = new Random();

    /** Cooldowns: UUID del mob -> (id de skill -> instante del proximo uso en epoch ms). */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public SkillService(MobService service) {
        this.service = service;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (CustomMob customMob : this.service.active()) {
            List<SkillSpec> skills = customMob.definition().skills();
            if (skills.isEmpty()) {
                continue;
            }
            LivingEntity entity = customMob.entity();
            if (entity.isDead() || !entity.isValid()) {
                continue;
            }
            // Las de interaccion solo las dispara un clic derecho; las activas, solo en
            // combate; las pasivas, siempre que el mob este cargado.
            boolean inCombat = this.inCombat(entity);
            for (SkillSpec skill : skills) {
                // Las de item solo las dispara el uso explicito de un objeto. Un mob que
                // las referencie con 'ref:' no debe ejecutarlas por su cuenta.
                if (skill.trigger() == SkillTrigger.INTERACT
                        || skill.trigger() == SkillTrigger.ITEM
                        || skill.trigger() == SkillTrigger.IMPACT) {
                    continue;
                }
                if (skill.trigger() == SkillTrigger.ACTIVE && !inCombat) {
                    continue;
                }
                this.tryUse(customMob, skill, now);
            }
        }
        // Un mob que ya no existe no debe dejar cooldowns colgando.
        this.cooldowns.keySet().removeIf(id -> this.service.find(id).isEmpty());
    }

    // ------------------------------------------------------------------ ejecucion

    private void tryUse(CustomMob customMob, SkillSpec skill, long now) {
        LivingEntity entity = customMob.entity();
        Map<String, Long> perMob = this.cooldowns.computeIfAbsent(entity.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        Long until = perMob.get(skill.id());
        if (until != null && now < until) {
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            return;
        }

        if (skill.effect() == SkillEffect.MESSAGE) {
            List<Player> listeners = this.listeners(entity, skill.range());
            if (listeners.isEmpty()) {
                return;
            }
            perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
            String spoken = this.spoken(customMob, skill);
            for (Player listener : listeners) {
                // El texto se colorea aqui: los '&' del yml no los traduce Bukkit solo.
                listener.sendMessage(com.juanp.custommobs.mob.Texts.color(spoken));
            }
            return;
        }

        // La invocacion no va contra un objetivo: pone copias alrededor del propio mob. Si no
        // encuentra sitio, se reintenta en el siguiente ciclo sin gastar el cooldown.
        if (skill.effect() == SkillEffect.SUMMON) {
            if (!this.summon(customMob, skill)) {
                return;
            }
            perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
            return;
        }

        List<LivingEntity> targets = this.resolve(customMob, skill);
        if (targets.isEmpty()) {
            return;
        }
        perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
        for (LivingEntity target : targets) {
            this.apply(customMob, skill, target);
        }
    }

    private List<LivingEntity> resolve(CustomMob customMob, SkillSpec skill) {
        if (skill.targetType() != null) {
            return this.resolveTyped(customMob, skill);
        }
        LivingEntity entity = customMob.entity();
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.target()) {
            case SELF -> found.add(entity);
            // El radio manda tambien para el dueno y para el objetivo atacado. Sin esto, una
            // skill de montura le seguia curando al dueno desde el otro lado del mundo.
            case OWNER -> this.ownerOf(customMob)
                    .filter(owner -> this.inRange(entity, owner, skill.range()))
                    .ifPresent(found::add);
            case TARGET, LOOK -> {
                // Desde un mob, 'look' es lo mismo que su objetivo actual.
                if (entity instanceof Mob mob && mob.getTarget() != null
                        && this.inRange(entity, mob.getTarget(), skill.range())) {
                    found.add(mob.getTarget());
                }
            }
            case ENEMIES -> {
                for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                    if (this.service.policy().isValidTarget(customMob, candidate)) {
                        found.add(candidate);
                    }
                }
            }
            case ALLIES -> {
                for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                    if (this.isAlly(customMob, candidate)) {
                        found.add(candidate);
                    }
                }
            }
        }
        return found;
    }

    /** Resolucion nueva: LOOK (un objetivo filtrado por bando) o RADIUS (area). */
    private List<LivingEntity> resolveTyped(CustomMob customMob, SkillSpec skill) {
        LivingEntity entity = customMob.entity();
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.targetType()) {
            case LOOK -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(entity);
                } else if (entity instanceof Mob mob && mob.getTarget() != null) {
                    LivingEntity target = mob.getTarget();
                    if (skill.target() == SkillTarget.ENEMIES
                            && this.service.policy().isValidTarget(customMob, target)) {
                        found.add(target);
                    } else if (skill.target() == SkillTarget.ALLIES
                            && this.isAlly(customMob, target)) {
                        found.add(target);
                    }
                }
            }
            case RADIUS -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(entity);
                } else if (skill.target() == SkillTarget.ENEMIES) {
                    for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                        if (this.service.policy().isValidTarget(customMob, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (skill.target() == SkillTarget.ALLIES) {
                    for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                        if (this.isAlly(customMob, candidate)) {
                            found.add(candidate);
                        }
                    }
                }
            }
        }
        return found;
    }

    private void apply(CustomMob customMob, SkillSpec skill, LivingEntity target) {
        switch (skill.effect()) {
            case DAMAGE -> target.damage(skill.amount(), customMob.entity());
            case HEAL -> this.heal(target, skill.amount(), skill.percent());
            case IGNITE -> this.ignite(target, skill.amount());
            case FREEZE -> this.freeze(target, skill.amount());
            case BLOCK_BELOW -> this.blockBelow(target, skill);
            case EXPLOSION -> this.explosion(target, skill, customMob.entity());
            case REPAIR_ALL -> this.repairAll(target);
            case TELEPORT_BEHIND -> this.teleportBehind(customMob.entity(), target, skill);
            case BACKSTEP -> {
                if (target.getUniqueId().equals(customMob.entity().getUniqueId())) {
                    this.backstep(customMob.entity(), skill);
                }
            }
            case POTION -> this.potion(customMob.entity(), target, skill);
            case CONSOLE_COMMAND -> this.consoleCommand(customMob, skill,
                    target instanceof Player player ? player : this.onlineOwner(customMob));
            case PLAYER_COMMAND -> {
                if (target instanceof Player player) {
                    this.playerCommand(customMob, skill, player);
                }
            }
            case MESSAGE -> {
                // Resuelto aparte: un mensaje no tiene un unico destinatario.
            }
            case SUMMON -> {
                // Resuelto aparte: la invocacion se maneja antes de resolver objetivos.
            }
        }
        // Aviso opcional: en dano o curacion se le dice al jugador afectado.
        if (skill.hasMessage() && target instanceof Player player) {
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    this.spoken(customMob, skill, player)));
        }
    }

    private void heal(LivingEntity target, double amount, boolean percent) {
        AttributeInstance health = target.getAttribute(Attribute.MAX_HEALTH);
        double max = health != null ? health.getValue() : 20.0D;
        // En modo porcentaje, 'amount' es una fraccion de la vida maxima (0.50 = 50%).
        double value = percent ? max * amount : amount;
        target.setHealth(Math.max(0.0D, Math.min(max, target.getHealth() + value)));
    }

    /** Prende fuego al objetivo. {@code amount} son segundos. */
    private void ignite(LivingEntity target, double seconds) {
        int ticks = (int) Math.max(20L, Math.round(Math.max(1.0D, seconds) * 20.0D));
        target.setFireTicks(Math.max(target.getFireTicks(), ticks));
    }

    /**
     * Congela al objetivo. {@code amount} son segundos.
     *
     * <p>Los freeze ticks dan el estado visual y el dano de congelacion; la lentitud
     * asegura que tambien se note en el movimiento, porque el hielo visual por si solo
     * no siempre ralentiza.
     */
    private void freeze(LivingEntity target, double seconds) {
        int ticks = (int) Math.max(20L, Math.round(Math.max(1.0D, seconds) * 20.0D));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), ticks));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                ticks, 1, false, true, true));
    }

    /**
     * Convierte los bloques bajo el objetivo en {@code block}. Salta aire, liquidos y
     * bloques irrompibles; el radio y la profundidad ya vienen limitados del parser.
     */
    private void blockBelow(LivingEntity target, SkillSpec skill) {
        Material material = skill.block();
        if (material == null) {
            return;
        }
        int radius = Math.max(0, Math.min(5, skill.blockRadius()));
        int depth = Math.max(1, Math.min(5, skill.blockDepth()));
        Block base = target.getLocation().getBlock().getRelative(BlockFace.DOWN);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy < depth; dy++) {
                    Block block = base.getRelative(dx, -dy, dz);
                    Material current = block.getType();
                    if (current.isAir() || block.isLiquid()
                            || current == Material.BEDROCK
                            || current == Material.BARRIER
                            || current == Material.END_PORTAL_FRAME) {
                        continue;
                    }
                    block.setType(material, false);
                }
            }
        }
    }

    /**
     * Explosion al lado del objetivo, como un ender crystal. {@code amount} es la potencia
     * (1-10) y {@code break-blocks} decide si rompe terreno.
     */
    private void explosion(LivingEntity target, SkillSpec skill, LivingEntity source) {
        Location location = target.getLocation().add(0, 0.5, 0);
        if (source != null) {
            Vector direction = target.getLocation().toVector()
                    .subtract(source.getLocation().toVector());
            direction.setY(0);
            if (direction.lengthSquared() < 0.01D) {
                direction = target.getLocation().getDirection().setY(0);
            }
            if (direction.lengthSquared() > 0.0D) {
                direction.normalize();
                Vector side = new Vector(-direction.getZ(), 0, direction.getX())
                        .normalize().multiply(0.8D);
                location.add(side);
            }
        }
        float power = (float) Math.max(1.0D, Math.min(10.0D, skill.amount()));
        target.getWorld().createExplosion(location, power, false, skill.breakBlocks(), source);
    }

    /**
     * Coloca al lanzador detras del objetivo y lo orienta hacia el. Busca un punto
     * seguro y, si la config lo pide, consulta las protecciones.
     */
    private void teleportBehind(Entity caster, LivingEntity target, SkillSpec skill) {
        Location destination = this.safeBehind(target, skill.distance());
        if (destination == null) {
            return;
        }
        double eye = caster instanceof Player ? 1.62D : caster.getHeight() * 0.5D;
        Vector look = target.getEyeLocation().toVector()
                .subtract(destination.clone().add(0, eye, 0).toVector());
        destination.setDirection(look);
        this.teleport(caster, destination);
    }

    /** Retrocede al lanzador y lo deja mirando al punto de origen. */
    private void backstep(Entity caster, SkillSpec skill) {
        Vector facing = caster.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001D) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Location base = caster.getLocation().subtract(facing.multiply(
                Math.max(0.5D, Math.min(5.0D, skill.distance()))));
        Location destination = this.safeSpot(base);
        if (destination == null) {
            return;
        }
        double eye = caster instanceof Player ? 1.62D : caster.getHeight() * 0.5D;
        Location origin = caster.getLocation().clone().add(0, eye, 0);
        destination.setDirection(origin.toVector()
                .subtract(destination.clone().add(0, eye, 0).toVector()));
        this.teleport(caster, destination);
    }

    /** Aplica protecciones si la config lo pide y teletransporta. */
    private void teleport(Entity caster, Location destination) {
        if (this.service.plugin().config().teleportRespectProtections()) {
            if (caster instanceof Player player) {
                PlayerTeleportEvent event = new PlayerTeleportEvent(player,
                        player.getLocation(), destination, TeleportCause.PLUGIN);
                this.service.plugin().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
            } else {
                EntityTeleportEvent event = new EntityTeleportEvent(caster,
                        caster.getLocation(), destination);
                this.service.plugin().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
            }
        }
        caster.teleport(destination);
    }

    /** Punto libre detras del objetivo, o {@code null} si no hay sitio. */
    private Location safeBehind(LivingEntity target, double distance) {
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001D) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Location base = target.getLocation().subtract(facing.multiply(
                Math.max(0.5D, Math.min(5.0D, distance))));
        return this.safeSpot(base);
    }

    /** Busca dos bloques libres con suelo solido a partir de una base. */
    private Location safeSpot(Location base) {
        World world = base.getWorld();
        for (int dy = 0; dy <= 3; dy++) {
            Location candidate = base.clone().add(0, dy, 0);
            if (candidate.getY() <= world.getMinHeight() + 1
                    || candidate.getY() >= world.getMaxHeight() - 1) {
                continue;
            }
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(BlockFace.UP);
            Block below = feet.getRelative(BlockFace.DOWN);
            if (feet.isPassable() && head.isPassable() && below.getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Repara todos los objetos con durabilidad del inventario del jugador o del equipo
     * del mob. Respeta la proteccion 'reparar' del catalogo.
     */
    private void repairAll(LivingEntity target) {
        if (target instanceof Player player) {
            ItemStack[] contents = player.getInventory().getContents();
            for (ItemStack stack : contents) {
                this.repair(stack);
            }
            player.getInventory().setContents(contents);
            player.updateInventory();
            return;
        }
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = equipment.getItem(slot);
            if (this.repair(stack)) {
                equipment.setItem(slot, stack);
            }
        }
    }

    /** {@code true} si el objeto tenia dano y se repara. */
    private boolean repair(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (!(stack.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        if (damageable.getDamage() <= 0) {
            return false;
        }
        if (this.service.plugin().drops().catalog()
                .modificationDenied(stack, "reparar")) {
            return false;
        }
        damageable.setDamage(0);
        stack.setItemMeta(damageable);
        return true;
    }

    /**
     * La entidad viva que el jugador tiene en la cruz de vista.
     *
     * <p>Se hace el raycast a mano en vez de usar {@code getTargetEntity}: ese metodo queda
     * limitado por el rango de interaccion del jugador (3 bloques), no por el alcance que
     * pida la skill. Aqui manda el {@code range} de la skill, y un poco de tolerancia
     * lateral para no exigir punteria perfecta.
     */
    private LivingEntity lookingAt(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();
        double max = Math.max(1.0D, range);

        // Una pared corta el alcance: no se atraviesan bloques.
        RayTraceResult block = caster.getWorld().rayTraceBlocks(eye, direction, max,
                FluidCollisionMode.NEVER, true);
        if (block != null) {
            max = Math.min(max, block.getHitPosition().distance(eye.toVector()));
        }

        RayTraceResult hit = caster.getWorld().rayTraceEntities(eye, direction, max, 0.35D,
                entity -> entity instanceof LivingEntity
                        && !entity.getUniqueId().equals(caster.getUniqueId())
                        && !entity.isDead() && entity.isValid());
        return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    private void potion(LivingEntity caster, LivingEntity target, SkillSpec skill) {
        // Un efecto del plugin (effects.yml) manda sobre los de Minecraft, y vale tambien para
        // buffear al propio mob con target: self.
        var effects = this.service.plugin().effects();
        if (effects != null) {
            var custom = effects.effect(skill.potion()).orElse(null);
            if (custom != null) {
                UUID notify = caster instanceof Player player ? player.getUniqueId() : null;
                String end = skill.endMessage();
                if (end != null && caster instanceof Player player) {
                    end = end.replace("{jugador}", player.getName());
                }
                effects.apply(target, custom, skill.potionDurationSeconds(), notify, end);
                return;
            }
        }
        PotionEffectType type = this.effectOf(skill.potion());
        if (type == null) {
            return;
        }
        target.addPotionEffect(new PotionEffect(type,
                Math.max(1, skill.potionDurationSeconds()) * 20,
                Math.max(0, skill.potionAmplifier()),
                false, true, true));
    }

    private PotionEffectType effectOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Registry.EFFECT.get(NamespacedKey.minecraft(raw.toLowerCase(Locale.ROOT).trim()));
    }

    // ------------------------------------------------------------------ interaccion

    /** {@code true} si el mob esta en combate: tiene a alguien a quien atacar. */
    private boolean inCombat(LivingEntity entity) {
        return entity instanceof Mob mob && mob.getTarget() != null;
    }

    /**
     * Dispara las skills de interaccion de un mob: las que un jugador activa con clic
     * derecho. En {@code MESSAGE} el texto va solo a quien hizo clic, y admite
     * {@code {jugador}} ademas de {@code {mob}}.
     */
    public void interact(CustomMob customMob, Player clicker) {
        List<SkillSpec> skills = customMob.definition().skills();
        if (skills.isEmpty()) {
            return;
        }
        LivingEntity entity = customMob.entity();
        if (entity.isDead() || !entity.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (SkillSpec skill : skills) {
            if (skill.trigger() == SkillTrigger.INTERACT) {
                this.useInteraction(customMob, skill, clicker, now);
            }
        }
    }

    private void useInteraction(CustomMob customMob, SkillSpec skill, Player clicker, long now) {
        LivingEntity entity = customMob.entity();
        Map<String, Long> perMob = this.cooldowns.computeIfAbsent(entity.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        Long until = perMob.get(skill.id());
        if (until != null && now < until) {
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            return;
        }
        perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
        if (skill.effect() == SkillEffect.MESSAGE) {
            // El dialogo va solo a quien hizo clic, no a todo el que este cerca.
            String text = this.spoken(customMob, skill, clicker);
            if (this.service.config().debug()) {
                this.service.plugin().getLogger().info("[skill] Interaccion de "
                        + customMob.definition().id() + " a " + clicker.getName() + ": " + text);
            }
            clicker.sendMessage(com.juanp.custommobs.mob.Texts.color(text));
            return;
        }
        // Con un clic, el actor es quien hizo clic: los comandos y la invocacion van por ahi,
        // sin pasar por los objetivos declarados.
        switch (skill.effect()) {
            case CONSOLE_COMMAND -> this.consoleCommand(customMob, skill, clicker);
            case PLAYER_COMMAND -> this.playerCommand(customMob, skill, clicker);
            case SUMMON -> this.summon(customMob, skill);
            case CHAIN -> this.service.plugin().chains().start(customMob, clicker, skill);
            default -> {
                // Los demas efectos se resuelven igual que una skill normal: el clic es el gatillo.
                for (LivingEntity target : this.resolve(customMob, skill)) {
                    this.apply(customMob, skill, target);
                }
            }
        }
        if (skill.hasMessage()) {
            clicker.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    this.spoken(customMob, skill, clicker)));
        }
    }

    // ------------------------------------------------------------------ comandos

    /**
     * Ejecuta el comando de la skill como si lo escribiera la consola.
     *
     * <p>El texto admite {@code {mob}} y {@code {jugador}}: el segundo se sustituye por el
     * jugador que disparo la skill —quien hizo clic, el objetivo, o el dueno—. Si no hay
     * jugador al que referirse, el comando igual sale, con {@code {jugador}} sin sustituir.
     */
    private void consoleCommand(CustomMob customMob, SkillSpec skill, Player player) {
        String text = this.commandText(customMob, skill, player);
        if (text == null) {
            return;
        }
        this.service.plugin().getServer().dispatchCommand(
                this.service.plugin().getServer().getConsoleSender(), text);
    }

    /** Hace que el jugador ejecute el comando de la skill, como si lo hubiera escrito el. */
    private void playerCommand(CustomMob customMob, SkillSpec skill, Player player) {
        if (player == null) {
            return;
        }
        String text = this.commandText(customMob, skill, player);
        if (text == null) {
            return;
        }
        player.performCommand(text);
    }

    /** El comando con los marcadores sustituidos, o {@code null} si la skill no trae ninguno. */
    private String commandText(CustomMob customMob, SkillSpec skill, Player player) {
        if (skill.command() == null || skill.command().isBlank()) {
            return null;
        }
        String text = skill.command().replace("{mob}", customMob.definition().displayName());
        return player == null ? text : text.replace("{jugador}", player.getName());
    }

    /** El dueno del mob, si esta conectado; si no, {@code null}. */
    private Player onlineOwner(CustomMob customMob) {
        if (customMob.ownerId() == null) {
            return null;
        }
        Player owner = this.service.plugin().getServer().getPlayer(customMob.ownerId());
        return owner != null && owner.isOnline() ? owner : null;
    }

    // ------------------------------------------------------------------ invocacion

    /**
     * Invoca hasta {@code amount} copias de la definicion {@code summon}, alrededor del mob.
     *
     * <p>Los invocados toman el dueno del invocador: si el que invoca es de un jugador, los
     * suyos tambien. Los mobs de servidor invocan mobs libres.
     *
     * @return {@code true} si aparecio al menos uno
     */
    private boolean summon(CustomMob caster, SkillSpec skill) {
        if (skill.summon() == null || skill.summon().isBlank()) {
            return false;
        }
        MobDefinition definition = this.service.plugin().registry().get(skill.summon()).orElse(null);
        if (definition == null) {
            return false;
        }
        int count = Math.max(1, Math.min(MAX_SUMMON, (int) Math.round(skill.amount())));
        double range = Math.max(1.0D, skill.range());
        LivingEntity entity = caster.entity();
        World world = entity.getWorld();
        Player owner = caster.ownerId() == null
                ? null : this.service.plugin().getServer().getPlayer(caster.ownerId());
        int placed = 0;
        for (int i = 0; i < count; i++) {
            Location spot = this.summonSpot(world, entity.getLocation(), range);
            if (spot == null) {
                continue;
            }
            this.service.spawn(definition, spot, owner);
            placed++;
        }
        return placed > 0;
    }

    /** Un sitio libre cerca del invocador, o {@code null} si no lo encuentra. */
    private Location summonSpot(World world, Location around, double range) {
        for (int tries = 0; tries < 8; tries++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 1.0D + this.random.nextDouble() * Math.max(0.0D, range - 1.0D);
            int x = around.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = around.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Block ground = world.getBlockAt(x, y - 1, z);
            Block air = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid() || !air.isPassable()) {
                continue;
            }
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    // ------------------------------------------------------------------ auxiliares

    private Optional<LivingEntity> ownerOf(CustomMob customMob) {
        UUID ownerId = customMob.ownerId();
        if (ownerId == null) {
            return Optional.empty();
        }
        Player owner = this.service.plugin().getServer().getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return Optional.empty();
        }
        return Optional.of(owner);
    }

    /**
     * {@code true} si el objetivo esta dentro del radio de la skill.
     *
     * <p>El cargador siempre deja un radio util (por defecto 8 bloques), asi que en la
     * practica estas skills siempre tienen tope; la guarda de 0 o menos solo protege a quien
     * construya un SkillSpec a mano.
     */
    private boolean inRange(LivingEntity source, LivingEntity target, double range) {
        if (range <= 0.0D) {
            return true;
        }
        if (!source.getWorld().equals(target.getWorld())) {
            return false;
        }
        return source.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

    private List<LivingEntity> nearby(LivingEntity entity, double range) {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity candidate : entity.getNearbyEntities(range, range, range)) {
            if (candidate instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                found.add(living);
            }
        }
        return found;
    }

    private List<Player> listeners(LivingEntity entity, double range) {
        double effective = range > 0 ? range : DEFAULT_SPEAK_RANGE;
        List<Player> found = new ArrayList<>();
        for (Entity candidate : entity.getNearbyEntities(effective, effective, effective)) {
            if (candidate instanceof Player player && !player.isDead()) {
                found.add(player);
            }
        }
        return found;
    }

    /** Un aliado: el dueno, un companero de team, o un mob de faccion aliada. */
    private boolean isAlly(CustomMob source, LivingEntity candidate) {
        if (candidate.getUniqueId().equals(source.entity().getUniqueId())) {
            return false;
        }
        if (source.isOwner(candidate.getUniqueId())) {
            return true;
        }
        Optional<CustomMob> other = this.service.find(candidate.getUniqueId());
        if (other.isPresent()) {
            CustomMob ally = other.get();
            if (source.definition().server() && ally.definition().server()) {
                return this.service.factions().isAlly(source.definition().faction(), ally.definition().faction());
            }
            return source.teamId() != null && source.teamId().equals(ally.teamId());
        }
        if (candidate instanceof Player player) {
            return source.ownerId() != null
                    && this.service.teamLink().isAlly(source.ownerId(), player.getUniqueId());
        }
        return false;
    }

    // ------------------------------------------------------------------ skills de item

    /**
     * Lanza skills desde un jugador, en el orden en que las declara el item.
     *
     * <p>El cooldown lo aplica el item: usa el mayor entre su propio 'cooldown' y el
     * 'cooldown-seconds' de las skills que lanza. Tampoco se tira 'chance': un clic
     * consciente no debe fallar al azar.
     */
    public int cast(Player caster, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int cast = 0;
        for (String id : ids) {
            SkillSpec skill = this.service.plugin().skillCatalog().get(id).orElse(null);
            if (skill == null) {
                this.service.plugin().getLogger().warning(
                        "El item apunta a una skill que no existe en skills/: " + id);
                continue;
            }
            if (skill.trigger() == SkillTrigger.IMPACT) {
                // Se arma al disparar y se resuelve al impactar: no se castea al usar.
                continue;
            }
            this.castOne(caster, skill);
            cast++;
        }
        return cast;
    }

    private void castOne(Player caster, SkillSpec skill) {
        // La unica puerta para comandos y cadenas desde un item es 'mode: item'.
        boolean commandLike = skill.effect() == SkillEffect.CONSOLE_COMMAND
                || skill.effect() == SkillEffect.PLAYER_COMMAND
                || skill.effect() == SkillEffect.CHAIN;
        if (commandLike && skill.trigger() != SkillTrigger.ITEM) {
            this.service.plugin().getLogger().warning("La skill '" + skill.id()
                    + "' usa " + skill.effect() + " pero no es 'mode: item'; no se ejecuta"
                    + " desde un objeto.");
            return;
        }
        if (skill.effect() == SkillEffect.CHAIN || skill.effect() == SkillEffect.SUMMON) {
            this.service.plugin().getLogger().warning("La skill '" + skill.id() + "' usa "
                    + skill.effect() + "; ese efecto no se admite desde un item.");
            return;
        }
        if (skill.hasStartMessage()) {
            caster.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    skill.startMessage().replace("{jugador}", caster.getName())));
        }
        if (skill.effect() == SkillEffect.CONSOLE_COMMAND) {
            this.consoleCommandFromPlayer(caster, skill);
            return;
        }
        if (skill.effect() == SkillEffect.PLAYER_COMMAND) {
            caster.performCommand(this.commandTextForPlayer(caster, skill));
            return;
        }
        if (skill.effect() == SkillEffect.MESSAGE) {
            String text = this.spokenForPlayer(caster, skill);
            if (text.isBlank()) {
                return;
            }
            double range = skill.range() > 0 ? skill.range() : DEFAULT_SPEAK_RANGE;
            for (Player listener : this.listeners(caster, range)) {
                listener.sendMessage(com.juanp.custommobs.mob.Texts.color(text));
            }
            return;
        }

        for (LivingEntity target : this.playerTargets(caster, skill)) {
            this.applyFromPlayer(caster, skill, target);
        }
    }

    /** Aplica el efecto de una skill de jugador a un objetivo ya resuelto. */
    private void applyFromPlayer(Player caster, SkillSpec skill, LivingEntity target) {
        switch (skill.effect()) {
            case HEAL -> this.heal(target, skill.amount(), skill.percent());
            case DAMAGE -> target.damage(skill.amount(), caster);
            case IGNITE -> this.ignite(target, skill.amount());
            case FREEZE -> this.freeze(target, skill.amount());
            case BLOCK_BELOW -> this.blockBelow(target, skill);
            case EXPLOSION -> this.explosion(target, skill, caster);
            case REPAIR_ALL -> this.repairAll(target);
            case TELEPORT_BEHIND -> this.teleportBehind(caster, target, skill);
            case BACKSTEP -> {
                if (target.getUniqueId().equals(caster.getUniqueId())) {
                    this.backstep(caster, skill);
                }
            }
            case POTION -> this.potion(caster, target, skill);
            default -> {
            }
        }
        if (skill.hasMessage() && target instanceof Player player) {
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    this.spokenForPlayer(caster, skill)));
        }
    }

    // ------------------------------------------------------------------ impacto

    /**
     * Castea una skill de impacto: el origen es el punto donde cayo el proyectil y
     * {@code hit} la entidad golpeada, si la hubo.
     *
     * <p>El lanzador sigue siendo el jugador, para atribuir el dano y resolver los
     * avisos. El cooldown y el aviso de inicio los pone el disparo, no el impacto.
     */
    public void castImpact(Player shooter, SkillSpec skill, Location origin, LivingEntity hit) {
        if (skill.effect() == SkillEffect.CHAIN || skill.effect() == SkillEffect.SUMMON
                || skill.effect() == SkillEffect.CONSOLE_COMMAND
                || skill.effect() == SkillEffect.PLAYER_COMMAND) {
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            return;
        }
        for (LivingEntity target : this.impactTargets(shooter, skill, origin, hit)) {
            this.applyFromPlayer(shooter, skill, target);
        }
    }

    /**
     * Objetivos de una skill de impacto. Sin {@code target-type} es una sola entidad:
     * la golpeada. Con {@code radius}, el area se mide desde el punto de impacto.
     */
    private List<LivingEntity> impactTargets(Player shooter, SkillSpec skill,
                                             Location origin, LivingEntity hit) {
        List<LivingEntity> found = new ArrayList<>();
        boolean area = skill.targetType() == SkillTargetType.RADIUS;
        double range = skill.range() > 0 ? skill.range() : DEFAULT_SPEAK_RANGE;
        switch (skill.target()) {
            case IMPACT -> {
                if (hit != null) {
                    found.add(hit);
                }
            }
            case SELF -> found.add(shooter);
            case ENEMIES -> {
                if (area) {
                    for (LivingEntity candidate : this.nearby(origin, range)) {
                        if (this.isEnemyOfPlayer(shooter, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (hit != null && this.isEnemyOfPlayer(shooter, hit)) {
                    found.add(hit);
                }
            }
            case ALLIES -> {
                if (area) {
                    // El lanzador tambien es aliado de si mismo, pero solo si esta dentro
                    // del radio: curar a distancia no debe curar a quien disparo.
                    if (origin != null && shooter.getWorld().equals(origin.getWorld())
                            && shooter.getLocation().distanceSquared(origin) <= range * range) {
                        found.add(shooter);
                    }
                    for (LivingEntity candidate : this.nearby(origin, range)) {
                        if (this.isAllyOfPlayer(shooter, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (hit != null && this.isAllyOfPlayer(shooter, hit)) {
                    found.add(hit);
                }
            }
            default -> {
            }
        }
        return found;
    }

    /** Entidades vivas alrededor de un punto (el centro no se incluye). */
    private List<LivingEntity> nearby(Location center, double range) {
        List<LivingEntity> found = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return found;
        }
        for (Entity candidate : center.getWorld().getNearbyEntities(center, range, range, range)) {
            if (candidate instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                found.add(living);
            }
        }
        return found;
    }

    private List<LivingEntity> playerTargets(Player caster, SkillSpec skill) {
        if (skill.targetType() != null) {
            return this.playerTargetsTyped(caster, skill);
        }
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.target()) {
            case SELF -> found.add(caster);
            case TARGET, LOOK -> {
                LivingEntity looked = this.lookingAt(caster, skill.range());
                if (looked != null) {
                    found.add(looked);
                }
            }
            case ALLIES -> {
                for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                    if (this.isAllyOfPlayer(caster, candidate)) {
                        found.add(candidate);
                    }
                }
            }
            case ENEMIES -> {
                for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                    if (this.isEnemyOfPlayer(caster, candidate)) {
                        found.add(candidate);
                    }
                }
            }
            // OWNER es un concepto de mob; desde un item apunta al lanzador.
            default -> found.add(caster);
        }
        return found;
    }

    /** Resolucion nueva desde un jugador: LOOK filtrado por bando o RADIUS. */
    private List<LivingEntity> playerTargetsTyped(Player caster, SkillSpec skill) {
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.targetType()) {
            case LOOK -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(caster);
                } else {
                    LivingEntity looked = this.lookingAt(caster, skill.range());
                    if (looked != null) {
                        if (skill.target() == SkillTarget.ENEMIES
                                && this.isEnemyOfPlayer(caster, looked)) {
                            found.add(looked);
                        } else if (skill.target() == SkillTarget.ALLIES
                                && this.isAllyOfPlayer(caster, looked)) {
                            found.add(looked);
                        }
                    }
                }
            }
            case RADIUS -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(caster);
                } else if (skill.target() == SkillTarget.ENEMIES) {
                    for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                        if (this.isEnemyOfPlayer(caster, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (skill.target() == SkillTarget.ALLIES) {
                    for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                        if (this.isAllyOfPlayer(caster, candidate)) {
                            found.add(candidate);
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Aliado de un jugador: su team, su faccion, una aliada, o un mob de los suyos. */
    private boolean isAllyOfPlayer(Player caster, LivingEntity candidate) {
        if (candidate.getUniqueId().equals(caster.getUniqueId())) {
            return false;
        }
        if (candidate instanceof Player other) {
            if (this.service.teamLink().isAlly(caster.getUniqueId(), other.getUniqueId())) {
                return true;
            }
            return this.service.sameSide(this.service.factionOf(caster),
                    this.service.factionOf(other));
        }
        CustomMob other = this.service.find(candidate.getUniqueId()).orElse(null);
        if (other == null) {
            return false;
        }
        if (other.isOwner(caster.getUniqueId())) {
            return true;
        }
        if (other.ownerId() != null
                && this.service.teamLink().isAlly(caster.getUniqueId(), other.ownerId())) {
            return true;
        }
        return this.service.sameSide(this.service.factionOf(caster),
                this.service.factionOf(other));
    }

    /** Enemigo de un jugador: facciones enemigas. Sin faccion no hay enemistad. */
    private boolean isEnemyOfPlayer(Player caster, LivingEntity candidate) {
        if (candidate.getUniqueId().equals(caster.getUniqueId())) {
            return false;
        }
        if (candidate instanceof Player other) {
            if (this.service.teamLink().isAlly(caster.getUniqueId(), other.getUniqueId())) {
                return false;
            }
            String mine = this.service.factionOf(caster);
            String theirs = this.service.factionOf(other);
            return mine != null && theirs != null && this.service.factions().isEnemy(mine, theirs);
        }
        CustomMob other = this.service.find(candidate.getUniqueId()).orElse(null);
        if (other != null) {
            if (other.isOwner(caster.getUniqueId())) {
                return false;
            }
            if (other.ownerId() != null
                    && this.service.teamLink().isAlly(caster.getUniqueId(), other.ownerId())) {
                return false;
            }
            String mine = this.service.factionOf(caster);
            String theirs = this.service.factionOf(other);
            return mine != null && theirs != null && this.service.factions().isEnemy(mine, theirs);
        }
        // Vanilla: los monstruos hostiles son enemigos; el resto no.
        return candidate instanceof org.bukkit.entity.Monster;
    }

    private String spokenForPlayer(Player caster, SkillSpec skill) {
        String message = skill.hasMessage() ? skill.message() : "";
        return message.replace("{mob}", caster.getName())
                .replace("{jugador}", caster.getName());
    }

    private String commandTextForPlayer(Player caster, SkillSpec skill) {
        String command = skill.command() == null ? "" : skill.command();
        return command.replace("{mob}", caster.getName())
                .replace("{jugador}", caster.getName());
    }

    private void consoleCommandFromPlayer(Player caster, SkillSpec skill) {
        String command = this.commandTextForPlayer(caster, skill);
        if (command.isBlank()) {
            return;
        }
        this.service.plugin().getServer().dispatchCommand(
                this.service.plugin().getServer().getConsoleSender(), command);
    }

    /** Texto que dice el mob: {@code {mob}} se sustituye por su nombre visible. */
    private String spoken(CustomMob customMob, SkillSpec skill) {
        return this.spoken(customMob, skill, null);
    }

    /** Igual, pero con {@code {jugador}} disponible cuando hay un interlocutor. */
    private String spoken(CustomMob customMob, SkillSpec skill, Player listener) {
        String message = skill.hasMessage() ? skill.message() : "";
        String text = message.replace("{mob}", customMob.definition().displayName());
        if (listener != null) {
            text = text.replace("{jugador}", listener.getName());
        }
        return text;
    }
}
