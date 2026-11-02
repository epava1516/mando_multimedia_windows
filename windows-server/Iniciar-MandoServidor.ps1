#requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$python = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    & py -3.11 -m venv (Join-Path $PSScriptRoot '.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Instala Python 3.11 para Windows con el lanzador py.' }
}
& $python -c "import importlib.util,sys; s=importlib.util.find_spec('cryptography'); sys.exit(0 if s and __import__('cryptography').__version__ == '50.0.2' else 1)"
if ($LASTEXITCODE -ne 0) {
    & $python -m pip install --disable-pip-version-check -r (Join-Path $PSScriptRoot 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { throw 'No se ha podido instalar la dependencia del servidor.' }
}
$address = Read-Host 'IPv4 Wi-Fi del PC (consulta ipconfig)'
[Net.IPAddress]$parsed = $null
if (-not [Net.IPAddress]::TryParse($address, [ref]$parsed) -or $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
    throw 'Introduce una IPv4 valida.'
}
& $python (Join-Path $PSScriptRoot 'server.py') --host ($parsed.ToString())
if ($LASTEXITCODE -ne 0) { throw 'El servidor se ha cerrado con un error.' }
