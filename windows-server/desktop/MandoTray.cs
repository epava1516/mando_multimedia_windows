using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Principal;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

internal sealed class MandoTray : Form {
    sealed class Address {
        internal string IP,Name;
        public override string ToString(){return Name+" · "+IP;}
    }
    readonly NotifyIcon tray=new NotifyIcon();
    readonly ComboBox networks=new ComboBox();
    readonly Label status=new Label();
    readonly TextBox code=new TextBox();
    readonly PictureBox qr=new PictureBox();
    readonly Button refresh=new Button(),copy=new Button(),restart=new Button(),diagnostics=new Button();
    string desktopStatus="";
    readonly System.Windows.Forms.Timer timer=new System.Windows.Forms.Timer();
    readonly bool smoke,background;
    readonly string smokeDirectory;
    Process child;
    string activeHost;
    bool exiting,ready;
    int ticks,smokeResult=1;
    DateTime codeUntil;
    string startupError="";
    static readonly Regex Pairing=new Regex(@"^mando1:([0-9.]{7,15}):([0-9]{4,5}):[a-f0-9]{64}:[0-9]{8}$",RegexOptions.CultureInvariant);

    static bool Private(string value) {
        IPAddress ip;if(!IPAddress.TryParse(value,out ip)||ip.AddressFamily!=AddressFamily.InterNetwork)return false;
        byte[] b=ip.GetAddressBytes();return b[0]==10||(b[0]==172&&b[1]>=16&&b[1]<=31)||(b[0]==192&&b[1]==168);
    }
    static List<Address> Addresses() {
        var found=new List<Address>();
        foreach(var nic in NetworkInterface.GetAllNetworkInterfaces()) {
            if(nic.OperationalStatus!=OperationalStatus.Up)continue;
            if(nic.NetworkInterfaceType!=NetworkInterfaceType.Wireless80211&&nic.NetworkInterfaceType!=NetworkInterfaceType.Ethernet)continue;
            string name=nic.Name+" "+nic.Description;
            if(Regex.IsMatch(name,"vpn|wireguard|tailscale|zerotier|tunnel|virtual",RegexOptions.IgnoreCase))continue;
            foreach(var entry in nic.GetIPProperties().UnicastAddresses) {
                string ip=entry.Address.ToString();
                if(Private(ip)&&!found.Any(a=>a.IP==ip))found.Add(new Address{IP=ip,Name=nic.Name});
            }
        }
        return found;
    }
    internal MandoTray(bool test,bool hidden) {
        smoke=test;background=hidden;
        if(smoke)smokeDirectory=Path.Combine(Path.GetTempPath(),"mando-tray-test-"+Guid.NewGuid().ToString("N"));
        Text="Mando Server";ClientSize=new Size(600,680);MinimumSize=new Size(540,520);
        if(background){WindowState=FormWindowState.Minimized;ShowInTaskbar=false;}
        StartPosition=FormStartPosition.CenterScreen;Font=new Font("Segoe UI",10);Icon=SystemIcons.Application;
        var layout=new TableLayoutPanel{Dock=DockStyle.Fill,Padding=new Padding(18),ColumnCount=1,RowCount=7,AutoScroll=true};
        for(int row=0;row<7;row++)layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
        layout.Controls.Add(new Label{Text="Mando Server",AutoSize=true,Font=new Font("Segoe UI",17,FontStyle.Bold)});
        networks.DropDownStyle=ComboBoxStyle.DropDownList;networks.Dock=DockStyle.Fill;
        layout.Controls.Add(networks);
        status.AutoSize=true;status.MaximumSize=new Size(490,0);status.Text="Detectando la red local…";layout.Controls.Add(status);
        code.ReadOnly=true;code.Multiline=true;code.Height=62;code.Dock=DockStyle.Fill;code.ScrollBars=ScrollBars.Vertical;
        qr.Size=new Size(280,280);qr.SizeMode=PictureBoxSizeMode.Zoom;qr.AccessibleName="QR para emparejar Mando en Android";qr.Anchor=AnchorStyles.Top;layout.Controls.Add(qr);
        code.AccessibleName="Código de emparejamiento del PC";layout.Controls.Add(code);
        var buttons=new FlowLayoutPanel{AutoSize=true,Dock=DockStyle.Fill};
        copy.Text="Copiar código";refresh.Text="Nuevo código";restart.Text="Reiniciar";
        diagnostics.Text="Diagnóstico";
        foreach(var b in new[]{copy,refresh,restart,diagnostics}){b.AutoSize=true;b.MinimumSize=new Size(110,36);buttons.Controls.Add(b);}
        layout.Controls.Add(buttons);
        layout.Controls.Add(new Label{AutoSize=true,MaximumSize=new Size(490,0),Text="En Android: Escanear QR del PC → Conectar. El PC queda recordado; si cambia su IP, Buscar en Wi-Fi permite recuperarlo."});
        Controls.Add(layout);
        var menu=new ContextMenuStrip();
        menu.Items.Add("Abrir Mando",null,(s,e)=>ShowWindow());
        menu.Items.Add("Copiar código",null,(s,e)=>CopyCode());
        menu.Items.Add("Nuevo código",null,(s,e)=>Send("R"));
        menu.Items.Add(new ToolStripSeparator());menu.Items.Add("Salir",null,(s,e)=>ExitServer());
        tray.Text="Mando Server";tray.Icon=Icon;tray.ContextMenuStrip=menu;tray.Visible=true;
        tray.DoubleClick+=(s,e)=>ShowWindow();
        copy.Click+=(s,e)=>CopyCode();refresh.Click+=(s,e)=>Send("R");restart.Click+=(s,e)=>StartServer();diagnostics.Click+=(s,e)=>ShowDiagnostics();
        FormClosing+=(s,e)=>{if(e.CloseReason==CloseReason.UserClosing&&!exiting){e.Cancel=true;Hide();}};
        FormClosed+=(s,e)=>{timer.Stop();StopServer();ClearQr();tray.Visible=false;tray.Dispose();timer.Dispose();};
        Shown+=(s,e)=>{
            if(smoke)networks.Items.Add(new Address{IP="127.0.0.1",Name="Prueba aislada"});
            else foreach(var address in Addresses())networks.Items.Add(address);
            if(networks.Items.Count>0){networks.SelectedIndex=0;StartServer();}
            else SetStatus("Esperando Wi-Fi o Ethernet. Mando iniciará el servidor automáticamente.");
            if(background)Hide();
            timer.Interval=1000;timer.Tick+=Tick;timer.Start();
        };
    }
    void ClearQr(){var previous=qr.Image;qr.Image=null;if(previous!=null)previous.Dispose();}
    void SetQr(string line) {
        string[] fields=line.Split(' ');int width;
        if(fields.Length!=3||!Int32.TryParse(fields[1],out width)||width<21||width>97||fields[2].Length!=width*width||!Regex.IsMatch(fields[2],"^[01]+$"))return;
        var image=new Bitmap(width*4,width*4);
        using(var graphics=Graphics.FromImage(image)) {
            graphics.Clear(Color.White);
            for(int y=0;y<width;y++)for(int x=0;x<width;x++)if(fields[2][y*width+x]=='1')graphics.FillRectangle(Brushes.Black,x*4,y*4,4,4);
        }
        ClearQr();qr.Image=image;
    }
    void ShowDiagnostics() {
        string account="No disponible",service="No disponible",configured="No disponible";
        try {
            string sid=WindowsIdentity.GetCurrent().User.Value;
            string root=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"MandoDesktop");
            if(File.Exists(Path.Combine(root,"allowed.sid")))configured=File.ReadAllText(Path.Combine(root,"allowed.sid")).Trim()==sid?"Cuenta autorizada: coincide":"Cuenta autorizada: NO coincide";
            account="Sesión del proceso: "+Process.GetCurrentProcess().SessionId;
            using(var controller=new System.ServiceProcess.ServiceController("MandoDesktopBroker"))service="Servicio: "+controller.Status;
        }catch(Exception){}
        MessageBox.Show(service+"\r\n"+configured+"\r\n"+account+"\r\n"+(desktopStatus.Length>0?desktopStatus:"Sin fallo de bloqueo detectado")+"\r\n\r\nEl escritorio normal funciona aunque el bloqueo no esté disponible.","Diagnóstico de Mando");
    }
    void ShowWindow(){ShowInTaskbar=true;Show();WindowState=FormWindowState.Normal;Activate();if(ready&&DateTime.UtcNow>=codeUntil)Send("R");}
    void SetStatus(string value){status.Text=value;}
    void OnUI(Action action){if(!IsDisposed&&IsHandleCreated)try{BeginInvoke(action);}catch(InvalidOperationException){}}
    void StartServer() {
        StopServer();ready=false;code.Clear();ClearQr();startupError="";desktopStatus="";copy.Enabled=false;refresh.Enabled=false;
        var selected=networks.SelectedItem as Address;
        if(selected==null||(!Private(selected.IP)&&!smoke)){SetStatus("Selecciona una red local para iniciar.");return;}
        activeHost=selected.IP;
        var info=new ProcessStartInfo(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"MandoServer.exe"),"--host "+activeHost+(smoke?" --dry-run":"")) {
            UseShellExecute=false,CreateNoWindow=true,RedirectStandardInput=true,RedirectStandardOutput=true,RedirectStandardError=true,
            WorkingDirectory=AppDomain.CurrentDomain.BaseDirectory
        };
        info.EnvironmentVariables["PYINSTALLER_RESET_ENVIRONMENT"]="1";
        if(smoke){Directory.CreateDirectory(smokeDirectory);info.EnvironmentVariables["LOCALAPPDATA"]=smokeDirectory;}
        var process=new Process{StartInfo=info};
        process.OutputDataReceived+=(s,e)=>{
            string line=e.Data;if(line==null||line.Length>10000)return;
            OnUI(()=>{
                if(child!=process)return;
                if(line.StartsWith("MANDO_QR ",StringComparison.Ordinal)){if(ready)SetQr(line);return;}
                if(line.Length>256)return;
                var match=Pairing.Match(line);
                if(match.Success&&match.Groups[1].Value==activeHost&&match.Groups[2].Value=="1979") {
                    ClearQr();code.Text=line;codeUntil=DateTime.UtcNow.AddMinutes(10);ready=true;copy.Enabled=true;refresh.Enabled=true;
                    SetStatus("Escuchando en "+activeHost+":1979 · esperando al móvil"+(desktopStatus.Length>0?" · bloqueo pendiente":""));
                } else if(Regex.IsMatch(line,@"^MANDO_STATUS [0-4]$")) {
                    SetStatus("Escuchando en "+activeHost+":1979 · móviles conectados: "+line.Substring(13)+(desktopStatus.Length>0?" · bloqueo pendiente":""));
                } else if(line.StartsWith("MANDO_DESKTOP_UNAVAILABLE DESKTOP_",StringComparison.Ordinal)){desktopStatus=line.Substring(26);SetStatus("Servidor activo. Bloqueo no disponible; abre Diagnóstico.");}
                else if(line=="MANDO_DISCOVERY_UNAVAILABLE")SetStatus("Servidor activo; la búsqueda Wi-Fi no está disponible. Puedes conectar con el código.");
            });
        };
        process.ErrorDataReceived+=(s,e)=>{
            string line=e.Data;if(line==null)return;
            if(line.Contains("10048"))startupError="El puerto 1979 está ocupado. Cierra Remote Mouse y pulsa Reiniciar.";
            else if(line.Contains("10049"))startupError="La IP ya no está disponible. Selecciona la red actual y pulsa Reiniciar.";
            else if(line.Contains("Instala primero"))startupError="No está listo el servicio de escritorio. Reinstala Mando con tu cuenta de Windows.";
        };
        try {child=process;process.Start();process.BeginOutputReadLine();process.BeginErrorReadLine();SetStatus("Iniciando en "+activeHost+"…");}
        catch(Exception){child=null;process.Dispose();SetStatus("No se pudo abrir MandoServer.exe. Reinstala el paquete completo.");}
    }
    void Send(string command) {
        try {if(child!=null&&!child.HasExited){child.StandardInput.WriteLine(command);child.StandardInput.Flush();}}
        catch(IOException){SetStatus("El servidor se ha cerrado. Pulsa Reiniciar.");}
        catch(InvalidOperationException){}
    }
    void CopyCode() {
        if(!ready||DateTime.UtcNow>=codeUntil){SetStatus("El código ha caducado. Pulsa Nuevo código para emparejar.");return;}
        try {Clipboard.SetText(code.Text);}catch(System.Runtime.InteropServices.ExternalException){SetStatus("El portapapeles está ocupado. Inténtalo de nuevo.");}
    }
    void StopServer() {
        var previous=child;child=null;ready=false;
        if(previous==null)return;
        try {
            if(!previous.HasExited){previous.StandardInput.WriteLine("STOP");previous.StandardInput.Flush();if(!previous.WaitForExit(4000))previous.Kill();}
            previous.WaitForExit(2000);
        }catch(InvalidOperationException){}catch(IOException){}catch(System.ComponentModel.Win32Exception){}
        finally{previous.Dispose();}
    }
    void ExitServer(){exiting=true;Close();}
    void Tick(object sender,EventArgs e) {
        ticks++;
        if(smoke){if(ready&&qr.Image!=null){if(background&&Visible){ExitServer();return;}Close();smokeResult=!Visible&&tray.Visible&&child!=null&&!child.HasExited?0:1;ExitServer();}else if(ticks>=15)ExitServer();return;}
        if(child!=null&&child.HasExited){ready=false;copy.Enabled=false;refresh.Enabled=false;SetStatus(startupError.Length>0?startupError:"El servidor se ha cerrado. Comprueba el servicio de Mando y pulsa Reiniciar.");}
        else if(ready&&ticks%3==0)Send("STATUS");
        if(ready&&DateTime.UtcNow>=codeUntil){copy.Enabled=false;ClearQr();}
        if(ticks%5==0&&!exiting) {
            var available=Addresses();
            if(ready&&!available.Any(a=>a.IP==activeHost)) {
                StopServer();code.Clear();ClearQr();copy.Enabled=false;refresh.Enabled=false;
                SetStatus("La red ha cambiado. Esperando una dirección local para reconectar…");
            }
            if(!ready&&available.Count>0) {
                string previous=(networks.SelectedItem as Address)==null?null:((Address)networks.SelectedItem).IP;
                networks.Items.Clear();foreach(var address in available)networks.Items.Add(address);
                int selected=available.FindIndex(a=>a.IP==previous);networks.SelectedIndex=selected<0?0:selected;
                StartServer();
            }
        }
    }
    [STAThread] static int Main(string[] args) {
        bool smoke=args.Contains("--smoke-test"),background=args.Contains("--background");
        if(args.Length==1&&args[0]=="--self-test") {
            if(!Private("192.168.1.105")||!Private("10.8.0.2")||Private("8.8.8.8")||Private("127.0.0.1")||Private("not-ip"))return 1;
            if(!Pairing.IsMatch("mando1:192.168.1.105:1979:"+new string('a',64)+":12345678"))return 2;
            return 0;
        }
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
        if(!smoke&&new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator)) {
            MessageBox.Show("Abre Mando Server desde Inicio con tu cuenta normal, sin ejecutar como administrador.","Mando");return 1;
        }
        bool created;
        using(var mutex=new Mutex(true,@"Local\Mando.Server.Tray."+WindowsIdentity.GetCurrent().User.Value,out created)) {
            if(!created){if(!smoke&&!background)MessageBox.Show("Mando ya está abierto. Busca su icono en la bandeja del sistema.","Mando");return 0;}
            try {
                using(var form=new MandoTray(smoke,background)) {
                    Application.Run(form);
                    if(smoke){File.WriteAllText(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"tray-smoke-status.txt"),form.smokeResult==0?"passed":form.status.Text);try{Directory.Delete(form.smokeDirectory,true);}catch(IOException){}return form.smokeResult;}
                }
            }finally{mutex.ReleaseMutex();}
        }
        return 0;
    }
}
