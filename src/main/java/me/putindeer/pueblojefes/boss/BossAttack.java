package me.putindeer.pueblojefes.boss;

/**
 * Un ataque de jefe. Ciclo: aviso (telegraph, el jugador ve venir el golpe) → activo (hace daño) →
 * recuperación (el jefe queda quieto y recibe más daño: la ventana para castigarlo).
 * <p>
 * Los tiempos de aviso se acortan solos en las fases avanzadas (ver {@link BossFight#speedFactor()}).
 */
public interface BossAttack {
    String name();

    /** Fase mínima en la que el jefe usa este ataque (1 = desde el principio). */
    default int minPhase() {
        return 1;
    }

    /** Distancia al objetivo en la que tiene sentido usarlo. */
    double minRange();

    double maxRange();

    /** Probabilidad relativa frente a los demás ataques posibles. */
    default int weight() {
        return 10;
    }

    /** Ticks de aviso antes de golpear (20 ticks = 1 segundo). */
    int windupTicks();

    /** Ticks que dura la parte activa (1 = golpe instantáneo). */
    default int activeTicks() {
        return 1;
    }

    /** Ticks que queda vulnerable después. */
    int recoverTicks();

    /** Ticks hasta poder repetir este ataque. */
    int cooldownTicks();

    /** Al empezar el aviso (ej. fijar el punto donde caerá el golpe). */
    default void start(BossFight fight) {
    }

    /** Cada tick del aviso: partículas, sonidos, el jefe "cargando". */
    void telegraph(BossFight fight, int tick);

    /** Cada tick de la parte activa: aquí se hace el daño. */
    void active(BossFight fight, int tick);
}
