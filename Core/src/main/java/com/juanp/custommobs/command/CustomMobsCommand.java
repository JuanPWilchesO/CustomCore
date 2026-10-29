package com.juanp.custommobs.command;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.style.MobStyle;
import com.juanp.custommobs.team.TeamLink;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.skill.SkillEvent;
import com.juanp.custommobs.skill.SkillSpec;
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

/** /customcore reload | list | give | enchants */
public final class CustomMobsCommand implements CommandExecutor, TabCompleter {

    /**
     * Etiqueta de los mensajes en el chat: negra y roja, en linea con el MOTD.
     *
     * <p>Aqui si vale el '&'/'\u00a7': los mensajes de chat los renderiza el cliente.
     */
    // El color del cierre manda: lo que venga despues del prefijo hereda ese color. En negro
    // (&0) los mensajes no se leian en el chat, asi que va en gris (&8).
    private static final String TAG = com.juanp.custommobs.mob.Texts.color("&8[&cCustomCore&8]");

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
            case "effects" -> this.effects(sender, args);
            case "testcast", "cast" -> this.testCast(sender, args);
            case "triggers", "trigger" -> this.triggers(sender, args);
            case "potion" -> this.potion(sender, args);
            case "model", "modelo" -> this.model(sender, args);
            case "spawner" -> this.spawner(sender, args);
            case "book", "libro" -> this.book(sender, args);
            case "upgrade", "mejora" -> this.upgrade(sender, args);
            case "despedir", "dismiss" -> this.dismiss(sender, args);
            case "_chain" -> this.chainStep(sender, args);
            case "glowfaccion" -> this.factionGlow(sender);
            case "faction", "faccion" -> this.faction(sender, args);
            case "class", "clase" -> this.clazz(sender, args);
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
    /**
     * Clases: asignarlas, consultarlas y listarlas.
     *
     * <p>A diferencia de la faccion, la clase no cambia a quien atacas ni a quien proteges:
     * solo restringe objetos. Un jugador puede ser de cualquier clase en cualquier faccion.
     */
    private void clazz(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /customcore class <set|get|clear|list>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            Map<UUID, String> all = this.plugin.playerClasses().all();
            if (all.isEmpty()) {
                sender.sendMessage(TAG + " Nadie tiene clase todavia.");
                return;
            }
            sender.sendMessage(TAG + " Jugadores con clase (" + all.size() + "):");
            for (Map.Entry<UUID, String> entry : all.entrySet()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                String label = player.getName() != null ? player.getName() : entry.getKey().toString();
                sender.sendMessage(" - " + label + " -> " + entry.getValue());
            }
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TAG + " Uso: /customcore class " + action + " <jugador>"
                    + (action.equals("set") ? " <clase>" : ""));
            return;
        }
        OfflinePlayer target = this.resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(TAG + " No encuentro a '" + args[2] + "'.");
            return;
        }
        String label = target.getName() != null ? target.getName() : args[2];
        switch (action) {
            case "get" -> sender.sendMessage(TAG + " " + label + ": " + this.plugin.playerClasses()
                    .of(target.getUniqueId()).orElse("sin clase"));
            case "clear" -> {
                if (this.plugin.playerClasses().clear(target.getUniqueId())) {
                    sender.sendMessage(TAG + " " + label + " ya no tiene clase.");
                } else {
                    sender.sendMessage(TAG + " " + label + " no tenia clase.");
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(TAG + " Uso: /customcore class set <jugador> <clase>");
                    return;
                }
                String clazz = args[3].toLowerCase(Locale.ROOT);
                if (!this.plugin.classes().exists(clazz)) {
                    sender.sendMessage(TAG + " Esa clase no existe. Conocidas: "
                            + String.join(", ", this.plugin.classes().known())
                            + " (se definen en classes.yml).");
                    return;
                }
                this.plugin.playerClasses().set(target.getUniqueId(), clazz);
                sender.sendMessage(TAG + " " + label + " ahora es de la clase '" + clazz + "'.");
            }
            default -> sender.sendMessage(TAG + " Uso: /customcore class <set|get|clear|list>");
        }
    }

    private void faction(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /customcore faction <set|get|clear|list>");
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
            sender.sendMessage(TAG + " Uso: /customcore faction " + action + " <jugador>"
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
                    sender.sendMessage(TAG + " Uso: /customcore faction set <jugador> <faccion>");
                    return;
                }
                String faction = args[3].toLowerCase(Locale.ROOT);
                // No se entra en una faccion que rompa el team: se rechaza el cambio, en vez de
                // dejar entrar y tener que sacar a nadie despues.
                String conflict = this.plugin.factionTeams()
                        .conflictFor(target.getUniqueId(), faction);
                if (conflict != null) {
                    sender.sendMessage(TAG + " No se puede: " + label + " " + conflict
                            + ". Que salga del team primero —o dale esa faccion—.");
                    return;
                }
                this.plugin.playerFactions().set(target.getUniqueId(), faction);
                sender.sendMessage(TAG + " " + label + " ahora es de la faccion '" + faction + "'.");
                if (!this.knownFaction(faction)) {
                    sender.sendMessage(TAG + " Aviso: '" + faction + "' no sale en factions.yml"
                            + " ni en ningun mob; comprueba la ortografia.");
                }
            }
            default -> sender.sendMessage(TAG + " Uso: /customcore faction <set|get|clear|list>");
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

    /** Lanza una skill desde el jugador, sin pasar por el clic del item. Sirve para probar distancias. */
    private void testCast(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TAG + " Solo un jugador puede lanzar una skill de prueba.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(TAG + " Uso: /customcore testcast <skill>");
            return;
        }
        if (this.plugin.skillService() == null) {
            player.sendMessage(TAG + " El servicio de skills no esta listo.");
            return;
        }
        int cast = this.plugin.skillService().cast(player,
                List.of(args[1].toLowerCase(Locale.ROOT)));
        player.sendMessage(TAG + " Skills lanzadas: " + cast);
    }

    /**
     * Diagnostico de triggers: dice, para cada skill de un mob, por que via se activa.
     *
     * <p>Sirve para lo que mas cuesta depurar a mano: una skill que no salta porque su
     * trigger es de estado y no se cumple, o una que tiene solo trigger y por eso el ciclo
     * periodico no la toca. No dispara nada: solo lee la definicion ya cargada.
     */
    private void triggers(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /customcore triggers <mob>");
            return;
        }
        MobDefinition definition = this.plugin.registry().get(args[1].toLowerCase(Locale.ROOT))
                .orElse(null);
        if (definition == null) {
            sender.sendMessage(TAG + " No existe la definicion '" + args[1] + "'.");
            return;
        }
        if (definition.skills().isEmpty()) {
            sender.sendMessage(TAG + " '" + definition.id() + "' no tiene skills.");
            return;
        }
        sender.sendMessage(TAG + " Triggers de '" + definition.id() + "' ("
                + definition.skills().size() + " skills):");
        for (SkillSpec skill : definition.skills()) {
            String via;
            if (skill.mode() != null && skill.mode().periodic()) {
                via = "tick (" + skill.mode().name().toLowerCase(Locale.ROOT) + ")";
            } else if (skill.mode() != null) {
                via = "clic (" + skill.mode().name().toLowerCase(Locale.ROOT) + ")";
            } else {
                via = "solo triggers";
            }
            if (skill.hasStateTrigger()) {
                via += " + estado";
            }
            String events = skill.hasTrigger() ? skill.events().stream()
                    .map(SkillEvent::label).sorted()
                    .reduce((a, b) -> a + ", " + b).orElse("-") : "-";
            sender.sendMessage(" - " + skill.id() + ": " + via + " | " + events
                    + (skill.triggerBlock() != null ? " | block=" + skill.triggerBlock() : "")
                    + (skill.daytime() != null ? " | daytime=" + skill.daytime() : "")
                    + (skill.health() > 0.0D ? " | health=" + (skill.healthPercent()
                    ? (skill.health() * 100.0D) + "%" : skill.health()) : ""));
        }
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
            sender.sendMessage(TAG + " '" + definition.id() + "' no define huevo; usa /customcore spawn.");
            return;
        }
        target.getInventory().addItem(egg);
        sender.sendMessage(TAG + " Huevo '" + definition.id() + "' entregado a " + target.getName() + ".");
    }

    private void spawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TAG + " Uso: /customcore spawn <id> [mundo x y z]");
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
            sender.sendMessage(TAG + " Desde consola hace falta: /customcore spawn <id> <mundo> <x> <y> <z>");
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
                            + " /customcore cuota retirar " + target.getName());
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
            sender.sendMessage(TAG + " Uso: /customcore cuota [jugador]");
            sender.sendMessage(TAG + "      /customcore cuota recontar <jugador>  (solo rehace la cuenta, no toca mobs)");
            sender.sendMessage(TAG + "      /customcore cuota retirar <jugador>  (mata sus mobs y deja el cupo en cero)");
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
            // Con el id delante, el jugador ya sabe que escribir en 'despedir'. La marca
            // de montura ayuda a distinguirlos cuando comparten definicion.
            boolean mount = customMob.definition().category()
                    == com.juanp.custommobs.mob.MobCategory.MOUNT;
            String mark = this.plugin.mobs().playerMobs().byEntity(customMob.entity().getUniqueId())
                    .map(link -> "#" + com.juanp.custommobs.mob.MobService.shortId(link.linkId()))
                    .orElse("");
            sender.sendMessage(" - " + customMob.definition().id() + mark + (mount ? " (montura)" : "")
                    + " en " + location.getWorld().getName() + " " + location.getBlockX() + " "
                    + location.getBlockY() + " " + location.getBlockZ());
        }
        // Los que estan dentro de un huevo no son entidades vivas: no salen en la lista de
        // activos, pero si cuentan. Sin esto, el jugador no veia la mitad de su cupo.
        int inEggs = 0;
        for (com.juanp.custommobs.mob.PlayerMobRegistry.MobLink link
                : this.plugin.mobs().playerMobs().all()) {
            if (!ownerId.equals(link.owner()) || link.deployed()) {
                continue;
            }
            inEggs++;
            sender.sendMessage(" - " + link.definitionId() + "#"
                    + com.juanp.custommobs.mob.MobService.shortId(link.linkId()) + " (guardado en su huevo)");
        }
        if (used > loaded) {
            sender.sendMessage(TAG + " " + (used - loaded)
                    + " mas en chunks descargados (anclados a su bloque).");
        }
        if (loaded + inEggs == 0 && used == 0) {
            sender.sendMessage(" - (no tiene mobs)");
        } else {
            sender.sendMessage(TAG + " Para deshacerte de uno: /customcore despedir <id>, o 'todos'.");
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
     * <p>Hasta ahora la unica via era {@code /customcore remove}, que exige estar en el
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
        sender.sendMessage(TAG + " Uso: /customcore spawner list");
        sender.sendMessage(TAG + "      /customcore spawner remove <id>");
        sender.sendMessage(TAG + "      /customcore spawner removeall [mob]");
        sender.sendMessage(TAG + "      /customcore spawner reload");
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
            sender.sendMessage(TAG + " Uso: /customcore spawner remove <id>");
            return;
        }
        UUID id = parseUuid(args[2]);
        SpawnerEntry entry = id == null ? null : this.plugin.spawners().get(id).orElse(null);
        if (entry == null) {
            sender.sendMessage(TAG + " No hay ningun spawner con ese id; miralos con"
                    + " '/customcore spawner list'.");
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
            sender.sendMessage(TAG + " Uso: /customcore despedir <id|todos>");
            return;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        if (raw.equals("todos") || raw.equals("all")) {
            int removed = this.plugin.mobs().dismiss(player.getUniqueId(), null);
            sender.sendMessage(TAG + " Mobs despedidos: " + removed
                    + ". Los huevos que los representaban quedan inservibles.");
            return;
        }
        // Un mob concreto: id#marca. La marca es la cabeza del uuid del vinculo, y es lo que
        // distingue dos 'mercenario_basico' entre si.
        int hash = raw.indexOf('#');
        if (hash > 0) {
            String definitionId = raw.substring(0, hash);
            String handle = raw.substring(hash + 1);
            UUID target = null;
            int matches = 0;
            for (UUID linkId : this.plugin.mobs().playerMobs().linksOf(player.getUniqueId())) {
                var link = this.plugin.mobs().playerMobs().byLink(linkId).orElse(null);
                if (link == null || !link.definitionId().equalsIgnoreCase(definitionId)
                        || !com.juanp.custommobs.mob.MobService.shortId(linkId).startsWith(handle)) {
                    continue;
                }
                target = linkId;
                matches++;
            }
            if (matches == 0) {
                sender.sendMessage(TAG + " No tienes ese mob; miralos con /customcore cuota.");
                return;
            }
            if (matches > 1) {
                sender.sendMessage(TAG + " Esa marca no distingue entre varios: escribe mas caracteres.");
                return;
            }
            this.dismissOne(sender, player, target);
            return;
        }
        // Id suelto: solo vale si hay UNO. Con varios seria la trampa de siempre —despedir uno
        // se llevaba todos los de esa definicion.
        UUID only = null;
        int count = 0;
        for (UUID linkId : this.plugin.mobs().playerMobs().linksOf(player.getUniqueId())) {
            var link = this.plugin.mobs().playerMobs().byLink(linkId).orElse(null);
            if (link != null && link.definitionId().equalsIgnoreCase(raw)) {
                only = linkId;
                count++;
            }
        }
        if (count == 0) {
            sender.sendMessage(TAG + " No tienes ese mob; miralos con /customcore cuota.");
            return;
        }
        if (count > 1) {
            sender.sendMessage(TAG + " Tienes " + count + " '" + raw + "'. Elige cual: /customcore despedir "
                    + raw + "#<marca>  (usa el tabulador).");
            return;
        }
        this.dismissOne(sender, player, only);
    }

    /** Despide un solo mob por su vinculo y lo cuenta. */
    private void dismissOne(CommandSender sender, Player player, UUID linkId) {
        boolean gone = this.plugin.mobs().dismissLink(player.getUniqueId(), linkId);
        sender.sendMessage(TAG + (gone
                ? " Mobs despedidos: 1. El huevo que lo representaba queda inservible."
                : " No se pudo despedir ese mob."));
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
            sender.sendMessage(TAG + " Uso: /customcore upgrade list | give <id> [jugador] | reload");
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
                    sender.sendMessage(TAG + " Uso: /customcore upgrade give <id> [jugador]");
                    return;
                }
                var spec = this.plugin.upgrades().get(args[2]).orElse(null);
                if (spec == null) {
                    sender.sendMessage(TAG + " No hay ningun item de mejora con ese id;"
                            + " miralos con '/customcore upgrade list'.");
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
            default -> sender.sendMessage(TAG + " Uso: /customcore upgrade list | give <id> [jugador] | reload");
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
            sender.sendMessage(TAG + " Uso: /customcore kill <id>");
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
            sender.sendMessage(TAG + " Uso: /customcore " + (isName ? "color" : "glow")
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
        String usage = TAG + " Uso: /customcore item save <nombre> | list | remove <nombre>"
                + " | faccion <nombre> <faccion|nada> | clase <nombre> <clase|nada>"
                + " | mod <nombre> <clave> <si|no>";
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
                    sender.sendMessage(TAG + " Uso: /customcore item save <nombre>");
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
                sender.sendMessage(TAG + " Para craftearlo, anade 'receta:' a items/" + name
                        + ".yml y /customcore reload.");
            }
            case "list" -> {
                var names = this.plugin.drops().catalog().names();
                sender.sendMessage(TAG + " Items guardados (" + names.size() + "):");
                for (String name : names) {
                    String faction = this.plugin.drops().catalog().factionOf(name).orElse(null);
                    boolean craftable = this.plugin.drops().catalog().recipeOf(name).isPresent();
                    sender.sendMessage(" - " + name
                            + (faction == null ? "" : " &7[faccion: " + faction + "]")
                            + (craftable ? " &7[crafteable]" : ""));
                }
            }
            case "clase" -> {
                if (args.length < 4) {
                    sender.sendMessage(TAG + " Uso: /customcore item clase <nombre> <clase|nada>");
                    return;
                }
                String clazz = args[3].equalsIgnoreCase("nada") || args[3].equals("-")
                        ? null : args[3].toLowerCase(Locale.ROOT);
                if (clazz != null && !this.plugin.classes().exists(clazz)) {
                    sender.sendMessage(TAG + " Esa clase no existe. Conocidas: "
                            + String.join(", ", this.plugin.classes().known()));
                    return;
                }
                String itemName = args[2].toLowerCase(Locale.ROOT);
                if (!this.plugin.drops().catalog().setClass(args[2], clazz)) {
                    sender.sendMessage(TAG + " No existe ese item: " + itemName);
                    return;
                }
                sender.sendMessage(TAG + (clazz == null
                        ? " Item '" + itemName + "' liberado: lo usa cualquiera."
                        : " Item '" + itemName + "' reservado a la clase '" + clazz + "'."));
                if (clazz != null) {
                    sender.sendMessage(TAG + " Las copias ya repartidas no cambian. Reparte copias"
                            + " marcadas con /customcore item give " + itemName + " [jugador].");
                }
            }
            case "mod", "modificacion", "proteccion" -> {
                if (args.length < 5) {
                    sender.sendMessage(TAG + " Uso: /customcore item mod <nombre> <clave> <si|no>");
                    sender.sendMessage(TAG + " Claves: " + String.join(", ",
                            com.juanp.custommobs.drop.ItemCatalog.MOD_KEYS));
                    return;
                }
                String key = com.juanp.custommobs.drop.ItemCatalog.normalizeModKey(args[3]);
                if (key == null) {
                    sender.sendMessage(TAG + " Clave desconocida: " + args[3]
                            + ". Usa: " + String.join(", ",
                            com.juanp.custommobs.drop.ItemCatalog.MOD_KEYS));
                    return;
                }
                String value = args[4].toLowerCase(Locale.ROOT);
                boolean allowed = value.equals("si") || value.equals("sí")
                        || value.equals("true") || value.equals("1") || value.equals("on");
                boolean denied = value.equals("no") || value.equals("false")
                        || value.equals("0") || value.equals("off");
                if (!allowed && !denied) {
                    sender.sendMessage(TAG + " Valor invalido: usa 'si' o 'no'.");
                    return;
                }
                String itemName = args[2].toLowerCase(Locale.ROOT);
                if (!this.plugin.drops().catalog().setModification(args[2], key, allowed)) {
                    sender.sendMessage(TAG + " No existe ese item: " + itemName);
                    return;
                }
                sender.sendMessage(TAG + " Item '" + itemName + "': " + key
                        + (allowed ? " permitido." : " prohibido."));
                sender.sendMessage(TAG + " Las copias ya repartidas no cambian; reparte copias"
                        + " nuevas con /customcore item give " + itemName + " [jugador].");
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(TAG + " Uso: /customcore item give <nombre> [jugador]");
                    return;
                }
                Player target = this.targetPlayer(sender, args, 3);
                if (target == null) {
                    return;
                }
                // Sale del catalogo, asi que es una copia NUEVA: lleva la marca de faccion
                // si el objeto esta reservado. Es la via para repartir objetos bloqueados.
                var stored = this.plugin.drops().catalog().get(args[2]);
                if (stored.isEmpty()) {
                    sender.sendMessage(TAG + " No existe ese item: " + args[2].toLowerCase(Locale.ROOT));
                    return;
                }
                this.deliver(target, stored.get());
                sender.sendMessage(TAG + " Entregado '" + args[2].toLowerCase(Locale.ROOT)
                        + "' a " + target.getName() + ".");
            }
            case "remove" -> {
                if (args.length < 3 || !this.plugin.drops().catalog().remove(args[2])) {
                    sender.sendMessage(TAG + " Uso: /customcore item remove <nombre> (debe existir)");
                    return;
                }
                sender.sendMessage(TAG + " Item '" + args[2].toLowerCase(Locale.ROOT) + "' borrado del catalogo.");
            }
            case "faccion" -> {
                if (args.length < 4) {
                    sender.sendMessage(TAG + " Uso: /customcore item faccion <nombre> <faccion|nada>");
                    return;
                }
                String faction = args[3].equalsIgnoreCase("nada") || args[3].equals("-")
                        ? null : args[3].toLowerCase(Locale.ROOT);
                if (faction != null && !this.plugin.factions().known().contains(faction)) {
                    sender.sendMessage(TAG + " Esa faccion no existe. Conocidas: "
                            + String.join(", ", this.plugin.factions().known()));
                    return;
                }
                String itemName = args[2].toLowerCase(Locale.ROOT);
                if (!this.plugin.drops().catalog().setFaction(args[2], faction)) {
                    sender.sendMessage(TAG + " No existe ese item: " + itemName);
                    return;
                }
                sender.sendMessage(TAG + (faction == null
                        ? " Item '" + itemName + "' liberado: cualquiera puede usarlo."
                        : " Item '" + itemName + "' reservado a la faccion '" + faction + "'."));
                if (faction != null) {
                    // La marca solo la llevan las copias que salen del catalogo: las que ya
                    // estan repartidas por el mundo no cambian.
                    sender.sendMessage(TAG + " Las copias ya repartidas no cambian. Reparte copias"
                            + " marcadas con /customcore item give " + itemName + " [jugador].");
                }
            }
            default -> sender.sendMessage(usage);
        }
    }

    /** Efectos custom: listarlos o ver uno. */
    private void effects(CommandSender sender, String[] args) {
        var all = this.plugin.effects().effects();
        if (args.length >= 2 && !"list".equalsIgnoreCase(args[1])) {
            var spec = this.plugin.effects().effect(args[1]).orElse(null);
            if (spec == null) {
                sender.sendMessage(TAG + " No existe ese efecto. Miralos con /customcore effects list.");
                return;
            }
            sender.sendMessage(TAG + " " + spec.name() + " (" + spec.id() + ")");
            sender.sendMessage(" - " + spec.description());
            sender.sendMessage(" - Duracion: " + spec.duration() + "s | mecanica: "
                    + (spec.mechanic().isEmpty() ? "ninguna" : spec.mechanic())
                    + (spec.power() > 0.0D ? " | potencia: " + spec.power() : ""));
            return;
        }
        sender.sendMessage(TAG + " Efectos custom (" + all.size() + "):");
        for (var spec : all.values()) {
            sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &7" + spec.id() + " &8- &f"
                    + spec.name() + (spec.mechanic().isEmpty() ? "" : " &8[" + spec.mechanic() + "]")));
        }
    }

    /** Pociones: listarlas o repartirlas. */
    /**
     * Modelos de BetterModel: los cargados en el servidor y los que usan los mobs.
     *
     * <p>{@code model list} lista los modelos disponibles; {@code model list <mob>} los de un
     * mob; {@code model validate} avisa de los nombres que no existen en BetterModel.
     */
    private void model(CommandSender sender, String[] args) {
        var link = this.plugin.modelLink();
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            if (args.length >= 3) {
                this.modelOfMob(sender, args[2]);
                return;
            }
            if (!(link instanceof com.juanp.custommobs.model.BetterModelLink better)) {
                sender.sendMessage(TAG + " BetterModel no esta instalado: no hay modelos que listar.");
                this.modelSummary(sender);
                return;
            }
            var names = better.modelNames();
            sender.sendMessage(TAG + " Modelos de BetterModel (" + names.size() + "):");
            for (String name : names) {
                sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &7" + name));
            }
            this.modelSummary(sender);
            return;
        }
        if ("validate".equalsIgnoreCase(args[1]) || "validar".equalsIgnoreCase(args[1])) {
            this.validateModels(sender, link);
            return;
        }
        sender.sendMessage(TAG + " Uso: /customcore model list [mob] | model validate");
    }

    /** Cuantos mobs del catalogo llevan modelo y cuantos no. */
    private void modelSummary(CommandSender sender) {
        int withModel = 0;
        for (MobDefinition definition : this.plugin.registry().all()) {
            if (definition.model() != null && definition.model().active()) {
                withModel++;
            }
        }
        sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &8Mobs con modelo: &f" + withModel
                + " &8de &f" + this.plugin.registry().size()));
    }

    /** Modelo y animaciones de un mob concreto. */
    private void modelOfMob(CommandSender sender, String id) {
        MobDefinition definition = this.plugin.registry().get(id.toLowerCase(Locale.ROOT)).orElse(null);
        if (definition == null) {
            sender.sendMessage(TAG + " No existe el mob: " + id);
            return;
        }
        var spec = definition.model();
        if (spec == null || !spec.active()) {
            sender.sendMessage(TAG + " '" + definition.id() + "' no lleva modelo.");
            return;
        }
        sender.sendMessage(TAG + " '" + definition.id() + "': modelo '" + spec.name() + "'"
                + (spec.scale() != null ? " x" + spec.scale() : "")
                + (spec.seat() ? " con asiento propio" : ""));
        sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &8cuerpo: "
                + (spec.hideBody() ? "oculto" : "visible")
                + " &8| equipo: " + (spec.hideEquipment() ? "oculto" : "visible")
                + " &8| brillo: " + (spec.hideGlow() ? "oculto" : "visible")));
        if (spec.animations().isEmpty()) {
            sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &8animaciones: ninguna"));
            return;
        }
        for (var entry : spec.animations().entrySet()) {
            var animation = entry.getValue();
            sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &7" + entry.getKey().key()
                    + " &8-> &f" + animation.name()
                    + (animation.once() ? " &8(una vez)" : "")
                    + (animation.speed() != null ? " &8x" + animation.speed() : "")));
        }
    }

    /** Recorre el catalogo y avisa de los modelos que BetterModel no conoce. */
    private void validateModels(CommandSender sender, com.juanp.custommobs.model.ModelLink link) {
        int checked = 0;
        int wrong = 0;
        for (MobDefinition definition : this.plugin.registry().all()) {
            var spec = definition.model();
            if (spec == null || !spec.active()) {
                continue;
            }
            checked++;
            boolean exists = link instanceof com.juanp.custommobs.model.BetterModelLink better
                    && better.hasModelName(spec.name());
            if (!exists) {
                wrong++;
                sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &c" + definition.id()
                        + " &8-> &f" + spec.name() + " &8(no existe)"));
            }
            if (definition.disguise() != null && definition.disguise().enabled()) {
                sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &e" + definition.id()
                        + " &8tiene 'disguise' y 'model': gana el disfraz"));
                continue;
            }
            // El nombre de hueso compone tags: 'hitbox' NO es un tag, 'b_hitbox' si. Es el
            // error mas facil de cometer en Blockbench y deja el mob sin caja de colision.
            if (exists && link instanceof com.juanp.custommobs.model.BetterModelLink better) {
                var report = better.inspectBones(spec.name());
                if (report == null) {
                    sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &8" + definition.id()
                            + " &7modelo no cargado aun; se revisa al aplicarse"));
                    continue;
                }
                if (!report.hitBox() && spec.hideBody()) {
                    wrong++;
                    sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &c" + definition.id()
                            + " &8-> &f" + spec.name() + " &8sin hitbox: renombra un hueso a"
                            + " 'b_algo' (no 'hitbox'). Cuerpo visible."));
                }
                if (!report.nametag() && report.tagProbe() && spec.hideBody()) {
                    // Con un hueso 'tag' la placa la pinta el nombre vanilla de la entidad, y
                    // sobre un cuerpo oculto eso parpadea. Lo limpio es 'mtag'.
                    sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &e" + definition.id()
                            + " &8-> &f" + spec.name() + " &8tiene hueso 'tag': la placa es la"
                            + " vanilla y con el cuerpo oculto parpadea. Renombralo a 'mtag_algo'."));
                }
                if (!report.seat() && spec.seat() && definition.mountable()) {
                    sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &e" + definition.id()
                            + " &8-> &f" + spec.name() + " &8sin asiento: renombra el hueso a"
                            + " 'p_algo'."));
                }
            }
        }
        if (checked == 0) {
            sender.sendMessage(TAG + " Ningun mob declara modelo.");
            return;
        }
        sender.sendMessage(TAG + (wrong == 0
                ? com.juanp.custommobs.mob.Texts.color(" &aTodo correcto: " + checked + " modelo(s).")
                : com.juanp.custommobs.mob.Texts.color(" &c" + wrong + " de " + checked
                        + " modelo(s) no existen.")));
    }

    private void potion(CommandSender sender, String[] args) {
        var all = this.plugin.effects().potions();
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(TAG + " Pociones (" + all.size() + "):");
            for (var spec : all.values()) {
                sender.sendMessage(com.juanp.custommobs.mob.Texts.color(" &7" + spec.id()
                        + " &8- &f" + spec.name() + " &8(efecto: " + spec.effectId() + ")"));
            }
            return;
        }
        if (!"give".equalsIgnoreCase(args[1]) || args.length < 3) {
            sender.sendMessage(TAG + " Uso: /customcore potion list | potion give <id> [jugador]");
            return;
        }
        var spec = this.plugin.effects().potion(args[2]).orElse(null);
        if (spec == null) {
            sender.sendMessage(TAG + " No existe esa pocion: " + args[2]);
            return;
        }
        Player target = this.targetPlayer(sender, args, 3);
        if (target == null) {
            return;
        }
        this.deliver(target, this.plugin.effects().item(spec));
        sender.sendMessage(TAG + " Pocion '" + spec.id() + "' entregada a " + target.getName() + ".");
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
                sender.sendMessage("... (afina con /customcore enchants <texto>)");
                break;
            }
        }
        sender.sendMessage(TAG + " Mostrados: " + shown + ". Usa la clave completa en 'enchants:' del yml.");
    }

    private void help(CommandSender sender) {
        sender.sendMessage(TAG + " /customcore reload | list | give <id> [jugador] | spawn <id> [mundo x y z]");
        sender.sendMessage(TAG + "           | remove [radio] | active | kill <id> | enchants [filtro]");
        sender.sendMessage(TAG + "           | cuota [jugador] | cuota recontar <jugador> | cuota retirar <jugador>");
        sender.sendMessage(TAG + "           | item save <nombre> | item list | item remove <nombre>  (catalogo de items)");
        sender.sendMessage(TAG + "           | color <color|nada> | glow <color|nada>  (tus player mobs)");
        sender.sendMessage(TAG + "           | spawner list | spawner remove <id> | spawner removeall [mob] | spawner reload");
        sender.sendMessage(TAG + "           | book [jugador]  (libro de inspeccion)");
        sender.sendMessage(TAG + "           | upgrade list | upgrade give <id> [jugador] | upgrade reload");
        sender.sendMessage(TAG + "           | model list [mob] | model validate  (modelos de BetterModel)");
        sender.sendMessage(TAG + "           | triggers <mob>  (por que via se activa cada skill)");
        sender.sendMessage(TAG + "           | despedir <todos|id|id#marca>  (tus mobs; cualquiera puede usarlo)");
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
                            "enchants", "cuota", "item", "color", "glow", "spawner", "book", "upgrade",
                            "despedir", "faction", "class", "effects", "potion", "model", "triggers")
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
        // 'despedir' es del jugador: se le ofrecen SUS mobs, con una marca corta por mob
        // cuando hay varios de la misma definicion. Antes salia el id a secas, y despedir
        // uno se llevaba por delante todos los de esa definicion.
        if (args.length == 2 && (args[0].equalsIgnoreCase("despedir") || args[0].equalsIgnoreCase("dismiss"))
                && sender instanceof Player owner) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("todos".startsWith(prefix)) {
                out.add("todos");
            }
            java.util.Map<String, java.util.List<UUID>> byDefinition = new java.util.LinkedHashMap<>();
            for (UUID linkId : this.plugin.mobs().playerMobs().linksOf(owner.getUniqueId())) {
                this.plugin.mobs().playerMobs().byLink(linkId).ifPresent(link ->
                        byDefinition.computeIfAbsent(link.definitionId(), key -> new ArrayList<>())
                                .add(linkId));
            }
            for (var entry : byDefinition.entrySet()) {
                String id = entry.getKey();
                if (entry.getValue().size() == 1) {
                    if (id.startsWith(prefix)) {
                        out.add(id);
                    }
                    continue;
                }
                for (UUID linkId : entry.getValue()) {
                    String handle = id + "#" + com.juanp.custommobs.mob.MobService.shortId(linkId);
                    if (handle.startsWith(prefix)) {
                        out.add(handle);
                    }
                }
            }
            return out;
        }
        // De aqui para abajo, todo es administracion: sin permiso, no se completa nada.
        if (!admin) {
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("triggers")
                || args[0].equalsIgnoreCase("trigger"))) {
            for (MobDefinition definition : this.plugin.registry().all()) {
                if (definition.hasSkills()
                        && definition.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(definition.id());
                }
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("model") || args[0].equalsIgnoreCase("modelo"))) {
            for (String option : List.of("list", "validate")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("model") || args[0].equalsIgnoreCase("modelo"))
                && args[1].equalsIgnoreCase("list")) {
            for (MobDefinition definition : this.plugin.registry().all()) {
                var spec = definition.model();
                if (spec != null && spec.active() && definition.id().startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(definition.id());
                }
            }
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
        if (args.length == 2 && (args[0].equalsIgnoreCase("class") || args[0].equalsIgnoreCase("clase"))) {
            for (String option : List.of("set", "get", "clear", "list")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("class") || args[0].equalsIgnoreCase("clase"))
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("get")
                || args[1].equalsIgnoreCase("clear"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("class") || args[0].equalsIgnoreCase("clase"))
                && args[1].equalsIgnoreCase("set")) {
            for (String clazz : this.plugin.classes().known()) {
                if (clazz.startsWith(args[3].toLowerCase(Locale.ROOT))) {
                    out.add(clazz);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("effects")) {
            if ("list".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add("list");
            }
            for (String id : this.plugin.effects().effects().keySet()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("potion")) {
            for (String option : List.of("list", "give")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("potion")
                && args[1].equalsIgnoreCase("give")) {
            for (String id : this.plugin.effects().potions().keySet()) {
                if (id.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("faction")) {
            for (String option : List.of("set", "get", "clear", "list")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("faction")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("get")
                || args[1].equalsIgnoreCase("clear"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("faction") && args[1].equalsIgnoreCase("set")) {
            for (String faction : this.plugin.factions().known()) {
                if (faction.startsWith(args[3].toLowerCase(Locale.ROOT))) {
                    out.add(faction);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            for (String option : List.of("save", "list", "remove", "faccion", "clase", "give")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
            return out;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("clase")) {
            if ("nada".startsWith(args[3].toLowerCase(Locale.ROOT))) {
                out.add("nada");
            }
            for (String clazz : this.plugin.classes().known()) {
                if (clazz.startsWith(args[3].toLowerCase(Locale.ROOT))) {
                    out.add(clazz);
                }
            }
            return out;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("item") && args[1].equalsIgnoreCase("faccion")) {
            if ("nada".startsWith(args[3].toLowerCase(Locale.ROOT))) {
                out.add("nada");
            }
            for (String faction : this.plugin.factions().known()) {
                if (faction.startsWith(args[3].toLowerCase(Locale.ROOT))) {
                    out.add(faction);
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
