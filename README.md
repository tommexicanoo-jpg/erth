# EL ECHO — Fabric 1.21.1

Fusión de **The Echo** (principal) y **El Eco** (ambientación). Mod id: `elecho`.

## Premisa fusionada
El sol dejó de salir. En este mundo **siempre es de noche** (la Bruma). Antes que tú estuvo **Elías**, otro jugador:
quedó atrapado en la noche y lo único que sabe hacer es dejarte sus notas… y pedirte que borres el mundo para liberarlo.
Con cada noche que pasa el mundo te **olvida** (Decadencia), te **copia** (los Ecos, tus dobles) y la Bruma
te **observa** (el Observador de la Bruma, los ojos rojos). Cuando duermes, a veces la noche te lleva al **laberinto**.

## Sistemas
| Sistema | Origen | Qué hace |
|---|---|---|
| Noche eterna | El Eco | `dimension_type/overworld.json` con `fixed_time: 18000` + respaldo por código (gamerules) + niebla/color por Mixin (la niebla se cierra y enrojece con la paranoia). Shaderpack opcional (`/ecoshader install`). |
| Noches 1-5 | Echo | Cada `dayLengthTicks` (24000 = 20 min) es una "Noche". Se muestra "Noche N de ▒▒▒▒". La Noche 5 es el final. |
| Diario de Elías | Echo | 5 estructuras en cadena (cabaña, ruinas, capilla, campamento, tumba) con espejo, cofre, atril y página del diario. El chat te da las coordenadas, una por noche. |
| Chat falso de Elías | Echo | Mensajes con tu nombre; responde a lo que escribes. Cada noche más hostil. Avisa de que debes dormir. |
| **Paranoia** | Echo | **Manda sobre todo**: más paranoia = más Ecos a la vez, más rápido y de tipos peores; más sonidos (hasta 4 seguidos y más variedad); más ojos; el Observador se acerca; la niebla se cierra; **las antorchas se apagan más rápido** (vida media ~240 s con 0, ~50 s con 50, ~10 s con 100). |
| Ecos | Echo (+ dobles de El Eco) | Dobles tuyos: miran, caminan, corren, gritan, vuelan, De reojo, el Mirón y el Registrador. |
| Observador de la Bruma | El Eco | Alto, con ojos rojos; solo se mueve si no lo miras. Aparece con paranoia ≥ 30; en la Noche 5 te persigue. |
| Antorchas apagadas + **Mechero** | El Eco (+ nuevo) | Las antorchas se apagan solas. Reenciéndelas con el **mechero** (clic derecho en la antorcha apagada, o con la antorcha apagada en la otra mano), pedernal y acero, o crafteando antorcha apagada + pedernal. |
| **Dormir** | Echo | Siempre es de noche, así que **tu cuerpo es el reloj**: a los 20/30/40 min sin dormir recibes avisos (barra de acción, HUD "zZz", mensajes de Elías); luego mareos y microsueños. Dormir (5 s) calma la paranoia, pero hay probabilidad de despertar en el **laberinto** (20 % noche 1, +20 % por noche, +0,2 % por punto de paranoia). Sal tocando **El Original**. |
| Espejo del Eco | Echo | Protege un radio de 24, calma mirándolo con la mano vacía y acepta ofrendas de cosas de noches anteriores. |
| Decadencia | Echo | Lo que construyes vuelve a su estado original cuando no lo miras (los espejos protegen). |
| Final | Echo | Al cerrar el último libro empieza el apocalipsis de la Noche 5; después el mundo te echa y debes borrarlo tú. El mod crea `gracias.txt` en el escritorio (nunca borra nada). |
| Estructuras naturales | El Eco | Mansión gótica, carruaje abandonado y capilla en ruinas (worldgen). |

## Compilar
```bash
gradle wrapper --gradle-version 8.8   # solo la primera vez
./gradlew build                        # -> build/libs/el-echo-1.0.0.jar
./gradlew runClient
```
JDK 21. Mundo **nuevo** recomendado (el dimension_type de noche eterna se aplica al crearlo).

## Comandos (op 2) — `/echo`
`status` · `dread <v>` · `paranoia <v>` · `day <1-5>` · `spawn <registrador|miron|observador|sombra|corre|grita|vuela|lanza|camina|reojo>` ·
`chat <0-4>` · `intro` · `ruina [1-5]` · `sitios` · `decadencia` · `hijack` · `escape` · `apocalipsis` · `reset`.
Cliente: `/ecoshader install|status`.

## Config: `config/elecho.json`
Todo ajustable: `torchBaseSeconds`, `torchMinFactor`, `sleepWarn1/2/3Ticks`, `hijackChanceBase/PerDay/ParanoiaBonus`,
`echoParanoiaThreshold/Step`, `soundScale`, `observerParanoia`, `fogMaxEnd/MinEnd`, `dayLengthTicks`...
