#requires -Version 5.1
#requires -RunAsAdministrator
param([ValidateSet('Install','Remove')][string]$Mode='Install')
$ErrorActionPreference='Stop'
$root=Join-Path ([Environment]::GetFolderPath('ProgramFiles')) 'MandoDesktop'
$marker=Join-Path $root 'mando-install.json'
$errorFile=Join-Path $PSScriptRoot 'setup-error.txt'
try {
    if (Test-Path -LiteralPath $errorFile) { Remove-Item -LiteralPath $errorFile -Force }
    if (Test-Path -LiteralPath $root) {
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) { throw 'Existe MandoDesktop sin una instalacion reconocida. No se modificara.' }
        & (Join-Path $PSScriptRoot 'Desinstalar-MandoCompleto.ps1')
        [GC]::Collect(); [GC]::WaitForPendingFinalizers()
        for ($attempt=0; $attempt -lt 50; $attempt++) {
            $service=Get-Service -Name MandoDesktopBroker -ErrorAction SilentlyContinue
            if (-not $service) { break }
            $service.Dispose(); Start-Sleep -Milliseconds 200
        }
    }
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
    }
    exit 0
} catch {
    Set-Content -LiteralPath $errorFile -Value $_.Exception.Message -Encoding UTF8
    exit 1
}
