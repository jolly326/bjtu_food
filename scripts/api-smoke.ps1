# =============================================================
# 食在交大 接口层自动化测试 v2（真实路径，依据前端 api 定义）
# =============================================================
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
$results = @()
$cur = ''

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

# ============ A. 认证与权限 ============
TestName 'A1 学生登录'
$s = Api 'POST' '/auth/login' @{ account = '2024001'; password = '123456' }
if ($s.code -eq 200 -and $s.data.role -eq 'student') { OK "student token" } else { FAIL "code=$($s.code)" }
$STOK = $s.data.token

TestName 'A2 管理员登录'
$a = Api 'POST' '/auth/login' @{ account = 'admin'; password = '123456' }
if ($a.code -eq 200 -and $a.data.role -eq 'super_admin') { OK "super_admin token" } else { FAIL "code=$($a.code)" }
$ATOK = $a.data.token

TestName 'A3 错误密码'
$bad = Api 'POST' '/auth/login' @{ account = 'admin'; password = 'wrong' }
if ($bad.code -eq 400) { OK "400" } else { FAIL "code=$($bad.code)" }

TestName 'A4 游客访问公开接口'
$pub = Api 'GET' '/canteens'
if ($pub.code -eq 200) { OK "public 200" } else { FAIL "code=$($pub.code)" }

TestName 'A5 游客访问需登录接口'
$me = Api 'GET' '/profile'
if ($me.code -eq 401) { OK "401" } else { FAIL "code=$($me.code)" }

TestName 'A6 学生访问 /admin 越权'
$adminH = Api 'GET' '/admin/canteens' $null $STOK
if ($adminH.code -eq 403) { OK "403" } else { FAIL "code=$($adminH.code)" }

TestName 'A7 学生访问 /admin/apply 越权'
$applyH = Api 'GET' '/admin/apply?status=pending' $null $STOK
if ($applyH.code -eq 403) { OK "403" } else { FAIL "code=$($applyH.code)" }

# ============ B. 公开接口 ============
TestName 'B1 首页轮播'
$b = Api 'GET' '/canteens/banners'
if ($b.code -eq 200 -and (ArrCount $b.data) -ge 1) { OK "banners=$(ArrCount $b.data)" } else { FAIL "code=$($b.code)" }

TestName 'B2 食堂列表(7)'
$c = Api 'GET' '/canteens'
if ($c.code -eq 200 -and (ArrCount $c.data) -eq 7) { OK "canteens=7" } else { FAIL "n=$(ArrCount $c.data)" }

TestName 'B3 距离排序'
$d = Api 'GET' '/canteens?lat=39.953800&lng=116.335400'
if ($d.code -eq 200 -and $null -ne $d.data[0].distance) { OK "distance present" } else { FAIL "code=$($d.code)" }

TestName 'B4 食堂含档口'
$all = Api 'GET' '/canteens/all'
if ($all.code -eq 200 -and $null -ne $all.data[0].stalls) { OK "stalls embedded" } else { FAIL "code=$($all.code)" }

TestName 'B5 档口列表(学一)'
$st = Api 'GET' '/stalls?canteenId=1'
if ($st.code -eq 200 -and (ArrCount $st.data) -ge 1) { OK "stalls=$(ArrCount $st.data)" } else { FAIL "code=$($st.code)" }

TestName 'B6 菜品列表(档口1)'
$di = Api 'GET' '/dishes?stallId=1&page=1&pageSize=50'
if ($di.code -eq 200 -and (ArrCount $di.data) -ge 1) { OK "dishes=$(ArrCount $di.data)" } else { FAIL "code=$($di.code) n=$(ArrCount $di.data)" }

TestName 'B7 分类宫格'
$cat = Api 'GET' '/categories'
if ($cat.code -eq 200 -and (ArrCount $cat.data) -eq 8) { OK "categories=8" } else { FAIL "n=$(ArrCount $cat.data)" }

TestName 'B8 广播条'
$bc = Api 'GET' '/broadcasts'
if ($bc.code -eq 200 -and (ArrCount $bc.data) -eq 3) { OK "broadcasts=3" } else { FAIL "n=$(ArrCount $bc.data)" }

