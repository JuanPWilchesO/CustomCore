package com.juanp.custommobs.drop;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalogo de objetos con nombre.
 *
 * <p>Resuelve el problema de fondo: un objeto tocado por otro plugin (un encantamiento de
 * ExcellentEnchants, cualquier NBT) no se puede escribir a mano en un yml. El comando lo
 * captura una vez y le da un <b>nombre</b>; despues cualquier mob lo llama por ese nombre.
 *
 * <p>Cada objeto vive en su propio archivo, {@code items/<nombre>.yml}, y el nombre queda
 * escrito dentro. Asi el catalogo se puede leer, copiar y versionar como cualquier config.
 */
public final class ItemCatalog {

    private final CustomMobsPlugin plugin;
    private final File folder;
    private final NamespacedKey factionKey;
    private final NamespacedKey classKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey modificationKey;
    private final NamespacedKey stunFreeKey;

    /** Claves de modificacion. Falta = permitido; false = prohibido. */
    public static final List<String> MOD_KEYS = List.of(
            "modificable", "encantar-libros", "encantar-mesa",
            "renombrar", "reparar", "desencantar", "tallar");

    public ItemCatalog(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "items");
        this.factionKey = new NamespacedKey(plugin, "item_faccion");
        this.classKey = new NamespacedKey(plugin, "item_clase");
        this.nameKey = new NamespacedKey(plugin, "item_catalogo");
        this.modificationKey = new NamespacedKey(plugin, "item_mod");
        this.stunFreeKey = new NamespacedKey(plugin, "item_stun_free");
    }

    /**
     * Guarda el objeto bajo ese nombre, con todo su NBT.
     *
     * @return {@code false} si el nombre no es valido o no se pudo escribir
     */
    public boolean save(String rawName, ItemStack item) {
        String name = clean(rawName);
        if (name == null || item == null || item.getType() == Material.AIR) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nombre", name);
        yaml.set("item", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        yaml.set("etiqueta", labelOf(item));
        // Volver a guardar un objeto no debe soltarlo de su faccion: la reserva es del
        // catalogo, no del NBT del objeto en la mano.
        File file = this.fileOf(name);
        if (file.exists()) {
            YamlConfiguration previousYaml = YamlConfiguration.loadConfiguration(file);
            String previous = factionOf(previousYaml);
            if (previous != null) {
                yaml.set("faccion", previous);
            }
            String previousClass = factionOf(previousYaml, "clase");
            if (previousClass != null) {
                yaml.set("clase", previousClass);
            }
            // Las protecciones de modificacion tambien son del catalogo.
            for (String key : MOD_KEYS) {
                if (!previousYaml.getBoolean(key, true)) {
                    yaml.set(key, false);
                }
            }
            // La receta tambien es del catalogo: re-guardar el objeto no debe borrarla.
            Object previousRecipe = previousYaml.get("receta");
            if (previousRecipe != null) {
                yaml.set("receta", previousRecipe);
            }
        }
        try {
            if (!this.folder.exists() && !this.folder.mkdirs()) {
                this.plugin.getLogger().warning("No se pudo crear la carpeta items/");
                return false;
            }
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            this.plugin.getLogger().warning("No se pudo guardar el item '" + name + "': " + ex.getMessage());
            return false;
        }
    }

    /** Objeto guardado con ese nombre, con su NBT intacto. */
    public Optional<ItemStack> get(String rawName) {
        String name = clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return Optional.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String data = yaml.getString("item");
        if (data == null) {
            return Optional.empty();
        }
        try {
            ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
            // Dos marcas viajan DENTRO del objeto: de que catalogo salio, y a que faccion
            // esta reservado. Una vez suelto o entregado, el yml ya no esta a mano, asi
            // que las marcas tienen que ir con el.
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(this.nameKey,
                        PersistentDataType.STRING, name);
                String faction = factionOf(yaml, "faccion");
                if (faction != null) {
                    meta.getPersistentDataContainer().set(this.factionKey,
                            PersistentDataType.STRING, faction);
                }
                String clazz = factionOf(yaml, "clase");
                if (clazz != null) {
                    meta.getPersistentDataContainer().set(this.classKey,
                            PersistentDataType.STRING, clazz);
                }
                java.util.Set<String> denied = deniedModifications(yaml);
                if (denied.isEmpty()) {
                    meta.getPersistentDataContainer().remove(this.modificationKey);
                } else {
                    meta.getPersistentDataContainer().set(this.modificationKey,
                            PersistentDataType.STRING, String.join(",", denied));
                }
                // Marca que viaja con el objeto: se puede usar durante un control.
                if (yaml.getBoolean("stun-free", yaml.getBoolean("stun_free", false))) {
                    meta.getPersistentDataContainer().set(this.stunFreeKey,
                            PersistentDataType.BYTE, (byte) 1);
                } else {
                    meta.getPersistentDataContainer().remove(this.stunFreeKey);
                }
                item.setItemMeta(meta);
            }
            return Optional.of(item);
        } catch (Throwable ex) {
            this.plugin.getLogger().warning("El item '" + name + "' esta corrupto y no se pudo leer.");
            return Optional.empty();
        }
    }

    /**
     * Las etiquetas de los objetos guardados (su nombre visible).
     *
     * <p>Sirve para reconocer las copias que se repartieron **antes** de que los objetos
     * llevaran la marca dentro: a esas solo se las distingue por el nombre.
     */
    public java.util.Set<String> labels() {
        java.util.Set<String> labels = new java.util.LinkedHashSet<>();
        File[] files = this.folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return labels;
        }
        for (File file : files) {
            String label = YamlConfiguration.loadConfiguration(file).getString("etiqueta");
            if (label != null && !label.isBlank()) {
                labels.add(label);
            }
        }
        return labels;
    }

    /** Nombres guardados, ordenados. */
    public List<String> names() {
        File[] files = this.folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(files.length);
        for (File file : files) {
            names.add(file.getName().substring(0, file.getName().length() - 4));
        }
        Collections.sort(names);
        return List.copyOf(names);
    }

    /**
     * Reserva el objeto a una faccion, o lo libera pasando {@code null}.
     *
     * @return {@code false} si el objeto no existe o no se pudo escribir
     */
    public boolean setFaction(String rawName, String faction) {
        String name = clean(rawName);
        if (name == null) {
            return false;
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("faccion", faction == null ? null : faction.trim().toLowerCase(Locale.ROOT));
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            this.plugin.getLogger().warning("No se pudo escribir items/" + name + ".yml: " + ex.getMessage());
            return false;
        }
    }

    /** La faccion a la que el objeto queda reservado, o vacio si es libre. */
    public Optional<String> factionOf(String rawName) {
        String name = clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return Optional.empty();
        }
        return Optional.ofNullable(factionOf(YamlConfiguration.loadConfiguration(file)));
    }

    /** Clave con la que se marca, dentro del objeto, su faccion. */
    public NamespacedKey factionKey() {
        return this.factionKey;
    }

    /** Clave con la que se marca, dentro del objeto, su clase. */
    public NamespacedKey classKey() {
        return this.classKey;
    }

    /**
     * Reserva el objeto a una clase, o lo libera pasando {@code null}.
     *
     * @return {@code false} si el objeto no existe o no se pudo escribir
     */
    public boolean setClass(String rawName, String clazz) {
        String name = clean(rawName);
        if (name == null) {
            return false;
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("clase", clazz == null ? null : clazz.trim().toLowerCase(Locale.ROOT));
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            this.plugin.getLogger().warning("No se pudo escribir items/" + name + ".yml: " + ex.getMessage());
            return false;
        }
    }

    /** La clase del objeto, si su yml la declara. */
    public Optional<String> classOf(String rawName) {
        String name = clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        File file = this.fileOf(name);
        return file.exists()
                ? Optional.ofNullable(factionOf(YamlConfiguration.loadConfiguration(file), "clase"))
                : Optional.empty();
    }

    /** Clave con la que se marca, dentro del objeto, de que catalogo salio. */
    public NamespacedKey nameKey() {
        return this.nameKey;
    }

    /** Clave con la que se marcan las protecciones de modificacion. */
    public NamespacedKey modificationKey() {
        return this.modificationKey;
    }

    /**
     * Normaliza una clave de modificacion, con alias en espanol.
     *
     * @return la clave canonica, o {@code null} si no existe
     */
    public static String normalizeModKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        key = switch (key) {
            case "todo", "all", "todo-lo-demais" -> "modificable";
            case "libros", "libro", "book", "books", "encantar-con-libros" -> "encantar-libros";
            case "mesa", "table", "encantar-con-mesa" -> "encantar-mesa";
            case "nombre", "rename", "renombrar-objeto" -> "renombrar";
            case "reparar-objeto", "repair" -> "reparar";
            case "grindstone", "desencantado", "quitar-encantamientos" -> "desencantar";
            case "smithing", "smith", "forja", "tallar-objeto" -> "tallar";
            default -> key;
        };
        return MOD_KEYS.contains(key) ? key : null;
    }

    /**
     * Cambia una proteccion de modificacion. {@code allowed=false} la prohibe;
     * {@code true} la vuelve a permitir.
     *
     * @return {@code false} si el item o la clave no existen
     */
    public boolean setModification(String rawName, String rawKey, boolean allowed) {
        String name = clean(rawName);
        String key = normalizeModKey(rawKey);
        if (name == null || key == null) {
            return false;
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(key, allowed ? null : false);
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            this.plugin.getLogger().warning("No se pudo escribir items/" + name + ".yml: " + ex.getMessage());
            return false;
        }
    }

    /** Protecciones guardadas en el yml del item. */
    private static java.util.Set<String> deniedModifications(YamlConfiguration yaml) {
        java.util.Set<String> denied = new java.util.LinkedHashSet<>();
        for (String key : MOD_KEYS) {
            if (!yaml.getBoolean(key, true)) {
                denied.add(key);
            }
        }
        return denied;
    }

    /** Protecciones que lleva el objeto en su NBT. */
    public java.util.Set<String> deniedModifications(ItemStack item) {
        java.util.Set<String> denied = new java.util.LinkedHashSet<>();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return denied;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(this.modificationKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return denied;
        }
        for (String key : raw.split(",")) {
            String normalized = normalizeModKey(key);
            if (normalized != null) {
                denied.add(normalized);
            }
        }
        return denied;
    }

    /** {@code true} si esa modificacion esta prohibida para el objeto. */
    public boolean modificationDenied(ItemStack item, String key) {
        String normalized = normalizeModKey(key);
        if (normalized == null) {
            return false;
        }
        java.util.Set<String> denied = deniedModifications(item);
        return denied.contains("modificable") || denied.contains(normalized);
    }

    /** {@code true} si otro objeto del catalogo comparte el mismo material. */
    public boolean materialShared(org.bukkit.Material material, String name) {
        if (material == null) {
            return false;
        }
        boolean found = false;
        for (String other : this.names()) {
            ItemStack item = this.get(other).orElse(null);
            if (item == null || item.getType() != material) {
                continue;
            }
            if (found) {
                return true;
            }
            found = true;
        }
        return false;
    }

    /** {@code true} si el objeto se puede usar durante un aturdimiento o silencio. */
    public boolean stunFree(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte flag = item.getItemMeta().getPersistentDataContainer()
                .get(this.stunFreeKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /**
     * Lo que hace el objeto al usarse, si su yml trae un bloque {@code usar:}.
     *
     * @see com.juanp.custommobs.item.UseSpec
     */
    public Optional<com.juanp.custommobs.item.UseSpec> useOf(String rawName) {
        String name = clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return Optional.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("usar");
        if (section == null) {
            return Optional.empty();
        }
        String effect = section.getString("efecto");
        java.util.List<String> skills = new java.util.ArrayList<>();
        for (String raw : section.getStringList("skills")) {
            if (raw != null && !raw.isBlank()) {
                skills.add(raw.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        // 'skill:' singular es un atajo para una sola skill.
        String single = section.getString("skill");
        if (single != null && !single.isBlank()) {
            skills.add(single.trim().toLowerCase(java.util.Locale.ROOT));
        }
        if ((effect == null || effect.isBlank()) && skills.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new com.juanp.custommobs.item.UseSpec(
                effect == null || effect.isBlank()
                        ? null : effect.trim().toLowerCase(java.util.Locale.ROOT),
                java.util.List.copyOf(skills),
                Math.max(0, section.getInt("duracion", 0)),
                section.getBoolean("consume", true),
                Math.max(0, section.getInt("cooldown", 0)),
                messageOf(section, "inicio", "mensaje-inicio", "start-message", "start"),
                messageOf(section, "fin", "mensaje-fin", "end-message", "end"),
                messageOf(section, "cooldown-aviso", "aviso-cooldown",
                        "mensaje-cooldown", "cooldown-message")));
    }

    /** Primer mensaje no vacio de las claves dadas, ya con colores. */
    private static String messageOf(org.bukkit.configuration.ConfigurationSection section,
                                    String... keys) {
        for (String key : keys) {
            String raw = section.getString(key);
            if (raw != null && !raw.isBlank()) {
                return Texts.color(raw);
            }
        }
        return null;
    }

    /**
     * Receta del objeto, si su yml trae una.
     *
     * @see com.juanp.custommobs.item.ItemRecipe
     */
    public Optional<com.juanp.custommobs.item.ItemRecipe> recipeOf(String rawName) {
        String name = clean(rawName);
        if (name == null) {
            return Optional.empty();
        }
        File file = this.fileOf(name);
        if (!file.exists()) {
            return Optional.empty();
        }
        return parseRecipe(YamlConfiguration.loadConfiguration(file));
    }

    /**
     * Lee un bloque de receta (con {@code forma} o {@code ingredientes}).
     *
     * <p>Acepta el archivo entero, que lleva {@code receta:} dentro, o el propio bloque
     * {@code receta:} ya seleccionado. Las pociones usan la segunda forma.
     */
    public static Optional<com.juanp.custommobs.item.ItemRecipe> parseRecipe(
            org.bukkit.configuration.ConfigurationSection yaml) {
        // Dos formas de llamada: el archivo entero (con 'receta:' dentro) o el propio
        // bloque 'receta:'. Antes solo valia la primera, y las pociones le pasaban la
        // segunda: buscaba 'receta.receta' y la receta quedaba nula en silencio.
        var section = yaml.isSet("forma") || yaml.isSet("ingredientes")
                ? yaml
                : yaml.getConfigurationSection("receta");
        if (section == null) {
            return Optional.empty();
        }
        int amount = Math.max(1, Math.min(8, section.getInt("cantidad", 1)));
        java.util.List<String> shape = section.getStringList("forma");
        if (!shape.isEmpty()) {
            var ingredients = section.getConfigurationSection("ingredientes");
            if (ingredients == null) {
                return Optional.empty();
            }
            java.util.Map<Character, String> slots = new java.util.LinkedHashMap<>();
            for (String letter : ingredients.getKeys(false)) {
                if (letter.isEmpty()) {
                    continue;
                }
                slots.put(letter.charAt(0), ingredients.getString(letter));
            }
            return slots.isEmpty() ? Optional.empty()
                    : Optional.of(new com.juanp.custommobs.item.ItemRecipe(amount,
                            java.util.List.copyOf(shape), java.util.Map.copyOf(slots), java.util.List.of()));
        }
        java.util.List<String> loose = section.getStringList("ingredientes");
        return loose.isEmpty() ? Optional.empty()
                : Optional.of(new com.juanp.custommobs.item.ItemRecipe(amount,
                        java.util.List.of(), java.util.Map.of(), java.util.List.copyOf(loose)));
    }

    private static String factionOf(YamlConfiguration yaml) {
        return factionOf(yaml, "faccion");
    }

    private static String factionOf(YamlConfiguration yaml, String key) {
        String value = yaml.getString(key);
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Borra un objeto del catalogo. */
    public boolean remove(String rawName) {
        String name = clean(rawName);
        if (name == null) {
            return false;
        }
        File file = this.fileOf(name);
        return file.exists() && file.delete();
    }

    /** Etiqueta legible del objeto, para que el archivo se entienda al abrirlo. */
    private static String labelOf(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    private File fileOf(String name) {
        return new File(this.folder, name + ".yml");
    }

    /**
     * Normaliza el nombre: minusculas, sin espacios y solo con caracteres seguros.
     * El nombre acaba siendo un nombre de archivo, asi que no puede llevar barras ni puntos.
     */
    private static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (!name.matches("[a-z0-9_-]{1,48}")) {
            return null;
        }
        return name;
    }
}
