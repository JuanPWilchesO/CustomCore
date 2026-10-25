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
    /** Ultimo evento de estado por mob, para no relanzar la misma animacion en cada ciclo. */
    private final Map<java.util.UUID, Boolean> walking = new ConcurrentHashMap<>();
    /** Mobs que ya tienen la animacion de reposo puesta. */
    private final Set<java.util.UUID> idlePlayed = ConcurrentHashMap.newKeySet();
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

        boolean hitBox = !this.requireHitBox || this.ensureHitBox(tracker, target, spec);

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
                        "El modelo '" + spec.name() + "' no declara hitbox (tag 'hitbox' o tag"
                                + " 'seat'): el mob '" + definition.id() + "' conserva su cuerpo"
                                + " vanilla para poder recibir dano. Anade el tag al modelo o pon"
                                + " 'hide-body: false'.");
            }
        }

        this.idlePlayed.remove(entity.getUniqueId());
        this.animate(entity, definition, ModelAnimation.SPAWN);
        this.animate(entity, definition, ModelAnimation.IDLE);
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
     * Se asegura de que el modelo aporta una hitbox, que es lo que recibe el dano cuando el
     * cuerpo vanilla esta oculto.
     */
    private boolean ensureHitBox(EntityTracker tracker, PlatformEntity target, ModelSpec spec) {
        EntityTrackerRegistry registry = tracker.registry();
        if (registry != null && !registry.hitBoxes().isEmpty()) {
            return true;
        }
        if (this.hitBoxOf(tracker) != null) {
            return true;
        }
        // BonePredicate.Builder ya es un Predicate<RenderedBone>: createHitBox acepta eso.
        java.util.function.Predicate<RenderedBone> predicate = spec.seat()
                ? BonePredicate.tag(BoneTags.HITBOX, BoneTags.SEAT)
                : BonePredicate.tag(BoneTags.HITBOX);
        try {
            tracker.createHitBox(null, BonePredicate.from(predicate));
        } catch (Throwable throwable) {
            // Sin hitbox no se oculta el cuerpo; el modelo se ve igual.
        }
        if (registry != null && !registry.hitBoxes().isEmpty()) {
            return true;
        }
        return this.hitBoxOf(tracker) != null;
    }

    private HitBox hitBoxOf(EntityTracker tracker) {
        for (RenderedBone bone : tracker.bones()) {
            HitBox box = bone.getHitBox();
            if (box != null) {
                return box;
            }
        }
        return null;
    }

    @Override
    public boolean seatEnabled(LivingEntity entity, MobDefinition definition) {
        ModelSpec spec = definition.model();
        if (spec == null || !spec.active() || !spec.seat() || !definition.mountable()) {
            return false;
        }
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            return false;
        }
        try {
            EntityTrackerRegistry registry = tracker.registry();
            return registry != null && !registry.hitBoxes().isEmpty();
        } catch (Throwable throwable) {
            return false;
        }
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
                // 'walk' solo se relanza cuando el mob EMPIEZA a andar: si se relanzara en
                // cada ciclo, la animacion se cortaria a si misma y el mob pareceria congelado.
                if (this.walking.put(entity.getUniqueId(), Boolean.TRUE) == null) {
                    this.play(tracker, spec);
                }
                return;
            }
            if (event == ModelAnimation.IDLE) {
                if (this.walking.remove(entity.getUniqueId()) == null
                        && this.idlePlayed.contains(entity.getUniqueId())) {
                    // Ya estaba en reposo: no se repite.
                    return;
                }
                this.idlePlayed.add(entity.getUniqueId());
            }
            this.play(tracker, spec);
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
        if (this.walking.remove(entity.getUniqueId()) != null && this.hasModel(entity)) {
            this.animate(entity, definition, ModelAnimation.IDLE);
        }
    }

    private void play(EntityTracker tracker, AnimationSpec spec) {
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
        this.idlePlayed.remove(id);
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
        this.idlePlayed.clear();
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

    /** Nombres de todos los modelos cargados por BetterModel. */
    public java.util.List<String> modelNames() {
        java.util.List<String> names = new java.util.ArrayList<>(this.modelKeys());
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
