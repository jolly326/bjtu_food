# =============================================================
# 食在交大 接口层扩展测试（第2轮+覆盖：发布/编辑/删除/通知/上传/管理CRUD/审核副作用/越权边界）
# 依赖 api-smoke.ps1 已通过的 48 项为基础；本脚本只测新增覆盖。
# 用例产生的数据带 API-TEST 标记，跑完由 seed 清理（见 docs/test-checklist.md §0.3）
# =============================================================
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
$results = @()
$cur = ''
$suffix = Get-Date -Format 'HHmmss'

function TestName([string]$n) { $script:cur = $n }
function OK([string]$m) { $script:results += "PASS  | $script:cur | $m" }
function FAIL([string]$m) { $script:results += "FAIL  | $script:cur | $m" }

function Api([string]$method, [string]$path, $body = $null, [string]$token = '') {
    $params = @{ Uri = "$base$path"; Method = $method }
    if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = ($body | ConvertTo-Json -Compress -Depth 6) }
    if ($token) { $params.Headers = @{ Authorization = "Bearer $token" } }
    try { return Invoke-RestMethod @params }
    catch { return [pscustomobject]@{ code = [int]$_.Exception.Response.StatusCode } }
}
function ArrCount($data) {
    if ($null -eq $data) { return 0 }
    if ($data -is [System.Array]) { return $data.Count }
    if ($null -ne $data.records) { return $data.records.Count }
    if ($null -ne $data.list) { return $data.list.Count }
    return -1
}
function ArrList($data) {
    if ($null -eq $data) { return @() }
    if ($data -is [System.Array]) { return $data }
    if ($null -ne $data.records) { return $data.records }
    if ($null -ne $data.list) { return $data.list }
    return @()
}

$s = Api 'POST' '/auth/login' @{ account = '2024001'; password = '123456' }
$STOK = $s.data.token
$a = Api 'POST' '/auth/login' @{ account = 'admin'; password = '123456' }
$ATOK = $a.data.token
$s2 = Api 'POST' '/auth/login' @{ account = '2024002'; password = '123456' }
$STOK2 = $s2.data.token

# ============ F. 学生端发布/编辑/删除/通知/资料 ============
TestName 'F1 学生发布菜品'
$dn = "API-TEST-DISH-$suffix"
$pd = Api 'POST' '/dishes' @{ stallId = 1; name = $dn; price = 1600; description = 'API-TEST publish'; images = @(); tags = 'recommended' } $STOK
if ($pd.code -eq 200 -and $null -ne $pd.data) { OK "dishId=$($pd.data)" } else { FAIL "code=$($pd.code) msg=$($pd.message)" }
$PDISHID = $pd.data

TestName 'F2 我的发布列表'
$md = Api 'GET' '/my/dishes' $null $STOK
$found = $false
foreach ($r in (ArrList $md.data)) { if ("$($r.id)" -eq "$PDISHID") { $found = $true } }
if ($md.code -eq 200 -and $found) { OK "found dish $PDISHID" } else { FAIL "code=$($md.code) found=$found" }

TestName 'F3 编辑重提(复用原记录,置pending)'
$up = Api 'PUT' "/dishes/$PDISHID" @{ stallId = 1; name = $dn; price = 1800; description = 'API-TEST update'; images = @() } $STOK
if ($up.code -eq 200) { OK "updated" } else { FAIL "code=$($up.code) msg=$($up.message)" }

TestName 'F4 删除本人评价'
$rv = Api 'POST' '/reviews' @{ dishId = 11; rating = 5; content = 'API-TEST delrv'; images = @() } $STOK
if ($rv.code -eq 200) { OK "review submitted" } else { FAIL "submit code=$($rv.code)" }
$my = Api 'GET' '/my/reviews' $null $STOK
$myId = $null
foreach ($r in (ArrList $my.data)) { if ($r.content -like '*API-TEST delrv*') { $myId = $r.id; break } }
if ($null -ne $myId) {
    $d = Api 'DELETE' "/reviews/$myId" $null $STOK
    if ($d.code -eq 200) { OK "review $myId deleted" } else { FAIL "del code=$($d.code)" }
} else { FAIL 'review not in my list' }

