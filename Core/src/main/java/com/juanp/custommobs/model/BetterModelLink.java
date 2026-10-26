package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.MobDefinition;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bone.BoneTags;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.nms.HitBox;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.api.util.function.BonePredicate;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enlace con BetterModel 3.x. Aislado en su propia clase para que el resto del plugin
 * cargue aunque BetterModel no este instalado.
 *
 * <p>La entidad de Bukkit no se toca: sigue teniendo vida, dano y objetivos. El modelo se
 * dibuja encima y, si la definicion lo pide, se ocultan partes del cuerpo vanilla.
 *
 * <p><b>Nunca se oculta el cuerpo sin un hitbox que lo sustituya</b>: un mob invisible y
 * sin hitbox es intocable.
 */
public final class BetterModelLink implements ModelLink {

    /** Margen tras el que se cierra el tracker de un mob muerto. */
    private static final long DEATH_KEEP_TICKS = 40L;

    private final Plugin plugin;
    private final Map<java.util.UUID, EntityTracker> trackers = new ConcurrentHashMap<>();
    /**
     * Mobs que estan andando ahora mismo.
     *
     * <p>Es la UNICA fuente de verdad para alternar idle/walk: antes habia ademas un
     * {@code idlePlayed}, y los dos se contradecian (se quedaba puesta la animacion de
     * andar con el mob parado).
     */
    private final Set<java.util.UUID> walking = ConcurrentHashMap.newKeySet();
    /** Mobs cuyo nombre vanilla se oculto para que mande la placa del modelo. */
    private final Set<java.util.UUID> hiddenNames = ConcurrentHashMap.newKeySet();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedModels = ConcurrentHashMap.newKeySet();

    private final boolean requireHitBox;

    public BetterModelLink(Plugin plugin, boolean requireHitBox) {
        this.plugin = plugin;
        this.requireHitBox = requireHitBox;
        Set<String> keys = this.modelKeys();
        this.plugin.getLogger().info("BetterModel enlazado: " + keys.size() + " modelo(s) cargado(s).");
    }

    @Override
    public String name() {
        return "BetterModel";
    }

    @Override
    public void apply(LivingEntity entity, MobDefinition definition, boolean disguised) {
        ModelSpec spec = definition.model();
        if (spec == null || !spec.active()) {
            return;
        }
        if (disguised) {
            // Decision del proyecto: el disfraz manda. Se avisa una vez por mob, no por entidad.
            this.warnOnce("disguise:" + definition.id(),
                    "El mob '" + definition.id() + "' tiene 'disguise' y 'model': se aplica el disfraz"
                            + " y se ignora el modelo de BetterModel.");
            return;
        }
        try {
            this.attach(entity, definition, spec);
        } catch (Throwable throwable) {
            this.warnOnce("apply:" + definition.id() + ":" + throwable.getMessage(),
                    "No se pudo aplicar el modelo '" + spec.name() + "' de '" + definition.id()
                            + "': " + throwable.getMessage());
        }
    }

