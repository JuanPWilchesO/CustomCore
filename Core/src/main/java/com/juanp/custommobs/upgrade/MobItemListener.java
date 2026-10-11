package com.juanp.custommobs.upgrade;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.book.MobBookService;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * El libro de inspeccion y los items de mejora.
 *
 * <p>El de mejora se aplica <b>haciendo clic derecho sobre un mob propio</b> con el item en
 * la mano, y se consume. Si el mob no es tuyo, no se gasta.
 */
public final class MobItemListener implements Listener {

    private final CustomMobsPlugin plugin;
    private final MobService service;
    private final UpgradeService upgrades;
    private final MobBookService book;

    public MobItemListener(CustomMobsPlugin plugin, MobService service,
                           UpgradeService upgrades, MobBookService book) {
        this.plugin = plugin;
        this.service = service;
        this.upgrades = upgrades;
        this.book = book;
    }

    /** El libro se abre desde cualquier clic derecho. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // Fuera de los mundos del plugin, ni el libro ni las mejoras hacen nada.
        if (!this.service.worldEnabled(event.getPlayer().getWorld())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!this.book.isBook(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        this.book.open(event.getPlayer());
    }

    /**
     * Un mob custom no se modifica a mano.
     *
     * <p>Un jugador puede renombrarlo con una etiqueta, ponerle silla o armadura, o
     * llevarselo con una correa. Nada de eso debe poder hacer: el mob cambia solo por lo
     * que el plugin permite —el estilo de su dueno, los items de mejora, el huevo— y
     * todo lo demas lo decide su definicion.
     *
     * <p>Con la mano vacia se sigue pudiendo montar una montura: solo se bloquea el clic
     * cuando trae puesto precisamente uno de esos objetos.
     */
    @EventHandler(ignoreCancelled = true)
    public void onModifyMob(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        CustomMob customMob = this.service.find(event.getRightClicked().getUniqueId()).orElse(null);
        if (customMob == null) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return;
        }
        String type = item.getType().name();
        boolean nameTag = item.getType() == org.bukkit.Material.NAME_TAG;
        boolean gear = item.getType() == org.bukkit.Material.SADDLE
                || type.endsWith("_HORSE_ARMOR") || type.equals("WOLF_ARMOR");
        boolean lead = item.getType() == org.bukkit.Material.LEAD;
        // Alimentar y criar quedan fuera: un mob custom no se cuida a mano, y criarlo
        // seria sacar copias de una definicion que es del servidor.
        boolean food = FEEDING.contains(item.getType()) || item.getType().name().endsWith("_SEEDS");
        if (!nameTag && !gear && !lead && !food) {
            return;
        }
        event.setCancelled(true);
        if (nameTag) {
            event.getPlayer().sendMessage(com.juanp.custommobs.mob.Texts.color(
                    "&cEl nombre de un mob custom lo pone su definicion."));
        } else if (lead) {
            event.getPlayer().sendMessage(com.juanp.custommobs.mob.Texts.color(
                    "&cA un mob custom no se lo lleva nadie con correa."));
        } else if (food) {
            event.getPlayer().sendMessage(com.juanp.custommobs.mob.Texts.color(
                    "&cA un mob custom no se le da de comer ni se le cria."));
        } else {
            event.getPlayer().sendMessage(com.juanp.custommobs.mob.Texts.color(
                    "&cLa silla y la armadura de un mob custom las pone su definicion."));
        }
    }

    /**
     * Un dispensador tampoco equipa a un mob custom.
     *
     * <p>Un jugador puede construir uno delante de un mob y meterle armadura sin
     * tocarle: es la rendija que queda cuando se bloquea el clic a mano.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (!this.service.worldEnabled(event.getBlock().getWorld())) {
            return;
        }
        org.bukkit.Material type = event.getItem().getType();
        boolean gear = type == org.bukkit.Material.SADDLE
                || type.name().endsWith("_HORSE_ARMOR")
                || type.name().equals("WOLF_ARMOR")
                || type == org.bukkit.Material.NAME_TAG;
        if (!gear || !(event.getBlock().getBlockData()
                instanceof org.bukkit.block.data.Directional directional)) {
            return;
        }
        org.bukkit.block.Block target = event.getBlock().getRelative(directional.getFacing());
        org.bukkit.Location center = target.getLocation().add(0.5D, 0.5D, 0.5D);
        for (org.bukkit.entity.Entity entity
                : target.getWorld().getNearbyEntities(center, 0.6D, 0.6D, 0.6D)) {
            if (this.service.find(entity.getUniqueId()).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Lo que un jugador no le da de comer a un mob custom. */
    private static final java.util.Set<org.bukkit.Material> FEEDING = java.util.Set.of(
            org.bukkit.Material.WHEAT, org.bukkit.Material.CARROT,
            org.bukkit.Material.GOLDEN_CARROT, org.bukkit.Material.POTATO,
            org.bukkit.Material.BAKED_POTATO, org.bukkit.Material.APPLE,
            org.bukkit.Material.GOLDEN_APPLE, org.bukkit.Material.ENCHANTED_GOLDEN_APPLE,
            org.bukkit.Material.HAY_BLOCK, org.bukkit.Material.SUGAR,
            org.bukkit.Material.SWEET_BERRIES, org.bukkit.Material.GLOW_BERRIES,
            org.bukkit.Material.BAMBOO, org.bukkit.Material.COOKIE,
            org.bukkit.Material.DRIED_KELP, org.bukkit.Material.KELP,
            org.bukkit.Material.SEAGRASS, org.bukkit.Material.COD,
            org.bukkit.Material.SALMON, org.bukkit.Material.TROPICAL_FISH,
            org.bukkit.Material.PUFFERFISH, org.bukkit.Material.CHICKEN,
            org.bukkit.Material.BEEF, org.bukkit.Material.PORKCHOP,
            org.bukkit.Material.MUTTON, org.bukkit.Material.RABBIT,
            org.bukkit.Material.CRIMSON_FUNGUS, org.bukkit.Material.WARPED_FUNGUS);

    /**
     * Quien abra el inventario de una montura con alforjas no puede tocar la silla ni la
     * armadura: son los dos primeros huecos del inventario de un caballo.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMountInventory(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof org.bukkit.entity.AbstractHorse horse)) {
            return;
        }
        CustomMob customMob = this.service.find(horse.getUniqueId()).orElse(null);
        if (customMob == null || !customMob.definition().mountable()) {
            return;
        }
        if (event.getRawSlot() == 0 || event.getRawSlot() == 1) {
            event.setCancelled(true);
        }
    }

    /** Con el libro en la mano apuntando a un mob, tambien se abre. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.service.worldEnabled(player.getWorld())) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (this.book.isBook(item)) {
            event.setCancelled(true);
            this.book.open(player);
            return;
        }

        Optional<UpgradeSpec> spec = this.upgrades.specOf(item);
        if (spec.isEmpty()) {
            return;
        }
        CustomMob customMob = this.service.find(event.getRightClicked().getUniqueId()).orElse(null);
        if (customMob == null) {
            return;
        }
        if (!player.getUniqueId().equals(customMob.ownerId())) {
            player.sendMessage(Texts.color("&cEse mob no es tuyo."));
            return;
        }
        event.setCancelled(true);
        this.upgrades.applyTo(customMob, spec.get());
        item.setAmount(item.getAmount() - 1);
        player.sendMessage(Texts.color("&aMejora aplicada a " + customMob.definition().displayName()
                + "&a: &f" + UpgradeService.describe(spec.get())));
    }
}
