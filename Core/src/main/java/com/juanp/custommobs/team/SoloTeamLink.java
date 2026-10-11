package com.juanp.custommobs.team;

import java.util.Optional;
import java.util.UUID;

/** Respaldo sin Teams: solo cuenta la identidad del jugador. */
public final class SoloTeamLink implements TeamLink {

    @Override
    public String name() {
        return "solo";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<UUID> teamOf(UUID player) {
        return Optional.empty();
    }

    @Override
    public boolean isAlly(UUID playerA, UUID playerB) {
        return playerA != null && playerA.equals(playerB);
    }

    @Override
    public boolean isEnemyTeam(UUID teamId, UUID player) {
        return false;
    }

    @Override
    public java.util.Set<UUID> members(UUID teamId) {
        return java.util.Set.of(); // sin Teams no hay teams que mirar
    }

    @Override
    public Optional<UUID> ownerOf(UUID player) {
        // Sin Teams no hay jefe: cada jugador decide lo suyo.
        return Optional.empty();
    }
}
