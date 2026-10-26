package com.juanp.custommobs.mob;

/**
 * Sonidos de un mob custom. Cada campo es opcional; {@code null} significa silencio.
 *
 * @param ambient               sonido periodico mientras el mob esta vivo
 * @param ambientIntervalSeconds cada cuantos segundos suena {@code ambient} (minimo 1)
 * @param hurt                  al recibir dano
 * @param death                 al morir
 * @param attack                al golpear a alguien
 */
public record MobSounds(
        SoundSpec ambient,
        int ambientIntervalSeconds,
        SoundSpec hurt,
        SoundSpec death,
        SoundSpec attack
) {

    public static final MobSounds NONE = new MobSounds(null, 0, null, null, null);

    /** {@code true} si el yml define al menos un sonido. */
    public boolean hasAny() {
        return !this.isEmpty();
    }

    public boolean hasAmbient() {
        return this.ambient != null && this.ambientIntervalSeconds > 0;
    }

    public boolean isEmpty() {
        return this.ambient == null && this.hurt == null && this.death == null && this.attack == null;
    }
}
