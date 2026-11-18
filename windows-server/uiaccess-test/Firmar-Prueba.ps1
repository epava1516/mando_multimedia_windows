#requires -Version 5.1
param([Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{40}$')][string]$Thumbprint)
$ErrorActionPreference = 'Stop'
$cert = Get-Item -LiteralPath "Cert:\CurrentUser\My\$Thumbprint"
if ($cert.Subject -ne 'CN=Mando UIAccess Prueba Local' -or -not $cert.HasPrivateKey -or $cert.NotAfter -le (Get-Date)) {
    throw 'Selecciona el certificado local de prueba vigente.'
}
$csc = Join-Path ([Runtime.InteropServices.RuntimeEnvironment]::GetRuntimeDirectory()) 'csc.exe'
$out = Join-Path $PSScriptRoot 'MandoUiAccessProbe.exe'
if (Test-Path -LiteralPath $out) { throw 'Retira el EXE anterior antes de compilar de nuevo.' }
& $csc /nologo /target:exe "/out:$out" "/win32manifest:$PSScriptRoot\MandoUiAccessProbe.manifest" "$PSScriptRoot\Probe.cs"
if ($LASTEXITCODE -ne 0) { throw 'Fallo al compilar la prueba.' }
$signature = Set-AuthenticodeSignature -LiteralPath $out -Certificate $cert -HashAlgorithm SHA256
if ($signature.Status -notin 'Valid','NotTrusted','UnknownError' -or $signature.SignerCertificate.Thumbprint -ne $cert.Thumbprint) {
    throw "No se pudo comprobar la firma: $($signature.Status)"
}
Export-Certificate -Cert $cert -FilePath (Join-Path $PSScriptRoot 'MandoUiAccessProbe.cer') | Out-Null
Write-Host "Firma aplicada. Estado de confianza: $($signature.Status)"
