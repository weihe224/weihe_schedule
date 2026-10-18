# AGENTS.md — 拾课（WeiheSchedule）开发规范

本文件记录**长期有效**的开发规范。当前项目状态见 [`AGENT_HANDOFF.md`](AGENT_HANDOFF.md)。

---

## 1. 技术栈约束（硬性）

| 约束 | 说明 |
|---|---|
| **纯 Java** | 不引入 Kotlin、Compose、Room、Hilt 或任何注解处理器 |
| **不新增依赖** | 现有依赖只有 `libs/jxl-2.6.12.jar` 与 `junit:junit:4.13.2`。加依赖前必须先问用户 |
| **不用 XML 布局** | `res/layout` 不存在，**所有 View 在 Java 中构建**。不要新建 layout 文件（例外见下） |
| **不用 AndroidX** | 用平台 API（`android.app.Activity`、`android.app.Dialog` 等） |
| **不改存储格式** | `SharedPreferences("weihe_schedule")` 的键与 JSON 结构属于兼容契约 |
| Java 17 语法 | `sourceCompatibility`/`targetCompatibility` = 17，可用 `var`、records、`java.time` |

**「不用 XML 布局」的唯一例外（2026-10-18 起）**：桌面小组件。

`RemoteViews` 只能从 XML 膨胀，Java 里拼不出来，所以
`res/layout/widget_*.xml`、`res/xml/course_widget_info.xml`、`res/values/widget.xml`
是**允许且仅允许**服务于 `widget/` 包的布局资源。
**页面 UI 仍然全部在 Java 中构建**；不要因为小组件开了这个口子就把 Activity 的界面也改成 XML，
也不要把小组件的样式常量搬回 `UiTokens`（`RemoteViews` 由桌面进程膨胀，读不到 Java 常量）。

---

## 2. 分层与放置规则

```
（根）Activity   只做：建界面、读输入、调用规则、跳转、保存
model/           不可变数据结构 + 与 Android 无关的计算（ScheduleUtils 等）
data/            CourseRepository 是唯一数据入口；序列化在 MiniJson/SemesterJson/BackupCodec
rule/            纯逻辑（无 Android 依赖），必须能被 JVM 测试直接调用
reminder/        提醒计算与调度
importer/        Excel 读取与解析
ui/              UiKit / UiTokens / Pickers / Insets —— 公共 UI 组件
design/          Palette 等设计常量
```

**关键纪律**：

- **凡是能做成纯函数的判断，放进 `rule/`，不要在 Activity 里写**
  （周次、冲突、校验、草稿、命名、日历网格都属于这一类）。
  理由：`unitTests.returnDefaultValues = false`，规则层保持无 Android 依赖才能被测试覆盖。
- **数据只经 `CourseRepository` 读写**，Activity 不得直接碰 `SharedPreferences`。
- **业务规则不得有第二份实现**。例：单双周只由 `ScheduleUtils.occursInWeek` 决定，
  时长只由 `BellSchedule.PERIOD_MINUTES`/`endOf` 决定，时间格式只由 `Pickers` 与
  `BellSchedule.label` 决定。新代码要**调用**它们，不得复制逻辑。
- **测试要放在与服务同一个包下**，因为 `CourseRepository` 的测试接缝
  （`useStoreForNextInstance(FakePreferences)`、`new CourseRepository((Context) null)`）
  是包级可见的。

---

## 3. 代码风格

- **UI 文案用中文，源码里写成 `\uXXXX` 转义**
  （根包 Activity 一律如此，便于在纯 ASCII 环境阅读与 diff）。
- **注释与 javadoc 用英文**，写**为什么**而不是**做了什么**；
  对取舍、边界、反直觉之处必须写明理由。
- `rule/`、`reminder/`、`data/` 包的**源码保持纯 ASCII**（转义中文也在其中）。
- 缩进 4 空格，行宽约 90；方法与字段按现有文件的顺序习惯排列。
- **不要提交注释掉的旧代码**，也不要留 TODO 而不写清原因。

