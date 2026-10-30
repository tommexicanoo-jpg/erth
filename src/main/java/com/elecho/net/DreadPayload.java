package com.elecho.net;

import com.elecho.ElEcho;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Servidor -> cliente.
 * hud: 0 = ocultar, 1 = mostrar, -1 = sin cambio. dread / paranoia: 0..100 (-1 = sin cambio).
 * day: noche a mostrar (1, 2, 3...). stage: 0 normal, 1 tensión, 2 corrupción, 3 final.
 * extra = scare (0 nada, 1 susto corto, 2 fuerte) + 10 * sueño (0..3) ; -1 = sin cambio.
 */
public record DreadPayload(int hud, int dread, int paranoia, int day, int stage, int extra) implements CustomPayload {
    public static final Id<DreadPayload> ID = new Id<>(ElEcho.id("dread"));
    public static final PacketCodec<RegistryByteBuf, DreadPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DreadPayload::hud,
            PacketCodecs.VAR_INT, DreadPayload::dread,
            PacketCodecs.VAR_INT, DreadPayload::paranoia,
            PacketCodecs.VAR_INT, DreadPayload::day,
            PacketCodecs.VAR_INT, DreadPayload::stage,
            PacketCodecs.VAR_INT, DreadPayload::extra,
            DreadPayload::new);

    public static DreadPayload scare(int level) {
        return new DreadPayload(-1, -1, -1, -1, -1, level);
    }

    public int scareLevel() { return extra < 0 ? 0 : extra % 10; }
    public int sleepStage() { return extra < 0 ? -1 : extra / 10; }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
