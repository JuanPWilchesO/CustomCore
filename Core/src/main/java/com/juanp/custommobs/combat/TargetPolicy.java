package com.juanp.custommobs.combat;

import com.juanp.custommobs.mob.Attitude;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Decide que puede atacar un mob custom.
 *
 * <p>Mobs de jugador ({@code player}):
 * <ul>
 *   <li>Nunca atacan a su dueno ni a si mismos.</li>
 *   <li>Atacan monstruos hostiles de vanilla ({@link Monster}).</li>
 *   <li>Atacan mobs custom cuyo team sea enemigo del suyo.</li>
 *   <li>Atacan jugadores solo si el team de su dueno los tiene por enemigos.</li>
 *   <li>Sin team, nunca atacan jugadores: la relacion es unicamente con el dueno.</li>
 * </ul>
 *
 * <p>Mobs de servidor ({@code server}):
 * <ul>
 *   <li>Atacan a los mobs de facciones enemigas; del mismo bando o aliadas, nunca.</li>
 *   <li>Segun su {@link Attitude}: hostil ataca jugadores, neutral solo devuelve el golpe,
 *       defensor nunca ataca jugadores y persigue a los monstruos que amenazan la zona.</li>
 * </ul>
 */
public final class TargetPolicy {

    private final MobService service;
    private final double radius;

    public TargetPolicy(MobService service, double radius) {
        this.service = service;
        this.radius = radius;
    }