TestName 'B9 动态列表(仅approved)'
$mm = Api 'GET' '/moments?page=1&pageSize=50'
if ($mm.code -eq 200 -and (ArrCount $mm.data) -ge 1) { OK "moments=$(ArrCount $mm.data)" } else { FAIL "code=$($mm.code)" }

TestName 'B10 菜品详情'
$det = Api 'GET' '/dishes/1'
if ($det.code -eq 200 -and $det.data.name) { OK "dish#1 ok" } else { FAIL "code=$($det.code)" }

TestName 'B11 菜品评价列表'
$rv = Api 'GET' '/reviews?dishId=1&page=1&pageSize=50'
if ($rv.code -eq 200 -and (ArrCount $rv.data) -ge 1) { OK "reviews=$(ArrCount $rv.data)" } else { FAIL "code=$($rv.code)" }

# ============ C. 学生写操作 ============
TestName 'C1 发表评价(dish10)'
$rv1 = Api 'POST' '/reviews' @{ dishId = 10; rating = 5; content = 'API-TEST review'; images = @() } $STOK
if ($rv1.code -eq 200) { OK "submitted" } else { FAIL "code=$($rv1.code) msg=$($rv1.message)" }

TestName 'C2 重复评价唯一键(dish10)'
$rv2 = Api 'POST' '/reviews' @{ dishId = 10; rating = 4; content = 'API-TEST dup'; images = @() } $STOK
if ($rv2.code -eq 400) { OK "400 blocked" } else { FAIL "code=$($rv2.code)" }

TestName 'C3 评价有用切换(on)'
$u1 = Api 'POST' '/reviews/1/useful' $null $STOK
if ($u1.code -eq 200 -and $u1.data.useful -eq $true) { OK "useful=true" } else { FAIL "code=$($u1.code) useful=$($u1.data.useful)" }

TestName 'C4 评价有用切换(off)'
$u2 = Api 'POST' '/reviews/1/useful' $null $STOK
if ($u2.code -eq 200 -and $u2.data.useful -eq $false) { OK "useful=false" } else { FAIL "code=$($u2.code)" }

TestName 'C5 发布动态'
$mom = Api 'POST' '/moments' @{ content = 'API-TEST moment'; images = @() } $STOK
if ($mom.code -eq 200 -and $null -ne $mom.data.id) { OK "moment id=$($mom.data.id)" } else { FAIL "code=$($mom.code)" }
$MOMID = $mom.data.id

TestName 'C6 待审核动态对外不可见'
$mm2 = Api 'GET' '/moments?page=1&pageSize=50'
$visible = $false
foreach ($r in (ArrList $mm2.data)) { if ("$($r.id)" -eq "$MOMID") { $visible = $true } }
if (-not $visible) { OK "pending hidden" } else { FAIL "pending visible" }

TestName 'C7 动态评论'
$cm = Api 'POST' "/moments/$MOMID/comments" @{ content = 'API-TEST comment' } $STOK
if ($cm.code -eq 200) { OK "comment ok" } else { FAIL "code=$($cm.code)" }

TestName 'C8 提交反馈'
$fb = Api 'POST' '/feedback' @{ type = 'suggestion'; content = 'API-TEST feedback'; contact = '2024001@bjtu.edu.cn' } $STOK
if ($fb.code -eq 200) { OK "submitted" } else { FAIL "code=$($fb.code) msg=$($fb.message)" }

TestName 'C9 提交菜品申请(NEW)'
$ap1 = Api 'POST' '/my/apply' @{ entityType = 'DISH'; applyType = 'NEW'; payload = @{ name = 'API-TEST Dish'; price = 1500; description = 'test'; stall_id = 1 } } $STOK
if ($ap1.code -eq 200 -and $null -ne $ap1.data.id) { OK "apply id=$($ap1.data.id)" } else { FAIL "code=$($ap1.code) msg=$($ap1.message)" }
$AP1ID = $ap1.data.id

