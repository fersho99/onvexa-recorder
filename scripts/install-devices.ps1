param(
    [Parameter(Mandatory=$true)][string]$PhoneSerial,
    [string]$WatchSerial,
    [string]$AdbPath
)
$ErrorActionPreference='Stop'
if (-not $AdbPath) {
    $sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
    $AdbPath=Join-Path $sdk 'platform-tools\adb.exe'
}
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'No se encontro adb. Proporciona -AdbPath.' }
$root=Split-Path -Parent $PSScriptRoot
function Install-Onvexa([string]$Serial,[string]$Module,[bool]$ExpectWatch) {
    $kind=(& $AdbPath -s $Serial shell getprop ro.build.characteristics) -join ''
    if ($LASTEXITCODE -ne 0) { throw 'Dispositivo no conectado o no autorizado en ADB.' }
    if (($kind -match 'watch') -ne $ExpectWatch) { throw 'El tipo del dispositivo no corresponde al APK seleccionado.' }
    $apk=Join-Path $root "$Module\build\outputs\apk\debug\$Module-debug.apk"
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Compila los APK debug antes de instalar.' }
    & $AdbPath -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar. No se desinstalo la app ni se borraron datos.' }
    & $AdbPath -s $Serial shell monkey -p com.onvexa.recorder -c android.intent.category.LAUNCHER 1
    if ($LASTEXITCODE -ne 0) { throw 'Instalada, pero no se pudo abrir automaticamente.' }
}
Install-Onvexa $PhoneSerial 'app' $false
if ($WatchSerial) { Install-Onvexa $WatchSerial 'wear' $true }
