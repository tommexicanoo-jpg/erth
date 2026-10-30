package com.elecho.client;

import com.elecho.EchoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

/** Estado visual del cliente: la niebla y el color de la bruma dependen de la PARANOIA. */
public final class ClientEco {
    public static boolean active = true;      // la noche eterna siempre tiene bruma
    public static int paranoia = 0;
    public static int dread = 0;
    public static int night = 1;
    public static int stage = 0;
    public static int sleepStage = 0;
    public static float fogEnd = 60f;
    public static float r = 0.05f, g = 0.06f, b = 0.034f;

    private ClientEco() {}

    public static void reset() {
        paranoia = 0; dread = 0; night = 1; stage = 0; sleepStage = 0; fogEnd = 60f;
    }

    /** Cada tick del cliente: la niebla se acerca y se enrojece poco a poco según la paranoia. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // la bruma solo existe en el Overworld (no toca el Nether ni el End)
        active = mc.world != null && mc.world.getRegistryKey().equals(World.OVERWORLD);
        EchoConfig c = EchoConfig.INSTANCE;
        float t = Math.min(1f, paranoia / 100f);
        float target = c.fogMaxEnd + (c.fogMinEnd - c.fogMaxEnd) * t;
        fogEnd += (target - fogEnd) * 0.02f;
        // gris verdoso -> rojo sangre. El azul codifica la "noche" (1..5) para el shaderpack: b = 0.03 + 0.004 * n
        int shaderDay = 1 + Math.min(4, (int) (paranoia / 20f));
        float tr = 0.050f + (0.120f - 0.050f) * t;
        float tg = 0.058f + (0.012f - 0.058f) * t;
        float tb = 0.03f + 0.004f * shaderDay;
        r += (tr - r) * 0.02f; g += (tg - g) * 0.02f; b = tb;
    }
}
