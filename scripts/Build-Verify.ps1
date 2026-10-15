param([string]$Maven='mvn',[string]$Npm='npm',[string]$Node='node',[string]$MavenSettings,[string]$GlobalMavenSettings,[switch]$Offline,[switch]$SkipInstall)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskMavenOptions=@('-B','-ntp')
if($MavenSettings){$taskMavenOptions+=@('-s',(Resolve-Path -LiteralPath $MavenSettings).Path)}
if($GlobalMavenSettings){$taskMavenOptions+=@('-gs',(Resolve-Path -LiteralPath $GlobalMavenSettings).Path)}
if($Offline){$taskMavenOptions+='-o'}
# Builds only. It does not launch services, migrate databases, modify defence settings or initialize data.
Push-Location $taskRoot
try {
 & $Maven @taskMavenOptions -f backend/pom.xml package
 if($LASTEXITCODE -ne 0){throw 'Backend build or JUnit checks failed'}
 Push-Location (Join-Path $taskRoot 'frontend')
 try { if(-not $SkipInstall){& $Npm ci; if($LASTEXITCODE -ne 0){throw 'Frontend dependency installation failed'}}
  & $Npm run build; if($LASTEXITCODE -ne 0){throw 'Frontend type check or build failed'}
 }finally{Pop-Location}
 foreach($taskTest in @('export.test.mjs','roster.test.mjs','workspace.test.mjs','revision-diff.test.mjs')){
  if(Test-Path -LiteralPath (Join-Path $taskRoot ('tests/'+$taskTest))){& $Node (Join-Path $taskRoot ('tests/'+$taskTest));if($LASTEXITCODE -ne 0){throw "Verification failed: $taskTest"}}
 }
 Write-Output 'Build and static verification passed. Real API/browser/database checks are separate.'
}finally{Pop-Location}
