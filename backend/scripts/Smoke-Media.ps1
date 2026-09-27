param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$CameraSourceBaseUrl = 'http://127.0.0.1:8080',
    [string]$Ffmpeg = 'ffmpeg',
    [string]$Ffprobe = 'ffprobe'
)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$CameraSourceBaseUrl = $CameraSourceBaseUrl.TrimEnd('/')
$buildDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../target'))
$smokeDirectory = Join-Path $buildDirectory ('media-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $smokeDirectory -Force | Out-Null
$videoId = $null
$cameraId = $null

function Require($Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Api([string]$Method, [string]$Path, $Body = $null) {
    $options = @{ Method = $Method; Uri = "$BaseUrl$Path"; TimeoutSec = 40 }
    if ($null -ne $Body) { $options.ContentType = 'application/json'; $options.Body = ($Body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @options
}
function Check-Jpeg([string]$Path, [string]$Name) {
    $file = Join-Path $smokeDirectory $Name
    $result = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$Path" -OutFile $file -PassThru -TimeoutSec 40
    Require ($result.Headers['Content-Type'] -match 'image/jpeg') 'Snapshot has incorrect Content-Type'
    $json = & $Ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height -of json $file
    Require ($LASTEXITCODE -eq 0) 'FFprobe could not read JPEG'
    $stream = ($json | ConvertFrom-Json).streams[0]
    Require ($stream.codec_name -eq 'mjpeg' -and $stream.width -eq 160 -and $stream.height -eq 120) 'Snapshot metadata mismatch'
}
function Check-Hls([string]$Url) {
    $playlist = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$Url" -TimeoutSec 20
    Require ($playlist.Headers['Content-Type'] -match 'application/vnd.apple.mpegurl') 'Wrong playlist Content-Type'
    # PowerShell may treat this non-text MIME type as bytes.
    $text = if ($playlist.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($playlist.Content) } else { [string]$playlist.Content }
    Require ($text.StartsWith('#EXTM3U')) 'Invalid HLS playlist'
    $segments = @($text -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^segment_[0-9]{6,}\.ts$' })
    Require ($segments.Count -gt 0) 'No HLS segment references'
    $segmentUrl = $Url.Replace('index.m3u8', $segments[-1])
    $segmentFile = Join-Path $smokeDirectory 'segment.ts'
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$segmentUrl" -OutFile $segmentFile -PassThru -TimeoutSec 20
    Require ($response.Headers['Content-Type'] -match 'video/mp2t') 'Wrong segment Content-Type'
    $json = & $Ffprobe -v error -show_entries stream=codec_name,codec_type -of json $segmentFile
    Require ($LASTEXITCODE -eq 0) 'FFprobe could not read HLS segment'
    $streams = @(($json | ConvertFrom-Json).streams)
    Require ($streams.Count -eq 1 -and $streams[0].codec_name -eq 'h264' -and $streams[0].codec_type -eq 'video') 'HLS must contain H.264 video without audio'
}

try {
    Require ((Api GET '/actuator/health').status -eq 'UP') 'Actuator health is not UP'
    $swagger = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/swagger-ui/index.html" -TimeoutSec 20
    Require ($swagger.StatusCode -eq 200) 'Swagger UI is unavailable'
    $openapi = Api GET '/v3/api-docs'
    foreach ($path in @('/api/cameras/{id}/hls/start', '/api/cameras/{id}/hls/stop', '/api/cameras/{id}/hls/status',
            '/api/videos/{id}/simulation/start', '/api/videos/{id}/simulation/stop', '/api/videos/{id}/simulation/status')) {
        Require ($null -ne $openapi.paths.PSObject.Properties[$path]) "Missing OpenAPI path: $path"
    }
    $fixture = Join-Path $smokeDirectory 'fixture.mp4'
    & $Ffmpeg -hide_banner -loglevel error -nostdin -y -f lavfi -i 'testsrc=size=160x120:rate=10' -t 4 -c:v libx264 -pix_fmt yuv420p $fixture
    Require ($LASTEXITCODE -eq 0) 'Cannot generate MP4 fixture'
    $uploaded = & curl.exe --silent --show-error --fail-with-body -X POST "$BaseUrl/api/videos" -F "file=@$fixture;type=video/mp4"
    Require ($LASTEXITCODE -eq 0) 'Upload failed'
    $video = $uploaded | ConvertFrom-Json
    $videoId = $video.id
    Require ($video.status -eq 'READY' -and $video.width -eq 160 -and $video.durationSeconds -gt 0) 'Invalid uploaded metadata'
    Require ((Api GET "/api/videos/$videoId").id -eq $videoId) 'Persisted video cannot be fetched'
    Check-Jpeg "/api/videos/$videoId/snapshot?second=0" 'video-zero.jpg'
    Check-Jpeg "/api/videos/$videoId/snapshot?second=2" 'video-middle.jpg'
    $simulation = Api POST "/api/videos/$videoId/simulation/start"
    $duplicate = Api POST "/api/videos/$videoId/simulation/start"
    Require ($simulation.running -and $simulation.startedAt -eq $duplicate.startedAt) 'Simulation start is not idempotent'
    Check-Hls $simulation.streamUrl
    Require ((Api GET "/api/videos/$videoId/simulation/status").running) 'Simulation status is not running'

    $camera = Api POST '/api/cameras' @{ name = 'Local HLS smoke fixture'; sourceType = 'HLS'; sourceUrl = "$CameraSourceBaseUrl$($simulation.streamUrl)" }
    $cameraId = $camera.id
    Check-Jpeg "/api/cameras/$cameraId/snapshot" 'camera.jpg'
    $cameraStream = Api POST "/api/cameras/$cameraId/hls/start"
    Require ($cameraStream.running) 'Camera stream did not start'
    Require ((Api GET "/api/cameras/$cameraId/hls/status").running) 'Camera status is not running'
    Check-Hls $cameraStream.streamUrl
    Require ((Api POST "/api/cameras/$cameraId/hls/stop").status -eq 'STOPPED') 'Camera stop failed'
    Require ((Api POST "/api/cameras/$cameraId/hls/stop").status -eq 'STOPPED') 'Repeated camera stop failed'
    Require ((Api POST "/api/videos/$videoId/simulation/stop").status -eq 'STOPPED') 'Simulation stop failed'
    Require ((Api POST "/api/videos/$videoId/simulation/stop").status -eq 'STOPPED') 'Repeated simulation stop failed'
    Require (-not (Api GET "/api/videos/$videoId/simulation/status").running) 'Stopped simulation still reports running'
    Write-Output 'SMOKE PASSED: health, Swagger/OpenAPI, persisted upload/metadata, video JPEG 0/middle, live simulation, camera JPEG/HLS, H.264 segments, idempotent start/stop.'
}
finally {
    try {
        if ($cameraId) { Api DELETE "/api/cameras/$cameraId" | Out-Null }
    } finally {
        try {
            if ($videoId) { Api DELETE "/api/videos/$videoId" | Out-Null }
        } finally {
            # Delete only the generated, uniquely named subtree within this backend's target directory.
            $resolved = [IO.Path]::GetFullPath($smokeDirectory)
            if ($resolved.StartsWith($buildDirectory + [IO.Path]::DirectorySeparatorChar) -and
                    [IO.Path]::GetFileName($resolved) -match '^media-smoke-[0-9a-f]{32}$') {
                Remove-Item -LiteralPath $resolved -Recurse -Force
            }
        }
    }
}
