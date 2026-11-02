#requires -Version 5.1
#requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:ProgramFiles 'MandoDesktop'
$marker = Join-Path $root 'mando-install.json'
if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) { throw 'No se reconoce una instalacion propia de MandoDesktop.' }
$item = Get-Item -LiteralPath $root -Force
if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'La carpeta no debe ser un enlace.' }
$record = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
if ($record.version -ne 1) { throw 'Marca de instalacion incompatible.' }
foreach ($entry in @(@{name='MandoDesktopBroker.exe';hash=$record.brokerSha256},@{name='MandoTouchZoom.exe';hash=$record.zoomSha256})) {
    $path = Join-Path $root $entry.name
    if ((Test-Path -LiteralPath $path) -and (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.hash) { throw "Archivo modificado: $($entry.name). No se elimina automaticamente." }
}
$registered = Get-CimInstance Win32_Service -Filter "Name='MandoDesktopBroker'"
$expected = '"'+(Join-Path $root 'MandoDesktopBroker.exe')+'"'
if ($registered -and $registered.PathName -ne $expected) { throw 'El servicio con ese nombre tiene otro ejecutable.' }
if ($registered) {
    Stop-Service MandoDesktopBroker -Force
    & sc.exe delete MandoDesktopBroker | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se ha podido quitar el servicio.' }
}
foreach ($name in @('MandoDesktopBroker.exe','MandoTouchZoom.exe','allowed.sid','mando-install.json')) {
    $path = Join-Path $root $name
    if (Test-Path -LiteralPath $path -PathType Leaf) { Remove-Item -LiteralPath $path -Force }
}
if (@(Get-ChildItem -LiteralPath $root -Force).Count -eq 0) { Remove-Item -LiteralPath $root }
Write-Host 'Componente local eliminado. Se conserva el servidor de red, su identidad TLS y archivos ajenos.'
