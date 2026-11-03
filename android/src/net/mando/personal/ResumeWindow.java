package net.mando.personal;

/** Lifecycle policy only: no credentials, sockets or automatic input retries. */
final class ResumeWindow {
    static final long TTL_MS=20*60*1000L;
    static final class Target {
        final String mode,host;
        Target(String mode,String host){this.mode=mode;this.host=host;}
    }
    static final class Ticket {
        final long generation;
        final Target target;
        Ticket(long generation,Target target){this.generation=generation;this.target=target;}
    }
    static final class Snapshot {
        final Target target;final long pausedAt;
        Snapshot(Target target,long pausedAt){this.target=target;this.pausedAt=pausedAt;}
    }
    private long generation;
    private boolean foreground,active;
    private Target target;
    private long pausedAt=-1;
    synchronized long generation(){return generation;}
    synchronized boolean current(long stamp){return foreground&&stamp==generation;}
    synchronized void restore(Target restored,long stoppedAt){target=restored;pausedAt=stoppedAt;active=false;}
    synchronized Ticket start(long now){
        foreground=true;generation++;
        if(target!=null&&pausedAt>=0&&now>=pausedAt&&now-pausedAt<TTL_MS)return new Ticket(generation,target);
        target=null;pausedAt=-1;active=false;return null;
    }
    synchronized void pause(long now,boolean connected){
        foreground=false;generation++;
        if(active&&connected)pausedAt=now;
        active=false;
    }
    synchronized boolean connected(long stamp,Target connected){
        if(!current(stamp))return false;
        target=connected;pausedAt=-1;active=true;return true;
    }
    synchronized void clear(){generation++;target=null;pausedAt=-1;active=false;}
    synchronized boolean owns(String mode){return target!=null&&target.mode.equals(mode);}
    synchronized Snapshot checkpoint(){return target!=null&&pausedAt>=0?new Snapshot(target,pausedAt):null;}
}
