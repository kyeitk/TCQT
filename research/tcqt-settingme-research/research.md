# TCQT：QQ 原生侧滑栏（SettingMe）完整取证报告

## 1. 环境

- 设备：一加 Ace 6（PLQ110 / OP6113L1）
- Android：16（API 36），OPPO 系统
- QQ：9.3.55（15900）
- TCQT：4.0.0.r656.043868b（debug，656）
- 注入方式：Zygisk + LSPosed + KernelSU，未使用 su
- 本地证据：
  - `evidence/qq-2026-09-17-log.txt`
  - `evidence/qq-2026-09-17-dump2.txt`
  - `evidence/qq-dumpsys-current.txt`

## 2. 页面结论

目标不是横向 Pager 负一页，而是 QQ 原生 SettingMe 侧边栏。

```text
QQSettingMeMenuPanelPartV3
└── Part.mRootView（RelativeLayout）
    ├── QQSettingMeBackgroundView（a81，背景类）
    ├── DrawerRandomCoverView（wou，随机封面）
    ├── QQSettingMeFloatingViewV3（uuy）
    │   └── ObservableRelativeLayout
    │       ├── BounceScrollView pp0
    │       │   └── RelativeLayout
    │       │       ├── FrameLayout ner → QQBlurView（毛玻璃）
    │       │       ├── View o3c / o3b（背景效果层）
    │       │       ├── FrameLayout pou（用户 Tab）
    │       │       │   └── ViewPager2 xqe → adapter aiavatar.b
    │       │       └── RecyclerView poh（功能菜单）
    │       └── LinearLayout pnv（底部按钮行）
    └── RelativeLayout pph（顶部快捷区）
        ├── FrameLayout pp9 → 打卡
        ├── RoundFrameLayout wv0 → 状态
        ├── RoundFrameLayout poy → QQ秀
        └── RoundFrameLayout pnt → 返回/×
```

## 3. 顶部快捷区

已确认 View：

- 容器：`RelativeLayout res=pph`，bounds `[51,129-1221,282]`
- 打卡：
  - `FrameLayout res=pp9`
  - `RoundFrameLayout res=ppa`
  - `LinearLayout res=pp_`（clickable）
  - 图标 `URLImageView res=pp6`
  - 文字 `QUIBlendTextView res=pp8 text=打卡`
- 状态：
  - `RoundFrameLayout res=wv0`
  - `DrawerOnlineStatusView`
  - 文字 `QUIBlendTextView text=状态`
- QQ秀：
  - `RoundFrameLayout res=poy`
  - `ImageView res=pox desc=QQ秀`
- 返回/×：
  - `RoundFrameLayout res=pnt`
  - `ImageView res=pns desc=返回`

修改入口：

```text
QQSettingMeMenuPanelPartV3.onInitView
→ SettingMeMenuSession 根视图
→ 定位 pph
→ 修改 child visibility / TextView.text / ImageView drawable / child 顺序
```

等级：Confirmed。

注意：隐藏“返回/×”时必须保留其他关闭侧边栏方式。

## 4. 背景

已确认类：

- `com.tencent.mobileqq.settingme.QQSettingMeBackgroundView`
- `com.tencent.mobileqq.widget.DrawerRandomCoverView`
- `com.tencent.qui.quiblurview.QQBlurView`

当前树中的背景/效果层：

- `FrameLayout res=ner` → `QQBlurView`（当前 GONE）
- `View res=o3c`，bounds `[0,0-1272,165]`
- `View res=o3b`，bounds `[0,165-1272,464]`
- `DrawerRandomCoverView res=wou`，bounds `[0,0-1272,770]`，内含 `URLImageView`
- `View res=aav`，bounds `[0,0-0,584]`

修改入口：

```text
QQSettingMeBackgroundView
├── View 层：alpha / visibility / clipBounds / translation / scale / drawable
└── 方法层：需要反射该类的加载/绘制/变换方法
```

