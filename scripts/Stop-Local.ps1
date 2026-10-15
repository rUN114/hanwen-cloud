$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskRecord=Join-Path $taskRoot '.local\processes.json'
if(-not(Test-Path -LiteralPath $taskRecord)){return}
$taskEntries=Get-Content -LiteralPath $taskRecord -Raw | ConvertFrom-Json
foreach($taskEntry in $taskEntries){
 $taskProcess=Get-CimInstance Win32_Process -Filter ('ProcessId='+$taskEntry.pid)
 if($taskProcess -and $taskProcess.Name -eq 'java.exe' -and $taskProcess.CommandLine.Contains($taskRoot) -and $taskProcess.CommandLine.Contains($taskEntry.module+'-1.0.0.jar')){Stop-Process -Id $taskEntry.pid -ErrorAction Stop;Write-Output ('Stopped '+$taskEntry.module+' on '+$taskEntry.port)}
}
