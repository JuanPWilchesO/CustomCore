package com.juanp.custommobs.mob;

import com.juanp.custommobs.combat.PlayerTargetMode;
import com.juanp.custommobs.item.EquipItem;
import com.juanp.custommobs.item.RecipeSpec;
import com.juanp.custommobs.drop.DropSpec;
import com.juanp.custommobs.skill.ChainOption;
import com.juanp.custommobs.skill.ChainSpec;
import com.juanp.custommobs.skill.ChainStep;
import com.juanp.custommobs.skill.SkillEffect;
import com.juanp.custommobs.skill.SkillSpec;
import com.juanp.custommobs.skill.SkillTarget;
import com.juanp.custommobs.skill.SkillTrigger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parsea un archivo yml a una {@link MobDefinition}. Nunca lanza: devuelve vacio si el yml es invalido. */
public final class MobLoader {

    /** Tope de seguridad: ninguna skill puede apuntar mas lejos que esto. */
    private static final double MAX_SKILL_RANGE = 32.0D;

    private MobLoader() {
    }

    public static Optional<MobDefinition> parse(String fileId, ConfigurationSection cfg) {
        String id = cfg.getString("id", fileId);
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        id = id.toLowerCase(Locale.ROOT).trim();

        MobCategory category = MobCategory.parse(cfg.getString("category"));
        if (category == null) {
            return Optional.empty();
        }

        EntityType type = parseEnum(EntityType.class, cfg.getString("type"));
        if (type == null) {
            return Optional.empty();
        }

        Material egg = parseEgg(cfg, type, category);
        // Los mobs con dueno siempre se craftean desde un huevo; los de servidor pueden no tener.
        if (category.owned() && egg == null) {
            return Optional.empty();
        }

        Optional<RecipeSpec> recipe = parseRecipe(cfg, category);
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Double> attributes = new LinkedHashMap<>();
        ConfigurationSection attributesSection = cfg.getConfigurationSection("attributes");
        if (attributesSection != null) {
            for (String key : attributesSection.getKeys(false)) {
                attributes.put(key.toLowerCase(Locale.ROOT), attributesSection.getDouble(key));
            }
        }

        Map<EquipmentSlot, EquipItem> equipment = new LinkedHashMap<>();
        ConfigurationSection equipmentSection = cfg.getConfigurationSection("equipment");
        if (equipmentSection != null) {
            for (String rawSlot : equipmentSection.getKeys(false)) {
                EquipmentSlot slot = slotOf(rawSlot);
                if (slot == null) {
                    continue;
                }
                ConfigurationSection itemSection = equipmentSection.getConfigurationSection(rawSlot);
                if (itemSection == null) {
                    continue;
                }
                parseItem(itemSection).ifPresent(item -> equipment.put(slot, item));
            }
        }

        // El ancla es el dueno o el bloque de aparicion. Los mobs de servidor, al no
        // tener dueno, siempre quedan anclados al punto donde aparecen.
        LeashSettings leash = parseLeash(cfg.getConfigurationSection("leash"), category);
        int respawnSeconds = category == MobCategory.SERVER ? Math.max(0, cfg.getInt("respawn-seconds", 0)) : 0;

        // Sin valor, o con valor invalido, se hereda la politica global.
        PlayerTargetMode targetMode = PlayerTargetMode.parse(cfg.getString("targets.players"));

        String faction = null;
        Attitude attitude = null;
        if (category == MobCategory.SERVER) {
            faction = normalize(cfg.getString("faction"));
            attitude = Attitude.parse(cfg.getString("attitude"));
            if (attitude == null) {
                return Optional.empty();
            }
        }

        DisguiseSpec disguise = parseDisguise(cfg.getConfigurationSection("disguise"));
        MobSounds sounds = parseSounds(cfg.getConfigurationSection("sounds"));
        List<SkillSpec> skills = parseSkills(cfg);
        List<DropSpec> drops = parseDrops(cfg);
        // Los mobs de rol no deberian arder al amanecer; el resto conserva el vanilla.
        boolean burnsInDaylight = cfg.getBoolean("burn-in-daylight", category == MobCategory.PLAYER);
        boolean usesAi = cfg.getBoolean("ai", true);

        List<String> lore = new ArrayList<>();
        for (String line : cfg.getStringList("lore")) {
            lore.add(Texts.color(line));
        }

        MountSpec mount = parseMount(cfg.getConfigurationSection("mount"), category);
        NaturalSpawn spawn = parseNatural(cfg.getConfigurationSection("spawn"), category);

        return Optional.of(new MobDefinition(
                id,
                Texts.color(cfg.getString("display-name", id)),
                type,
                egg,
                recipe.get(),
                cfg.getBoolean("glow", false),
                Map.copyOf(attributes),
                Map.copyOf(equipment),
                targetMode,
                leash,
                List.copyOf(lore),
                category,
                faction,
                attitude,
                disguise,
                sounds,
                burnsInDaylight,
                usesAi,
                respawnSeconds,
                skills,
                drops,
                cfg.getBoolean("clear-vanilla-drops", false),
                chunkRadius(cfg),
                mount,
                spawn,
                cfg.getBoolean("village-friendly", false),
                parseImmune(cfg),
                cfg.getBoolean("attacks-monsters", false)
        ));
    }