TestName 'C10 提交档口关闭申请(CLOSE)'
$ap2 = Api 'POST' '/my/apply' @{ entityType = 'STALL'; entityId = 2; applyType = 'CLOSE'; payload = @{ name = 'API-TEST close' } } $STOK
if ($ap2.code -eq 200 -and $null -ne $ap2.data.id) { OK "apply id=$($ap2.data.id)" } else { FAIL "code=$($ap2.code) msg=$($ap2.message)" }
$AP2ID = $ap2.data.id

TestName 'C11 我的评价'
$mr = Api 'GET' '/my/reviews' $null $STOK
if ($mr.code -eq 200 -and (ArrCount $mr.data) -ge 1) { OK "n=$(ArrCount $mr.data)" } else { FAIL "code=$($mr.code)" }

TestName 'C12 我的动态'
$mm3 = Api 'GET' '/my/moments' $null $STOK
if ($mm3.code -eq 200 -and (ArrCount $mm3.data) -ge 1) { OK "n=$(ArrCount $mm3.data)" } else { FAIL "code=$($mm3.code)" }

TestName 'C13 我的提交聚合'
$sub = Api 'GET' '/my/submissions' $null $STOK
if ($sub.code -eq 200 -and (ArrCount $sub.data) -ge 1) { OK "n=$(ArrCount $sub.data)" } else { FAIL "code=$($sub.code)" }

# ============ D. Web 管理接口 ============
TestName 'D1 工作台'
$dash = Api 'GET' '/admin/stats/overview' $null $ATOK
if ($dash.code -eq 200) { OK "overview 200" } else { FAIL "code=$($dash.code)" }

TestName 'D2 食堂管理'
$mc = Api 'GET' '/admin/canteens' $null $ATOK
if ($mc.code -eq 200 -and (ArrCount $mc.data) -eq 7) { OK "canteens=7" } else { FAIL "n=$(ArrCount $mc.data)" }

TestName 'D3 菜品管理'
$md = Api 'GET' '/admin/dishes' $null $ATOK
if ($md.code -eq 200 -and (ArrCount $md.data) -ge 30) { OK "dishes=$(ArrCount $md.data)" } else { FAIL "n=$(ArrCount $md.data)" }

TestName 'D4 评价审核列表'
$mr2 = Api 'GET' '/admin/reviews?page=1&pageSize=200' $null $ATOK
if ($mr2.code -eq 200 -and $mr2.data.total -ge 20) { OK "reviews total=$($mr2.data.total)" } else { FAIL "total=$($mr2.data.total)" }

TestName 'D5 申请待办(action别名)'
$ma = Api 'GET' '/admin/apply?status=pending&action=NEW' $null $ATOK
if ($ma.code -eq 200 -and $ma.data.total -ge 2) { OK "apply total=$($ma.data.total)" } else { FAIL "total=$($ma.data.total)" }

TestName 'D6 反馈待办'
$mf = Api 'GET' '/admin/feedbacks?status=pending' $null $ATOK
if ($mf.code -eq 200 -and $mf.data.total -ge 5) { OK "feedbacks pending=$($mf.data.total)" } else { FAIL "total=$($mf.data.total)" }

TestName 'D7 学生账号列表'
$mu = Api 'GET' '/admin/users?page=1&pageSize=200' $null $ATOK
if ($mu.code -eq 200 -and $mu.data.total -ge 5) { OK "users total=$($mu.data.total)" } else { FAIL "total=$($mu.data.total)" }

TestName 'D8 管理员列表'
$mad = Api 'GET' '/admin/admins' $null $ATOK
if ($mad.code -eq 200) { OK "admins ok" } else { FAIL "code=$($mad.code)" }

TestName 'D9 动态管理'
$mdm = Api 'GET' '/admin/moments?page=1&pageSize=50' $null $ATOK
if ($mdm.code -eq 200 -and $mdm.data.total -ge 1) { OK "moments total=$($mdm.data.total)" } else { FAIL "code=$($mdm.code)" }

TestName 'D10 操作日志'
$mlog = Api 'GET' '/admin/operation-logs?page=1&pageSize=20' $null $ATOK
if ($mlog.code -eq 200) { OK "logs 200" } else { FAIL "code=$($mlog.code)" }

