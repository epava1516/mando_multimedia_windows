#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$directory = Join-Path $env:LOCALAPPDATA 'MandoTouchZoom'
if (-not (Test-Path -LiteralPath $directory)) { Write-Host 'No esta instalado.'; return }
$item = Get-Item -LiteralPath $directory -Force
if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Ruta de instalacion no reconocida.' }
$exe = Join-Path $directory 'MandoTouchZoom.exe'
$marker = Join-Path $directory 'mando-touch-zoom.source.sha256'
$binaryMarker = Join-Path $directory 'mando-touch-zoom.exe.sha256'
if (-not (Test-Path -LiteralPath $marker -PathType Leaf) -or -not (Test-Path -LiteralPath $binaryMarker -PathType Leaf)) { throw 'Faltan marcadores de propiedad; no se borrara la carpeta.' }
if (Test-Path -LiteralPath $exe) {
    if ((Get-Content -LiteralPath $binaryMarker -Raw).Trim() -ne (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash) { throw 'Ejecutable modificado; no se borrara automaticamente.' }
}
$shell = New-Object -ComObject WScript.Shell
$programs = [Environment]::GetFolderPath('Programs')
foreach ($entry in @(@{ Name = 'Mando - Zoom visual alejar.lnk'; Argument = 'out' }, @{ Name = 'Mando - Zoom visual acercar.lnk'; Argument = 'in' })) {
    $path = Join-Path $programs $entry.Name
    if (Test-Path -LiteralPath $path) {
        $shortcut = $shell.CreateShortcut($path)
        if ($shortcut.TargetPath -eq $exe -and $shortcut.Arguments -eq $entry.Argument) { Remove-Item -LiteralPath $path }
    }
}
foreach ($path in @($exe, $marker, $binaryMarker)) { if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path } }
if (@(Get-ChildItem -LiteralPath $directory -Force).Count -eq 0) { Remove-Item -LiteralPath $directory }
Write-Host 'Componente eliminado; cualquier archivo ajeno se conserva.'
