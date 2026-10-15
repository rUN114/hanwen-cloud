param([string]$RootCredentialPath,[string]$Python='python',[string]$MySqlDirectory='C:\Program Files\MySQL\MySQL Server 9.7\bin')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskLocal=Join-Path $taskRoot '.local'
$taskRuntime=Get-Content -LiteralPath (Join-Path $taskLocal 'runtime.json') -Raw|ConvertFrom-Json
if($RootCredentialPath){$taskCredential=Import-Clixml -LiteralPath $RootCredentialPath}else{$taskCredential=New-Object Management.Automation.PSCredential('root',(Read-Host 'MySQL root password' -AsSecureString))}
$taskClient=Join-Path $taskLocal ('v7-migration-'+[Guid]::NewGuid().ToString('N')+'.ini')
$taskSecret=$taskCredential.GetNetworkCredential().Password.Replace('\','\\').Replace('"','\"')
[IO.File]::WriteAllText($taskClient,"[client]`r`nhost=127.0.0.1`r`nport=3307`r`nuser=root`r`npassword=`"$taskSecret`"`r`ndefault-character-set=utf8mb4`r`n",(New-Object Text.UTF8Encoding($false)))
try {
 & $Python (Join-Path $taskRoot 'tools/backup.py') --client-file $taskClient --mysql-dir $MySqlDirectory --storage $taskRuntime.HW_STORAGE_DIR --destination (Join-Path $taskLocal 'backups')
 if($LASTEXITCODE -ne 0){throw 'Complete SQL/upload backup failed; migration stopped.'}
 foreach($taskMigration in @('V9__review_and_revisions.sql','V10__course_team.sql','V11__written_practice.sql')){
  Get-Content -LiteralPath (Join-Path $taskRoot ('database/migrations/'+$taskMigration)) -Raw | & (Join-Path $MySqlDirectory 'mysql.exe') "--defaults-file=$taskClient" --batch
  if($LASTEXITCODE -ne 0){throw ('Migration incomplete: '+$taskMigration)}
 }
 if($LASTEXITCODE -ne 0){throw 'Migration incomplete. Keep backup and inspect before retrying.'}
 $taskCount=& (Join-Path $MySqlDirectory 'mysql.exe') "--defaults-file=$taskClient" --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment');"
 if($LASTEXITCODE -ne 0 -or [int]$taskCount -ne 39){throw 'Expected thirty-nine tables after V7 migration.'}
 Write-Output 'V7 migration complete: thirty-nine tables; original answers and grades preserved.'
}finally{if(Test-Path -LiteralPath $taskClient){Remove-Item -LiteralPath $taskClient}}
