param([string]$RootCredentialPath,[string]$DbHost='127.0.0.1',[int]$DbPort=3307)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskLocal=Join-Path $taskRoot '.local'
if(!(Test-Path -LiteralPath (Join-Path $taskLocal 'runtime.json'))){throw 'Existing local environment required.'}
if($RootCredentialPath){$taskCredential=Import-Clixml -LiteralPath $RootCredentialPath}else{$taskCredential=New-Object Management.Automation.PSCredential('root',(Read-Host 'MySQL root password' -AsSecureString))}
$taskSecret=$taskCredential.GetNetworkCredential().Password
$taskClient=Join-Path $taskLocal ('p2-migration-'+[Guid]::NewGuid().ToString('N')+'.ini')
$taskEscaped=$taskSecret.Replace('\','\\').Replace('"','\"')
[IO.File]::WriteAllText($taskClient,"[client]`r`nhost=$DbHost`r`nport=$DbPort`r`nuser=root`r`npassword=`"$taskEscaped`"`r`ndefault-character-set=utf8mb4`r`n",(New-Object Text.UTF8Encoding($false)))
try {
 $taskBackupDir=Join-Path $taskLocal 'backups'
 New-Item -ItemType Directory -Path $taskBackupDir -Force | Out-Null
 $taskBackup=Join-Path $taskBackupDir ('before-p2-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'-'+[Guid]::NewGuid().ToString('N')+'.sql')
 & mysqldump "--defaults-file=$taskClient" --single-transaction --no-tablespaces --set-gtid-purged=OFF "--result-file=$taskBackup" --databases hw_user hw_course hw_learning hw_assignment
 if($LASTEXITCODE -ne 0){throw 'Backup failed; migration stopped.'}
 Get-Content -LiteralPath (Join-Path $taskRoot 'database\migrations\V5__question_bank_and_attempts.sql') -Raw | & mysql "--defaults-file=$taskClient" --batch
 if($LASTEXITCODE -ne 0){throw 'Migration incomplete. Preserve the backup and inspect schema before retrying.'}
 $taskCount=& mysql "--defaults-file=$taskClient" --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment');"
 if($LASTEXITCODE -ne 0 -or [int]$taskCount -ne 30){throw 'Expected thirty tables after P2 migration.'}
 Write-Output 'P2 migration complete: thirty tables; previous answers and grades preserved. Rebuild all backend services before starting.'
}finally{if(Test-Path -LiteralPath $taskClient){Remove-Item -LiteralPath $taskClient -Force}}
