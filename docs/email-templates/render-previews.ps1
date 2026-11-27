param(
    [string]$SampleToken = "482917",
    # sample addresses for the notifications (example.com only, never a real address)
    [string]$SampleEmail = "nova.adresa@example.com",
    [string]$SampleOldEmail = "stara.adresa@example.com"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..")
$outDir = Join-Path $repoRoot "llm\outputs\email-previews"

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$templates = @(
    "confirm-signup",
    "reset-password",
    "reauthentication",
    "password-changed",
    "email-changed"
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$branchRegex = [regex]::new('(?s)\{\{\s*if\s+eq\s+\.Data\.lang\s+"sk"\s*\}\}(.*?)\{\{\s*else\s*\}\}(.*?)\{\{\s*end\s*\}\}')

foreach ($name in $templates) {
    $srcPath = Join-Path $scriptDir "$name.html"
    $raw = [System.IO.File]::ReadAllText($srcPath, [System.Text.Encoding]::UTF8)

    foreach ($lang in @("sk", "hu")) {
        $rendered = $branchRegex.Replace($raw, {
            param($m)
            if ($lang -eq "sk") { return $m.Groups[1].Value }
            return $m.Groups[2].Value
        })
        $rendered = $rendered.Replace("{{ .Token }}", $SampleToken)
        $rendered = $rendered.Replace("{{ .OldEmail }}", $SampleOldEmail)
        $rendered = $rendered.Replace("{{ .Email }}", $SampleEmail)

        if ($rendered -match '\{\{|\}\}') {
            throw "Unresolved template placeholder in $name ($lang)"
        }

        $outPath = Join-Path $outDir "$name-$lang.html"
        [System.IO.File]::WriteAllText($outPath, $rendered, $utf8NoBom)
        Write-Host "Wrote $outPath"
    }
}
