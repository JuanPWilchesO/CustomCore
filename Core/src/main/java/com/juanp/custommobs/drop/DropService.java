package com.juanp.custommobs.drop;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Construye los drops de un mob a partir de su tabla.
 *
 * <p>Los objetos con NBT salen del catalogo ({@link ItemCatalog}) por <b>nombre</b>. Un
 * nombre que no exista no rompe nada: se avisa una vez y ese drop se omite — asi un mob mal
 * configurado no inunda el log ni deja de cargar.
 */
public final class DropService {

    private final CustomMobsPlugin plugin;
    private final ItemCatalog catalog;
    private final Random random = new Random();
    private final Set<String> warned = new HashSet<>();

    public DropService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new ItemCatalog(plugin);
    }

    public ItemCatalog catalog() {
        return this.catalog;
    }

    /** Convierte una entrada en el objeto que se suelta, o {@code null} si no se pudo. */
    public ItemStack build(DropSpec spec, int amount) {
        if (spec.fromCatalog()) {
            Optional<ItemStack> stored = this.catalog.get(spec.itemName());
            if (stored.isEmpty()) {
                this.warnMissing(spec.itemName());
                return null;
            }
            ItemStack item = stored.get().clone();
            item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
            return item;
        }
        if (spec.material() == null) {
            return null;
        }
        return new ItemStack(spec.material(), Math.max(1, amount));
    }

    /** {@code true} si esta entrada debe soltarse en esta tirada. */
    public boolean rolls(DropSpec spec) {
        return spec.chance() >= 1.0D || this.random.nextDouble() < spec.chance();
    }

    public int rollAmount(DropSpec spec) {
        return spec.rollAmount(this.random);
    }

    private void warnMissing(String name) {
        if (this.warned.add(name)) {
            this.plugin.getLogger().warning("El drop '" + name
                    + "' no esta en el catalogo. Guardalo con /customcore item save " + name);
        }
    }
}