    /** Objetivo valido mas cercano dentro del radio, o {@code null}. */
    public LivingEntity findTarget(Mob mob) {
        CustomMob soldier = this.service.find(mob.getUniqueId()).orElse(null);
        if (soldier == null) {
            return null;
        }
        // Si se paso del radio, no persigue: primero vuelve a su dueno.
        if (this.service.isBeyondLeash(soldier, mob)) {
            return null;
        }

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity candidate : mob.getNearbyEntities(this.radius, this.radius, this.radius)) {
            if (!(candidate instanceof LivingEntity living) || !this.isValidTarget(soldier, living)) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(mob.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    public boolean isValidTarget(Mob mob, LivingEntity candidate) {
        CustomMob soldier = this.service.find(mob.getUniqueId()).orElse(null);
        return soldier != null && this.isValidTarget(soldier, candidate);
    }

    public boolean isValidTarget(CustomMob soldier, LivingEntity candidate) {
        if (candidate == null || candidate.isDead() || !candidate.isValid()) {
            return false;
        }
        if (candidate.getUniqueId().equals(soldier.entity().getUniqueId())) {
            return false;
        }
        if (soldier.isOwner(candidate.getUniqueId())) {
            return false;
        }
        // Un mob amistoso con el pueblo no toca a la gente del pueblo.
        if (soldier.definition().villageFriendly() && VillageFolk.isFolk(candidate)) {
            return false;
        }
        return soldier.definition().server()
                ? this.isValidServerTarget(soldier, candidate)
                : this.isValidPlayerMobTarget(soldier, candidate);
    }

    // ------------------------------------------------------------------ mobs de jugador

    private boolean isValidPlayerMobTarget(CustomMob soldier, LivingEntity candidate) {
        Optional<CustomMob> otherCustom = this.service.find(candidate.getUniqueId());
        if (otherCustom.isPresent()) {
            return this.isHostileCustomMob(soldier, otherCustom.get());
        }

        if (candidate instanceof Monster) {
            return true;
        }

        if (candidate instanceof Player player) {
            return this.isHostilePlayer(soldier, player);
        }

        return false;
    }

    /** Como se llevan dos facciones. */
    private enum Relation { FRIEND, ENEMY, NEUTRAL }

    /**
     * La faccion de un mob: la suya, o —si es un mob de jugador— <b>la de su dueno</b>.
     *
     * <p>Es lo que hace que un mob de jugador cuente como una extension del jugador: para el
     * sistema de facciones vale la faccion de quien lo lleva.
     */
    private String factionOf(CustomMob mob) {
        String own = mob.definition().faction();
        if (own != null && !own.isBlank()) {
            return own;
        }
        return mob.ownerId() == null ? null
                : this.service.plugin().playerFactions().of(mob.ownerId()).orElse(null);
    }

    /** La faccion de un jugador, o {@code null} si no tiene ninguna. */
    private String factionOf(Player player) {
        return this.service.plugin().playerFactions().of(player.getUniqueId()).orElse(null);
    }

    /** La relacion entre dos facciones: la misma o aliadas son amigos; enemigas, enemigos. */
    private Relation relation(String first, String second) {
        if (first == null || second == null) {
            return Relation.NEUTRAL;
        }
        if (first.equals(second) || this.service.factions().isAlly(first, second)) {
            return Relation.FRIEND;
        }
        return this.service.factions().isEnemy(first, second) ? Relation.ENEMY : Relation.NEUTRAL;
    }

    /**
     * Decide si un jugador es objetivo, segun la politica efectiva del mob.
     * Companeros y aliados nunca son objetivo, en ningun modo.
     */
    private boolean isHostilePlayer(CustomMob soldier, Player player) {
        if (soldier.ownerId() != null && soldier.isOwner(player.getUniqueId())) {
            return false;
        }
        if (this.service.teamLink().isAlly(soldier.ownerId(), player.getUniqueId())) {
            return false;
        }

        // La faccion del dueno manda, igual que en los mobs de servidor: un jugador de faccion
        // enemiga es objetivo, y uno de la misma faccion o de una aliada es intocable. Un mob
        // de jugador es una extension de su dueno, y hereda sus amigos y sus enemigos.
        PlayerTargetMode mode = this.modeFor(soldier);
        if (mode == PlayerTargetMode.NEVER) {
            return false;
        }
        Relation faction = this.relation(this.factionOf(soldier), this.factionOf(player));
        if (faction == Relation.FRIEND) {
            return false;
        }
        if (faction == Relation.ENEMY) {
            return true;
        }

        return switch (mode) {
            case NEVER -> false;
            case ENEMIES -> soldier.teamId() != null
                    && this.service.teamLink().isEnemyTeam(soldier.teamId(), player.getUniqueId());
            case NON_ALLIES -> true;
            case DEFENSIVE -> this.service.aggro()
                    .isHostile(soldier.entity().getUniqueId(), player.getUniqueId());
        };
    }

    /** Politica efectiva: la del mob si la define, si no la global del servidor. */
    private PlayerTargetMode modeFor(CustomMob soldier) {
        PlayerTargetMode override = soldier.definition().playerTargetMode();
        return override != null ? override : this.service.config().playerTargetMode();
    }

    private boolean isHostileCustomMob(CustomMob soldier, CustomMob other) {
        if (soldier.isOwner(other.ownerId())) {
            return false;
        }
        UUID myTeam = soldier.teamId();
        if (myTeam != null && myTeam.equals(other.teamId())) {
            return false;
        }
        // Companeros y aliados siguen siendo intocables, aunque hayan agredido.
        if (other.ownerId() != null && this.service.teamLink().isAlly(soldier.ownerId(), other.ownerId())) {
            return false;
        }
        // Y las facciones, antes que la agresion: mismo bando o aliados nunca; enemigos, siempre.
        Relation faction = this.relation(this.factionOf(soldier), this.factionOf(other));
        if (faction == Relation.FRIEND) {
            return false;
        }
        if (faction == Relation.ENEMY) {
            return true;
        }
        // Venganza: si ese mob agredio a nuestro bando, pasa a ser objetivo.
        if (this.service.aggro().isHostile(soldier.entity().getUniqueId(), other.entity().getUniqueId())) {
            return true;
        }
        if (myTeam != null && other.ownerId() != null) {
            return this.service.teamLink().isEnemyTeam(myTeam, other.ownerId());
        }
        return false;
    }

    // ------------------------------------------------------------------ mobs de servidor

    private boolean isValidServerTarget(CustomMob soldier, LivingEntity candidate) {
        Optional<CustomMob> other = this.service.find(candidate.getUniqueId());
        if (other.isPresent()) {
            return this.isHostileFactionMob(soldier, other.get());
        }
        if (candidate instanceof Player player) {
            return this.isHostileServerPlayer(soldier, player);
        }
        // Un defensor persigue a los monstruos hostiles que amenazan la zona. Y con
        // 'attacks-monsters: true' tambien los ataca cualquier mob de servidor, sea cual sea
        // su actitud: un neutral que se defiende de un monstruo, o un hostil que ademas
        // limpia la zona.
        if (candidate instanceof Monster) {
            return this.attitudeOf(soldier) == Attitude.DEFENDER
                    || soldier.definition().attacksMonsters();
        }
        return false;
    }

    /**
     * Relacion entre dos mobs de servidor: misma faccion o aliadas son intocables;
     * las enemigas son objetivo; entre neutrales solo cuenta la agresion recibida.
     */
    private boolean isHostileFactionMob(CustomMob soldier, CustomMob other) {
        // Un mob de jugador cuenta por la faccion de su dueno: asi un mob de faccion enemiga va
        // a por el y por los suyos, que es lo que se pidio.
        String mine = this.factionOf(soldier);
        String theirs = this.factionOf(other);
        // Mismo bando o aliados: intocables, pase lo que pase, y se mira ANTES que la agresion.
        // Cuando iba despues, dos mobs del mismo bando que se habian marcado agresion se
        // quedaban atacandose sin poder hacerse dano —el dano si esta bloqueado—, trabados
        // para siempre. Es el fallo que se veia en el servidor: aliados peleandose de mentira.
        if (mine != null && theirs != null
                && (mine.equals(theirs) || this.service.factions().isAlly(mine, theirs))) {
            return false;
        }
        if (this.service.aggro().isHostile(soldier.entity().getUniqueId(), other.entity().getUniqueId())) {
            return true;
        }
        if (mine == null || theirs == null) {
            return false;
        }
        return this.service.factions().isEnemy(mine, theirs);
    }

    private boolean isHostileServerPlayer(CustomMob soldier, Player player) {
        // La faccion del jugador manda sobre la actitud: los suyos y los aliados son
        // intocables, los enemigos son objetivo. Si el jugador no tiene faccion, o su
        // faccion es neutral con la del mob, decide la actitud de siempre.
        Relation faction = this.relation(this.factionOf(soldier), this.factionOf(player));
        if (faction == Relation.FRIEND) {
            return false;
        }
        if (faction == Relation.ENEMY) {
            return true;
        }
        return switch (this.attitudeOf(soldier)) {
            case HOSTILE -> true;
            case NEUTRAL, DEFENDER -> this.service.aggro()
                    .isHostile(soldier.entity().getUniqueId(), player.getUniqueId());
        };
    }

    private Attitude attitudeOf(CustomMob soldier) {
        Attitude attitude = soldier.definition().attitude();
        return attitude != null ? attitude : Attitude.DEFAULT;
    }
}
