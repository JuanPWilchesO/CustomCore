package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.MobDefinition;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bone.BoneTags;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.bukkit.BetterModelBukkit;
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

    /** Margen que se suma a la duracion de la muerte antes de cerrar el tracker. */
    private static final long DEATH_MARGIN_TICKS = 10L;

    /**
     * Margen antes de devolver el mob a su estado tras un golpe o un dano.
     *
     * <p>Sin el, una animacion de 20 ticks y un retorno a los 20 ticks exactos se cortaban en el
     * ultimo fotograma y se veia a medias.
     */
    private static final long REACTIVE_MARGIN_TICKS = 3L;

    /** Ticks antes de reintentar completar un modelo cuyos huesos aun no estaban cargados. */
    private static final long COMPLETE_RETRY_TICKS = 20L;

    /** Intentos de "completar" antes de rendirse y dejar el aviso. */
    private static final int COMPLETE_ATTEMPTS = 10;

    private final Plugin plugin;
    private final Map<java.util.UUID, EntityTracker> trackers = new ConcurrentHashMap<>();
    /**
     * Estado de animacion de cada mob.
     *
     * <p>Es la UNICA fuente de verdad para alternar idle/walk. Antes habia un {@code Set} de
     * "andando" y un {@code idlePlayed} aparte, y se contradecian: ademas el {@code idle}
     * inicial nunca se lanzaba (se saltaba la guarda de transicion), asi que el mob no tenia
     * estado y {@code walk} nunca entraba.
     */
    private final Map<java.util.UUID, ModelState> states = new ConcurrentHashMap<>();
    /**
     * Mobs con una animacion puntual en curso (golpe, dano, muerte).
     *
     * <p>Mientras esten aqui, la tarea de estado no los toca: si los tocara, pondria reposo o
     * marcha encima y cortaria el golpe.
     */
    private final Set<java.util.UUID> busy = ConcurrentHashMap.newKeySet();
    /**
     * Tick de servidor del ultimo evento reactivo de cada mob.
     *
     * <p>La tarea de estado corre cada 5 ticks y el golpe llega por evento: sin esto, en el
     * mismo tick en que el mob se para a atacar, la tarea pide {@code idle} y le gana la partida
     * al {@code attack} que llega justo despues.
     */
    private final Map<java.util.UUID, Long> lastReactive = new ConcurrentHashMap<>();
    /** Tarea de repeticion de cada estado, para cancelarla antes de programar otra. */
    private final Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> stateLoops =
            new ConcurrentHashMap<>();
    /** Mobs cuyo nombre vanilla se oculto para que mande la placa del modelo. */
    private final Set<java.util.UUID> hiddenNames = ConcurrentHashMap.newKeySet();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedModels = ConcurrentHashMap.newKeySet();

    private final boolean requireHitBox;

    public BetterModelLink(Plugin plugin, boolean requireHitBox) {
        this.plugin = plugin;
        this.requireHitBox = requireHitBox;
        this.hookReloadEnd();
        int keys = this.modelKeys().size();
        if (keys == 0) {
            // CustomCore arranca ANTES que BetterModel: en este punto sus modelos aun no estan
            // cargados. No es un error; se avisa para que no parezca que no hay modelos.
            this.plugin.getLogger().info("BetterModel enlazado; sus modelos aun se estan"
                    + " cargando. Los mobs que aparezcan mientras tanto se completan solos.");
        } else {
            this.plugin.getLogger().info("BetterModel enlazado: " + keys + " modelo(s) cargado(s).");
        }
    }

    /**
     * Se engancha al final de la carga/recarga de BetterModel.
     *
     * <p>Hace falta porque CustomCore habilita ANTES que BetterModel (orden alfabetico de
     * plugins): cuando aparece el primer mob, {@code tracker.bones()} viene vacio y no se puede
     * saber si el modelo trae hitbox ni placa. Al terminar la carga se revisan los mobs vivos.
     */
    private void hookReloadEnd() {
        try {
            BetterModelBukkit.platform().addReloadEndHandler(result -> {
                int count = this.modelKeys().size();
                this.plugin.getLogger().info("BetterModel termino de cargar: " + count
                        + " modelo(s). Se revisan los mobs con modelo.");
                this.completePending();
            });
        } catch (Throwable throwable) {
            // Sin el enganche queda el reintento periodico de la tarea de estado.
            this.plugin.getLogger().warning("No se pudo enganchar al final de la carga de"
                    + " BetterModel (" + throwable.getClass().getSimpleName()
                    + "); los modelos se completan por reintento.");
        }
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
        if (!bones.ready()) {
            // Huesos vacios = BetterModel aun cargando. No se decide nada: si se decidiera
            // ahora, la placa y la hitbox quedarian mal para siempre. Se reintenta desde la
            // tarea de estado y al terminar la carga.
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
                    () -> this.completeLater(entity, definition, spec), COMPLETE_RETRY_TICKS);
            return;
        }
    }

    /**
     * Aplica lo que decide el modelo cuando sus huesos ya se conocen: ocultar el cuerpo y
     * quien pinta el nombre. Es la ultima parte de {@link #attach}, separada para poder
     * reintentarla cuando BetterModel termina de cargar.
     */
    private void finish(LivingEntity entity, MobDefinition definition, ModelSpec spec,
                        EntityTracker tracker, BoneInfo bones) {
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
        // El reposo se pone solo la PRIMERA vez: si se repitiera en cada refresh, cortaria la
        // animacion que el mob tuviera (un golpe, por ejemplo).
        if (this.states.putIfAbsent(entity.getUniqueId(), ModelState.IDLE) == null) {
            this.startStateLoop(entity.getUniqueId(), definition, tracker, ModelState.IDLE);
            this.animate(entity, definition, ModelAnimation.SPAWN);
        }
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
        // Por defecto: manda el modelo si trae su propia placa ('mtag'/'ptag'). Un hueso con
        // 'tag' no cuenta: ese justo lo que hace es dejar el nombre en manos del vanilla.
        boolean hide = showName != null ? !showName : bones.nametag();
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
        boolean nametag = false;
        int count = 0;
        if (tracker == null || tracker.isClosed()) {
            return new BoneInfo(hitBox, seat, nametag, 0);
        }
        try {
            for (RenderedBone bone : tracker.bones()) {
                count++;
                hitBox |= bone.name().tagged(BoneTags.HITBOX);
                seat |= bone.name().tagged(BoneTags.SEAT);
                // OJO: 'tag' NO es lo mismo que 'mtag'. BetterModel crea su placa en el hueso
                // con 'mtag' (o 'ptag'); un hueso con 'tag' lo que hace es IMPEDIRLA, y la
                // placa la pinta entonces el nombre vanilla de la entidad.
                nametag |= bone.name().tagged(BoneTags.MOB_TAG, BoneTags.PLAYER_TAG);
            }
        } catch (Throwable throwable) {
            // Sin poder leer los huesos se asume lo conservador: no ocultar el cuerpo.
        }
        return new BoneInfo(hitBox, seat, nametag, count);
    }

    /** Estado de animacion de un mob: el que alterna reposo y andar. */
    private enum ModelState {
        IDLE,
        WALK
    }

    /**
     * Lo que aporta el modelo a la entidad, leido de sus huesos.
     *
     * @param boneCount cuantos huesos se leyeron; {@code 0} = el modelo aun no esta cargado
     */
    private record BoneInfo(boolean hitBox, boolean seat, boolean nametag, int boneCount) {

        /** {@code true} si ya se pueden sacar conclusiones (hay huesos que mirar). */
        boolean ready() {
            return this.boneCount > 0;
        }
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
            // tracker.hitbox(predicado) CREA el hitbox del hueso si no existia. Es la diferencia
            // clave: el predicado por defecto de BetterModel crea hitboxes solo en huesos SIN
            // tag, asi que un 'p_asiento' nunca aparece en registry.hitBoxes() y buscar ahi no
            // encontraba nada (la montura se quedaba en el asiento vanilla del caballo).
            HitBox seat = tracker.hitbox(null, bone -> bone.name().tagged(BoneTags.SEAT));
            if (seat == null) {
                this.warnOnce("seat:" + definition.id(),
                        "La montura '" + definition.id() + "' usa 'model.seat' pero el modelo '"
                                + definition.model().name() + "' no tiene ningun hueso con el tag"
                                + " 'seat'. Renombra el hueso del asiento a 'p_algo' (dos palabras,"
                                + " con el prefijo 'p_').");
                return;
            }
            PlatformEntity passenger = BukkitAdapter.adapt((org.bukkit.entity.Entity) player);
            // El controlador ya lo pone BetterModel al cargar el modelo (lee el tag 'p_' y usa
            // 'default-mount-controller' de su config), asi que NO se toca aqui.
            // Con jinete la montura necesita su IA: sin ella no obedece al jugador.
            if (entity instanceof org.bukkit.entity.AbstractHorse horse) {
                horse.setAI(true);
            }
            seat.mount(passenger);
            this.trace("asiento de " + definition.id() + " en el hueso '"
                    + seat.positionSource().name().rawName() + "'"
                    + " controlador=" + seat.mountController());
        } catch (Throwable throwable) {
            this.warnOnce("seat:" + definition.id() + ":" + throwable.getMessage(),
                    "No se pudo subir al jugador al asiento de '" + definition.id() + "': "
                            + throwable.getMessage());
        }
    }

    @Override
    public void unseat(LivingEntity entity, MobDefinition definition, org.bukkit.entity.Player player) {
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        try {
            HitBox seat = tracker.hitbox(null, bone -> bone.name().tagged(BoneTags.SEAT));
            if (seat == null) {
                return;
            }
            // Se desmonta al jugador del asiento. El controlador no se toca: es del modelo.
            seat.dismount(BukkitAdapter.adapt((org.bukkit.entity.Entity) player));
            // Sin jinete vuelve a quedarse quieta (la IA se apaga en MountEffectTask).
            this.trace("desmontado del asiento de " + definition.id());
        } catch (Throwable throwable) {
            // Si ya no estaba montado, no hay nada que hacer.
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
            // Si el modelo estaba a medias (BetterModel aun cargando al invocar el mob), se
            // termina ahora, que ya hay huesos que mirar.
            BoneInfo bones = this.readBones(tracker);
            if (!bones.ready()) {
                this.completeLater(entity, definition, spec);
                return;
            }
            // Se vuelven a aplicar cuerpo y placa: tras una recarga del catalogo la definicion
            // puede haber cambiado, y el reenvio de mundo reafirma lo mismo.
            this.finish(entity, definition, spec, tracker, bones);
            tracker.forceUpdate(true);
        } catch (Throwable throwable) {
            // Un tracker cerrado por el propio BetterModel no es un error del plugin.
        }
    }

    /**
     * Traza de animaciones, solo con {@code debug: true} en el config.
     *
     * <p>Existe porque el orden entre el evento de golpe y la tarea de estado no se puede
     * deducir mirando el modelo: hay que ver quien pide que y cuando.
     */
    private void trace(String message) {
        if (this.plugin instanceof com.juanp.custommobs.CustomMobsPlugin owner
                && owner.config() != null && owner.config().debug()) {
            this.plugin.getLogger().info("[modelo] " + message);
        }
    }

    /** {@code true} si el mob esta en una animacion puntual y no debe tocarse. */
    public boolean isBusy(LivingEntity entity) {
        java.util.UUID id = entity.getUniqueId();
        if (!this.busy.contains(id)) {
            return false;
        }
        // Red de seguridad: si el tracker desaparecio (BetterModel recargo, la entidad se
        // invalido), nadie va a limpiar la marca y el mob se quedaria pegado en el golpe.
        // OJO: con un cadaver la marca SI se respeta, que es lo que mantiene la animacion de
        // muerte hasta el final.
        EntityTracker tracker = this.trackers.get(id);
        if (tracker == null || tracker.isClosed()) {
            this.busy.remove(id);
            this.cancelStateLoop(id);
            return false;
        }
        return true;
    }

    /**
     * {@code true} si el mob acaba de recibir un evento reactivo (golpe o dano).
     *
     * <p>La tarea de estado lo consulta para no pisar el {@code attack} con un {@code idle}
     * pedido en el mismo tick.
     */
    public boolean reactedRecently(LivingEntity entity, long ticks) {
        Long at = this.lastReactive.get(entity.getUniqueId());
        if (at == null) {
            return false;
        }
        return this.currentTick() - at <= ticks;
    }

    /** Tick de servidor actual (Paper lo expone en el Server; es lo que se usa). */
    private long currentTick() {
        return this.plugin.getServer().getCurrentTick();
    }

    @Override
    public boolean hasModel(LivingEntity entity) {
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        return tracker != null && !tracker.isClosed();
    }

    @Override
    public void animate(LivingEntity entity, MobDefinition definition, ModelAnimation event) {
        AnimationSpec spec = definition.model() == null ? null : definition.model().animation(event);
        // Sin animacion para ese evento no se hace NADA y no se avisa: es opcional. Un modelo
        // con solo 'attack' es perfectamente valido, y 'hurt' no suele existir.
        if (spec == null || spec.name().isBlank()) {
            return;
        }
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        // La clave en el yml no basta: el modelo tiene que traer esa animacion. Si no, el
        // evento se ignora entero (no se corta el golpe para dejar la pantalla vacia).
        if (!this.exists(tracker, spec)) {
            String model = definition.model() == null ? "?" : definition.model().name();
            this.warnOnce("missing:" + definition.id() + ":" + spec.name(),
                    "El modelo '" + model + "' de '" + definition.id() + "' no tiene la animacion '"
                            + spec.name() + "' (" + event.key() + "); el evento se ignora.");
            return;
        }
        try {
            if (event == ModelAnimation.WALK || event == ModelAnimation.IDLE) {
                this.playState(entity, definition, tracker,
                        event == ModelAnimation.WALK ? ModelState.WALK : ModelState.IDLE);
                return;
            }
            this.playReactive(entity, definition, tracker, spec, event);
        } catch (Throwable throwable) {
            this.warnOnce("anim:" + definition.id() + ":" + event.key(),
                    "No se pudo reproducir la animacion '" + spec.name() + "' (" + event.key()
                            + ") en '" + definition.id() + "'.");
        }
    }

    /**
     * Reintenta aplicar el modelo cuando sus huesos ya estan cargados.
     *
     * <p>Se llama al aparecer un mob mientras BetterModel aun carga. Si al reintentar sigue sin
     * huesos, se vuelve a programar; al agotar los intentos se avisa y se deja el mob sin
     * tocar, que es lo mas seguro.
     */
    private void completeLater(LivingEntity entity, MobDefinition definition, ModelSpec spec) {
        this.completeLater(entity, definition, spec, 0);
    }

    private void completeLater(LivingEntity entity, MobDefinition definition, ModelSpec spec,
                               int attempt) {
        if (!entity.isValid()) {
            return;
        }
        EntityTracker tracker = this.trackers.get(entity.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        BoneInfo bones = this.readBones(tracker);
        if (bones.ready()) {
            this.finish(entity, definition, spec, tracker, bones);
            return;
        }
        if (attempt >= COMPLETE_ATTEMPTS) {
            this.warnOnce("pending:" + definition.id(),
                    "El modelo '" + spec.name() + "' de '" + definition.id() + "' sigue sin"
                            + " huesos: BetterModel no lo ha cargado. El mob se queda sin modelo.");
            return;
        }
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin,
                () -> this.completeLater(entity, definition, spec, attempt + 1),
                COMPLETE_RETRY_TICKS);
    }

    /**
     * Termina los modelos que se aplicaron a medias por estar BetterModel cargando.
     *
     * <p>No se reaplica todo: solo se completan los que quedaron pendientes. Reaplicar los
     * demas recrearia trackers sanos sin necesidad.
     */
    private void completePending() {
        if (!(this.plugin instanceof com.juanp.custommobs.CustomMobsPlugin owner)
                || owner.mobs() == null) {
            return;
        }
        for (var customMob : owner.mobs().active()) {
            if (!(customMob.entity() instanceof LivingEntity living) || !living.isValid()) {
                continue;
            }
            EntityTracker tracker = this.trackers.get(living.getUniqueId());
            if (tracker == null || tracker.isClosed()) {
                continue;
            }
            if (this.readBones(tracker).ready() && this.states.containsKey(living.getUniqueId())) {
                // Ya estaba completo: nada que hacer.
                continue;
            }
            ModelSpec spec = customMob.definition().model();
            if (spec == null || !spec.active()) {
                continue;
            }
            this.completeLater(living, customMob.definition(), spec);
        }
    }

    /**
     * Cambia entre reposo y andar. Solo actua si el estado CAMBIA: repetirlo en cada ciclo de la
     * tarea reiniciaria la animacion sin parar.
     */
    private boolean play(EntityTracker tracker, AnimationSpec spec, ModelAnimation event) {
        if (tracker == null || tracker.isClosed()) {
            return false;
        }
        int priority = spec.priorityOr(event == null ? 0 : event.defaultPriority());
        return tracker.animate(spec.name(), modifier(spec, priority));
    }

    private void playState(LivingEntity entity, MobDefinition definition, EntityTracker tracker,
                           ModelState state) {
        this.playState(entity.getUniqueId(), definition, tracker, state);
    }

    private void playState(java.util.UUID id, MobDefinition definition, EntityTracker tracker,
                           ModelState state) {
        if (tracker == null || tracker.isClosed()) {
            return;
        }
        if (this.states.get(id) == state) {
            return;
        }
        this.trace("estado " + state + " en " + id + " (busy=" + this.busy.contains(id) + ")");
        this.states.put(id, state);
        // Las reactivas se paran SIEMPRE: si no, la de prioridad alta sigue mandando aunque se
        // pida walk/idle, y el mob se queda repitiendo el golpe.
        this.stopReactive(tracker, definition);
        ModelState other = state == ModelState.WALK ? ModelState.IDLE : ModelState.WALK;
        this.stopState(tracker, definition, other);
        this.startStateLoop(id, definition, tracker, state);
    }

    /**
     * Lanza la animacion del estado y la mantiene viva mientras el estado no cambie.
     *
     * <p>Hace falta porque casi todos los modelos exportan las animaciones como {@code once}:
     * sin repetirla al terminar, el mob se queda congelado al andar. La duracion sale del propio
     * .bbmodel; si no se puede leer, se reintenta a los 20 ticks.
     */
    private void startStateLoop(java.util.UUID id, MobDefinition definition, EntityTracker tracker,
                                ModelState state) {
        ModelAnimation event = state == ModelState.WALK ? ModelAnimation.WALK : ModelAnimation.IDLE;
        AnimationSpec spec = definition.model() == null ? null : definition.model().animation(event);
        if (spec == null || spec.name().isBlank()) {
            return;
        }
        this.play(tracker, spec, event);
        long period = this.ticksOf(spec);
        // Un solo bucle por mob: se cancela el anterior antes de programar el siguiente. Antes se
        // acumulaba un task por cada cambio de estado.
        this.cancelStateLoop(id);
        this.stateLoops.put(id, this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            // Solo se repite si el mob sigue en ese estado y nadie lanzo un golpe encima.
            if (this.states.get(id) != state || this.busy.contains(id)
                    || tracker.isClosed() || !tracker.isScheduled()) {
                return;
            }
            this.play(tracker, spec, event);
            this.startStateLoop(id, definition, tracker, state);
        }, period));
    }

    /** Cancela el bucle de repeticion de ese mob, si lo tenia. */
    private void cancelStateLoop(java.util.UUID id) {
        var task = this.stateLoops.remove(id);
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable throwable) {
                // Ya estaba cancelada o el plugin se esta apagando.
            }
        }
    }

    /**
     * Para todas las animaciones reactivas configuradas (golpe, dano, aparicion, muerte).
     *
     * <p>Es imprescindible: una animacion {@code once} de BetterModel NO se retira al terminar,
     * se queda puesta mandando por prioridad. Sin pararla, la de andar o el reposo nunca ganan y
     * el mob se queda repitiendo el ultimo golpe para siempre.
     */
    private void stopReactive(EntityTracker tracker, MobDefinition definition) {
        if (definition.model() == null || tracker == null || tracker.isClosed()) {
            return;
        }
        for (ModelAnimation event : ModelAnimation.values()) {
            if (event == ModelAnimation.WALK || event == ModelAnimation.IDLE) {
                continue;
            }
            AnimationSpec spec = definition.model().animation(event);
            if (spec != null && !spec.name().isBlank()) {
                try {
                    tracker.stopAnimation(spec.name());
                } catch (Throwable throwable) {
                    // Parar algo que no estaba no es un problema.
                }
            }
        }
    }

    /** Para la animacion del estado indicado, si el mob la declara. */
    private void stopState(EntityTracker tracker, MobDefinition definition, ModelState state) {
        ModelAnimation event = state == ModelState.WALK ? ModelAnimation.WALK : ModelAnimation.IDLE;
        AnimationSpec spec = definition.model() == null ? null : definition.model().animation(event);
        if (spec != null && !spec.name().isBlank()) {
            tracker.stopAnimation(spec.name());
        }
    }

    /** Anima el estado en reposo tras dejar de andar. */
    @Override
    public void stopWalking(LivingEntity entity, MobDefinition definition) {
        ModelState current = this.states.get(entity.getUniqueId());
        if (current == ModelState.WALK) {
            this.playState(entity, definition, this.trackers.get(entity.getUniqueId()),
                    ModelState.IDLE);
        }
    }

    /**
     * Animacion puntual: golpe, dano, aparicion o muerte.
     *
     * <p>Se para lo que estuviera en curso y se lanza con la prioridad del evento. Pasada su
     * duracion REAL (la del .bbmodel) el mob vuelve a su estado, salvo en la muerte, que es
     * terminal.
     */
    private void playReactive(LivingEntity entity, MobDefinition definition, EntityTracker tracker,
                              AnimationSpec spec, ModelAnimation event) {
        java.util.UUID id = entity.getUniqueId();
        this.lastReactive.put(id, this.currentTick());
        this.trace("reactiva " + event.key() + " (" + spec.name() + ") en " + id);

        // Mientras dure, la tarea de estado no toca a este mob: si lo hiciera, pondria reposo o
        // marcha encima y cortaria el golpe.
        this.busy.add(id);
        this.cancelStateLoop(id);
        this.stopState(tracker, definition, ModelState.WALK);
        this.stopState(tracker, definition, ModelState.IDLE);
        // Antes de una reactiva nueva se paran las anteriores.
        //
        // El dano gana al golpe: si al mob le pegan mientras golpea, se ve el 'hurt'. La muerte
        // para todo lo anterior, que si no tapa a 'death'. Y el golpe corta un dano que estuviera
        // sonando, porque es el evento mas reciente.
        if (event == ModelAnimation.HURT || event == ModelAnimation.ATTACK
                || event.terminal()) {
            this.stopReactive(tracker, definition);
        }
        this.play(tracker, spec, event);
        // SIEMPRE se programa el final. Sin esto, un camino que salga antes deja 'busy' puesto y
        // el mob se queda para siempre en la animacion del golpe.
        this.rescheduleReactiveEnd(entity, definition, tracker, spec, event);
    }

    /**
     * Programa el final de la animacion reactiva.
     *
     * <p>Es el UNICO sitio que limpia {@code busy}. Para la muerte, ademas cierra el tracker: el
     * mob no vuelve a ningun estado.
     */
    private void rescheduleReactiveEnd(LivingEntity entity, MobDefinition definition,
                                       EntityTracker tracker, AnimationSpec spec,
                                       ModelAnimation event) {
        java.util.UUID id = entity.getUniqueId();
        long delay = this.ticksOf(spec) + (event.terminal() ? DEATH_MARGIN_TICKS
                : REACTIVE_MARGIN_TICKS);
        this.cancelStateLoop(id);
        this.stateLoops.put(id, this.plugin.getServer().getScheduler().runTaskLater(
                this.plugin, () -> {
                    if (event.terminal()) {
                        // Cadaver: se cierra el tracker y ya esta.
                        this.closeTracker(id, tracker);
                        return;
                    }
                    this.busy.remove(id);
                    // Se retira la reactiva explicitamente: BetterModel no la quita sola.
                    this.stopReactive(tracker, definition);
                    ModelState state = this.states.get(id);
                    this.trace("fin de " + event.key() + ", se reanuda " + state + " en " + id);
                    if (state == null || tracker.isClosed()) {
                        return;
                    }
                    // Se reanuda el estado donde se quedo el mob, con la animacion correcta.
                    this.states.remove(id);
                    this.playState(id, definition, tracker, state);
                }, delay));
    }

    /**
     * Duracion real de la animacion en ticks, leida del modelo.
     *
     * <p>Antes era un valor fijo de 15 ticks: las animaciones de 1 s se cortaban a la mitad.
     */
    /**
     * {@code true} si el modelo TRAE esa animacion con ese nombre.
     *
     * <p>Un yml puede declarar 'hurt: algo' y el modelo no tener 'algo'. En ese caso hay que
     * tratarlo como si no hubiera animacion: no cortar el golpe para luego no mostrar nada.
     */
    private boolean exists(EntityTracker tracker, AnimationSpec spec) {
        if (tracker == null || tracker.isClosed()) {
            return false;
        }
        try {
            return tracker.renderer().animation(spec.name()).isPresent();
        } catch (Throwable throwable) {
            // Sin poder comprobarlo se asume que si, que es lo conservador para el que la tiene.
            return true;
        }
    }

    private long ticksOf(AnimationSpec spec) {
        try {
            EntityTracker any = this.trackers.values().stream()
                    .filter(t -> t != null && !t.isClosed()).findFirst().orElse(null);
            if (any != null) {
                var animation = any.renderer().animation(spec.name());
                if (animation.isPresent()) {
                    float seconds = animation.get().length();
                    if (seconds > 0.0F) {
                        return Math.max(2L, Math.round(seconds * 20.0F));
                    }
                }
            }
        } catch (Throwable throwable) {
            // Sin duracion se cae al valor por defecto.
        }
        return 20L;
    }

    /**
     * Cierra el tracker de un mob muerto tras su animacion de muerte.
     *
     * <p>Con la duracion real del .bbmodel mas un margen: antes eran 40 ticks fijos y una muerte
     * de 1 s se quedaba con el cadaver quieto y una de 3 s se cortaba.
     */
    /** Cierra el tracker de un mob muerto: sin el, el modelo se queda flotando. */
    private void closeTracker(java.util.UUID id, EntityTracker tracker) {
        this.trace("cierre del tracker en " + id);
        this.busy.remove(id);
        this.lastReactive.remove(id);
        this.cancelStateLoop(id);
        this.states.remove(id);
        this.trackers.remove(id);
        try {
            tracker.close();
        } catch (Throwable throwable) {
            // Ya estaba cerrado.
        }
    }

    private static AnimationModifier modifier(AnimationSpec spec, int priority) {
        AnimationModifier.Builder builder = AnimationModifier.builder()
                .start(spec.start())
                .end(spec.end())
                .priority(priority)
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
        this.states.remove(id);
        this.busy.remove(id);
        this.lastReactive.remove(id);
        this.cancelStateLoop(id);
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
        for (java.util.UUID id : new java.util.HashSet<>(this.stateLoops.keySet())) {
            this.cancelStateLoop(id);
        }
        this.trackers.clear();
        this.states.clear();
        this.busy.clear();
        this.lastReactive.clear();
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
        // releaseAll limpia tambien los estados, asi que la reaplicacion vuelve a poner el
        // reposo y reevalua hitbox y placa con el yml nuevo.
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
        boolean nametag = false;
        boolean tagProbe = false;
        java.util.List<String> bones = new java.util.ArrayList<>();
        try {
            // flatten() recorre todo el arbol y da cada hueso con su BoneName ya parseado.
            for (kr.toxicity.model.api.data.renderer.RendererGroup group : renderer.flatten().toList()) {
                var bone = group.name();
                bones.add(bone.rawName());
                hitBox |= bone.tagged(BoneTags.HITBOX);
                seat |= bone.tagged(BoneTags.SEAT);
                nametag |= bone.tagged(BoneTags.MOB_TAG, BoneTags.PLAYER_TAG);
                tagProbe |= bone.tagged(BoneTags.TAG);
            }
        } catch (Throwable throwable) {
            return null;
        }
        return new BoneReport(hitBox, seat, nametag, tagProbe, java.util.List.copyOf(bones));
    }

    /**
     * Lo que un modelo aporta a la entidad: caja de colision, asiento y placa.
     *
     * @param hitBox si trae hueso con tag {@code b}/{@code ob}
     * @param seat   si trae hueso con tag {@code p}
     * @param nametag  si trae hueso de placa propia ({@code mtag}/{@code ptag})
     * @param tagProbe si trae hueso con tag {@code tag}, que IMPIDE la placa del modelo
     * @param bones    nombres de los huesos leidos
     */
    public record BoneReport(boolean hitBox, boolean seat, boolean nametag, boolean tagProbe,
                             java.util.List<String> bones) {
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
