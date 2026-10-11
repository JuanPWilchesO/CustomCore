package com.juanp.custommobs.team;

import com.juanp.custommobs.CustomMobsPlugin;

import java.util.UUID;

/**
 * Las reglas que mantienen Teams y las facciones de acuerdo: <b>no se entra en una faccion que
 * rompa el team</b>.
 *
 * <p>Un team es de la faccion de su jefe, y dentro no deberia haber nadie de otra. En vez de
 * dejar entrar y luego sacar —que es lo que haciamos antes—, se <b>rechaza la entrada</b>: asi
 * nunca se llega al estado incoherente, y no hace falta expulsar a nadie.
 *
 * <p>Mira los dos lados: si el jugador es <b>miembro</b>, manda la faccion del jefe; si es <b>el
 * jefe</b>, romperia a cualquiera de los suyos que sea de otra faccion.
 *
 * <p>No toca la API de Teams: solo mira facciones y pregunta por el team al enlace.
 */
public final class FactionTeams {

    private final CustomMobsPlugin plugin;

    public FactionTeams(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Por que esa faccion romperia el team del jugador, o {@code null} si no rompe nada.
     *
     * @return el motivo, ya redactado para ensenarselo al que lo intenta
     */
    public String conflictFor(UUID playerId, String faction) {
        if (faction == null || !this.plugin.teamLink().isAvailable()) {
            return null;
        }
        UUID teamId = this.plugin.teamLink().teamOf(playerId).orElse(null);
        UUID ownerId = this.plugin.teamLink().ownerOf(playerId).orElse(null);
        if (teamId == null || ownerId == null) {
            return null; // sin team no hay nada que romper
        }
        if (!ownerId.equals(playerId)) {
            String owner = this.plugin.playerFactions().of(ownerId).orElse(null);
            if (owner != null && !owner.equals(faction)) {
                return "esta en un team de la faccion '" + owner + "'";
            }
            return null;
        }
        // Es el jefe: solo rompe si alguno de los suyos es de otra faccion.
        for (UUID member : this.plugin.teamLink().members(teamId)) {
            if (member.equals(playerId)) {
                continue;
            }
            String theirs = this.plugin.playerFactions().of(member).orElse(null);
            if (theirs != null && !theirs.equals(faction)) {
                return "su team tiene miembros de la faccion '" + theirs + "'";
            }
        }
        return null;
    }
}
