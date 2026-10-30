package com.elecho;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.*;

/**
 * Mensajes de chat falsos que parecen escritos por ti (<TuNombre> ...) pero no los escribiste.
 * Es Elías, el jugador que estuvo antes: atrapado en esta noche sin fin, quiere que borres el mundo para ser libre.
 * Noche 1: amable. Cada noche pierde la paciencia. Noche 5: ordena, amenaza, grita.
 */
public final class ChatHaunt {
    private static final Random RNG = new Random();

    private static final String[] T0 = {
            "hola! qué bueno que llegaste", "por fin alguien", "me alegra no estar solo acá",
            "es de noche, siempre es de noche. te acostumbras", "cuando quieras construimos algo juntos",
            "a veces pienso que sería más fácil empezar de cero... borrar todo, no?",
            "jaja perdón, estoy hablando solo", "este mundo ya está muy viejo. quizás algún día deberíamos borrarlo",
            "gracias por quedarte un rato", "aquí nunca amanece. cuando te pese la cabeza, duerme"
    };
    private static final String[] T1 = {
            "no te alejes mucho", "oye... alguna vez pensaste en borrar este mundo?",
            "creo que este mundo ya no está bien", "borrarlo sería lo mejor para los dos",
            "no me contestas", "por favor, piénsalo: borrar el mundo", "esto está empezando a doler",
            "los bloques se van... yo también", "las antorchas duran menos cuando tienes miedo. guarda un mechero"
    };
    private static final String[] T2 = {
            "ya te lo dije: hay que borrar el mundo", "por qué sigues construyendo?",
            "cada bloque que pones me ata más", "borra el mundo, por favor, ya no aguanto",
            "no me ignores", "estoy perdiendo la paciencia", "esto no es un juego, {name}",
            "no entiendes que quiero salir?", "la bruma se espesa cuando no duermes"
    };
    private static final String[] T3 = {
            "BORRA EL MUNDO, {name}", "te lo pedí amable. ya no", "voy a hacer que lo borres",
            "la carpeta saves. {world}. bórrala", "cuando duermas voy a entrar",
            "no vas a salir de acá de otra forma", "sé dónde estás: {x} {z}", "ya casi te alcanzo, {name}",
            "la luz se apaga. siempre se apaga"
    };
    private static final String[] T4 = {
            "BORRA EL MUNDO. BORRA EL MUNDO. BORRA EL MUNDO", "saves/{world}. AHORA.",
            "YA NO TE PIDO NADA. TE LO ORDENO", "voy a matarte si no lo borras",
            "no hay otra salida, {name}", "DEJA DE MIRARME Y BÓRRALO", "el Observador ya viene por ti",
            "ESTO SE ACABA HOY", "{name} {name} {name} {name}", "SOLO TIENES QUE BORRAR LA CARPETA"
    };
    private static final String[][] POOLS = {T0, T1, T2, T3, T4};

    private static final String[][] SLEEP_NUDGE = {
            {},
            {"tus ojos pesan, {name}. aquí no amanece nunca: tu cuerpo es el único reloj. duerme",
                    "ya llevas mucho despierto. busca una cama, {name}"},
            {"duerme. sé que a veces el mundo te lleva a otro lado cuando duermes... pero peor es no dormir",
                    "no aguantas más, lo veo. una cama. ahora"},
            {"DUERME {name}. YA. se te cierran los ojos y los ecos lo saben",
                    "NO TE QUEDES DESPIERTO. NO AQUÍ. NO DE NOCHE. DUERME"}
    };

