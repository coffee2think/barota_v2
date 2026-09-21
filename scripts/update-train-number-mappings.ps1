param(
    [string]$WeekdayDate,
    [string]$WeekendDate,
    [string]$OutputPath = "app/src/main/assets/train_number_mappings.json",
    [switch]$Verify
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resolvedOutputPath = Join-Path $repositoryRoot $OutputPath

function Get-MappingKey($mapping) {
    @(
        $mapping.timetableLineName,
        $mapping.realtimeTrainNumber,
        $mapping.direction,
        $mapping.terminalStation,
        $mapping.trainType,
        $mapping.dayType
    ) -join "`u{001f}"
}

function Test-MappingAsset([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "매핑 자산을 찾을 수 없습니다: $path"
    }
    $asset = Get-Content -Raw -Encoding UTF8 -LiteralPath $path | ConvertFrom-Json
    if ($asset.version -ne 1) {
        throw "지원하지 않는 매핑 자산 버전입니다: $($asset.version)"
    }
    $duplicates = @($asset.mappings | Group-Object { Get-MappingKey $_ } | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        throw "복합 키가 중복된 매핑이 $($duplicates.Count)개 있습니다."
    }
    $invalid = @($asset.mappings | Where-Object {
        [string]::IsNullOrWhiteSpace($_.timetableLineName) -or
        [string]::IsNullOrWhiteSpace($_.realtimeTrainNumber) -or
        [string]::IsNullOrWhiteSpace($_.direction) -or
        [string]::IsNullOrWhiteSpace($_.terminalStation) -or
        [string]::IsNullOrWhiteSpace($_.trainType) -or
        [string]::IsNullOrWhiteSpace($_.dayType) -or
        [string]::IsNullOrWhiteSpace($_.timetableTrainNumber)
    })
    if ($invalid.Count -gt 0) {
        throw "필수 값이 비어 있는 매핑이 $($invalid.Count)개 있습니다."
    }
    Write-Host "매핑 자산 검증 완료: $($asset.mappings.Count)개"
}

if ($Verify) {
    Test-MappingAsset $resolvedOutputPath
    exit 0
}

function Find-Date([bool]$weekend) {
    $date = (Get-Date).Date
    for ($offset = 0; $offset -lt 7; $offset++) {
        $candidate = $date.AddDays($offset)
        $isWeekend = $candidate.DayOfWeek -in @([DayOfWeek]::Saturday, [DayOfWeek]::Sunday)
        if ($isWeekend -eq $weekend) {
            return $candidate.ToString("yyyy-MM-dd")
        }
    }
    throw "대표 날짜를 결정하지 못했습니다."
}

if ([string]::IsNullOrWhiteSpace($WeekdayDate)) { $WeekdayDate = Find-Date $false }
if ([string]::IsNullOrWhiteSpace($WeekendDate)) { $WeekendDate = Find-Date $true }

$propertiesPath = Join-Path $repositoryRoot "local.properties"
$keyLine = Get-Content -Encoding UTF8 -LiteralPath $propertiesPath |
    Where-Object { $_ -match '^SEOUL_TIMETABLE_API_KEY=' } |
    Select-Object -First 1
if (-not $keyLine) {
    throw "local.properties에 SEOUL_TIMETABLE_API_KEY가 필요합니다."
}
$apiKey = $keyLine.Substring($keyLine.IndexOf('=') + 1).Trim()

function Encode-Segment([string]$value) {
    [Uri]::EscapeDataString($value)
}

function New-ScheduleUrl([string[]]$segments) {
    "http://openapi.seoul.go.kr:8088/" + (($segments | ForEach-Object { Encode-Segment $_ }) -join "/")
}

function Normalize-StationName([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return "" }
    $normalized = $value.Trim() -replace '\s', '' -replace '역$', ''
    $normalized = $normalized -replace '\([^)]*\)', '' -replace '행$', ''
    $normalized
}

function Normalize-TrainNumber([string]$value) {
    $digits = $value -replace '\D', ''
    if ([string]::IsNullOrEmpty($digits)) { return "" }
    $normalized = $digits.TrimStart('0')
    if ([string]::IsNullOrEmpty($normalized)) { "0" } else { $normalized }
}

$collectionPoints = @(
    [pscustomobject]@{ line = "1호선"; station = "종로3가"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "2호선"; station = "강남"; directions = @("내선", "외선") },
    [pscustomobject]@{ line = "2호선"; station = "성수"; directions = @("내선", "외선") },
    [pscustomobject]@{ line = "2호선"; station = "신도림"; directions = @("내선", "외선") },
    [pscustomobject]@{ line = "3호선"; station = "교대"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "4호선"; station = "사당"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "5호선"; station = "광화문"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "6호선"; station = "공덕"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "6호선"; station = "응암"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "7호선"; station = "고속터미널"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "8호선"; station = "잠실"; directions = @("상행", "하행") },
    [pscustomobject]@{ line = "9호선"; station = "종합운동장"; directions = @("상행", "하행") }
)
$dayCases = @(
    [pscustomobject]@{ dayType = "평일"; date = $WeekdayDate },
    [pscustomobject]@{ dayType = "주말"; date = $WeekendDate }
)

$candidates = @{}
$pageSize = 1000
$requestCount = 0

foreach ($dayCase in $dayCases) {
    foreach ($point in $collectionPoints) {
        foreach ($direction in $point.directions) {
            $startIndex = 1
            $loaded = 0
            $totalCount = 1
            do {
                $endIndex = $startIndex + $pageSize - 1
                $segments = @(
                    $apiKey, "json", "getTrainSch", "$startIndex", "$endIndex", "", "N",
                    $direction, $dayCase.dayType, $point.line, "", $point.station,
                    "", "", "", "", "", "", "$($dayCase.date) 12:00:00"
                )
                $response = Invoke-RestMethod -Uri (New-ScheduleUrl $segments) -Method Get -TimeoutSec 60
                $requestCount++
                if ($response.response.header.resultCode -ne "00") {
                    throw "$($point.line) $direction $($dayCase.dayType) 조회 실패: $($response.response.header.resultMsg)"
                }
                $rows = @($response.response.body.items.item)
                $totalCount = [int]$response.response.body.totalCount
                $loaded += $rows.Count

                foreach ($row in $rows) {
                    $realtimeNumber = Normalize-TrainNumber ([string]$row.trainno)
                    $terminal = Normalize-StationName ([string]$row.arvlStnNm)
                    if ([string]::IsNullOrWhiteSpace($realtimeNumber) -or [string]::IsNullOrWhiteSpace($terminal)) {
                        continue
                    }
                    $realtimeNumbers = [System.Collections.Generic.List[string]]::new()
                    $realtimeNumbers.Add($realtimeNumber)
                    if ([string]$row.lineNm -eq "2호선" -and [string]$row.trainno -match '^2(\d{3})$') {
                        # 실시간 API에서 관찰된 2호선 운행 구분 번호(6/7/8xxx)를 정적 별칭 행으로 펼친다.
                        foreach ($prefix in @("6", "7", "8")) {
                            $realtimeNumbers.Add("$prefix$($Matches[1])")
                        }
                    }

                    foreach ($candidateRealtimeNumber in $realtimeNumbers) {
                        $mapping = [ordered]@{
                            timetableLineName = [string]$row.lineNm
                            realtimeTrainNumber = $candidateRealtimeNumber
                            direction = [string]$row.upbdnbSe
                            terminalStation = $terminal
                            trainType = if ([string]$row.etrnYn -eq "Y") { "급행" } else { "일반" }
                            dayType = [string]$row.wkndSe
                            timetableTrainNumber = [string]$row.trainno
                        }
                        $key = Get-MappingKey ([pscustomobject]$mapping)
                        if (-not $candidates.ContainsKey($key)) {
                            $candidates[$key] = [pscustomobject]@{
                                mapping = $mapping
                                timetableNumbers = [System.Collections.Generic.HashSet[string]]::new()
                            }
                        }
                        [void]$candidates[$key].timetableNumbers.Add([string]$row.trainno)
                    }
                }

                $startIndex += $pageSize
            } while ($loaded -lt $totalCount -and $rows.Count -gt 0)

            Write-Host "$($dayCase.dayType) $($point.line) $direction $($point.station): $loaded/$totalCount"
        }
    }
}

$mappings = [System.Collections.Generic.List[object]]::new()
$conflicts = [System.Collections.Generic.List[object]]::new()
foreach ($candidate in $candidates.Values) {
    if ($candidate.timetableNumbers.Count -eq 1) {
        $candidate.mapping.timetableTrainNumber = @($candidate.timetableNumbers)[0]
        $mappings.Add([pscustomobject]$candidate.mapping)
    } else {
        $conflicts.Add([pscustomobject]@{
            key = Get-MappingKey ([pscustomobject]$candidate.mapping)
            timetableTrainNumbers = @($candidate.timetableNumbers | Sort-Object)
        })
    }
}

$sortedMappings = @($mappings | Sort-Object timetableLineName, dayType, direction, realtimeTrainNumber, terminalStation, trainType)
$asset = [ordered]@{
    version = 1
    generatedAt = (Get-Date).ToString("o")
    sourceDates = [ordered]@{ weekday = $WeekdayDate; weekend = $WeekendDate }
    mappings = $sortedMappings
}

$outputDirectory = Split-Path -Parent $resolvedOutputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$asset | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -LiteralPath $resolvedOutputPath

$reportDirectory = Join-Path $repositoryRoot "build/reports"
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$conflictPath = Join-Path $reportDirectory "train-number-mapping-conflicts.json"
@($conflicts) | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath $conflictPath

Test-MappingAsset $resolvedOutputPath
Write-Host "API 요청: $requestCount, 저장 매핑: $($sortedMappings.Count), 충돌 제외: $($conflicts.Count)"
Write-Host "매핑 자산: $resolvedOutputPath"
Write-Host "충돌 보고서: $conflictPath"
