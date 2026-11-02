package net.mando.personal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Independent encoder for observed application frames; no original binary code. */
final class Protocol {
    static byte[] frame(String kind, String payload) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        if (!kind.matches("[a-z]{3}") || body.length > 999) throw new IllegalArgumentException("Mensaje demasiado largo");
        return (kind + String.format(Locale.US, "%3d", body.length) + payload).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] text(String value) {
        return frame("key", "[noe]" + value);
    }

    static byte[] systemMedia(String token) {
        if(!java.util.Arrays.asList("media_play_pause","media_next_track","media_prev_track",
                "media_stop","volume_up","volume_down","volume_mute").contains(token))
            throw new IllegalArgumentException("Tecla multimedia no permitida");
        // The trailing [*] selects the server's explicit VK down/up path with zero modifiers.
        return frame("key","[kld]"+token+"[*]");
    }

    static byte[] navigationKey(String token) {
        if(!java.util.Arrays.asList("space","lf","rt","up","dw").contains(token))
            throw new IllegalArgumentException("Tecla de navegación no permitida");
        return frame("key","[kld]"+token+"[*]");
    }

    static byte[] zoom(boolean enlarge) {
        // One complete Ctrl+key combination; no modifier is held across UDP packets.
        return frame("key", "[kld]" + (enlarge ? "+" : "-") + "[*]ctrl");
    }

    static byte[] move(int dx, int dy) {
        return frame("mos", String.format(Locale.US, "m %.4f %.4f", (double)dx, (double)dy));
    }
}
