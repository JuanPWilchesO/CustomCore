package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.MobDefinition;
import org.bukkit.entity.LivingEntity;

/**
 * Aplica el modelo de BetterModel a un mob. Se elige un adaptador segun si el plugin esta
 * presente, para que el resto del core cargue aunque no lo este.
 */
public interface ModelLink {

    /** Nombre del enlace activo, para el log de arranque. */
    String name();

    /**
     * Aplica el modelo a una entidad recien creada.
     *
     * <p>{@code disguised} dice si la entidad ya lleva un disfraz de LibsDisguises: en ese
     * caso el modelo se ignora y el disfraz manda.
     */
    void apply(LivingEntity entity, MobDefinition definition, boolean disguised);

    /**
     * Vuelve a puntar el modelo de una entidad que ya lo tenia.
     *
     * <p>Hace falta tras un cambio de mundo: el cliente rehace las entidades al entrar en
     * el mundo nuevo y el modelo se queda por el camino.
     */
    void refresh(LivingEntity entity, MobDefinition definition);

    /** {@code true} si la entidad lleva un modelo de este plugin. */
    boolean hasModel(LivingEntity entity);

    /**
     * Reproduce la animacion de ese evento, si el mob la define.
     *
     * <p>Para {@link ModelAnimation#DEATH} el tracker se cierra tras un breve margen: la
     * animacion se ve mientras el cadaver sigue en el mundo.
     */
    void animate(LivingEntity entity, MobDefinition definition, ModelAnimation event);

    /**
     * La entidad desaparece del mundo. Si la definicion pide animacion de muerte, el modelo
     * se queda un momento para verla; si no, se suelta en el acto.
     */
    void ended(LivingEntity entity, MobDefinition definition);

    /** El mob ha dejado de moverse: vuelve a la animacion en reposo, si venia andando. */
    void stopWalking(LivingEntity entity, MobDefinition definition);

    /** Suelta el modelo de una entidad que murio o desaparecio. */
    void release(LivingEntity entity);

    /** Suelta todos los modelos: al desactivar el plugin. */
    void releaseAll();

    /**
     * {@code true} si el modelos ya pone el asiento de la montura (hueso con tag {@code seat}).
     *
     * <p>Cuando es asi, el mob vanilla se oculta y el jugador se sube al asiento a mano:
     * BetterModel no deja montar en el hueso de una entidad que ya lleva pasajero.
     */
    default boolean seatEnabled(LivingEntity entity, MobDefinition definition) {
        return false;
    }

    /** Sube al jugador al asiento del modelo. */
    default void seat(LivingEntity entity, MobDefinition definition, org.bukkit.entity.Player player) {
    }

    /** Cierra el tracker de todos los mobs vivos: recarga del catalogo o del propio modelo. */
    default void reload() {
    }
}
