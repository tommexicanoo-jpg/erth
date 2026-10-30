package com.elecho;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/elecho.json (se crea sola).
 * Los "días" son NOCHES del Eco (el contador empieza en 0 = Noche 1). Como el sol nunca sale, cada "día" dura
 * dayLengthTicks de tiempo real de juego (24000 = 20 min).
 */
public final class EchoConfig {
    public int configVersion = 1;

    // --- tiempo (la noche eterna no avanza, así que contamos nosotros) ---
    public int dayLengthTicks = 24000;

    // --- arco de 5 noches. 0 = NOCHE 1, 4 = NOCHE 5 (el final) ---
    public int chatStartDay = 0;
    public int audioStartDay = 0;
    public int registrarDay = 2;
    public int decayStartDay = 2;
    public int fakeBlockDay = 2;
    public int corruptionDay = 4;

    // --- la PARANOIA manda: cuanto más alta, más Ecos / sonidos / antorchas apagadas ---
    public int echoParanoiaThreshold = 12;   // con menos paranoia que esto no aparece ningún Eco
    public double echoParanoiaStep = 6.0;    // cada tanta paranoia extra se permite un Eco más a la vez
    public int maxEchoes = 14;
    public int walkParanoia = 25;
    public int rushParanoia = 22;
    public int shadowParanoia = 40;
    public int screamParanoia = 50;
    public int flyParanoia = 60;
    public double soundScale = 1.0;          // multiplicador global de los sonidos paranoicos
    public double eventIntensity = 1.0;

    // --- antorchas ---
    public int torchBaseSeconds = 240;       // vida media de una antorcha con paranoia 0
    public double torchMinFactor = 0.04;     // con paranoia 100 la vida media es base * este factor (~10 s)
    public int torchScanRadius = 10;
    public int lighterUses = 128;

    // --- sueño (noche eterna: tu cuerpo te avisa) ---
    public int sleepWarn1Ticks = 24000;      // 20 min sin dormir: primer aviso
    public int sleepWarn2Ticks = 36000;      // 30 min: aviso fuerte + paranoia sube más rápido
    public int sleepWarn3Ticks = 48000;      // 40 min: microsueños (visión y movimiento fallan)
    public boolean enableRealm = true;
    public double hijackChanceBase = 0.20;   // prob. de despertar en el laberinto al dormir (noche 1)
    public double hijackChancePerDay = 0.20; // ...sube por cada noche
    public double hijackParanoiaBonus = 0.002; // ...y un poco por cada punto de paranoia
    public int mazeSize = 7;

    // --- Observador de la Bruma ---
    public int observerParanoia = 30;        // paranoia mínima para que aparezca
    public float fogMaxEnd = 60f;            // niebla (bloques) con paranoia 0
    public float fogMinEnd = 10f;            // niebla con paranoia 100

    // --- el final ---
    public boolean corruptionEnding = true;
    public int apocalypseSeconds = 60;
    public boolean writeThanksFile = true;

    // --- terreno ---
    public boolean enableDecay = true;
    public double decayGraceDays = 0.5;

    // --- miedo / espejo ---
    public double dreadPerMinute = 9.0;
    public double damageFear = 15.0;
    public double killFear = 15.0;
    public int mirrorRadius = 24;
    public double mirrorGazeRelief = 8.0;
    public int mirrorGazeCooldownSeconds = 45;
    public double offeringDreadReduction = 30.0;
    public double offeringParanoiaReduction = 30.0;
    public int offeringDayRelief = 1;
    public int offeringBanishDays = 1;
    public int maxBanishDays = 5;
    public double eatDreadRelief = 2.0;
    public double eatParanoiaRelief = 1.5;
    public double chopDreadRelief = 0.8;
    public double chopParanoiaRelief = 0.6;
    public int hordeMobCap = 24;

    // --- estructuras con el diario de Elías ---
    public int shrineStartMin = 110;
    public int shrineStartMax = 160;
    public int shrineLinkMin = 90;
    public int shrineLinkMax = 140;
    public boolean dayTitle = true;

    public static EchoConfig INSTANCE = new EchoConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("elecho.json");
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) {
                    EchoConfig c = GSON.fromJson(r, EchoConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            if (INSTANCE.dayLengthTicks < 200) INSTANCE.dayLengthTicks = 200;
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException | RuntimeException e) {
            ElEcho.LOGGER.error("No se pudo leer/escribir la config", e);
        }
    }
}