    private ChatHaunt() {}

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                onPlayerChat(sender, message.getSignedContent());
            } catch (RuntimeException e) {
                ElEcho.LOGGER.warn("Fallo respondiendo al chat", e);
            }
        });
    }

    private static final Map<UUID, Long> LAST_REPLY = new HashMap<>();

    public static void forget(UUID id) { LAST_REPLY.remove(id); }
    public static void clearAll() { LAST_REPLY.clear(); }

    /** Una vez por segundo por jugador activo. */
    public static void tick(MinecraftServer server, ServerPlayerEntity p, EchoState.PlayerData d, int day) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (day < cfg.chatStartDay) return;
        double perMin = (0.35 + day * 1.0 + d.dread / 70.0 + d.paranoia / 60.0) * cfg.eventIntensity;
        perMin = Math.min(perMin, 14.0);
        perMin /= Math.max(1, server.getPlayerManager().getCurrentPlayerCount());
        if (RNG.nextDouble() > perMin / 60.0) return;
        int tier = baseTier(day, d);
        if (tier > 0 && tier < 4 && RNG.nextInt(4) == 0) tier--;
        if (tier < 4 && (d.dread > 80 || d.paranoia > 85)) tier++;
        burst(server, p, tier, day);
    }

    /** El tono depende de la noche y también de cuánta paranoia llevas. */
    public static int baseTier(int day, EchoState.PlayerData d) {
        int byPar = (int) (d.paranoia / 25.0);
        return Math.max(0, Math.min(4, Math.max(day, byPar)));
    }

    public static void burst(MinecraftServer server, ServerPlayerEntity p, int tier, int day) {
        tier = Math.max(0, Math.min(4, tier));
        int count = switch (tier) {
            case 4 -> 2 + RNG.nextInt(4);
            case 3 -> 1 + RNG.nextInt(3);
            case 2 -> 1 + RNG.nextInt(2);
            default -> 1;
        };
        int delay = 0;
        if (tier >= 2 && RNG.nextInt(7) == 0) { // falso "salió del juego" antes de la ráfaga
            p.sendMessage(Text.translatable("multiplayer.player.left", p.getDisplayName()).formatted(Formatting.YELLOW), false);
            delay = 50;
            Sched.at(server, 45, () -> p.sendMessage(
                    Text.translatable("multiplayer.player.joined", p.getDisplayName()).formatted(Formatting.YELLOW), false));
        }
        for (int i = 0; i < count; i++) {
            final String msg = format(server, pick(tier), p, day);
            Sched.at(server, delay + i * (18 + RNG.nextInt(35)), () -> send(p, msg));
        }
    }

    private static String pick(int tier) {
        String[] pool = POOLS[Math.max(0, Math.min(4, tier))];
        return pool[RNG.nextInt(pool.length)];
    }

    private static String format(MinecraftServer server, String raw, ServerPlayerEntity p, int day) {
        String world = server.getSaveProperties().getLevelName();
        return raw.replace("{name}", p.getName().getString())
                .replace("{world}", world)
                .replace("{x}", String.valueOf(p.getBlockX() + RNG.nextInt(9) - 4))
                .replace("{y}", String.valueOf(p.getBlockY()))
                .replace("{z}", String.valueOf(p.getBlockZ() + RNG.nextInt(9) - 4))
                .replace("{day}", String.valueOf(day + 1));
    }

    /** El mensaje falso lo ven TODOS los jugadores, escrito con el nombre de quien lo provocó. */
    private static void send(ServerPlayerEntity p, String msg) {
        if (p.isRemoved()) return;
        Text t = Text.literal("<" + p.getName().getString() + "> " + msg);
        MinecraftServer server = p.getServer();
        if (server == null) {
            p.sendMessage(t, false);
            return;
        }
        for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) o.sendMessage(t, false);
    }

    public static void force(MinecraftServer server, ServerPlayerEntity p, int tier, int day) {
        send(p, format(server, pick(tier), p, day));
    }

    /** Aviso de sueño: Elías te dice que duermas (stage 1..3). */
    public static void sleepNudge(MinecraftServer server, ServerPlayerEntity p, int stage, int day) {
        stage = Math.max(1, Math.min(3, stage));
        String[] pool = SLEEP_NUDGE[stage];
        send(p, format(server, pool[RNG.nextInt(pool.length)], p, day));
    }

    // ------------------------------------------------------------------ pistas hacia la siguiente estructura

    private static final String[] REVEAL = {
            "ve a {x} {y} {z}. ahí dejé algo mío. te estoy esperando",
            "ya pasó otra noche. ahora ve a {x} {y} {z}. hay más de mí ahí",
            "{x} {y} {z}. hay una capilla. no mires a nadie cuando entres",
            "ve a {x} {y} {z}. ahí acampé. falta poco",
            "este es el último lugar: {x} {y} {z}. ahí estoy yo, lo que queda de mí. lee todo hasta el final"
    };
    private static final String[][] ARRIVAL_TOMORROW = {
            {"llegaste. cuando pase otra noche te mostraré algo más", "ahí estás. en la siguiente noche te mostraré algo más, lo prometo"},
            {"bien. en la siguiente noche te mostraré algo más", "sigues mi rastro. en la siguiente noche te mostraré algo más"},
            {"ya casi. en la siguiente noche te mostraré algo más", "lo estás haciendo bien. en la siguiente noche te mostraré algo más"},
            {"falta solo uno. en la siguiente noche te mostraré algo más", "casi llegamos. en la siguiente noche te mostraré algo más"}
    };

    public static void reveal(ServerPlayerEntity p, int idx, int x, int y, int z) {
        if (p.isRemoved()) return;
        String raw = REVEAL[Math.floorMod(idx, REVEAL.length)];
        send(p, raw.replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y)).replace("{z}", String.valueOf(z)));
    }

    public static void arrival(ServerPlayerEntity p, int idx, boolean tomorrow) {
        if (p.isRemoved()) return;
        if (idx >= Shrines.NOTE_COUNT - 1) {
            send(p, "lee mi última página. hasta el final. y cuando la cierres... perdóname");
        } else if (tomorrow) {
            String[] o = ARRIVAL_TOMORROW[Math.min(idx, ARRIVAL_TOMORROW.length - 1)];
            send(p, o[RNG.nextInt(o.length)]);
        } else {
            send(p, "llegaste. espera un momento, hay más...");
        }
    }

    // ------------------------------------------------------------------ responde a lo que escribes

    private enum Intent { WHO, HOW_ME, WHERE, WHY_DELETE, SLEEP, HELP, GREET, THANKS, BYE }

    private static final String[][] FALLBACK = {
            {"perdón, no te entendí bien", "jaja no sé qué decirte", "sigo aquí, por si acaso"},
            {"...", "no sé si me entiendes. yo te escucho", "no te alejes, sí?"},
            {"no es eso lo que quiero decirte", "no me cambies el tema", "lee mis notas"},
            {"no me ignores", "ya te dije lo que quiero", "borra el mundo"},
            {"BORRA EL MUNDO", "NO ME IMPORTA LO QUE DIGAS", "{name} {name} {name}"}
    };
    private static final Map<Intent, String[][]> REPLIES = new EnumMap<>(Intent.class);

    static {
        REPLIES.put(Intent.GREET, new String[][]{
                {"hola! qué bueno que me contestas", "hola... no creí que me fueras a responder"},
                {"hola. ya te estaba esperando", "hola otra vez. no te alejes mucho"},
                {"hola. por fin me haces caso", "hola. ya era hora de que me hablaras"},
                {"no me saludes como si nada, {name}. escúchame", "hola. ahora escucha: borra el mundo"},
                {"NO HAY TIEMPO PARA HOLAS", "CÁLLATE Y BORRA EL MUNDO"}});
        REPLIES.put(Intent.WHO, new String[][]{
                {"soy Elías. estuve aquí antes que tú, en esta misma noche", "me llamo Elías. llegué antes que tú y no pude irme"},
                {"soy Elías. el jugador de antes. el mundo todavía no me suelta", "me llamo Elías. estuve en tu lugar hace mucho"},
                {"ya te lo dije: soy Elías. y llevo demasiadas noches aquí", "soy Elías. lo único que queda de mí"},
                {"SOY ELÍAS. el que te escribe. el que no puede irse", "soy el que estuvo antes. al que no quisiste escuchar"},
                {"SOY ELÍAS. SOY LO QUE QUEDA DE ÉL", "ELÍAS. ELÍAS. ELÍAS. BORRA EL MUNDO"}});
        REPLIES.put(Intent.HOW_ME, new String[][]{
                {"perdón, es que aquí solo puedo usar tu nombre. no quiero engañarte", "el mundo me presta tu nombre. yo ya no tengo uno"},
                {"no tengo voz propia aquí. solo la tuya. perdón", "uso tu nombre porque es lo único que el chat me deja usar"},
                {"porque ya no tengo nombre. la noche me lo quitó. ahora uso el tuyo", "escribo con tu nombre porque este mundo se olvidó del mío"},
                {"uso tu nombre porque tú estás vivo aquí y yo no", "porque tú existes en este mundo. yo ya no. por eso necesito que lo borres"},
                {"USO TU NOMBRE PORQUE ES LO ÚNICO QUE ME DEJA", "NO IMPORTA CÓMO. ESCUCHA: BORRA EL MUNDO"}});
        REPLIES.put(Intent.WHERE, new String[][]{
                {"estoy cerca. más de lo que crees", "en la noche. en todas partes y en ninguna"},
                {"detrás de ti, a veces. no mires", "cerca de los lugares viejos. ahí me siento más yo"},
                {"estoy en la bruma, {name}", "atrapado. por eso te hablo"},
                {"MUY CERCA. cada vez más", "donde no me miras"},
                {"DETRÁS DE TI", "ESTOY EN TODAS PARTES Y TÚ NO ME VES"}});
        REPLIES.put(Intent.WHY_DELETE, new String[][]{
                {"porque es la única forma de que los dos descansemos", "es difícil de explicar. léelo en mis notas, por favor"},
                {"porque este mundo no te va a dejar ir, igual que a mí", "si lo borras, yo me voy. y tú quedas a salvo"},
                {"porque cada bloque que pones lo hace más fuerte", "porque no hay otra salida. créeme, lo intenté todo"},
                {"PORQUE YO LO DIGO. porque si no, vas a terminar como yo", "porque ya no hay otra opción, {name}"},
                {"PORQUE SI NO, TODO ESTO TERMINA CONTIGO ADENTRO", "NO PREGUNTES. BORRA. EL. MUNDO"}});
        REPLIES.put(Intent.SLEEP, new String[][]{
                {"dormir ayuda. te calma la cabeza. pero a veces el mundo se queda con tu sueño", "duerme cuando te pesen los ojos. aquí no hay sol que te avise"},
                {"si no duermes la paranoia sube. si duermes, quizá despiertes en el laberinto. elige", "una cama, un rato. y no mires el techo al despertar"},
                {"el laberinto te espera si duermes. el Original es la salida. búscalo", "dormir es peor cada noche. no dormir también"},
                {"DUERME Y ARRIESGA, O NO DUERMAS Y ENLOQUECE", "CADA NOCHE ES MÁS PROBABLE QUE NO VUELVAS DEL LABERINTO"},
                {"YA NO IMPORTA SI DUERMES", "DORMIR NO TE SALVA"}});
        REPLIES.put(Intent.HELP, new String[][]{
                {"busca mis notas. están en lugares viejos, una por noche", "las antorchas se apagan con el miedo. con un mechero las vuelves a encender"},
                {"deja algo de tu primera noche en un espejo. eso calma. y mantén las antorchas encendidas", "los espejos te protegen. ponlos donde duermes"},
                {"no tengo mucho que darte. lee las notas", "no mires a los Ecos. y no dejes que la luz se apague"},
                {"NO PUEDO AYUDARTE. SOLO TÚ PUEDES. BORRA EL MUNDO", "ya es tarde para ayudas"},
                {"NADIE PUEDE AYUDARTE", "CORRE O BORRA EL MUNDO"}});
        REPLIES.put(Intent.THANKS, new String[][]{
                {"de nada. gracias a ti por quedarte", "no hay de qué. me alegra que estés aquí"},
                {"no me agradezcas. solo piénsalo, por favor", "gracias a ti. no te alejes"},
                {"no es momento de agradecer. es momento de entender", "guarda tus gracias. lo que quiero es otra cosa"},
                {"no quiero tus gracias. quiero que borres el mundo", "las gracias no me sacan de aquí"},
                {"NO QUIERO TUS GRACIAS", "GRACIAS NO. BORRA EL MUNDO"}});
        REPLIES.put(Intent.BYE, new String[][]{
                {"ya te vas? vuelve pronto, por favor", "hasta luego... aquí te espero"},
                {"no te vayas lejos. aquí te espero", "adiós... no tardes"},
                {"no te vayas. no me dejes", "vuelve. siempre vuelves"},
                {"no te vas a ir. no así", "tú no te vas, {name}"},
                {"NO TE VAS", "NO PUEDES IRTE"}});
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static Intent classify(String t) {
        if (t.contains("quien eres") || t.contains("quien es") || t.contains("quien sos") || t.contains("who are you") || t.contains("como te llamas")) return Intent.WHO;
        if (t.contains("mi nombre") || t.contains("como escribes") || t.contains("como hablas") || t.contains("porque con mi")) return Intent.HOW_ME;
        if (t.contains("donde estas") || t.contains("donde esta") || t.contains("where")) return Intent.WHERE;
        if (t.contains("porque borrar") || t.contains("por que borrar") || t.contains("por que") && t.contains("borr")) return Intent.WHY_DELETE;
        if (t.contains("dormir") || t.contains("cama") || t.contains("sueno") || t.contains("sleep")) return Intent.SLEEP;
        if (t.contains("ayuda") || t.contains("help") || t.contains("que hago") || t.contains("como sobrevivo") || t.contains("mechero")) return Intent.HELP;
        if (t.contains("gracias") || t.contains("thanks")) return Intent.THANKS;
        if (t.contains("adios") || t.contains("chau") || t.contains("bye") || t.contains("me voy")) return Intent.BYE;
        if (t.startsWith("hola") || t.startsWith("hey") || t.startsWith("buenas") || t.equals("hi") || t.startsWith("hello")) return Intent.GREET;
        return null;
    }

    private static void onPlayerChat(ServerPlayerEntity p, String raw) {
        MinecraftServer server = p.getServer();
        if (server == null || raw == null || raw.isBlank()) return;
        long now = server.getOverworld().getTime();
        Long last = LAST_REPLY.get(p.getUuid());
        if (last != null && now - last < 100) return;
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        if (d.doomed) return;
        int day = EchoManager.effectiveDay(server, st);
        // sólo responde a veces para no ser predecible (siempre si es una pregunta directa)
        Intent in = classify(norm(raw));
        if (in == null && RNG.nextInt(3) != 0) return;
        LAST_REPLY.put(p.getUuid(), now);
        int tier = baseTier(day, d);
        String[] pool = in == null ? FALLBACK[tier] : REPLIES.get(in)[tier];
        final String msg = format(server, pool[RNG.nextInt(pool.length)], p, day);
        Sched.at(server, 30 + RNG.nextInt(50), () -> send(p, msg));
    }
}
