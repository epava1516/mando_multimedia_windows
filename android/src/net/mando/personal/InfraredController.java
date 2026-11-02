package net.mando.personal;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.SystemClock;
import java.io.IOException;

/** Fixed LGTV1 signals only; no caller-supplied frequencies, timings or background repeats. */
final class InfraredController {
    private final ConsumerIrManager manager;
    private long lastSent = -1000;

    InfraredController(Context context) {
        manager = (ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE);
    }

    boolean isAvailable() {
        if (manager == null || !manager.hasIrEmitter()) return false;
        ConsumerIrManager.CarrierFrequencyRange[] ranges = manager.getCarrierFrequencies();
        if (ranges == null) return true;
        for (ConsumerIrManager.CarrierFrequencyRange range : ranges) {
            if (range.getMinFrequency() <= LgTv1.FREQUENCY && range.getMaxFrequency() >= LgTv1.FREQUENCY)
                return true;
        }
        return false;
    }

    synchronized void send(String command) throws IOException {
        int[] pattern = LgTv1.pattern(command); // Exact allowlist, returns a fresh copy.
        if (!isAvailable()) throw new IOException("El emisor infrarrojo no está disponible para LGTV1.");
        long now = SystemClock.elapsedRealtime();
        if (now - lastSent < 150) throw new IOException("Espera un instante antes de enviar otra señal IR.");
        lastSent = now;
        try {
            manager.transmit(LgTv1.FREQUENCY, pattern);
        } catch (RuntimeException error) {
            throw new IOException("Android no ha podido transmitir la señal infrarroja.");
        }
    }
}
