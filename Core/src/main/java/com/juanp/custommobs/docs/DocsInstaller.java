package com.juanp.custommobs.docs;

import com.juanp.custommobs.CustomMobsPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Restaura la documentacion y los tutoriales que viajan dentro del jar en la carpeta del
 * plugin, para que un admin pueda leerlos sin abrir el jar.
 *
 * <p><b>Sobrescribe siempre</b>: estas dos carpetas son del plugin, no del admin. Si se
 * editan o se meten archivos propios, la siguiente actualizacion los pisa. Solo toca
 * {@code docs/} y {@code tutorials/}; el resto de la carpeta del plugin no se toca.
 */
public final class DocsInstaller {

    /** Carpetas del jar que se copian a la carpeta del plugin. */
    private static final List<String> FOLDERS = List.of("docs", "tutorials");

    private final CustomMobsPlugin plugin;

    public DocsInstaller(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Restaura lo que haya en el jar. Devuelve cuantos archivos ha escrito. */
    public int install() {
        File jar = this.plugin.jarFile();
        if (jar == null) {
            return 0;
        }
        if (jar.isDirectory()) {
            // Corriendo desde clases sueltas (un IDE): se copia desde el arbol de archivos.
            return this.copyFromFolder(jar.toPath());
        }
        return this.copyFromJar(jar);
    }

    private int copyFromJar(File jar) {
        int copied = 0;
        Path data = this.plugin.getDataFolder().toPath();
        try (JarFile archive = new JarFile(jar)) {
            Enumeration<JarEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !this.isDoc(entry.getName())) {
                    continue;
                }
                Path target = this.target(data, entry.getName());
                if (target == null) {
                    continue;
                }
                try (InputStream in = archive.getInputStream(entry)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (IOException ex) {
                    this.warn(entry.getName(), ex);
                }
            }
        } catch (IOException ex) {
            this.plugin.getLogger().warning(
                    "No se pudo restaurar la documentacion: " + ex.getMessage());
        }
        return copied;
    }

    private int copyFromFolder(Path root) {
        int copied = 0;
        Path data = this.plugin.getDataFolder().toPath();
        for (String folder : FOLDERS) {
            Path source = root.resolve(folder);
            if (!Files.isDirectory(source)) {
                continue;
            }
            try (var walk = Files.walk(source)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String relative = root.relativize(file).toString()
                            .replace(File.separatorChar, '/');
                    if (!this.isDoc(relative)) {
                        continue;
                    }
                    Path target = this.target(data, relative);
                    if (target == null) {
                        continue;
                    }
                    try {
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    } catch (IOException ex) {
                        this.warn(relative, ex);
                    }
                }
            } catch (IOException ex) {
                this.plugin.getLogger().warning(
                        "No se pudo restaurar la documentacion: " + ex.getMessage());
            }
        }
        return copied;
    }

    /**
     * Resuelve la ruta de destino y comprueba que no se sale de {@code docs/} o
     * {@code tutorials/}. Devuelve {@code null} si la entrada no es de esas carpetas.
     */
    private Path target(Path root, String name) {
        Path resolved = root.resolve(name).normalize();
        if (!resolved.startsWith(root.resolve("docs").normalize())
                && !resolved.startsWith(root.resolve("tutorials").normalize())) {
            return null;
        }
        return resolved;
    }

    private void warn(String name, IOException ex) {
        this.plugin.getLogger().warning("No se pudo restaurar " + name + ": " + ex.getMessage());
    }

    /** {@code true} si esa ruta del jar es documentacion que hay que restaurar. */
    private boolean isDoc(String name) {
        for (String folder : FOLDERS) {
            if (name.startsWith(folder + "/") && (name.endsWith(".md") || name.endsWith(".txt"))) {
                return true;
            }
        }
        return false;
    }
}