    private void attach(LivingEntity entity, MobDefinition definition, ModelSpec spec) {
        ModelRenderer renderer = BetterModel.modelOrNull(spec.name());
        if (renderer == null) {
            this.warnMissingModel(definition, spec);
            return;
        }

        PlatformEntity target = BukkitAdapter.adapt((org.bukkit.entity.Entity) entity);
        TrackerModifier defaultModifier = TrackerModifier.DEFAULT;
        TrackerModifier.Builder modifier = defaultModifier.toBuilder()
                .damageAnimation(spec.damageAnimation())
                .damageTint(spec.damageTint());
        if (spec.sightTrace() != null) {
            modifier.sightTrace(spec.sightTrace());
        }

        // getOrCreate: si el tracker ya existe (restauracion, cambio de mundo) no se duplica.
        EntityTracker tracker = renderer.getOrCreate(target, modifier.build(), created -> {
            if (spec.scale() != null) {
                created.scaler(ModelScaler.value(spec.scale().floatValue()));
            }
        });
        this.trackers.put(entity.getUniqueId(), tracker);

        BoneInfo bones = this.readBones(tracker);
        // Una montura con asiento tambien sirve de caja de colision.
        boolean hasBox = bones.hitBox() || (spec.seat() && bones.seat());
        boolean hitBox = !this.requireHitBox || hasBox;

        if (spec.hideBody() || spec.hideEquipment() || spec.hideGlow()) {
            if (hitBox || !spec.hideBody()) {
                tracker.hideOption(EntityHideOption.builder()
                        .or(spec.hideBody() ? EntityHideOption.DEFAULT : EntityHideOption.FALSE)
                        .or(spec.hideEquipment() ? HIDE_EQUIPMENT : EntityHideOption.FALSE)
                        .or(spec.hideGlow() ? HIDE_GLOW : EntityHideOption.FALSE)
                        .build());
            } else {
                // Sin hitbox propia, ocultar el cuerpo dejaria al mob intocable: se deja visible.
                this.warnOnce("nohitbox:" + definition.id(),
                        "El modelo '" + spec.name() + "' no declara hitbox: BetterModel solo se la"
                                + " pone a los huesos cuyo NOMBRE lleva el tag 'b' u 'ob' delante"
                                + " (por ejemplo 'b_body' u 'ob_hitbox'), no a un hueso llamado"
                                + " 'hitbox'. El mob '" + definition.id() + "' conserva su cuerpo"
                                + " vanilla para poder recibir dano. Renombra el hueso en Blockbench"
                                + " o pon 'hide-body: false'.");
            }
        }

        this.applyName(entity, definition, spec, bones);
        this.animate(entity, definition, ModelAnimation.SPAWN);
        this.animate(entity, definition, ModelAnimation.IDLE);
    }

    /**
     * Decide quien pinta el nombre del mob.
     *
     * <p>Si el modelo trae un hueso con tag {@code mtag}, BetterModel dibuja SU placa siguiendo
     * al hueso. Dejar ademas la vanilla es lo que la hace parpadear: las dos se superponen y la
     * del modelo se oculta cuando no hay linea de vision. En ese caso se apaga la vanilla.
     */
    private void applyName(LivingEntity entity, MobDefinition definition, ModelSpec spec,
                           BoneInfo bones) {
        Boolean showName = spec.showName();
        boolean hide = showName != null ? !showName : bones.name();
        if (!hide) {
            return;
        }
        // Se recuerda para devolverlo al liberar el mob: el nombre vanilla es parte del mob
        // custom y otros sistemas (el estilo, el libro) cuentan con el.
        if (entity.isCustomNameVisible()) {
            this.hiddenNames.add(entity.getUniqueId());
        }
        entity.setCustomNameVisible(false);
    }

    /** Oculta solo el equipo: cuerpo visible. */
    private static final EntityHideOption HIDE_EQUIPMENT = option(false, true, false, false);
    /** Oculta solo el brillo. */
    private static final EntityHideOption HIDE_GLOW = option(false, false, false, true);

    /**
     * EntityHideOption no expone un builder por campo en su API de Java; se compone desde
     * los presets. {@code DEFAULT} oculta todo, asi que el resto se arma a mano.
     */
    private static EntityHideOption option(boolean equipment, boolean fire, boolean visibility,
                                           boolean glowing) {
        String json = "[\"" + equipment + "\",\"" + fire + "\",\"" + visibility + "\",\""
                + glowing + "\"]";
        try {
            return EntityHideOption.deserialize(com.google.gson.JsonParser.parseString(json)
                    .getAsJsonArray());
        } catch (Throwable throwable) {
            return EntityHideOption.FALSE;
        }
    }

