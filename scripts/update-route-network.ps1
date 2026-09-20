param(
    [switch]$Verify
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
$assetPath = Join-Path $repositoryRoot 'app/src/main/assets/route_network.json'
$stationAssetPath = Join-Path $repositoryRoot 'app/src/main/assets/stations.json'

function ConvertTo-StationList([string]$value) {
    return @($value -split '\s*→\s*' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Join-StationPaths([object[]]$paths) {
    $result = [Collections.Generic.List[string]]::new()
    foreach ($path in $paths) {
        foreach ($station in $path) {
            if ($result.Count -eq 0 -or $result[$result.Count - 1] -ne $station) {
                $result.Add($station)
            }
        }
    }
    return $result.ToArray()
}

function New-Edges([string[]]$stations) {
    return @(
        for ($index = 0; $index -lt $stations.Count - 1; $index++) {
            ,@($stations[$index], $stations[$index + 1])
        }
    )
}

function New-Service(
    [string]$id,
    [string]$forward,
    [AllowNull()][object]$reverse,
    [string[]]$stations
) {
    $directions = [ordered]@{ forward = $forward }
    if ($null -ne $reverse) { $directions.reverse = [string]$reverse }
    return [ordered]@{
        id = $id
        directions = $directions
        edges = @(New-Edges $stations)
    }
}

# Source: the 1-9 line ordering transcribed from the user-supplied one-page route map on 2026-09-20.
$line1Common = ConvertTo-StationList '연천 → 전곡 → 청산 → 소요산 → 동두천 → 보산 → 동두천중앙 → 지행 → 덕정 → 덕계 → 양주 → 녹양 → 가능 → 의정부 → 회룡 → 망월사 → 도봉산 → 도봉 → 방학 → 창동 → 녹천 → 월계 → 광운대 → 석계 → 신이문 → 외대앞 → 회기 → 청량리 → 제기동 → 신설동 → 동묘앞 → 동대문 → 종로5가 → 종로3가 → 종각 → 시청 → 서울역 → 남영 → 용산 → 노량진 → 대방 → 신길 → 영등포 → 신도림 → 구로'
$line1Incheon = ConvertTo-StationList '구로 → 구일 → 개봉 → 오류동 → 온수 → 역곡 → 소사 → 부천 → 중동 → 송내 → 부개 → 부평 → 백운 → 동암 → 간석 → 주안 → 도화 → 제물포 → 도원 → 동인천 → 인천'
$line1Sinchang = ConvertTo-StationList '구로 → 가산디지털단지 → 독산 → 금천구청 → 석수 → 관악 → 안양 → 명학 → 금정 → 군포 → 당정 → 의왕 → 성균관대 → 화서 → 수원 → 세류 → 병점 → 세마 → 오산대 → 오산 → 진위 → 송탄 → 서정리 → 평택지제 → 평택 → 성환 → 직산 → 두정 → 천안 → 봉명 → 쌍용 → 아산 → 탕정 → 배방 → 온양온천 → 신창'
$line1Gwangmyeong = ConvertTo-StationList '금천구청 → 광명'
$line1Seodongtan = ConvertTo-StationList '병점 → 서동탄'

$line2Circle = ConvertTo-StationList '시청 → 을지로입구 → 을지로3가 → 을지로4가 → 동대문역사문화공원 → 신당 → 상왕십리 → 왕십리 → 한양대 → 뚝섬 → 성수 → 건대입구 → 구의 → 강변 → 잠실나루 → 잠실 → 잠실새내 → 종합운동장 → 삼성 → 선릉 → 역삼 → 강남 → 교대 → 서초 → 방배 → 사당 → 낙성대 → 서울대입구 → 봉천 → 신림 → 신대방 → 구로디지털단지 → 대림 → 신도림 → 문래 → 영등포구청 → 당산 → 합정 → 홍대입구 → 신촌 → 이대 → 아현 → 충정로 → 시청'
$line2Seongsu = ConvertTo-StationList '성수 → 용답 → 신답 → 용두 → 신설동'
$line2Sinjeong = ConvertTo-StationList '신도림 → 도림천 → 양천구청 → 신정네거리 → 까치산'

$line3 = ConvertTo-StationList '대화 → 주엽 → 정발산 → 마두 → 백석 → 대곡 → 화정 → 원당 → 원흥 → 삼송 → 지축 → 구파발 → 연신내 → 불광 → 녹번 → 홍제 → 무악재 → 독립문 → 경복궁 → 안국 → 종로3가 → 을지로3가 → 충무로 → 동대입구 → 약수 → 금호 → 옥수 → 압구정 → 신사 → 잠원 → 고속터미널 → 교대 → 남부터미널 → 양재 → 매봉 → 도곡 → 대치 → 학여울 → 대청 → 일원 → 수서 → 가락시장 → 경찰병원 → 오금'
$line4 = ConvertTo-StationList '진접 → 오남 → 별내별가람 → 불암산 → 상계 → 노원 → 창동 → 쌍문 → 수유 → 미아 → 미아사거리 → 길음 → 성신여대입구 → 한성대입구 → 혜화 → 동대문 → 동대문역사문화공원 → 충무로 → 명동 → 회현 → 서울역 → 숙대입구 → 삼각지 → 신용산 → 이촌 → 동작 → 총신대입구(이수) → 사당 → 남태령 → 선바위 → 경마공원 → 대공원 → 과천 → 정부과천청사 → 인덕원 → 평촌 → 범계 → 금정 → 산본 → 수리산 → 대야미 → 반월 → 상록수 → 한대앞 → 중앙 → 고잔 → 초지 → 안산 → 신길온천 → 정왕 → 오이도'

$line5Common = ConvertTo-StationList '방화 → 개화산 → 김포공항 → 송정 → 마곡 → 발산 → 우장산 → 화곡 → 까치산 → 신정 → 목동 → 오목교 → 양평 → 영등포구청 → 영등포시장 → 신길 → 여의도 → 여의나루 → 마포 → 공덕 → 애오개 → 충정로 → 서대문 → 광화문 → 종로3가 → 을지로4가 → 동대문역사문화공원 → 청구 → 신금호 → 행당 → 왕십리 → 마장 → 답십리 → 장한평 → 군자 → 아차산 → 광나루 → 천호 → 강동'
$line5Hanam = ConvertTo-StationList '강동 → 길동 → 굽은다리 → 명일 → 고덕 → 상일동 → 강일 → 미사 → 하남풍산 → 하남시청 → 하남검단산'
$line5Macheon = ConvertTo-StationList '강동 → 둔촌동 → 올림픽공원 → 방이 → 오금 → 개롱 → 거여 → 마천'

$line6Loop = ConvertTo-StationList '응암 → 역촌 → 불광 → 독바위 → 연신내 → 구산 → 응암'
$line6Main = ConvertTo-StationList '응암 → 새절 → 증산 → 디지털미디어시티 → 월드컵경기장 → 마포구청 → 망원 → 합정 → 상수 → 광흥창 → 대흥 → 공덕 → 효창공원앞 → 삼각지 → 녹사평 → 이태원 → 한강진 → 버티고개 → 약수 → 청구 → 신당 → 동묘앞 → 창신 → 보문 → 안암 → 고려대 → 월곡 → 상월곡 → 돌곶이 → 석계 → 태릉입구 → 화랑대 → 봉화산 → 신내'
$line7 = ConvertTo-StationList '장암 → 도봉산 → 수락산 → 마들 → 노원 → 중계 → 하계 → 공릉 → 태릉입구 → 먹골 → 중화 → 상봉 → 면목 → 사가정 → 용마산 → 중곡 → 군자 → 어린이대공원 → 건대입구 → 자양 → 청담 → 강남구청 → 학동 → 논현 → 반포 → 고속터미널 → 내방 → 총신대입구(이수) → 남성 → 숭실대입구 → 상도 → 장승배기 → 신대방삼거리 → 보라매 → 신풍 → 대림 → 남구로 → 가산디지털단지 → 철산 → 광명사거리 → 천왕 → 온수 → 까치울 → 부천종합운동장 → 춘의 → 신중동 → 부천시청 → 상동 → 삼산체육관 → 굴포천 → 부평구청 → 산곡 → 석남'
$line8 = ConvertTo-StationList '모란 → 수진 → 신흥 → 단대오거리 → 남한산성입구 → 산성 → 남위례 → 복정 → 장지 → 문정 → 가락시장 → 송파 → 석촌 → 잠실 → 몽촌토성 → 강동구청 → 천호 → 암사'
$line9 = ConvertTo-StationList '개화 → 김포공항 → 공항시장 → 신방화 → 마곡나루 → 양천향교 → 가양 → 증미 → 등촌 → 염창 → 신목동 → 선유도 → 당산 → 국회의사당 → 여의도 → 샛강 → 노량진 → 노들 → 흑석 → 동작 → 구반포 → 신반포 → 고속터미널 → 사평 → 신논현 → 언주 → 선정릉 → 삼성중앙 → 봉은사 → 종합운동장 → 삼전 → 석촌고분 → 석촌 → 송파나루 → 한성백제 → 올림픽공원 → 둔촌오륜 → 중앙보훈병원'

$lines = @(
    [ordered]@{ line = '1호선'; subwayId = '1001'; timetableLineName = '1호선'; services = @(
        (New-Service 'incheon' '하행' '상행' (Join-StationPaths @($line1Common, $line1Incheon)))
        (New-Service 'sinchang' '하행' '상행' (Join-StationPaths @($line1Common, $line1Sinchang)))
        (New-Service 'gwangmyeong' '하행' '상행' (Join-StationPaths @($line1Common, $line1Sinchang[0..3], $line1Gwangmyeong)))
        (New-Service 'seodongtan' '하행' '상행' (Join-StationPaths @($line1Common, $line1Sinchang[0..16], $line1Seodongtan)))
    ) }
    [ordered]@{ line = '2호선'; subwayId = '1002'; timetableLineName = '2호선'; services = @(
        (New-Service 'circle' '내선' '외선' $line2Circle)
        (New-Service 'seongsu_branch' '내선' '외선' $line2Seongsu)
        (New-Service 'sinjeong_branch' '내선' '외선' $line2Sinjeong)
    ) }
    [ordered]@{ line = '3호선'; subwayId = '1003'; timetableLineName = '3호선'; services = @((New-Service 'main' '하행' '상행' $line3)) }
    [ordered]@{ line = '4호선'; subwayId = '1004'; timetableLineName = '4호선'; services = @((New-Service 'main' '하행' '상행' $line4)) }
    [ordered]@{ line = '5호선'; subwayId = '1005'; timetableLineName = '5호선'; services = @(
        (New-Service 'hanam' '하행' '상행' (Join-StationPaths @($line5Common, $line5Hanam)))
        (New-Service 'macheon' '하행' '상행' (Join-StationPaths @($line5Common, $line5Macheon)))
    ) }
    [ordered]@{ line = '6호선'; subwayId = '1006'; timetableLineName = '6호선'; services = @(
        (New-Service 'eungam_loop' '하행' $null $line6Loop)
        (New-Service 'main' '하행' '상행' $line6Main)
    ) }
    [ordered]@{ line = '7호선'; subwayId = '1007'; timetableLineName = '7호선'; services = @((New-Service 'main' '하행' '상행' $line7)) }
    [ordered]@{ line = '8호선'; subwayId = '1008'; timetableLineName = '8호선'; services = @((New-Service 'main' '상행' '하행' $line8)) }
    [ordered]@{ line = '9호선'; subwayId = '1009'; timetableLineName = '9호선'; services = @((New-Service 'main' '하행' '상행' $line9)) }
)

$network = [ordered]@{
    version = 2
    aliases = [ordered]@{
        '서울' = '서울역'
        '종로3가역' = '종로3가'; '종각역' = '종각'; '시청역' = '시청'; '남영역' = '남영'
        '용산역' = '용산'; '노량진역' = '노량진'; '대방역' = '대방'; '신길역' = '신길'
        '영등포역' = '영등포'; '신도림역' = '신도림'; '구로역' = '구로'
        '가산디지털단지역' = '가산디지털단지'; '독산역' = '독산'
        '자양(뚝섬한강공원)' = '자양'
        '당고개' = '불암산'
    }
    apiStationNames = [ordered]@{
        '서울역' = '서울'
        '응암' = '응암순환(상선)'
        '공릉' = '공릉(서울산업대입구)'
        '남한산성입구' = '남한산성입구(성남법원, 검찰청)'
        '천호' = '천호(풍납토성)'
        '몽촌토성' = '몽촌토성(평화의문)'
        '자양' = '자양(뚝섬한강공원)'
    }
    lines = $lines
}

$catalog = @(Get-Content $stationAssetPath -Raw -Encoding utf8 | ConvertFrom-Json)
$catalogByName = @{}
foreach ($station in $catalog) {
    $catalogByName[$station.name] = @($catalogByName[$station.name]) + @($station.lines) | Sort-Object -Unique
}
$catalogErrors = [Collections.Generic.List[string]]::new()
foreach ($line in $lines) {
    $ids = @($line.services | ForEach-Object id)
    if (($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw "Duplicate service ID in $($line.line)." }
    foreach ($service in $line.services) {
        if ($service.edges.Count -eq 0) { throw "Empty service $($line.line)/$($service.id)." }
        foreach ($edge in $service.edges) {
            if ($edge.Count -ne 2 -or $edge[0] -eq $edge[1]) { throw "Invalid edge in $($line.line)/$($service.id)." }
            foreach ($name in $edge) {
                if (!$catalogByName.ContainsKey($name)) {
                    $catalogErrors.Add("Station '$name' is missing from stations.json.")
                } elseif ($line.line -notin $catalogByName[$name]) {
                    $catalogErrors.Add("Station '$name' is not catalogued on $($line.line).")
                }
            }
        }
    }
}
if ($catalogErrors.Count -gt 0) {
    throw (($catalogErrors | Sort-Object -Unique) -join [Environment]::NewLine)
}

$content = ((ConvertTo-Json -InputObject $network -Depth 12).Replace("`r`n", "`n")) + "`n"
if ($Verify) {
    $actual = [IO.File]::ReadAllText($assetPath).Replace("`r`n", "`n")
    if ($actual -cne $content) { throw 'route_network.json is stale. Run this script to regenerate it.' }
} else {
    [IO.File]::WriteAllText($assetPath, $content, [Text.UTF8Encoding]::new($false))
}

$serviceCount = @($lines | ForEach-Object services).Count
$edgeCount = @($lines | ForEach-Object services | ForEach-Object edges).Count
$stationCount = @($lines | ForEach-Object services | ForEach-Object edges | ForEach-Object { $_[0]; $_[1] } | Sort-Object -Unique).Count
Write-Output "Verified $($lines.Count) lines, $serviceCount services, $edgeCount edges and $stationCount unique stations."
