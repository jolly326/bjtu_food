# 椋熷湪浜ゅぇ 鈥?鍓嶇 API 鎺ュ彛鏂囨。

> 鍩虹鍦板潃锛歚http://localhost:8080/api`
>
> 缁熶竴鍝嶅簲鏍煎紡锛?
> ```json
> { "code": 200, "message": "success", "data": <T> }
> ```
>
> 閴存潈鏂瑰紡锛歚Authorization: Bearer <token>`锛堢櫥褰曞悗鎵€鏈夋帴鍙ｈ嚜鍔ㄦ惡甯︼級

---

## 鐩綍

| 妯″潡 | 鎺ュ彛 | 鏂规硶 | 鐢ㄩ€?|
|------|------|------|------|
| 璁よ瘉 | `/auth/login` | POST | 鐧诲綍/娉ㄥ唽 |
| 璁よ瘉 | `/auth/profile` | PUT | 淇敼鏄电О/澶村儚 |
| 璁よ瘉 | `/auth/stats` | GET | 鑾峰彇鐢ㄦ埛缁熻 |
| 鑿滃搧 | `/dishes/hot` | GET | 棣栭〉鎺ㄨ崘鑿滃搧 |
| 鑿滃搧 | `/dishes` | GET | 鎼滅储/绛涢€夎彍鍝?|
| 鑿滃搧 | `/dishes/:id` | GET | 鑿滃搧璇︽儏 |
| 椋熷爞 | `/canteens/banners` | GET | 棣栭〉杞挱鍥?|
| 椋熷爞 | `/canteens` | GET | 椋熷爞鍒楄〃 |
| 椋熷爞 | `/canteens/images` | GET | 椋熷爞鑳屾櫙鍥剧墖 |
| 椋熷爞 | `/canteens/stallDetail` | GET | 妗ｅ彛璇︽儏 |
| 璇勪环 | `/dishes/:id/reviews` | GET | 鑿滃搧璇勪环鍒楄〃 |
| 璇勪环 | `/reviews` | POST | 鎻愪氦璇勪环 |
| 鏀惰棌 | `/favorites` | GET | 鏀惰棌鍒楄〃 |
| 鏀惰棌 | `/favorites/toggle` | POST | 鍒囨崲鏀惰棌锛堟坊鍔?鍙栨秷锛?|
| 涓婁紶 | `/upload/image` | POST | 涓婁紶鍥剧墖 |

---

## 1. 璁よ瘉妯″潡

### 1.1 鐧诲綍 / 鑷姩娉ㄥ唽

```
POST /auth/login
```

**Request Body:**

```json
{
  "username": "20240001",
  "password": "123456"
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `username` | string | 鏄?| 瀛﹀彿 |
| `password` | string | 鏄?| 楠岃瘉鐮侊紙棣栨鐧诲綍鑷姩鍒涘缓璐﹀彿锛?|

**Response `data`:**

```json
{
  "token": "jwt_token_string",
  "userId": 1,
  "nickname": "浜ゅぇ瀛﹀瓙",
  "avatar": "http://.../avatar.jpg",
  "role": "student"
}
```

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `token` | string | JWT token锛屽悗缁帴鍙ｉ€氳繃 `Authorization: Bearer <token>` 浼犻€?|
| `userId` | number | 鐢ㄦ埛 ID |
| `nickname` | string | 鏄电О |
| `avatar` | string | 澶村儚 URL锛堝彲鑳戒负绌猴級 |
| `role` | `"student" \| "visitor"` | 瑙掕壊 |

---

### 1.2 淇敼璧勬枡

```
PUT /auth/profile
```

**Request Body**锛堜袱涓瓧娈佃嚦灏戜紶涓€涓級锛?

```json
{
  "nickname": "鏂版樀绉?,
  "avatar": "http://.../new_avatar.jpg"
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `nickname` | string | 鍚?| 鏂版樀绉帮紝鏈€澶?20 瀛?|
| `avatar` | string | 鍚?| 澶村儚鍥剧墖 URL锛?*蹇呴』鍏堥€氳繃涓婁紶鎺ュ彛鑾峰彇 URL**锛屼笉鍙洿鎺ヤ紶鏈湴涓存椂璺緞锛?|

> 鈿狅笍 **鍓嶇璋冪敤椤哄簭**锛歚uni.chooseImage` 鈫?`POST /upload/image`锛堣幏鍙?URL锛夆啋 `PUT /auth/profile`锛堜紶鍏?URL锛?

**Response `data`:** 鏇存柊鍚庣殑 `UserInfo` 瀵硅薄

```json
{
  "id": 1,
  "nickname": "鏂版樀绉?,
  "avatar": "http://.../new_avatar.jpg",
  "role": "student"
}
```

**鍓嶇绫诲瀷 `UserInfo`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `id` | number | 鐢ㄦ埛 ID |
| `nickname` | string | 鏄电О |
| `avatar` | string | 澶村儚 URL |
| `role` | `"student" \| "visitor"` | 瑙掕壊 |

---

### 1.3 鑾峰彇鐢ㄦ埛缁熻

```
GET /auth/stats
```

**Response `data`:**

```json
{
  "favoriteCount": 12,
  "reviewCount": 8
}
```

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `favoriteCount` | number | 鏀惰棌鏁?|
| `reviewCount` | number | 璇勪环鏁?|

**鍓嶇绫诲瀷 `UserStats`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `favoriteCount` | number | 鏀惰棌鏁?|
| `reviewCount` | number | 璇勪环鏁?|

---

## 2. 鑿滃搧妯″潡

### 2.1 棣栭〉鎺ㄨ崘鑿滃搧

```
GET /dishes/hot
```

鏃犲弬鏁般€?

**Response `data`:** `Dish[]`

```json
[
  {
    "id": 1,
    "name": "绾㈢儳鐗涜倝闈?,
    "price": 1500,
    "images": ["http://.../dish1.jpg"],
    "avgRating": 4.8,
    "ratingCount": 256,
    "tags": ["recommended", "signature"],
    "description": "娴撴堡鎱㈢倴锛岀墰鑲夐叆鐑?,
    "canteenName": "绗竴椋熷爞",
    "stallName": "闈㈤潰淇卞埌"
  }
]
```

---

### 2.2 鎼滅储 / 绛涢€夎彍鍝?

```
GET /dishes
```

**Query Parameters:**

| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `keyword` | string | 鍚?| 鍏抽敭璇嶆悳绱紙鍖归厤鑿滃搧鍚嶃€佹。鍙ｅ悕锛?|
| `minPrice` | number | 鍚?| 鏈€浣庝环鏍硷紙**鍒?*锛?|
| `maxPrice` | number | 鍚?| 鏈€楂樹环鏍硷紙**鍒?*锛?|
| `sortBy` | `"rating" \| "price"` | 鍚?| 鎺掑簭鏂瑰紡 |
| `page` | number | 鍚?| 鍒嗛〉椤电爜锛岄粯璁?1 |
| `pageSize` | number | 鍚?| 姣忛〉鏉℃暟锛岄粯璁?20 |

> 鍓嶇褰撳墠鐗堟湰鏆傛湭浼?`page` / `pageSize`锛屽悗绔彲鍏堥粯璁よ繑鍥炴墍鏈夊尮閰嶇粨鏋滄垨鍒嗛〉銆?

**Response `data`:** 鍒嗛〉鎴栨暟缁?

鍒嗛〉鏍煎紡锛堣嫢鏀寔鍒嗛〉锛夛細

```json
{
  "records": [ ...Dish[] ],
  "total": 100,
  "page": 1,
  "pageSize": 20
}
```

鑻ヤ笉鍒嗛〉鍒欑洿鎺ヨ繑鍥?`Dish[]`銆?

---

### 2.3 鑿滃搧璇︽儏

```
GET /dishes/:id
```

**Response `data`:**

```json
{
  "id": 1,
  "name": "绾㈢儳鐗涜倝闈?,
  "price": 1500,
  "images": ["http://.../img1.jpg", "http://.../img2.jpg"],
  "avgRating": 4.8,
  "ratingCount": 256,
  "collectCount": 120,
  "tags": ["recommended", "signature"],
  "description": "娴撴堡鎱㈢倴锛岀墰鑲夐叆鐑?,
  "canteenName": "绗竴椋熷爞",
  "stallName": "闈㈤潰淇卞埌",
  "ratingDistribution": [
    { "star": 5, "count": 156 },
    { "star": 4, "count": 68 },
    { "star": 3, "count": 22 },
    { "star": 2, "count": 8 },
    { "star": 1, "count": 2 }
  ]
}
```

**鍓嶇绫诲瀷 `Dish`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `id` | number | 鑿滃搧 ID |
| `name` | string | 鑿滃搧鍚?|
| `price` | number | **鍗曚綅锛氬垎**锛屽墠绔樉绀烘椂 /100 杞负鍏?|
| `images` | string[] | 图片 URL 列表；单图时仅包含 1 个 URL |
| `avgRating` / `rating` | number | 璇勫垎锛堝墠绔紭鍏堝彇 `avgRating`锛?|
| `ratingCount` | number | 璇勪环鏁?|
| `collectCount` / `favoriteCount` | number | 鏀惰棌鏁?|
| `tags` | string[] | 鏍囩鏁扮粍锛屽悗绔紶鑻辨枃 key锛堣涓嬫柟鏄犲皠琛級 |
| `description` | string | 鎻忚堪 |
| `canteenName` / `canteen` | string | 鎵€灞為鍫?|
| `stallName` | string | 鎵€灞炴。鍙?|

**鏍囩鏄犲皠琛紙鍚庣 鈫?鍓嶇鏄剧ず锛夛細**

| 鍚庣鍊?| 鍓嶇鏄剧ず |
|--------|----------|
| `recommended` | 蹇呭悆鎺ㄨ崘 |
| `signature` | 鎷涚墝鑿?|

**璇︽儏棰濆瀛楁 `DishDetail`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `ratingDistribution` | `{ star: number, count: number }[]` | 鍚勬槦绾т汉鏁帮紙1-5 鏄熷悇涓€涓級 |

---

## 3. 椋熷爞妯″潡

### 3.1 棣栭〉杞挱鍥?

```
GET /canteens/banners
```

**Response `data`:** `BannerItem[]`

```json
[
  {
    "title": "馃崪 浜ゅぇ缇庨瀛?,
    "subtitle": "鍙戠幇鏍″洯閲岀殑姣忎竴閬撶編鍛?,
    "images": ["http://.../banner1.jpg"]
  }
]
```

**鍓嶇绫诲瀷 `BannerItem`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `title` | string | 鏍囬 |
| `subtitle` | string | 鍓爣棰?|
| `images` | string[] | 图片 URL 列表；单图时仅包含 1 个 URL |

---

### 3.2 椋熷爞鍒楄〃

```
GET /canteens
```

**Response `data`:** `CanteenInfo[]`

```json
[
  {
    "name": "绗竴椋熷爞",
    "description": "涓€椋熷爞涓€灞?,
    "images": ["http://.../canteen_icon.svg"]
  }
]
```

**鍓嶇绫诲瀷 `CanteenInfo`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `name` | string | 椋熷爞鍚嶇О |
| `location` / `description` | string | 浣嶇疆鎻忚堪 |
| `images` | string[] | 食堂图片 URL 列表；单图时仅包含 1 个 URL |

> 鍓嶇浼樺厛鍙?`description`锛屽彇涓嶅埌鍒欏彇 `location`銆?

---

### 3.3 椋熷爞鑳屾櫙鍥剧墖

```
GET /canteens/images
```

**Response `data`:** `Record<string, string[]>` - key 为食堂名，value 为图片 URL 数组

```json
{
  "绗竴椋熷爞": ["http://.../canteen1_bg.jpg"],
  "绗簩椋熷爞": ["http://.../canteen2_bg.jpg"]
}
```

> 鍓嶇鐢ㄦ鎺ュ彛鑾峰彇椋熷爞椤电殑鑳屾櫙鍥剧墖锛岃嫢鏃犺繑鍥炲垯浣跨敤榛樿鍗犱綅銆?

---

### 3.4 妗ｅ彛璇︽儏

```
GET /canteens/stallDetail
```

**Query Parameters:**

| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `canteen` | string | 鏄?| 椋熷爞鍚嶇О |
| `stallName` | string | 鏄?| 妗ｅ彛鍚嶇О |

**Response `data`:** `StallDetail`

```json
{
  "name": "闈㈤潰淇卞埌",
  "images": ["http://.../stall1.jpg", "http://.../stall2.jpg"],
  "location": "绗竴椋熷爞",
  "description": "绗竴椋熷爞路闈㈤潰淇卞埌锛屼负鎮ㄦ彁渚涚編鍛崇殑鏍″洯椁愰ギ浣撻獙銆?
}
```

**鍓嶇绫诲瀷 `StallDetail`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `name` | string | 妗ｅ彛鍚嶇О |
| `images` | string[] | 妗ｅ彛灞曠ず鍥剧墖锛堟敮鎸佸寮狅級 |
| `location` | string | 浣嶇疆 |
| `description` | string | 鎻忚堪鏂囨 |

---

## 4. 璇勪环妯″潡

### 4.1 鑾峰彇鑿滃搧璇勪环鍒楄〃

```
GET /dishes/:dishId/reviews
```

**Query Parameters:**

| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `page` | number | 鍚?| 椤电爜锛岄粯璁?1 |
| `pageSize` | number | 鍚?| 姣忛〉鏉℃暟锛岄粯璁?20 |

**Response `data`:**

鍒嗛〉鏍煎紡锛?

```json
{
  "records": [ ...Review[] ],
  "total": 10,
  "page": 1,
  "pageSize": 20
}
```

鎴栦笉鍒嗛〉鐩存帴杩斿洖 `Review[]`銆?

**鍓嶇绫诲瀷 `Review`锛?*

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `id` | number | 璇勪环 ID |
| `userId` | number | 鐢ㄦ埛 ID |
| `userNickname` | string | 鐢ㄦ埛鏄电О锛堝墠绔?fallback 涓?鍖垮悕鐢ㄦ埛"锛?|
| `userAvatar` | string | 鐢ㄦ埛澶村儚 URL锛堝彲涓虹┖锛?|
| `dishId` | number | 鍏宠仈鑿滃搧 ID |
| `rating` | number | 璇勫垎锛?-5锛?|
| `content` | string | 璇勪环鍐呭 |
| `images` | string[] | 璇勪环鍥剧墖 URL 鍒楄〃 |
| `createdAt` | string | 鍒涘缓鏃堕棿锛圛SO 8601 鏍煎紡锛?|

---

### 4.2 鎻愪氦璇勪环

```
POST /reviews
```

**Request Body:**

```json
{
  "dishId": 1,
  "rating": 5,
  "content": "瓒呯骇濂藉悆锛?,
  "images": ["http://.../review_img1.jpg"]
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `dishId` | number | 鏄?| 鑿滃搧 ID |
| `rating` | number | 鏄?| 璇勫垎锛?-5锛?|
| `content` | string | 鏄?| 姝ｆ枃锛屾渶闀?500 瀛?|
| `images` | string[] | 鍚?| 鍥剧墖 URL 鍒楄〃锛堟渶澶?3 寮狅級 |

**Response `data`:** 鏃?`data`锛屼粎杩斿洖 `{ code: 200, message: "success" }`銆?

> 鈿狅笍 **鍓嶇璋冪敤椤哄簭**锛歚uni.chooseImage` 鈫?`POST /upload/image`锛堥€愪竴涓婁紶鑾峰彇 URL锛夆啋 `POST /reviews`锛堜紶鍏?URL 鏁扮粍锛?


---

## 5. 鏀惰棌妯″潡

### 5.1 鑾峰彇鏀惰棌鍒楄〃

```
GET /favorites
```

**Query Parameters:**

| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `page` | number | 鍚?| 椤电爜锛岄粯璁?1 |
| `pageSize` | number | 鍚?| 姣忛〉鏉℃暟锛岄粯璁?50 |

**Response `data`:** `Dish[]`锛堜笌鑿滃搧妯″潡鐨?`Dish` 绫诲瀷涓€鑷达級

```json
[
  {
    "id": 1,
    "name": "绾㈢儳鐗涜倝闈?,
    "price": 1500,
    "images": ["http://.../dish1.jpg"],
    ...
  }
]
```

---

### 5.2 鍒囨崲鏀惰棌

```
POST /favorites/toggle
```

**Request Body:**

```json
{
  "dishId": 1
}
```

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `dishId` | number | 鏄?| 鑿滃搧 ID |

**琛屼负璇存槑锛?* 鏈嶅姟绔仛 toggle锛堣嫢宸叉敹钘忓垯鍙栨秷锛屾湭鏀惰棌鍒欐坊鍔狅級銆傚墠绔皟鐢ㄥ悗閲嶆柊鎷夊彇鏀惰棌鍒楄〃銆?

**Response `data`:** 鏃?`data`锛屼粎杩斿洖 `{ code: 200, message: "success" }`銆?

> 鈿狅笍 **鍓嶇璋冪敤椤哄簭**锛歚uni.chooseImage` 鈫?`POST /upload/image`锛堥€愪竴涓婁紶鑾峰彇 URL锛夆啋 `POST /reviews`锛堜紶鍏?URL 鏁扮粍锛?


---

## 6. 涓婁紶妯″潡

### 6.1 涓婁紶鍥剧墖

```
POST /upload/image
Content-Type: multipart/form-data
```

**Form Data:**

| 瀛楁 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `file` | file | 鏄?| 鍥剧墖鏂囦欢 |

**Response `data`:**

```json
{
  "url": "http://.../uploads/2024/01/xxx.jpg"
}
```

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `url` | string | 涓婁紶鍚庡彲璁块棶鐨勫畬鏁村浘鐗?URL |

---

## 闄勫綍

### A. 浠锋牸鍗曚綅绾﹀畾

鎵€鏈夋帴鍙ｄ腑浠锋牸瀛楁 **浠ャ€屽垎銆嶄负鍗曚綅**锛屽墠绔湪鏄剧ず鏃?`/100` 杞崲涓恒€屽厓銆嶃€?

| 鎺ュ彛瀛楁 | 鍗曚綅 | 绀轰緥鍊?| 鍓嶇鏄剧ず |
|---------|------|--------|---------|
| `Dish.price` | 鍒?| 1500 | 楼15 |
| 鎼滅储鍙傛暟 `minPrice` / `maxPrice` | 鍒?| 1000 | 鍓嶇鍏堣浆涓哄垎鍐嶄紶鍙?|

### B. 閫氱敤鍝嶅簲閿欒鐮?

| `code` | 璇存槑 |
|--------|------|
| 200 | 鎴愬姛 |
| 400 | 璇锋眰鍙傛暟閿欒 |
| 401 | 鏈櫥褰?/ token 杩囨湡 |
| 403 | 鏃犳潈闄?|
| 404 | 璧勬簮涓嶅瓨鍦?|
| 500 | 鏈嶅姟绔唴閮ㄩ敊璇?|

### C. 鏍囩鏋氫妇

璇峰悗绔寜浠ヤ笅鏋氫妇鍊艰繑鍥?`tags` 瀛楁锛?

| 鍊?| 鍚箟 |
|----|------|
| `recommended` | 蹇呭悆鎺ㄨ崘 |
| `signature` | 鎷涚墝鑿?|
