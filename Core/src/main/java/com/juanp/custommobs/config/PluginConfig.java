package com.juanp.custommobs.config;

import com.juanp.custommobs.combat.PlayerTargetMode;
import com.juanp.custommobs.mob.Texts;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuracion tipada del plugin.
 *
 * @param debug               log extra
 * @param targetRadius        radio de busqueda de objetivos
 * @param taskIntervalTicks   intervalo del task de objetivos
 * @param playerTargetMode    politica por defecto contra jugadores
 * @param aggroDurationMillis cuanto dura la hostilidad ganada por agresion
 * @param aggroWatchRadius    radio en el que se vigilan agresiones al bando
 * @param aggroOnFriendlyMobs si reacciona a agresiones contra mobs del mismo bando
 * @param skillIntervalTicks  cada cuantos ticks se evaluan las skills
 * @param playerPermission    permiso para usar los huevos custom
 * @param adminPermission     permiso para los comandos de administracion
 * @param bypassPermission    permiso para saltarse las restricciones de objetos y pociones
 * @param defaultPlayerMobs   cupo de mobs anclados al dueno; {@code 0} = sin limite
 * @param defaultPointMobs    cupo de mobs anclados a un bloque; {@code 0} = sin limite
 * @param defaultMountMobs    cupo de monturas; {@code 0} = sin limite
 * @param groupLimits         cupo por grupo de LuckPerms, con las claves en minusculas
 * @param pointGroupLimits    cupo por grupo para los mobs anclados a un bloque
 * @param mountGroupLimits    cupo por grupo para las monturas
 * @param pointChunkRadius    radio en chunks que se mantiene cargado alrededor del bloque
 * @param bookMaterial        material del libro de inspeccion
 * @param bookName            nombre del libro, con color ya traducido
 * @param bookLore            lore del libro, con color ya traducido
 * @param motdEnabled         si el MOTD se manda al entrar
 * @param motd                lineas del MOTD, ya con los codigos de color traducidos
 * @param enabledWorlds       mundos donde funciona el plugin, en minusculas; vacio = todos
 * @param recallEnabled       si los mobs abandonados se retiran solos
 * @param recallRadius        radio de abandono; {@code 0} = derivado de la simulation-distance
 * @param recallMobSeconds    segundos antes de destruir el mob abandonado
 * @param recallChunkSeconds  segundos antes de liberar el chunk que se mantuvo cargado
 * @param spawnEnabled        si los mobs de servidor pueden aparecer solos por el mundo
 * @param spawnIntervalSeconds cada cuantos segundos se intenta
 * @param spawnAttempts       intentos de aparicion por jugador y ciclo
 * @param spawnGlobalCap      tope de mobs naturales vivos a la vez
 * @param itemFactionAllies   si un objeto de faccion vale tambien para sus aliados
 * @param itemIgnoreCancelledUse procesar el 'usar:' aunque otro plugin cancele el clic
 * @param teleportRespectProtections si las skills de teletransporte respetan protecciones
 * @param arrowClass          clase que puede cargar flechas en el arco (por defecto, cazador)
 */
