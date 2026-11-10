[CmdletBinding()]
param()

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

function Invoke-Keytool {
    param(
        [Parameter(Mandatory)][string] $Keytool,
        [Parameter(Mandatory)][string[]] $Arguments
    )
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Keytool @Arguments 2>&1 | Out-Null
        $keytoolExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }

    if ($keytoolExitCode -ne 0) {
        throw 'keytool falhou; nenhum conjunto incompleto será considerado válido.'
    }
}

function New-DemoPassword {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] 32
    try {
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $rng.GetBytes($bytes)
        }
        finally {
            $rng.Dispose()
        }
        $builder = [System.Text.StringBuilder]::new()
        foreach ($byte in $bytes) { [void] $builder.Append($alphabet[$byte % $alphabet.Length]) }
        return $builder.ToString()
    } finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Protect-DemoPassword {
    param([Parameter(Mandatory)][string] $Password)
    $secure = ConvertTo-SecureString -String $Password -AsPlainText -Force
    return ConvertFrom-SecureString -SecureString $secure
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tlsRoot = Join-Path $projectRoot 'tls-local'
$finalDirectory = Join-Path $tlsRoot 'demo'
$stagingDirectory = Join-Path $tlsRoot ('.demo-staging-' + [Guid]::NewGuid().ToString('N'))
$lastStage = 'INITIALIZATION'

try {
    $lastStage = 'JAVA_RESOLUTION'
    $keytool = Resolve-JdkTool -Name 'keytool.exe'
    $java = Resolve-JdkTool -Name 'java.exe'

    $lastStage = 'VERSION_CHECK'
    $versionErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $versionText = (& $java '-version' 2>&1 | Out-String)
        $javaExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $versionErrorAction
    }

    if ($javaExitCode -ne 0) {
        throw 'Não foi possível executar o Java para verificar a versão.'
    }

    if ($versionText -notmatch 'version "(?<major>\d+)') {
        throw 'Não foi possível identificar a versão do Java.'
    }
    if ([int] $Matches.major -lt 17) {
        throw 'É necessário Java 17 ou superior.'
    }

    if (Test-Path -LiteralPath $finalDirectory) {
        throw 'A configuração TLS de demonstração já existe; não será sobrescrita automaticamente.'
    }

    $lastStage = 'PASSWORD_GENERATION'
    $senha = New-DemoPassword
    $previousPassword = $env:CHAT_DEMO_TLS_PASSWORD
    $serverKeystore = Join-Path $stagingDirectory 'servidor.p12'
    $clientKeystore = Join-Path $stagingDirectory 'cliente.p12'
    $certificate = Join-Path $stagingDirectory 'servidor.crt'
    $truststore = Join-Path $stagingDirectory 'truststore.p12'
    $passwordFile = Join-Path $stagingDirectory 'senhas.dpapi.json'

    $lastStage = 'STAGING_CREATE'
    try {
        New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null
        $env:CHAT_DEMO_TLS_PASSWORD = $senha

        $lastStage = 'KEYTOOL_SERVER'
        Invoke-Keytool -Keytool $keytool -Arguments @('-genkeypair', '-alias', 'servidor', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '825', '-keystore', $serverKeystore,
            '-storetype', 'PKCS12', '-storepass:env', 'CHAT_DEMO_TLS_PASSWORD',
            '-keypass:env', 'CHAT_DEMO_TLS_PASSWORD', '-dname', 'CN=localhost',
            '-ext', 'SAN=dns:localhost,ip:127.0.0.1', '-noprompt')

        $lastStage = 'KEYTOOL_CLIENT'
        Invoke-Keytool -Keytool $keytool -Arguments @('-genkeypair', '-alias', 'cliente', '-keyalg', 'RSA',
            '-keysize', '2048', '-validity', '825', '-keystore', $clientKeystore,
            '-storetype', 'PKCS12', '-storepass:env', 'CHAT_DEMO_TLS_PASSWORD',
            '-keypass:env', 'CHAT_DEMO_TLS_PASSWORD', '-dname', 'CN=chat-client', '-noprompt')

        $lastStage = 'CERT_EXPORT'
        Invoke-Keytool -Keytool $keytool -Arguments @('-exportcert', '-alias', 'servidor', '-keystore',
            $serverKeystore, '-storetype', 'PKCS12', '-storepass:env',
            'CHAT_DEMO_TLS_PASSWORD', '-rfc', '-file', $certificate)

        $lastStage = 'TRUSTSTORE_CREATE'
        Invoke-Keytool -Keytool $keytool -Arguments @('-importcert', '-alias', 'servidor', '-file', $certificate,
            '-keystore', $truststore, '-storetype', 'PKCS12', '-storepass:env',
            'CHAT_DEMO_TLS_PASSWORD', '-noprompt')

        $lastStage = 'DPAPI_SAVE'
        $protectedPassword = Protect-DemoPassword -Password $senha
        [pscustomobject]@{
            servidor_keystore = $protectedPassword
            cliente_keystore = $protectedPassword
            truststore = $protectedPassword
        } | ConvertTo-Json | Set-Content -LiteralPath $passwordFile -Encoding UTF8 -NoNewline
        New-Item -ItemType File -Path (Join-Path $stagingDirectory '.prepared') -Force | Out-Null

        $lastStage = 'FINAL_MOVE'
        Move-Item -LiteralPath $stagingDirectory -Destination $finalDirectory
        Write-Host 'TLS de demonstração preparado em tls-local\demo.'
    }
    catch {
        if (Test-Path -LiteralPath $stagingDirectory) {
            Remove-Item -LiteralPath $stagingDirectory -Recurse -Force -ErrorAction SilentlyContinue
        }
        throw
    }
    finally {
        if ($null -eq $previousPassword) {
            Remove-Item Env:CHAT_DEMO_TLS_PASSWORD -ErrorAction SilentlyContinue
        } else {
            $env:CHAT_DEMO_TLS_PASSWORD = $previousPassword
        }
        $senha = $null
        $protectedPassword = $null
    }
}
catch {
    $record = $_
    $invocation = $record.InvocationInfo
    $sourceFileName = if ($invocation.ScriptName) {
        Split-Path -Leaf $invocation.ScriptName
    } else {
        ''
    }
    Write-Output "LAST_STAGE=$lastStage"
    Write-Output "EXCEPTION_TYPE=$($record.Exception.GetType().FullName)"
    Write-Output "SOURCE_FILE_NAME=$sourceFileName"
    Write-Output "SOURCE_LINE=$($invocation.ScriptLineNumber)"
    throw
}
