# Pueblo Jefes

Plugin de Paper 26.2 con **jefes estilo Dark Souls** para el servidor Pueblo Extremo. Funciona solo.

## Cómo pelea un jefe

- **No pega con su golpe normal**: solo con sus ataques, y cada uno se **anuncia** antes (partículas y sonido mientras se queda quieto) para poder esquivarlo.
- **Ventana de castigo**: después de cada ataque queda vulnerable un momento y recibe más daño.
- **Fases** según su vida: en cada una avisa más rápido y suma ataques nuevos.
- No sale de su arena y no recibe daño de caída, asfixia, fuego ni lava.
- Al morir: top 3 de daño y botín para quien le haya quitado al menos un % de vida.

Incluye un jefe de ejemplo, **El Guardián Caído** (`guardian`): Golpe sísmico y Embestida en la fase 1,
Lluvia de fuego desde la fase 2 y escoltas en la fase 3.

## Comandos

Permiso `pueblojefes.admin`.

- `/jefes invocar [jefe]`: aviso y luego aparece el jefe en la arena.
- `/jefes parar` / `/jefes lista` / `/jefes recargar`
- `/jefes loot agregar <peso> [min] [max] | lista | quitar <n>`: añade al botín el ítem que tienes en la mano.

Para el **jefe semanal**, el horario de [Pueblo Eventos](https://github.com/EnderGamerPlus/pueblo-eventos)
ejecuta `jefes invocar` a la hora configurada.

## Configuración

`plugins/pueblojefes/config.yml`: arena, aviso, duración, botín, mensajes y los ajustes de cada jefe
(vida, tamaño, fases, daño de cada ataque).

## Crear un jefe nuevo

1. Una clase que extienda `Boss` en `boss/bosses/` (usa `FallenGuardianBoss` como plantilla).
   Cada ataque implementa `BossAttack`: distancia, fase mínima, tiempos de aviso/ataque/recuperación,
   `telegraph()` (el aviso) y `active()` (el daño). `BossFight` trae ayudas como `hitPlayers()` y `ring()`.
2. Registrarla en `BossRegistry`.
3. Añadir su sección en `bosses.<id>` de la config.

## Compilar

Java 25 y Maven: `mvn package` → `target/pueblojefes-1.0.jar`.
