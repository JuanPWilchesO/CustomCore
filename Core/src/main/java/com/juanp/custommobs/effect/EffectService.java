package com.juanp.custommobs.effect;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
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

/**
 * Efectos y pociones: se cargan de {@code effects.yml} y {@code potions.yml}.
 *
 * <p>Un efecto se define una vez y vale para los tres usos: pocion, skill de mob y uso de un
 * item. Aqui viven las definiciones, el estado activo por jugador y la fabrica del item de
 * pocion.
 */
public final class EffectService {

    private final CustomMobsPlugin plugin;
    private final SigiloService sigilo;
    private final NamespacedKey potionKey;
    private final Map<String, EffectSpec> effects = new LinkedHashMap<>();
    private final Map<String, PotionSpec> potions = new LinkedHashMap<>();
    /** Efectos activos: jugador -> (efecto -> instante de caducidad, en millis). */
    private final Map<UUID, Map<String, Long>> active = new ConcurrentHashMap<>();
    /** Avisos de fin: objetivo -> (efecto -> a quien avisar y que decir). */
    private final Map<UUID, Map<String, EndNotice>> notices = new ConcurrentHashMap<>();

    /** Un aviso de fin de efecto, dirigido a quien lanzo la skill. */
    private record EndNotice(UUID receiver, String message) {
    }

