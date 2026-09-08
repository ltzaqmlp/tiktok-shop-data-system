$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'backup.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`""
$trigger = New-ScheduledTaskTrigger -Daily -At '20:00'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 1)
Register-ScheduledTask -TaskName 'TikTokShopDailyBackup' -Description '经营数据平台每日数据库备份，保留7日与4周备份' -Action $action -Trigger $trigger -Settings $settings -User ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -RunLevel Highest -Force | Select-Object TaskName,State
