param([string]$Python='python',[int]$GatewayPort=8080,[string]$FrontendUrl='http://127.0.0.1:5173')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& $Python (Join-Path $taskRoot 'tools/monitor.py') --directory $taskRoot --history-dir (Join-Path $taskRoot '.local/monitor') --gateway-port $GatewayPort --frontend-url $FrontendUrl
exit $LASTEXITCODE
