param([string]$Python='python',[switch]$CreateTestFixtures)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if(-not $CreateTestFixtures){throw 'Use -CreateTestFixtures explicitly: these real API checks create isolated users, courses, attempts, revisions and task batches. They need the running local services and private runtime/demo account configuration.'}
foreach($taskFile in @('runtime.json','demo-accounts.json')){
 if(-not(Test-Path -LiteralPath (Join-Path $taskRoot ('.local/'+$taskFile)))){throw "Missing private local prerequisite: $taskFile"}
}
foreach($taskTest in @('v6_review.py','v6_revision.py','v6_batch.py','v6_archive.py')){
 & $Python (Join-Path $taskRoot ('tests/'+$taskTest))
 if($LASTEXITCODE -ne 0){throw "Real API verification stopped: $taskTest"}
}
Write-Output 'V6 real API checks passed. Browser, migration, SQL restore and remote CI remain separate evidence.'
