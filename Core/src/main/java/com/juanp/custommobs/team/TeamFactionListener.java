package com.juanp.custommobs.team;

import com.hites.godxteam.api.event.TeamChangedEvent;
import com.hites.godxteam.api.event.TeamMemberJoinEvent;
import com.hites.godxteam.api.TeamView;
import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Impide entrar a un team de otra faccion.
 *
 * <p>Si un jugador con faccion intenta unirse a un team cuyo jefe es de <b>otra</b> faccion, la
 * entrada se cancela: si no, el team acabaria con gente de dos bandos dentro, y ni el fuego amigo
 * ni los colores de faccion tendrian sentido.
 *
 * <p>Esto cubre la mitad facil: <b>entrar</b> a un team. La otra mitad —que alguien cambie de
 * faccion despues de estar dentro— no se puede resolver desde aqui, porque la API de Teams no
 * deja sacar a nadie de un team; para eso esta el comando de {@code config.yml}.
 *
 * <p>La clase solo se carga si Teams esta instalado: por eso el plugin solo la registra cuando
 * lo esta. Sin Teams, estas clases ni se tocan.
 */
public final class TeamFactionListener implements Listener {

    private final CustomMobsPlugin plugin;

    public TeamFactionListener(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(TeamMemberJoinEvent event) {
        Player player = event.getPlayer();
        String mine = this.plugin.playerFactions().of(player.getUniqueId()).orElse(null);
        if (mine == null) {
            return; // sin faccion no hay nada que cuadrar
        }
        UUID ownerId = event.getTeam().getOwner();
        String ownerFaction = ownerId == null ? null
                : this.plugin.playerFactions().of(ownerId).orElse(null);
        if (ownerFaction == null) {
            if (!this.plugin.getConfig().getBoolean("teams.block-join-if-owner-factionless", false)) {
                return;
            }
        } else if (ownerFaction.equals(mine)) {
            return; // es de los suyos
        }
        event.setCancelled(true);
        player.sendMessage(Texts.color("&cEse team es de otra faccion; la tuya es '" + mine
                + "'. Sal de ella, o busca un team de los tuyos."));
    }

    /**
     * Cuando el team cambia —por ejemplo, porque cambia su jefe—, se repasa quien hay dentro: si
     * alguno dejo de cuadrar con la faccion del jefe, sale.
     */
    @EventHandler
    public void onChanged(TeamChangedEvent event) {
        TeamView team = event.getTeamView();
        UUID ownerId = team.owner();
        if (ownerId == null || team.members() == null) {
            return;
        }
        for (UUID member : team.members()) {
            this.plugin.factionTeams().kickIfInconsistent(member, ownerId);
        }
    }
}
