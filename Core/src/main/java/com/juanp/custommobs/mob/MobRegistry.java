package com.juanp.custommobs.mob;

import org.bukkit.configuration.file.YamlConfiguration;
import com.juanp.custommobs.CustomMobsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Catalogo de definiciones, cargado desde {@code plugins/CustomMobs/mobs/*.yml}. */
public final class MobRegistry {

    private final CustomMobsPlugin plugin;
    private final Map<String, MobDefinition> definitions = new LinkedHashMap<>();

    public MobRegistry(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Recarga todo el catalogo. Los archivos invalidos se reportan y se omiten. */
    public void reload() {
        this.definitions.clear();

        File dir = new File(this.plugin.getDataFolder(), "mobs");
        if (!dir.exists() && !dir.mkdirs()) {
            this.plugin.getLogger().warning("No se pudo crear la carpeta mobs/");
            return;
        }
        this.copyExamples(dir);

        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String fallbackId = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            Optional<MobDefinition> parsed = MobLoader.parse(fallbackId, cfg,
                    this.plugin.skillCatalog());
            if (parsed.isEmpty()) {
                this.plugin.getLogger().warning("Definicion invalida, se omite: " + file.getName());
                continue;
            }
            MobDefinition definition = parsed.get();
            MobDefinition previous = this.definitions.put(definition.id(), definition);
            if (previous != null) {
                this.plugin.getLogger().warning("Id duplicado '" + definition.id() + "' en " + file.getName());
            }
        }

        int skills = 0;
        for (MobDefinition definition : this.definitions.values()) {
            skills += definition.skills().size();
        }
        this.plugin.getLogger().info("Catalogo cargado: " + this.definitions.size() + " definiciones"
                + (skills > 0 ? ", " + skills + " skills." : "."));
    }

    /**
     * Copia a {@code mobs/} los ejemplos que trae el jar, sin pisar los que ya existan.
     *
     * <p>La lista se lee del propio jar en vez de estar escrita a mano: asi no puede
     * desincronizarse al anadir o renombrar un ejemplo. Antes estaba a mano, y por eso
     * un ejemplo nuevo no llegaba nunca a un servidor que ya tuviera la carpeta.
     */
    private void copyExamples(File dir) {
        File jar = this.plugin.jarFile();
        if (jar == null || !jar.isFile()) {
            return;
        }
        int copied = 0;
        try (JarFile archive = new JarFile(jar)) {
            List<String> names = new ArrayList<>();
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                // Solo 'mobs/algo.yml': nada de subcarpetas.
                if (name.startsWith("mobs/") && name.endsWith(".yml")
                        && name.indexOf('/', "mobs/".length()) < 0) {
                    names.add(name);
                }
            }
            Collections.sort(names);
            for (String name : names) {
                if (new File(dir, name.substring("mobs/".length())).exists()) {
                    continue;
                }
                this.plugin.saveResource(name, false);
                copied++;
            }
        } catch (IOException ex) {
            this.plugin.getLogger().warning("No se pudieron copiar los ejemplos: " + ex.getMessage());
        }
        if (copied > 0) {
            this.plugin.getLogger().info("Ejemplos copiados en mobs/: " + copied
                    + ". Estan numerados: abrelos en orden.");
        }
    }

    public Optional<MobDefinition> get(String id) {
        return Optional.ofNullable(this.definitions.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<MobDefinition> all() {
        return List.copyOf(this.definitions.values());
    }

    public int size() {
        return this.definitions.size();
    }
}
