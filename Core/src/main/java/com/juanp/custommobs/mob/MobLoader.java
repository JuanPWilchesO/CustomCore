package com.juanp.custommobs.mob;

import com.juanp.custommobs.combat.PlayerTargetMode;
import com.juanp.custommobs.item.EquipItem;
import com.juanp.custommobs.item.RecipeSpec;
import com.juanp.custommobs.drop.DropSpec;
import com.juanp.custommobs.model.AnimationSpec;
import com.juanp.custommobs.model.ModelAnimation;
import com.juanp.custommobs.model.ModelSpec;
import com.juanp.custommobs.skill.ChainOption;
import com.juanp.custommobs.skill.ChainSpec;
import com.juanp.custommobs.skill.ChainStep;
import com.juanp.custommobs.skill.SkillCatalog;
import com.juanp.custommobs.skill.SkillEffect;
import com.juanp.custommobs.skill.SkillSpec;
import com.juanp.custommobs.skill.SkillTarget;
import com.juanp.custommobs.skill.SkillTargetType;
import com.juanp.custommobs.skill.MessageDisplay;
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
    private static final double MAX_SKILL_RANGE = 128.0D;

    private MobLoader() {
    }

    public static Optional<MobDefinition> parse(String fileId, ConfigurationSection cfg,
                                                 SkillCatalog catalog) {
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
        ModelSpec model = parseModel(cfg.getConfigurationSection("model"));
        MobSounds sounds = parseSounds(cfg.getConfigurationSection("sounds"));
        List<SkillSpec> skills = parseSkills(cfg, catalog);
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
                model,
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
                cfg.getBoolean("attacks-monsters", false),
                // Por defecto SI se transforma: es el comportamiento de siempre, y asi no cambia
                // nada en los mobs que ya existian.
                cfg.getBoolean("transformable", true)
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
                    normalize(asString(map.containsKey("pay-item") ? map.get("pay-item")
                            : map.containsKey("pay_item") ? map.get("pay_item")
                            : map.get("pagar-objeto"))),
                    (int) Math.max(1L, Math.round(asNumber(map.get("pay-item-count"),
                            asNumber(map.get("cantidad-objeto"), 1.0D)))),
                    (int) Math.max(0L, Math.round(asNumber(map.get("pay-xp"),
                            asNumber(map.get("niveles"), 0.0D)))),
                    normalize(asString(map.get("no-pay"))),
                    normalize(asString(map.get("faction"))),
                    normalize(asString(map.get("no-faction"))),
                    normalize(asString(map.containsKey("clase") ? map.get("clase") : map.get("class"))),
                    normalize(asString(map.containsKey("no-clase") ? map.get("no-clase") : map.get("no-class"))),
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
        // Un mob invulnerable ignora TODAS las causas. Se resuelve aqui, en la misma lista que
        // ya se consulta al recibir dano: asi no hay un camino paralelo que se pueda olvidar.
        if (cfg.getBoolean("invulnerable", false)) {
            return Set.copyOf(EnumSet.allOf(EntityDamageEvent.DamageCause.class));
        }
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
    private static List<SkillSpec> parseSkills(ConfigurationSection cfg, SkillCatalog catalog) {
        List<Map<?, ?>> raw = cfg.getMapList("skills");
        if (raw.isEmpty()) {
            return List.of();
        }
        List<SkillSpec> skills = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> entry : raw) {
            // 'ref:' apunta a un archivo de skills/. Las skills inline siguen valiendo.
            String ref = normalize(asString(entry.get("ref")));
            SkillSpec skill = ref == null
                    ? parseSkill(entry)
                    : (catalog == null ? null : catalog.get(ref).orElse(null));
            if (skill == null) {
                // Una skill que no se puede leer se descartaba en silencio, y eso es una trampa:
                // la definicion carga, pero la skill no existe y nada lo dice. Que se sepa.
                Bukkit.getLogger().warning("[CustomMobs] Una skill no se pudo leer y se descarta:"
                        + " id=" + entry.get("id") + " effect=" + entry.get("effect")
                        + " target=" + entry.get("target") + " mode=" + entry.get("mode"));
                continue;
            }
            if (seen.add(skill.id())) {
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

    /** Parseo compartido por los mobs y por el catalogo de {@code skills/}. */
    public static SkillSpec parseSkill(Map<?, ?> entry) {
        String id = normalize(asString(entry.get("id")));
        SkillEffect effect = SkillEffect.parse(asString(entry.get("effect")));
        SkillTarget target = SkillTarget.parse(asString(entry.get("target")));
        SkillTargetType targetType = SkillTargetType.parse(asString(entry.get("target-type")));
        if (targetType == null) {
            targetType = SkillTargetType.parse(asString(entry.get("target_type")));
        }
        if (id == null || effect == null) {
            return null;
        }
        if (target == null) {
            // Una cadena, una invocacion o un comando no van contra un objetivo: si el yml no
            // dice 'target', se toma 'self'. Antes la skill se descartaba EN SILENCIO, y era una
            // trampa: la definicion cargaba, pero la skill no existia y nada avisaba.
            boolean sinObjetivo = effect == SkillEffect.CHAIN || effect == SkillEffect.SUMMON
                    || effect == SkillEffect.CONSOLE_COMMAND || effect == SkillEffect.PLAYER_COMMAND;
            if (!sinObjetivo) {
                Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' no dice 'target';"
                        + " se descarta. Con este efecto hace falta un objetivo.");
                return null;
            }
            target = SkillTarget.SELF;
        }
        if (targetType != null) {
            // 'impacto' solo admite LOOK: es una entidad, no un area.
            boolean allowed = target.isRelation() || target == SkillTarget.IMPACT;
            if (!allowed) {
                Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' usa"
                        + " 'target-type' pero su 'target' es " + target
                        + "; debe ser ENEMIES, ALLIES, SELF o IMPACT. Se descarta.");
                return null;
            }
            if (target == SkillTarget.IMPACT && targetType == SkillTargetType.RADIUS) {
                Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' apunta a"
                        + " 'impacto' con 'target-type: radius'; impacto es una sola"
                        + " entidad. Se descarta.");
                return null;
            }
        } else if (target == SkillTarget.LOOK) {
            // Compatibilidad: un LOOK viejo no filtraba bando. Ahora se asume ENEMIES
            // para que una skill de mirada no haga fuego amigo por accidente.
            targetType = SkillTargetType.LOOK;
            target = SkillTarget.ENEMIES;
        } else if (target == SkillTarget.ENEMIES || target == SkillTarget.ALLIES
                || target == SkillTarget.SELF) {
            // Las skills viejas ENEMIES/ALLIES/SELF ya eran de radio.
            targetType = SkillTargetType.RADIUS;
        }
        String message = asString(entry.get("message"));
        String startMessage = messageOf(entry, "inicio", "mensaje-inicio",
                "start-message", "start");
        String endMessage = messageOf(entry, "fin", "mensaje-fin",
                "end-message", "end");
        String cooldownMessage = messageOf(entry, "cooldown-aviso", "aviso-cooldown",
                "mensaje-cooldown", "cooldown-message");
        // Si el yml no dice nada, la skill es pasiva: el comportamiento de siempre.
        SkillTrigger parsed = SkillTrigger.parse(asString(entry.get("mode")));
        SkillTrigger trigger = parsed == null ? SkillTrigger.DEFAULT : parsed;

        // Los comandos y las cadenas solo valen en una interaccion. Un mob que ejecuta comandos
        // por su cuenta —en pasiva, o en mitad de un combate— es un agujero de seguridad: se
        // descarta la skill y se avisa, sin tumbar la carga del mob.
        boolean commandLike = effect == SkillEffect.CONSOLE_COMMAND
                || effect == SkillEffect.PLAYER_COMMAND || effect == SkillEffect.CHAIN;
        if (commandLike && trigger != SkillTrigger.INTERACT && trigger != SkillTrigger.ITEM) {
            Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' usa " + effect
                    + " pero no es de interaccion ni de item; se descarta. Los comandos van"
                    + " con 'mode: interaccion' o 'mode: item'; las cadenas, solo con"
                    + " 'mode: interaccion'.");
            return null;
        }
        if (trigger == SkillTrigger.IMPACT) {
            // El impacto solo resuelve una entidad golpeada o un area alrededor del golpe.
            if (target == SkillTarget.OWNER || target == SkillTarget.TARGET
                    || target == SkillTarget.LOOK) {
                Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' es de"
                        + " impacto y su 'target' es " + target + "; usa 'impacto', 'self',"
                        + " 'enemies' o 'allies'. Se descarta.");
                return null;
            }
            if (effect == SkillEffect.SUMMON) {
                Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' es de"
                        + " impacto y usa 'summon'; ese efecto no se admite al impactar."
                        + " Se descarta.");
                return null;
            }
        }
        if (effect == SkillEffect.CHAIN && trigger != SkillTrigger.INTERACT) {
            Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' es una cadena y"
                    + " solo vale con 'mode: interaccion'; se descarta.");
            return null;
        }
        // matchMaterial(null) lanza IllegalArgumentException en Paper: solo se llama
        // cuando la clave existe y no esta vacia.
        String blockName = asString(entry.get("block"));
        org.bukkit.Material block = null;
        if (blockName != null && !blockName.isBlank()) {
            block = Material.matchMaterial(blockName);
        }
        if (block != null && (!block.isBlock() || block.isAir())) {
            block = null;
        }
        if (effect == SkillEffect.BLOCK_BELOW && block == null) {
            Bukkit.getLogger().warning("[CustomMobs] La skill '" + id + "' es block_below pero"
                    + " no tiene un 'block' valido; se descarta.");
            return null;
        }
        int blockRadius = (int) Math.min(5L, Math.max(0L,
                Math.round(asNumber(entry.get("block-radius"), 0.0D))));
        int blockDepth = (int) Math.min(5L, Math.max(1L,
                Math.round(asNumber(entry.get("block-depth"), 1.0D))));
        boolean breakBlocks = asBoolean(entry.get("break-blocks"))
                || asBoolean(entry.get("romper-bloques"));
        boolean throughBlocks = asBoolean(entry.get("through-blocks"))
                || asBoolean(entry.get("through_blocks"))
                || asBoolean(entry.get("atravesar-bloques"))
                || asBoolean(entry.get("atravesar_bloques"));
        double distance = Math.min(SkillSpec.MAX_DISTANCE,
                Math.max(SkillSpec.MIN_DISTANCE, asNumber(entry.get("distance"), 1.0D)));
        boolean cancelOnHit = asBoolean(entry.get("cancel-on-hit"))
                || asBoolean(entry.get("cancel_on_hit"));
        boolean revealOnAttack = asBoolean(entry.get("reveal-on-attack"))
                || asBoolean(entry.get("reveal_on_attack"));
        String displayRaw = asString(entry.get("message-display"));
        if (displayRaw == null) {
            displayRaw = asString(entry.get("message_display"));
        }
        MessageDisplay messageDisplay = MessageDisplay.parse(displayRaw);
        if (messageDisplay == null) {
            messageDisplay = MessageDisplay.AUTO;
        }
        return new SkillSpec(
                id,
                effect,
                target,
                targetType,
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
                effect == SkillEffect.CHAIN ? parseChain(entry) : ChainSpec.EMPTY,
                asBoolean(entry.get("porcentaje")) || asBoolean(entry.get("percent")),
                block,
                blockRadius,
                blockDepth,
                breakBlocks,
                throughBlocks,
                distance,
                startMessage,
                endMessage,
                cooldownMessage,
                cancelOnHit,
                revealOnAttack,
                messageDisplay
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

    /** Primer mensaje no vacio de las claves dadas, ya con colores. */
    private static String messageOf(Map<?, ?> entry, String... keys) {
        for (String key : keys) {
            String raw = asString(entry.get(key));
            if (raw != null && !raw.isBlank()) {
                return Texts.color(raw);
            }
        }
        return null;
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    /** Numero opcional: {@code null} si la clave no existe o no es numerica. */
    private static Double number(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double asNumber(Object value, double fallback) {        if (value instanceof Number number) {
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

    /**
     * Lee la seccion {@code model:} (BetterModel).
     *
     * <p>Sin seccion, o con {@code enabled: false}, el mob se ve como siempre. Un bloque
     * presente pero sin {@code name} tampoco engancha nada: el nombre es lo unico que no se
     * puede adivinar.
     */
    private static ModelSpec parseModel(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return ModelSpec.NONE;
        }
        String name = normalize(section.getString("name"));
        if (name == null) {
            return ModelSpec.NONE;
        }
        Map<ModelAnimation, AnimationSpec> animations = new LinkedHashMap<>();
        ConfigurationSection animationsSection = section.getConfigurationSection("animations");
        if (animationsSection != null) {
            for (String rawKey : animationsSection.getKeys(false)) {
                ModelAnimation event = ModelAnimation.parse(rawKey);
                if (event == null) {
                    continue;
                }
                ConfigurationSection entry = animationsSection.getConfigurationSection(rawKey);
                String animation;
                AnimationSpec spec;
                if (entry != null) {
                    animation = normalize(entry.getString("name"));
                    Double speed = number(entry.get("speed"));
                    spec = animation == null ? null : new AnimationSpec(
                            animation,
                            entry.getBoolean("once", event == ModelAnimation.DEATH),
                            speed == null ? null : speed.floatValue(),
                            Math.max(0, entry.getInt("start", 0)),
                            Math.max(0, entry.getInt("end", 0)));
                } else {
                    animation = normalize(animationsSection.getString(rawKey));
                    spec = animation == null ? null : AnimationSpec.of(animation);
                }
                if (spec != null) {
                    animations.put(event, spec);
                }
            }
        }
        Double scale = number(section.get("scale"));
        Boolean sightTrace = section.isSet("sight-trace") ? section.getBoolean("sight-trace") : null;
        // 'auto' (o sin clave) deja que mande el modelo: si trae hueso 'mtag', pinta su placa.
        Boolean showName = null;
        if (section.isSet("show-name")) {
            String raw = String.valueOf(section.get("show-name")).trim().toLowerCase(Locale.ROOT);
            if ("auto".equals(raw)) {
                showName = null;
            } else {
                showName = Boolean.parseBoolean(raw);
            }
        }
        return new ModelSpec(
                true,
                name,
                section.getBoolean("hide-body", true),
                section.getBoolean("hide-equipment", true),
                section.getBoolean("hide-glow", false),
                scale,
                section.getBoolean("damage-animation", true),
                section.getBoolean("damage-tint", true),
                sightTrace,
                showName,
                section.getBoolean("seat", true),
                Map.copyOf(animations));
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
