# AGENTS.md

## Project Overview

TCQT is an Xposed module for QQ/TIM Android (NT architecture) providing message anti-recall and other features. Targets LSPosed framework on Android 8.1-16.

## Build Commands

```bash
# Build debug APK (no signing required)
./gradlew :app:assembleDebug

# Build release APK (requires signing config)
./gradlew :app:assembleRelease

# Build both
./gradlew :app:assembleDebug :app:assembleRelease

# Build Zygisk module ZIP (Magisk/KernelSU; a copy of the dual-format release APK)
./gradlew :app:packageZygiskModule

# Build dual-format APK for a specific build type (.apk installs, rename to .zip to flash)
./gradlew :app:buildDualApkRelease
./gradlew :app:buildDualApkDebug

# Clean build
./gradlew clean :app:assembleDebug
```

Output APKs: `app/build/outputs/apk/{debug,release}/TCQT-*.apk` (dual-format: installable APK + flashable Zygisk module in one file, no nested payload APK)
Output Zygisk module: `app/build/outputs/zygisk/TCQT-zygisk-*.zip` (byte-identical copy of the dual-format release APK)

## Requirements

- **JDK 21** (enforced via toolchain)
- **Android SDK** with compileSdk 37, minSdk 27
- **Git** required for version generation (commit count + short hash)
- `local.properties` must point to valid `sdk.dir`

## Project Structure

```
app/                    # Main module (com.owo233.tcqt)
├── src/main/
│   ├── cpp/            # Zygisk native injector + self-made ArtMethod hook engine
│   ├── java/
│   │   ├── com/owo233/tcqt/
│   │   │   ├── hooks/      # Hook implementations
│   │   │   ├── ext/        # Core interfaces (IAction, Setting)
│   │   │   ├── loader/     # Xposed entry points (legacy + modern + zygisk)
│   │   │   ├── ui/         # Compose settings UI
│   │   │   └── utils/      # DexKit, reflection, logging
│   │   └── top/artmoe/inao/# QQ protobuf message models
│   ├── proto/         # Protobuf definitions
│   └── zygisk-template/ # Magisk/KernelSU module template for Zygisk mode
libs/
├── annotations/        # @RegisterAction annotation
├── processor/          # KSP code generator
└── qqinterface/        # QQ class stubs (compileOnly)
```

## Key Architecture Patterns

### Action Registration (KSP)

1. Create hook class implementing `IAction` in `app/src/main/java/com/owo233/tcqt/hooks/`
2. Annotate with `@RegisterAction` (from `libs:annotations`)
3. KSP processor generates `GeneratedActionList` at build time
4. `ActionManager` loads and executes registered actions per-process

Example:
```kotlin
@RegisterAction
object MyHook : IAction {
    override val key = "my_feature"
    override val name = "My Feature"
    override val processes = setOf(ActionProcess.MAIN) // which QQ processes

    override fun onRun(app: Application, process: ActionProcess) {
        // Hook logic here
    }
}
```

### Process Model

QQ runs multiple processes. Hooks specify which process(es) to run in:
- `MAIN` - Main UI process (default)
- `MSF` - Message service
- `TOOL`, `OPENSDK`, `QZONE`, `QQFAV` - Other processes
- `ALL` - All processes

### Zygisk Mode (no Xposed framework needed)

TCQT can also run by injecting into QQ/TIM through a Zygisk module
(`./gradlew :app:buildDualApkRelease` or `:app:packageZygiskModule`). The build
emits a **dual-format package** (FunBox-style): one file that is simultaneously
an installable APK and a flashable Zygisk module zip — the module files
(`customize.sh`, `module.prop`, `zygisk/arm64-v8a.so`, `META-INF/...`) sit at the
zip root next to the APK entries (`classes*.dex`, `AndroidManifest.xml`, ...).
No payload APK is nested inside, keeping the package ≈ the plain APK size.
Architecture:

