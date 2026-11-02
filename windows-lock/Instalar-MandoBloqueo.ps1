# Ejecutar una vez con Windows PowerShell 5.1, en la sesión del usuario.
# No bloquea el equipo durante la instalación. No requiere administrador.
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -ne 'Desktop') {
    throw 'Abre Windows PowerShell 5.1 para ejecutar este instalador.'
}
$helperDir = Join-Path $env:LOCALAPPDATA 'MandoLock'
$helperExe = Join-Path $helperDir 'MandoLock.exe'
$marker = Join-Path $helperDir 'mando-lock-source.sha256'
$programs = [Environment]::GetFolderPath('Programs')
$shortcutPath = Join-Path $programs 'Mando - Bloquear PC.lnk'
$hotkey = 'CTRL+ALT+L'
$source = @'
using System;
using System.Runtime.InteropServices;
internal static class MandoLock {
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool LockWorkStation();
    [STAThread]
    private static int Main() {
        if (LockWorkStation()) return 0;
        int error = Marshal.GetLastWin32Error();
        return error == 0 ? 1 : error;
    }
}
'@
$sha = [Security.Cryptography.SHA256]::Create()
try { $sourceHash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($source))).Replace('-', '') }
finally { $sha.Dispose() }
if (Test-Path -LiteralPath $helperDir) {
    if (-not (Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw).Trim() -ne $sourceHash) {
        throw 'Ya existe una carpeta MandoLock ajena a este instalador. No se ha modificado.'
    }
}
$shell = New-Object -ComObject WScript.Shell
if (Test-Path -LiteralPath $shortcutPath) {
    $existing = $shell.CreateShortcut($shortcutPath)
    if ($existing.TargetPath -ne $helperExe) {
        throw 'Ya existe un acceso directo con ese nombre y otro destino. No se ha modificado.'
    }
}
# No sustituir atajos que ya utilice otra aplicación.
$roots = @([Environment]::GetFolderPath('Desktop'), $programs,
    [Environment]::GetFolderPath('CommonDesktopDirectory'), [Environment]::GetFolderPath('CommonPrograms'))
foreach ($folder in ($roots | Select-Object -Unique)) {
    if (-not $folder -or -not (Test-Path -LiteralPath $folder)) { continue }
    foreach ($file in (Get-ChildItem -LiteralPath $folder -Filter '*.lnk' -Recurse -ErrorAction SilentlyContinue)) {
        try { $link = $shell.CreateShortcut($file.FullName) } catch { continue }
        if ($link.Hotkey -eq $hotkey -and $link.TargetPath -ne $helperExe) {
            throw "Ctrl+Alt+L ya está asignado a '$($file.Name)'. No se ha cambiado ese atajo."
        }
    }
}
New-Item -ItemType Directory -Path $helperDir -Force | Out-Null
Set-Content -LiteralPath $marker -Value $sourceHash -Encoding Ascii
if (-not (Test-Path -LiteralPath $helperExe)) {
    $temporaryExe = Join-Path $helperDir (([Guid]::NewGuid().ToString()) + '.exe')
    try {
        Add-Type -TypeDefinition $source -Language CSharp -OutputAssembly $temporaryExe -OutputType WindowsApplication
        Move-Item -LiteralPath $temporaryExe -Destination $helperExe
    } finally {
        if (Test-Path -LiteralPath $temporaryExe) { Remove-Item -LiteralPath $temporaryExe }
    }
}
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $helperExe
$shortcut.WorkingDirectory = $helperDir
$shortcut.Description = 'Mando: bloquear la sesión de Windows sin cerrar aplicaciones'
$shortcut.Hotkey = $hotkey
$shortcut.Save()
Write-Host 'Instalado. Ctrl+Alt+L invoca la API de bloqueo de Windows.'
Write-Host 'En Mando pulsa el candado y confirma que has instalado este ajuste.'
Write-Host 'Puedes retirarlo ejecutando Desinstalar-MandoBloqueo.ps1.'
