#Requires -Version 5.1
<#
.SYNOPSIS
  Diagnostico seguro do Free My TV em TCL Android TV.
.DESCRIPTION
  Nao modifica o launcher padrao, nao desativa pacotes nem limpa dados.
  Pode instalar um APK de homologacao e abrir sua HomeActivity isoladamente.
  Logs podem conter informacoes de outros apps: revise antes de compartilhar.
.EXAMPLE
  .\tcl-diagnostico.ps1 -ApkPath ".\FreeMyTV-Fase-7.apk"
.EXAMPLE
  .\tcl-diagnostico.ps1 -NoLaunch
#>
[CmdletBinding()]
param(
    [string]$ApkPath = "",
    [string]$PackageName = "io.freemytv.homep7test",
    [string]$OutputDirectory = ".",
    [ValidateRange(0,120)][int]$ObservationSeconds = 8,
    [switch]$NoLaunch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Run-Adb {
    param([string[]]$Arguments)
    $output = & $script:adbPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("ADB falhou: adb {0} : {1}" -f ($Arguments -join ' '), ($output | Out-String))
    }
    return $output
}
function Save-Lines {
    param([string]$Path,[object]$Lines)
    [System.IO.File]::WriteAllText($Path,($Lines | Out-String),[System.Text.Encoding]::UTF8)
}

try {
    $script:adbPath = (Get-Command "adb" -ErrorAction Stop).Source
} catch {
    throw "ADB nao encontrado. Instale Android SDK Platform-Tools e inclua adb.exe no PATH."
}

$devices = (& $script:adbPath devices -l | Out-String)
if ($LASTEXITCODE -ne 0 -or $devices -notmatch '(?m)^\S+\s+device(?:\s|$)') {
    throw "Nenhuma TV autorizada. Use adb connect IP_DA_TV:5555 e confirme a autorizacao na TCL."
}
if (([regex]::Matches($devices,'(?m)^\S+\s+device(?:\s|$)')).Count -gt 1) {
    throw "Ha mais de um dispositivo conectado. Desconecte os extras para evitar testar a TV errada."
}
if ($PackageName -ne "io.freemytv.homep7test") {
    throw "Este diagnostico aceita apenas o pacote de testes da Fase 7."
}
if (-not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}
$outDir = (Resolve-Path -LiteralPath $OutputDirectory).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$prefix = Join-Path $outDir "tcl-freemytv-fase7-$stamp"

if ($ApkPath -ne "") {
    if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
        throw "APK nao encontrado: $ApkPath"
    }
    Write-Host "Instalando teste via adb install -r, sem desinstalar as versoes anteriores."
    $install = & $script:adbPath install -r $ApkPath 2>&1
    Save-Lines -Path "$($prefix)-instalacao.txt" -Lines $install
    if ($LASTEXITCODE -ne 0 -or ($install | Out-String) -notmatch "Success") {
        throw "Instalacao falhou. NAO desinstale o launcher atual. Verifique a mensagem e a assinatura do APK."
    }
}

$installed = Run-Adb -Arguments @("shell","pm","path",$PackageName)
if (($installed | Out-String) -notmatch 'package:') {
    throw "Pacote de teste nao instalado: $PackageName"
}

$model = Run-Adb -Arguments @("shell","getprop","ro.product.model")
$brand = Run-Adb -Arguments @("shell","getprop","ro.product.brand")
$android = Run-Adb -Arguments @("shell","getprop","ro.build.version.release")
$sdk = Run-Adb -Arguments @("shell","getprop","ro.build.version.sdk")
$deviceInfo = @(
    "Data do teste: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "Marca: $(($brand | Out-String).Trim())",
    "Modelo: $(($model | Out-String).Trim())",
    "Android: $(($android | Out-String).Trim())",
    "SDK: $(($sdk | Out-String).Trim())",
    "Pacote: $PackageName",
    "Launch manual: $(-not $NoLaunch)"
)
Save-Lines -Path "$($prefix)-dispositivo.txt" -Lines $deviceInfo
Write-Host "TV: $(($model | Out-String).Trim()) Android $(($android | Out-String).Trim())"

if (-not $NoLaunch) {
    Write-Host "Abrindo somente a HomeActivity de teste. O launcher padrao NAO sera alterado."
    $start = Run-Adb -Arguments @("shell","am","start","-W","-n",
        "$PackageName/io.freemytv.HomeActivity")
    Save-Lines -Path "$($prefix)-abertura.txt" -Lines $start
    if ($ObservationSeconds -gt 0) { Start-Sleep -Seconds $ObservationSeconds }
}

# Diagnosticos read-only. Nao limpar logcat.
$windows = Run-Adb -Arguments @("shell","dumpsys","window","windows")
$focus = $windows | Select-String -Pattern "mCurrentFocus|mFocusedApp|mInputMethodTarget"
Save-Lines -Path "$($prefix)-foco.txt" -Lines $focus
$activity = Run-Adb -Arguments @("shell","dumpsys","activity","activities")
$activityFocus = $activity | Select-String -Pattern "mResumedActivity|topResumedActivity|mTopResumedActivity"
Save-Lines -Path "$($prefix)-atividade.txt" -Lines $activityFocus

# Pode conter erros de outros aplicativos instalados.
$log = Run-Adb -Arguments @("logcat","-d","-t","1600","-v","threadtime",
    "-s","AndroidRuntime:E","FreeMyTV:E","ActivityTaskManager:W")
Save-Lines -Path "$($prefix)-erros.txt" -Lines $log

Write-Host "Diagnostico concluido. Arquivos com prefixo: $prefix"
Write-Host "Revise os logs antes de compartilhar. O launcher padrao nao foi alterado."
