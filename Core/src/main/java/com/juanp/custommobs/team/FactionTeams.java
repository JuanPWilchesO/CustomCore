package com.juanp.custommobs.team;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Las reglas que mantienen Teams y las facciones de acuerdo.
 *
 * <p>La idea: un team es de una faccion —la de su jefe—, y dentro no deberia haber nadie de otra.
 * Cuando alguien deja de cuadrar, sale del team.
 *
 * <p>No toca la API de Teams a proposito: solo mira la faccion de cada uno y manda el comando de
 * salida que diga el config. Asi esta clase se puede usar aunque Teams no este instalado.
 */
public final class FactionTeams {

    private final CustomMobsPlugin plugin;

    public FactionTeams(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Saca del team a un jugador si su faccion no cuadra con la del jefe.
     *
     * <p>La API de Teams es de solo lectura —no tiene forma de sacar a nadie—, asi que la salida
     * se hace con su comando: {@code teams.kick-command} del config, donde {@code {jugador}} se
     * sustituye por el nombre.
     *
     * @return {@code true} si se le saco
     */
    public boolean kickIfInconsistent(UUID playerId, UUID ownerId) {
        if (ownerId == null || ownerId.equals(playerId)) {
            return false; // es el jefe: el team es suyo
        }
        String mine = this.plugin.playerFactions().of(playerId).orElse(null);
        String owner = this.plugin.playerFactions().of(ownerId).orElse(null);
        boolean inconsistent;
        if (owner == null) {
            inconsistent = mine != null && this.plugin.getConfig()
                    .getBoolean("teams.kick-if-owner-factionless", false);
        } else {
            inconsistent = !owner.equals(mine);
        }
        if (!inconsistent) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        String name = player.getName() != null ? player.getName() : "";
        String command = this.plugin.getConfig()
                .getString("teams.kick-command", "team kick {jugador}")
                .replace("{jugador}", name);
        this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), command);
        return true;
    }
}
