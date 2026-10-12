package com.juanp.custommobs.disguise;

import com.juanp.custommobs.mob.MobDefinition;
import org.bukkit.entity.LivingEntity;

/** Aplica la apariencia de un mob. Se elige un adaptador segun los plugins presentes. */
public interface DisguiseLink {

    /** Nombre del enlace activo, para el log de arranque. */
    String name();

    /**
     * Encola el disfraz para la proxima entidad que se cree. Debe llamarse justo
     * antes de invocarla: aplicar el disfraz despues deja ver un parpadeo del mob base.
     */
    void applyNext(MobDefinition definition);

    /** Aplica el disfraz a una entidad ya existente (restaurada tras un reinicio). */
    void apply(LivingEntity entity, MobDefinition definition);

    /**
     * Vuelve a mandar el disfraz a una entidad que ya lo llevaba.
     *
     * <p>Hace falta tras un cambio de mundo: el cliente rehace las entidades al entrar en
     * el mundo nuevo y el disfraz se queda por el camino. {@link #apply} no sirve para
     * esto, porque se salta las entidades que ya estan marcadas como disfrazadas.
     */
    void refresh(LivingEntity entity, MobDefinition definition);

    /** {@code true} si la entidad ya lleva un disfraz aplicado. */
    boolean isDisguised(LivingEntity entity);

    /**
     * Nombre con el que el cliente conoce a esa entidad en el scoreboard.
     *
     * <p>Un mob disfrazado de jugador lo pinta el cliente <b>como un jugador</b>, y a los
     * jugadores los casa con su equipo por el <b>nombre del perfil</b>, no por el uuid. Sin
     * esto, ni el brillo ni el color del nombre de faccion se le pegan al disfraz.
     *
     * @return el nombre del perfil, o {@code null} si la entidad no se pinta como jugador
     */
    String scoreboardEntry(LivingEntity entity);
}
