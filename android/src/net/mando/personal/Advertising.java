package net.mando.personal;

/** Deliberately offline preparation. Personal builds can never enable advertising. */
final class Advertising {
    private Advertising() {}
    static boolean isAvailable() {
        if (RemoveAdsPurchase.advertisingExempt() || !Edition.ADS_ENABLED) return false;
        return false; // SDK + consent adapter must be implemented before activation.
    }
}
