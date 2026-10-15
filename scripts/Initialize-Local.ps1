param([string]$RootCredentialPath,[string]$DbHost='127.0.0.1',[int]$DbPort=3307)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskLocal=Join-Path $taskRoot '.local'
if(Test-Path -LiteralPath (Join-Path $taskLocal 'runtime.json')){throw 'Local environment already exists; refusing to rotate keys or database passwords.'}
New-Item -ItemType Directory -Path $taskLocal -Force | Out-Null
$taskSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $taskLocal /inheritance:r /grant ('*'+$taskSid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Unable to protect local credentials'}
if($RootCredentialPath){$taskCredential=Import-Clixml -LiteralPath $RootCredentialPath}else{$taskCredential=New-Object Management.Automation.PSCredential('root',(Read-Host 'MySQL root password' -AsSecureString))}
$taskSecret=$taskCredential.GetNetworkCredential().Password
$taskClientFile=Join-Path $taskLocal 'initialize-client.ini'
$taskEscaped=$taskSecret.Replace('\','\\').Replace('"','\"')
[IO.File]::WriteAllText($taskClientFile,"[client]`r`nhost=$DbHost`r`nport=$DbPort`r`nuser=root`r`npassword=`"$taskEscaped`"`r`ndefault-character-set=utf8mb4`r`n",(New-Object Text.UTF8Encoding($false)))
function New-HanwenSecret { $taskBytes=New-Object byte[] 24; $taskRandom=[Security.Cryptography.RandomNumberGenerator]::Create();$taskRandom.GetBytes($taskBytes);$taskRandom.Dispose();return [Convert]::ToBase64String($taskBytes)+'Aa9!' }
$taskEnv=@{HW_DB_HOST=$DbHost;HW_DB_PORT="$DbPort";HW_SERVICE_KEY=(New-HanwenSecret);HW_ADMIN_PASSWORD=(New-HanwenSecret);HW_PUBLIC_KEY_FILE=(Join-Path $taskLocal 'keys\public.pem');HW_PRIVATE_KEY_FILE=(Join-Path $taskLocal 'keys\private.pem');HW_FRONTEND_ORIGIN='http://127.0.0.1:5173';HW_STORAGE_DIR=(Join-Path $taskLocal 'uploads')}
$taskAccountSql=Join-Path $taskLocal 'initialize-accounts.sql'
try {
 $taskVersion=& mysql "--defaults-file=$taskClientFile" --batch --skip-column-names '--execute=SELECT VERSION();'
 if($LASTEXITCODE -ne 0){throw 'MySQL connection failed'}
 $taskExisting=& mysql "--defaults-file=$taskClientFile" --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('hw_user','hw_course','hw_learning','hw_assignment');"
 if($LASTEXITCODE -ne 0 -or [int]$taskExisting -ne 0){throw 'Target databases contain tables; inspect existing data before initialization.'}
 Get-Content -LiteralPath (Join-Path $taskRoot 'database\hanwen.sql') -Raw | & mysql "--defaults-file=$taskClientFile" --batch
 if($LASTEXITCODE -ne 0){throw 'Schema initialization failed'}
 $taskSql=@()
 foreach($taskDomain in @('user','course','learning','assignment')){
  $taskPassword=New-HanwenSecret;$taskUser='hw_'+$taskDomain+'_app'
  $taskEnv['HW_'+$taskDomain.ToUpper()+'_DB_PASSWORD']=$taskPassword
  $taskSql+="CREATE USER '$taskUser'@'127.0.0.1' IDENTIFIED BY '$taskPassword';"
  $taskSql+="CREATE USER '$taskUser'@'localhost' IDENTIFIED BY '$taskPassword';"
  $taskSql+="GRANT SELECT,INSERT,UPDATE,DELETE ON hw_$taskDomain.* TO '$taskUser'@'127.0.0.1';"
  $taskSql+="GRANT SELECT,INSERT,UPDATE,DELETE ON hw_$taskDomain.* TO '$taskUser'@'localhost';"
 }
 [IO.File]::WriteAllText($taskAccountSql,($taskSql -join "`n"),(New-Object Text.UTF8Encoding($false)))
 Get-Content -LiteralPath $taskAccountSql -Raw | & mysql "--defaults-file=$taskClientFile" --batch
 if($LASTEXITCODE -ne 0){throw 'Scoped application accounts could not be created'}
 $taskKeyDir=Join-Path $taskLocal 'keys'
 & java (Join-Path $taskRoot 'tools\GenerateKeys.java') $taskKeyDir
 if($LASTEXITCODE -ne 0){throw 'RSA key generation failed'}
 $taskEnv | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskLocal 'runtime.json') -Encoding UTF8
 Write-Output ('Local setup ready. MySQL '+$taskVersion+' on '+$DbHost+':'+$DbPort+'. Credentials are private in .local/runtime.json.')
}finally{foreach($taskFile in @($taskClientFile,$taskAccountSql)){if(Test-Path -LiteralPath $taskFile){Remove-Item -LiteralPath $taskFile -Force}}}
