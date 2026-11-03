#requires -Version 5.1
#requires -RunAsAdministrator
param([ValidateSet('Install','Remove')][string]$Mode='Install')
$ErrorActionPreference='Stop'
$root=Join-Path ([Environment]::GetFolderPath('ProgramFiles')) 'MandoDesktop'
$marker=Join-Path $root 'mando-install.json'
$errorFile=Join-Path $PSScriptRoot 'setup-error.txt'
$networkProgram=Join-Path $PSScriptRoot 'MandoServer.exe'
function Update-MandoFirewall([bool]$Install) {
    foreach ($entry in @(@{name='Mando-Server-TCP';protocol='TCP';port=1979},@{name='Mando-Discovery-UDP';protocol='UDP';port=1980})) {
        $existing=Get-NetFirewallRule -Name $entry.name -ErrorAction SilentlyContinue
        if ($existing) {
            $program=($existing | Get-NetFirewallApplicationFilter).Program
            if ($program -ne $networkProgram) { throw 'Existe una regla Mando de otra aplicacion; no se modificara.' }
            $existing | Remove-NetFirewallRule
        }
        if ($Install) {
            New-NetFirewallRule -Name $entry.name -DisplayName ('Mando - '+$entry.protocol) -Direction Inbound -Action Allow -Profile Private -RemoteAddress LocalSubnet -Program $networkProgram -Protocol $entry.protocol -LocalPort $entry.port | Out-Null
        }
    }
}
function Update-MandoStartup([string]$Sid,[bool]$Install) {
    if ($Sid -notmatch '^S-1-5-21-[0-9-]+$') { throw 'Cuenta de inicio automatico no valida.' }
    $hive='Registry::HKEY_USERS\'+$Sid
    if (-not (Test-Path -LiteralPath $hive)) { throw 'La cuenta de Windows debe tener la sesion iniciada para configurar Mando.' }
    $key=$hive+'\Software\Microsoft\Windows\CurrentVersion\Run'
    $expected='"'+(Join-Path $PSScriptRoot 'MandoTray.exe')+'" --background'
    $entry=Get-ItemProperty -LiteralPath $key -Name MandoServer -ErrorAction SilentlyContinue
    if ($entry -and $entry.MandoServer -ne $expected) { throw 'Existe otro inicio automatico llamado MandoServer. No se modificara.' }
    if ($Install) {
        New-Item -Path $key -Force | Out-Null
        New-ItemProperty -LiteralPath $key -Name MandoServer -Value $expected -PropertyType String -Force | Out-Null
    } elseif ($entry) {
        Remove-ItemProperty -LiteralPath $key -Name MandoServer
    }
}
try {
    if (Test-Path -LiteralPath $errorFile) { Remove-Item -LiteralPath $errorFile -Force }
    if (Test-Path -LiteralPath $root) {
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) { throw 'Existe MandoDesktop sin una instalacion reconocida. No se modificara.' }
        if ($Mode -eq 'Remove') {
            $installed=Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
            Update-MandoStartup $installed.userSid $false
        }
        & (Join-Path $PSScriptRoot 'Desinstalar-MandoCompleto.ps1')
        [GC]::Collect(); [GC]::WaitForPendingFinalizers()
        for ($attempt=0; $attempt -lt 50; $attempt++) {
            $service=Get-Service -Name MandoDesktopBroker -ErrorAction SilentlyContinue
            if (-not $service) { break }
            $service.Dispose(); Start-Sleep -Milliseconds 200
        }
    }
    if ($Mode -eq 'Remove') { Update-MandoFirewall $false }
    if ($Mode -eq 'Install') {
        # UAC may use another administrative account: authorize the console user instead.
        $account=(Get-CimInstance Win32_ComputerSystem).UserName
        if ($account) {
            $sid=(New-Object Security.Principal.NTAccount($account)).Translate([Security.Principal.SecurityIdentifier]).Value
        } else {
            # A noninteractive isolated runner has no console user.
            $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        }
        & (Join-Path $PSScriptRoot 'Instalar-MandoCompleto.ps1') -UserSid $sid
        if (-not (Test-Path -LiteralPath $marker)) { throw 'No se ha completado el componente de escritorio.' }
        Update-MandoFirewall $true
        Update-MandoStartup $sid $true
    }
    exit 0
} catch {
    Set-Content -LiteralPath $errorFile -Value $_.Exception.Message -Encoding UTF8
    exit 1
}
