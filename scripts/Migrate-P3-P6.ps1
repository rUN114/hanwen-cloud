param([string]$RootCredentialPath,[string]$DbHost='127.0.0.1',[int]$DbPort=3307,[string]$Python='python',[string]$MySqlDirectory='C:\Program Files\MySQL\MySQL Server 9.7\bin')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskLocal=Join-Path $taskRoot '.local'
if(!(Test-Path -LiteralPath (Join-Path $taskLocal 'runtime.json'))){throw 'Existing local environment required.'}
if($RootCredentialPath){$taskCredential=Import-Clixml -LiteralPath $RootCredentialPath}else{$taskCredential=New-Object Management.Automation.PSCredential('root',(Read-Host 'MySQL root password' -AsSecureString))}
$taskSecret=$taskCredential.GetNetworkCredential().Password
$taskClient=Join-Path $taskLocal ('p3-p6-migration-'+[Guid]::NewGuid().ToString('N')+'.ini')
$taskEscaped=$taskSecret.Replace('\','\\').Replace('"','\"')
[IO.File]::WriteAllText($taskClient,"[client]`r`nhost=$DbHost`r`nport=$DbPort`r`nuser=root`r`npassword=`"$taskEscaped`"`r`ndefault-character-set=utf8mb4`r`n",(New-Object Text.UTF8Encoding($false)))
try {
 $taskRuntime=Get-Content -LiteralPath (Join-Path $taskLocal 'runtime.json') -Raw|ConvertFrom-Json
 & $Python (Join-Path $taskRoot 'tools/backup.py') --client-file $taskClient --mysql-dir $MySqlDirectory --storage $taskRuntime.HW_STORAGE_DIR --destination (Join-Path $taskLocal 'backups')
 if($LASTEXITCODE -ne 0){throw 'Full SQL/upload backup failed; migration stopped.'}
 $taskBackupDir=Join-Path $taskLocal 'backups'
 New-Item -ItemType Directory -Path $taskBackupDir -Force | Out-Null
 $taskBackup=Join-Path $taskBackupDir ('before-p3-p6-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'-'+[Guid]::NewGuid().ToString('N')+'.sql')
 & mysqldump "--defaults-file=$taskClient" --single-transaction --no-tablespaces --set-gtid-purged=OFF "--result-file=$taskBackup" --databases hw_user hw_course hw_learning hw_assignment
 if($LASTEXITCODE -ne 0){throw 'Backup failed; migration stopped.'}
 foreach($taskMigration in @('V6__rubrics_and_gradebook.sql','V7__literature_documents_and_attachments.sql','V8__directed_reminders.sql')){
 Get-Content -LiteralPath (Join-Path $taskRoot ('database/migrations/'+$taskMigration)) -Raw | & mysql "--defaults-file=$taskClient" --batch
 if($LASTEXITCODE -ne 0){throw 'Migration incomplete. Preserve backup and inspect schema.'}
 }
 if($LASTEXITCODE -ne 0){throw 'Migration incomplete. Preserve the backup and inspect schema before retrying.'}
 $taskCount=& mysql "--defaults-file=$taskClient" --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment');"
 if($LASTEXITCODE -ne 0 -or [int]$taskCount -ne 33){throw 'Expected thirty-three tables after P3-P6 migration.'}
 Write-Output 'P3-P6 migration complete: thirty-three tables; previous answers and grades preserved. Rebuild all backend services before starting.'
}finally{if(Test-Path -LiteralPath $taskClient){Remove-Item -LiteralPath $taskClient -Force}}
