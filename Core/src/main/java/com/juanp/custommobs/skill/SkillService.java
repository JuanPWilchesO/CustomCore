package com.juanp.custommobs.skill;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
                if (skill.trigger() == SkillTrigger.INTERACT) {
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
                listener.sendMessage(spoken);
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
        LivingEntity entity = customMob.entity();
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.target()) {
            case SELF -> found.add(entity);
            // El radio manda tambien para el dueno y para el objetivo atacado. Sin esto, una
            // skill de montura le seguia curando al dueno desde el otro lado del mundo.
            case OWNER -> this.ownerOf(customMob)
                    .filter(owner -> this.inRange(entity, owner, skill.range()))
                    .ifPresent(found::add);
            case TARGET -> {
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

    private void apply(CustomMob customMob, SkillSpec skill, LivingEntity target) {
        switch (skill.effect()) {
            case DAMAGE -> target.damage(skill.amount(), customMob.entity());
            case HEAL -> this.heal(target, skill.amount());
            case POTION -> this.potion(target, skill);
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
            player.sendMessage(skill.message());
        }
    }

    private void heal(LivingEntity target, double amount) {
        AttributeInstance health = target.getAttribute(Attribute.MAX_HEALTH);
        double max = health != null ? health.getValue() : 20.0D;
        target.setHealth(Math.max(0.0D, Math.min(max, target.getHealth() + amount)));
    }

    private void potion(LivingEntity target, SkillSpec skill) {
        // Un efecto del plugin (effects.yml) manda sobre los de Minecraft, y vale tambien para
        // buffear al propio mob con target: self.
        var effects = this.service.plugin().effects();
        if (effects != null) {
            var custom = effects.effect(skill.potion()).orElse(null);
            if (custom != null) {
                effects.apply(target, custom, skill.potionDurationSeconds());
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
            clicker.sendMessage(text);
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
            clicker.sendMessage(skill.message());
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
