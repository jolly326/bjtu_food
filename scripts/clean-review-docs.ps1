# 删除 docs/产品设计评审/ 下除 trouble.md 外的所有文件
# 用途：评审决议已全部落地到代码与各层真源文档（api/schema/func/ui），
#       评审目录完成使命，仅保留 trouble.md 作为遗留台账。
$target = Join-Path $PSScriptRoot '..\docs\产品设计评审'
if (-not (Test-Path $target)) { Write-Error "目录不存在: $target"; exit 1 }

$files = Get-ChildItem $target -File | Where-Object { $_.Name -ne 'trouble.md' }
foreach ($f in $files) {
    Write-Host "DELETE $($f.Name)"
    Remove-Item $f.FullName -Force
}
Write-Host "KEPT   trouble.md"
Write-Host "已删除 $($files.Count) 个文件"
