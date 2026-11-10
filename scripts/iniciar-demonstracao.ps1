[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Server', 'Client')]
    [string] $Role
)

$ErrorActionPreference = 'Stop'

function Resolve-JdkTool {
    param([Parameter(Mandatory)][string] $Name)
    if ($env:JAVA_HOME) {
        $candidate = Join-Path -Path (Join-Path -Path $env:JAVA_HOME -ChildPath 'bin') -ChildPath $Name
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    $command = Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "JDK não encontrado: $Name. Instale um JDK 17 ou superior e configure JAVA_HOME."
}

function Convert-SecureStringToText {
    param([Parameter(Mandatory)][Security.SecureString] $SecureString)
    $pointer = [IntPtr]::Zero
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
    }
}

function Ensure-Build {
    param([Parameter(Mandatory)][string] $MavenWrapper)
    $classes = Join-Path $projectRoot 'target\classes'
    $classpathFile = Join-Path $projectRoot 'target\classpath.txt'
    if (-not (Test-Path -LiteralPath $classes -PathType Container)) {
        & $MavenWrapper 'compile' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'A compilação Maven falhou.' }
    }
    if (-not (Test-Path -LiteralPath $classpathFile -PathType Leaf)) {
        & $MavenWrapper 'dependency:build-classpath' '-Dmdep.outputFile=target\classpath.txt' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'A preparação do classpath Maven falhou.' }
    }
    return $classpathFile
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaExe = Resolve-JdkTool -Name 'java.exe'
[void] (Resolve-JdkTool -Name 'keytool.exe')
$originalErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $versionText = (& $javaExe '-version' 2>&1 | Out-String)
    $javaExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $originalErrorActionPreference
}
if ($javaExitCode -ne 0) { throw 'A verificação da versão do Java falhou.' }
if ($versionText -notmatch 'version "(?<major>\d+)') { throw 'Não foi possível identificar a versão do Java.' }
if ([int] $Matches.major -lt 17) { throw 'É necessário Java 17 ou superior.' }

$tlsDirectory = Join-Path $projectRoot 'tls-local\demo'
$passwordFile = Join-Path $tlsDirectory 'senhas.dpapi.json'
$requiredTlsFiles = @(
    (Join-Path $tlsDirectory 'servidor.p12'),
    (Join-Path $tlsDirectory 'cliente.p12'),
    (Join-Path $tlsDirectory 'truststore.p12'),
    (Join-Path $tlsDirectory '.prepared'),
    $passwordFile
)
if ($requiredTlsFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) }) {
    throw 'TLS de demonstração não está preparado. Execute preparar-tls-demo.ps1 primeiro.'
}

$mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw 'Maven Wrapper não encontrado na raiz do projeto.'
}
$classpathFile = Ensure-Build -MavenWrapper $mavenWrapper
$dependencyClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($dependencyClasspath)) { throw 'target\classpath.txt está vazio.' }
$chatClassPath = (Join-Path $projectRoot 'target\classes') + ';' + $dependencyClasspath

$tlsJson = Get-Content -LiteralPath $passwordFile -Raw | ConvertFrom-Json
$secureKeystorePassword = ConvertTo-SecureString ([string] $tlsJson.servidor_keystore)
$secureClientPassword = ConvertTo-SecureString ([string] $tlsJson.cliente_keystore)
$secureTruststorePassword = ConvertTo-SecureString ([string] $tlsJson.truststore)
$previousTls = @{}
foreach ($name in 'CHAT_TLS_KEYSTORE', 'CHAT_TLS_KEYSTORE_PASSWORD',
        'CHAT_TLS_TRUSTSTORE', 'CHAT_TLS_TRUSTSTORE_PASSWORD') {
    $previousTls[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    if ($Role -eq 'Server') {
        $keystorePath = Join-Path $tlsDirectory 'servidor.p12'
        $keystorePassword = Convert-SecureStringToText -SecureString $secureKeystorePassword
    } else {
        $keystorePath = Join-Path $tlsDirectory 'cliente.p12'
        $keystorePassword = Convert-SecureStringToText -SecureString $secureClientPassword
    }
    $truststorePath = Join-Path $tlsDirectory 'truststore.p12'
    $truststorePassword = Convert-SecureStringToText -SecureString $secureTruststorePassword
    $env:CHAT_TLS_KEYSTORE = $keystorePath
    $env:CHAT_TLS_KEYSTORE_PASSWORD = $keystorePassword
    $env:CHAT_TLS_TRUSTSTORE = $truststorePath
    $env:CHAT_TLS_TRUSTSTORE_PASSWORD = $truststorePassword

    Push-Location $projectRoot
    try {
        if ($Role -eq 'Server') {
            & $javaExe '-cp' $chatClassPath 'br.edu.chat.server.ServidorApp'
        } else {
            & $javaExe '-cp' $chatClassPath 'br.edu.chat.client.ClienteChatApp'
        }
    } finally {
        Pop-Location
    }
}
finally {
    foreach ($name in 'CHAT_TLS_KEYSTORE', 'CHAT_TLS_KEYSTORE_PASSWORD',
            'CHAT_TLS_TRUSTSTORE', 'CHAT_TLS_TRUSTSTORE_PASSWORD') {
        if ($null -eq $previousTls[$name]) {
            Remove-Item ("Env:" + $name) -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previousTls[$name], 'Process')
        }
    }
    $secureKeystorePassword.Dispose()
    $secureClientPassword.Dispose()
    $secureTruststorePassword.Dispose()
    $keystorePassword = $null
    $truststorePassword = $null
    $tlsJson = $null
}