已取证到 `QQSettingMeBackgroundView` 方法：

- `onDraw(android.graphics.Canvas)`
- `setVipNumberInfo(java.lang.String, int)`
- `verifyDrawable(android.graphics.drawable.Drawable)`
- `onVisibilityChanged(android.view.View, int)`

当前可确认：该类自绘背景并处理 VIP 数字；模糊效果在 `QQBlurView`，随机封面在 `DrawerRandomCoverView`。缩放/位置的最终方法仍未直接定位。

## 5. 用户 Tab

当前真机已展开的用户 Tab 完整结构：

```text
FrameLayout pou（0,0-1272,677）
└── ViewPager2 xqe
    └── RecyclerViewImpl
        └── RelativeLayout pnu
            ├── QQProAvatarView ppw（大头像，51,57-324,330）
            │   └── QQProAvatarLayerImageView
            ├── LinearLayout pot（右侧资料）
            │   ├── RelativeLayout ppn
            │   │   ├── LinearLayout ppm
            │   │   │   ├── QQProAvatarView ppl（小头像）
            │   │   │   ├── MaxHeightLinearLayout ppv
            │   │   │   │   └── VipFlashNickNameView ppu（昵称）
            │   │   │   ├── LinearLayout
            │   │   │   │   ├── QUIBlendTextView 40v（切换账号）
            │   │   │   │   └── ImageView uaq
            │   │   │   └── FrameLayout wuy
            │   │   └── ImageView h0z
            │   ├── RelativeLayout pq6
            │   │   ├── ImageView pq3
            │   │   ├── QUIBlendTextView pq4
            │   │   └── ImageView ppo
            │   ├── LinearLayout ppr
            │   │   ├── ImageView ppz
            │   │   ├── LinearLayout whv
            │   │   │   ├── ImageView pq9
            │   │   │   └── ImageView pq_
            │   │   ├── AnimationTextView pq1
            │   │   ├── View pps
            │   │   └── QVipMedalView pq0（QQ会员）
            ├── FlowLayoutContainer x_4（标签流）
            │   └── 多个 LinearLayout
            │       ├── ShapeableImageView image
            │       ├── QUIBlendTextView jfb
            │       └── ImageView yw
            ├── RelativeLayout lox（右下按钮）
            ├── RelativeLayout ljj（按钮）
            ├── RelativeLayout twm（按钮）
            ├── View ppp（分隔线）
            ├── RelativeLayout 5h8（按钮）
            ├── AutoResizeLinearLayout tx5（AI 头像动作）
            └── RelativeLayout ppe
                └── QQProAvatarView ppd
```

Pager 还有第二个 page：`pnu` 在 `x=1297` 位置，说明 ViewPager2 可横向滑动。

修改入口：

```text
FrameLayout pou / ViewPager2 xqe
→ hook com.tencent.mobileqq.parts.aiavatar.b
    ├── onCreateViewHolder / onBindViewHolder
    ├── page 创建/绑定
    └── getItemCount / instantiate
→ 或 View 层修改 pnu / pot / x_4 / 动作按钮
```

`com.tencent.mobileqq.parts.aiavatar.b` 当前只反射到 `$redirector_` 字段，没有 declared 方法，说明实际 adapter 逻辑在父类/组合类中，尚未定位到具体 page 创建方法。

等级：Container/Pager/主要 View Confirmed；通知、点赞、创建QQ秀等具体文字标签尚未取得文本映射，标记 Partial/Unknown。

## 6. 功能菜单

已确认结构：

- RecyclerView：`res=poh`，bounds `[0,677-1272,1658]`
- Adapter：`RFWConcatAdapter`
- Part 数据字段：`QQSettingMeMenuPanelPartV3.q`（ArrayList）
- Item 类：`com.tencent.mobileqq.adapter.u`
- Bean 字段：`d` 为业务 ID
  - `d_album`：相册
  - `d_favorite`：收藏
  - `d_qqwallet`：钱包
  - `d_vip_identity`：会员中心
  - `d_decoration`：个性装扮
