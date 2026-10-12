package com.juanp.custommobs.style;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Aplica el estilo elegido —color del nombre y del brillo— a los player mobs.
 *
 * <p>Dos decisiones que conviene tener presentes:
 *
 * <ul>
 *   <li>El color del nombre <b>tine el nombre entero</b> y sobreescribe el que escribio el
 *       admin en el yml. Es lo pedido.</li>
 *   <li>El color del brillo lo determina un <b>equipo de scoreboard</b>, no la entidad: por
 *       eso se crean equipos {@code cm_<color>} y se meten ahi los mobs. Se limpia la entrada
 *       de esos equipos antes de aplicar de nuevo, para que un cambio de color no deje al
 *       mob pintado en el anterior.</li>
 * </ul>
 */
public final class StyleService {

    /** Prefijo de los equipos de scoreboard propios, para no pisar los de otros plugins. */
    private static final String TEAM_PREFIX = "cm_";

    private final MobService service;
    private final StyleRegistry registry;

    public StyleService(MobService service, StyleRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    public StyleRegistry registry() {
        return this.registry;
    }

    public void apply(CustomMob customMob) {
        this.apply(customMob.entity(), customMob.definition(), customMob.ownerId(), customMob.teamId());
    }

    /** Aplica nombre y brillo de una vez. */
    public void apply(LivingEntity entity, MobDefinition definition, UUID ownerId, UUID teamId) {
        this.applyName(entity, definition, ownerId, teamId);
        this.applyGlow(entity, definition, ownerId, teamId);
    }

    /**
     * Tine el nombre: los mobs de jugador segun lo que eligio su dueno (o su team); los de
     * servidor, segun el color que {@code factions.yml} le declare a su faccion.
     */
    public void applyName(LivingEntity entity, MobDefinition definition, UUID ownerId, UUID teamId) {
        MobStyle style = this.resolve(ownerId, teamId);
        String name = definition.displayName();
        if (style != null && style.name() != null) {
            name = style.name() + ChatColor.stripColor(name);
        } else if (this.factionNameColor(definition) != null) {
            // El color lo pone el EQUIPO de la faccion, asi que el nombre va sin color: si
            // llevara el suyo, taparia el del equipo (y en un disfraz de jugador, tambien).
            name = ChatColor.stripColor(name);
        }
        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

    /** Color del nombre que la faccion de un mob de servidor declare en {@code factions.yml}. */
    private ChatColor factionNameColor(MobDefinition definition) {
        if (!definition.server() || definition.faction() == null) {
            return null;
        }
        String raw = this.service.factions().nameColor(definition.faction()).orElse(null);
        return raw == null ? null : MobStyle.color(raw);
    }

    /** Color del brillo que la faccion de un mob de servidor declare en {@code factions.yml}. */
    private ChatColor factionGlowColor(MobDefinition definition) {
        if (!definition.server() || definition.faction() == null) {
            return null;
        }
        String raw = this.service.factions().glowColor(definition.faction()).orElse(null);
        return raw == null ? null : MobStyle.color(raw);
    }

    /**
     * Decide el brillo. Sin eleccion se hereda el {@code glow} del yml; con {@code nada} se
     * apaga; con un color se enciende en ese color.
     */
    public void applyGlow(LivingEntity entity, MobDefinition definition, UUID ownerId, UUID teamId) {
        MobStyle style = this.resolve(ownerId, teamId);
        this.clearGlow(entity);

        if (style == null || style.glowColor() == null) {
            // Mobs de servidor: si su faccion declara brillo, se enciende en ese color.
            ChatColor factionGlow = this.factionGlowColor(definition);
            if (factionGlow != null) {
                this.glowTeam(factionGlow).ifPresent(team -> team.addEntry(this.entryOf(entity)));
                entity.setGlowing(true);
                return;
            }
            ChatColor factionName = this.factionNameColor(definition);
            if (factionName != null) {
                // Solo el nombre: el equipo le da el color sin encender el brillo.
                this.glowTeam(factionName).ifPresent(team -> team.addEntry(this.entryOf(entity)));
            }
            entity.setGlowing(definition.glow());
            return;
        }
        if (style.glowOff()) {
            entity.setGlowing(false);
            return;
        }
        ChatColor color = style.glow();
        this.glowTeam(color).ifPresent(team -> team.addEntry(entity.getScoreboardEntryName()));
        entity.setGlowing(true);
    }

    /** Reaplica el estilo a todos los mobs vivos. Devuelve cuantos se tocaron. */
    public int refresh() {
        int count = 0;
        for (CustomMob customMob : this.service.active()) {
            if (customMob.entity().isValid()) {
                this.apply(customMob);
                count++;
            }
        }
        return count;
    }

    /**
     * Limpia lo que el plugin le puso al mob: el brillo y la placa.
     *
     * <p>Se llama en TODOS los caminos por los que el mob se va (muerte, guardado, retirada).
     * Ocultar el nombre aqui es lo que evita la <b>placa fantasma</b>: si el cliente se queda
     * con una copia de la entidad, al menos no tiene nombre que pintar.
     */
    public void clear(LivingEntity entity) {
        this.clearGlow(entity);
        entity.setCustomNameVisible(false);
    }

    private MobStyle resolve(UUID ownerId, UUID teamId) {
        if (ownerId == null) {
            return null;
        }
        // El team manda sobre el jugador: asi todos sus miembros se ven iguales.
        if (teamId != null) {
            MobStyle team = this.registry.teamStyle(teamId);
            if (team != null && !team.isEmpty()) {
                return team;
            }
        }
        return this.registry.playerStyle(ownerId);
    }

    /**
     * Nombre con el que el cliente conoce a la entidad en el scoreboard: para una entidad
     * normal es su uuid; para un mob disfrazado de jugador, el nombre del perfil del disfraz.
     */
    private String entryOf(LivingEntity entity) {
        String disguised = this.service.disguiseLink().scoreboardEntry(entity);
        return disguised != null ? disguised : entity.getScoreboardEntryName();
    }

    private void clearGlow(LivingEntity entity) {
        String entry = this.entryOf(entity);
        for (Team team : new ArrayList<>(this.scoreboard().getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.removeEntry(entry);
            }
        }
    }

    private java.util.Optional<Team> glowTeam(ChatColor color) {
        if (color == null) {
            return java.util.Optional.empty();
        }
        Scoreboard board = this.scoreboard();
        String name = TEAM_PREFIX + color.name().toLowerCase(java.util.Locale.ROOT);
        Team team = board.getTeam(name);
        if (team == null) {
            try {
                team = board.registerNewTeam(name);
            } catch (IllegalArgumentException ex) {
                team = board.getTeam(name);
            }
            if (team != null) {
                team.setColor(color);
            }
        }
        return java.util.Optional.ofNullable(team);
    }

    private Scoreboard scoreboard() {
        return this.service.plugin().getServer().getScoreboardManager().getMainScoreboard();
    }
}
