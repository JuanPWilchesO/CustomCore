package com.juanp.custommobs.team;

import com.hites.godxteam.api.TeamView;
import com.hites.godxteam.api.TeamsAPI;

import java.util.Optional;
import java.util.UUID;

/** Implementacion real sobre la API publica de Teams (com.hites.godxteam). */
public final class TeamsTeamLink implements TeamLink {

    private final TeamsAPI api;

    public TeamsTeamLink(TeamsAPI api) {
        this.api = api;
    }

    @Override
    public String name() {
        return "Teams";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<UUID> teamOf(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        return this.api.getTeam(player).map(TeamView::id);
    }

    @Override
    public boolean isAlly(UUID playerA, UUID playerB) {
        if (playerA == null || playerB == null) {
            return false;
        }
        if (playerA.equals(playerB)) {
            return true;
        }
        Optional<TeamView> teamA = this.api.getTeam(playerA);
        Optional<TeamView> teamB = this.api.getTeam(playerB);
        if (teamA.isEmpty() || teamB.isEmpty()) {
            return false;
        }
        if (teamA.get().id().equals(teamB.get().id())) {
            return true;
        }
        return this.api.areAllies(teamA.get().name(), teamB.get().name())
                || teamA.get().allies().contains(teamB.get().name())
                || teamB.get().allies().contains(teamA.get().name());
    }

    @Override
    public boolean isEnemyTeam(UUID teamId, UUID player) {
        if (teamId == null || player == null) {
            return false;
        }
        Optional<TeamView> mine = this.api.getTeamById(teamId);
        Optional<TeamView> other = this.api.getTeam(player);
        if (mine.isEmpty() || other.isEmpty()) {
            return false;
        }
        return mine.get().enemies().contains(other.get().name())
                || other.get().enemies().contains(mine.get().name());
    }

    @Override
    public java.util.Set<UUID> members(UUID teamId) {
        if (teamId == null) {
            return java.util.Set.of();
        }
        return this.api.getTeamById(teamId).map(TeamView::members).orElse(java.util.Set.of());
    }

    @Override
    public Optional<UUID> ownerOf(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        return this.api.getTeam(player).map(TeamView::owner);
    }
}
