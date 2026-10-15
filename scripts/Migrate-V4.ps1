param([string]$RootCredentialPath,[string]$DbHost='127.0.0.1',[int]$DbPort=3307)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskLocal=Join-Path $taskRoot '.local'
if(!(Test-Path -LiteralPath (Join-Path $taskLocal 'runtime.json'))){throw 'Initialize the local environment first.'}
if($RootCredentialPath){$taskCredential=Import-Clixml -LiteralPath $RootCredentialPath}else{$taskCredential=New-Object Management.Automation.PSCredential('root',(Read-Host 'MySQL root password' -AsSecureString))}
$taskSecret=$taskCredential.GetNetworkCredential().Password
$taskClient=Join-Path $taskLocal 'migration-client.ini'
$taskEscaped=$taskSecret.Replace('\','\\').Replace('"','\"')
[IO.File]::WriteAllText($taskClient,"[client]`r`nhost=$DbHost`r`nport=$DbPort`r`nuser=root`r`npassword=`"$taskEscaped`"`r`ndefault-character-set=utf8mb4`r`n",(New-Object Text.UTF8Encoding($false)))
try {
 $taskBackupDir=Join-Path $taskLocal 'backups'
 New-Item -ItemType Directory -Path $taskBackupDir -Force | Out-Null
 $taskBackup=Join-Path $taskBackupDir ('before-v4-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'.sql')
 & mysqldump "--defaults-file=$taskClient" --single-transaction --no-tablespaces --set-gtid-purged=OFF "--result-file=$taskBackup" --databases hw_user hw_course hw_learning hw_assignment
 if($LASTEXITCODE -ne 0){throw 'Backup failed; migration stopped.'}
 Get-Content -LiteralPath (Join-Path $taskRoot 'database\migrations\V4__sessions_and_audit.sql') -Raw | & mysql "--defaults-file=$taskClient" --batch
 if($LASTEXITCODE -ne 0){throw 'Additive migration incomplete. Keep the backup and inspect database state before retrying.'}
 $taskCount=& mysql "--defaults-file=$taskClient" --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment');"
 if($LASTEXITCODE -ne 0 -or [int]$taskCount -ne 27){throw 'Expected twenty-seven tables after V4 migration.'}
 Write-Output 'V4 migration complete: twenty-seven tables. Original data preserved; private backup saved in .local/backups.'
}finally{if(Test-Path -LiteralPath $taskClient){Remove-Item -LiteralPath $taskClient -Force}}
