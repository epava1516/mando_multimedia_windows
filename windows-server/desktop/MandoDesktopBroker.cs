using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.ServiceProcess;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using Microsoft.Win32.SafeHandles;

// No network listener. No caller-supplied paths, executables, session IDs or desktop names.
internal sealed class MandoDesktopBroker : ServiceBase {
    internal const string ServiceId = "MandoDesktopBroker";
    internal const string PublicPipe = "Mando.Desktop.v1";
    static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength=8192, RecursionLimit=8 };
    static readonly string Root = AppDomain.CurrentDomain.BaseDirectory;
    volatile bool stopping;
    Thread thread;
    NamedPipeServerStream listener, workerPipe;
    Process worker;
    uint workerSession;
    string allowedSid;

    [StructLayout(LayoutKind.Sequential)] struct SA { public int length; public IntPtr descriptor; public int inherit; }
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct STARTUPINFO {
        public uint cb; public string reserved,desktop,title; public uint x,y,xSize,ySize,xChars,yChars,fill,flags;
        public ushort show,reservedSize; public IntPtr reservedData,input,output,error;
    }
    [StructLayout(LayoutKind.Sequential)] struct PROCESSINFO {public IntPtr process,thread;public uint pid,tid;}
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern SafePipeHandle CreateNamedPipe(string name,uint open,uint mode,uint instances,uint outSize,uint inSize,uint timeout,ref SA security);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetNamedPipeClientProcessId(SafePipeHandle pipe,out uint pid);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool GetNamedPipeClientSessionId(SafePipeHandle pipe,out uint session);
    [DllImport("kernel32.dll")] static extern uint WTSGetActiveConsoleSessionId();
    [DllImport("kernel32.dll")] static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [StructLayout(LayoutKind.Sequential)] struct LUID {public uint low;public int high;}
    [StructLayout(LayoutKind.Sequential)] struct PRIVILEGE {public uint count;public LUID luid;public uint attributes;}
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool LookupPrivilegeValue(string system,string name,out LUID luid);
    [DllImport("advapi32.dll",SetLastError=true)] static extern bool AdjustTokenPrivileges(IntPtr token,bool disable,ref PRIVILEGE state,uint length,IntPtr previous,IntPtr needed);
    [DllImport("advapi32.dll",SetLastError=true)] static extern bool OpenProcessToken(IntPtr process,uint access,out IntPtr token);
    [DllImport("advapi32.dll",SetLastError=true)] static extern bool DuplicateTokenEx(IntPtr token,uint access,IntPtr attributes,int impersonation,int type,out IntPtr duplicate);
    [DllImport("advapi32.dll",SetLastError=true)] static extern bool SetTokenInformation(IntPtr token,int information,ref uint value,uint length);
    [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool CreateProcessAsUser(IntPtr token,string application,StringBuilder command,IntPtr processAttributes,IntPtr threadAttributes,
        bool inherit,uint flags,IntPtr environment,string directory,ref STARTUPINFO startup,out PROCESSINFO process);
    [DllImport("wtsapi32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool WTSQuerySessionInformation(IntPtr server,uint session,int information,out IntPtr buffer,out uint size);
    [DllImport("wtsapi32.dll")] static extern void WTSFreeMemory(IntPtr memory);
    [DllImport("user32.dll",SetLastError=true)] static extern IntPtr OpenInputDesktop(uint flags,bool inherit,uint access);
    [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr desktop);
    [DllImport("user32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
    static extern bool GetUserObjectInformation(IntPtr handle,int index,StringBuilder buffer,uint length,out uint needed);
    [DllImport("user32.dll",SetLastError=true)] static extern uint SendInput(uint count,INPUT[] events,int size);
    [StructLayout(LayoutKind.Sequential)] struct MOUSE {public int x,y;public uint data,flags,time;public UIntPtr extra;}
    [StructLayout(LayoutKind.Sequential)] struct KEY {public ushort vk,scan;public uint flags,time;public UIntPtr extra;}
    [StructLayout(LayoutKind.Explicit)] struct PAYLOAD {[FieldOffset(0)] public MOUSE mouse;[FieldOffset(0)] public KEY key;}
    [StructLayout(LayoutKind.Sequential)] struct INPUT {public uint type;public PAYLOAD data;}

    static NamedPipeServerStream NewPipe(string name,string sid) {
        PipeSecurity descriptor=new PipeSecurity();descriptor.SetAccessRuleProtection(true,false);
        SecurityIdentifier system=new SecurityIdentifier(WellKnownSidType.LocalSystemSid,null);
        descriptor.SetOwner(system);descriptor.AddAccessRule(new PipeAccessRule(system,PipeAccessRights.FullControl,AccessControlType.Allow));
        if(sid!=null)descriptor.AddAccessRule(new PipeAccessRule(new SecurityIdentifier(sid),PipeAccessRights.ReadWrite,AccessControlType.Allow));
        byte[] bytes=descriptor.GetSecurityDescriptorBinaryForm();GCHandle pinned=GCHandle.Alloc(bytes,GCHandleType.Pinned);
        SafePipeHandle handle;
        try {
            SA attributes=new SA {length=Marshal.SizeOf(typeof(SA)),descriptor=pinned.AddrOfPinnedObject()};
            // First instance, overlapped, duplex; PIPE_REJECT_REMOTE_CLIENTS. No SMB access.
            handle=CreateNamedPipe("\\\\.\\pipe\\"+name,0x40080003,8,1,8192,8192,0,ref attributes);
        } finally {pinned.Free();}
        if(handle.IsInvalid){handle.Dispose();throw new IOException("Cannot create protected local pipe");}
        return new NamedPipeServerStream(PipeDirection.InOut,true,false,handle);
    }
    static void Connected(NamedPipeServerStream pipe,int milliseconds) {
        IAsyncResult pending=pipe.BeginWaitForConnection(null,null);
        using(WaitHandle wait=pending.AsyncWaitHandle) {
            if(!wait.WaitOne(milliseconds))throw new TimeoutException();
            pipe.EndWaitForConnection(pending);
        }
    }
    static byte[] ReadBytes(PipeStream pipe,int length,long deadline) {
        byte[] data=new byte[length];int offset=0;
        while(offset<length) {
            int timeout=(int)Math.Max(0,deadline-Stopwatch.GetTimestamp()*1000/Stopwatch.Frequency);
            if(timeout<=0)throw new TimeoutException();
            IAsyncResult pending=pipe.BeginRead(data,offset,length-offset,null,null);
            int read;
            using(WaitHandle wait=pending.AsyncWaitHandle) {if(!wait.WaitOne(timeout))throw new TimeoutException();read=pipe.EndRead(pending);}
            if(read==0)throw new EndOfStreamException();offset+=read;
        }
        return data;
    }
    static Dictionary<string,object> Read(PipeStream pipe,int timeout) {
        long deadline=Stopwatch.GetTimestamp()*1000/Stopwatch.Frequency+timeout;
        byte[] size=ReadBytes(pipe,4,deadline);
        int length=(size[0]<<24)|(size[1]<<16)|(size[2]<<8)|size[3];
        if(length<1||length>8192)throw new IOException("Invalid frame");
        object value=Json.DeserializeObject(new UTF8Encoding(false,true).GetString(ReadBytes(pipe,length,deadline)));
        Dictionary<string,object> data=value as Dictionary<string,object>;
        if(data==null)throw new IOException("Object required");return data;
    }
    static void Write(PipeStream pipe,object value) {
        byte[] body=Encoding.UTF8.GetBytes(Json.Serialize(value));if(body.Length>8192)throw new IOException();
        byte[] frame=new byte[body.Length+4];frame[0]=(byte)(body.Length>>24);frame[1]=(byte)(body.Length>>16);
        frame[2]=(byte)(body.Length>>8);frame[3]=(byte)body.Length;Buffer.BlockCopy(body,0,frame,4,body.Length);
        IAsyncResult pending=pipe.BeginWrite(frame,0,frame.Length,null,null);
        using(WaitHandle wait=pending.AsyncWaitHandle){if(!wait.WaitOne(3000))throw new TimeoutException();pipe.EndWrite(pending);}
    }
    static bool Locked(uint session) {
        IntPtr buffer;uint bytes;
        if(!WTSQuerySessionInformation(IntPtr.Zero,session,25,out buffer,out bytes))return false;
        try {return bytes>=20 && Marshal.ReadInt32(buffer)==1 && Marshal.ReadInt32(buffer,16)==0;}
        finally {WTSFreeMemory(buffer);}
    }
    static string InputDesktop() {
        IntPtr handle=OpenInputDesktop(0,false,1);if(handle==IntPtr.Zero)return "";
        try {StringBuilder name=new StringBuilder(128);uint size;return GetUserObjectInformation(handle,2,name,256,out size)?name.ToString():"";}
        finally {CloseDesktop(handle);}
    }
    static string Text(Dictionary<string,object> value,string key) {object result;return value.TryGetValue(key,out result)?result as string:null;}
    static int Number(Dictionary<string,object> value,string key,int min,int max) {
        object result;if(!value.TryGetValue(key,out result)||!(result is int)||(int)result<min||(int)result>max)throw new IOException("Invalid number");
        return (int)result;
    }
    static byte Vk(string name) {
        switch(name) {
            case "ESC":return 27;case "TAB":return 9;case "ENTER":return 13;case "SPACE":return 32;
            case "BACKSPACE":return 8;case "DELETE":return 46;case "INSERT":return 45;
            case "HOME":return 36;case "END":return 35;case "PAGEUP":return 33;case "PAGEDOWN":return 34;
            case "LEFT":return 37;case "UP":return 38;case "RIGHT":return 39;case "DOWN":return 40;
            case "VOLUME_DOWN":return 174;case "VOLUME_UP":return 175;case "MUTE":return 173;
            case "PLAY_PAUSE":return 179;case "STOP":return 178;case "NEXT":return 176;case "PREVIOUS":return 177;
        }
        if(name!=null&&name.Length==1&&((name[0]>='A'&&name[0]<='Z')||(name[0]>='0'&&name[0]<='9')))return (byte)name[0];
        int function;if(name!=null&&name.StartsWith("F",StringComparison.Ordinal)&&Int32.TryParse(name.Substring(1),out function)&&function>=1&&function<=12)return (byte)(111+function);
        throw new IOException("Key not allowed");
    }
    static INPUT Key(ushort key,bool up,ushort unicode) {
        uint flags=(up?2u:0u)|(unicode!=0?4u:((key>=33&&key<=46)||key==91||(key>=173&&key<=179)?1u:0u));
        return new INPUT{type=1,data=new PAYLOAD{key=new KEY{vk=key,scan=unicode,flags=flags}}};
    }
    static INPUT Mouse(uint flags,int x,int y,int data) {return new INPUT{type=0,data=new PAYLOAD{mouse=new MOUSE{x=x,y=y,data=unchecked((uint)data),flags=flags}}};}
    static INPUT[] Events(Dictionary<string,object> action) {
        List<INPUT> result=new List<INPUT>();string kind=Text(action,"type");
        switch(kind) {
            case "move":result.Add(Mouse(1,Number(action,"dx",-500,500),Number(action,"dy",-500,500),0));break;
            case "click":
                string button=Text(action,"button");if(button!="left"&&button!="right")throw new IOException();
                result.Add(Mouse(button=="left"?2u:8u,0,0,0));result.Add(Mouse(button=="left"?4u:16u,0,0,0));break;
            case "scroll":result.Add(Mouse(0x800,0,0,Number(action,"steps",-10,10)*120));break;
            case "text":
                string text=Text(action,"text");if(text==null||text.Length<1||text.Length>1000)throw new IOException();
                new UnicodeEncoding(false,false,true).GetBytes(text);
                int points=0;for(int i=0;i<text.Length;i++){if(Char.IsHighSurrogate(text[i]))i++;points++;}if(points>500)throw new IOException();
                foreach(char ch in text){if(ch==0)throw new IOException();result.Add(Key(0,false,ch));result.Add(Key(0,true,ch));}break;
            case "key":
                byte key=Vk(Text(action,"key"));object value;object[] modifiers=action.TryGetValue("modifiers",out value)?value as object[]:new object[0];
                if(modifiers==null||modifiers.Length>4)throw new IOException();
                List<ushort> codes=new List<ushort>();HashSet<string> unique=new HashSet<string>();
                foreach(object item in modifiers) {
                    string name=item as string;if(name==null||!unique.Add(name))throw new IOException();
                    switch(name){case "CTRL":codes.Add(17);break;case "ALT":codes.Add(18);break;case "SHIFT":codes.Add(16);break;case "WIN":codes.Add(91);break;default:throw new IOException();}
                }
                codes.Add(key);foreach(ushort code in codes)result.Add(Key(code,false,0));codes.Reverse();foreach(ushort code in codes)result.Add(Key(code,true,0));break;
            default:throw new IOException("Action not allowed on lock screen");
        }
        return result.ToArray();
    }
    static void Execute(INPUT[] events) {
        if(SendInput((uint)events.Length,events,Marshal.SizeOf(typeof(INPUT)))!=(uint)events.Length) {
            INPUT[] release={Key(17,true,0),Key(18,true,0),Key(16,true,0),Key(91,true,0),Mouse(0x14,0,0,0)};
            SendInput((uint)release.Length,release,Marshal.SizeOf(typeof(INPUT)));throw new IOException("Input rejected");
        }
    }
    void StopWorker() {
        if(workerPipe!=null){workerPipe.Dispose();workerPipe=null;}
        if(worker!=null){try{if(!worker.HasExited)worker.Kill();}catch{}worker.Dispose();worker=null;}
    }
    void EnsureWorker(uint session) {
        if(worker!=null&&!worker.HasExited&&workerSession==session&&workerPipe!=null&&workerPipe.IsConnected)return;
        StopWorker();string nonce=Guid.NewGuid().ToString("N");workerPipe=NewPipe("Mando.Private."+nonce,null);
        IntPtr original=IntPtr.Zero,token=IntPtr.Zero;
        PROCESSINFO process=new PROCESSINFO();
        try {
            if(!OpenProcessToken(GetCurrentProcess(),42,out original))throw new IOException("Cannot open own service token");
            LUID tcb;if(!LookupPrivilegeValue(null,"SeTcbPrivilege",out tcb))throw new IOException();
            PRIVILEGE privilege=new PRIVILEGE{count=1,luid=tcb,attributes=2};
            if(!AdjustTokenPrivileges(original,false,ref privilege,0,IntPtr.Zero,IntPtr.Zero)||Marshal.GetLastWin32Error()!=0)throw new IOException("Session privilege unavailable");
            if(!DuplicateTokenEx(original,0xF01FF,IntPtr.Zero,2,1,out token)||
                    !SetTokenInformation(token,12,ref session,4))throw new IOException("Cannot create desktop token");
            string executable=Path.Combine(Root,"MandoDesktopBroker.exe");
            STARTUPINFO startup=new STARTUPINFO{cb=(uint)Marshal.SizeOf(typeof(STARTUPINFO)),desktop="WinSta0\\Winlogon"};
            StringBuilder command=new StringBuilder("\""+executable+"\" --worker "+nonce);
            if(!CreateProcessAsUser(token,executable,command,IntPtr.Zero,IntPtr.Zero,false,0x08000000,IntPtr.Zero,Root,ref startup,out process))throw new IOException("Cannot start desktop worker");
            worker=Process.GetProcessById((int)process.pid);workerSession=session;
            Connected(workerPipe,5000);uint peer;
            if(!GetNamedPipeClientProcessId(workerPipe.SafePipeHandle,out peer)||peer!=process.pid)throw new IOException("Wrong worker identity");
        } catch {StopWorker();throw;}
        finally{if(process.process!=IntPtr.Zero)CloseHandle(process.process);if(process.thread!=IntPtr.Zero)CloseHandle(process.thread);if(token!=IntPtr.Zero)CloseHandle(token);if(original!=IntPtr.Zero)CloseHandle(original);}
    }
    bool Authorized(NamedPipeServerStream pipe,uint session) {
        uint clientSession;if(!GetNamedPipeClientSessionId(pipe.SafePipeHandle,out clientSession)||clientSession!=session)return false;
        string sid=null;pipe.RunAsClient(delegate{using(WindowsIdentity user=WindowsIdentity.GetCurrent(true)){if(user!=null&&user.User!=null)sid=user.User.Value;}});
        return sid==allowedSid;
    }
    void Serve() {
        try {
            using(NamedPipeServerStream pipe=NewPipe(PublicPipe,allowedSid)) {
                listener=pipe;
                while(!stopping) {
                    try {
                        IAsyncResult accept=pipe.BeginWaitForConnection(null,null);
                        using(WaitHandle wait=accept.AsyncWaitHandle) {
                            while(!wait.WaitOne(1000)){if(stopping)return;}
                            pipe.EndWaitForConnection(accept);
                        }
                        uint session=WTSGetActiveConsoleSessionId();
                        if(session==UInt32.MaxValue||!Authorized(pipe,session))throw new IOException("Wrong client");
                        while(!stopping&&pipe.IsConnected) {
                            Dictionary<string,object> request=Read(pipe,30000);session=WTSGetActiveConsoleSessionId();
                            if(session==UInt32.MaxValue||!Authorized(pipe,session))throw new IOException("Session changed");
                            if(Text(request,"op")=="status") {Write(pipe,new{ok=true,available=true,locked=Locked(session),protocol=1});continue;}
                            object raw;Dictionary<string,object> action;
                            if(Text(request,"op")!="input"||!request.TryGetValue("action",out raw)||(action=raw as Dictionary<string,object>)==null)throw new IOException();
                            Events(action); // Validate before creating any privileged worker.
                            if(!Locked(session)) {Write(pipe,new{ok=false});continue;}
                            EnsureWorker(session);Write(workerPipe,action);Dictionary<string,object> response=Read(workerPipe,3000);
                            Write(pipe,new{ok=response.ContainsKey("ok")&&Object.Equals(response["ok"],true)});
                        }
                    } catch {StopWorker();}
                    finally {if(!stopping&&pipe.IsConnected)pipe.Disconnect();}
                }
            }
        } catch {if(!stopping)Environment.Exit(2);}
    }
    protected override void OnStart(string[] args) {
        if(!WindowsIdentity.GetCurrent().IsSystem)throw new InvalidOperationException();
        allowedSid=File.ReadAllText(Path.Combine(Root,"allowed.sid")).Trim();new SecurityIdentifier(allowedSid);
        stopping=false;thread=new Thread(Serve){IsBackground=true};thread.Start();
    }
    protected override void OnStop(){stopping=true;if(listener!=null)listener.Dispose();StopWorker();if(thread!=null)thread.Join(2000);}
    static int Worker(string nonce) {
        if(!WindowsIdentity.GetCurrent().IsSystem||nonce.Length!=32)return 5;
        foreach(char c in nonce)if(!Uri.IsHexDigit(c))return 5;
        using(NamedPipeClientStream pipe=new NamedPipeClientStream(".","Mando.Private."+nonce,PipeDirection.InOut,PipeOptions.Asynchronous)) {
            pipe.Connect(5000);
            while(true) {
                Dictionary<string,object> action=Read(pipe,30000);
                bool ok=false;
                try {
                    uint session=(uint)Process.GetCurrentProcess().SessionId;
                    if(session==WTSGetActiveConsoleSessionId()&&Locked(session)&&String.Equals(InputDesktop(),"Winlogon",StringComparison.OrdinalIgnoreCase)) {
                        Execute(Events(action));ok=true;
                    }
                } catch {}
                Write(pipe,new{ok=ok});
            }
        }
    }
    static int SelfTest() {
        int expected=IntPtr.Size==8?40:28;if(Marshal.SizeOf(typeof(INPUT))!=expected)throw new Exception("INPUT ABI");
        if(Events(new Dictionary<string,object>{{"type","move"},{"dx",1},{"dy",-1}}).Length!=1)throw new Exception();
        if(Events(new Dictionary<string,object>{{"type","text"},{"text","a\u00f1\ud83d\ude42"}}).Length!=8)throw new Exception();
        try{Events(new Dictionary<string,object>{{"type","execute"},{"path","cmd.exe"}});throw new Exception("Accepted arbitrary action");}catch(IOException){}
        try{Events(new Dictionary<string,object>{{"type","move"},{"dx",true},{"dy",0}});throw new Exception("Accepted boolean");}catch(IOException){}
        return 0;
    }
    [STAThread] static int Main(string[] args) {
        try {
            if(args.Length==1&&args[0]=="--self-test")return SelfTest();
            if(args.Length==2&&args[0]=="--worker")return Worker(args[1]);
            if(args.Length!=0)return 87;
            using(MandoDesktopBroker service=new MandoDesktopBroker()){service.ServiceName=ServiceId;ServiceBase.Run(service);}return 0;
        }catch{return 1;}
    }
}