- `app/src/main/zygisk-template/customize.sh` — `SKIPUNZIP=1`; extracts only the
  module files (incl. `webroot/*`) and copies the whole `$ZIPFILE` (which is
  the APK itself) to `/data/adb/tcqt/main.apk` (atomic tmp+mv), then writes the
  payload's SHA-256 fingerprint to `/data/adb/tcqt/main.apk.sha256`. The
  installed module dir holds no payload; `zygisk/arm64-v8a.so` is the injector.
- `app/src/main/zygisk-template/webroot/` — KernelSU/APatch WebUI: per-app
  injection toggles backed by realtime marker files
  `/data/adb/tcqt/{qq,tim}.disable` (per-user: `user_<id>/` subdir); plus a
  **原生 Hook** section whose toggles are also per host × user, using markers
  `<host>.<hookId>.disable` (e.g. `qq.fekit.disable`, default enabled); the
  injector re-checks them in `preAppSpecialize` on every fork, so changes take
  effect on the next app start without rebooting (mirrors FunBox's scope
  markers, but default-enabled). Also has a 「导出日志」button: copies
  `files/.tcqt/log.txt` (QQ/TIM) to `/sdcard/Download` for bug reporting.
- `app/src/main/cpp/plt_hook.h/.cpp` — generic PLT/GOT hook engine with a
  declarative spec table: new hooks are added by writing one same-signature
  replacement function and appending one `PLT_HOOK_SPEC(id, lib, symbol,
  hook_fn, real)` row to `kDefaultPltHooks` (see `plt_hook.h`); the engine
  handles library discovery (background poller, 50ms — do NOT add a
  dlopen/android_dlopen_ext GOT trigger: it redirects system libs' loads into
  the module and reentrantly scans in-flight link_maps on Android 17+,
  crashing `System.load`), GOT slot patching and real symbol resolution. The
  `id` doubles as the WebUI marker
  suffix (`<host>.<id>.disable`): `zygisk_entry` collects disabled ids per
  host/user in `preAppSpecialize` and `install_default_plt_hooks(disabled_ids)`
  skips them, so each hook can be toggled per host × user. Currently contains
  the `libfekit.so` `fopen` detection evasion (`/proc/self/smaps` → `/dev/null`),
  installed only in the `:MSF` process.
- `app/src/main/cpp/log_file.h/.cpp` — local file logging: every `LOG*` macro
  (see `log.h`) also appends to the app's `files/.tcqt/log.txt` (1MB cap per
  file; over the cap the file rotates to `log.1.txt`, keeping one generation).
  New log files start with a one-time device header (model / Android SDK /
  kernel / page size). Java-side key lifecycle logs reach the same file via
  `ZygiskEntry.nativeLog`.
