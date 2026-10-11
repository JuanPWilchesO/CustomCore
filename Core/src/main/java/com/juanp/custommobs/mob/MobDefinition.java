package com.juanp.custommobs.mob;

import com.juanp.custommobs.item.EquipItem;
import com.juanp.custommobs.combat.PlayerTargetMode;
import com.juanp.custommobs.item.RecipeSpec;
import com.juanp.custommobs.drop.DropSpec;
import com.juanp.custommobs.skill.SkillSpec;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definicion inmutable de un mob custom, cargada desde un archivo en {@code mobs/}.
 *
 * <p>Hay dos categorias ({@link MobCategory}): los mobs de jugador ({@code player}) se
 * invocan por crafteo y defienden a su dueno; los de servidor ({@code server}) no tienen
 * dueno, se agrupan en facciones y se relacionan con los jugadores segun su {@link Attitude}.
 *
 * @param id               identificador unico (campo {@code id} o nombre del archivo)
 * @param displayName      nombre visible, con codigos de color ya traducidos
 * @param entityType       tipo base de la entidad
 * @param eggMaterial      huevo usado como ingrediente; {@code null} si el mob no se craftea
 * @param recipe           receta shapeless: ingredientes extra y cantidad producida
 * @param glow             si el mob brilla
 * @param attributes       atributos por clave amistosa (health, damage, speed, armor, ...)
 * @param equipment        equipamiento por slot
 * @param playerTargetMode politica contra jugadores propia de este mob; {@code null} = usar la global
 * @param leash            limite de separacion respecto al dueno (solo mobs de jugador)
 * @param lore             lore del huevo custom
 * @param category         categoria del mob (jugador o servidor)
 * @param faction          faccion (solo mobs de servidor); {@code null} si no tiene
 * @param attitude         actitud frente a los jugadores (solo mobs de servidor)
 * @param disguise         apariencia opcional via LibsDisguises
 * @param sounds           sonidos opcionales del mob
 * @param burnsInDaylight  si se incendia con el sol (por defecto, solo los mobs de jugador)
 * @param usesAi           si conserva la IA de vanilla; {@code false} lo deja quieto en su puesto
 * @param respawnSeconds   segundos hasta reaparecer en su bloque; {@code 0} = una sola vida
 *                         (solo mobs de servidor: convierte el punto en un spawner)
 * @param skills           habilidades activas del mob (dano, curacion, habla, pociones)
 * @param drops            tabla de drops escrita a mano en el yml (los capturados con el
 *                         comando viven aparte, en {@code drops/<id>.yml})
 * @param clearVanillaDrops si {@code true}, el mob no suelta nada de lo vanilla: solo su tabla
 * @param chunkRadius      radio en chunks que se mantiene cargado alrededor de su bloque;
 *                         {@code null} = usar el valor global del config
 * @param mount            como preparar la montura (solo {@code category: mount});
 *                         {@code null} en cualquier otro caso
 */
public record MobDefinition(
        String id,
        String displayName,
        EntityType entityType,
        Material eggMaterial,
        RecipeSpec recipe,
        boolean glow,
        Map<String, Double> attributes,
        Map<EquipmentSlot, EquipItem> equipment,
        PlayerTargetMode playerTargetMode,
        LeashSettings leash,
        List<String> lore,
        MobCategory category,
        String faction,
        Attitude attitude,
        DisguiseSpec disguise,
        MobSounds sounds,
        boolean burnsInDaylight,
        boolean usesAi,
        int respawnSeconds,
        List<SkillSpec> skills,
        List<DropSpec> drops,
        boolean clearVanillaDrops,
        Integer chunkRadius,
        MountSpec mount,
        NaturalSpawn spawn,
        boolean villageFriendly,
        Set<EntityDamageEvent.DamageCause> immune,
        boolean attacksMonsters,
        boolean transformable
) {

    /** Vida maxima configurada, o 20 si no se especifico. */
    public double health() {
        return this.attributes.getOrDefault("health", 20.0D);
    }

    /** {@code true} si el mob tiene al menos una skill configurada. */
    public boolean hasSkills() {
        return !this.skills.isEmpty();
    }

    /** {@code true} si es un mob de servidor (PvE, sin dueno ni leash). */
    public boolean server() {
        return this.category == MobCategory.SERVER;
    }

    /** {@code true} si es una montura. */
    public boolean mountable() {
        return this.category == MobCategory.MOUNT;
    }

    /** {@code true} si este mob aparece solo por el mundo. */
    public boolean natural() {
        return this.spawn != null && this.spawn.natural();
    }

    /** Efectos constantes de la montura; vacio si no es montura o no tiene. */
    public List<MountEffect> mountEffects() {
        return this.mount == null ? List.of() : this.mount.effects();
    }

    /**
     * {@code true} si el mob ignora ese tipo de dano.
     *
     * <p>Sirve para cualquier categoria: montura, servidor, o mob de jugador anclado a el o
     * a un bloque.
     */
    public boolean ignores(EntityDamageEvent.DamageCause cause) {
        return this.immune.contains(cause);
    }
}
