package com.juanp.custommobs.store;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Escritura segura de los YAML que el plugin guarda solo.
 *
 * <p>{@code YamlConfiguration#save} escribe sobre el archivo final, asi que una caida a mitad
 * de escritura lo deja truncado. En un archivo de estado —{@code player-mobs.yml} guarda los
 * mobs que solo existen dentro de su huevo— eso es una perdida total: al arrancar, el YAML
 * invalido se lee como vacio y el siguiente guardado lo sobrescribe.
 *
 * <p>Por eso se escribe primero un {@code .tmp} y se renombra de golpe: o queda el archivo
 * viejo entero o el nuevo entero. El {@code .bak} se conserva para poder recuperarlo a mano.
 */
public final class AtomicYaml {

    private AtomicYaml() {
    }

    /**
     * Guarda el YAML en {@code file} sin dejarlo nunca a medias.
     *
     * <p>El archivo anterior queda como {@code .bak}. Si el sistema de archivos no soporta el
     * renombrado atomico, se cae a un reemplazo normal: sigue siendo mejor que escribir sobre
     * el archivo vivo, porque el temporal ya esta completo.
     *
     * @param file destino; su carpeta se crea si no existe
     * @return {@code true} si quedo escrito
     */
    public static boolean save(YamlConfiguration yaml, File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            return false;
        }
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        Path backup = target.resolveSibling(file.getName() + ".bak");
        try {
            yaml.save(temp.toFile());
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Si el temporal tampoco se puede borrar, el siguiente guardado lo pisa.
            }
            return false;
        }
    }

    /**
     * {@code true} si el archivo existe y no se puede leer.
     *
     * <p>Un YAML truncado no siempre lanza excepcion: {@code loadConfiguration} avisa y
     * devuelve una config vacia. Lo que se distingue aqui es el caso inequivoco —el archivo
     * esta ahi pero no se puede abrir— para no pisarlo con una lectura vacia.
     */
    public static boolean unreadable(File file) {
        return file.exists() && !file.canRead();
    }

    /** Borra el {@code .tmp} que hubiera sobrado de una escritura interrumpida. */
    public static void cleanTemp(File file) {
        try {
            Files.deleteIfExists(file.toPath().resolveSibling(file.getName() + ".tmp"));
        } catch (IOException ignored) {
            // Un temporal sin permisos de borrado no impide arrancar.
        }
    }
}
