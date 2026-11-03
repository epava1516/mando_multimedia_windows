package net.mando.personal;

public final class ResumeWindowTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static ResumeWindow active(){
        ResumeWindow state=new ResumeWindow();state.start(100);
        if(!state.connected(state.generation(),new ResumeWindow.Target("official","192.168.1.105")))throw new AssertionError();
        return state;
    }
    public static void main(String[] args){
        ResumeWindow state=active();long oldInput=state.generation();state.pause(1000,true);
        check(!state.current(oldInput),"Background must invalidate pending input");
        ResumeWindow.Ticket ticket=state.start(1000+ResumeWindow.TTL_MS-1);
        check(ticket!=null&&ticket.target.host.equals("192.168.1.105"),"Return inside 20 minutes must resume same PC");
        check(!state.current(oldInput),"Old input must remain invalid after return");
        state.clear();check(!state.current(ticket.generation),"Disconnect cancels in-flight reconnect");
        state.pause(2000,false);check(state.start(2001)==null,"Explicit disconnect must not resume");
        state=active();state.pause(1000,true);
        check(state.start(1000+ResumeWindow.TTL_MS)==null,"Exact TTL boundary expires");
        state=active();state.pause(1000,true);ticket=state.start(1100);state.pause(1200,false);
        check(!state.connected(ticket.generation,ticket.target),"Late reconnect cannot survive leaving app");
        check(state.checkpoint().pausedAt==1000,"Failed or interrupted reconnect must not extend TTL");
        check(state.start(1000+ResumeWindow.TTL_MS)==null,"Repeated failures cannot keep grace alive forever");
        state=new ResumeWindow();state.restore(new ResumeWindow.Target("own",""),4000);
        ticket=state.start(5000);check(ticket!=null&&ticket.target.mode.equals("own"),"Validated persisted checkpoint can resume own server");
        check(state.connected(ticket.generation,ticket.target),"Current reconnect can become active");
        state.pause(6000,true);check(state.checkpoint().pausedAt==6000,"A successful return renews grace on next exit");
        state=new ResumeWindow();state.restore(new ResumeWindow.Target("official","192.168.1.1"),8000);
        check(state.start(7000)==null,"Elapsed clock moving backwards invalidates checkpoint");
        state=new ResumeWindow();state.start(100);state.pause(200,false);
        check(state.start(300)==null,"IR-only session must not invent a PC connection");
        System.out.println(checks+" lifecycle and TTL checks passed");
    }
}
