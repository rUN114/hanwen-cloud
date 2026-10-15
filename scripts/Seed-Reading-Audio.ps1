param([string]$VoiceName='Microsoft Huihui Desktop')
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskSample=Join-Path $taskRoot 'samples'
New-Item -ItemType Directory -Path $taskSample -Force|Out-Null
Add-Type -AssemblyName System.Speech
$taskSynth=New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
 $taskSynth.SelectVoice($VoiceName)
 $taskSynth.Rate=-2
 $taskSynth.SetOutputToWaveFile((Join-Path $taskSample '静夜思_合成朗读.wav'))
 $taskSynth.Speak('静夜思。李白。床前明月光，疑是地上霜。举头望明月，低头思故乡。')
 Write-Output 'Original Windows speech synthesis sample saved. This is not a human recording.'
} finally {$taskSynth.Dispose()}
