package com.juanp.custommobs.mob;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.config.PluginConfig;
import com.juanp.custommobs.region.RegionGate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Hace aparecer mobs de servidor al azar por el mundo.
 *
 * <p>Es lo contrario de un spawner: aqui no hay punto fijo ni reaparicion, sino intentos
 * alrededor de los jugadores, como hace el propio Minecraft. Los mobs que salen son de una
 * sola vida y sin persistencia: el servidor se los lleva cuando no hay nadie cerca.
 *
 * <p><b>Solo suma.</b> No toca la aparicion vanilla ni los spawners del plugin: si algo no
 * cuadra en las condiciones, simplemente no aparece nada.
 */
public final class NaturalSpawnTask extends BukkitRunnable {

    /** Intentos por sitio antes de rendirse con ese jugador. */
    private static final int PLACE_TRIES = 12;

    private final CustomMobsPlugin plugin;
    private final MobService service;
    private final MobRegistry registry;
    private final RegionGate regions;
    private final Random random = new Random();
    /** Ultimo motivo de descarte, para no repetir la misma traza en cada ciclo. */
    private final java.util.Map<String, String> lastReason = new java.util.HashMap<>();
    /** Ultima foto del ciclo, para escribirla solo cuando cambia. */
    private String lastSnapshot;

    public NaturalSpawnTask(CustomMobsPlugin plugin, MobService service, MobRegistry registry,
                            RegionGate regions) {
        this.plugin = plugin;
        this.service = service;
        this.registry = registry;
        this.regions = regions;
    }

