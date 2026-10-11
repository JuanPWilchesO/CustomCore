package com.juanp.custommobs.craft;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.RecipeSpec;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobKeys;
import com.juanp.custommobs.mob.MobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registra las recetas de los huevos custom y los construye.
 *
 * <p>La receta por defecto es sin forma: huevo base + catalizador. Si dos definiciones
 * comparten el mismo par de ingredientes, la segunda se omite con un aviso, porque el
 * cliente no podria distinguirlas.
 */
public final class CraftService {

    private final CustomMobsPlugin plugin;
    private final MobRegistry registry;
    private final MobKeys keys;
    private final Set<String> recipeKeys = new HashSet<>();
    private final Set<String> ingredientPairs = new HashSet<>();

    public CraftService(CustomMobsPlugin plugin, MobRegistry registry, MobKeys keys) {
        this.plugin = plugin;
        this.registry = registry;
        this.keys = keys;
    }

    public void registerAll() {
        this.unregisterAll();
        for (MobDefinition definition : this.registry.all()) {
            this.register(definition);
        }
    }

    public void unregisterAll() {
        for (String key : this.recipeKeys) {
            NamespacedKey namespacedKey = NamespacedKey.fromString(key);
            if (namespacedKey != null) {
                Bukkit.removeRecipe(namespacedKey);
            }
        }
        this.recipeKeys.clear();
        this.ingredientPairs.clear();
    }

    private void register(MobDefinition definition) {
        if (definition.eggMaterial() == null) {
            return;
        }
        RecipeSpec spec = definition.recipe();
        if (!spec.enabled()) {
            this.plugin.getLogger().info("Crafteo desactivado para '" + definition.id() + "' (recipe.amount <= 0).");
            return;
        }
        if (spec.extras().size() > 8) {
            this.plugin.getLogger().warning("La receta de '" + definition.id() + "' tiene demasiados ingredientes (maximo 8 ademas del huevo).");
            return;
        }

        String signature = spec.signature(definition.eggMaterial());
        if (!this.ingredientPairs.add(signature)) {
            this.plugin.getLogger().warning("La receta de '" + definition.id() + "' choca con otra ("
                    + signature + "). Cambia 'recipe.extras' en el yml para diferenciarlas.");
            return;
        }

        NamespacedKey key = new NamespacedKey(this.plugin, "egg_" + definition.id());
        ItemStack result = this.createEgg(definition);
        result.setAmount(Math.max(1, Math.min(9, spec.amount())));

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        recipe.addIngredient(new RecipeChoice.MaterialChoice(definition.eggMaterial()));
        for (Material extra : spec.extras()) {
            recipe.addIngredient(new RecipeChoice.MaterialChoice(extra));
        }

        try {
            Bukkit.addRecipe(recipe);
            this.recipeKeys.add(key.toString());
        } catch (Throwable throwable) {
            this.plugin.getLogger().warning("No se pudo registrar la receta de '"
                    + definition.id() + "': " + throwable.getMessage());
        }
    }

    /**
     * Registra una receta sin forma cualquiera, comprobando que sus ingredientes no esten
     * ya usados por otra. Lo usan tambien los items de mejora, para no chocar con los
     * huevos ni entre ellos: dos recetas iguales son indistinguibles para el cliente.
     *
     * @return {@code false} si esos ingredientes ya estaban ocupados
     */
    public boolean registerShapeless(NamespacedKey key, ItemStack result, List<Material> ingredients) {
        String signature = signatureOf(ingredients);
        if (!this.ingredientPairs.add(signature)) {
            return false;
        }
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Material material : ingredients) {
            recipe.addIngredient(new RecipeChoice.MaterialChoice(material));
        }
        try {
            Bukkit.addRecipe(recipe);
            this.recipeKeys.add(key.toString());
            return true;
        } catch (Throwable throwable) {
            this.ingredientPairs.remove(signature);
            this.plugin.getLogger().warning("No se pudo registrar la receta '" + key + "': "
                    + throwable.getMessage());
            return false;
        }
    }

    /**
     * {@code true} si el objeto crafteado es del plugin: un huevo (lleva el id de su
     * definicion) o un item de mejora. Reconocerlo por el resultado evita depender de la
     * API de recetas, que no expone la clave.
     */
    public boolean isCustomResult(ItemStack result) {
        if (result == null || result.getType().isAir() || !result.hasItemMeta()) {
            return false;
        }
        var data = result.getItemMeta().getPersistentDataContainer();
        return data.has(this.keys.definition(), PersistentDataType.STRING)
                || data.has(this.keys.upgrade(), PersistentDataType.STRING);
    }

    /** Firma de una lista de ingredientes, sin importar el orden. */
    public static String signatureOf(List<Material> ingredients) {
        List<String> parts = new ArrayList<>(ingredients.size());
        for (Material material : ingredients) {
            parts.add(material.name());
        }
        Collections.sort(parts);
        return String.join("+", parts);
    }

    /** Construye el huevo custom: el huevo base marcado con el id de la definicion. */
    public ItemStack createEgg(MobDefinition definition) {
        if (definition.eggMaterial() == null) {
            return null;
        }
        ItemStack item = new ItemStack(definition.eggMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(definition.displayName());
            if (!definition.lore().isEmpty()) {
                meta.setLore(definition.lore());
            }
            meta.getPersistentDataContainer().set(this.keys.definition(), PersistentDataType.STRING, definition.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Id de vinculo de un huevo, si ya quedo ligado a un mob.
     *
     * <p>Un huevo recien crafteado no lo tiene: se liga al colocar el mob por primera vez.
     * Desde entonces lo representa, sirve para recogerlo y vuelve a desplegarlo.
     */
    public Optional<UUID> linkOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(this.keys.link(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Liga el huevo a un mob: desde aqui lo representa. */
    public boolean bind(ItemStack item, UUID linkId) {
        if (item == null || item.getType().isAir() || linkId == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(this.keys.link(), PersistentDataType.STRING, linkId.toString());
        item.setItemMeta(meta);
        return true;
    }

    /** Definicion a la que pertenece un item, si es un huevo custom. */
    public Optional<MobDefinition> definitionOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String id = meta.getPersistentDataContainer().get(this.keys.definition(), PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return this.registry.get(id);
    }
}
