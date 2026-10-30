package com.elecho;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /echo ... (op nivel 2): herramientas para probar el mod. */
public final class EchoCommands {
    private EchoCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(CommandManager.literal("echo").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("status").executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            String s = EchoManager.status(p);
                            c.getSource().sendFeedback(() -> Text.literal(s), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("dread").then(CommandManager.argument("v", DoubleArgumentType.doubleArg(0, 120)).executes(c -> {
                            EchoManager.setDread(c.getSource().getPlayerOrThrow(), DoubleArgumentType.getDouble(c, "v"));
                            return 1;
                        })))
                        .then(CommandManager.literal("paranoia").then(CommandManager.argument("v", DoubleArgumentType.doubleArg(0, 100)).executes(c -> {
                            EchoManager.setParanoia(c.getSource().getPlayerOrThrow(), DoubleArgumentType.getDouble(c, "v"));
                            return 1;
                        })))
                        .then(CommandManager.literal("day").then(CommandManager.argument("n", IntegerArgumentType.integer(1, 5)).executes(c -> {
                            EchoManager.setDay(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))
                        .then(CommandManager.literal("spawn").then(CommandManager.argument("tipo", StringArgumentType.word()).executes(c -> {
                            boolean ok = EchoManager.debugSpawn(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "tipo"));
                            c.getSource().sendFeedback(() -> Text.literal(ok ? "Generado." : "No hay sitio válido cerca."), false);
                            return ok ? 1 : 0;
                        })))
                        .then(CommandManager.literal("chat").then(CommandManager.argument("nivel", IntegerArgumentType.integer(0, 4)).executes(c -> {
                            EchoManager.forceChat(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c, "nivel"));
                            return 1;
                        })))
                        .then(CommandManager.literal("intro").executes(c -> {
                            EchoManager.intro(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("ruina")
                                .executes(c -> EchoManager.forceShrine(c.getSource().getPlayerOrThrow(), null) ? 1 : 0)
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(1, 5)).executes(c ->
                                        EchoManager.forceShrine(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c, "n")) ? 1 : 0)))
                        .then(CommandManager.literal("apocalipsis").executes(c -> {
                            EchoManager.triggerApocalypse(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("sitios").executes(c -> {
                            EchoManager.listSites(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("decadencia").executes(c -> {
                            EchoManager.forceDecay(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("hijack").executes(c -> {
                            EchoManager.forceHijack(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("escape").executes(c -> {
                            EchoManager.leaveRealm(c.getSource().getPlayerOrThrow(), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("reset").executes(c -> {
                            EchoManager.reset(c.getSource().getPlayerOrThrow());
                            c.getSource().sendFeedback(() -> Text.literal("Estado del Eco reiniciado."), false);
                            return 1;
                        }))));
    }
}