public record PluginConfig(
        boolean debug,
        double targetRadius,
        long taskIntervalTicks,
        PlayerTargetMode playerTargetMode,
        long aggroDurationMillis,
        double aggroWatchRadius,
        boolean aggroOnFriendlyMobs,
        long skillIntervalTicks,
        String playerPermission,
        String adminPermission,
        String bypassPermission,
        int defaultPlayerMobs,
        int defaultPointMobs,
        int defaultMountMobs,
        Map<String, Integer> groupLimits,
        Map<String, Integer> pointGroupLimits,
        Map<String, Integer> mountGroupLimits,
        int pointChunkRadius,
        org.bukkit.Material bookMaterial,
        String bookName,
        List<String> bookLore,
        boolean motdEnabled,
        List<String> motd,
        List<String> enabledWorlds,
        boolean recallEnabled,
        double recallRadius,
        long recallMobSeconds,
        long recallChunkSeconds,
        boolean spawnEnabled,
        long spawnIntervalSeconds,
        int spawnAttempts,
        int spawnGlobalCap,
        boolean itemLockAnyWorld,
        boolean playerPvpAnyWorld,
        boolean factionGlowAnyWorld,
        boolean itemFactionAllies,
        boolean itemIgnoreCancelledUse,
        boolean teleportRespectProtections,
        String arrowClass,
        int ccImmunitySeconds,
        int ccShieldSeconds,
        String ccShieldMessage,
        String ccBreakMessage,
        String ccStunMessage,
        String ccRootMessage,
        String ccSilenceMessage,
        String ccDisarmMessage,
        String ccImmuneMessage,
        String ccEndMessage,
        String ccCleanseMessage
) {

    public static PluginConfig load(FileConfiguration cfg) {
        PlayerTargetMode mode = PlayerTargetMode.parse(cfg.getString("targeting.player-mode", "defensive"));
        if (mode == null) {
            mode = PlayerTargetMode.DEFENSIVE;
        }
        // La chunk tiene que sobrevivir al mob: si se pidiera soltar antes de destruirlo,
        // nadie podria tocarlo. Por eso el suelo de chunk-seconds es mob-seconds.
        long mobSeconds = Math.max(1L, cfg.getLong("recall.mob-seconds", 60L));
        long chunkSeconds = Math.max(mobSeconds, cfg.getLong("recall.chunk-seconds", 120L));
        String arrowClass = cfg.getString("items.arrows-class", "cazador");
        if (arrowClass == null || arrowClass.isBlank()) {
            arrowClass = "cazador";
        }
        arrowClass = arrowClass.trim().toLowerCase(java.util.Locale.ROOT);

        int ccImmunity = Math.max(0, cfg.getInt("cc.immunity-seconds", 1));
        int ccShield = Math.max(0, cfg.getInt("cc.shield-seconds", 8));
        String ccShieldMessage = cfg.getString("cc.shield-message",
                "&8Estas protegido contra controles.");
        String ccBreakMessage = cfg.getString("cc.break-message",
                "&8El golpe rompe el control.");
        String ccStun = cfg.getString("cc.stun-message", "&8Estas aturdido.");
        String ccRoot = cfg.getString("cc.root-message", "&8Estas atrapado.");
        String ccSilence = cfg.getString("cc.silence-message", "&8Estas silenciado.");
        String ccDisarm = cfg.getString("cc.disarm-message", "&8Estas desarmado.");
        String ccImmune = cfg.getString("cc.immune-message", "&8Eres inmune a ese control.");
        String ccEnd = cfg.getString("cc.end-message", "&8Te liberas del control.");
        String ccCleanse = cfg.getString("cc.cleanse-message",
                "&8Te liberas y ganas inmunidad.");

        return new PluginConfig(
                cfg.getBoolean("debug", false),
                Math.max(1.0D, cfg.getDouble("targeting.radius", 16.0D)),
                Math.max(1L, cfg.getLong("targeting.interval-ticks", 20L)),
                mode,
                Math.max(1L, cfg.getLong("targeting.aggro-duration-seconds", 120L)) * 1000L,
                Math.max(1.0D, cfg.getDouble("targeting.aggro-watch-radius", 24.0D)),
                cfg.getBoolean("targeting.aggro-on-friendly-mobs", true),
                Math.max(1L, cfg.getLong("skills.interval-ticks", 20L)),
                cfg.getString("permissions.player", "customcore.player"),
                cfg.getString("permissions.admin", "customcore.admin"),
                cfg.getString("permissions.bypass", "customcore.bypass"),
                // El mismo valor que trae la plantilla: un servidor con un config.yml
                // anterior no debe quedarse sin tope por no tener la clave.
                Math.max(0, cfg.getInt("limits.default-player-mobs", 3)),
                Math.max(0, cfg.getInt("limits.default-point-mobs", 1)),
                Math.max(0, cfg.getInt("limits.default-mount-mobs", 1)),
                readGroupLimits(cfg, "limits.groups"),
                readGroupLimits(cfg, "limits.point-groups"),
                readGroupLimits(cfg, "limits.mount-groups"),
                Math.max(0, cfg.getInt("point.chunk-radius", 4)),
                bookMaterial(cfg),
                Texts.color(cfg.getString("book.display-name", "&6Inventario de fuerzas")),
                colorList(cfg.getStringList("book.lore")),
                cfg.getBoolean("branding.motd-enabled", true),
                motdOf(cfg),
                lowerList(cfg.getStringList("worlds.enabled")),
                cfg.getBoolean("recall.enabled", true),
                Math.max(0.0D, cfg.getDouble("recall.radius", 0.0D)),
                mobSeconds,
                chunkSeconds,
                cfg.getBoolean("spawning.enabled", true),
                Math.max(1L, cfg.getLong("spawning.interval-seconds", 10L)),
                Math.max(1, cfg.getInt("spawning.attempts", 2)),
                Math.max(1, cfg.getInt("spawning.global-cap", 80)),
                cfg.getBoolean("worlds.anywhere.item-faction-lock", false),
                cfg.getBoolean("worlds.anywhere.player-friendly-fire", false),
                cfg.getBoolean("worlds.anywhere.faction-glow", false),
                // Por defecto, la faccion exacta: un aliado NO hereda el objeto.
                cfg.getBoolean("items.faction-allies", false),
                // Por defecto se procesa el uso aunque otro plugin cancele el clic.
                cfg.getBoolean("items.ignore-cancelled-use", true),
                // Por defecto el plugin no consulta protecciones al teletransportar.
                cfg.getBoolean("teleports.respect-protections", false),
                arrowClass,
                ccImmunity,
                ccShield,
                ccShieldMessage,
                ccBreakMessage,
                ccStun,
                ccRoot,
                ccSilence,
                ccDisarm,
                ccImmune,
                ccEnd,
                ccCleanse
        );
    }

    private static List<String> lowerList(List<String> raw) {
        List<String> values = new ArrayList<>(raw.size());
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                values.add(value.toLowerCase(java.util.Locale.ROOT).trim());
            }
        }
        return List.copyOf(values);
    }

    /**
     * MOTD efectivo: el del yml si lo define, y si no el que trae el plugin.
     *
     * <p>Hace falta un valor por defecto en el codigo porque {@code config.yml} NO se
     * reescribe en un servidor que ya lo tenia: sin esto, un servidor existente se quedaria
     * sin MOTD solo porque su archivo es anterior a esta seccion.
     */
    private static List<String> motdOf(FileConfiguration cfg) {
        if (cfg.isList("branding.motd")) {
            return colorList(cfg.getStringList("branding.motd"));
        }
        return DEFAULT_MOTD;
    }

    /** El mismo MOTD que trae la plantilla del config, para servidores con un yml anterior. */
    private static final List<String> DEFAULT_MOTD = colorList(List.of(
            "&0&m----------------------------------",
            " &cCustomMobs &0| &cAP2P Project",
            " &cBienvenido, &4{jugador}&c.",
            "&0&m----------------------------------"));

    private static List<String> colorList(List<String> raw) {
        List<String> lines = new ArrayList<>(raw.size());
        for (String line : raw) {
            lines.add(Texts.color(line));
        }
        return List.copyOf(lines);
    }

    /** Material del libro; si el yml trae uno invalido, se cae al libro escrito. */
    private static org.bukkit.Material bookMaterial(FileConfiguration cfg) {
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(cfg.getString("book.material", ""));
        return material == null || material.isAir() ? org.bukkit.Material.WRITTEN_BOOK : material;
    }

    /** Cupo por grupo. Las claves se guardan en minusculas para comparar sin sorpresas. */
    private static Map<String, Integer> readGroupLimits(FileConfiguration cfg, String path) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String group : section.getKeys(false)) {
            limits.put(group.toLowerCase(Locale.ROOT), Math.max(0, section.getInt(group)));
        }
        return Map.copyOf(limits);
    }
}
