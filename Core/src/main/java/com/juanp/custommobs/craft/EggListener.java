package com.juanp.custommobs.craft;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.PlayerMobRegistry;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El huevo custom como ficha de un mob.
 *
 * <p>Un huevo recien crafteado no esta ligado a nada: al colocarlo nace un mob y el huevo
 * queda ligado a el. Desde entonces el huevo <b>no se gasta</b> — representa a ese mob,
 * sirve para recogerlo y lo vuelve a desplegar.
 *
 * <p>Si el mob muere, el vinculo se borra y el huevo queda inerte: apunta a la nada, asi que
 * colocarlo no hace nada. No hay que ir a buscarlo por el mundo para desactivarlo.
 */
public final class EggListener implements Listener {

    private final CustomMobsPlugin plugin;
    private final CraftService craft;
    private final MobService service;

    /**
     * Jugadores que acaban de recoger un mob. El mismo clic derecho llega tambien como
     * RIGHT_CLICK_AIR; sin esta marca, el huevo recogia el mob y lo volvia a soltar en el
     * acto, y parecia que el mob solo venia hacia el jugador.
     */
    private final Set<UUID> justCollected = ConcurrentHashMap.newKeySet();

    public EggListener(CustomMobsPlugin plugin, CraftService craft, MobService service) {
        this.plugin = plugin;
        this.craft = craft;
        this.service = service;
    }

