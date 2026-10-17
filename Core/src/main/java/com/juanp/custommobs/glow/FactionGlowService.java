package com.juanp.custommobs.glow;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pinta el brillo de cada jugador segun su faccion, PARA CADA QUIEN.
 *
 * <p>El color de un brillo sale del equipo de scoreboard al que pertenece la entidad, y los
 * equipos son globales: pintarlo de una vez no sirve, porque la relacion es distinta para cada
 * espectador —el mismo jugador es aliado mio y enemigo tuyo—. Por eso se le manda a cada jugador
 * un equipo falso con PacketEvents: el cliente lo pinta tal como se le dice, y el scoreboard de
 * verdad no se toca; asi no se rompe nada de Teams ni de ningun otro plugin.
 *
 * <p>Modos (faction-glow.mode del config): off, global o player.
 */
public final class FactionGlowService extends BukkitRunnable {

    private static final String ENEMY_TEAM = "cmglow_enemy";
    private static final String ALLY_TEAM = "cmglow_ally";
    private static final String NEUTRAL_TEAM = "cmglow_neutral";
    /** Equipo sin color y con la placa oculta: el que va agachado. */
    private static final String HIDDEN_TEAM = "cmglow_hidden";
    /** El bit de "resplandece" dentro de los flags compartidos de la entidad. */
    private static final byte GLOW_FLAG = 0x40;

    private final CustomMobsPlugin plugin;
    private final MobService service;

    /** Ultimo equipo que se le mando a cada espectador, por jugador visto. */
    private final Map<UUID, Map<UUID, String>> teams = new ConcurrentHashMap<>();
    /** Equipos ya creados en el cliente de cada espectador. */
    private final Map<UUID, Set<String>> created = new ConcurrentHashMap<>();
    /** Quienes se han apartado del brillo por su cuenta. */
    private final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();
    /** Cuando se le mando el brillo a cada pareja (espectador -&gt; visto), en millis. */
    private final Map<UUID, Map<UUID, Long>> glowAt = new ConcurrentHashMap<>();

