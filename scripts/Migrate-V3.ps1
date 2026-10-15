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
 $taskBackup=Join-Path $taskBackupDir ('before-v3-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'.sql')
 & mysqldump "--defaults-file=$taskClient" --single-transaction --no-tablespaces --set-gtid-purged=OFF "--result-file=$taskBackup" --databases hw_user hw_course hw_learning hw_assignment
 if($LASTEXITCODE -ne 0){throw 'Backup failed; migration stopped.'}
 Get-Content -LiteralPath (Join-Path $taskRoot 'database\migrations\V3__teaching-workflows.sql') -Raw | & mysql "--defaults-file=$taskClient" --batch
 if($LASTEXITCODE -ne 0){throw 'Additive migration incomplete. Keep the backup and inspect database state before retrying.'}
 $taskCount=& mysql "--defaults-file=$taskClient" --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment');"
 if($LASTEXITCODE -ne 0 -or [int]$taskCount -ne 25){throw 'Expected twenty-five tables after V3 migration.'}
 $taskRuntimePath=Join-Path $taskLocal 'runtime.json'
 $taskRuntime=Get-Content -LiteralPath $taskRuntimePath -Raw|ConvertFrom-Json
 if(!$taskRuntime.PSObject.Properties['HW_STORAGE_DIR'] -or [string]::IsNullOrWhiteSpace($taskRuntime.HW_STORAGE_DIR)){
  Copy-Item -LiteralPath $taskRuntimePath -Destination (Join-Path $taskBackupDir ('runtime-before-v3-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'.json'))
  $taskRuntime|Add-Member -NotePropertyName HW_STORAGE_DIR -NotePropertyValue (Join-Path $taskLocal 'uploads') -Force
  $taskRuntime|ConvertTo-Json|Set-Content -LiteralPath $taskRuntimePath -Encoding utf8
 }
 Write-Output 'V3 migration complete: twenty-five tables. Original data preserved; private backup saved in .local/backups.'
}finally{if(Test-Path -LiteralPath $taskClient){Remove-Item -LiteralPath $taskClient -Force}}