    /**
     * Lee los huesos del tracker, que es la unica fuente fiable de lo que el modelo aporta.
     *
     * <p>Solo se MIRA: no se crea nada. Antes se llamaba a {@code createHitBox(null, ...)} y
     * la implementacion de BetterModel hace {@code checkNotNull(entity)}, asi que lanzaba
     * siempre y el resultado era "sin hitbox" para CUALQUIER modelo. BetterModel ya crea las
     * hitboxes de los huesos con tag {@code b}/{@code ob} por su cuenta.
     */
    private BoneInfo readBones(EntityTracker tracker) {
        boolean hitBox = false;
        boolean seat = false;
        boolean name = false;
        if (tracker == null || tracker.isClosed()) {
            return new BoneInfo(hitBox, seat, name);
        }
        try {
            for (RenderedBone bone : tracker.bones()) {
                hitBox |= bone.name().tagged(BoneTags.HITBOX);
                seat |= bone.name().tagged(BoneTags.SEAT);
                name |= bone.name().tagged(BoneTags.MOB_TAG, BoneTags.TAG, BoneTags.PLAYER_TAG);
            }
        } catch (Throwable throwable) {
            // Sin poder leer los huesos se asume lo conservador: no ocultar el cuerpo.
        }
        return new BoneInfo(hitBox, seat, name);
    }

    /** Lo que aporta el modelo a la entidad, leido de sus huesos. */
    private record BoneInfo(boolean hitBox, boolean seat, boolean name) {
    }

    @Override
    public boolean seatEnabled(LivingEntity entity, MobDefinition definition) {
        ModelSpec spec = definition.model();
        if (spec == null || !spec.active() || !spec.seat() || !definition.mountable()) {
            return false;
        }
        // El asiento es un hueso con tag 'p': si el modelo no lo trae, no hay donde sentarse.
        return this.readBones(this.trackers.get(entity.getUniqueId())).seat();
    }

    @Override
    public void seat(LivingEntity entity, MobDefinition definition, org.bukkit.entity.Player player) {
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        try {
            EntityTrackerRegistry registry = tracker.registry();
            if (registry == null) {
                return;
            }
            PlatformEntity passenger = BukkitAdapter.adapt((org.bukkit.entity.Entity) player);
            for (HitBox box : registry.hitBoxes()) {
                if (box.positionSource() != null && box.positionSource().name().tagged(BoneTags.SEAT)) {
                    box.mount(passenger);
                    return;
                }
            }
            // Sin hueso SEAT no hay asiento; la columna vertebral sigue quieta.
            this.warnOnce("seat:" + definition.id(),
                    "La montura '" + definition.id() + "' usa 'model.seat' pero el modelo '"
                            + definition.model().name() + "' no tiene ningun hueso con el tag"
                            + " 'seat': el jugador se monta al asiento vanilla del caballo.");
        } catch (Throwable throwable) {
            this.warnOnce("seat:" + definition.id() + ":" + throwable.getMessage(),
                    "No se pudo subir al jugador al asiento de '" + definition.id() + "': "
                            + throwable.getMessage());
        }
    }

