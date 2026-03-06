# 推荐奖励系统（Referral Program）设计文档

> Personal English AI — 邀请返利模块技术设计
> 版本：v1.0 | 最后更新：2026-03-06

---

## 目录

1. [功能概述](#1-功能概述)
2. [核心流程](#2-核心流程)
3. [数据库设计（DDL）](#3-数据库设计ddl)
4. [API 接口契约](#4-api-接口契约)
5. [前端页面与交互设计](#5-前端页面与交互设计)
6. [防刷风控规则](#6-防刷风控规则)
7. [可配置业务参数](#7-可配置业务参数)
8. [与现有系统的集成点](#8-与现有系统的集成点)

---

## 1. 功能概述

### 1.1 产品定位

用户（学生）通过邀请链接或邀请码邀请好友注册并付费，邀请人获得被邀请人首次付费金额一定比例的平台积分，积分可用于抵扣会员费。

### 1.2 核心规则

- 分销层级：一级（A 邀请 B，仅 A 获得奖励）
- 奖励触发：被邀请人首次付费成功时
- 奖励形式：平台积分/余额，可抵扣会员费
- 邀请方式：邀请链接（主推）+ 邀请码（兜底）
- 业务参数（返利比例、积分抵扣上限等）均为可配置项，存储于系统配置表

### 1.3 角色说明

| 角色 | 说明 |
|------|------|
| 邀请人（Referrer） | 发起邀请的已注册用户 |
| 被邀请人（Invitee） | 通过邀请链接/码注册的新用户 |
| 管理员 | 后台查看推荐数据、调整配置、处理异常 |

---

## 2. 核心流程

### 2.1 邀请链路

```
邀请人获取邀请码/链接
        ↓
分享至微信/QQ/复制邀请码
        ↓
被邀请人点击链接（自动携带 refCode）
  或 注册页手动输入邀请码
        ↓
被邀请人注册（绑定邀请关系，状态: REGISTERED）
        ↓
被邀请人首次付费成功
        ↓
系统计算奖励积分 = 付费金额 × 返利比例
        ↓
风控校验（通过 → 发放积分；不通过 → 标记待审核）
        ↓
邀请人积分到账，可用于抵扣会员费
```

### 2.2 状态机

邀请关系的状态流转：

```
REGISTERED → PAID → REWARDED
                 ↘ REVIEW（触发风控）→ REWARDED / REJECTED
```

| 状态 | 含义 |
|------|------|
| REGISTERED | 被邀请人已注册，尚未付费 |
| PAID | 被邀请人已首次付费，奖励待计算 |
| REWARDED | 积分已发放至邀请人账户 |
| REVIEW | 触发风控规则，待人工审核 |
| REJECTED | 审核拒绝，判定为作弊 |

---

## 3. 数据库设计（DDL）

### 3.1 邀请码表 `referral_code`

每个用户拥有一个唯一邀请码，同时作为链接参数和手动输入码使用。

```sql
CREATE TABLE referral_code (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL COMMENT '邀请人用户ID',
    code            VARCHAR(16)     NOT NULL COMMENT '邀请码（如 ENG_A3K9）',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '1-有效 0-停用',
    total_invited   INT             NOT NULL DEFAULT 0 COMMENT '累计邀请注册人数',
    total_rewarded  INT             NOT NULL DEFAULT 0 COMMENT '累计获得奖励次数',
    total_points    BIGINT          NOT NULL DEFAULT 0 COMMENT '累计获得积分（分为单位）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码表';
```

### 3.2 邀请关系表 `referral_relation`

记录每一对邀请关系及其状态。

```sql
CREATE TABLE referral_relation (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    referrer_id     BIGINT          NOT NULL COMMENT '邀请人用户ID',
    invitee_id      BIGINT          NOT NULL COMMENT '被邀请人用户ID',
    ref_code        VARCHAR(16)     NOT NULL COMMENT '使用的邀请码',
    channel         VARCHAR(16)     NOT NULL DEFAULT 'LINK' COMMENT '来源渠道: LINK-链接 CODE-手动输入',
    status          VARCHAR(16)     NOT NULL DEFAULT 'REGISTERED' COMMENT '状态: REGISTERED/PAID/REWARDED/REVIEW/REJECTED',
    registered_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '被邀请人注册时间',
    paid_at         DATETIME        NULL COMMENT '被邀请人首次付费时间',
    rewarded_at     DATETIME        NULL COMMENT '积分发放时间',
    payment_amount  BIGINT          NULL COMMENT '被邀请人首次付费金额（分为单位）',
    reward_points   BIGINT          NULL COMMENT '发放的奖励积分（分为单位）',
    reward_rate     DECIMAL(5,4)    NULL COMMENT '实际使用的返利比例（快照）',
    review_reason   VARCHAR(256)    NULL COMMENT '触发风控的原因',
    reviewed_by     BIGINT          NULL COMMENT '审核人（管理员ID）',
    reviewed_at     DATETIME        NULL COMMENT '审核时间',
    ip_address      VARCHAR(45)     NULL COMMENT '被邀请人注册IP',
    device_id       VARCHAR(64)     NULL COMMENT '被邀请人设备指纹',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invitee (invitee_id),
    INDEX idx_referrer_status (referrer_id, status),
    INDEX idx_ref_code (ref_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请关系表';
```

### 3.3 积分账户表 `points_account`

用户积分钱包，独立于邀请模块，后续其他积分来源也可复用。

```sql
CREATE TABLE points_account (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    balance         BIGINT          NOT NULL DEFAULT 0 COMMENT '可用积分余额（分为单位）',
    total_earned    BIGINT          NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    total_spent     BIGINT          NOT NULL DEFAULT 0 COMMENT '累计消费积分',
    frozen          BIGINT          NOT NULL DEFAULT 0 COMMENT '冻结积分（审核中）',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账户表';
```

### 3.4 积分流水表 `points_transaction`

每一笔积分变动的明细记录，用于审计和对账。

```sql
CREATE TABLE points_transaction (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    type            VARCHAR(32)     NOT NULL COMMENT '类型: REFERRAL_REWARD/MEMBERSHIP_DEDUCT/ADMIN_ADJUST/EXPIRE',
    amount          BIGINT          NOT NULL COMMENT '变动数量（正数入账，负数扣减）',
    balance_after   BIGINT          NOT NULL COMMENT '变动后余额',
    ref_id          BIGINT          NULL COMMENT '关联业务ID（如 referral_relation.id 或订单ID）',
    ref_type        VARCHAR(32)     NULL COMMENT '关联业务类型: REFERRAL/ORDER/ADMIN',
    description     VARCHAR(256)    NULL COMMENT '描述',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';
```

### 3.5 ER 关系简述

```
user (1) ──── (1) referral_code      一个用户一个邀请码
user (1) ──── (N) referral_relation   一个用户可邀请多人（作为 referrer）
user (1) ──── (1) referral_relation   一个用户只能被邀请一次（作为 invitee）
user (1) ──── (1) points_account      一个用户一个积分账户
user (1) ──── (N) points_transaction  一个用户多条积分流水
```

---

## 4. API 接口契约

### 4.1 通用约定

- Base URL: `/api/v1`
- 认证: JWT Bearer Token（除注册接口外均需登录）
- 积分金额单位: 分（1 积分 = 1 分钱，100 积分 = 1 元抵扣额度）
- 响应格式:

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

- 错误码:

| code | HTTP Status | 说明 |
|------|-------------|------|
| 200 | 200 | 成功 |
| 40001 | 400 | 邀请码无效或已停用 |
| 40002 | 400 | 不能邀请自己 |
| 40003 | 400 | 该用户已被其他人邀请 |
| 40004 | 400 | 邀请码已达上限 |
| 40101 | 401 | 未登录 |
| 40301 | 403 | 无权限 |
| 42901 | 429 | 操作过于频繁 |
| 50001 | 500 | 积分发放失败 |

---

### 4.2 用户端接口

#### 4.2.1 获取我的邀请码与邀请链接

首次调用时自动生成邀请码。

```
GET /api/v1/referral/my-code
```

**Response 200:**

```json
{
    "code": 200,
    "data": {
        "refCode": "ENG_A3K9",
        "inviteLink": "https://xxx.com/register?ref=ENG_A3K9",
        "totalInvited": 5,
        "totalRewarded": 3,
        "totalPoints": 8940
    }
}
```

#### 4.2.2 获取我的邀请记录

```
GET /api/v1/referral/my-invitations?page=1&size=10&status=REWARDED
```

**Query Params:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 10，最大 50 |
| status | string | 否 | 按状态筛选: REGISTERED / PAID / REWARDED |

**Response 200:**

```json
{
    "code": 200,
    "data": {
        "total": 5,
        "pages": 1,
        "list": [
            {
                "inviteeNickname": "小明",
                "inviteeAvatar": "https://...",
                "channel": "LINK",
                "status": "REWARDED",
                "registeredAt": "2026-03-01T10:00:00",
                "paidAt": "2026-03-02T14:30:00",
                "rewardPoints": 2980
            }
        ]
    }
}
```

#### 4.2.3 验证邀请码（注册页调用）

```
GET /api/v1/referral/validate?code=ENG_A3K9
```

**Response 200（有效）:**

```json
{
    "code": 200,
    "data": {
        "valid": true,
        "referrerNickname": "学霸小王"
    }
}
```

**Response 200（无效）:**

```json
{
    "code": 200,
    "data": {
        "valid": false,
        "reason": "邀请码不存在或已停用"
    }
}
```

#### 4.2.4 注册时绑定邀请关系

不新增接口，在现有注册接口追加可选参数：

```
POST /api/v1/auth/register
```

**Request Body 新增字段:**

```json
{
    "...现有字段...",
    "refCode": "ENG_A3K9",
    "channel": "LINK"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refCode | string | 否 | 邀请码，为空则无邀请关系 |
| channel | string | 否 | 来源: LINK（URL 参数带入）/ CODE（手动输入），默认 LINK |

**处理逻辑:**
1. 校验 refCode 有效性（存在、未停用、未达上限）
2. 校验不能自己邀请自己
3. 注册成功后写入 `referral_relation`（status = REGISTERED）
4. 更新 `referral_code.total_invited` + 1
5. 绑定失败不阻塞注册（静默失败，记录日志）

#### 4.2.5 查询我的积分账户

```
GET /api/v1/points/account
```

**Response 200:**

```json
{
    "code": 200,
    "data": {
        "balance": 8940,
        "totalEarned": 12000,
        "totalSpent": 3060,
        "frozen": 0
    }
}
```

#### 4.2.6 查询积分流水

```
GET /api/v1/points/transactions?page=1&size=20&type=REFERRAL_REWARD
```

**Response 200:**

```json
{
    "code": 200,
    "data": {
        "total": 12,
        "list": [
            {
                "id": 1001,
                "type": "REFERRAL_REWARD",
                "amount": 2980,
                "balanceAfter": 8940,
                "description": "邀请好友 小明 付费奖励",
                "createdAt": "2026-03-02T14:30:05"
            }
        ]
    }
}
```

---

### 4.3 管理员端接口

#### 4.3.1 推荐数据概览

```
GET /api/v1/admin/referral/stats
```

**Response 200:**

```json
{
    "code": 200,
    "data": {
        "todayInvited": 23,
        "todayRewarded": 8,
        "todayPointsIssued": 45600,
        "totalInvited": 1523,
        "totalRewarded": 687,
        "totalPointsIssued": 2890000,
        "pendingReview": 3
    }
}
```

#### 4.3.2 邀请关系列表（含风控审核）

```
GET /api/v1/admin/referral/relations?page=1&size=20&status=REVIEW
```

**Response 200:**

```json
{
    "code": 200,
    "data": {
        "total": 3,
        "list": [
            {
                "id": 5001,
                "referrerNickname": "用户A",
                "referrerId": 100,
                "inviteeNickname": "用户B",
                "inviteeId": 200,
                "refCode": "ENG_A3K9",
                "channel": "LINK",
                "status": "REVIEW",
                "reviewReason": "同一IP 24小时内注册3个被邀请账号",
                "paymentAmount": 29800,
                "rewardPoints": 5960,
                "registeredAt": "2026-03-02T10:00:00",
                "paidAt": "2026-03-02T14:30:00"
            }
        ]
    }
}
```

#### 4.3.3 审核操作

```
POST /api/v1/admin/referral/review
```

**Request Body:**

```json
{
    "relationId": 5001,
    "action": "APPROVE",
    "remark": "核实后属正常邀请"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| relationId | long | 是 | 邀请关系ID |
| action | string | 是 | APPROVE / REJECT |
| remark | string | 否 | 审核备注 |

**处理逻辑:**
- APPROVE: 状态 → REWARDED，发放积分，更新积分账户
- REJECT: 状态 → REJECTED，不发放积分

#### 4.3.4 停用/启用邀请码

```
POST /api/v1/admin/referral/toggle-code
```

```json
{
    "userId": 100,
    "enabled": false
}
```

---

## 5. 前端页面与交互设计

### 5.1 页面清单

| 页面 | 路由 | 入口位置 |
|------|------|----------|
| 邀请好友页 | `/referral` | 个人中心 → "邀请好友" |
| 我的积分页 | `/points` | 个人中心 → "我的积分" |
| 注册页（改造） | `/register` | 新增邀请码输入框 |
| 管理端-推荐管理 | `/admin/referral` | 管理后台侧边栏 |

### 5.2 邀请好友页（`/referral`）

**布局结构:**

```
┌──────────────────────────────────┐
│         邀请好友，一起学英语         │  标题
│                                    │
│  ┌──────────────────────────────┐  │
│  │  你的专属邀请码: ENG_A3K9    │  │  邀请码展示卡片
│  │  [复制邀请码]  [复制链接]     │  │  两个操作按钮
│  │  [生成海报]                  │  │  可选：分享海报
│  └──────────────────────────────┘  │
│                                    │
│  累计邀请 5 人 | 已获奖励 3 次      │  统计数据
│  累计获得 89.40 元积分              │
│                                    │
│  ── 邀请记录 ──                    │  Tab 切换
│  [全部] [已注册] [已付费] [已奖励]   │
│                                    │
│  小明  通过链接  已奖励  +29.80     │  列表项
│  小红  通过邀请码  已注册  等待付费  │
│  ...                               │
└──────────────────────────────────┘
```

**交互说明:**
- 复制邀请码：点击后复制到剪贴板，Toast 提示"已复制"
- 复制链接：复制完整邀请链接，适合直接发送给好友
- 邀请记录列表：下拉加载更多，按时间倒序
- 被邀请人信息脱敏：仅显示昵称和头像，不显示手机号等隐私信息

### 5.3 注册页改造

**场景一：通过邀请链接进入（URL 带 `?ref=ENG_A3K9`）**

```
┌──────────────────────────────────┐
│  你的好友 学霸小王 邀请你加入       │  顶部提示条（自动展示）
│                                    │
│  用户名: [________________]        │  现有表单
│  密码:   [________________]        │
│  ...                               │
│                                    │
│  邀请码: [ENG_A3K9        ] ✓ 已验证│  自动填入，灰色不可编辑
│                                    │
│  [注 册]                           │
└──────────────────────────────────┘
```

**场景二：直接进入注册页（无 ref 参数）**

```
│  ...                               │
│  邀请码: [________________] (选填)  │  可手动输入
│          输入后实时验证              │  debounce 300ms 调验证接口
│  ...                               │
```

**交互说明:**
- 链接进入时：从 URL 读取 `ref` 参数 → 调验证接口 → 成功则自动填入且锁定输入框，显示邀请人昵称
- 手动输入时：输入满 4 位后触发验证（debounce 300ms），显示验证状态（加载中/有效/无效）
- 邀请码字段永远是选填，不影响正常注册流程
- `ref` 参数同时存入 `localStorage`，防止用户在注册页刷新后丢失（有效期 7 天）

### 5.4 我的积分页（`/points`）

```
┌──────────────────────────────────┐
│  我的积分                          │
│                                    │
│  ┌────────────────────────┐       │
│  │  可用积分                │       │
│  │  ¥ 89.40               │       │  积分余额卡片
│  │  累计获得 ¥120.00       │       │
│  │  已使用 ¥30.60          │       │
│  └────────────────────────┘       │
│                                    │
│  ── 积分明细 ──                    │
│  邀请奖励  +29.80  03-02 14:30    │  流水列表
│  会员抵扣  -30.60  03-01 09:00    │
│  邀请奖励  +59.60  02-28 16:45    │
│  ...                               │
└──────────────────────────────────┘
```

### 5.5 管理端-推荐管理（`/admin/referral`）

**Tab 结构:**
- 数据概览：今日/累计邀请数、奖励积分总额、待审核数量
- 邀请记录：全量邀请关系列表，支持按状态筛选、按邀请人搜索
- 待审核：风控拦截的记录，操作按钮"通过"/"拒绝"
- 配置管理：返利比例、邀请上限等参数的可视化配置界面

---

## 6. 防刷风控规则

### 6.1 规则清单

| # | 规则 | 触发动作 | 说明 |
|---|------|----------|------|
| R1 | 同一 IP 24h 内注册 ≥ N 个被邀请账号 | 标记 REVIEW | 阈值 N 可配置，建议初始值 3 |
| R2 | 同一设备指纹 24h 内注册 ≥ N 个被邀请账号 | 标记 REVIEW | 基于浏览器 fingerprint |
| R3 | 邀请人单月累计奖励次数 ≥ M | 标记 REVIEW | 阈值 M 可配置，建议初始值 20 |
| R4 | 被邀请人注册后 T 分钟内即付费 | 标记 REVIEW | 极短间隔可能为自充，建议 T=5 |
| R5 | 邀请人与被邀请人注册手机号前 7 位相同 | 标记 REVIEW | 同一运营商同一地区批量号段 |
| R6 | 被邀请人付费后 24h 内申请退款 | 冻结积分，标记 REVIEW | 防止付费-获积分-退款套利 |

### 6.2 风控执行流程

```
被邀请人首次付费事件
        ↓
依次执行 R1 ~ R6 规则检查
        ↓
  ┌─ 全部通过 → 状态 PAID → 计算积分 → 发放 → 状态 REWARDED
  └─ 任一触发 → 状态 REVIEW → 记录 review_reason → 等待管理员审核
                                                      ↓
                                              APPROVE → 发放积分
                                              REJECT  → 不发放
```

### 6.3 设备指纹采集

前端在注册时采集浏览器指纹，作为风控辅助维度：

```javascript
// 采集维度（不依赖第三方库的简易方案）
const fingerprint = {
    userAgent: navigator.userAgent,
    language: navigator.language,
    screenResolution: `${screen.width}x${screen.height}`,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    canvas: getCanvasFingerprint(),   // Canvas 渲染哈希
    webgl: getWebGLFingerprint()      // WebGL 渲染器信息
};
// 将以上信息哈希后生成 device_id 传给后端
```

后续可根据需要升级为 FingerprintJS 等成熟方案。

### 6.4 退款联动

当被邀请人发起退款且退款成功时：
1. 查询该用户对应的 `referral_relation`
2. 如果状态为 REWARDED，则扣回已发放积分（写入负数流水）
3. 如果邀请人积分余额不足以扣回，则将余额扣至 0，差额记录为"欠账"待后续积分到账时自动扣回

---

## 7. 可配置业务参数

以下参数均存储于系统配置表（`sys_config` 或独立配置表），管理后台可修改，修改后实时生效。

| 参数 Key | 说明 | 建议默认值 | 备注 |
|----------|------|------------|------|
| `referral.reward.rate` | 返利比例 | 0.20（20%） | 被邀请人首次付费金额 × 此比例 = 奖励积分 |
| `referral.reward.max_per_invite` | 单次邀请奖励上限（分） | 10000（100元） | 防止高价订单导致过高返利 |
| `referral.monthly_invite_limit` | 单用户每月邀请奖励上限次数 | 20 | 超出后标记 REVIEW |
| `points.deduct.max_rate` | 积分抵扣订单最高比例 | 0.50（50%） | 最多抵扣订单金额的 50% |
| `points.expire.days` | 积分有效期（天） | 365 | 0 表示永不过期 |
| `risk.same_ip_24h_limit` | 同 IP 24h 注册上限 | 3 | 超出触发 REVIEW |
| `risk.same_device_24h_limit` | 同设备 24h 注册上限 | 3 | 超出触发 REVIEW |
| `risk.min_pay_interval_minutes` | 注册到付费最短间隔（分钟） | 5 | 低于此值触发 REVIEW |

---

## 8. 与现有系统的集成点

### 8.1 注册模块

- 改动点：注册接口新增可选参数 `refCode` + `channel`
- 注册成功后异步写入 `referral_relation`
- 原则：邀请绑定失败不阻塞注册

### 8.2 付费/订单模块（待建）

- 首次付费成功时发布领域事件（如 `OrderPaidEvent`）
- 推荐模块监听该事件，触发奖励计算 + 风控检查 + 积分发放
- 建议使用 Spring Event 或消息队列解耦

### 8.3 会员抵扣（待建）

- 用户购买会员时，选择使用积分抵扣
- 调用积分账户扣减接口，使用乐观锁保证并发安全
- 积分抵扣金额不超过订单金额的 `points.deduct.max_rate`

### 8.4 管理员后台

- 新增"推荐管理"菜单项
- 需要权限：`referral:view`（查看）、`referral:review`（审核）、`referral:config`（配置修改）
- 对接现有 RBAC 权限体系

### 8.5 审计日志

- 积分发放、审核操作、配置修改均需写入审计日志
- 复用现有 `audit_log` 表，`module = 'REFERRAL'`

---

*文档结束 — 具体业务参数待付费体系上线后确定*