# ============ E. 管理写操作与闭环 ============
TestName 'E1 申请退回(原因必填, 正确路径)'
$rj = Api 'POST' "/admin/apply/$AP1ID/reject" @{ rejectReason = 'API-TEST reject reason' } $ATOK
if ($rj.code -eq 200) { OK "reject ok" } else { FAIL "code=$($rj.code)" }

TestName 'E2 退回无原因被拒'
$rj2 = Api 'POST' "/admin/apply/$AP2ID/reject" @{} $ATOK
if ($rj2.code -eq 400) { OK "400 no reason" } else { FAIL "code=$($rj2.code)" }

TestName 'E3 评价隐藏→公开不可见'
$hid = Api 'PUT' '/admin/reviews/1/hide' @{ hidden = $true } $ATOK
$pubRv = Api 'GET' '/reviews?dishId=1&page=1&pageSize=50'
$hiddenVisible = $false
foreach ($r in (ArrList $pubRv.data)) { if ("$($r.id)" -eq '1') { $hiddenVisible = $true } }
if ($hid.code -eq 200 -and -not $hiddenVisible) { OK "review#1 hidden" } else { FAIL "hid=$($hid.code) visible=$hiddenVisible" }
Api 'PUT' '/admin/reviews/1/hide' @{ hidden = $false } $ATOK | Out-Null

TestName 'E4 反馈处理'
$fbList = Api 'GET' '/admin/feedbacks?status=pending&page=1&pageSize=50' $null $ATOK
$fbTarget = $null
foreach ($r in (ArrList $fbList.data)) { if ($r.content -like '*API-TEST feedback*') { $fbTarget = $r; break } }
if ($null -ne $fbTarget) {
    $hd = Api 'PUT' "/admin/feedbacks/$($fbTarget.id)" @{ status = 'handled'; reply = 'API-TEST reply' } $ATOK
    if ($hd.code -eq 200) { OK "feedback handled" } else { FAIL "code=$($hd.code)" }
} else { FAIL "API-TEST feedback not found" }

TestName 'E5 菜品下架→公开不可见'
$off = Api 'PUT' '/admin/dishes/1' @{ status = 'off' } $ATOK
$pubDish = Api 'GET' '/dishes?stallId=1&page=1&pageSize=50'
$offVisible = $false
foreach ($r in (ArrList $pubDish.data)) { if ("$($r.id)" -eq '1') { $offVisible = $true } }
if ($off.code -eq 200 -and -not $offVisible) { OK "dish#1 offline" } else { FAIL "off=$($off.code) visible=$offVisible" }
Api 'PUT' '/admin/dishes/1' @{ status = 'on' } $ATOK | Out-Null

TestName 'E6 学生禁用→再登录被拒'
$usrList = Api 'GET' '/admin/users?page=1&pageSize=200' $null $ATOK
$usr = $null
foreach ($r in (ArrList $usrList.data)) { if ($r.username -eq '2024003') { $usr = $r; break } }
if ($null -ne $usr) {
    $dis = Api 'PUT' "/admin/users/$($usr.id)/status" @{ status = 'disabled' } $ATOK
    $relog = Api 'POST' '/auth/login' @{ account = '2024003'; password = '123456' }
    if ($dis.code -eq 200 -and $relog.code -eq 400) { OK "disabled+login blocked" } else { FAIL "dis=$($dis.code) relog=$($relog.code)" }
    Api 'PUT' "/admin/users/$($usr.id)/status" @{ status = 'active' } $ATOK | Out-Null
} else { FAIL "2024003 not found" }

TestName 'E7 操作日志落库'
$mlog2 = Api 'GET' '/admin/operation-logs?page=1&pageSize=50' $null $ATOK
if ($mlog2.code -eq 200 -and $mlog2.data.total -ge 1) { OK "logs total=$($mlog2.data.total)" } else { FAIL "code=$($mlog2.code) total=$($mlog2.data.total)" }

# ============ 汇总 ============
"`n===== SUMMARY ====="
$pass = @($results | Where-Object { $_ -like 'PASS*' }).Count
$fail = @($results | Where-Object { $_ -like 'FAIL*' }).Count
$results | ForEach-Object { $_ }
"-----"
"TOTAL: $($results.Count)  PASS: $pass  FAIL: $fail"