    @Override
    public void refresh(LivingEntity entity, MobDefinition definition) {
        ModelSpec spec = definition.model();
        if (spec == null || !spec.active()) {
            this.release(entity);
            return;
        }
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            // Ya no queda tracker (reinicio, cambio de mundo): se vuelve a crear. El
            // disfraz deja de contar aqui: si el modelo llego a aplicarse, sigue aplicandose.
            try {
                this.attach(entity, definition, spec);
            } catch (Throwable throwable) {
                this.warnOnce("refresh:" + definition.id() + ":" + throwable.getMessage(),
                        "No se pudo reaplicar el modelo de '" + definition.id() + "': "
                                + throwable.getMessage());
            }
            return;
        }
        try {
            tracker.forceUpdate(true);
        } catch (Throwable throwable) {
            // Un tracker cerrado por el propio BetterModel no es un error del plugin.
        }
    }

    @Override
    public boolean hasModel(LivingEntity entity) {
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        return tracker != null && !tracker.isClosed();
    }

    @Override
    public void animate(LivingEntity entity, MobDefinition definition, ModelAnimation event) {
        AnimationSpec spec = definition.model() == null ? null : definition.model().animation(event);
        if (spec == null || spec.name().isBlank()) {
            return;
        }
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        if (event == ModelAnimation.IDLE && !this.hasModel(entity)) {
            // Mob sin modelo (disfrazado, o modelo inexistente): no hay nada que animar.
            // (Antes se comprueba el tracker: un mob disfrazado no debe recibir animaciones.)
            return;
        }
        try {
            if (event == ModelAnimation.WALK) {
                // Solo al EMPEZAR a andar. Relanzarla en cada ciclo la cortaria a si misma.
                if (this.walking.add(entity.getUniqueId())) {
                    this.play(tracker, spec, definition, ModelAnimation.IDLE);
                }
                return;
            }
            if (event == ModelAnimation.IDLE) {
                // Solo se vuelve al reposo si venia andando: repetirla en cada ciclo de la tarea
                // la reiniciaria sin parar.
                if (this.walking.remove(entity.getUniqueId())) {
                    this.play(tracker, spec, definition, ModelAnimation.WALK);
                }
                return;
            }
            this.play(tracker, spec, definition, null);
            if (event == ModelAnimation.DEATH) {
                this.closeAfterDeath(entity.getUniqueId(), tracker);
            }
        } catch (Throwable throwable) {
            this.warnOnce("anim:" + definition.id() + ":" + event.key(),
                    "No se pudo reproducir la animacion '" + spec.name() + "' (" + event.key()
                            + ") en '" + definition.id() + "'.");
        }
    }

    /** Anima el estado en reposo tras dejar de andar. */
    @Override
    public void stopWalking(LivingEntity entity, MobDefinition definition) {
        if (this.walking.remove(entity.getUniqueId())) {
            AnimationSpec idle = definition.model() == null
                    ? null
                    : definition.model().animation(ModelAnimation.IDLE);
            if (idle != null && !idle.name().isBlank()) {
                this.play(this.trackers.get(entity.getUniqueId()), idle, definition,
                        ModelAnimation.WALK);
            }
        }
    }

    /**
     * Lanza una animacion parando antes la del estado contrario.
     *
     * <p>Sin el {@code stopAnimation} previo, la animacion de andar se queda montada encima de
     * la de reposo: BetterModel apila animaciones y la que no se para sigue mandando. Ese era
     * el sintoma de "se queda la de andar con el mob parado".
     */
    private void play(EntityTracker tracker, AnimationSpec spec, MobDefinition definition,
                      ModelAnimation stop) {
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        if (stop != null && definition != null && definition.model() != null) {
            AnimationSpec previous = definition.model().animation(stop);
            if (previous != null && !previous.name().isBlank()
                    && !previous.name().equalsIgnoreCase(spec.name())) {
                tracker.stopAnimation(previous.name());
            }
        }
        tracker.animate(spec.name(), modifier(spec));
    }

    private void closeAfterDeath(java.util.UUID id, EntityTracker tracker) {
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            this.trackers.remove(id);
            try {
                tracker.close();
            } catch (Throwable throwable) {
                // Ya estaba cerrado.
            }
        }, DEATH_KEEP_TICKS);
    }

    private static AnimationModifier modifier(AnimationSpec spec) {
        AnimationModifier.Builder builder = AnimationModifier.builder()
                .start(spec.start())
                .end(spec.end())
                .type(spec.once()
                        ? AnimationIterator.Type.PLAY_ONCE
                        : AnimationIterator.Type.LOOP);
        if (spec.speed() != null) {
            builder.speed(spec.speed());
        }
        return builder.build();
    }

    @Override
    public void ended(LivingEntity entity, MobDefinition definition) {
        // Si otro plugin cancela la muerte (un totem, por ejemplo), el mob sigue vivo: no
        // hay animacion de despedida ni se cierra el modelo.
        if (!entity.isDead() && entity.getHealth() > 0.0D) {
            return;
        }
        AnimationSpec death = definition.model() == null
                ? null
                : definition.model().animation(ModelAnimation.DEATH);
        if (death == null || death.name().isBlank()) {
            this.release(entity);
            return;
        }
        this.animate(entity, definition, ModelAnimation.DEATH);
    }

    @Override
    public void release(LivingEntity entity) {
        java.util.UUID id = entity.getUniqueId();
        this.walking.remove(id);
        this.restoreName(entity);
        EntityTracker tracker = this.trackers.remove(id);
        if (tracker == null) {
            return;
        }
        try {
            tracker.close();
        } catch (Throwable throwable) {
            // Cerrar dos veces no es un problema.
        }
    }

    @Override
    public void releaseAll() {
        for (EntityTracker tracker : this.trackers.values()) {
            try {
                tracker.close();
            } catch (Throwable throwable) {
                // Al desactivar, que un tracker falle no impide soltar el resto.
            }
        }
        this.trackers.clear();
        this.walking.clear();
        this.hiddenNames.clear();
    }

    /**
     * Devuelve el nombre vanilla que se habia ocultado para la placa del modelo.
     *
     * <p>El mob sigue siendo custom despues de soltar el modelo (se guarda en el huevo, se
     * vuelve a aparecer, se le cambia el estilo): su nombre tiene que volver a verse.
     */
    private void restoreName(LivingEntity entity) {
        if (this.hiddenNames.remove(entity.getUniqueId()) && entity.isValid()) {
            entity.setCustomNameVisible(true);
        }
    }

    @Override
    public void reload() {
        this.releaseAll();
        this.warnedModels.clear();
        int count = this.modelKeys().size();
        this.plugin.getLogger().info("BetterModel recargado: " + count + " modelo(s) cargado(s).");
    }

    private Set<String> modelKeys() {
        try {
            return new HashSet<>(BetterModel.modelKeys());
        } catch (Throwable throwable) {
            return Set.of();
        }
    }

    /** {@code true} si el nombre existe entre los modelos cargados por BetterModel. */
    public boolean hasModelName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            return BetterModel.modelKeys().stream()
                    .anyMatch(key -> key.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)));
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Que huesos con tag trae un modelo cargado (para el comando de validacion).
     *
     * <p>Se lee del modelo, no de una entidad: asi se puede revisar antes de invocar el mob.
     * {@code null} si el modelo no esta cargado.
     *
     * @param hitBox si algun hueso lleva tag {@code b}/{@code ob}
     * @param seat   si algun hueso lleva tag {@code p}
     * @param name   si algun hueso lleva tag de placa ({@code mtag}, {@code tag}, {@code ptag})
     */
    public BoneReport inspectBones(String model) {
        ModelRenderer renderer = BetterModel.modelOrNull(model);
        if (renderer == null) {
            return null;
        }
        boolean hitBox = false;
        boolean seat = false;
        boolean name = false;
        java.util.List<String> bones = new java.util.ArrayList<>();
        try {
            // flatten() recorre todo el arbol y da cada hueso con su BoneName ya parseado.
            for (kr.toxicity.model.api.data.renderer.RendererGroup group : renderer.flatten().toList()) {
                var bone = group.name();
                bones.add(bone.rawName());
                hitBox |= bone.tagged(BoneTags.HITBOX);
                seat |= bone.tagged(BoneTags.SEAT);
                name |= bone.tagged(BoneTags.MOB_TAG, BoneTags.TAG, BoneTags.PLAYER_TAG);
            }
        } catch (Throwable throwable) {
            return null;
        }
        return new BoneReport(hitBox, seat, name, java.util.List.copyOf(bones));
    }

    /**
     * Lo que un modelo aporta a la entidad: caja de colision, asiento y placa.
     *
     * @param hitBox si trae hueso con tag {@code b}/{@code ob}
     * @param seat   si trae hueso con tag {@code p}
     * @param name   si trae hueso de placa ({@code mtag}, {@code tag}, {@code ptag})
     * @param bones  nombres de los huesos leidos
     */
    public record BoneReport(boolean hitBox, boolean seat, boolean name, java.util.List<String> bones) {
    }

    /** Nombres de todos los modelos cargados por BetterModel. */
    public java.util.List<String> modelNames() {        java.util.List<String> names = new java.util.ArrayList<>(this.modelKeys());
        java.util.Collections.sort(names);
        return names;
    }

    private void warnMissingModel(MobDefinition definition, ModelSpec spec) {
        if (this.warnedModels.add(definition.id() + ":" + spec.name())) {
            this.plugin.getLogger().warning("El modelo '" + spec.name() + "' de '" + definition.id()
                    + "' no existe en BetterModel (modelos cargados: " + this.modelKeys().size()
                    + "). El mob se vera sin modelo.");
        }
    }

    private void warnOnce(String key, String message) {
        if (this.warned.add(key)) {
            this.plugin.getLogger().warning(message);
        }
    }
}
