package com.juanp.custommobs.skill;

import com.juanp.custommobs.mob.MobLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catalogo de skills reutilizables: un archivo por skill en {@code skills/}.
 *
 * <p>El id sale del nombre del archivo. Los archivos que empiezan por {@code _} o {@code .}
 * se ignoran: sirven para desactivar una skill sin borrarla. Un archivo invalido se omite
 * con un aviso y nunca tumba la carga del resto.
 */
public final class SkillCatalog {

    private final Plugin plugin;
    private final Map<String, SkillSpec> skills = new LinkedHashMap<>();

    public SkillCatalog(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.skills.clear();
        File dir = new File(this.plugin.getDataFolder(), "skills");
        if (!dir.exists() && !dir.mkdirs()) {
            this.plugin.getLogger().warning("No se pudo crear la carpeta skills/");
            return;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
                && !name.startsWith("_") && !name.startsWith("."));
        if (files == null) {
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4)
                    .toLowerCase(Locale.ROOT).trim();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Map<String, Object> map = new LinkedHashMap<>(yaml.getValues(false));
            Object declared = map.get("id");
            if (declared != null
                    && !id.equals(String.valueOf(declared).toLowerCase(Locale.ROOT).trim())) {
                this.plugin.getLogger().warning("La skill " + file.getName() + " declara id '"
                        + declared + "'; se usa el del archivo: '" + id + "'.");
            }
            map.put("id", id);
            SkillSpec spec = MobLoader.parseSkill(map);
            if (spec == null) {
                this.plugin.getLogger().warning("Skill invalida, se omite: " + file.getName());
                continue;
            }
            SkillSpec previous = this.skills.putIfAbsent(id, spec);
            if (previous != null) {
                this.plugin.getLogger().warning("Skill duplicada '" + id + "' en " + file.getName());
            }
        }
        this.plugin.getLogger().info("Skills reutilizables cargadas: " + this.skills.size() + ".");
    }

    public Optional<SkillSpec> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.skills.get(id.toLowerCase(Locale.ROOT).trim()));
    }

    public Collection<SkillSpec> all() {
        return List.copyOf(this.skills.values());
    }

    public int size() {
        return this.skills.size();
    }
}