TestName 'F5 删除自己动态'
$mm = Api 'POST' '/moments' @{ content = 'API-TEST delmom'; images = @() } $STOK
$MOMID = $mm.data.id
$dm = Api 'DELETE' "/my/moments/$MOMID" $null $STOK
if ($dm.code -eq 200) { OK "moment $MOMID deleted" } else { FAIL "code=$($dm.code)" }

TestName 'F6 删除自己评论'
$mm2 = Api 'POST' '/moments' @{ content = 'API-TEST cmt'; images = @() } $STOK
$MOMID2 = $mm2.data.id
$cm = Api 'POST' "/moments/$MOMID2/comments" @{ content = 'API-TEST mycmt' } $STOK
$cmlist = Api 'GET' "/moments/$MOMID2/comments" $null $STOK
$cid = $null
foreach ($r in (ArrList $cmlist.data)) { if ($r.content -like '*API-TEST mycmt*') { $cid = $r.id; break } }
if ($null -ne $cid) {
    $dc = Api 'DELETE' "/my/moments/$MOMID2/comments/$cid" $null $STOK
    if ($dc.code -eq 200) { OK "comment $cid deleted" } else { FAIL "del code=$($dc.code)" }
    Api 'DELETE' "/my/moments/$MOMID2" $null $STOK | Out-Null
} else { FAIL 'comment not found' }

TestName 'F7 评论有用切换'
$mm3 = Api 'POST' '/moments' @{ content = 'API-TEST cu'; images = @() } $STOK
$MOMID3 = $mm3.data.id
$cm3 = Api 'POST' "/moments/$MOMID3/comments" @{ content = 'API-TEST cu2' } $STOK
$cid3 = $cm3.data.id
$cu = Api 'POST' "/moments/$MOMID3/comments/$cid3/useful" $null $STOK
if ($cu.code -eq 200 -and $cu.data.useful -eq $true) { OK 'comment useful on' } else { FAIL "code=$($cu.code) useful=$($cu.data.useful)" }
Api 'DELETE' "/my/moments/$MOMID3" $null $STOK | Out-Null

TestName 'F8 通知列表'
$nl = Api 'GET' '/my/notifications?page=1&pageSize=20' $null $STOK
if ($nl.code -eq 200) { OK "notifications total=$($nl.data.total)" } else { FAIL "code=$($nl.code)" }

TestName 'F9 未读计数'
$nc = Api 'GET' '/my/notifications/unread-count' $null $STOK
if ($nc.code -eq 200 -and $null -ne $nc.data.count) { OK "unread=$($nc.data.count)" } else { FAIL "code=$($nc.code)" }

TestName 'F10 单条已读'
$nl2 = Api 'GET' '/my/notifications?page=1&pageSize=20' $null $STOK
$nid = $null
foreach ($r in (ArrList $nl2.data)) { if ($r.isRead -eq 0) { $nid = $r.id; break } }
if ($null -ne $nid) {
    $rd = Api 'PUT' "/my/notifications/$nid/read" $null $STOK
    if ($rd.code -eq 200) { OK "read $nid" } else { FAIL "code=$($rd.code)" }
} else { OK 'no unread to test' }

TestName 'F11 全部已读'
$ra = Api 'PUT' '/my/notifications/read-all' $null $STOK
if ($ra.code -eq 200) { OK 'read-all' } else { FAIL "code=$($ra.code)" }

TestName 'F12 动态详情'
$dd = Api 'GET' '/moments/1'
if ($dd.code -eq 200 -and $null -ne $dd.data.id) { OK 'detail ok' } else { FAIL "code=$($dd.code)" }

TestName 'F13 动态评论列表'
$cl = Api 'GET' '/moments/1/comments?page=1&pageSize=20'
if ($cl.code -eq 200) { OK 'comments list' } else { FAIL "code=$($cl.code)" }

TestName 'F14 评价同步动态(shareToMoment)'
$rsm = Api 'POST' '/reviews' @{ dishId = 12; rating = 4; content = 'API-TEST share'; images = @(); shareToMoment = $true } $STOK
if ($rsm.code -eq 200) { OK 'review+share ok' } else { FAIL "code=$($rsm.code) msg=$($rsm.message)" }

