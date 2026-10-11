package com.juanp.custommobs.skill;

import java.util.List;
import java.util.Optional;

/**
 * Una cadena de interaccion: la conversacion que arma un mob, paso a paso.
 *
 * <p>Se recorre a base de clics en el chat. El estado no vive aqui: aqui solo esta el guion;
 * cada jugador lleva por donde va.
 */
public record ChainSpec(List<ChainStep> steps) {

    public static final ChainSpec EMPTY = new ChainSpec(List.of());

    public boolean isEmpty() {
        return this.steps.isEmpty();
    }

    /** El paso por donde empieza. */
    public Optional<ChainStep> first() {
        return this.steps.isEmpty() ? Optional.empty() : Optional.of(this.steps.get(0));
    }

    /** Un paso por su id, o vacio si no existe. */
    public Optional<ChainStep> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return this.steps.stream().filter(step -> step.id().equals(id)).findFirst();
    }
}