    public FactionGlowService(CustomMobsPlugin plugin, MobService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /** Modo configurado: off, global o player. */
    public String mode() {
        String raw = this.plugin.getConfig().getString("faction-glow.mode", "off");
        return raw == null ? "off" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Alterna el brillo de un jugador. Devuelve como queda: true si queda activado. */
    public boolean toggle(Player player) {
        if (this.optedOut.remove(player.getUniqueId())) {
            return true;
        }
        this.optedOut.add(player.getUniqueId());
        return false;
    }

    /** true si ese jugador ve el brillo de facciones. */
    public boolean enabledFor(Player player) {
        return switch (this.mode()) {
            case "global" -> true;
            case "player" -> !this.optedOut.contains(player.getUniqueId());
            default -> false;
        };
    }

    @Override
    public void run() {
        String current = this.mode();
        if (!"global".equals(current) && !"player".equals(current)) {
            return;
        }
        double radius = this.plugin.getConfig().getDouble("faction-glow.radius", 48.0D);
        NamedTextColor enemy = this.color("faction-glow.enemy", NamedTextColor.RED);
        NamedTextColor ally = this.color("faction-glow.ally", NamedTextColor.GREEN);
        NamedTextColor neutral = this.color("faction-glow.neutral", NamedTextColor.GRAY);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Map<UUID, String> seen = this.teams.computeIfAbsent(viewer.getUniqueId(),
                    id -> new ConcurrentHashMap<>());
            boolean wants = this.enabledFor(viewer)
                    && this.service.factionGlowActive(viewer.getWorld());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }
                if (!wants || !this.near(viewer, target, radius)
                        || !this.service.factionGlowActive(target.getWorld())) {
                    this.forget(viewer, target, seen);
                    continue;
                }
                // Agachado, ni placa ni brillo: se le mete en un equipo que oculta el nombre.
                boolean sneaking = target.isSneaking();
                this.paint(viewer, target,
                        sneaking ? HIDDEN_TEAM
                                : this.colorFor(viewer, this.service.factionOf(target)),
                        seen, !sneaking);
            }
            // Los mobs NO se pintan por paquetes. Su color ya sale sin tocar el protocolo: los
            // de servidor por el color de su faccion (factions.yml) y los de jugador por el
            // estilo de su dueno o su team. El pintado por espectador para mobs era el que
            // tiraba a los jugadores con "Error de protocolo de red" al matar o guardar un mob:
            // fuera entero. El de los jugadores se queda como estaba, que lleva tiempo estable.
        }
    }

    /** {@code true} si el espectador y la entidad estan lo bastante cerca para pintarla. */
    private boolean near(Player viewer, LivingEntity target, double radius) {
        return radius <= 0.0D
                || (viewer.getWorld().equals(target.getWorld())
                && viewer.getLocation().distanceSquared(target.getLocation()) <= radius * radius);
    }

    // ------------------------------------------------------------------ pintar

    /**
     * Pinta (o deja de pintar) a un jugador para un espectador.
     *
     * <p>El brillo se <b>refresca</b> cada {@code faction-glow.refresh-seconds}: el cliente
     * lo pierde en cuanto el servidor le manda los datos de verdad de la entidad —al moverse,
     * al reaparecer, al cambiar de mundo—, asi que mandarlo una sola vez dura poco. La placa
     * si aguanta, porque va en el equipo falso.
     *
     * @param glowing {@code false} para el que va agachado: ni placa ni brillo
     */
    private void paint(Player viewer, LivingEntity target, String team, Map<UUID, String> seen,
                       boolean glowing) {
        UUID targetId = target.getUniqueId();
        String previous = seen.get(targetId);
        String entry = entryOf(target);
        Map<UUID, Long> sent = this.glowAt.computeIfAbsent(viewer.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        boolean changed = !team.equals(previous);
        if (changed) {
            if (previous != null) {
                this.send(viewer, new WrapperPlayServerTeams(previous,
                        WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES, Optional.empty(), entry));
            }
            Set<String> known = this.created.computeIfAbsent(viewer.getUniqueId(),
                    id -> ConcurrentHashMap.newKeySet());
            if (known.add(team)) {
                boolean hidden = HIDDEN_TEAM.equals(team);
                WrapperPlayServerTeams.ScoreBoardTeamInfo info =
                        new WrapperPlayServerTeams.ScoreBoardTeamInfo(Component.text(team),
                                Component.empty(), Component.empty(),
                                hidden ? WrapperPlayServerTeams.NameTagVisibility.NEVER
                                        : WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                                WrapperPlayServerTeams.CollisionRule.ALWAYS, this.colorOf(team),
                                WrapperPlayServerTeams.OptionData.NONE);
                this.send(viewer, new WrapperPlayServerTeams(team,
                        WrapperPlayServerTeams.TeamMode.CREATE, info, entry));
            } else {
                this.send(viewer, new WrapperPlayServerTeams(team,
                        WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, Optional.empty(), entry));
            }
            seen.put(targetId, team);
            sent.remove(targetId);
        }
        // Se re-manda cada refresh-seconds: el cliente pierde el brillo cuando el servidor le
        // manda los datos de verdad de la entidad (al moverse, al reaparecer...).
        long now = System.currentTimeMillis();
        Long last = sent.get(targetId);
        if (last != null && now - last < this.refreshMillis()) {
            return;
        }
        // Se re-manda TAMBIEN la pertenencia al equipo, no solo el brillo: al cliente se le
        // pierde sola (al cambiar de mundo, al reconstruir entidades) y entonces la placa y el
        // brillo se quedaban blancos aunque el jugador estuviera quieto y al lado. Reañadir una
        // entrada que ya esta no hace nada, asi que repetirlo es inofensivo.
        this.send(viewer, new WrapperPlayServerTeams(team,
                WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, Optional.empty(), entry));
        NamedTextColor nameColor = HIDDEN_TEAM.equals(team) ? null : this.colorOf(team);
        this.send(viewer, this.metadata(target, glowing, nameColor));
        sent.put(targetId, now);
    }

    /** El nombre que usa el scoreboard para esta entidad (su uuid, salvo jugadores). */
    private static String entryOf(LivingEntity target) {
        return target instanceof Player player ? player.getName() : target.getScoreboardEntryName();
    }

    /** Deja de pintar a esa entidad para ese espectador, y le devuelve su aspecto real. */
    private void forget(Player viewer, LivingEntity target, Map<UUID, String> seen) {
        String team = seen.remove(target.getUniqueId());
        if (team == null) {
            return;
        }
        Map<UUID, Long> sent = this.glowAt.get(viewer.getUniqueId());
        if (sent != null) {
            sent.remove(target.getUniqueId());
        }
        this.send(viewer, new WrapperPlayServerTeams(team,
                WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES, Optional.empty(),
                entryOf(target)));
        // Solo se le mandan datos si la entidad sigue viva: mandar metadatos de una entidad ya
        // retirada es otro paquete que el cliente no necesita (y nos costo un Network Protocol).
        if (target.isValid()) {
            this.send(viewer, this.metadata(target, target.isGlowing(), null));
        }
    }

    /**
     * Los datos de la entidad: los flags (con el brillo puesto o quitado) y, en los mobs, el
     * nombre en el color de la faccion.
     *
     * <p>El nombre va aqui y <b>no</b> solo en el equipo porque un nombre que ya trae color
     * propio —el del yml, o el que eligio el dueno con {@code /cm color}— no lo tapa el color
     * del equipo. Mandandolo en el color de la faccion, la faccion gana de verdad.
     *
     * @param nameColor color de la placa; {@code null} deja el nombre real de la entidad
     */
    private WrapperPlayServerEntityMetadata metadata(LivingEntity target, boolean glowing,
                                                     NamedTextColor nameColor) {
        byte flags = 0;
        if (target.getFireTicks() > 0) {
            flags |= 0x01;
        }
        if (target.isSneaking()) {
            flags |= 0x02;
        }
        if (target instanceof Player player && player.isSprinting()) {
            flags |= 0x08;
        }
        if (target.isInvisible()) {
            flags |= 0x20;
        }
        if (glowing) {
            flags |= GLOW_FLAG;
        }
        // Solo los flags. El nombre NO se manda aqui: ese campo de metadatos es el que dependia
        // de la version del servidor y el que tiraba a los jugadores con "Error de protocolo de
        // red" al morir el mob. El color del nombre lo pone el EQUIPO, que es un paquete estable.
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, flags));
        return new WrapperPlayServerEntityMetadata(target.getEntityId(), data);
    }

    private void send(Player viewer, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    // ------------------------------------------------------------------ relaciones

    /** El equipo que le toca a ese jugador, visto por ese espectador. */
    /** El equipo que le toca a una faccion, visto por ese espectador. */
    private String colorFor(Player viewer, String theirs) {
        String mine = this.service.factionOf(viewer);
        if (mine == null || theirs == null) {
            return NEUTRAL_TEAM;
        }
        if (this.service.sameSide(mine, theirs)) {
            return ALLY_TEAM;
        }
        return this.service.plugin().factions().isEnemy(mine, theirs) ? ENEMY_TEAM : NEUTRAL_TEAM;
    }

    /** Cada cuanto se re-manda el brillo, en milisegundos ({@code 0} = en cada pasada). */
    private long refreshMillis() {
        return Math.max(0L, this.plugin.getConfig().getLong("faction-glow.refresh-seconds", 5L))
                * 1000L;
    }

    private NamedTextColor colorOf(String team) {
        if (ENEMY_TEAM.equals(team)) {
            return this.color("faction-glow.enemy", NamedTextColor.RED);
        }
        if (ALLY_TEAM.equals(team)) {
            return this.color("faction-glow.ally", NamedTextColor.GREEN);
        }
        if (HIDDEN_TEAM.equals(team)) {
            return NamedTextColor.WHITE;
        }
        return this.color("faction-glow.neutral", NamedTextColor.GRAY);
    }

    private NamedTextColor color(String path, NamedTextColor fallback) {
        String raw = this.plugin.getConfig().getString(path, fallback.toString());
        NamedTextColor color = raw == null ? null
                : NamedTextColor.NAMES.value(raw.trim().toLowerCase(Locale.ROOT));
        return color == null ? fallback : color;
    }
}