- `app/src/main/cpp/zygisk_entry.cpp` — Zygisk API v4 injector: in
  `preAppSpecialize` (still root) opens `/data/adb/tcqt/main.apk`, keeps the fd
  and reads the global SHA-256 fingerprint `/data/adb/tcqt/main.apk.sha256`
  (must happen pre-specialization: the app loses access to `/data/adb` after);
  in `postAppSpecialize` it re-copies the payload into the app's `files/.tcqt`
  dir only when that fingerprint differs from the cached `main.apk.sha256`
  (size is NOT compared — same-size new versions would otherwise keep the old
  cached APK), reads every `classes*.dex` entry from that copy via JNI
  `java.util.zip.ZipFile` (mirrors FunBox's loader), loads them via
  `InMemoryDexClassLoader` and calls
  `com.owo233.tcqt.loader.zygisk.ZygiskEntry.init(processName, dataDir, apkPath)`.
- `app/src/main/cpp/art_hook.cpp` — self-made ArtMethod hook engine:
  JNI-probed ArtMethod layout (method_size / access_flags offset / entry point),
  `ScopedSuspendAll` + `WritableArtMethod` for safe mutation, ELF-symbol-resolved
  libart.so helpers, memfd dual-mapped trampoline pool (arm64).
- Java side (`loader/zygisk/`): `ZygiskEntry` bootstraps the host ClassLoader
  (hook `LoadedApk.createAppFactory` → `AppComponentFactory.instantiateClassLoader`
  → `ModuleLoader.initialize`); `ZygiskHookBridge` uses DexMaker to generate a
  same-signature `bridge`/`backup` method pair per hooked method and dispatches
  before/original/after callbacks in Java; `ZygiskHookEngine` implements
  `IHookEngine` so all existing hooks run unchanged.
- Module template at `app/src/main/zygisk-template/`; the staging task pulls
  the current build's `libtcqtzygisk.so` from the CMake obj output
  (`intermediates/cmake/<variant>/obj`) and strips it with the NDK's
  `llvm-strip` into `zygisk/arm64-v8a.so`. The injector is excluded from the
  APK's `lib/` (`packaging.jniLibs.excludes`) and from `ZygiskEntry.loadNativeLibs`
  — it is compiled only for the module, never shipped inside the APK.
  `buildDualApk*` merge the staged module files into the signed APK, re-zip
  (`.so` under `lib/` + `resources.arsc` STORED), `zipalign -p 4` and re-sign
  (v2 only; debug keystore fallback). `packageZygiskModule` just copies the
  dual-format release APK to `outputs/zygisk/TCQT-zygisk-*.zip`.

Do NOT enable the TCQT module in LSPosed and Zygisk mode simultaneously
(shared config, double hooking).

### 主页侧滑面板（home_side_panel）

`features/menu/HomeSidePanel.kt` + `features/menu/homepanel/PanelOverlay.kt`：修改
原 SettingMe 侧滑页宽度，右侧透明露出主内容。

- **触发复用原生**：不拦截手势、不 hook 打开方法。宿主 `DrawerFrame` 照常滑出
  SettingMe。
- **宽度调整**：`PanelSession`（`PanelOverlay.kt`）用 OnDrawListener 逐帧观测
  DrawerFrame，找到 SettingMe 视图后用 `View.clipBounds` 裁剪其绘制区域为屏宽
 百分比（默认 84%，可配）。不隐藏 SettingMe（保持可见），不创建 Compose 覆盖层，
 不拦截触摸——原侧滑页自身渲染内容（头像/昵称/签名/背景/菜单项），QQ 原生
 手势（打开/关闭/拖动）不受影响。用 clipBounds 而非 layoutParams.width（后者
 被 DrawerFrame 的 onMeasure 每帧重置），不触发 requestLayout，避免无限循环。
- **SettingMe 视图获取（双路径）**：路径 A：hook
  `QQSettingMeMenuPanelPartV3.onInitView`，从 Part 的 View 字段向上取根视图，
  存入 `PanelRuntime.settingMeRootView`，PanelSession 每帧 OnDraw 用此引用施
  clipBounds。路径 B（兜底）：搜索 DrawerFrame（先按结构——找到含底栏的主内容
  向上取第一个 childCount >= 2 的父容器，再按类名 "DrawerFrame" 兜底），再在
  DrawerFrame 子级中找不含底栏的 ViewGroup 子视图作为 SettingMe。
- **SettingMe 视图定位**：优先类名含 "SettingMe" 的视图（向上取到 frame 直接
  子级）；类名混淆时兜底为「不含底栏的 ViewGroup 子视图」。主内容通过子树内
  含 `QQTabWidget`/`QQTabLayout` 识别。
- 与 `setting_me_tab`（转移设置页入口）互斥；`simplify_qq_setting_me` 对本面板无效。
### Generated Code

- `GeneratedActionList` - Auto-generated by KSP, lists all `@RegisterAction` classes
- `BuildTime.kt` - Generated at build time with timestamp
- Proto classes - Generated from `.proto` files via protobuf plugin

## Build Configuration

- **Version**: Derived from git (`3.6.5.r<commit-count>.<short-hash>`)
- **ABI**: arm64-v8a only
- **Signing**: CI uses env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- **R8**: Full mode enabled
- **Resource optimization**: Custom package ID `0x53`, reserved ID allowed

## Important Notes

- `libs:qqinterface` is `compileOnly` - QQ's actual classes used at runtime
- Xposed API (`de.robv.android.xposed:api`) and libxposed API are `compileOnly`
- Settings UI uses Jetpack Compose with Material3
- DexKit used for runtime method finding in QQ's dex files
- Configuration cache enabled (`org.gradle.configuration-cache=true`)
- Kotlin code style: `official`