---

## 4. UI 规范

- **优先复用 `UiKit`**：`filledButton`/`tonalButton`/`outlinedButton`/`textButton`/
  `primaryAction`/`card`/`sectionLabel`/`emptyState`/`dialog`/`chip`/`pill`/`field`/
  `ripple`/`pressable` 等。**不要**自己拼一套样式。
- **尺寸与颜色只用 `UiTokens` 与 `Palette`**，不写字面量 dp 与色值。
- **按钮层级**：主要操作=实心（一屏一个）；次要=描边或文字；危险=`Palette.ERROR` + 明确动词
  （「删除课程」而不是「删除」）。
- **弹窗**：统一用 `UiKit.dialog`，标题说明目的，按钮用具体动词（避免「确定/取消」）；
  破坏性操作必须二次确认。
- **空状态**：区分"没有数据"与"筛选后没有结果"，给出下一步操作，不要用假数据填充。
- **反馈**：成功给简短确认；失败说明原因与下一步；**不得在成功之前报成功**。
- **输入**：日期与时刻**必须**用 `ui/Pickers`（内部是 `UiKit.dialog` + 自绘内容），
  不要用系统 `DatePickerDialog`/`TimePickerDialog`（会出现英文按钮与大块绿色标题）。
  不得把"节次""周次""提前几分钟"这类**数字**做成日期/时间选择器。

---

## 5. 构建与测试

```powershell
cd 'D:\vibe coding\Weihe\Weihe'
$env:GRADLE_USER_HOME='D:\vibe coding\Weihe\Weihe\.gradle-local\home'
$env:ANDROID_HOME='D:\Sdk'; $env:ANDROID_SDK_ROOT='D:\Sdk'
& '.\.gradle-local\bin\gradle.bat' "-Dorg.gradle.vfs.watch=false" `
    --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

- **必须带 `--rerun-tasks`**，否则未变化的测试任务会被跳过，读到的是旧结果。
- 环境：Gradle 8.13、JDK 20、AGP 8.13.0；`ANDROID_HOME=D:\Sdk`。
- 交付前**三项都要是绿的**：测试全通过、`assembleDebug` 成功、`lintDebug` 报
  `No issues found.`。
- **项目没有 `androidTest` 源集**，不要写仪器测试，也不要声称跑过 UI 自动化。
- **`assembleDebug` 不会安装**：改完界面必须
  `adb install -r app\build\outputs\apk\debug\app-debug.apk`，
  否则设备上跑的还是旧包。本项目已两次因此误判"新功能没生效"。
- 改了 UI 就**必须在模拟器上打开那个页面走一遍主路径**再交付。
  仅"编译通过 + 测试全绿 + lint 干净"**不能**证明 UI 可用
  （本项目出过 `ArrayStoreException` 只有在打开页面时才暴露、而 776 项测试全绿的先例）。

---

## 6. 工作约定

- **未经用户明确要求，不要 `git commit` / `git push`**。
- **绝对禁止** `git reset --hard`、`git clean`、强制切分支、`git checkout -- <file>`
  等会丢失未提交工作的操作。工作区可能存有他人的修改。
- 发现**自己引入的缺陷**要如实说明并修掉；**不要**为了让测试通过而放宽断言、
  改既有测试的期望值或删除测试。若某个既有测试与需求冲突，**停下来报告**。
- 需求与代码现状冲突时（例如需求假定某功能已存在或要求改存储格式），
  **先报告，不要按猜测实现**。
- 交付说明要包含：改了哪些文件、测试/构建/lint 的**实际**结果、
  **哪些没验证**。不得虚构结果，不得把计划写成已完成。

---

## 7. 交接文档的维护

- **每完成一项任务就更新 `AGENT_HANDOFF.md`**（当前状态的唯一有效文档）：
  更新 Git 状态、功能完成状态、已知问题与下一步。
