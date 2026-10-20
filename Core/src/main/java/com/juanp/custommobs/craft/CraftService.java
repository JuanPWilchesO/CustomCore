package com.juanp.custommobs.craft;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.drop.ItemCatalog;
import com.juanp.custommobs.item.ItemRecipe;
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
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        this.registerItemRecipes();
        // addRecipe no avisa a los clientes ya conectados: sin esto, una receta
        // registrada en caliente no aparece hasta reconectar.
        for (var player : org.bukkit.Bukkit.getOnlinePlayers()) {
            this.syncRecipes(player);
        }
    }

    /** Manda al cliente las recetas registradas; sirve al entrar y tras un reload. */
    public void syncRecipes(org.bukkit.entity.Player player) {
        for (String key : this.recipeKeys) {
            NamespacedKey namespacedKey = NamespacedKey.fromString(key);
            if (namespacedKey == null) {
                continue;
            }
            try {
                player.discoverRecipe(namespacedKey);
            } catch (Throwable ignored) {
                // Una receta que el cliente no acepta no debe tumbar el resto.
            }
        }
    }

    /**
     * Registra las recetas de los objetos del catalogo que traigan una en su yml.
     *
     * <p>Un ingrediente puede ser <b>otro objeto guardado</b> (se compara con su NBT, no
     * solo por material) o un <b>material</b> vanilla. Las recetas con {@code forma} fijan
     * el orden.
     */
    public void registerItemRecipes() {
        ItemCatalog catalog = this.plugin.drops().catalog();
        for (String name : catalog.names()) {
            ItemRecipe spec = catalog.recipeOf(name).orElse(null);
            if (spec == null) {
                continue;
            }
            ItemStack result = catalog.get(name).orElse(null);
            if (result == null) {
                continue;
            }
            result.setAmount(spec.amount());
            NamespacedKey key = new NamespacedKey(this.plugin, "item_" + name);
            String trouble = spec.shaped()
                    ? this.registerShaped(key, result, spec)
                    : this.registerItemShapeless(key, result, spec.loose());
            if (trouble != null) {
                this.plugin.getLogger().warning("Receta de '" + name + "' omitida: " + trouble);
            }
        }

        // Las pociones tambien pueden ser crafteables (bloque 'receta:' en potions.yml).
        if (this.plugin.effects() == null) {
            return;
        }
        for (var potion : this.plugin.effects().potions().values()) {
            var recipe = potion.recipe();
            if (recipe == null) {
                continue;
            }
            ItemStack result = this.plugin.effects().item(potion);
            result.setAmount(recipe.amount());
            NamespacedKey key = new NamespacedKey(this.plugin, "potion_" + potion.id());
            String trouble = recipe.shaped()
                    ? this.registerShaped(key, result, recipe)
                    : this.registerItemShapeless(key, result, recipe.loose());
            if (trouble != null) {
                this.plugin.getLogger().warning("Receta de la pocion '" + potion.id() + "' omitida: " + trouble);
            }
        }
    }

    /** Registra una receta con forma (el orden importa). Devuelve el motivo del fallo, o {@code null}. */
    private String registerShaped(NamespacedKey key, ItemStack result, ItemRecipe spec) {
        List<String> shape = spec.shape();
        if (shape.size() > 3) {
            return "la forma pasa de 3 filas";
        }
        int width = shape.get(0).length();
        if (width > 3) {
            return "la forma pasa de 3 columnas";
        }
        for (String row : shape) {
            if (row.length() != width) {
                return "las filas de la forma no miden lo mismo";
            }
        }
        Map<Character, RecipeChoice> choices = new LinkedHashMap<>();
        for (Map.Entry<Character, String> slot : spec.slots().entrySet()) {
            RecipeChoice choice = this.choiceFor(slot.getValue());
            if (choice == null) {
                return "'" + slot.getValue() + "' no es un objeto del catalogo ni un material";
            }
            choices.put(slot.getKey(), choice);
        }
        for (String row : shape) {
            for (char letter : row.toCharArray()) {
                if (letter != ' ' && !choices.containsKey(letter)) {
                    return "la letra '" + letter + "' no esta en 'ingredientes'";
                }
            }
        }
        java.util.TreeSet<String> parts = new java.util.TreeSet<>();
        spec.slots().forEach((letter, token) -> parts.add(letter + "=" + token));
        String signature = "shaped::" + String.join("|", shape) + "::" + String.join(",", parts);
        if (!this.ingredientPairs.add(signature)) {
            return "choca con otra receta identica";
        }
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.toArray(new String[0]));
        choices.forEach(recipe::setIngredient);
        try {
            Bukkit.addRecipe(recipe);
            this.recipeKeys.add(key.toString());
            return null;
        } catch (Throwable throwable) {
            this.ingredientPairs.remove(signature);
            return String.valueOf(throwable.getMessage());
        }
    }

    /** Registra una receta sin forma de un objeto del catalogo. Devuelve el motivo del fallo, o {@code null}. */
    private String registerItemShapeless(NamespacedKey key, ItemStack result, List<String> ingredients) {
        List<RecipeChoice> choices = new ArrayList<>(ingredients.size());
        for (String token : ingredients) {
            RecipeChoice choice = this.choiceFor(token);
            if (choice == null) {
                return "'" + token + "' no es un objeto del catalogo ni un material";
            }
            choices.add(choice);
        }
        String signature = "shapeless-item::" + String.join("+", new java.util.TreeSet<>(ingredients));
        if (!this.ingredientPairs.add(signature)) {
            return "choca con otra receta identica";
        }
        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        choices.forEach(recipe::addIngredient);
        try {
            Bukkit.addRecipe(recipe);
            this.recipeKeys.add(key.toString());
            return null;
        } catch (Throwable throwable) {
            this.ingredientPairs.remove(signature);
            return String.valueOf(throwable.getMessage());
        }
    }

    /** Objeto del catalogo por su nombre, o material vanilla; {@code null} si no es ninguno. */
    private RecipeChoice choiceFor(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        ItemStack stored = this.plugin.drops().catalog().get(token).orElse(null);
        if (stored != null) {
            stored.setAmount(1);
            return new RecipeChoice.ExactChoice(stored);
        }
        Material material = Material.matchMaterial(token);
        return material == null ? null : new RecipeChoice.MaterialChoice(material);
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
                || data.has(this.keys.upgrade(), PersistentDataType.STRING)
                || data.has(this.plugin.drops().catalog().nameKey(), PersistentDataType.STRING);
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
