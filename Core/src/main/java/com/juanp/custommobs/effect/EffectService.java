package com.juanp.custommobs.effect;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efectos y pociones: se cargan de {@code effects.yml} y {@code potions.yml}.
 *
 * <p>Un efecto se define una vez y vale para los tres usos: pocion, skill de mob y uso de un
 * item. Aqui viven las definiciones, el estado activo por jugador y la fabrica del item de
 * pocion.
 */
public final class EffectService {

    private final CustomMobsPlugin plugin;
    private final NamespacedKey potionKey;
    private final Map<String, EffectSpec> effects = new LinkedHashMap<>();
    private final Map<String, PotionSpec> potions = new LinkedHashMap<>();
    /** Efectos activos: jugador -> (efecto -> instante de caducidad, en millis). */
    private final Map<UUID, Map<String, Long>> active = new ConcurrentHashMap<>();

    public EffectService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.potionKey = new NamespacedKey(plugin, "potion");
    }

    // ------------------------------------------------------------------ carga

    public void reload() {
        this.effects.clear();
        this.potions.clear();

        YamlConfiguration effectYaml = this.load("effects.yml");
        for (String key : effectYaml.getKeys(false)) {
            ConfigurationSection section = effectYaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            this.effects.put(id, new EffectSpec(id,
                    section.getString("nombre", id),
                    section.getString("descripcion", ""),
                    Math.max(1, section.getInt("duracion", 300)),
                    section.getDouble("potencia", 0.0D),
                    // Se aceptan guiones y guiones bajos: 'quitar-positivos' y
                    // 'quitar_positivos' son la misma mecanica.
                    section.getString("mecanica", "").toLowerCase(Locale.ROOT).trim()
                            .replace('-', '_'),
                    section.getStringList("vanilla"),
                    typeOf(section.getString("tipo"))));
        }

        YamlConfiguration potionYaml = this.load("potions.yml");
        for (String key : potionYaml.getKeys(false)) {
            ConfigurationSection section = potionYaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String effectId = section.getString("efecto");
            if (effectId == null || !this.effects.containsKey(effectId.toLowerCase(Locale.ROOT))) {
                this.plugin.getLogger().warning("La pocion '" + key + "' apunta a un efecto que no existe: " + effectId);
                continue;
            }
            Material material = Material.matchMaterial(section.getString("material", "POTION"));
            String id = key.toLowerCase(Locale.ROOT);
            var recipeSection = section.getConfigurationSection("receta");
            var recipe = recipeSection == null ? null
                    : com.juanp.custommobs.drop.ItemCatalog.parseRecipe(recipeSection).orElse(null);
            this.potions.put(id, new PotionSpec(id,
                    section.getString("nombre", id),
                    material == null ? Material.POTION : material,
                    section.getString("color"),
                    effectId.toLowerCase(Locale.ROOT),
                    section.getInt("duracion", 0),
                    // Un valor vacio no es una restriccion: se normaliza a null.
                    blankToNull(section.getString("faccion")),
                    blankToNull(section.getString("clase")),
                    recipe));
        }
        this.plugin.getLogger().info("Efectos cargados: " + this.effects.size()
                + " | pociones: " + this.potions.size() + ".");
    }

    private YamlConfiguration load(String name) {
        File file = new File(this.plugin.getDataFolder(), name);
        if (!file.exists()) {
            this.plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------------ consulta

    public Optional<EffectSpec> effect(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(this.effects.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<PotionSpec> potion(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(this.potions.get(id.toLowerCase(Locale.ROOT)));
    }

    public Map<String, EffectSpec> effects() {
        return Map.copyOf(this.effects);
    }

    public Map<String, PotionSpec> potions() {
        return Map.copyOf(this.potions);
    }

    // ------------------------------------------------------------------ activos

    /** Aplica un efecto a un jugador. {@code seconds <= 0} usa la duracion del efecto. */
    public void apply(LivingEntity player, EffectSpec spec, int seconds) {
        // Las purgas son instantaneas: no se quedan en la lista de efectos activos.
        if ("quitar_positivos".equals(spec.mechanic())) {
            this.clearByType(player, true);
            return;
        }
        if ("quitar_negativos".equals(spec.mechanic())) {
            this.clearByType(player, false);
            return;
        }
        int duration = seconds > 0 ? seconds : spec.duration();
        this.active.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                .put(spec.id(), System.currentTimeMillis() + duration * 1000L);
        for (String raw : spec.vanilla()) {
            String[] parts = raw.split(":");
            PotionEffectType type = Registry.EFFECT.get(
                    NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                continue;
            }
            int level = parts.length > 1 ? Math.max(1, parseInt(parts[1], 1)) : 1;
            player.addPotionEffect(new PotionEffect(type, duration * 20, level - 1, true, true, true));
        }
    }

    /**
     * Quita los efectos de una categoria: primero los del plugin (su estado interno) y
     * despues los de Minecraft, que es donde entran tambien los que aplican otros plugins.
     *
     * <p>La categoria vanilla sale de {@link PotionEffectType#getCategory()}: BENEFICIAL
     * para los positivos y HARMFUL para los negativos. Los NEUTRAL no se tocan.
     *
     * @param positive {@code true} para positivos, {@code false} para negativos
     * @return cuantos efectos se quitaron
     */
    public int clearByType(LivingEntity player, boolean positive) {
        int removed = 0;
        Map<String, Long> mine = this.active.get(player.getUniqueId());
        if (mine != null) {
            Iterator<Map.Entry<String, Long>> iterator = mine.entrySet().iterator();
            while (iterator.hasNext()) {
                EffectSpec spec = this.effects.get(iterator.next().getKey());
                if (spec == null) {
                    continue;
                }
                boolean match = positive
                        ? "positivo".equals(spec.type())
                        : "negativo".equals(spec.type());
                if (match) {
                    iterator.remove();
                    removed++;
                }
            }
            if (mine.isEmpty()) {
                this.active.remove(player.getUniqueId());
            }
        }

        PotionEffectTypeCategory wanted = positive
                ? PotionEffectTypeCategory.BENEFICIAL
                : PotionEffectTypeCategory.HARMFUL;
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            if (effect.getType().getCategory() == wanted) {
                player.removePotionEffect(effect.getType());
                removed++;
            }
        }
        return removed;
    }

    /** Normaliza el tipo del yml; lo que no se reconoce se trata como positivo. */
    private static String typeOf(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "negativo", "negative", "harmful" -> "negativo";
            case "neutro", "neutral" -> "neutro";
            default -> "positivo";
        };
    }

    /** {@code true} si el jugador tiene activo algun efecto con esa mecanica. */
    public boolean hasMechanic(LivingEntity player, String mechanic) {
        Map<String, Long> mine = this.active.get(player.getUniqueId());
        if (mine == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : mine.entrySet()) {
            if (entry.getValue() <= now) {
                continue;
            }
            EffectSpec spec = this.effects.get(entry.getKey());
            if (spec != null && mechanic.equals(spec.mechanic())) {
                return true;
            }
        }
        return false;
    }

    /** La potencia de la mecanica activa, o 0 si no la tiene. */
    public double powerOf(LivingEntity player, String mechanic) {
        Map<String, Long> mine = this.active.get(player.getUniqueId());
        if (mine == null) {
            return 0.0D;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : mine.entrySet()) {
            if (entry.getValue() <= now) {
                continue;
            }
            EffectSpec spec = this.effects.get(entry.getKey());
            if (spec != null && mechanic.equals(spec.mechanic())) {
                return spec.power();
            }
        }
        return 0.0D;
    }

    /** Quita los efectos caducados y devuelve los ids que se han ido. */
    public List<String> expire(LivingEntity player) {
        return this.expire(player.getUniqueId());
    }

    /** Quita los efectos caducados de un uuid (vale para jugadores y para mobs). */
    public List<String> expire(java.util.UUID id) {
        Map<String, Long> mine = this.active.get(id);
        if (mine == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<String> gone = new ArrayList<>();
        for (Map.Entry<String, Long> entry : mine.entrySet()) {
            if (entry.getValue() <= now) {
                gone.add(entry.getKey());
            }
        }
        for (String goneId : gone) {
            mine.remove(goneId);
        }
        if (mine.isEmpty()) {
            this.active.remove(id);
        }
        return gone;
    }

    /** Efectos activos del jugador (ids), sin tocar los caducados. */
    public List<String> activeOf(LivingEntity player) {
        Map<String, Long> mine = this.active.get(player.getUniqueId());
        if (mine == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Long> entry : mine.entrySet()) {
            if (entry.getValue() > now) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ item

    /** Los uuid con algun efecto activo (para limpiar los de mobs). */
    public java.util.Set<UUID> activeIds() {
        return java.util.Set.copyOf(this.active.keySet());
    }

    public NamespacedKey potionKey() {
        return this.potionKey;
    }

    /** Fabrica el item de una pocion, con su marca en el NBT. */
    public ItemStack item(PotionSpec spec) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(Texts.color(spec.name()));
        if (meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(org.bukkit.potion.PotionType.WATER);
            if (spec.color() != null) {
                try {
                    org.bukkit.Color color = org.bukkit.DyeColor
                            .valueOf(spec.color().trim().toUpperCase(Locale.ROOT)).getColor();
                    potionMeta.setColor(color);
                } catch (IllegalArgumentException ignored) {
                    // Un color que no existe se ignora: la pocion funciona igual.
                }
            }
        }
        meta.getPersistentDataContainer().set(this.potionKey,
                PersistentDataType.STRING, spec.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Id de la pocion que representa ese item, si es una. */
    public Optional<String> potionOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(this.potionKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    /** Un texto vacio vale lo mismo que no estar: no impone restriccion. */
    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