TestName 'F15 动态编辑重提'
$mm4 = Api 'POST' '/moments' @{ content = 'API-TEST editmom'; images = @() } $STOK
$MOMID4 = $mm4.data.id
$em = Api 'PUT' "/my/moments/$MOMID4" @{ content = 'API-TEST editmom2'; images = @() } $STOK
if ($em.code -eq 200) { OK 'edit ok' } else { FAIL "code=$($em.code)" }
Api 'DELETE' "/my/moments/$MOMID4" $null $STOK | Out-Null

TestName 'F16 资料修改'
$pf = Api 'PUT' '/auth/profile' @{ nickname = 'API-TEST User' } $STOK
if ($pf.code -eq 200) { OK 'profile updated' } else { FAIL "code=$($pf.code)" }
# 恢复昵称
Api 'PUT' '/auth/profile' @{ nickname = '交大干饭王' } $STOK | Out-Null

TestName 'F17 我的统计'
$st = Api 'GET' '/auth/stats' $null $STOK
if ($st.code -eq 200) { OK 'stats ok' } else { FAIL "code=$($st.code)" }

# ============ G. 管理端扩展 ============
TestName 'G1 实体审核通过(菜品)'
$ap = Api 'POST' "/admin/audit/dish/$PDISHID/approve" $null $ATOK
if ($ap.code -eq 200) { OK "dish $PDISHID approved" } else { FAIL "code=$($ap.code) msg=$($ap.message)" }

TestName 'G1b 审核通过→作者收到通知'
$nl = Api 'GET' '/my/notifications?page=1&pageSize=20' $null $STOK
$gotAudit = $false
foreach ($r in (ArrList $nl.data)) { if ($r.type -eq 'dish_audit') { $gotAudit = $true } }
if ($nl.code -eq 200 -and $gotAudit) { OK 'dish_audit notification received' } else { FAIL "total=$($nl.data.total) gotAudit=$gotAudit" }

TestName 'G2 审核通过后公开可见'
$pub = Api 'GET' '/dishes?stallId=1&page=1&pageSize=100'
$pv = $false
foreach ($r in (ArrList $pub.data)) { if ("$($r.id)" -eq "$PDISHID") { $pv = $true } }
if ($pub.code -eq 200 -and $pv) { OK 'approved dish public' } else { FAIL "visible=$pv" }

TestName 'G3 实体审核退回(原因必填)'
$dn2 = "API-TEST-DISH2-$suffix"
$pd2 = Api 'POST' '/dishes' @{ stallId = 2; name = $dn2; price = 900; description = 'API-TEST reject'; images = @() } $STOK
$PD2 = $pd2.data
$rj = Api 'POST' "/admin/audit/dish/$PD2/reject" @{ rejectReason = 'API-TEST reason' } $ATOK
if ($rj.code -eq 200) { OK 'reject ok' } else { FAIL "code=$($rj.code)" }

TestName 'G4 审核列表(待审)'
$gl = Api 'GET' '/admin/audit?type=dish&status=pending' $null $ATOK
if ($gl.code -eq 200) { OK 'audit list ok' } else { FAIL "code=$($gl.code)" }

TestName 'G5 广播CRUD'
$bc = Api 'POST' '/admin/broadcasts' @{ title = 'API-TEST BC'; content = 'API-TEST bc content'; broadcastType = 'NOTICE'; sortOrder = 99; status = 'enabled' } $ATOK
if ($bc.code -eq 200) { OK 'create ok' } else { FAIL "create code=$($bc.code) msg=$($bc.message)" }
$bcl = Api 'GET' '/admin/broadcasts' $null $ATOK
$bcid = $null
foreach ($r in (ArrList $bcl.data)) { if ($r.title -eq 'API-TEST BC') { $bcid = $r.id; break } }
if ($null -ne $bcid) {
    $bu = Api 'PUT' "/admin/broadcasts/$bcid" @{ title = 'API-TEST BC2' } $ATOK
    $bd = Api 'DELETE' "/admin/broadcasts/$bcid" $null $ATOK
    if ($bu.code -eq 200 -and $bd.code -eq 200) { OK 'update+delete ok' } else { FAIL "up=$($bu.code) del=$($bd.code)" }
} else { FAIL 'broadcast not found' }

