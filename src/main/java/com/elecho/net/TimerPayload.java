package com.elecho.net;

import com.elecho.ElEcho;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Servidor -> cliente: segundos que quedan del apocalipsis de la Noche 5 (-1 = ocultar). */
public record TimerPayload(int seconds) implements CustomPayload {
    public static final Id<TimerPayload> ID = new Id<>(ElEcho.id("timer"));
    public static final PacketCodec<RegistryByteBuf, TimerPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, TimerPayload::seconds, TimerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
