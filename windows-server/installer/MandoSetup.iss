#ifndef PayloadDir
  #error PayloadDir is required
#endif
[Setup]
AppId={{DF9BB8D5-33B6-45C9-95D7-AC12D3485D1D}
AppName=Mando Server
AppVersion=0.5.0-alpha.12
VersionInfoVersion=0.5.0.12
AppPublisher=Mando
DefaultDirName={autopf}\Mando
DefaultGroupName=Mando
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.22000
OutputDir=..\..\dist
OutputBaseFilename=MandoSetup-0.5.0-alpha.12
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\MandoServer.exe
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
[Languages]
Name: spanish; MessagesFile: compiler:Languages\Spanish.isl
Name: english; MessagesFile: compiler:Default.isl
[Files]
Source: "{#PayloadDir}\MandoServer.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PayloadDir}\MandoTray.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PayloadDir}\Instalar-MandoCompleto.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PayloadDir}\Desinstalar-MandoCompleto.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PayloadDir}\Configure-Mando.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PayloadDir}\desktop\MandoDesktopBroker.cs"; DestDir: "{app}\desktop"; Flags: ignoreversion
Source: "{#PayloadDir}\windows-zoom\MandoTouchZoom.cs"; DestDir: "{app}\windows-zoom"; Flags: ignoreversion
Source: "{#PayloadDir}\LEEME.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PayloadDir}\licenses\*"; DestDir: "{app}\licenses"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{commonprograms}\Mando\Mando Server"; Filename: "{app}\MandoTray.exe"; WorkingDir: "{app}"
Name: "{commonprograms}\Mando\Instrucciones"; Filename: "{app}\LEEME.txt"
[Code]
function Configure(Mode: String): Boolean;
var
  ExitCode: Integer;
  ErrorText: AnsiString;
begin
  Result := Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'),
    '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' +
    ExpandConstant('{app}\Configure-Mando.ps1') + '" -Mode ' + Mode,
    ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, ExitCode);
  if Result then Result := ExitCode = 0;
  if not Result then begin
    ErrorText := '';
    LoadStringFromFile(ExpandConstant('{app}\setup-error.txt'), ErrorText);
    if ErrorText = '' then ErrorText := 'Windows no ha completado la configuracion del servicio.';
    RaiseException('Mando: ' + String(ErrorText));
  end;
end;
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then Configure('Install');
end;
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then Configure('Remove');
end;
procedure InitializeWizard();
begin
  WizardForm.FinishedLabel.Caption := 'Mando esta instalado. Abre Mando Server desde Inicio. Detecta la IP automaticamente, permanece en la bandeja y arrancara al iniciar tu sesion de Windows. Cierra Remote Mouse antes de conectar. En Android pulsa Escanear QR del PC y luego Conectar. El PC quedara recordado.';
end;
