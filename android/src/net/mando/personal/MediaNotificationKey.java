package net.mando.personal;
/** Closed action set: notification intents cannot send arbitrary keyboard/text input. */
final class MediaNotificationKey {
    static String parse(String action) {
        if(action==null)return null;
        switch(action) {
            case "net.mando.media.LEFT": return "LEFT";
            case "net.mando.media.PLAY_PAUSE": return "PLAY_PAUSE";
            case "net.mando.media.RIGHT": return "RIGHT";
            case "net.mando.media.VOLUME_DOWN": return "VOLUME_DOWN";
            case "net.mando.media.VOLUME_UP": return "VOLUME_UP";
            default:return null;
        }
    }
}
