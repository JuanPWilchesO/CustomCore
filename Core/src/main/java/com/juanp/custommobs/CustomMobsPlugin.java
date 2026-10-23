package com.juanp.custommobs;

import com.juanp.custommobs.anchor.AnchorChunkService;
import com.juanp.custommobs.book.MobBookService;
import com.juanp.custommobs.upgrade.MobItemListener;
import com.juanp.custommobs.upgrade.UpgradeRegistry;
import com.juanp.custommobs.upgrade.UpgradeService;
import com.juanp.custommobs.api.CustomMobsApi;
import com.juanp.custommobs.combat.AggroListener;
import com.juanp.custommobs.combat.DamageImmunityListener;
import com.juanp.custommobs.combat.FriendlyFireListener;
import com.juanp.custommobs.combat.VillagePeaceListener;
import com.juanp.custommobs.combat.TargetListener;
import com.juanp.custommobs.audio.AmbientTask;
import com.juanp.custommobs.audio.SoundListener;
import com.juanp.custommobs.audio.SoundService;
import com.juanp.custommobs.combat.TargetingTask;
import com.juanp.custommobs.command.CustomMobsCommand;
import com.juanp.custommobs.config.PluginConfig;
import com.juanp.custommobs.craft.CraftService;
import com.juanp.custommobs.craft.EggListener;
import com.juanp.custommobs.disguise.DisguiseLink;
import com.juanp.custommobs.disguise.DisguiseLinkResolver;
import com.juanp.custommobs.docs.DocsInstaller;
import com.juanp.custommobs.drop.DropListener;
import com.juanp.custommobs.drop.DropService;
import com.juanp.custommobs.item.ItemFactionLock;
import com.juanp.custommobs.item.ItemModificationLock;
import com.juanp.custommobs.item.ArrowPickupListener;
import com.juanp.custommobs.economy.EconomyLink;
import com.juanp.custommobs.faction.FactionBook;
import com.juanp.custommobs.faction.PlayerFactions;
import com.juanp.custommobs.glow.FactionGlowService;
import com.juanp.custommobs.skill.ChainService;
import com.juanp.custommobs.group.GroupLink;
import com.juanp.custommobs.group.GroupLinkResolver;
import com.juanp.custommobs.impl.CustomMobsApiImpl;
import com.juanp.custommobs.message.MotdListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.juanp.custommobs.mob.DaylightListener;
import com.juanp.custommobs.mob.TransformBlockListener;
import com.juanp.custommobs.mob.MobRegistry;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.MountEffectTask;
import com.juanp.custommobs.mob.MountFrostTask;
import com.juanp.custommobs.mob.NaturalSpawnTask;
import com.juanp.custommobs.recall.RecallService;
import com.juanp.custommobs.region.RegionGate;
import com.juanp.custommobs.skill.SkillInteractListener;
import com.juanp.custommobs.skill.ProjectileListener;
import com.juanp.custommobs.cc.CcListener;
import com.juanp.custommobs.ui.StatusListener;
import com.juanp.custommobs.ui.StatusService;
import com.juanp.custommobs.cc.CcService;
import com.juanp.custommobs.skill.ProjectileService;
import com.juanp.custommobs.skill.SkillCatalog;
import com.juanp.custommobs.skill.SkillService;
import com.juanp.custommobs.spawner.SpawnerListener;
import com.juanp.custommobs.spawner.SpawnerRegistry;
import com.juanp.custommobs.style.StyleRegistry;
import com.juanp.custommobs.style.StyleService;
import com.juanp.custommobs.team.FactionTeams;
import com.juanp.custommobs.team.TeamFactionListener;
import com.juanp.custommobs.team.TeamLink;
import com.juanp.custommobs.team.TeamLinkResolver;
import org.bukkit.command.PluginCommand;

import java.io.File;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CustomMobs: mobs definidos por YAML, invocables por crafteo, vinculados al plugin de Teams.
 */
public final class CustomMobsPlugin extends JavaPlugin {

