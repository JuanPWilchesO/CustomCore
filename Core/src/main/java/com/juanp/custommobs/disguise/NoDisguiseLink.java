package com.juanp.custommobs.disguise;

import com.juanp.custommobs.mob.MobDefinition;
import org.bukkit.entity.LivingEntity;

/** Enlace nulo: el plugin funciona sin LibsDisguises. */
public final class NoDisguiseLink implements DisguiseLink {

    @Override
    public String name() {
        return "ninguno";
    }

    @Override
    public void applyNext(MobDefinition definition) {
        // Sin LibsDisguises no hay nada que encolar.
    }

    @Override
    public void apply(LivingEntity entity, MobDefinition definition) {
        // Sin LibsDisguises no hay nada que aplicar.
    }

    @Override
    public void refresh(LivingEntity entity, MobDefinition definition) {
        // Sin LibsDisguises no hay nada que reenviar.
    }

    @Override
    public boolean isDisguised(LivingEntity entity) {
        return false;
    }
}