TestName 'G6 分类CRUD'
$cc = Api 'POST' '/admin/categories' @{ name = 'API-TEST CAT'; sortOrder = 99; status = 'enabled' } $ATOK
if ($cc.code -eq 200) { OK 'create ok' } else { FAIL "create code=$($cc.code) msg=$($cc.message)" }
$ccl = Api 'GET' '/admin/categories' $null $ATOK
$ccid = $null
foreach ($r in (ArrList $ccl.data)) { if ($r.name -eq 'API-TEST CAT') { $ccid = $r.id; break } }
if ($null -ne $ccid) {
    $cs = Api 'PUT' "/admin/categories/$ccid/status" @{ status = 'disabled' } $ATOK
    $cd = Api 'DELETE' "/admin/categories/$ccid" $null $ATOK
    if ($cs.code -eq 200 -and $cd.code -eq 200) { OK 'status+delete ok' } else { FAIL "st=$($cs.code) del=$($cd.code)" }
} else { FAIL 'category not found' }

TestName 'G7 轮播CRUD'
$bn = Api 'POST' '/admin/banners' @{ title = 'API-TEST BN'; targetType = 'NONE'; sortOrder = 99; status = 'enabled'; images = '["uploads/images/2026/08/08/test.jpg"]' } $ATOK
if ($bn.code -eq 200) { OK 'create ok' } else { FAIL "create code=$($bn.code) msg=$($bn.message)" }
$bnl = Api 'GET' '/admin/banners' $null $ATOK
$bnid = $null
foreach ($r in (ArrList $bnl.data)) { if ($r.title -eq 'API-TEST BN') { $bnid = $r.id; break } }
if ($null -ne $bnid) {
    $bnd = Api 'DELETE' "/admin/banners/$bnid" $null $ATOK
    if ($bnd.code -eq 200) { OK 'delete ok' } else { FAIL "del code=$($bnd.code)" }
} else { FAIL 'banner not found' }

TestName 'G8 动态下架→公开不可见'
$mm5 = Api 'POST' '/moments' @{ content = 'API-TEST hide'; images = @() } $STOK
$MOMID5 = $mm5.data.id
# 先审核通过（public moments 只显示 approved+normal）
$ap5 = Api 'POST' "/admin/moments/$MOMID5/approve" $null $ATOK
$hd = Api 'PUT' "/admin/moments/$MOMID5/hide" $null $ATOK
$pubmm = Api 'GET' '/moments?page=1&pageSize=100'
$hv = $false
foreach ($r in (ArrList $pubmm.data)) { if ("$($r.id)" -eq "$MOMID5") { $hv = $true } }
if ($hd.code -eq 200 -and -not $hv) { OK 'hidden from public' } else { FAIL "hd=$($hd.code) visible=$hv" }
# 清理（删除该动态）
Api 'DELETE' "/admin/moments/$MOMID5" $null $ATOK | Out-Null

TestName 'G9 动态管理删除'
$mm6 = Api 'POST' '/moments' @{ content = 'API-TEST admindel'; images = @() } $STOK
$MOMID6 = $mm6.data.id
$adm = Api 'DELETE' "/admin/moments/$MOMID6" $null $ATOK
if ($adm.code -eq 200) { OK 'admin delete ok' } else { FAIL "code=$($adm.code)" }