    /**
     * Firma del autor en la consola. Va hardcodeada a proposito: es la firma del proyecto,
     * no un ajuste de configuracion.
     *
     * <p>Los colores son los de la bandera palestina —negro, blanco, rojo y verde— en
     * codigos ANSI, que es lo unico que un log puede pintar. Los codigos '&'/'\u00a7' de
     * Minecraft solo los renderiza el cliente, nunca el servidor: en un log saldrian como
     * texto literal.
     */
    private static final Component SIGNATURE = Component.text()
            .append(Component.text("Plugin ", NamedTextColor.BLACK))
            .append(Component.text("by ", NamedTextColor.WHITE))
            .append(Component.text("AP2P ", NamedTextColor.RED))
            .append(Component.text("Project", NamedTextColor.GREEN))
            .build();

    private PluginConfig config;
    private SkillCatalog skillCatalog;
    private MobRegistry registry;
    private MobService mobService;
    private com.juanp.custommobs.effect.SigiloService sigiloService;
    private com.juanp.custommobs.effect.SigiloPacketListener sigiloPacketListener;
    private com.juanp.custommobs.effect.EffectService effectService;
    private TeamLink teamLink;
    private GroupLink groupLink;
    private FactionBook factions;
    private PlayerFactions playerFactions;
    private com.juanp.custommobs.classes.PlayerClasses playerClasses;
    private com.juanp.custommobs.classes.ClassBook classBook;
    private EconomyLink economy;
    private ChainService chains;
    private FactionGlowService glow;
    private FactionTeams factionTeams;
    private DisguiseLink disguiseLink;
    private DropService dropService;
    private ItemFactionLock itemFactionLock;
    private StyleService styleService;
    private SpawnerRegistry spawners;
    private CraftService craftService;
    private SoundService soundService;
    private AmbientTask ambientTask;
    private SkillService skillService;
    private ProjectileService projectileService;
    private CcService cc;
    private StatusService status;
    private com.juanp.custommobs.effect.EffectListener effectListener;
    private TargetingTask targetingTask;
    private RecallService recallService;
    private AnchorChunkService anchorChunks;
    private MountEffectTask mountEffects;
    private MountFrostTask mountFrost;
    private NaturalSpawnTask naturalSpawn;
    private UpgradeRegistry upgrades;
    private UpgradeService upgradeService;
    private MobBookService book;
    private CustomMobsApiImpl api;

