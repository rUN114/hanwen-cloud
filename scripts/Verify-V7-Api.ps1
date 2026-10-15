param([string]$Python='python',[switch]$IncludeStability)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# Explicitly creates isolated validation courses/accounts. Never run against someone else's teaching environment.
foreach($taskTest in @('v7_platform.py','v7_original_readonly.py')){
 & $Python (Join-Path $taskRoot ('tests/'+$taskTest))
 if($LASTEXITCODE -ne 0){throw ('V7 verification failed: '+$taskTest)}
}
if($IncludeStability){
 foreach($taskTest in @('v7_stability.py','v7_attachment.py')){
  & $Python (Join-Path $taskRoot ('tests/'+$taskTest))
  if($LASTEXITCODE -ne 0){throw ('V7 stability verification failed: '+$taskTest)}
 }
}