    /** Atributos que se admiten en la mecanica 'atributos'. */
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "max_health", "attack_damage", "attack_speed", "movement_speed",
            "armor", "armor_toughness", "knockback_resistance", "attack_knockback",
            "max_absorption", "safe_fall_distance");

    public EffectService(CustomMobsPlugin plugin, SigiloService sigilo) {
        this.plugin = plugin;
        this.sigilo = sigilo;
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
                    typeOf(section.getString("tipo")),
                    this.attributesOf(section.getConfigurationSection("atributos")),
                    this.parseHit(section.getConfigurationSection("golpe"))));
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
            if (material != Material.POTION && material != Material.SPLASH_POTION
                    && material != Material.LINGERING_POTION) {
                this.plugin.getLogger().warning("La pocion '" + key + "' tiene un material"
                        + " que no es una pocion (" + material + "); se usa POTION.");
                material = Material.POTION;
            }
            String id = key.toLowerCase(Locale.ROOT);
            var recipeSection = section.getConfigurationSection("receta");
            var recipe = recipeSection == null ? null
                    : com.juanp.custommobs.drop.ItemCatalog.parseRecipe(recipeSection).orElse(null);
            if (recipeSection != null && recipe == null) {
                this.plugin.getLogger().warning("La receta de la pocion '" + key
                        + "' no se pudo leer; revisa 'forma' e 'ingredientes'.");
            }
            this.potions.put(id, new PotionSpec(id,
                    section.getString("nombre", id),
                    material == null ? Material.POTION : material,
                    section.getString("color"),
                    effectId.toLowerCase(Locale.ROOT),
                    section.getInt("duracion", 0),
                    Math.max(0, section.getInt("radio", 0)),
                    blankToNull(section.getString("bando")),
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
        this.apply(player, spec, seconds, null, null, false);
    }

    /**
     * Aplica un efecto y, si se indica, avisa al jugador cuando caduque.
     *
     * @param notify     a quien se le manda el aviso de fin; {@code null} para no avisar
     * @param endMessage texto del aviso de fin; {@code null} o vacio para no avisar
     */
    public void apply(LivingEntity player, EffectSpec spec, int seconds,
                      UUID notify, String endMessage) {
        this.apply(player, spec, seconds, notify, endMessage, false);
    }

    /**
     * Aplica un efecto con aviso opcional y la opcion de revelar el sigilo al atacar.
     *
     * @param revealOnAttack solo para {@code sigilo}: si el jugador se muestra al hacer dano
     */
    public void apply(LivingEntity player, EffectSpec spec, int seconds,
                      UUID notify, String endMessage, boolean revealOnAttack) {
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
        this.rememberNotice(player.getUniqueId(), spec.id(), notify, endMessage);
        this.applyAttributes(player, spec);
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
        if ("sigilo".equals(spec.mechanic()) && player instanceof Player target) {
            this.sigilo.hide(target, spec.id(), duration, revealOnAttack);
        }
        // La barra se crea aqui, punto unico: vale igual para pocion, objeto, skill y splash.
        if (player instanceof Player online) {
            this.showBar(online, spec, duration);
        }
    }

    /**
     * Boss bar con la cuenta atras del efecto. Da igual el tipo: positivos, negativos y
     * neutros se muestran para quien los tiene. No se envia a nadie mas.
     */
    private void showBar(Player player, EffectSpec spec, int duration) {
        var status = this.plugin.status();
        if (status == null || duration <= 0) {
            return;
        }
        status.show(player, "effect:" + spec.id(), spec.name(), duration,
                barColor(spec), BarStyle.SOLID, 60, true);
    }

    private static BarColor barColor(EffectSpec spec) {
        String type = spec.type() == null ? "" : spec.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "negativo", "negative", "malo" -> BarColor.RED;
            case "neutro", "neutral" -> BarColor.WHITE;
            default -> BarColor.BLUE;
        };
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
                Map.Entry<String, Long> entry = iterator.next();
                EffectSpec spec = this.effects.get(entry.getKey());
                if (spec == null) {
                    continue;
                }
                boolean match = positive
                        ? "positivo".equals(spec.type())
                        : "negativo".equals(spec.type());
                if (match) {
                    this.removeAttributes(player, spec);
                    this.forgetNotice(player.getUniqueId(), entry.getKey());
                    iterator.remove();
                    removed++;
                    // Primero devolver la visibilidad: un fallo de la UI no debe dejar a nadie invisible.
                    if ("sigilo".equals(spec.mechanic()) && player instanceof Player online
                            && !this.hasMechanic(online, "sigilo")) {
                        this.sigilo.show(online);
                    }
                    if (player instanceof Player online) {
                        this.plugin.status().clear(online, "effect:" + entry.getKey());
                    }
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

    /** Lee el bloque `atributos:` de un efecto; ignora los que no esten permitidos. */
    private Map<Attribute, Double> attributesOf(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Attribute, Double> attributes = new LinkedHashMap<>();
        for (String raw : section.getKeys(false)) {
            String key = raw.toLowerCase(Locale.ROOT).trim().replace('-', '_');
            if (!ALLOWED_ATTRIBUTES.contains(key)) {
                this.plugin.getLogger().warning("Atributo no permitido en effects.yml: " + raw);
                continue;
            }
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute == null) {
                this.plugin.getLogger().warning("Atributo desconocido: " + raw);
                continue;
            }
            attributes.put(attribute, section.getDouble(raw));
        }
        return Map.copyOf(attributes);
    }

    private EffectSpec.HitEffect parseHit(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String potion = section.getString("efecto");
        if (potion == null || potion.isBlank()) {
            this.plugin.getLogger().warning("El bloque 'golpe:' necesita 'efecto'; se ignora");
            return null;
        }
        return new EffectSpec.HitEffect(
                potion.toLowerCase(Locale.ROOT).trim(),
                Math.max(1, section.getInt("nivel", 1)),
                Math.max(1, section.getInt("duracion", 3)));
    }

    /** Aplica o renueva los modificadores de atributo de un efecto. */
    private void applyAttributes(LivingEntity entity, EffectSpec spec) {
        for (Map.Entry<Attribute, Double> entry : spec.attributes().entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (instance == null) {
                continue;
            }
            NamespacedKey key = this.attributeKey(spec.id(), entry.getKey());
            this.removeModifier(instance, key);
            instance.addModifier(new AttributeModifier(key, entry.getValue(),
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /** Quita los modificadores de atributo de un efecto y ajusta la vida si hace falta. */
    private void removeAttributes(LivingEntity entity, EffectSpec spec) {
        for (Map.Entry<Attribute, Double> entry : spec.attributes().entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (instance != null) {
                this.removeModifier(instance, this.attributeKey(spec.id(), entry.getKey()));
            }
        }
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && entity.getHealth() > health.getValue()) {
            entity.setHealth(health.getValue());
        }
    }

    private void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
            if (modifier.getKey().equals(key)) {
                instance.removeModifier(modifier);
            }
        }
    }

    private NamespacedKey attributeKey(String effectId, Attribute attribute) {
        return new NamespacedKey(this.plugin, "attr_" + effectId + "_"
                + attribute.getKey().getKey());
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

    public java.util.Optional<EffectSpec.HitEffect> hitOf(LivingEntity player) {
        Map<String, Long> mine = this.active.get(player.getUniqueId());
        if (mine == null) {
            return java.util.Optional.empty();
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : mine.entrySet()) {
            if (entry.getValue() <= now) {
                continue;
            }
            EffectSpec spec = this.effects.get(entry.getKey());
            if (spec != null && "golpe_efecto".equals(spec.mechanic()) && spec.hit() != null) {
                return java.util.Optional.of(spec.hit());
            }

        }
        return java.util.Optional.empty();
    }

    /** Quita los efectos caducados y devuelve los ids que se han ido. */
    public List<String> expire(LivingEntity player) {
        List<String> gone = this.expire(player.getUniqueId());
        for (String id : gone) {
            EffectSpec spec = this.effects.get(id);
            if (spec != null) {
                this.removeAttributes(player, spec);
                if ("sigilo".equals(spec.mechanic()) && player instanceof Player online
                        && !this.hasMechanic(online, "sigilo")) {
                    this.sigilo.show(online);
                }
            }
        }
        return gone;
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
            this.notifyEnd(id, goneId);
        }
        if (mine.isEmpty()) {
            this.active.remove(id);
        }
        return gone;
    }

    /**
     * Quita un efecto concreto del jugador. Si era el sigilo, lo devuelve a la vista.
     *
     * @return {@code true} si estaba activo y se ha quitado
     */
    public boolean remove(LivingEntity player, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Map<String, Long> mine = this.active.get(player.getUniqueId());
        if (mine == null || mine.remove(key) == null) {
            return false;
        }
        if (mine.isEmpty()) {
            this.active.remove(player.getUniqueId());
        }
        EffectSpec spec = this.effects.get(key);
        if (spec == null) {
            return true;
        }
        this.removeAttributes(player, spec);
        this.forgetNotice(player.getUniqueId(), key);
        if (player instanceof Player online) {
            // Primero devolver la visibilidad: un fallo de la UI no debe dejar a nadie invisible.
            if ("sigilo".equals(spec.mechanic()) && !this.hasMechanic(online, "sigilo")) {
                this.sigilo.show(online);
            }
            this.plugin.status().clear(online, "effect:" + key);
        }
        return true;
    }

    /** Guarda (o borra, si no hay aviso) el aviso de fin de un efecto. */
    private void rememberNotice(UUID target, String effectId, UUID notify, String message) {
        if (notify == null || message == null || message.isBlank()) {
            this.forgetNotice(target, effectId);
            return;
        }
        this.notices.computeIfAbsent(target, key -> new ConcurrentHashMap<>())
                .put(effectId, new EndNotice(notify, message));
    }

    private void forgetNotice(UUID target, String effectId) {
        Map<String, EndNotice> mine = this.notices.get(target);
        if (mine == null) {
            return;
        }
        mine.remove(effectId);
        if (mine.isEmpty()) {
            this.notices.remove(target);
        }
    }

    /** Envia el aviso de fin, si lo habia, al jugador que lanzo la skill. */
    private void notifyEnd(UUID target, String effectId) {
        Map<String, EndNotice> mine = this.notices.get(target);
        if (mine == null) {
            return;
        }
        EndNotice notice = mine.remove(effectId);
        if (mine.isEmpty()) {
            this.notices.remove(target);
        }
        if (notice == null) {
            return;
        }
        Player player = Bukkit.getPlayer(notice.receiver());
        if (player != null && player.isOnline()) {
            this.plugin.status().notice(player, notice.message(), BarColor.WHITE);
        }
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
