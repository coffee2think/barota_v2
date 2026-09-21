param(
    [switch]$Verify
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $repositoryRoot 'dev-resources/docs/서울교통공사_노선별 지하철역 정보.csv'
$assetPath = Join-Path $repositoryRoot 'app/src/main/assets/stations.json'

# The supplied CSV is CP949. Allow reading while it is open in another app.
[Text.Encoding]::RegisterProvider([Text.CodePagesEncodingProvider]::Instance)
$stream = [IO.File]::Open($sourcePath, 'Open', 'Read', 'ReadWrite')
$reader = [IO.StreamReader]::new($stream, [Text.Encoding]::GetEncoding(949))
try {
    $rows = @($reader.ReadToEnd() | ConvertFrom-Csv)
} finally {
    $reader.Dispose()
}
if ($rows.Count -eq 0) { throw 'The station CSV is empty.' }

$existing = @(Get-Content $assetPath -Raw -Encoding utf8 | ConvertFrom-Json)
$existingByName = @{}
foreach ($station in $existing) {
    $key = $station.name
    if ($key -in @('신촌', '양평')) { $key = "$key($($station.lines[0]))" }
    $existingByName[$key] = $station.id
}
$groups = @{}
foreach ($row in $rows) {
    $name = $row.전철역명.Trim()
    $line = $row.호선.Trim()
    $code = $row.전철역코드.Trim()
    if (!$name -or !$line -or $code -notmatch '^\d+[A-Z]?$') { throw 'Invalid station CSV row.' }
    if ($line -match '^0([1-9])호선$') { $line = "$($Matches[1])호선" }
    if ($line -eq '경의선') { $line = '경의중앙선' }
    if ($line -eq '인천선') { $line = '인천1호선' }
    if ($name -in @('총신대입구', '이수')) { $name = '총신대입구(이수)' }
    if ($name -eq '서울' -and $line -eq 'GTX-A') { $name = '서울역' }

    # These homonyms are separate stations, not interchanges.
    $key = $name
    if ($name -in @('신촌', '양평')) { $key = "$name($line)" }
    if (!$groups.ContainsKey($key)) {
        $groups[$key] = @{ Name = $name; Codes = @(); Lines = @() }
    }
    $groups[$key].Codes += $code
    $groups[$key].Lines += $line
}

$stations = @(
    foreach ($name in ($groups.Keys | Sort-Object)) {
        $group = $groups[$name]
        $id = if ($existingByName.ContainsKey($name)) {
            $existingByName[$name]
        } else {
            'station-' + ($group.Codes | Sort-Object | Select-Object -First 1)
        }
        [ordered]@{
            id = $id
            name = $group.Name
            lines = @($group.Lines | Sort-Object -Unique)
        }
    }
)
if (@($stations.id | Sort-Object -Unique).Count -ne $stations.Count) {
    throw 'Duplicate station IDs.'
}
$entries = @($stations | ForEach-Object { '  ' + (ConvertTo-Json -InputObject $_ -Compress -Depth 4) })
$content = "[`n" + ($entries -join ",`n") + "`n]`n"
if ($Verify) {
    $actual = [IO.File]::ReadAllText($assetPath).Replace("`r`n", "`n")
    if ($actual -cne $content) { throw 'stations.json differs from the station CSV. Run the updater.' }
    Write-Output "Verified $($rows.Count) CSV rows, $($stations.Count) stations and $(@($rows.호선 | Sort-Object -Unique).Count) lines."
} else {
    # Emit a patch; apply it with apply_patch instead of writing the asset directly.
    Write-Output '*** Begin Patch'
    Write-Output '*** Update File: app/src/main/assets/stations.json'
    Write-Output '@@'
    foreach ($oldLine in ([IO.File]::ReadAllLines($assetPath))) { Write-Output "-$oldLine" }
    foreach ($newLine in ($content.TrimEnd("`n") -split "`n")) { Write-Output "+$newLine" }
    Write-Output '*** End Patch'
}
