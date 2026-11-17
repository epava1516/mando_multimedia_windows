package net.mando.personal;

/** Disabled purchase boundary. No local switch may manufacture a Play entitlement. */
final class RemoveAdsPurchase {
    static final String PRODUCT_ID = "mando_remove_ads";
    enum State { UNAVAILABLE, CHECKING, NOT_OWNED, PENDING, OWNED, ERROR }
    private RemoveAdsPurchase() {}
    static State state() { return State.UNAVAILABLE; }
    static boolean canBuy() { return false; }
    static boolean canRestore() { return false; }
    static boolean advertisingExempt() {
        return Edition.PERSONAL;
        // Public entitlement requires a verified Play purchase; no SDK/backend yet.
    }
}
