package net.mando.personal;
public final class MediaNotificationKeyTest {
    public static void main(String[] args) {
        String[] keys={"PREVIOUS","PLAY_PAUSE","NEXT","VOLUME_DOWN","VOLUME_UP"};
        for(String key:keys)if(!key.equals(MediaNotificationKey.parse("net.mando.media."+key)))throw new AssertionError(key);
        for(String invalid:new String[]{null,"","net.mando.media.WIN","net.mando.media.text","PLAY_PAUSE","net.mando.media.PLAY_PAUSE.extra"})
            if(MediaNotificationKey.parse(invalid)!=null)throw new AssertionError("Accepted arbitrary intent");
        System.out.println("Notification actions: five allowed keys; other input rejected");
    }
}
