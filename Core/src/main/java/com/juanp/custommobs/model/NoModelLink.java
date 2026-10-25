package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.MobDefinition;
import org.bukkit.entity.LivingEntity;

/** Enlace inerte: sin BetterModel los mobs se ven como siempre. */
public final class NoModelLink implements ModelLink {

    @Override
    public String name() {
        return "sin modelo";
    }

    @Override
    public void apply(LivingEntity entity, MobDefinition definition, boolean disguised) {
        // Nada que hacer: el mob conserva su apariencia vanilla.
    }

    @Override
    public void refresh(LivingEntity entity, MobDefinition definition) {
        // Nada que reenviar.
    }

    @Override
    public boolean hasModel(LivingEntity entity) {
        return false;
    }

    @Override
    public void animate(LivingEntity entity, MobDefinition definition, ModelAnimation event) {
        // Sin modelos no hay animaciones que reproducir.
    }

    @Override
    public void ended(LivingEntity entity, MobDefinition definition) {
        // Sin modelos no hay nada que conservar.
    }

    @Override
    public void stopWalking(LivingEntity entity, MobDefinition definition) {
        // Sin modelos no hay animaciones.
    }

    @Override
    public void release(LivingEntity entity) {
        // Nada que soltar.
    }

    @Override
    public void releaseAll() {
        // Nada que soltar.
    }
}