    /**
     * Lee la seccion {@code steps} de una cadena: la conversacion, paso a paso.
     *
     * <p>Cada paso es un mapa con su {@code id} y, segun lo que haga, {@code message},
     * {@code options}, {@code next}, {@code end}, {@code pay}, {@code no-pay},
     * {@code console-command} o {@code player-command}. Un paso sin id se descarta.
     */
    private static ChainSpec parseChain(Map<?, ?> entry) {
        Object raw = entry.get("steps");
        if (!(raw instanceof List<?> list)) {
            return ChainSpec.EMPTY;
        }
        List<ChainStep> steps = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String stepId = normalize(asString(map.get("id")));
            if (stepId == null) {
                continue;
            }
            steps.add(new ChainStep(
                    stepId,
                    asString(map.get("message")),
                    parseChainOptions(map.get("options")),
                    normalize(asString(map.get("next"))),
                    Boolean.TRUE.equals(map.get("end")),
                    Math.max(0.0D, asNumber(map.get("pay"), 0.0D)),
                    normalize(asString(map.get("no-pay"))),
                    asString(map.get("console-command")),
                    asString(map.get("player-command"))));
        }
        return new ChainSpec(List.copyOf(steps));
    }

    /** Los botones clicables de un paso: etiqueta y a donde llevan. */
    private static List<ChainOption> parseChainOptions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ChainOption> options = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String label = asString(map.get("label"));
            if (label == null || label.isBlank()) {
                continue;
            }
            options.add(new ChainOption(label, normalize(asString(map.get("next"))),
                    Boolean.TRUE.equals(map.get("end"))));
        }
        return List.copyOf(options);
    }

    /**
     * Lee la lista {@code immune}: causas de dano que el mob ignora, en cualquier categoria.
     *
     * <p>Se escribe el nombre de la causa de Bukkit en minusculas ({@code fall}, {@code fire},
     * {@code drowning}, {@code magic}...). {@code potion} es un atajo de {@code magic}, que es
     * la causa del dano de las pociones. Lo que no se reconoce se avisa y se descarta: nunca
     * tumba la carga del mob.
     */
    private static Set<EntityDamageEvent.DamageCause> parseImmune(ConfigurationSection cfg) {
        List<String> raw = cfg.getStringList("immune");
        if (raw.isEmpty()) {
            return Set.of();
        }
        Set<EntityDamageEvent.DamageCause> causes = EnumSet.noneOf(EntityDamageEvent.DamageCause.class);
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String clean = value.trim().toLowerCase(Locale.ROOT);
            if (clean.equals("potion")) {
                causes.add(EntityDamageEvent.DamageCause.MAGIC);
                continue;
            }
            try {
                causes.add(EntityDamageEvent.DamageCause.valueOf(clean.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                Bukkit.getLogger().warning("[CustomMobs] 'immune' no reconoce '" + value
                        + "' en un mob; se descarta esa causa.");
            }
        }
        return Set.copyOf(causes);
    }

    /**
     * Huevo base. Si el yml no lo trae y el mob es de servidor, se usa el huevo natural
     * del tipo de entidad (ZOMBIE -> ZOMBIE_SPAWN_EGG) para poder invocarlo a mano.
     */
    private static Material parseEgg(ConfigurationSection cfg, EntityType type, MobCategory category) {
        Material egg = Material.matchMaterial(cfg.getString("egg", ""));
        if (egg != null && egg.isItem()) {
            return egg;
        }
        // Los de jugador necesitan un huevo declarado; las monturas pueden caer al huevo
        // natural de su tipo, como los de servidor.
        if (category == MobCategory.PLAYER) {
            return null;
        }
        try {
            Material natural = Material.matchMaterial(type.name() + "_SPAWN_EGG");
            return natural != null && natural.isItem() ? natural : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Lee la receta. Formatos aceptados, en orden de prioridad:
     * <ol>
     *   <li>{@code recipe.extras} + {@code recipe.amount} (formato actual)</li>
     *   <li>{@code catalyst} (compatibilidad con el formato anterior)</li>
     *   <li>Sin nada: huevo + diamante (mob de jugador) o crafteo desactivado (mob de servidor)</li>
     * </ol>
     *
     * @return la receta, o vacio si la seccion existe pero es invalida
     */
    private static Optional<RecipeSpec> parseRecipe(ConfigurationSection cfg, MobCategory category) {
        ConfigurationSection section = cfg.getConfigurationSection("recipe");
        if (section == null) {
            if (category == MobCategory.SERVER) {
                // Un mob de servidor aparece por comando o de forma natural, no se craftea.
                return Optional.of(new RecipeSpec(List.of(), 0));
            }
            Material legacy = Material.matchMaterial(cfg.getString("catalyst", ""));
            if (legacy != null && legacy.isItem()) {
                return Optional.of(new RecipeSpec(List.of(legacy), 1));
            }
            return Optional.of(RecipeSpec.DEFAULT);
        }

        List<Material> extras = new ArrayList<>();
        for (String raw : section.getStringList("extras")) {
            Material material = Material.matchMaterial(raw);
            if (material == null || !material.isItem()) {
                return Optional.empty();
            }
            extras.add(material);
        }
        if (extras.isEmpty() || extras.size() > 8) {
            return Optional.empty();
        }
        return Optional.of(new RecipeSpec(List.copyOf(extras), section.getInt("amount", 1)));
    }

    private static LeashSettings parseLeash(ConfigurationSection section, MobCategory category) {
        if (section == null) {
            return LeashSettings.NONE;
        }
        AnchorMode anchor = category == MobCategory.SERVER
                ? AnchorMode.POINT
                : AnchorMode.parse(section.getString("anchor"));
        if (anchor == null) {
            return LeashSettings.NONE;
        }
        return new LeashSettings(
                Math.max(0.0D, section.getDouble("max-distance", 0.0D)),
                Math.max(0.0D, section.getDouble("teleport-distance", 0.0D)),
                Math.max(0.1D, section.getDouble("return-speed", 1.0D)),
                anchor
        );
    }

    /**
     * Lee la lista {@code skills}. Una entrada invalida se omite con aviso implicito:
     * nunca hace fallar la carga del mob entero. Los ids repetidos se descartan.
     */
    private static List<SkillSpec> parseSkills(ConfigurationSection cfg) {
        List<Map<?, ?>> raw = cfg.getMapList("skills");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<SkillSpec> skills = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> entry : raw) {
            SkillSpec skill = parseSkill(entry);
            if (skill != null && seen.add(skill.id())) {
                skills.add(skill);
            }
        }
        return List.copyOf(skills);
    }

    /**
     * Lee la lista {@code drops}. Una entrada sin {@code material} valido ni {@code item}
     * se descarta sola: no tumba la carga del mob.
     */
    private static List<DropSpec> parseDrops(ConfigurationSection cfg) {
        List<Map<?, ?>> raw = cfg.getMapList("drops");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<DropSpec> drops = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            DropSpec spec = parseDrop(entry);
            if (spec != null) {
                drops.add(spec);
            }
        }
        return List.copyOf(drops);
    }

    private static DropSpec parseDrop(Map<?, ?> entry) {
        double chance = Math.min(1.0D, Math.max(0.0D, asNumber(entry.get("chance"), 1.0D)));
        int min = (int) Math.max(1L, Math.round(asNumber(entry.get("min"),
                asNumber(entry.get("amount"), 1.0D))));
        int max = (int) Math.max(min, Math.round(asNumber(entry.get("max"), min)));
        String label = asString(entry.get("etiqueta"));

        // 'item' es el NOMBRE de un objeto del catalogo (items/<nombre>.yml), no el
        // objeto en si: un item con NBT no se puede escribir a mano.
        String name = asString(entry.get("item"));
        if (name != null && !name.isBlank()) {
            return new DropSpec(null, name.toLowerCase(Locale.ROOT).trim(), min, max, chance, label);
        }
        Material material = Material.matchMaterial(asString(entry.get("material")));
        if (material == null || !material.isItem()) {
            return null;
        }
        return new DropSpec(material, null, min, max, chance, label);
    }

    private static SkillSpec parseSkill(Map<?, ?> entry) {
        String id = normalize(asString(entry.get("id")));
        SkillEffect effect = SkillEffect.parse(asString(entry.get("effect")));
        SkillTarget target = SkillTarget.parse(asString(entry.get("target")));
        if (id == null || effect == null || target == null) {
            return null;
        }
        String message = asString(entry.get("message"));
        // Si el yml no dice nada, la skill es pasiva: el comportamiento de siempre.
        SkillTrigger parsed = SkillTrigger.parse(asString(entry.get("mode")));
        SkillTrigger trigger = parsed == null ? SkillTrigger.DEFAULT : parsed;

        // Los comandos y las cadenas solo valen en una interaccion. Un mob que ejecuta comandos
        // por su cuenta —en pasiva, o en mitad de un combate— es un agujero de seguridad: se
        // descarta la skill y se avisa, sin tumbar la carga del mob.
        if ((effect == SkillEffect.CONSOLE_COMMAND || effect == SkillEffect.PLAYER_COMMAND)
                && trigger != SkillTrigger.INTERACT) {
            Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' usa " + effect
                    + " pero no es de interaccion; se descarta. Los comandos solo van con"
                    + " 'mode: interaccion' o dentro de una cadena de interaccion.");
            return null;
        }
        if (effect == SkillEffect.CHAIN && trigger != SkillTrigger.INTERACT) {
            Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' es una cadena pero no"
                    + " es de interaccion; se descarta. Una cadena empieza con un clic.");
            return null;
        }
        return new SkillSpec(
                id,
                effect,
                target,
                trigger,
                Math.min(MAX_SKILL_RANGE, Math.max(1.0D, asNumber(entry.get("range"), 8.0D))),
                (int) Math.max(0L, Math.round(asNumber(entry.get("cooldown-seconds"), 5.0D))),
                Math.min(1.0D, Math.max(0.0D, asNumber(entry.get("chance"), 1.0D))),
                Math.max(0.0D, asNumber(entry.get("amount"), 0.0D)),
                message == null ? null : Texts.color(message),
                normalize(asString(entry.get("potion"))),
                (int) Math.max(1L, Math.round(asNumber(entry.get("potion-duration-seconds"), 5.0D))),
                (int) Math.max(0L, Math.round(asNumber(entry.get("potion-amplifier"), 0.0D))),
                normalize(asString(entry.get("summon"))),
                asString(entry.get("command")),
                effect == SkillEffect.CHAIN ? parseChain(entry) : ChainSpec.EMPTY
        );
    }

    /**
     * Radio de chunks propio del mob para su ancla de bloque. Si no lo define, se usa el
     * global del config: {@code null} significa "lo que diga el config".
     */
    private static Integer chunkRadius(ConfigurationSection cfg) {
        if (!cfg.isInt("chunk-radius")) {
            return null;
        }
        return Math.max(0, cfg.getInt("chunk-radius"));
    }

    /**
     * Lee la seccion {@code spawn}: aparicion aleatoria por el mundo.
     *
     * <p>Solo tiene sentido en mobs de servidor. Un mob de servidor sin seccion no aparece
     * solo, que es como se ha comportado siempre.
     */
    private static NaturalSpawn parseNatural(ConfigurationSection section, MobCategory category) {
        if (section == null || !section.getBoolean("natural", false)) {
            return NaturalSpawn.NONE;
        }
        if (category != MobCategory.SERVER) {
            // Los mobs con dueno se invocan con su huevo; los naturales son cosa del mundo.
            return NaturalSpawn.NONE;
        }
        ConfigurationSection group = section.getConfigurationSection("group");
        int groupMin = group == null ? 1 : Math.max(1, group.getInt("min", 1));
        int groupMax = group == null ? groupMin : Math.max(groupMin, group.getInt("max", groupMin));
        ConfigurationSection light = section.getConfigurationSection("light");
        ConfigurationSection y = section.getConfigurationSection("y");
        ConfigurationSection distance = section.getConfigurationSection("distance");
        return new NaturalSpawn(
                true,
                lowerList(section.getStringList("worlds")),
                lowerList(section.getStringList("biomes")),
                groupMin,
                groupMax,
                Math.min(1.0D, Math.max(0.0D, section.getDouble("chance", 0.1D))),
                section.getString("time", "any").toLowerCase(Locale.ROOT).trim(),
                light == null ? 0 : light.getInt("min", 0),
                light == null ? 15 : light.getInt("max", 15),
                y == null ? 0 : y.getInt("min", 0),
                y == null ? 320 : y.getInt("max", 320),
                distance == null ? 24.0D : Math.max(0.0D, distance.getDouble("min", 24.0D)),
                distance == null ? 96.0D : Math.max(1.0D, distance.getDouble("max", 96.0D)),
                Math.max(1, section.getInt("cap", 20)),
                section.getString("allowed-region", ""),
                lowerList(section.getStringList("deny-regions")));
    }

    /** Lista de textos en minusculas, sin vacios. */
    private static List<String> lowerList(List<String> raw) {
        List<String> values = new ArrayList<>(raw.size());
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                values.add(value.toLowerCase(Locale.ROOT).trim());
            }
        }
        return List.copyOf(values);
    }

    /**
     * Lee la seccion {@code mount} de una montura.
     *
     * <p>Sin seccion, una montura sale domesticada y ensillada, que es lo que hace falta
     * para poder montarla. La seccion solo tiene sentido con {@code category: mount}; en
     * cualquier otra categoria se ignora, porque el resto de mobs no se monta.
     */
    private static MountSpec parseMount(ConfigurationSection section, MobCategory category) {
        if (!category.mountable()) {
            return null;
        }
        if (section == null) {
            return MountSpec.DEFAULT;
        }
        return new MountSpec(
                trimOrNull(section.getString("color")),
                trimOrNull(section.getString("style")),
                section.getBoolean("saddled", true),
                section.getBoolean("tamed", true),
                Material.matchMaterial(section.getString("armor", "")),
                parseMountEffects(section),
                section.getBoolean("frost-walker", false),
                Math.max(0, Math.min(4, section.getInt("frost-radius", 2))),
                Math.max(0, Math.min(32, section.getInt("frost-ahead", 16))));
    }

    /** Los efectos constantes que reparte la montura: al mob, al jinete o a quien este cerca. */
    private static List<MountEffect> parseMountEffects(ConfigurationSection section) {
        List<Map<?, ?>> raw = section.getMapList("effects");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<MountEffect> effects = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String potion = trimOrNull(asString(entry.get("potion")));
            if (potion == null) {
                continue;
            }
            effects.add(new MountEffect(
                    potion,
                    trimOrNull(asString(entry.get("to"))),
                    (int) Math.max(0L, Math.round(asNumber(entry.get("amplifier"), 0.0D))),
                    Math.max(0.0D, asNumber(entry.get("radius"), 8.0D)),
                    Boolean.parseBoolean(String.valueOf(entry.get("visible")))));
        }
        return List.copyOf(effects);
    }

    /** Texto sin espacios sobrantes; {@code null} si no dice nada. */
    private static String trimOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double asNumber(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static DisguiseSpec parseDisguise(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return DisguiseSpec.NONE;
        }
        String type = normalize(section.getString("type"));
        if (type == null) {
            type = "player";
        }
        return new DisguiseSpec(
                true,
                type,
                normalize(section.getString("skin")),
                normalize(section.getString("skin-url")),
                section.getBoolean("show-name", true)
        );
    }

    private static MobSounds parseSounds(ConfigurationSection section) {
        if (section == null) {
            return MobSounds.NONE;
        }
        int interval = Math.max(0, section.getInt("ambient.interval-seconds", 8));
        return new MobSounds(
                parseSound(section.getConfigurationSection("ambient")),
                interval,
                parseSound(section.getConfigurationSection("hurt")),
                parseSound(section.getConfigurationSection("death")),
                parseSound(section.getConfigurationSection("attack"))
        );
    }

    private static SoundSpec parseSound(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String name = normalize(section.getString("sound"));
        if (name == null) {
            return null;
        }
        return new SoundSpec(
                name,
                clamp((float) section.getDouble("volume", 1.0D), 0.0F, 8.0F),
                clamp((float) section.getDouble("pitch", 1.0D), 0.5F, 2.0F)
        );
    }

    private static Optional<EquipItem> parseItem(ConfigurationSection section) {
        float dropChance = (float) section.getDouble("drop-chance", 0.0D);

        // 'item' apunta al catalogo (items/<nombre>.yml): la via para equipar un objeto
        // con NBT, que el yml no puede escribir.
        String itemName = normalize(section.getString("item"));
        if (itemName != null) {
            return Optional.of(new EquipItem(null, "", List.of(), false, false, Map.of(), dropChance, itemName));
        }

        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null || !material.isItem()) {
            return Optional.empty();
        }

        Map<String, Integer> enchants = new LinkedHashMap<>();
        ConfigurationSection enchantsSection = section.getConfigurationSection("enchants");
        if (enchantsSection != null) {
            for (String name : enchantsSection.getKeys(false)) {
                enchants.put(name.toLowerCase(Locale.ROOT), enchantsSection.getInt(name, 1));
            }
        }

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(Texts.color(line));
        }

        return Optional.of(new EquipItem(
                material,
                Texts.color(section.getString("name", "")),
                List.copyOf(lore),
                section.getBoolean("unbreakable", false),
                section.getBoolean("glint", false),
                Map.copyOf(enchants),
                dropChance,
                null
        ));
    }

    /** Acepta tanto nombres cortos (helmet) como los de Bukkit (HEAD). */
    public static EquipmentSlot slotOf(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "helmet", "head" -> EquipmentSlot.HEAD;
            case "chestplate", "chest", "body" -> EquipmentSlot.CHEST;
            case "leggings", "legs" -> EquipmentSlot.LEGS;
            case "boots", "feet" -> EquipmentSlot.FEET;
            case "main-hand", "hand", "weapon" -> EquipmentSlot.HAND;
            case "off-hand", "shield" -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.toLowerCase(Locale.ROOT).trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
