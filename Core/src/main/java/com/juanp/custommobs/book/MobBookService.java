package com.juanp.custommobs.book;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.config.PluginConfig;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobKeys;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.PlayerMobRegistry;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Libro de inspeccion: muestra al jugador sus mobs y sus estadisticas de combate.
 *
 * <p>El item solo lo reparte un administrador por comando, pero <b>cualquiera puede usarlo</b>:
 * asi se puede vender o regalar desde el sistema de economia del servidor.
 */
public final class MobBookService {

    private final CustomMobsPlugin plugin;
    private final MobService service;
    private final MobKeys keys;

    public MobBookService(CustomMobsPlugin plugin, MobService service, MobKeys keys) {
        this.plugin = plugin;
        this.service = service;
        this.keys = keys;
    }

    /** El libro tal como lo vera el jugador, marcado para reconocerlo. */
    public ItemStack create() {
        PluginConfig config = this.service.config();
        ItemStack item = new ItemStack(config.bookMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.bookName());
            if (!config.bookLore().isEmpty()) {
                meta.setLore(config.bookLore());
            }
            meta.getPersistentDataContainer().set(this.keys.book(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** {@code true} si ese objeto es el libro de inspeccion. */
    public boolean isBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte mark = item.getItemMeta().getPersistentDataContainer()
                .get(this.keys.book(), PersistentDataType.BYTE);
        return mark != null;
    }

    /** Abre el libro con una pagina por mob. */
    public void open(Player player) {
        List<CustomMob> deployed = new ArrayList<>();
        for (CustomMob customMob : this.service.active()) {
            if (player.getUniqueId().equals(customMob.ownerId())) {
                deployed.add(customMob);
            }
        }
        List<PlayerMobRegistry.MobLink> stored = new ArrayList<>();
        for (PlayerMobRegistry.MobLink link : this.service.playerMobs().all()) {
            if (player.getUniqueId().equals(link.owner()) && !link.deployed()) {
                stored.add(link);
            }
        }

        List<String> pages = new ArrayList<>();
        if (deployed.isEmpty() && stored.isEmpty()) {
            pages.add(Texts.color("&0No tienes ningun mob todavia."));
        }
        for (CustomMob customMob : deployed) {
            pages.add(this.page(customMob));
        }
        for (PlayerMobRegistry.MobLink link : stored) {
            pages.add(this.storedPage(link));
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            player.sendMessage(Texts.color("&cNo se pudo construir el libro."));
            return;
        }
        meta.setTitle(Texts.color("&6Inventario de fuerzas"));
        meta.setAuthor("CustomMobs");
        meta.setPages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    /** Una pagina con las estadisticas de combate de un mob desplegado. */
    private String page(CustomMob customMob) {
        LivingEntity entity = customMob.entity();
        List<String> lines = new ArrayList<>();
        lines.add(customMob.definition().displayName());
        lines.add(Texts.color("&8" + customMob.definition().id()));
        lines.add("");
        // El papel del libro es claro: el texto va en tonos OSCUROS. En blanco o en
        // colores muy claros no se lee.
        lines.add(Texts.color("&4Vida: &0" + trim(entity.getHealth()) + " &8/ &0"
                + trim(value(entity, Attribute.MAX_HEALTH))));
        lines.add(Texts.color("&4Ataque: &0" + trim(value(entity, Attribute.ATTACK_DAMAGE))));
        lines.add(Texts.color("&4Velocidad: &0" + trim(value(entity, Attribute.MOVEMENT_SPEED))));
        lines.add(Texts.color("&4Armadura: &0" + trim(value(entity, Attribute.ARMOR))));
        lines.add("");
        lines.add(Texts.color("&8" + entity.getWorld().getName() + " "
                + entity.getLocation().getBlockX() + " " + entity.getLocation().getBlockY()
                + " " + entity.getLocation().getBlockZ()));
        lines.add("");
        // El id de arriba sirve para algo: aqui esta como usarlo.
        lines.add(Texts.color("&8Despedirlo: /cm despedir " + customMob.definition().id()));
        return String.join("\n", lines);
    }

    /** Una pagina para un mob guardado dentro de su huevo. */
    private String storedPage(PlayerMobRegistry.MobLink link) {
        List<String> lines = new ArrayList<>();
        String name = this.plugin.registry().get(link.definitionId())
                .map(definition -> definition.displayName()).orElse(link.definitionId());
        lines.add(name);
        lines.add(Texts.color("&8" + link.definitionId()));
        lines.add("");
        lines.add(Texts.color("&8Guardado en su huevo."));
        lines.add("");
        lines.add(Texts.color("&8Despedirlo: /cm despedir " + link.definitionId()));
        if (link.state() != null) {
            lines.add(Texts.color("&4Vida: &0" + trim(link.state().health())));
        }
        return String.join("\n", lines);
    }

    private static double value(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    private static String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