- **逐轮开发日志（根因、修法、当轮测试结果）追加到 `AGENT_HANDOFF_ARCHIVE.md`**，
  不要堆进 `AGENT_HANDOFF.md`，也不要覆盖其他 Agent 的记录。
- 完成状态必须使用明确标签（已实现并已验证 / 已实现但尚未充分验证 / 部分实现 /
  尚未实现 / 存在问题 / 待确认），并附代码路径或测试类作为依据。
- **讨论过、计划过、写过提示词，都不等于已实现。**

---

## 8. 环境陷阱（会浪费大量时间）

**PowerShell**

- `(...)` 内使用 `if` 是语法错误；多余的一个 `)` 会让**整个脚本不执行**（看起来像"已修改"）。
- 单引号 here-string 产生 LF，而源码是 CRLF → 多行匹配用 `[regex]` 加 `\r?\n`，或逐行替换。
- `git show > file` 在 PowerShell 里会写成 UTF-16；用 `Out-File -Encoding utf8` 或 .NET API。
- 用 `[System.IO.File]::ReadAllText/WriteAllText` 并显式指定 `UTF8Encoding(false)`，避免 BOM。
- **按单行做替换远比多行 here-string 可靠**；多行锚点经常因行尾差异静默失败。

**模拟器（`emulator-5554`）**

- `adb shell input text` **会丢掉 `.`**，且无法输入非 ASCII。
- `input swipe x y x y <ms>` 做长按容易拉下通知栏；`BACK` 按多会退到桌面。
- UI dump 里是**真实中文**，用 `\uXXXX` 去匹配必然找不到。
- 整个模拟器窗口黑屏但应用正常（系统 Settings 也一样）→ WindowManager 缺陷，
  `adb reboot` 可解，**不是应用 bug**，不要为此改代码。

---

## 9. 图标资源（长期规范）

**`tools/IconForge.java` 是图标资源的唯一来源。**

```powershell
java tools/IconForge.java icon.png app/src/main/res tools/out
```

- 它从根目录的 `icon.png`（原始素材，**不要覆盖**）生成五个密度各自的
  `ic_launcher_background` / `_foreground` / `_monochrome` / `ic_launcher` / `ic_launcher_round`，
  并把各遮罩下的效果写成 `tools/out/icon-masks.png` 供人工核对。
- **不要手工编辑或替换 `res/mipmap-*/` 下的任何 PNG**，也不要另起一套生成脚本：
  自适应图标的 108dp 画布、66dp 安全圆、72dp 可视区这套换算是集中在一处的，
  手改会让某个密度与其它密度不一致，而这一点在桌面图标上才会暴露。
- 页内品牌 Logo 用 `res/drawable-nodpi/ic_brand_logo.png`（同一原图裁掉透明边距后等比缩放）。
  **页面里不要用 `R.mipmap.ic_launcher`**：它是自适应图标，平台只保证中间 72dp 可见，
  放进 `ImageView` 会显得比实际小一圈。
- 启动画面在 `res/values-v31/themes.xml` 里显式指定
  `windowSplashScreenAnimatedIcon`（复用 `ic_launcher_foreground`）与
  `windowSplashScreenBackground`（与 `windowBackground` 同色，避免启动闪色）。

**注意**：`values/`、`values-v27/`、`values-v31/` 三份主题**各自完整定义** `Theme.WeiheSchedule`，
因为跨配置目录的 style 不会被合并。**改主题必须三份同时改**。

## 10. 签名与打包（长期规范）

- **绝不把 keystore、口令或签名配置写进仓库**。`app/build.gradle` 只允许引用仓库外的路径。
- `release` 目前**没有** `signingConfig`，`assembleRelease` 产出的是
  `app-release-unsigned.apk`，**不可安装**，这是预期行为而非构建失败。
- 个人自用可安装 **Debug 包**：它由 `~/.android/debug.keystore` 签名，功能与 Release 相同
  （`minifyEnabled` 两边都是 `false`）。
- **换签名必须卸载重装，而卸载会清空课表**。引导用户换签名前，先要求导出备份。
