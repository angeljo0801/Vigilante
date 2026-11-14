# Ejecutar en PowerShell. Requiere Java JDK (keytool) y GitHub CLI (gh).
# Guarda la clave fuera del repositorio. No imprime contraseñas.
param([string]$Keystore = "", [string]$Alias = "vigilante")
$ErrorActionPreference = "Stop"
$repo = "angeljo0801/Vigilante"
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) { throw "Instala un JDK de Java y agrega keytool al PATH." }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "Instala GitHub CLI: winget install --id GitHub.cli" }
gh auth status
if ($LASTEXITCODE -ne 0) { gh auth login; if ($LASTEXITCODE -ne 0) { throw "No se pudo conectar GitHub." } }
function Read-PlainPassword([string]$Prompt) {
    $s = Read-Host $Prompt -AsSecureString
    $p = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($p) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p) }
}
function Set-Secret([string]$Name, [string]$Value) {
    $Value | gh secret set $Name --repo $repo
    if ($LASTEXITCODE -ne 0) { throw "No se pudo configurar $Name." }
}
if (-not $Keystore) {
    $folder = Join-Path $HOME "VigilanteSigning"
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    $Keystore = Join-Path $folder "vigilante.jks"
}
$Keystore = [System.IO.Path]::GetFullPath($Keystore)
if (Test-Path $Keystore) {
    Write-Host "Usando clave existente: $Keystore"
    $storePass = Read-PlainPassword "Contraseña del almacén"
    $keyPass = Read-PlainPassword "Contraseña de la clave"
} else {
    Write-Host "Se creará una clave NUEVA. No actualiza APKs instaladas con una firma distinta."
    if ((Read-Host "Escribe CREAR para continuar") -ne "CREAR") { throw "Cancelado." }
    $storePass = Read-PlainPassword "Elige una contraseña de al menos 12 caracteres"
    if ($storePass.Length -lt 12) { throw "Contraseña demasiado corta." }
    $confirm = Read-PlainPassword "Repite la contraseña"
    if ($storePass -cne $confirm) { throw "Las contraseñas no coinciden." }
    $keyPass = $storePass
    $env:VIG_SIGN_STORE = $storePass
    $env:VIG_SIGN_KEY = $keyPass
    try {
        keytool -genkeypair -keystore $Keystore -storetype JKS -alias $Alias -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Vigilante" -storepass:env VIG_SIGN_STORE -keypass:env VIG_SIGN_KEY
        if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el almacén." }
    } finally { Remove-Item Env:VIG_SIGN_STORE,Env:VIG_SIGN_KEY -ErrorAction SilentlyContinue }
}
$env:VIG_SIGN_STORE = $storePass
try {
    keytool -list -keystore $Keystore -alias $Alias -storepass:env VIG_SIGN_STORE
    if ($LASTEXITCODE -ne 0) { throw "No se pudo validar el almacén o alias." }
} finally { Remove-Item Env:VIG_SIGN_STORE -ErrorAction SilentlyContinue }
Set-Secret "ANDROID_KEYSTORE_BASE64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore)))
Set-Secret "ANDROID_KEYSTORE_PASSWORD" $storePass
Set-Secret "ANDROID_KEY_ALIAS" $Alias
Set-Secret "ANDROID_KEY_PASSWORD" $keyPass
Write-Host "Secretos guardados. Conserva una copia privada de $Keystore y sus contraseñas."
gh workflow run android.yml --repo $repo --ref main
if ($LASTEXITCODE -ne 0) { Write-Host "Inicia manualmente el workflow desde Actions." }
