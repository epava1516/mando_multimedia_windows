#requires -Version 5.1
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -ne 'Desktop') { throw 'Abre este archivo con Windows PowerShell 5.1.' }
$sourcePath = Join-Path $PSScriptRoot 'MandoTouchZoom.cs'
$source = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
$directory = Join-Path $env:LOCALAPPDATA 'MandoTouchZoom'
$exe = Join-Path $directory 'MandoTouchZoom.exe'
$marker = Join-Path $directory 'mando-touch-zoom.source.sha256'
$binaryMarker = Join-Path $directory 'mando-touch-zoom.exe.sha256'
if (Test-Path -LiteralPath $directory) {
    $item = Get-Item -LiteralPath $directory -Force
    if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'La ruta de instalacion no es una carpeta normal.' }
    if (-not (Test-Path -LiteralPath $marker -PathType Leaf) -or (Get-Content -LiteralPath $marker -Raw).Trim() -ne $sourceHash) { throw 'Carpeta existente no reconocida o version distinta. Desinstala la version anterior primero.' }
    if (Test-Path -LiteralPath $exe) {
        if (-not (Test-Path -LiteralPath $binaryMarker -PathType Leaf) -or (Get-Content -LiteralPath $binaryMarker -Raw).Trim() -ne (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash) { throw 'El ejecutable existente no coincide con su marcador.' }
    }
}
$shell = New-Object -ComObject WScript.Shell
$programs = [Environment]::GetFolderPath('Programs')
$entries = @(
    @{ Name = 'Mando - Zoom visual alejar.lnk'; Argument = 'out'; Hotkey = 'CTRL+ALT+F11' },
    @{ Name = 'Mando - Zoom visual acercar.lnk'; Argument = 'in'; Hotkey = 'CTRL+ALT+F12' }
)
foreach ($entry in $entries) {
    $entry.Path = Join-Path $programs $entry.Name
    if (Test-Path -LiteralPath $entry.Path) {
        $shortcut = $shell.CreateShortcut($entry.Path)
        if ($shortcut.TargetPath -ne $exe -or $shortcut.Arguments -ne $entry.Argument) { throw "Acceso directo existente ajeno: $($entry.Path)" }
    }
}
$roots = @('Desktop', 'Programs', 'CommonDesktopDirectory', 'CommonPrograms') | ForEach-Object { [Environment]::GetFolderPath($_) } | Where-Object { $_ } | Select-Object -Unique
foreach ($root in $roots) {
    foreach ($link in @(Get-ChildItem -LiteralPath $root -Filter '*.lnk' -File -Recurse -ErrorAction SilentlyContinue)) {
        $shortcut = $shell.CreateShortcut($link.FullName)
        foreach ($entry in $entries) {
            if ($shortcut.Hotkey -eq $entry.Hotkey -and $link.FullName -ne $entry.Path) { throw "Atajo ocupado ($($entry.Hotkey)): $($link.FullName)" }
        }
    }
}
if (-not (Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Path $directory | Out-Null
    Set-Content -LiteralPath $marker -Value $sourceHash -Encoding ASCII
}
$temp = Join-Path $directory ([Guid]::NewGuid().ToString('N') + '.exe')
try {
    if (-not (Test-Path -LiteralPath $exe)) {
        Add-Type -TypeDefinition $source -Language CSharp -OutputAssembly $temp -OutputType WindowsApplication
        Move-Item -LiteralPath $temp -Destination $exe
        Set-Content -LiteralPath $marker -Value $sourceHash -Encoding ASCII
        Set-Content -LiteralPath $binaryMarker -Value (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash -Encoding ASCII
    }
    foreach ($entry in $entries) {
        $shortcut = $shell.CreateShortcut($entry.Path)
        $shortcut.TargetPath = $exe
        $shortcut.Arguments = $entry.Argument
        $shortcut.WorkingDirectory = $directory
        $shortcut.Hotkey = $entry.Hotkey
        $shortcut.Description = 'Gesto tactil visual acotado para Firefox (Mando)'
        $shortcut.Save()
    }
} finally {
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp }
    # Una compilacion fallida no debe dejar una carpeta vacia que impida reintentar.
    if ($createdDirectory -and (Test-Path -LiteralPath $directory) -and @(Get-ChildItem -LiteralPath $directory -Force).Count -eq 0) {
        Remove-Item -LiteralPath $directory
    }
}
Write-Host 'Instalado. Prueba fisicamente Ctrl+Alt+F12 y Ctrl+Alt+F11 con Firefox enfocado y el puntero sobre la pagina.'
