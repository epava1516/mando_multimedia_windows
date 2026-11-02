$ErrorActionPreference = 'Stop'
$helperDir = Join-Path $env:LOCALAPPDATA 'MandoLock'
$helperExe = Join-Path $helperDir 'MandoLock.exe'
$marker = Join-Path $helperDir 'mando-lock-source.sha256'
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'Mando - Bloquear PC.lnk'
if (-not (Test-Path -LiteralPath $marker)) { throw 'No se ha encontrado la marca de instalación de MandoLock.' }
$shell = New-Object -ComObject WScript.Shell
if (Test-Path -LiteralPath $shortcutPath) {
    $link = $shell.CreateShortcut($shortcutPath)
    if ($link.TargetPath -eq $helperExe) { Remove-Item -LiteralPath $shortcutPath }
}
# Eliminar únicamente los archivos concretos creados por el instalador.
foreach ($file in @($helperExe, $marker)) {
    if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
}
if ((Get-ChildItem -LiteralPath $helperDir -Force | Measure-Object).Count -eq 0) {
    Remove-Item -LiteralPath $helperDir
}
Write-Host 'Ajuste eliminado. Desactiva también la configuración de bloqueo en Mando.'