    /**
     * Migracion de identidad: la carpeta de datos paso de 'CustomMobs' a 'CustomCore'. Si existe
     * la vieja y la nueva esta vacia, se COPIA su contenido —nunca se mueve— para no perder
     * configs, mobs ni objetos. La carpeta vieja se queda donde esta.
     */
    private void migrateLegacyFolder() {
        File current = this.getDataFolder();
        File legacy = new File(current.getParentFile(), "CustomMobs");
        if (current.equals(legacy) || !legacy.isDirectory()
                || new File(current, "config.yml").exists()) {
            return;
        }
        try {
            java.nio.file.Path from = legacy.toPath();
            java.nio.file.Path to = current.toPath();
            java.nio.file.Files.walk(from).forEach(source -> {
                try {
                    java.nio.file.Path target = to.resolve(from.relativize(source));
                    if (java.nio.file.Files.isDirectory(source)) {
                        java.nio.file.Files.createDirectories(target);
                    } else {
                        java.nio.file.Files.createDirectories(target.getParent());
                        java.nio.file.Files.copy(source, target,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (java.io.IOException ignored) {
                    // Un archivo que no se copie no debe impedir el arranque.
                }
            });
            this.getLogger().info("Datos migrados desde 'CustomMobs' (la carpeta vieja se conserva).");
        } catch (java.io.IOException ex) {
            this.getLogger().warning("No se pudo migrar la carpeta antigua: " + ex.getMessage());
        }
    }

    @Override
    public void onEnable() {
        this.migrateLegacyFolder();
        this.saveDefaultConfig();
        this.config = PluginConfig.load(this.getConfig());

        // La documentacion y los tutoriales viajan dentro del jar; se dejan tambien en
        // disco para que un admin pueda leerlos sin descomprimir nada.
        int docs = new DocsInstaller(this).install();
        if (docs > 0) {
            this.getLogger().info("Documentacion y tutoriales instalados: " + docs
                    + " archivo(s) en docs/ y tutorials/.");
        }

        // La firma va lo primero que escribe el plugin, para que se vea al arrancar.
        this.getComponentLogger().info(SIGNATURE);

        // Las skills reutilizables van antes que los mobs: un mob puede referenciarlas
        // con 'ref:' y necesita que ya esten cargadas.
        this.skillCatalog = new SkillCatalog(this);
        this.skillCatalog.reload();
        this.registry = new MobRegistry(this);
        this.registry.reload();

        if (!new File(this.getDataFolder(), "factions.yml").exists()) {
            this.saveResource("factions.yml", false);
        }
        this.factions = new FactionBook();
        this.factions.reload(this);
        this.factionTeams = new FactionTeams(this);
        this.playerFactions = new PlayerFactions(this);
        this.playerFactions.load();
        this.classBook = new com.juanp.custommobs.classes.ClassBook();
        this.classBook.reload(this);
        this.playerClasses = new com.juanp.custommobs.classes.PlayerClasses(this);
        this.playerClasses.load();

        this.teamLink = TeamLinkResolver.resolve(this);
        this.groupLink = GroupLinkResolver.resolve(this);
        this.disguiseLink = DisguiseLinkResolver.resolve(this);
        this.spawners = new SpawnerRegistry(this);
        this.spawners.load();
        this.mobService = new MobService(this, this.config, this.registry, this.teamLink, this.factions,
                this.disguiseLink, this.groupLink, this.spawners);
        this.mobService.probeGoals();

        this.soundService = new SoundService(this.mobService);
        this.dropService = new DropService(this);

        StyleRegistry styleRegistry = new StyleRegistry(this);
        styleRegistry.load();
        this.styleService = new StyleService(this.mobService, styleRegistry);
        this.mobService.attachStyles(this.styleService);

        // Efectos y pociones antes de las recetas: el bloque 'receta:' de potions.yml se
        // lee del catalogo de pociones. Sin esto se registraba en silencio como inexistente.
        this.sigiloService = new com.juanp.custommobs.effect.SigiloService(this);
        this.effectService = new com.juanp.custommobs.effect.EffectService(this, this.sigiloService);
        this.effectService.reload();

        this.craftService = new CraftService(this, this.registry, this.mobService.keys());
        this.craftService.registerAll();

        // Items de mejora y libro de inspeccion: se crean antes de los listeners que los usan.
        this.upgrades = new UpgradeRegistry(this);
        this.upgrades.reload();
        this.upgradeService = new UpgradeService(this, this.upgrades, this.mobService.keys());
        this.book = new MobBookService(this, this.mobService, this.mobService.keys());
        this.upgradeService.registerRecipes(this.craftService);

        // Se crea aqui, y no mas abajo, porque el listener del clic derecho lo necesita.
        this.skillService = new SkillService(this.mobService);
        this.projectileService = new ProjectileService(this);
        this.status = new StatusService(this);
        this.cc = new CcService(this);
        // La economia, por reflexion: sin EssentialsX los pagos no funcionan y se avisa.
        this.economy = EconomyLink.create(this);
        this.chains = new ChainService(this, this.mobService, this.economy);

        this.api = new CustomMobsApiImpl(this.mobService, this.registry);
        this.getServer().getServicesManager().register(CustomMobsApi.class, this.api, this, ServicePriority.Normal);

        PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(this.mobService, this);
        pluginManager.registerEvents(new EggListener(this, this.craftService, this.mobService), this);
        pluginManager.registerEvents(new com.juanp.custommobs.craft.RecipeSyncListener(this.craftService), this);
        pluginManager.registerEvents(new TargetListener(this.mobService), this);
        pluginManager.registerEvents(new AggroListener(this.mobService), this);
        pluginManager.registerEvents(new FriendlyFireListener(this.mobService), this);
        pluginManager.registerEvents(new VillagePeaceListener(this.mobService), this);
        pluginManager.registerEvents(new DamageImmunityListener(this.mobService), this);
        pluginManager.registerEvents(new SoundListener(this.mobService, this.soundService), this);
        pluginManager.registerEvents(new DaylightListener(this.mobService), this);
        pluginManager.registerEvents(new TransformBlockListener(this.mobService), this);
        pluginManager.registerEvents(new SpawnerListener(this.mobService), this);
        pluginManager.registerEvents(new DropListener(this.mobService, this.dropService), this);
        this.itemFactionLock = new ItemFactionLock(this.mobService, this.dropService.catalog());
        pluginManager.registerEvents(this.itemFactionLock, this);
        pluginManager.registerEvents(new ItemModificationLock(this.dropService.catalog()), this);
        pluginManager.registerEvents(new ArrowPickupListener(this), this);
        pluginManager.registerEvents(new MotdListener(this), this);
        // Con Teams instalado, ademas no se deja entrar a un team de otra faccion. La clase toca
        // la API de Teams, asi que solo se carga cuando esta: sin el, ni se toca.
        if (pluginManager.getPlugin("Teams") != null) {
            pluginManager.registerEvents(new TeamFactionListener(this), this);
        }
        pluginManager.registerEvents(new SkillInteractListener(this.craftService, this.mobService, this.skillService), this);
        pluginManager.registerEvents(new MobItemListener(this, this.mobService, this.upgradeService, this.book), this);

        // Efectos y pociones: mecanicas y la tarea que las mantiene. El catalogo ya se
        // cargo antes de registrar las recetas.
        this.effectListener = new com.juanp.custommobs.effect.EffectListener(this, this.effectService);
        pluginManager.registerEvents(this.effectListener, this);
        pluginManager.registerEvents(this.sigiloService, this);
        // Limpia el flag de sprint de los ocultos: sin el, el cliente dibuja particulas.
        try {
            this.sigiloPacketListener =
                    new com.juanp.custommobs.effect.SigiloPacketListener(this.sigiloService);
            com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
                    .registerListener(this.sigiloPacketListener);
        } catch (Throwable ignored) {
            // Sin PacketEvents el sigilo sigue funcionando; solo se pierde el filtro.
        }
        // Cada tick: la congelacion y el paso helado necesitan ir al dia.
        this.getServer().getScheduler().runTaskTimer(this, this.effectListener::tick, 1L, 1L);
        // Skills al impactar: se arman al disparar y se resuelven al tocar.
        pluginManager.registerEvents(new ProjectileListener(this, this.projectileService), this);
        pluginManager.registerEvents(new CcListener(this.cc, this.dropService.catalog()), this);
        pluginManager.registerEvents(new StatusListener(this.status), this);
        this.getServer().getScheduler().runTaskTimer(this, this.projectileService::tick, 20L, 20L);

        PluginCommand command = this.getCommand("customcore");
        if (command != null) {
            CustomMobsCommand executor = new CustomMobsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        this.targetingTask = new TargetingTask(this.mobService, this.config);
        long interval = this.config.taskIntervalTicks();
        this.targetingTask.runTaskTimer(this, interval, interval);

        // La ventana de abandono vigila cada segundo: es la que decide cuando un mob
        // anclado a su dueno ha quedado atras y hay que retirarlo.
        this.recallService = new RecallService(this, this.mobService, this.config);
        this.recallService.runTaskTimer(this, 20L, 20L);

        // Los mobs anclados a un bloque mantienen cargada su zona. Se revisa cada 5 s:
        // no hace falta correr mas a menudo y asi el coste es despreciable.
        this.anchorChunks = new AnchorChunkService(this, this.mobService, this.config);
        this.anchorChunks.runTaskTimer(this, 100L, 100L);

        // Efectos constantes de las monturas: al mob, al jinete o a quien pase cerca.
        this.mountEffects = new MountEffectTask(this, this.mobService);
        this.mountEffects.runTaskTimer(this, 20L, 20L);

        // Paso helado: cada tick, porque la montura se mueve y cada tick cuenta para
        // que el hielo este puesto antes de que llegue.
        this.mountFrost = new MountFrostTask(this, this.mobService);
        this.mountFrost.runTaskTimer(this, 1L, 1L);

        // Aparicion aleatoria de mobs de servidor, si alguno la pide en su yml.
        // El cerco de regiones del spawneo aleatorio: WorldGuard si esta, abierto si no.
        this.naturalSpawn = new NaturalSpawnTask(this, this.mobService, this.registry,
                RegionGate.create(this));
        long spawnInterval = this.config.spawnIntervalSeconds() * 20L;
        this.naturalSpawn.runTaskTimer(this, spawnInterval, spawnInterval);

        this.ambientTask = new AmbientTask(this.soundService);
        this.ambientTask.runTaskTimer(this, 20L, 20L);

        // Brillo de facciones por espectador: cada segundo basta, y solo manda lo que cambia.
        this.glow = new FactionGlowService(this, this.mobService);
        this.glow.runTaskTimer(this, 20L, 20L);

        // Aviso en la action bar al mirar un mob custom: Enemigo / Aliado / Neutral.
        if (this.getConfig().getBoolean("faction-info.action-bar", true)) {
            new com.juanp.custommobs.combat.FactionHud(this.mobService)
                    .runTaskTimer(this, 20L, 10L);
        }

        // Diagnostico opcional: traza de los paquetes enviados a cada jugador, volcada al
        // desconectarse. Es la unica forma de cazar un "Error de protocolo de red" del cliente,
        // porque ahi el servidor no ve nada.
        if (this.getConfig().getBoolean("debug-packets", false)) {
            try {
                com.juanp.custommobs.glow.PacketTrace trace =
                        new com.juanp.custommobs.glow.PacketTrace(this);
                com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
                        .registerListener(trace);
                pluginManager.registerEvents(new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                        trace.dump(event.getPlayer());
                    }
                }, this);
                this.getLogger().info("Traza de paquetes ACTIVA (debug-packets).");
            } catch (Throwable throwable) {
                this.getLogger().warning("No se pudo activar la traza de paquetes: "
                        + throwable.getMessage());
            }
        }

        long skillInterval = this.config.skillIntervalTicks();
        this.skillService.runTaskTimer(this, skillInterval, skillInterval);

        this.mobService.restoreAll();
        this.mobService.resumeSpawners();

        this.getLogger().info("CustomCore habilitado. Definiciones: " + this.registry.size()
                + " | facciones: " + this.factions.size()
                + " | disfraces: " + this.disguiseLink.name()
                + " | teams: " + this.teamLink.name()
                + " | IA MobGoals: " + this.mobService.usesGoals()
                + " | jugadores: " + this.config.playerTargetMode()
                + " | aggro: " + (this.config.aggroDurationMillis() / 1000L) + "s"
                + " | skills cada " + skillInterval + "t"
                + " | grupos: " + this.groupLink.name()
                + " | abandono: " + (this.config.recallEnabled()
                        ? this.config.recallMobSeconds() + "s/" + this.config.recallChunkSeconds() + "s"
                        : "off")
                + " | cupo dueno: " + (this.config.defaultPlayerMobs() > 0
                        ? String.valueOf(this.config.defaultPlayerMobs()) : "sin limite")
                + " | cupo bloque: " + (this.config.defaultPointMobs() > 0
                        ? String.valueOf(this.config.defaultPointMobs()) : "sin limite")
                + " | zona fija: r" + this.config.pointChunkRadius());
    }

    @Override
    public void onDisable() {
        if (this.sigiloService != null) {
            this.sigiloService.showAll();
        }
        if (this.sigiloPacketListener != null) {
            try {
                com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
                        .unregisterListener(this.sigiloPacketListener);
            } catch (Throwable ignored) {
                // PacketEvents puede haber desaparecido antes que este plugin.
            }
        }
        if (this.mobService != null) {
            this.mobService.playerMobs().save();
        }
        if (this.styleService != null) {
            this.styleService.registry().save();
        }
        if (this.targetingTask != null) {
            this.targetingTask.cancel();
        }
        if (this.recallService != null) {
            this.recallService.cancel();
            // Suelta los tickets de chunk antes de que el mundo se guarde.
            this.recallService.shutdown();
        }
        if (this.anchorChunks != null) {
            this.anchorChunks.cancel();
            this.anchorChunks.shutdown();
        }
        if (this.mountEffects != null) {
            this.mountEffects.cancel();
        }
        if (this.mountFrost != null) {
            this.mountFrost.cancel();
        }
        if (this.naturalSpawn != null) {
            this.naturalSpawn.cancel();
        }
        if (this.ambientTask != null) {
            this.ambientTask.cancel();
        }
        if (this.status != null) {
            this.status.shutdown();
        }
        if (this.cc != null) {
            this.cc.releaseAll();
        }
        if (this.craftService != null) {
            this.craftService.unregisterAll();
        }
        if (this.spawners != null) {
            this.spawners.save();
        }
        if (this.mobService != null) {
            this.mobService.clear();
        }
        this.getServer().getServicesManager().unregisterAll(this);
    }

    /** Recarga config, catalogo y recetas. */
    public void reloadAll() {
        this.reloadConfig();
        this.config = PluginConfig.load(this.getConfig());
        if (this.skillCatalog != null) {
            this.skillCatalog.reload();
        }
        this.registry.reload();
        this.factions.reload(this);
        // Primero efectos y pociones: las recetas de potions.yml salen de ese catalogo.
        if (this.effectService != null) {
            this.effectService.reload();
        }
        this.craftService.registerAll();
        if (this.upgrades != null) {
            this.upgrades.reload();
            this.upgradeService.registerRecipes(this.craftService);
        }
        if (this.mobService != null) {
            this.mobService.playerMobs().load();
        }
    }

    /**
     * El jar del plugin. Sirve para leer los recursos que trae dentro (los ejemplos
     * de {@code mobs/}), porque {@code getFile()} es protected en JavaPlugin.
     */
    public File jarFile() {
        return this.getFile();
    }

    public PluginConfig config() {
        return this.config;
    }

    public MobRegistry registry() {
        return this.registry;
    }

    /** Skills reutilizables de {@code skills/}. */
    public SkillCatalog skillCatalog() {
        return this.skillCatalog;
    }

    /** Servicio que ejecuta las skills, tambien las que dispara un item. */
    public SkillService skillService() {
        return this.skillService;
    }

    /** Skills de impacto de los proyectiles. */
    public ProjectileService projectiles() {
        return this.projectileService;
    }

    /** Control de masas: aturdimiento, raiz, silencio, desarme, escudo e inmunidad. */
    public CcService cc() {
        return this.cc;
    }

    /** Avisos de estado en pantalla (boss bars y titulos). */
    public StatusService status() {
        return this.status;
    }

    /** Uso de items: cooldowns, restricciones y catalogo. */
    public com.juanp.custommobs.effect.EffectListener effectListener() {
        return this.effectListener;
    }

    public MobService mobs() {
        return this.mobService;
    }

    /** Restricciones de faccion/clase de los objetos; se reutilizan en el uso temprano. */
    public ItemFactionLock itemLock() {
        return this.itemFactionLock;
    }

    /** Efectos y pociones. */
    public com.juanp.custommobs.effect.EffectService effects() {
        return this.effectService;
    }

    /** Sigilo: invisibilidad sin sacar la entidad del cliente (conserva colision). */
    public com.juanp.custommobs.effect.SigiloService sigilo() {
        return this.sigiloService;
    }

    public DropService drops() {
        return this.dropService;
    }

    public StyleService styles() {
        return this.styleService;
    }

    public CraftService craft() {
        return this.craftService;
    }

    public TeamLink teamLink() {
        return this.teamLink;
    }

    public FactionBook factions() {
        return this.factions;
    }

    /** Conversaciones de las cadenas de interaccion. */
    public ChainService chains() {
        return this.chains;
    }

    /** Brillo de los jugadores segun su faccion, para cada quien. */
    public FactionGlowService glow() {
        return this.glow;
    }

    /** Las reglas que mantienen Teams y las facciones de acuerdo. */
    public FactionTeams factionTeams() {
        return this.factionTeams;
    }

    /** Cobrador de dinero, enganchado a EssentialsX si esta. */
    public EconomyLink economy() {
        return this.economy;
    }

    /** Facciones asignadas a jugadores, para las reglas de ataque por faccion. */
    /** Clases: catalogo y asignaciones. */
    public com.juanp.custommobs.classes.ClassBook classes() {
        return this.classBook;
    }

    public com.juanp.custommobs.classes.PlayerClasses playerClasses() {
        return this.playerClasses;
    }

    public PlayerFactions playerFactions() {
        return this.playerFactions;
    }

    public DisguiseLink disguiseLink() {
        return this.disguiseLink;
    }

    public SpawnerRegistry spawners() {
        return this.spawners;
    }

    /** Catalogo de items de mejora. */
    public UpgradeRegistry upgrades() {
        return this.upgrades;
    }

    /** Construccion y aplicacion de los items de mejora. */
    public UpgradeService upgradeService() {
        return this.upgradeService;
    }

    /** Libro de inspeccion. */
    public MobBookService book() {
        return this.book;
    }

    public CustomMobsApi api() {
        return this.api;
    }
}
