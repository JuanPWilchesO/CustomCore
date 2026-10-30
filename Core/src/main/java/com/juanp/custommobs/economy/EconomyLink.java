package com.juanp.custommobs.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Cobrar dinero, hablando con el plugin de economia del servidor por reflexion.
 *
 * <p>Se hace por reflexion a proposito: asi el plugin no depende de ninguno para compilar y
 * sigue funcionando —ignorando los pagos— en servidores que no tengan. El orden es:
 *
 * <ol>
 *   <li><b>Vault</b> (o VaultUnlocked), que es el estandar: sirve para EssentialsX, CMI,
 *       PlayerPoints y demas, y cobra por {@code OfflinePlayer} (UUID).</li>
 *   <li><b>EssentialsX</b> directo, por si no hay Vault. Su API cobra por <b>nombre</b>, que es
 *       lo unico que ofrece.</li>
 * </ol>
 *
 * <p><b>Aviso importante:</b> las skills de pago ({@code pay}) <b>no funcionan sin un plugin de
 * economia</b>. Sin ninguno, el pago siempre falla y la cadena toma la rama de {@code no-pay}.
 */
public final class EconomyLink {

    /** Resultado de un cobro. Distinguir "no puede pagar" de "fallo" es lo que faltaba. */
    public enum Charge {
        /** Cobrado. */
        CHARGED,
        /** No tiene bastante: es normal y lleva al {@code no-pay}. */
        INSUFFICIENT,
        /** No hay economia, o la API fallo. Tambien lleva a {@code no-pay}, pero se avisa. */
        ERROR
    }

    /** Como se cobra: {@code null} si no hay economia. */
    private interface Backend {
        Charge charge(Player player, double amount);
    }

    private final Backend backend;
    private final String name;
    private final java.util.logging.Logger logger;
    private boolean errorWarned;

    private EconomyLink(Backend backend, String name, java.util.logging.Logger logger) {
        this.backend = backend;
        this.name = name;
        this.logger = logger;
    }

    /** Engancha con Vault, o con EssentialsX, o devuelve un cobrador que siempre falla. */
    public static EconomyLink create(Plugin plugin) {
        var pm = plugin.getServer().getPluginManager();

        if (pm.getPlugin("Vault") != null) {
            Backend vault = vault(plugin);
            if (vault != null) {
                plugin.getLogger().info("Economia enganchada con Vault: las skills de pago"
                        + " funcionan.");
                return new EconomyLink(vault, "Vault", plugin.getLogger());
            }
            plugin.getLogger().warning("Vault esta instalado pero no hay ninguna economia"
                    + " enganchada a el: las skills de pago NO funcionaran.");
        }

        if (pm.getPlugin("Essentials") != null || pm.getPlugin("EssentialsX") != null) {
            Backend essentials = essentials(plugin);
            if (essentials != null) {
                plugin.getLogger().info("Economia enganchada con EssentialsX: las skills de pago"
                        + " funcionan.");
                return new EconomyLink(essentials, "EssentialsX", plugin.getLogger());
            }
        }

        plugin.getLogger().warning("Sin economia (Vault o EssentialsX): las skills de pago (pay)"
                + " NO funcionaran. La cadena tomara la rama de 'no-pay'.");
        return new EconomyLink(null, "ninguna", plugin.getLogger());
    }

    /** Vault por reflexion: {@code Economy#getBalance(OfflinePlayer)} y {@code withdrawPlayer}. */
    private static Backend vault(Plugin plugin) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider =
                    plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (provider == null) {
                return null;
            }
            Object economy = provider.getProvider();
            Method withdraw = economyClass.getMethod("withdrawPlayer",
                    org.bukkit.OfflinePlayer.class, double.class);
            return (player, amount) -> {
                try {
                    // Vault devuelve un EconomyResponse: si el cobro sale bien, se hizo.
                    Object response = withdraw.invoke(economy, player, amount);
                    if (response != null && !(Boolean) response.getClass()
                            .getMethod("transactionSuccess").invoke(response)) {
                        return Charge.INSUFFICIENT;
                    }
                    return Charge.CHARGED;
                } catch (java.lang.reflect.InvocationTargetException notEnough) {
                    // Vault lanza en el cobro cuando no hay fondos: es el caso normal.
                    return Charge.INSUFFICIENT;
                } catch (IllegalAccessException | NoSuchMethodException failure) {
                    throw new IllegalStateException("Vault no responde como se esperaba", failure);
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException failure) {
            plugin.getLogger().log(Level.WARNING, "Vault no expone la API de economia esperada", failure);
            return null;
        }
    }

    /**
     * EssentialsX por reflexion. Su API cobra por <b>nombre</b>: es la unica firma que ofrece,
     * asi que no hay forma de usar el UUID.
     */
    private static Backend essentials(Plugin plugin) {
        try {
            Class<?> economy = Class.forName("com.earth2me.essentials.api.Economy");
            // Las firmas de EssentialsX son con 'double', no con BigDecimal: buscarlas con
            // BigDecimal lanzaba NoSuchMethodException y los pagos no funcionaban aunque el
            // plugin estuviera instalado.
            Method hasEnough = economy.getMethod("hasEnough", String.class, double.class);
            Method withdraw = economy.getMethod("subtract", String.class, double.class);
            return (player, amount) -> {
                String name = player.getName();
                try {
                    if (!(Boolean) hasEnough.invoke(null, name, amount)) {
                        return Charge.INSUFFICIENT;
                    }
                    withdraw.invoke(null, name, amount);
                    return Charge.CHARGED;
                } catch (java.lang.reflect.InvocationTargetException notEnough) {
                    // 'hasEnough' ya lo descarto; un fallo aqui es la API, no la cartera.
                    return Charge.ERROR;
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException("EssentialsX no responde como se esperaba",
                            failure);
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException failure) {
            plugin.getLogger().log(Level.WARNING,
                    "EssentialsX no expone la API de economia esperada", failure);
            return null;
        }
    }

    /** {@code true} si hay economia con la que cobrar. */
    public boolean available() {
        return this.backend != null;
    }

    /** Con que se cobra: {@code Vault}, {@code EssentialsX} o {@code ninguna}. */
    public String name() {
        return this.name;
    }

    /**
     * Cobra el importe al jugador.
     *
     * <p>Los errores de la API no se confunden con "no tiene dinero": se devuelven como
     * {@link Charge#ERROR} y se avisan una vez en el log. La cadena trata ambos igual (no se
     * cobra nada y va a {@code no-pay}), pero ahora el fallo se puede ver.
     */
    public Charge charge(Player player, double amount) {
        if (this.backend == null || amount <= 0.0D) {
            return Charge.ERROR;
        }
        try {
            Charge result = this.backend.charge(player, amount);
            return result == null ? Charge.ERROR : result;
        } catch (Throwable failure) {
            if (!this.errorWarned) {
                this.errorWarned = true;
                this.logger.log(Level.SEVERE, "Fallo cobrando con " + this.name
                        + " a " + player.getUniqueId() + "; no se reintenta el aviso.", failure);
            }
            return Charge.ERROR;
        }
    }
}
