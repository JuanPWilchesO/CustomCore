package com.juanp.custommobs.command;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.style.MobStyle;
import com.juanp.custommobs.team.TeamLink;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.spawner.SpawnerEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** /custommobs reload | list | give | enchants */
public final class CustomMobsCommand implements CommandExecutor, TabCompleter {

    /**
     * Etiqueta de los mensajes en el chat: negra y roja, en linea con el MOTD.
     *
     * <p>Aqui si vale el '&'/'\u00a7': los mensajes de chat los renderiza el cliente.
     */
    private static final String TAG = com.juanp.custommobs.mob.Texts.color("&0[&cCustomMobs&0]");

    private final CustomMobsPlugin plugin;

    public CustomMobsCommand(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Un jugador raso puede hacer TRES cosas por su cuenta, y nada mas:
        //   - 'cuota' sin argumentos: ver SU propio cupo
        //   - 'color' y 'glow': el estilo de SUS mobs (o el de su team, si es el jefe)
        // Todo lo demas es administracion.
        boolean help = args.length == 0;
        boolean ownQuota = args.length == 1 && "cuota".equalsIgnoreCase(args[0]);
        boolean ownStyle = args.length == 2
                && ("color".equalsIgnoreCase(args[0]) || "glow".equalsIgnoreCase(args[0]));
        // 'despedir' es de jugador: es su via para deshacerse de sus mobs, ya que el
        // plugin le impide matarlos a golpes.
        boolean ownDismiss = args.length == 2
                && ("despedir".equalsIgnoreCase(args[0]) || "dismiss".equalsIgnoreCase(args[0]));
        // '_chain' es el avance de una conversacion: lo manda un boton del chat, en nombre
        // del jugador, asi que tiene que poder usarlo cualquiera.
        boolean ownChain = args.length >= 2 && "_chain".equalsIgnoreCase(args[0]);
        // El brillo de facciones, en modo 'player', lo decide cada uno.
        boolean ownGlow = args.length == 1 && "glowfaccion".equalsIgnoreCase(args[0]);
        String needed = (help || ownQuota || ownStyle || ownDismiss || ownChain || ownGlow)
                ? this.plugin.config().playerPermission()
                : this.plugin.config().adminPermission();
        if (!sender.hasPermission(needed)) {
            sender.sendMessage(TAG + " No tienes permiso (" + needed + ").");
            return true;
        }
        if (help) {
            this.help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                this.plugin.reloadAll();
                sender.sendMessage(TAG + " Recargado. Definiciones: " + this.plugin.registry().size());
            }
            case "list" -> {
                sender.sendMessage(TAG + " Definiciones (" + this.plugin.registry().size() + "):");
                for (MobDefinition definition : this.plugin.registry().all()) {
                    sender.sendMessage(" - " + definition.id() + " (" + definition.entityType().name()
                            + ", " + definition.category().name().toLowerCase(Locale.ROOT) + ")");
                }
            }
            case "give" -> this.give(sender, args);
            case "spawn" -> this.spawn(sender, args);
            case "remove" -> this.remove(sender, args);
            case "active" -> this.active(sender);
            case "cuota" -> this.quota(sender, args);
            case "kill" -> this.kill(sender, args);
            case "enchants" -> this.enchants(sender, args);
            case "item" -> this.item(sender, args);
            case "spawner" -> this.spawner(sender, args);
            case "book", "libro" -> this.book(sender, args);
            case "upgrade", "mejora" -> this.upgrade(sender, args);
            case "despedir", "dismiss" -> this.dismiss(sender, args);
            case "_chain" -> this.chainStep(sender, args);
            case "glowfaccion" -> this.factionGlow(sender);
            case "faction", "faccion" -> this.faction(sender, args);
            case "color" -> this.setStyle(sender, args, "name");
            case "glow" -> this.setStyle(sender, args, "glow");
            default -> this.help(sender);
        }
        return true;
    }

    /**
     * Avance de una conversacion, disparado por un boton del chat.
     *
     * <p>Es un comando interno: no sale en la ayuda y no se anuncia. El paso no viaja en el
     * comando; se usa el que el servidor tiene apuntado para ese jugador, asi que nadie puede
     * saltarse un paso —ni un pago— escribiendolo a mano.
     */
    private void chainStep(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 3) {
            return;
        }
        UUID conversationId;
        int option;
        try {
            conversationId = UUID.fromString(args[1]);
            option = Integer.parseInt(args[2]);
        } catch (IllegalArgumentException ex) {
            return;
        }
        this.plugin.chains().choose(player, conversationId, option);
    }

    /**
     * Asigna faccion a un jugador. Solo administracion: toca las reglas de ataque.
     *
     * <p>Con faccion, los mobs de faccion tratan al jugador por {@code factions.yml}: los
     * suyos y los aliados son intocables, los enemigos son objetivo. Sin faccion, decide la
     * actitud del mob, como hasta ahora.
     */
    private void faction(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /custommobs faction <set|get|clear|list>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            Map<UUID, String> all = this.plugin.playerFactions().all();
            if (all.isEmpty()) {
                sender.sendMessage(TAG + " No hay ninguna faccion asignada.");
                return;
            }
            sender.sendMessage(TAG + " Jugadores con faccion (" + all.size() + "):");
            for (Map.Entry<UUID, String> entry : all.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                String label = player.getName() != null ? player.getName() : entry.getKey().toString();
                sender.sendMessage(" - " + label + " -> " + entry.getValue());
            }
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TAG + " Uso: /custommobs faction " + action + " <jugador>"
                    + (action.equals("set") ? " <faccion>" : ""));
            return;
        }
        OfflinePlayer target = this.resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(TAG + " No encuentro a '" + args[2] + "'.");
            return;
        }
        String label = target.getName() != null ? target.getName() : args[2];
        switch (action) {
            case "get" -> sender.sendMessage(TAG + " " + label + ": " + this.plugin.playerFactions()
                    .of(target.getUniqueId()).orElse("sin faccion"));
            case "clear" -> {
                if (this.plugin.playerFactions().clear(target.getUniqueId())) {
                    sender.sendMessage(TAG + " " + label + " ya no tiene faccion.");
                } else {
                    sender.sendMessage(TAG + " " + label + " no tenia faccion.");
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(TAG + " Uso: /custommobs faction set <jugador> <faccion>");
                    return;
                }
                String faction = args[3].toLowerCase(Locale.ROOT);
                this.plugin.playerFactions().set(target.getUniqueId(), faction);
                sender.sendMessage(TAG + " " + label + " ahora es de la faccion '" + faction + "'.");
                this.leaveInconsistentTeam(target);
                if (!this.knownFaction(faction)) {
                    sender.sendMessage(TAG + " Aviso: '" + faction + "' no sale en factions.yml"
                            + " ni en ningun mob; comprueba la ortografia.");
                }
            }
            default -> sender.sendMessage(TAG + " Uso: /custommobs faction <set|get|clear|list>");
        }
    }

    /**
     * Enciende o apaga el brillo de facciones para quien lo pide.
     *
     * <p>Solo tiene efecto con {@code faction-glow.mode: player}; si el servidor lo fuerza
     * ({@code global}) o lo tiene apagado ({@code off}), la decision no es del jugador.
     */
    private void factionGlow(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TAG + " 'glowfaccion' solo funciona desde el juego.");
            return;
        }
        String mode = this.plugin.glow().mode();
        if (!"player".equals(mode)) {
            sender.sendMessage(TAG + " El brillo de facciones no lo decides tu ahora mismo"
                    + " (el servidor esta en modo '" + mode + "').");
            return;
        }
        boolean on = this.plugin.glow().toggle(player);
        sender.sendMessage(TAG + " Brillo de facciones: " + (on ? "activado" : "desactivado") + ".");
    }

    /**
     * Si el jugador esta en un team cuyo jefe es de OTRA faccion, lo saca del team.
     *
     * <p>Un team con dos facciones dentro deja de ser consecuente con las reglas. La API de
     * Teams solo deja <b>leer</b> —no tiene forma de sacar a nadie—, asi que la salida se hace
     * con su comando, configurable en {@code config.yml} para no adivinar la sintaxis.
     */
    private void leaveInconsistentTeam(OfflinePlayer target) {
        TeamLink teams = this.plugin.teamLink();
        if (!teams.isAvailable() || teams.teamOf(target.getUniqueId()).isEmpty()) {
            return;
        }
        UUID ownerId = teams.ownerOf(target.getUniqueId()).orElse(null);
        if (ownerId == null || ownerId.equals(target.getUniqueId())) {
            return; // es el jefe: el team es suyo, y decide el
        }
        String ownerFaction = this.plugin.playerFactions().of(ownerId).orElse(null);
        String mine = this.plugin.playerFactions().of(target.getUniqueId()).orElse(null);
        boolean inconsistent;
        if (ownerFaction == null) {
            inconsistent = mine != null && this.plugin.getConfig()
                    .getBoolean("teams.kick-if-owner-factionless", false);
        } else {
            inconsistent = !ownerFaction.equals(mine);
        }
        if (!inconsistent) {
            return;
        }
        String name = target.getName() != null ? target.getName() : "";
        String command = this.plugin.getConfig()
                .getString("teams.kick-command", "team kick {jugador}")
                .replace("{jugador}", name);
        this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), command);
    }

    /** Resuelve un jugador por nombre, conectado o no, sin bloquear el servidor. */
    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    /** {@code true} si esa faccion existe: aparece en la definicion de algun mob. */
    private boolean knownFaction(String faction) {
        for (MobDefinition definition : this.plugin.registry().all()) {
            if (faction.equals(definition.faction())) {
                return true;
            }
        }
        return false;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /" + "custommobs give <id> [jugador]");
            return;
        }
        MobDefinition definition = this.plugin.registry().get(args[1]).orElse(null);
        if (definition == null) {
            sender.sendMessage(TAG + " No existe la definicion '" + args[1] + "'.");
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = this.plugin.getServer().getPlayerExact(args[2]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            target = null;
        }
        if (target == null) {
            sender.sendMessage(TAG + " Jugador no encontrado.");
            return;
        }

        ItemStack egg = this.plugin.craft().createEgg(definition);
        if (egg == null) {
            sender.sendMessage(TAG + " '" + definition.id() + "' no define huevo; usa /custommobs spawn.");
            return;
        }
        target.getInventory().addItem(egg);
        sender.sendMessage(TAG + " Huevo '" + definition.id() + "' entregado a " + target.getName() + ".");
    }

    private void spawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /custommobs spawn <id> [mundo x y z]");
            return;
        }
        MobDefinition definition = this.plugin.registry().get(args[1]).orElse(null);
        if (definition == null) {
            sender.sendMessage(TAG + " No existe la definicion '" + args[1] + "'.");
            return;
        }

        Location location;
        if (args.length >= 6) {
            World world = this.plugin.getServer().getWorld(args[2]);
            if (world == null) {
                sender.sendMessage(TAG + " Mundo no encontrado: " + args[2]);
                return;
            }
            try {
                location = new Location(world, Double.parseDouble(args[3]),
                        Double.parseDouble(args[4]), Double.parseDouble(args[5]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(TAG + " Coordenadas invalidas.");
                return;
            }
        } else if (sender instanceof Player player) {
            location = player.getLocation();
        } else {
            sender.sendMessage(TAG + " Desde consola hace falta: /custommobs spawn <id> <mundo> <x> <y> <z>");
            return;
        }

        if (!this.plugin.mobs().worldEnabled(location.getWorld())) {
            sender.sendMessage(TAG + " El plugin no funciona en el mundo "
                    + location.getWorld().getName() + ".");
            return;
        }

        Player owner = sender instanceof Player player && !definition.server() ? player : null;
        LivingEntity spawned = this.plugin.mobs().spawn(definition, location, owner);
        if (spawned == null) {
            sender.sendMessage(TAG + " No se pudo invocar '" + definition.id() + "'.");
            return;
        }
        sender.sendMessage(TAG + " Invocado '" + definition.id() + "' (" + definition.category().name().toLowerCase(Locale.ROOT)
                + ") en " + location.getWorld().getName() + " " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ() + ".");
    }

    /** Lista los mobs custom vivos, con su posicion y si pertenecen a un spawner. */
    /**
     * Consulta el cupo de mobs de jugador y permite corregirlo.
     *
     * <p>El cupo vive en disco porque incluye los mobs anclados a un bloque que ahora
     * mismo estan en un chunk descargado. Si uno desaparece sin avisar (un plugin
     * externo, un corte), queda contado de mas:
     * <ul>
     *   <li>{@code recontar} rehace la cuenta y no toca ningun mob.</li>
     *   <li>{@code retirar} ademas mata los mobs que alcanza.</li>
     * </ul>
     */
    private void quota(CommandSender sender, String[] args) {
        // 'liberar' se acepta como sinonimo por costumbre, pero el nombre bueno es
        // 'recontar': el verbo no libera nada, solo rehace la cuenta.
        boolean libera = args.length >= 3
                && ("recontar".equalsIgnoreCase(args[1]) || "liberar".equalsIgnoreCase(args[1]));
        boolean retira = args.length >= 3 && "retirar".equalsIgnoreCase(args[1]);
        if (libera || retira) {
            Player target = this.plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(TAG + " El jugador " + args[2] + " debe estar conectado para tocarle el cupo.");
                return;
            }
            if (libera) {
                int ghosts = this.plugin.mobs().resyncPlayerMobs(target.getUniqueId());
                int used = this.plugin.mobs().countPlayerMobs(target.getUniqueId());
                int max = this.plugin.mobs().limitOf(target.getUniqueId());
                sender.sendMessage(TAG + " Cupo de " + target.getName() + " recontado: " + ghosts
                        + " entradas fantasma descartadas. No se toco ningun mob.");
                sender.sendMessage(TAG + " Quedan " + used
                        + (max > 0 ? " de " + max + " mobs" : " mobs") + ".");
                if (max > 0 && used >= max) {
                    sender.sendMessage(TAG + " Sigue en el tope: sus mobs existen, asi que no puede invocar mas.");
                    sender.sendMessage(TAG + " Para liberar cupo de verdad hay que retirarlos:"
                            + " /custommobs cuota retirar " + target.getName());
                } else if (used > 0) {
                    sender.sendMessage(TAG + " Los anclados en chunks descargados volveran a contar cuando carguen.");
                }
                return;
            }
            var result = this.plugin.mobs().purgePlayerMobs(target.getUniqueId());
            sender.sendMessage(TAG + " Cupo de " + target.getName() + " a cero. Mobs retirados: "
                    + result.removed() + ".");
            if (result.unreachable() > 0) {
                sender.sendMessage(TAG + " " + result.unreachable()
                        + " siguen anclados en chunks descargados: no se pueden retirar hasta que carguen,"
                        + " y volveran a contar solos cuando eso pase.");
            }
            return;
        }

        UUID ownerId;
        String label;
        if (args.length >= 2) {
            Player target = this.plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(TAG + " Jugador no encontrado: " + args[1]);
                return;
            }
            ownerId = target.getUniqueId();
            label = target.getName();
        } else if (sender instanceof Player player) {
            ownerId = player.getUniqueId();
            label = player.getName();
        } else {
            sender.sendMessage(TAG + " Uso: /custommobs cuota [jugador]");
            sender.sendMessage(TAG + "      /custommobs cuota recontar <jugador>  (solo rehace la cuenta, no toca mobs)");
            sender.sendMessage(TAG + "      /custommobs cuota retirar <jugador>  (mata sus mobs y deja el cupo en cero)");
            return;
        }

        int used = this.plugin.mobs().countPlayerMobs(ownerId);
        // Dos cuentas aparte: los que siguen al dueno y los fijos a un bloque.
        int maxOwner = this.plugin.mobs().limitOf(ownerId);
        int usedOwner = this.plugin.mobs().countPlayerMobs(ownerId, false);
        int maxPoint = this.plugin.mobs().pointLimitOf(ownerId);
        int usedPoint = this.plugin.mobs().countPlayerMobs(ownerId, true);
        sender.sendMessage(TAG + " " + label + ":");
        sender.sendMessage(" - Siguen al dueno: " + usedOwner
                + (maxOwner > 0 ? " de " + maxOwner : " (sin limite)"));
        sender.sendMessage(" - Fijos a un bloque: " + usedPoint
                + (maxPoint > 0 ? " de " + maxPoint : " (sin limite)"));
        int maxMount = this.plugin.mobs().mountLimitOf(ownerId);
        int usedMount = this.plugin.mobs().countPlayerMobs(ownerId,
                com.juanp.custommobs.mob.MobService.Account.MOUNT);
        sender.sendMessage(" - Monturas: " + usedMount
                + (maxMount > 0 ? " de " + maxMount : " (sin limite)"));
        int loaded = 0;
        for (CustomMob customMob : this.plugin.mobs().active()) {
            if (!ownerId.equals(customMob.ownerId())) {
                continue;
            }
            loaded++;
            var location = customMob.entity().getLocation();
            sender.sendMessage(" - " + customMob.definition().id() + " en "
                    + location.getWorld().getName() + " " + location.getBlockX() + " "
                    + location.getBlockY() + " " + location.getBlockZ());
        }
        if (used > loaded) {
            sender.sendMessage(TAG + " " + (used - loaded)
                    + " mas en chunks descargados (anclados a su bloque).");
        }
    }

    /** Nombre del dueno, aunque este desconectado. */
    private String ownerName(UUID ownerId) {
        String name = this.plugin.getServer().getOfflinePlayer(ownerId).getName();
        return name != null ? name : ownerId.toString().substring(0, 8);
    }

    /**
     * Gobierna los spawners: listarlos, borrarlos y releerlos.
     *
     * <p>Hasta ahora la unica via era {@code /custommobs remove}, que exige estar en el
     * juego y solo alcanza lo que este cargado alrededor. Con esto se limpian desde la
     * consola y sin caminar hasta el sitio.
     */
    private void spawner(CommandSender sender, String[] args) {
        if (args.length < 2) {
            this.spawnerUsage(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> this.spawnerList(sender);
            case "remove" -> this.spawnerRemove(sender, args);
            case "removeall" -> this.spawnerRemoveAll(sender, args);
            case "reload" -> {
                this.plugin.spawners().load();
                sender.sendMessage(TAG + " Spawners releidos: " + this.plugin.spawners().size() + ".");
            }
            default -> this.spawnerUsage(sender);
        }
    }

    private void spawnerUsage(CommandSender sender) {
        sender.sendMessage(TAG + " Uso: /custommobs spawner list");
        sender.sendMessage(TAG + "      /custommobs spawner remove <id>");
        sender.sendMessage(TAG + "      /custommobs spawner removeall [mob]");
        sender.sendMessage(TAG + "      /custommobs spawner reload");
    }

    private void spawnerList(CommandSender sender) {
        var all = this.plugin.spawners().all();
        sender.sendMessage(TAG + " Spawners (" + all.size() + "):");
        for (SpawnerEntry entry : all) {
            sender.sendMessage(" - " + entry.id() + " " + entry.definitionId()
                    + " en " + entry.world() + " " + (int) entry.x() + " " + (int) entry.y()
                    + " " + (int) entry.z()
                    + (entry.respawns() ? " cada " + entry.respawnSeconds() + "s" : " (una sola vida)")
                    + (entry.awaitingRespawn() ? " [esperando reaparicion]" : ""));
        }
    }

    private void spawnerRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TAG + " Uso: /custommobs spawner remove <id>");
            return;
        }
        UUID id = parseUuid(args[2]);
        SpawnerEntry entry = id == null ? null : this.plugin.spawners().get(id).orElse(null);
        if (entry == null) {
            sender.sendMessage(TAG + " No hay ningun spawner con ese id; miralos con"
                    + " '/custommobs spawner list'.");
            return;
        }
        int killed = this.retireSpawnerMobs(entry);
        this.plugin.spawners().remove(id);
        this.plugin.spawners().save();
        sender.sendMessage(TAG + " Spawner de '" + entry.definitionId() + "' borrado"
                + (killed > 0 ? " (mobs retirados: " + killed + ")" : "") + ".");
    }

    private void spawnerRemoveAll(CommandSender sender, String[] args) {
        String filter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        List<SpawnerEntry> doomed = new ArrayList<>();
        for (SpawnerEntry entry : this.plugin.spawners().all()) {
            if (filter == null || entry.definitionId().equalsIgnoreCase(filter)) {
                doomed.add(entry);
            }
        }
        if (doomed.isEmpty()) {
            sender.sendMessage(TAG + " No hay spawners"
                    + (filter == null ? "" : " de '" + filter + "'") + ".");
            return;
        }
        int killed = 0;
        for (SpawnerEntry entry : doomed) {
            killed += this.retireSpawnerMobs(entry);
            this.plugin.spawners().remove(entry.id());
        }
        this.plugin.spawners().save();
        sender.sendMessage(TAG + " Spawners borrados: " + doomed.size()
                + (filter == null ? "" : " (de '" + filter + "')")
                + (killed > 0 ? ", mobs retirados: " + killed : "") + ".");
    }

    /**
     * Retira los mobs vivos de ese spawner. Solo toca los que estan claramente en su
     * punto: los demas pueden ser de otro spawner del mismo mob.
     */
    private int retireSpawnerMobs(SpawnerEntry entry) {
        int removed = 0;
        for (CustomMob customMob : this.plugin.mobs().active()) {
            var entity = customMob.entity();
            if (!entity.isValid() || !customMob.definition().id().equals(entry.definitionId())) {
                continue;
            }
            if (!entry.world().equals(entity.getWorld().getName())) {
                continue;
            }
            Location point = new Location(entity.getWorld(), entry.x(), entry.y(), entry.z());
            if (entity.getLocation().distanceSquared(point) > 9.0D) {
                continue;
            }
            this.plugin.mobs().despawn(customMob);
            removed++;
        }
        return removed;
    }

    /** Nombres de color validos para 'color' y 'glow', mas el apagado. */
    private static List<String> colorNames() {
        List<String> names = new ArrayList<>();
        for (ChatColor color : ChatColor.values()) {
            if (color.isColor()) {
                names.add(color.name().toLowerCase(Locale.ROOT));
            }
        }
        names.add(MobStyle.OFF);
        return names;
    }

    /**
     * Despide mobs propios: los destruye a proposito.
     *
     * <p>Lo puede usar cualquier jugador porque solo alcanza a los suyos. Es la
     * contrapartida de que el plugin le impida matarlos a golpes.
     */
    private void dismiss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TAG + " 'despedir' solo funciona desde el juego.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /custommobs despedir <id|todos>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        boolean all = id.equals("todos") || id.equals("all");
        if (!all && this.plugin.registry().get(id).isEmpty()) {
            sender.sendMessage(TAG + " No existe ese mob; miralos con /custommobs list.");
            return;
        }
        int removed = this.plugin.mobs().dismiss(player.getUniqueId(), all ? null : id);
        sender.sendMessage(TAG + " Mobs despedidos: " + removed
                + ". Los huevos que los representaban quedan inservibles.");
    }

    /** Entrega el libro de inspeccion. Repartirlo es tarea de administracion. */
    private void book(CommandSender sender, String[] args) {
        Player target = this.targetPlayer(sender, args, 1);
        if (target == null) {
            return;
        }
        this.deliver(target, this.plugin.book().create());
        sender.sendMessage(TAG + " Libro entregado a " + target.getName() + ".");
        if (!sender.equals(target)) {
            target.sendMessage(TAG + " Has recibido el libro de mobs.");
        }
    }

    /** Items de mejora: verlos, repartirlos y releerlos. */
    private void upgrade(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /custommobs upgrade list | give <id> [jugador] | reload");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage(TAG + " Items de mejora (" + this.plugin.upgrades().size() + "):");
                for (var spec : this.plugin.upgrades().all()) {
                    sender.sendMessage(" - " + spec.id() + " (" + spec.material().name() + ")"
                            + (spec.craftable() ? " [crafteable]" : " [solo por comando]")
                            + " | " + com.juanp.custommobs.upgrade.UpgradeService.describe(spec));
                }
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(TAG + " Uso: /custommobs upgrade give <id> [jugador]");
                    return;
                }
                var spec = this.plugin.upgrades().get(args[2]).orElse(null);
                if (spec == null) {
                    sender.sendMessage(TAG + " No hay ningun item de mejora con ese id;"
                            + " miralos con '/custommobs upgrade list'.");
                    return;
                }
                Player target = this.targetPlayer(sender, args, 3);
                if (target == null) {
                    return;
                }
                this.deliver(target, this.plugin.upgradeService().create(spec));
                sender.sendMessage(TAG + " '" + spec.id() + "' entregado a " + target.getName() + ".");
            }
            case "reload" -> {
                this.plugin.upgrades().reload();
                this.plugin.upgradeService().registerRecipes(this.plugin.craft());
                sender.sendMessage(TAG + " Items de mejora recargados: "
                        + this.plugin.upgrades().size() + ".");
            }
            default -> sender.sendMessage(TAG + " Uso: /custommobs upgrade list | give <id> [jugador] | reload");
        }
    }

    /** Jugador objetivo: el que se nombra, o quien escribe si no se nombra. */
    private Player targetPlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player named = this.plugin.getServer().getPlayerExact(args[index]);
            if (named == null) {
                sender.sendMessage(TAG + " Jugador no encontrado: " + args[index]);
            }
            return named;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(TAG + " Desde consola hace falta decir el jugador.");
        return null;
    }

    /** Deja el objeto en el inventario; si no cabe, lo suelta en el suelo. */
    private void deliver(Player player, ItemStack item) {
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** Id desde texto; {@code null} si no es un UUID valido. */
    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void active(CommandSender sender) {
        var mobs = this.plugin.mobs().active();
        sender.sendMessage(TAG + " Mobs activos: " + mobs.size());
        for (CustomMob customMob : mobs) {
            var entity = customMob.entity();
            var location = entity.getLocation();
            String spawner = this.plugin.mobs().spawnerIdOf(entity) != null ? " [spawner]" : "";
            String disguised = this.plugin.mobs().disguiseLink().isDisguised(entity) ? " [disfrazado]" : "";
            String owner = customMob.ownerId() == null ? "" : " [dueno: "
                    + this.ownerName(customMob.ownerId()) + "]";
            sender.sendMessage(" - " + customMob.definition().id() + spawner + disguised + owner + " en "
                    + location.getWorld().getName() + " " + location.getBlockX() + " "
                    + location.getBlockY() + " " + location.getBlockZ()
                    + " | nombre='" + entity.getCustomName() + "'");
        }
    }

    /**
     * Mata los mobs custom de una definicion en todo el servidor. Si pertenecen a un
     * spawner, volveran a aparecer; para eliminarlos de raiz usa 'remove'.
     */
    private void kill(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /custommobs kill <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        // Alcanza tambien los que esten en chunks descargadas: el vinculo guarda donde se
        // les vio, asi que se carga su chunk un momento y se les mata.
        int killed = this.plugin.mobs().killDefinition(id);
        sender.sendMessage(TAG + " Eliminados " + killed + " mobs de '" + id + "'.");
    }

    /** Borra los mobs custom cercanos al emisor. Util para desmontar escenas. */
    private void remove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TAG + " 'remove' solo funciona desde el juego.");
            return;
        }
        double radius = 5.0D;
        if (args.length >= 2) {
            try {
                radius = Math.max(1.0D, Double.parseDouble(args[1]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(TAG + " Radio invalido.");
                return;
            }
        }

        int removed = 0;
        for (CustomMob customMob : this.plugin.mobs().active()) {
            var entity = customMob.entity();
            if (!entity.isValid() || !entity.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (entity.getLocation().distance(player.getLocation()) > radius) {
                continue;
            }
            this.plugin.mobs().despawn(customMob);
            removed++;
        }
        sender.sendMessage(TAG + " Eliminados " + removed + " mobs custom en un radio de "
                + (int) radius + " bloques.");
    }

    /**
     * Cambia el color del nombre o del brillo de los player mobs.
     *
     * <p>Si el jugador esta en un team, el ajuste es del TEAM y solo lo puede cambiar su
     * jefe: asi los mobs de todos los miembros se ven iguales, y nadie pisa el estilo de
     * los demas. Sin team, cada jugador decide sobre los suyos.
     */
    private void setStyle(CommandSender sender, String[] args, String field) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TAG + " Este ajuste solo funciona desde el juego.");
            return;
        }
        boolean isName = "name".equals(field);
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /custommobs " + (isName ? "color" : "glow")
                    + " <color|" + MobStyle.OFF + ">");
            sender.sendMessage(TAG + " Colores: RED, BLUE, GREEN, GOLD, AQUA, LIGHT_PURPLE...");
            return;
        }

        String raw = args[1].toLowerCase(Locale.ROOT);
        boolean off = MobStyle.OFF.equals(raw) || "none".equals(raw);
        if (!off && MobStyle.color(raw) == null) {
            sender.sendMessage(TAG + " Color invalido: '" + args[1] + "'. Prueba RED, GOLD... o '"
                    + MobStyle.OFF + "'.");
            return;
        }

        TeamLink teams = this.plugin.mobs().teamLink();
        Optional<UUID> teamId = teams.teamOf(player.getUniqueId());
        boolean viaTeam = false;
        if (teamId.isPresent()) {
            UUID leader = teams.ownerOf(player.getUniqueId()).orElse(null);
            if (leader == null || !leader.equals(player.getUniqueId())) {
                sender.sendMessage(TAG + " Tu team decide este ajuste: solo su jefe puede cambiarlo.");
                return;
            }
            viaTeam = true;
        }

        String value = off ? MobStyle.OFF : raw;
        if (viaTeam) {
            this.plugin.styles().registry().setTeam(teamId.get(), field, value);
        } else {
            this.plugin.styles().registry().setPlayer(player.getUniqueId(), field, value);
        }
        this.plugin.styles().registry().save();
        int touched = this.plugin.styles().refresh();

        sender.sendMessage(TAG + " " + (isName ? "Color del nombre" : "Brillo") + " en '" + value + "'"
                + (viaTeam ? " para todo el team" : "") + ". Mobs actualizados: " + touched + ".");
    }

    /**
     * Catalogo de objetos con nombre.
     *
     * <p>Es la via para guardar objetos con NBT: un encantamiento de otro plugin vive en el
     * NBT del item y no se puede escribir a mano en un yml. Se guarda una vez, con un nombre,
     * y despues cualquier mob lo llama desde su tabla de drops con {@code item: <nombre>}.
     */
    private void item(CommandSender sender, String[] args) {
        String usage = TAG + " Uso: /custommobs item save <nombre> | list | remove <nombre>";
        if (args.length < 2) {
            sender.sendMessage(usage);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "save" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TAG + " 'item save' necesita tu mano: solo desde el juego.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(TAG + " Uso: /custommobs item save <nombre>");
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    sender.sendMessage(TAG + " Pon el objeto a guardar en la mano principal.");
                    return;
                }
                if (!this.plugin.drops().catalog().save(args[2], hand.clone())) {
                    sender.sendMessage(TAG + " Nombre invalido: solo letras, numeros, guion y guion bajo (max 48).");
                    return;
                }
                String name = args[2].toLowerCase(Locale.ROOT).trim().replace(' ', '_');
                sender.sendMessage(TAG + " Item guardado como '" + name + "' en items/" + name + ".yml.");
                sender.sendMessage(TAG + " Para que caiga, en el yml del mob: drops: - item: '" + name + "'");
            }
            case "list" -> {
                var names = this.plugin.drops().catalog().names();
                sender.sendMessage(TAG + " Items guardados (" + names.size() + "):");
                for (String name : names) {
                    sender.sendMessage(" - " + name);
                }
            }
            case "remove" -> {
                if (args.length < 3 || !this.plugin.drops().catalog().remove(args[2])) {
                    sender.sendMessage(TAG + " Uso: /custommobs item remove <nombre> (debe existir)");
                    return;
                }
                sender.sendMessage(TAG + " Item '" + args[2].toLowerCase(Locale.ROOT) + "' borrado del catalogo.");
            }
            default -> sender.sendMessage(usage);
        }
    }

    /** Lista las claves de encantamiento disponibles (incluye las de ExcellentEnchants). */
    private void enchants(CommandSender sender, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        int shown = 0;
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            String key = enchantment.getKey().toString();
            if (!filter.isEmpty() && !key.contains(filter)) {
                continue;
            }
            sender.sendMessage(" - " + key);
            if (++shown >= 60) {
                sender.sendMessage("... (afina con /custommobs enchants <texto>)");
                break;
            }
        }
        sender.sendMessage(TAG + " Mostrados: " + shown + ". Usa la clave completa en 'enchants:' del yml.");
    }

    private void help(CommandSender sender) {
        sender.sendMessage(TAG + " /custommobs reload | list | give <id> [jugador] | spawn <id> [mundo x y z]");
        sender.sendMessage(TAG + "           | remove [radio] | active | kill <id> | enchants [filtro]");
        sender.sendMessage(TAG + "           | cuota [jugador] | cuota recontar <jugador> | cuota retirar <jugador>");
        sender.sendMessage(TAG + "           | item save <nombre> | item list | item remove <nombre>  (catalogo de items)");
        sender.sendMessage(TAG + "           | color <color|nada> | glow <color|nada>  (tus player mobs)");
        sender.sendMessage(TAG + "           | spawner list | spawner remove <id> | spawner removeall [mob] | spawner reload");
        sender.sendMessage(TAG + "           | book [jugador]  (libro de inspeccion)");
        sender.sendMessage(TAG + "           | upgrade list | upgrade give <id> [jugador] | upgrade reload");
        sender.sendMessage(TAG + "           | despedir <id|todos>  (tus mobs; cualquiera puede usarlo)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        // El autocompletado tambien revela: a un jugador raso se le ofrecen UNICAMENTE los
        // subcomandos que puede usar de verdad. Lo demas es informacion de administracion.
        boolean admin = sender.hasPermission(this.plugin.config().adminPermission());

        if (args.length == 1) {
            List<String> options = admin
                    ? List.of("reload", "list", "give", "spawn", "remove", "active", "kill",
                            "enchants", "cuota", "item", "color", "glow", "spawner", "book", "upgrade")
                    : List.of("cuota", "color", "glow", "despedir");
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        // 'color' y 'glow' son suyos: completar sus valores no revela nada de gestion.
        if (args.length == 2 && (args[0].equalsIgnoreCase("color") || args[0].equalsIgnoreCase("glow"))) {
            for (String name : colorNames()) {
                if (name.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(name);
                }
            }
            return out;
        }
        // De aqui para abajo, todo es administracion: sin permiso, no se completa nada.
        if (!admin) {
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("mejora"))) {
            for (String option : List.of("list", "give", "reload")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("mejora"))
                && args[1].equalsIgnoreCase("give")) {
            for (var spec : this.plugin.upgrades().all()) {
                if (spec.id().startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(spec.id());
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawner")) {
            for (String option : List.of("list", "remove", "removeall", "reload")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawner")) {
            if (args[1].equalsIgnoreCase("remove")) {
                for (SpawnerEntry entry : this.plugin.spawners().all()) {
                    String id = entry.id().toString();
                    if (id.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        out.add(id);
                    }
                }
            } else if (args[1].equalsIgnoreCase("removeall")) {
                for (MobDefinition definition : this.plugin.registry().all()) {
                    if (definition.id().startsWith(args[2].toLowerCase(Locale.ROOT))) {
                        out.add(definition.id());
                    }
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("spawn")
                || args[0].equalsIgnoreCase("kill"))) {
            for (MobDefinition definition : this.plugin.registry().all()) {
                if (definition.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(definition.id());
                }
            }
        }
        return out;
    }
}
