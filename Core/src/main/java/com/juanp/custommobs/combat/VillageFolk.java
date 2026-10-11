package com.juanp.custommobs.combat;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wolf;

/**
 * Aldeanos (y comerciantes) y golems: la gente del pueblo.
 *
 * <p>Reune en un solo sitio la lista que deciden {@code village-friendly}. La usan la
 * politica de objetivos —para no apuntarlos— y el bando protegido —para salir en su
 * defensa.
 */
public final class VillageFolk {

    private VillageFolk() {
    }

    /** Gente del pueblo: no se ataca, y si le pegan, se sale en su defensa. */
    public static boolean isFolk(Entity entity) {
        return entity instanceof AbstractVillager || entity instanceof IronGolem
                || entity instanceof Snowman;
    }

    /**
     * A quien un mob pacifico deja en paz sin que cuente como "pueblo".
     *
     * <p>El lobo caza esqueletos. Sin esto, un centinela amistoso con forma de esqueleto
     * seria cena de la primera manada que pasara, y el mob —que no le hace dano a nadie del
     * pueblo— acabaria a golpes con los perros del pueblo. Se le deja en paz en los dos
     * sentidos, pero no se sale en su defensa: el lobo no es del pueblo.
     */
    public static boolean isLeftAlone(Entity entity) {
        return isFolk(entity) || entity instanceof Wolf;
    }
}
