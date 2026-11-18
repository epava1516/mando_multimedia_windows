#requires -Version 5.1
#requires -RunAsAdministrator
param([switch]$Retirar)
$ErrorActionPreference = 'Stop'

$programFiles = [Environment]::GetFolderPath([Environment+SpecialFolder]::ProgramFiles)
$destino = Join-Path $programFiles 'MandoUiAccessProbe'
$exeInstalado = Join-Path $destino 'MandoUiAccessProbe.exe'
$registro = Join-Path $destino 'certificado.txt'
$root = 'Cert:\LocalMachine\Root'
$subject = 'CN=Mando UIAccess Prueba Local'

if ($Retirar) {
    if (-not (Test-Path -LiteralPath $registro -PathType Leaf)) { throw 'No se encontro una instalacion de prueba registrada.' }
    $huella = (Get-Content -LiteralPath $registro -Raw).Trim()
    if ($huella -ne 'PREEXISTENTE') {
        if ($huella -notmatch '^[A-Fa-f0-9]{40}$') { throw 'Registro de certificado invalido.' }
        $certificado = Join-Path $root $huella
        if (Test-Path -LiteralPath $certificado) {
            if ((Get-Item -LiteralPath $certificado).Subject -ne $subject) { throw 'Certificado inesperado.' }
            Remove-Item -LiteralPath $certificado
        }
    }
    if (Test-Path -LiteralPath $exeInstalado) { Remove-Item -LiteralPath $exeInstalado }
    Remove-Item -LiteralPath $registro
    if (-not (Get-ChildItem -LiteralPath $destino -Force)) { Remove-Item -LiteralPath $destino }
    Write-Host 'Prueba retirada.'
    return
}

$exe = Join-Path $PSScriptRoot 'MandoUiAccessProbe.exe'
$cer = Join-Path $PSScriptRoot 'MandoUiAccessProbe.cer'
if (-not ((Test-Path -LiteralPath $exe -PathType Leaf) -and (Test-Path -LiteralPath $cer -PathType Leaf))) {
    throw 'Coloca este script al lado del EXE firmado y el CER que genero Firmar-Prueba.ps1.'
}
$publico = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($cer)
$firma = Get-AuthenticodeSignature -LiteralPath $exe
$local = Get-Item -LiteralPath ('Cert:\CurrentUser\My\' + $publico.Thumbprint) -ErrorAction SilentlyContinue
if ($publico.Subject -ne $subject -or -not $local -or -not $local.HasPrivateKey -or
    $publico.NotAfter -le (Get-Date) -or $firma.SignerCertificate.Thumbprint -ne $publico.Thumbprint) {
    throw 'El EXE y el CER deben corresponder al certificado de prueba vigente de esta cuenta.'
}

$entrada = Join-Path $root $publico.Thumbprint
$agregado = $false
$creado = $false
try {
    if (-not (Test-Path -LiteralPath $entrada)) {
        Import-Certificate -FilePath $cer -CertStoreLocation $root | Out-Null
        $agregado = $true
    }
    $firma = Get-AuthenticodeSignature -LiteralPath $exe
    if ($firma.Status -ne 'Valid' -or $firma.SignerCertificate.Thumbprint -ne $publico.Thumbprint) {
        throw "Firma no valida: $($firma.StatusMessage)"
    }
    $carpeta = New-Item -ItemType Directory -Path $destino
    $creado = $true
    Copy-Item -LiteralPath $exe -Destination $exeInstalado
    $instalada = Get-AuthenticodeSignature -LiteralPath $exeInstalado
    if ($instalada.Status -ne 'Valid' -or $instalada.SignerCertificate.Thumbprint -ne $publico.Thumbprint) {
        throw 'No se pudo validar la copia instalada.'
    }
    $registroHuella = if ($agregado) { $publico.Thumbprint } else { 'PREEXISTENTE' }
    Set-Content -LiteralPath $registro -Value $registroHuella -Encoding ASCII
} catch {
    if ($agregado) { Remove-Item -LiteralPath $entrada }
    if ($creado) {
        foreach ($archivo in @($exeInstalado, $registro)) {
            if (Test-Path -LiteralPath $archivo) { Remove-Item -LiteralPath $archivo }
        }
        Remove-Item -LiteralPath $destino
    }
    throw
}
Write-Host "Instalado en $exeInstalado"
Write-Host 'Abre el EXE con tu cuenta normal. Para retirar la prueba, ejecuta este script como administrador con -Retirar.'