- Icon 字段已确认：`i`

修改入口：

```text
QQSettingMeMenuPanelPartV3.onInitView
→ field q
→ 修改 item/bean
→ AsyncListDifferDelegationAdapter.setItems(q)
```

已确认 `com.tencent.mobileqq.adapter.u`：

- `a(): com.tencent.mobileqq.activity.qqsettingme.config.QQSettingMeBizBean`
- `b(): boolean`
- 字段：`a` 为业务 bean，`b` 为布尔开关

标题应位于 `QQSettingMeBizBean` 内，但该类字段尚未 dump。

等级：Confirmed；标题字段仍在 `QQSettingMeBizBean` 内，精确映射 Unknown。

## 7. 底部按钮行

已确认结构：

- 容器：`LinearLayout res=pnv`，bounds `[0,2546-1272,2800]`
- 子项：
  - `res=pp1`：设置
  - `res=pok`：夜间
  - `res=pqe`：日间/夜间切换
  - TCQT：`TextView tag=tcqt_module_settings_entry text=模块设置`

修改入口：

```text
QQSettingMeMenuPanelPartV3.onInitView
→ SettingMeMenuSession.bottomBar()
→ child visibility / text / drawable / child 顺序
→ addView
```

等级：Confirmed。

注意：原按钮使用 weight 布局，直接复制 `layoutParams.width == 0` 会不可见；应使用测量宽度或固定 dp。

## 8. 整页尺寸 / 右侧空白

可复用方案：对 SettingMe 根视图或 `QQSettingMeFloatingViewV3` 使用 `clipBounds`，不直接改 `layoutParams.width`，避免 DrawerFrame 的 `onMeasure` 每帧重置。

推荐入口：

```text
QQSettingMeFloatingViewV3 / SettingMe 根视图
→ OnDrawListener 观测
→ clipBounds 设置目标宽度
→ 右侧空白由外层容器/背景承载
```

`QQSettingMeFloatingViewV3` 已反射出大量尺寸/手势相关方法，可作为整页大小入口：

- `setDefaultMarginTop(int)`
- `setMinHeight(int)`
- `setEnableMoveToHalf(boolean)`
- `setEnableMoveToMin(boolean)`
- `setForbidTouchEvent(boolean)`
- `setOutSideTouchEnable(boolean)`
- `g(float)` / `q(float)` / `h(float, int, AnimatorListenerAdapter)`

等级：Technique Confirmed（历史实现）；`QQSettingMeFloatingViewV3` 方法级入口已确认，当前版本落地前仍需回归验证。

## 9. 修改入口总表

| 区域 | 容器/类 | 入口 | 等级 |
|---|---|---|---|
| 顶部快捷区 | `pph` | View 层 | Confirmed |
| 背景 | `QQSettingMeBackgroundView` | View 层/方法层 | Class Confirmed，方法 Unknown |
| 用户 Tab | `pou` / `xqe` / `aiavatar.b` | Adapter + View 层 | Container Confirmed，子项 Partial |
| 功能菜单 | `poh` / `Part.q` | 数据驱动 `setItems` | Confirmed |
| 底部按钮行 | `pnv` | View 层 | Confirmed |
| 整页尺寸 | SettingMe 根视图 | clipBounds | Technique Confirmed |

## 10. 剩余未知项

1. `QQSettingMeBackgroundView` 的缩放/位置内部方法名；`onDraw`/`setVipNumberInfo` 已确认。
2. 用户 Tab 内通知、点赞、创建QQ秀等按钮的文字映射。
3. `QQSettingMeBizBean` 字段，用于补齐标题与点击 ID 映射。
4. 整页 `clipBounds` / `QQSettingMeFloatingViewV3` 尺寸方法在当前版本的回归验证。
5. `aiavatar.b` 的父类/组合 adapter 中 page 创建/绑定方法名。