using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

internal sealed class MandoUpdater : Form {
    const string Feed="https://mando.sherlockhomeless.net/updates.json";
    const string PublicKey=@"<RSAKeyValue><Modulus>vWjJQydIT+2Vvnm15lhMrZhkfevP5+lUyR6XRyRpZgQeP4uZ0xZ0+peY8P10RqHjGr4WkSm9F7byFY9VHzbLUc9Fpf9at33GDOA3aswUnj0nVcJWKfXNAQGToAYCo1ApFdi6WUkCgjFfcNTxbUXNeaUvumsds2tMLuj5WfSkm/cAzJdsyt/5L6FBCsdITloHDHFepksNkJq+ZZc9bjmhOk/J4WfTr25h4ko89PvE4DxHTYXenYhjQYRHlCG9yOUD38voNLsdIXapFRf7DjwmKNPSaFVl0pstWgzmITC3T8UQSCkTtxqKz1X5Z0xGRk5hsCW8cAEo3ZFX6N7q2l27BYD/b+Wo0o+uuOchVErOVpdKM9UFdYFIRX/mErTL/+7tpGVSJLVnZZcTOtMT/AnYRQikokQ5AcWYpLBvwnleMx5Nt/zc3aWI4WiXbXNIDSvCmR7M3DrjfsMaxc2QMWMFUsEmpTGr0xRG7e0hqHkjnL4xTqeRuKZGKc/csSRRIYnv</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>";
    const string Fixture=@"{""payload"":""eyJ2ZXJzaW9uIjoiMC41LjAtYWxwaGEuMTQiLCJ1cmwiOiJodHRwczovL21hbmRvLnNoZXJsb2NraG9tZWxlc3MubmV0L2Rvd25sb2Fkcy9NYW5kb1NldHVwLTAuNS4wLWFscGhhLjE0LmV4ZSIsImJ5dGVzIjo5LCJzaGEyNTYiOiI3OGE4MzQwM2ZjYjNiN2EzZmMxZGYxYWJiZWI2ZGZhZDY1MmMwMWU1NjFlZmQ5YmE2OTg1ZjdjNzVjNDQyNDVhIiwiaXNzdWVkIjoiMjAyNi0xMS0xN1QyMzo1MDowM1oiLCJleHBpcmVzIjoiMjAyNy0wMi0xNVQyMzo1MDowM1oifQ=="",""signature"":""DwUXtgHYfMAycId58SpWCHdFxf3RtnWmaVu/0+OuGq83Z3bJE7/enb8GvvJOInO39HNp2COQLqKHoPwq85/MlG6SjkouY3XLnVJWB2VblowlF55BhfZDqFa+Q13J6dsVXCMq6Qy3QNoePUVjYd6txC8yBqL9NpAhJ5B+8cqpaR5wpUkUHAgDCdDKbOMkJDHSaUM+/RYBeOtlNIS3VVu0JoyQTu+yZyJMbF9IJ2eDuXI3Lv4P2x2JJw8n9F7Euxzh1TRlMHeTnE8xspslMgDt4aIy3wrmm2lmDsKB4ppvsgko8dodzU9M/tSH9wNKGivVQzoVb+MFNwG1cUXzFh7HQ8H0jTiGnJ7nc23d56c6OtjFg7+pNNk1k7Gbk8J6B4V1GxAHUwwWpvDCLSb5ntJaEQMcwsx5q2z53Wh9U5ffvcHaJ87QhcOvABlzs+iv8NyvmOigin0GmaZvtKxmLoHNxpWdQx7l6/83UGMsqVQZJINn3IyDg2xv+4bv6Z1NAUOT""}";
    static readonly Version Current=new Version(0,5,0,14);
    static readonly JavaScriptSerializer Json=new JavaScriptSerializer{MaxJsonLength=8192,RecursionLimit=8};
    static readonly string InstallRoot=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),"Mando");
    readonly Label label=new Label{Dock=DockStyle.Fill,Text="Buscando actualizaciones…",Padding=new Padding(20),AutoSize=false};
    readonly ProgressBar progress=new ProgressBar{Dock=DockStyle.Bottom,Height=18,Style=ProgressBarStyle.Marquee};
    volatile bool busy=true,cancelled,installing;
    HttpWebRequest request;
    sealed class Release {public string VersionText,Url,Hash;public Version Version;public int Bytes;}
    static string Text(Dictionary<string,object> data,string key) {object value;return data.TryGetValue(key,out value)?value as string:null;}
    static Release Verify(string envelope,bool checkClock=true) {
        if(envelope==null||envelope.Length>8192)throw new IOException();
        var wrapper=Json.DeserializeObject(envelope) as Dictionary<string,object>;
        if(wrapper==null)throw new IOException();
        byte[] body=Convert.FromBase64String(Text(wrapper,"payload")??""),signature=Convert.FromBase64String(Text(wrapper,"signature")??"");
        if(body.Length>4096||signature.Length!=384)throw new IOException();
        using(var rsa=new RSACryptoServiceProvider()) {
            rsa.PersistKeyInCsp=false;rsa.FromXmlString(PublicKey);
            if(!rsa.VerifyData(body,"SHA256",signature))throw new CryptographicException();
        }
        var data=Json.DeserializeObject(new UTF8Encoding(false,true).GetString(body)) as Dictionary<string,object>;
        if(data==null)throw new IOException();string version=Text(data,"version");
        Match match=Regex.Match(version??"",@"\A(\d{1,5})\.(\d{1,5})\.(\d{1,5})-alpha\.(\d{1,5})\z");
        if(!match.Success)throw new IOException();
        int[] parts=Enumerable.Range(1,4).Select(i=>Int32.Parse(match.Groups[i].Value)).ToArray();
        if(parts.Any(x=>x>65535))throw new IOException();
        string expected="https://mando.sherlockhomeless.net/downloads/MandoSetup-"+version+".exe";
        string hash=Text(data,"sha256");object size;
        if(Text(data,"url")!=expected||!Regex.IsMatch(hash??"",@"\A[a-f0-9]{64}\z")||!data.TryGetValue("bytes",out size)||!(size is int)||(int)size<1||(int)size>64*1024*1024)throw new IOException();
        DateTime issued,expires;
        if(!DateTime.TryParseExact(Text(data,"issued"),"yyyy-MM-dd'T'HH:mm:ss'Z'",null,System.Globalization.DateTimeStyles.AssumeUniversal|System.Globalization.DateTimeStyles.AdjustToUniversal,out issued)||
           !DateTime.TryParseExact(Text(data,"expires"),"yyyy-MM-dd'T'HH:mm:ss'Z'",null,System.Globalization.DateTimeStyles.AssumeUniversal|System.Globalization.DateTimeStyles.AdjustToUniversal,out expires)||
           expires<=issued||(expires-issued).TotalDays>100||(checkClock&&(DateTime.UtcNow>expires||DateTime.UtcNow<issued.AddDays(-1))))throw new IOException();
        return new Release{VersionText=version,Version=new Version(parts[0],parts[1],parts[2],parts[3]),Url=expected,Hash=hash,Bytes=(int)size};
    }
    static bool Matches(Stream file,Release release) {
        if(file.Length!=release.Bytes)return false;
        file.Position=0;if(file.ReadByte()!=77||file.ReadByte()!=90)return false;
        file.Position=0;
        using(var sha=SHA256.Create())return BitConverter.ToString(sha.ComputeHash(file)).Replace("-","").ToLowerInvariant()==release.Hash;
    }
    public MandoUpdater() {
        Text="Actualizar Mando";Size=new Size(460,180);StartPosition=FormStartPosition.CenterScreen;
        Font=new Font("Segoe UI",10);Controls.Add(label);Controls.Add(progress);
        FormClosing+=(s,e)=>{if(busy&&installing){e.Cancel=true;label.Text="Instalando. Mando se reiniciará al terminar.";return;}if(busy){cancelled=true;if(request!=null)request.Abort();e.Cancel=true;label.Text="Cancelando…";}};
        Shown+=async(s,e)=>await RunUpdate();
    }
    void Status(string text) {if(!IsDisposed)BeginInvoke((Action)(()=>label.Text=text));}
    async Task Download(string url,Stream output,int limit) {
        request=(HttpWebRequest)WebRequest.Create(url);request.AllowAutoRedirect=false;request.Timeout=20000;request.ReadWriteTimeout=20000;
        request.UserAgent="Mando-Updater/0.5.0";
        using(var response=(HttpWebResponse)await request.GetResponseAsync()) {
            if(response.StatusCode!=HttpStatusCode.OK||response.ContentLength>limit)throw new IOException();
            using(var input=response.GetResponseStream()) {
                byte[] buffer=new byte[65536];int total=0,read;
                while((read=await input.ReadAsync(buffer,0,buffer.Length))>0) {
                    if(cancelled)throw new OperationCanceledException();total+=read;if(total>limit)throw new IOException();
                    await output.WriteAsync(buffer,0,read);
                }
            }
        }
        request=null;
    }
    static void RestartTray() {
        string tray=Path.Combine(InstallRoot,"MandoTray.exe");
        if(File.Exists(tray))Process.Start(new ProcessStartInfo(tray,"--background"){UseShellExecute=false,WorkingDirectory=InstallRoot});
    }
    async Task RunUpdate() {
        string installer=null;
        try {
            Release release;
            using(var memory=new MemoryStream()) {
                await Download(Feed,memory,8192);
                release=Verify(new UTF8Encoding(false,true).GetString(memory.ToArray()));
            }
            if(release.Version<=Current){label.Text="Mando ya está actualizado.";return;}
            if(cancelled)throw new OperationCanceledException();
            if(MessageBox.Show("Hay una versión "+release.VersionText+".\nSe instalará y reiniciará Mando. Habrá un corte breve de conexión y Windows puede pedir autorización UAC.\n¿Actualizar ahora?","Actualizar Mando",MessageBoxButtons.YesNo,MessageBoxIcon.Information)!=DialogResult.Yes){label.Text="Actualización cancelada.";return;}
            label.Text="Descargando y verificando el instalador…";
            installer=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"MandoSetup.exe");
            using(var output=new FileStream(installer,FileMode.CreateNew,FileAccess.Write,FileShare.None))await Download(release.Url,output,release.Bytes);
            // Keep this handle open through UAC/install so the verified executable cannot be replaced.
            using(var locked=new FileStream(installer,FileMode.Open,FileAccess.Read,FileShare.Read)) {
                if(!Matches(locked,release))throw new CryptographicException();
                if(cancelled)throw new OperationCanceledException();
                label.Text="Autoriza Windows si lo solicita. Mando volverá a la bandeja al terminar.";
                using(var process=Process.Start(new ProcessStartInfo(installer,"/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /CLOSEAPPLICATIONS /FORCECLOSEAPPLICATIONS"){UseShellExecute=true,Verb="runas",WorkingDirectory=InstallRoot})) {
                    installing=true;
                    await Task.Run(()=>process.WaitForExit());
                    RestartTray();
                    if(process.ExitCode!=0)throw new IOException();
                }
            }
            label.Text="Actualización completada. Mando está de nuevo en la bandeja.";
        } catch(System.ComponentModel.Win32Exception error) {
            label.Text=error.NativeErrorCode==1223?"Actualización cancelada en Windows. El servidor actual sigue disponible.":"No se pudo iniciar la actualización. Abre Mando desde Inicio.";
        } catch(OperationCanceledException) {label.Text="Actualización cancelada.";}
        catch(Exception) {label.Text=cancelled?"Actualización cancelada.":"No se pudo verificar o completar la actualización. No se instalará un archivo sin verificar. Abre Mando desde Inicio si fuera necesario.";}
        finally {
            busy=false;progress.Style=ProgressBarStyle.Blocks;progress.Value=100;
            if(installer!=null)try{File.Delete(installer);}catch{}
            if(cancelled)Close();
        }
    }
    static int SelfTest() {
        var release=Verify(Fixture,false);
        if(release.VersionText!="0.5.0-alpha.14")return 1;
        using(var body=new MemoryStream(Encoding.ASCII.GetBytes("MZfixture")))if(!Matches(body,release))return 2;
        var wrapper=Json.DeserializeObject(Fixture) as Dictionary<string,object>;
        byte[] payload=Convert.FromBase64String((string)wrapper["payload"]);payload[0]^=1;wrapper["payload"]=Convert.ToBase64String(payload);
        try{Verify(Json.Serialize(wrapper),false);return 3;}catch(CryptographicException){}
        using(var wrong=new MemoryStream(Encoding.ASCII.GetBytes("MZtampered")))if(Matches(wrong,release))return 4;
        return 0;
    }
    [STAThread] static int Main(string[] args) {
        if(args.Length==1&&args[0]=="--self-test")return SelfTest();
        if(args.Length!=0)return 2;
        Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
        if(new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator)){MessageBox.Show("Abre el actualizador desde Mando con tu cuenta normal.");return 1;}
        // The worker must live outside Program Files so Setup cannot terminate it while replacing binaries.
        if(String.Equals(Path.GetDirectoryName(Application.ExecutablePath),InstallRoot,StringComparison.OrdinalIgnoreCase)) {
            string directory=Path.Combine(Path.GetTempPath(),"MandoUpdate-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(directory);
            string worker=Path.Combine(directory,"MandoUpdater.exe");File.Copy(Application.ExecutablePath,worker);
            Process.Start(new ProcessStartInfo(worker){UseShellExecute=false,WorkingDirectory=directory});return 0;
        }
        bool created;
        using(var mutex=new Mutex(true,@"Local\Mando.Updater."+WindowsIdentity.GetCurrent().User.Value,out created)) {
            if(!created)return 0;
            try {Application.Run(new MandoUpdater());}finally{mutex.ReleaseMutex();}
        }
        return 0;
    }
}
