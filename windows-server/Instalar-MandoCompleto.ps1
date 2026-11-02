#requires -Version 5.1
#requires -RunAsAdministrator
param([string]$UserSid = ([Security.Principal.WindowsIdentity]::GetCurrent().User.Value))
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -ne 'Desktop') { throw 'Usa Windows PowerShell 5.1.' }
$identity = New-Object Security.Principal.SecurityIdentifier($UserSid)
if (-not $identity.IsAccountSid() -or $UserSid -notmatch '^S-1-5-21-[0-9-]+$') { throw 'Indica el SID de la cuenta que ejecutara el servidor Mando.' }
$root = Join-Path $env:ProgramFiles 'MandoDesktop'
$marker = Join-Path $root 'mando-install.json'
$serviceName = 'MandoDesktopBroker'
if ((Test-Path -LiteralPath $root) -or (Get-Service -Name $serviceName -ErrorAction SilentlyContinue)) {
    throw 'Ya existe MandoDesktop o su servicio. Desinstala la version anterior antes de continuar.'
}
$brokerSource = Join-Path $PSScriptRoot 'desktop\MandoDesktopBroker.cs'
$zoomSource = Join-Path $PSScriptRoot 'windows-zoom\MandoTouchZoom.cs'
# Repository checkout and distributed ZIP both contain the same owned zoom source.
if (-not (Test-Path -LiteralPath $zoomSource)) { $zoomSource = Join-Path $PSScriptRoot '..\windows-zoom\MandoTouchZoom.cs' }
if (-not (Test-Path -LiteralPath $brokerSource) -or -not (Test-Path -LiteralPath $zoomSource)) { throw 'Extrae el paquete completo, incluidas desktop y windows-zoom.' }
New-Item -ItemType Directory -Path $root | Out-Null
try {
    # SYSTEM and Administrators own all executable code. Standard users can only read/execute.
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true,$false)
    $admins = New-Object Security.Principal.SecurityIdentifier('S-1-5-32-544')
    $system = New-Object Security.Principal.SecurityIdentifier('S-1-5-18')
    $users = New-Object Security.Principal.SecurityIdentifier('S-1-5-32-545')
    $acl.SetOwner($admins)
    foreach ($principal in @($system,$admins)) {
        $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($principal,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))
    }
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($users,'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow')))
    Set-Acl -LiteralPath $root -AclObject $acl
    $broker = Join-Path $root 'MandoDesktopBroker.exe'
    $zoom = Join-Path $root 'MandoTouchZoom.exe'
    Add-Type -TypeDefinition (Get-Content -LiteralPath $brokerSource -Raw -Encoding UTF8) -Language CSharp -ReferencedAssemblies 'System.dll','System.Core.dll','System.ServiceProcess.dll','System.Web.Extensions.dll' -OutputAssembly $broker -OutputType WindowsApplication
    Add-Type -TypeDefinition (Get-Content -LiteralPath $zoomSource -Raw -Encoding UTF8) -Language CSharp -OutputAssembly $zoom -OutputType WindowsApplication
    $self = Start-Process -FilePath $broker -ArgumentList '--self-test' -Wait -PassThru
    if ($self.ExitCode -ne 0) { throw 'Fallo de validacion local del componente de escritorio.' }
    Set-Content -LiteralPath (Join-Path $root 'allowed.sid') -Value $UserSid -Encoding ASCII
    @{version=1;userSid=$UserSid;brokerSha256=(Get-FileHash -LiteralPath $broker -Algorithm SHA256).Hash;zoomSha256=(Get-FileHash -LiteralPath $zoom -Algorithm SHA256).Hash} | ConvertTo-Json | Set-Content -LiteralPath $marker -Encoding UTF8
    New-Service -Name $serviceName -BinaryPathName ('"'+$broker+'"') -StartupType Automatic -DisplayName 'Mando - Escritorio bloqueado' -Description 'Broker local restringido de entrada Mando. No abre puertos de red.' | Out-Null
    & sc.exe failure $serviceName reset= 86400 actions= restart/5000/restart/15000/restart/60000 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se ha podido configurar la recuperacion del servicio.' }
    Start-Service -Name $serviceName
    Write-Host 'Instalado: servicio local para bloqueo y zoom integrado. Inicia el servidor de red desde tu cuenta normal.'
    Write-Host "Cuenta autorizada: $UserSid"
} catch {
    $failure = $_
    $existing = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if ($existing) { Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue; & sc.exe delete $serviceName | Out-Null }
    foreach ($name in @('MandoDesktopBroker.exe','MandoTouchZoom.exe','allowed.sid','mando-install.json')) {
        $path = Join-Path $root $name
        if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force }
    }
    if ((Test-Path -LiteralPath $root) -and @(Get-ChildItem -LiteralPath $root -Force).Count -eq 0) { Remove-Item -LiteralPath $root }
    throw $failure
}
