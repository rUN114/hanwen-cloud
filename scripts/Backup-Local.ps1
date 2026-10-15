param([string]$RootCredentialPath,[string]$DbHost='127.0.0.1',[int]$DbPort=3307,[string]$MySqlDirectory='C:\Program Files\MySQL\MySQL Server 9.7\bin',[string]$Python='python')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskLocal=Join-Path $taskRoot '.local'
$taskRuntime=Get-Content -LiteralPath (Join-Path $taskLocal 'runtime.json') -Raw|ConvertFrom-Json
if($RootCredentialPath){$taskCredential=Import-Clixml -LiteralPath $RootCredentialPath}else{$taskCredential=New-Object Management.Automation.PSCredential('root',(Read-Host 'MySQL backup password' -AsSecureString))}
$taskClient=Join-Path $taskLocal ('backup-'+[Guid]::NewGuid().ToString('N')+'.ini')
$taskSecret=$taskCredential.GetNetworkCredential().Password.Replace('\','\\').Replace('"','\"')
[IO.File]::WriteAllText($taskClient,"[client]`r`nhost=$DbHost`r`nport=$DbPort`r`nuser=$($taskCredential.UserName)`r`npassword=`"$taskSecret`"`r`ndefault-character-set=utf8mb4`r`n",(New-Object Text.UTF8Encoding($false)))
try {
 & $Python (Join-Path $taskRoot 'tools/backup.py') --client-file $taskClient --mysql-dir $MySqlDirectory --storage $taskRuntime.HW_STORAGE_DIR --destination (Join-Path $taskLocal 'backups')
 if($LASTEXITCODE -ne 0){throw 'Backup failed. No existing backup was deleted.'}
}finally{if(Test-Path -LiteralPath $taskClient){Remove-Item -LiteralPath $taskClient}}