    /**
     * Craftear un huevo (o una mejora) tampoco vale en un mundo excluido: si no, se podria
     * fabricar aqui lo que luego no se puede usar, y el inventario se llena de objetos
     * muertos.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || this.service.worldEnabled(player.getWorld())) {
            return;
        }
        if (this.craft.isCustomResult(event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendMessage(Texts.color("&cEl plugin no funciona en este mundo."));
        }
    }

    /** Colocar el huevo: despliega el mob, o lo vuelve a traer si estaba guardado. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<MobDefinition> definition = this.craft.definitionOf(item);
        if (definition.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        // El mismo clic sobre un mob puede llegar TAMBIEN como RIGHT_CLICK_AIR. Si el
        // jugador esta apuntando a un mob nuestro, el clic es para ESE mob y lo maneja
        // onInteractEntity: sin esto, el huevo desplegaria un segundo mob a sus pies y
        // pareceria que nunca recoge.
        boolean collected = this.justCollected.contains(player.getUniqueId());
        boolean aiming = this.aimingAtCustomMob(player);
        if (collected || aiming) {
            this.debug("Clic ignorado (" + action + "): "
                    + (collected ? "acaba de recoger" : "apunta a un mob custom") + ".");
            return;
        }

        event.setCancelled(true);
        // Los mundos excluidos valen para TODO: tampoco se invoca desde el huevo. El
        // chequeo de 'spawn' llegaba tarde y con un mensaje enganoso; aqui se dice claro.
        if (!this.service.worldEnabled(player.getWorld())) {
            player.sendMessage(Texts.color("&cEl plugin no funciona en este mundo: no puedes invocar mobs aqui."));
            return;
        }
        if (!player.hasPermission(this.service.config().playerPermission())) {
            player.sendMessage(Texts.color("&cNo tienes permiso para usar los huevos de CustomMobs."));
            return;
        }

        Optional<UUID> bound = this.craft.linkOf(item);
        if (bound.isPresent()) {
            PlayerMobRegistry.MobLink link = this.service.playerMobs().byLink(bound.get()).orElse(null);
            if (link == null) {
                player.sendMessage(Texts.color("&cEste huevo ya no sirve: el mob que representaba ya no existe."));
                return;
            }
            if (link.deployed()) {
                player.sendMessage(Texts.color("&eEse mob ya esta desplegado. Golpealo con este huevo para recogerlo."));
                return;
            }
        }

        // Cada cuenta es aparte: los que siguen al dueno, los fijos a un bloque y las
        // monturas. Se comprueba solo la que le toca a este mob.
        MobService.Account account = MobService.accountOf(definition.get());
        if (this.service.atLimit(player.getUniqueId(), account)) {
            int max = this.service.limitOf(player.getUniqueId(), account);
            String what = switch (account) {
                case MOUNT -> " monturas desplegadas";
                case POINT -> " mobs fijos desplegados";
                case OWNER -> " mobs desplegados";
            };
            player.sendMessage(Texts.color("&cYa tienes " + max + what
                    + ", tu maximo. Recoge alguno con su huevo."));
            return;
        }

        Optional<PlayerMobRegistry.MobLink> used = this.service.deploy(definition.get(),
                this.spawnLocation(event, player), player, bound.orElse(null));
        if (used.isEmpty()) {
            player.sendMessage(Texts.color("&cNo se pudo invocar el mob."));
            return;
        }
        if (bound.isEmpty()) {
            // Huevo nuevo: se liga al mob que acaba de nacer. No se gasta.
            this.craft.bind(item, used.get().linkId());
            player.sendMessage(Texts.color("&aHuevo vinculado a tu mob. Golpealo con el para recogerlo."));
        } else {
            player.sendMessage(Texts.color("&aMob desplegado de nuevo."));
        }
    }

    /**
     * Golpear el mob con su huevo: se recoge y vuelve dentro del huevo.
     *
     * <p>El vinculo se resuelve por la ENTIDAD golpeada, no por lo que declare el huevo.
     * Un huevo puede perder o cambiar su marca —al recargar, al copiarlo, al moverlo entre
     * inventarios— y entonces el mob no se recogia nunca: parecia que el huevo estaba
     * atascado, trayendo el mob y sin guardarlo.
     */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<MobDefinition> held = this.craft.definitionOf(item);
        if (held.isEmpty()) {
            return;
        }
        PlayerMobRegistry.MobLink link = this.service.playerMobs()
                .byEntity(clicked.getUniqueId()).orElse(null);
        if (link == null || !link.deployed()) {
            // Aqui esta la clave: o el mob no tiene vinculo, o su vinculo ya no dice
            // "desplegado". Sin esta traza habria que adivinar cual de las dos es.
            this.debug("Golpe a " + clicked.getType() + " sin vinculo desplegado ("
                    + (link == null ? "sin vinculo" : "vinculo guardado") + "); no se recoge.");
            return;
        }
        if (!player.getUniqueId().equals(link.owner())) {
            this.debug("Golpe a " + link.definitionId() + " cuyo dueno no es quien golpea.");
            return;
        }
        // Hace falta el huevo de ESE mob: no vale cualquier huevo custom.
        if (!held.get().id().equals(link.definitionId())) {
            this.debug("Huevo de '" + held.get().id() + " para un mob '"
                    + link.definitionId() + "': no coincide.");
            return;
        }
        event.setCancelled(true);
        // El huevo pasa a representar a este mob, aunque su marca se hubiera perdido.
        this.craft.bind(item, link.linkId());
        if (this.service.store(link.linkId())) {
            // Se marca este clic para que su mitad RIGHT_CLICK_AIR no lo vuelva a soltar.
            UUID id = player.getUniqueId();
            this.justCollected.add(id);
            this.plugin.getServer().getScheduler().runTask(this.plugin,
                    () -> this.justCollected.remove(id));
            this.debug("Mob recogido: " + link.definitionId() + ".");
            player.sendMessage(Texts.color("&aMob recogido. Vuelve a colocar el huevo cuando quieras."));
        }
    }

    /** Traza de depuracion: solo escribe si 'debug' esta activo en el config. */
    private void debug(String message) {
        if (this.service.config().debug()) {
            this.plugin.getLogger().info("[huevo] " + message);
        }
    }

    /** {@code true} si el jugador esta apuntando a un mob gestionado por el plugin. */
    private boolean aimingAtCustomMob(Player player) {
        Entity aimed = player.getTargetEntity(ENTITY_REACH);
        return aimed != null && this.service.find(aimed.getUniqueId()).isPresent();
    }

    /** Alcance con el que se mira a un mob: cubre el de creativo con margen. */
    private static final int ENTITY_REACH = 6;

    private Location spawnLocation(PlayerInteractEvent event, Player player) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || event.getBlockFace() == null) {
            return player.getLocation();
        }
        return clicked.getRelative(event.getBlockFace()).getLocation().add(0.5D, 0.0D, 0.5D);
    }
}
