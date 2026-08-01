@echo off
powershell -NoProfile -Command "if (Test-Path 'D:\workspace\code\project\bjtu_food\scripts\end.wav') { (New-Object Media.SoundPlayer 'D:\workspace\code\project\bjtu_food\scripts\end.wav').PlaySync() } else { Write-Host 'end.wav not found' }"