    @Override
    public void run() {
        PluginConfig config = this.service.config();
        if (this.service.config().debug()) {
            this.snapshot(config);
        }
        if (!config.spawnEnabled() || this.service.naturalCount() >= config.spawnGlobalCap()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int attempt = 0; attempt < config.spawnAttempts(); attempt++) {
                if (this.service.naturalCount() >= config.spawnGlobalCap()) {
                    return;
                }
                this.attempt(player);
            }
        }
    }

    /** Un intento: elegir mob, tirar la probabilidad, buscar sitio y soltar el grupo. */
    private void attempt(Player player) {
        List<MobDefinition> candidates = new ArrayList<>();
        for (MobDefinition definition : this.registry.all()) {
            if (this.eligible(definition, player)) {
                candidates.add(definition);
            }
        }
        if (candidates.isEmpty()) {
            this.reason(player, "-", "ningun mob natural cabe en este mundo/jugador");
            return;
        }
        MobDefinition definition = candidates.get(this.random.nextInt(candidates.size()));
        if (this.random.nextDouble() > definition.spawn().chance()) {
            // Lo normal y lo esperado: no es un fallo. Se resume en la traza del siguiente
            // intento que si pase la probabilidad, para no inundar el log.
            return;
        }
        String why = this.spotReason(player, definition.spawn());
        if (why != null) {
            this.reason(player, definition.id(), why);
            return;
        }
        NaturalSpawn spawn = definition.spawn();
        Location spot = this.findSpot(player, spawn);
        if (spot == null) {
            this.reason(player, definition.id(), "sin sitio valido (no deberia pasar)");
            return;
        }
        int group = spawn.groupMin() + this.random.nextInt(
                Math.max(1, spawn.groupMax() - spawn.groupMin() + 1));
        int spawned = 0;
        for (int i = 0; i < group; i++) {
            if (this.service.spawnNatural(definition, spot) != null) {
                spawned++;
            }
        }
        if (this.service.config().debug()) {
            this.service.plugin().getLogger().info("[spawn] " + spawned + "x '" + definition.id()
                    + "' cerca de " + player.getName() + " en " + spot.getBlockX() + " "
                    + spot.getBlockY() + " " + spot.getBlockZ()
                    + " (" + spot.getWorld().getName() + ")");
        }
    }

    /**
     * Por que no hay sitio para ese mob, mirando el <b>primer</b> punto candidato.
     *
     * <p>Repite el sorteo de {@link #findSpot} una vez y dice que condicion falla. Con
     * {@code debug: true} en el config, el log dice entonces si el problema es la region, la
     * altura y o la luz, que es lo que de verdad cuesta ver a ojo.
     *
     * @return {@code null} si el sitio es bueno (lo normal), o el motivo del descarte
     */
    private String spotReason(Player player, NaturalSpawn spawn) {
        if (!this.service.config().debug()) {
            return this.findSpot(player, spawn) == null ? "sin sitio valido" : null;
        }
        World world = player.getWorld();
        double distance = spawn.minDistance()
                + this.random.nextDouble() * Math.max(0.0D, spawn.maxDistance() - spawn.minDistance());
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        Location candidate = new Location(world, x + 0.5D, 0.0D, z + 0.5D);
        if (!this.regions.allows(candidate, spawn.allowedRegion(), spawn.denyRegions())) {
            return "fuera de la region (allowed='" + spawn.allowedRegion() + "' deny="
                    + spawn.denyRegions() + " en " + x + "," + z + " del mundo "
                    + world.getName() + ")";
        }
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (y < spawn.minY() || y > spawn.maxY()) {
            return "altura " + y + " fuera de [" + spawn.minY() + "," + spawn.maxY() + "]";
        }
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!ground.getType().isSolid()) {
            return "el suelo es " + ground.getType() + " (no solido)";
        }
        Block air = world.getBlockAt(x, y, z);
        if (!air.isPassable()) {
            return "el hueco es " + air.getType() + " (no pasable)";
        }
        if (!this.timeMatches(world, spawn.time())) {
            return "hora " + world.getTime() + " no cuadra con '" + spawn.time() + "'";
        }
        int light = air.getLightLevel();
        if (light < spawn.lightMin() || light > spawn.lightMax()) {
            return "luz " + light + " fuera de [" + spawn.lightMin() + "," + spawn.lightMax() + "]";
        }
        if (!spawn.biomes().isEmpty()
                && !spawn.biomes().contains(this.biomeOf(ground).toLowerCase(Locale.ROOT))) {
            return "bioma " + this.biomeOf(ground);
        }
        return null;
    }

    /**
     * Foto del estado antes de cada ciclo, con {@code debug: true}.
     *
     * <p>Dice lo que ninguna otra traza puede decir: si la tarea corre, si hay jugadores
     * dentro, si el mundo esta habilitado y cuantos mobs naturales hay vivos. Se escribe solo
     * cuando cambia algo, para no llenar el log cada 10 segundos.
     */
    private void snapshot(PluginConfig config) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        StringBuilder where = new StringBuilder();
        for (Player player : players) {
            if (where.length() > 0) {
                where.append(", ");
            }
            where.append(player.getName()).append('@').append(player.getWorld().getName())
                    .append(' ').append(player.getLocation().getBlockX()).append(',')
                    .append(player.getLocation().getBlockZ());
        }
        String line = "spawning=" + config.spawnEnabled()
                + " naturales=" + this.service.naturalCount() + "/" + config.spawnGlobalCap()
                + " jugadores=[" + where + "]";
        if (line.equals(this.lastSnapshot)) {
            return;
        }
        this.lastSnapshot = line;
        this.service.plugin().getLogger().info("[spawn] ciclo " + line);
    }

    /** Deja en el log (con {@code debug: true}) por que no aparecio, sin repetirlo cada ciclo. */
    private void reason(Player player, String mobId, String why) {
        if (!this.service.config().debug()) {
            return;
        }
        String key = mobId + "@" + player.getName();
        if (why.equals(this.lastReason.get(key))) {
            return;
        }
        this.lastReason.put(key, why);
        this.service.plugin().getLogger().info("[spawn] '" + mobId + "' no aparece cerca de "
                + player.getName() + ": " + why);
    }

    /** Condiciones que se deciden sin mirar el terreno. */
    private boolean eligible(MobDefinition definition, Player player) {
        NaturalSpawn spawn = definition.spawn();
        if (spawn == null || !spawn.natural() || !definition.server()) {
            return false;
        }
        if (!this.service.worldEnabled(player.getWorld())) {
            return false;
        }
        if (!spawn.worlds().isEmpty()
                && !spawn.worlds().contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return this.service.naturalCount(definition.id()) < spawn.cap();
    }

    /** Busca un sitio valido alrededor del jugador. */
    private Location findSpot(Player player, NaturalSpawn spawn) {
        World world = player.getWorld();
        for (int tries = 0; tries < PLACE_TRIES; tries++) {
            double distance = spawn.minDistance()
                    + this.random.nextDouble() * Math.max(0.0D, spawn.maxDistance() - spawn.minDistance());
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            // Regiones (WorldGuard), si el mob las limita. Se mira antes de nada: es lo que
            // mas descarta y sale mas barato que mirar el terreno.
            Location candidate = new Location(world, x + 0.5D, 0.0D, z + 0.5D);
            if (!this.regions.allows(candidate, spawn.allowedRegion(), spawn.denyRegions())) {
                continue;
            }
            // Superficie: es donde tiene sentido y donde no aparece dentro de una cueva.
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y < spawn.minY() || y > spawn.maxY()) {
                continue;
            }
            Block ground = world.getBlockAt(x, y - 1, z);
            Block air = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid() || !air.isPassable()) {
                continue;
            }
            if (!this.timeMatches(world, spawn.time())) {
                continue;
            }
            int light = air.getLightLevel();
            if (light < spawn.lightMin() || light > spawn.lightMax()) {
                continue;
            }
            if (!spawn.biomes().isEmpty()
                    && !spawn.biomes().contains(this.biomeOf(ground).toLowerCase(Locale.ROOT))) {
                continue;
            }
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    /** {@code any} siempre vale; {@code day} y {@code night} miran la hora del mundo. */
    private boolean timeMatches(World world, String time) {
        if (time == null || time.isBlank() || time.equals("any")) {
            return true;
        }
        boolean night = world.getTime() >= 13000L && world.getTime() <= 23000L;
        return time.equals("night") == night;
    }

    /** Bioma del bloque, sin el prefijo {@code minecraft:}. */
    private String biomeOf(Block block) {
        String raw = block.getBiome().getKey().toString();
        int colon = raw.indexOf(':');
        return colon < 0 ? raw : raw.substring(colon + 1);
    }
}