TestName 'G10 评论治理(查看+删除)'
$mm7 = Api 'POST' '/moments' @{ content = 'API-TEST gov'; images = @() } $STOK
$MOMID7 = $mm7.data.id
Api 'POST' "/moments/$MOMID7/comments" @{ content = 'API-TEST govcmt' } $STOK | Out-Null
$gcl = Api 'GET' "/admin/moments/comments?momentId=$MOMID7" $null $ATOK
$gcid = $null
foreach ($r in (ArrList $gcl.data)) { if ($r.content -like '*API-TEST govcmt*') { $gcid = $r.id; break } }
if ($null -ne $gcid) {
    $gd = Api 'DELETE' "/admin/moments/comments/$gcid" $null $ATOK
    if ($gd.code -eq 200) { OK "comment $gcid deleted" } else { FAIL "del code=$($gd.code)" }
    Api 'DELETE' "/admin/moments/$MOMID7" $null $ATOK | Out-Null
} else { FAIL 'comment not found' }

TestName 'G11 管理员CRUD'
$an = "API-TEST-ADMIN-$suffix"
$ac = Api 'POST' '/admin/admins' @{ username = $an; password = '123456'; nickname = 'API-TEST Admin'; role = 'admin' } $ATOK
if ($ac.code -eq 200) { OK 'create ok' } else { FAIL "create code=$($ac.code) msg=$($ac.message)" }
$al = Api 'GET' '/admin/admins' $null $ATOK
$aid = $null
foreach ($r in (ArrList $al.data)) { if ($r.username -eq $an) { $aid = $r.id; break } }
if ($null -ne $aid) {
    $au = Api 'PUT' "/admin/admins/$aid" @{ nickname = 'API-TEST Admin2' } $ATOK
    $as = Api 'PUT' "/admin/admins/$aid/status" @{ status = 'disabled' } $ATOK
    $ad2 = Api 'DELETE' "/admin/admins/$aid" $null $ATOK
    if ($au.code -eq 200 -and $as.code -eq 200 -and $ad2.code -eq 200) { OK 'update+status+delete ok' } else { FAIL "up=$($au.code) st=$($as.code) del=$($ad2.code)" }
} else { FAIL 'admin not found' }

# ============ H. 边界/越权 ============
TestName 'H1 学生访问管理统计越权'
$h1 = Api 'GET' '/admin/stats/overview' $null $STOK
if ($h1.code -eq 403) { OK '403' } else { FAIL "code=$($h1.code)" }

TestName 'H2 上传未授权'
$h2 = Api 'POST' '/upload/image' @{ file = 'x' }
if ($h2.code -eq 401) { OK '401' } else { FAIL "code=$($h2.code)" }

TestName 'H3 学生发布菜品到不存在档口'
$h3 = Api 'POST' '/dishes' @{ stallId = 99999; name = 'API-TEST bad'; price = 1000 } $STOK
if ($h3.code -eq 400 -or $h3.code -eq 404) { OK "blocked code=$($h3.code)" } else { FAIL "code=$($h3.code)" }

TestName 'H4 重复关闭申请防重'
$d1 = Api 'POST' '/my/apply' @{ entityType = 'STALL'; entityId = 3; applyType = 'CLOSE'; payload = @{ name = 'API-TEST h4' } } $STOK
$d2 = Api 'POST' '/my/apply' @{ entityType = 'STALL'; entityId = 3; applyType = 'CLOSE'; payload = @{ name = 'API-TEST h4' } } $STOK
if ($d1.code -eq 200 -and $d2.code -eq 409) { OK 'dup blocked 409' } else { FAIL "d1=$($d1.code) d2=$($d2.code)" }

TestName 'H5 删除他人评价越权'
$h5 = Api 'DELETE' '/reviews/1' $null $STOK2
if ($h5.code -eq 403 -or $h5.code -eq 400) { OK "blocked code=$($h5.code)" } else { FAIL "code=$($h5.code)" }

TestName 'H6 删除他人动态越权'
$h6 = Api 'DELETE' '/my/moments/1' $null $STOK2
if ($h6.code -eq 403 -or $h6.code -eq 400) { OK "blocked code=$($h6.code)" } else { FAIL "code=$($h6.code)" }

# ============ 汇总 ============
"`n===== EXTENDED SUMMARY ====="
$pass = @($results | Where-Object { $_ -like 'PASS*' }).Count
$fail = @($results | Where-Object { $_ -like 'FAIL*' }).Count
$results | ForEach-Object { $_ }
"-----"
"TOTAL: $($results.Count)  PASS: $pass  FAIL: $fail"
