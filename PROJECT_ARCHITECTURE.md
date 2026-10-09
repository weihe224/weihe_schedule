# PROJECT_ARCHITECTURE.md — 拾课（WeiheSchedule）架构说明

> 本文只描述**事实**：技术栈、项目结构、页面结构、数据结构、数据流、模块依赖、导航关系。
> 所有结论均来自实际代码，标注 `文件:行号`。
> 评价、问题与改进建议见同目录的 `PROJECT_ANALYSIS.md`。
>
> **文档生成时间**：本次分析基于工作区 `D:\vibe coding\Weihe\Weihe` 的当前工作树，git HEAD = `21f20ec`（分支 `main`），工作树干净。
>
> **关于路径**：需求指定的项目路径是 `D:\Weihe`，但该目录是早期快照且对本次会话不可写；
> 实际活的项目是 `D:\vibe coding\Weihe\Weihe`（有 `.git`、`icon.png`、`tools/`，`app_name` 已是「拾课」）。
> 本文档写入工作区根目录。

---

## 一、技术栈（实际配置，非推测）

| 项目 | 实际值 | 证据 |
|---|---|---|
| Android Gradle Plugin | **8.13.0** | `build.gradle:2` — `id 'com.android.application' version '8.13.0' apply false` |
| Gradle | **8.13** | `gradle/wrapper/gradle-wrapper.properties` — `distributionUrl=...gradle-8.13-bin.zip`（阿里云镜像，`validateDistributionUrl=false`） |
| compileSdk | **34** | `app/build.gradle:13` |
| targetSdk | **34** | `app/build.gradle:22` |
| minSdk | **26** | `app/build.gradle:21` |
| Java 源码/目标版本 | **17** | `app/build.gradle:58-59` — `sourceCompatibility/targetCompatibility JavaVersion.VERSION_17` |
| 本机构建用 JDK | **20**（`C:\Program Files\Java\jdk-20`） | 环境变量探测；不影响产物字节码目标（17） |
| Kotlin | **未使用**。无 Kotlin 插件、无 `.kt` 文件、无 `kotlin-stdlib` 依赖 | `app/build.gradle` plugins 块只有 `com.android.application`；`app/src/main/kotlin` 不存在 |
| 主要语言 | **100% Java**，31 个源文件 / 10,820 行 / 7 个包 | `app/src/main/java/com/weihe/schedule/`（根包 = 7 个 Activity，另有 `ui/ data/ rule/ model/ importer/ design/`） |
| UI 实现方式 | **既不是 XML 布局，也不是 Jetpack Compose**，而是**纯 Java 代码构建 View 树** | `app/src/main/res/layout` 目录不存在；`Activity.setContentView(View)`（如 `MainActivity.java:82`、`WeekGridActivity.java:127`） |
| AndroidX | **明确关闭** | `gradle.properties:4` — `android.useAndroidX=false` |
| Material Components | **未使用** `com.google.android.material` | `app/build.gradle:78-86` 依赖块 |
| 依赖 1 | `implementation files('libs/jxl-2.6.12.jar')`（725,735 字节，jxl 2.6.12） | `app/build.gradle:79` |
| 依赖 2 | `testImplementation 'junit:junit:4.13.2'` | `app/build.gradle:85` |
| 其他依赖 | **无**。无 Retrofit / OkHttp / Gson / Room / Glide / Coil / Kotlin 协程 | `app/build.gradle:78-86` |
| applicationId | **com.weihe.schedule** | `app/build.gradle:20` |
| namespace | **com.weihe.schedule** | `app/build.gradle:12` |
| versionCode / versionName | 1 / `1.0` | `app/build.gradle:23-24` |
| rootProject.name | `WeiheSchedule` | `settings.gradle` |
| 依赖仓库 | 阿里云镜像优先，回落到 `google()`/`mavenCentral()`/`gradlePluginPortal()` | `settings.gradle` pluginManagement + dependencyResolutionManagement |
| 签名 | 仓库内 `.android-home/debug.keystore`（存在时启用），否则用默认位置 | `app/build.gradle:9, 34-43, 47-49` |
| Lint | `abortOnError true`，`checkReleaseBuilds false`，禁用 7 项检查（`HardcodedText`、`SetTextI18n`、`MissingTranslation`、`LockedOrientationActivity`、`DiscouragedApi`、`OldTargetApi`、`GradleDependency`） | `app/build.gradle:62-75` |
| 单元测试选项 | `unitTests.returnDefaultValues = false`（**显式写出，是刻意的架构约束**） | `app/build.gradle:31` |
| R 类 | `android.nonTransitiveRClass=true` | `gradle.properties:5` |
| 构建缓存 | `org.gradle.parallel=true`、`org.gradle.caching=true`、`org.gradle.tooling.parallel=true` | `gradle.properties:2,3,8` |
| 混淆 | release `minifyEnabled false`；`proguard-rules.pro` 只有 1 行注释 | `app/build.gradle:51-55` |
| 代码仓库 | `https://github.com/weihe224/weihe_schedule.git`，分支 `main` | `git remote -v` |

### 1.1 为什么"没有 XML 布局"值得单独说明

`res/` 下**没有 `layout/` 目录**（`app/src/main/res` 只有 `anim/`、`drawable/`、`mipmap-*/`、`values/`、`values-v27/`）。
所有界面在 Java 里用 `LinearLayout`/`FrameLayout`/`GridLayout` + `UiKit` 工厂方法搭出来。
这带来两个直接后果：

1. **没有 `findViewById`、没有 Adapter、没有 `RecyclerView`**：列表就是往 `LinearLayout` 里 `addView`（如 `MainActivity.java:1067`、`SemesterListActivity`、`ScheduleListActivity`）。
2. **资源里只有主题、动画、图标、启动图标和一条 `app_name`**，共 50 个文件；颜色、文案、尺寸都在 Java 里。

---

## 二、项目目录结构

```
D:\vibe coding\Weihe\Weihe\           （= 需求里说的 D:\Weihe，见文首说明）
├── .android-home/                    本地 Android 用户目录：debug.keystore + 模拟器状态（.gitignore）
├── .gradle/                          构建缓存（.gitignore）
├── .gradle-local/                    本地 Gradle 发行版 + GRADLE_USER_HOME（.gitignore）
├── .idea/                            IDE 本地状态（.gitignore）
├── app/
│   ├── build/                        构建产物（.gitignore）
│   ├── libs/
│   │   └── jxl-2.6.12.jar            唯一的二进制依赖，只读 .xls
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/weihe/schedule/     31 个 .java，10,820 行，7 个包
│   │   │   │   ├── (根包)                    7 个 Activity，4,691 行
│   │   │   │   ├── ui/                       UiKit、UiTokens、Insets，1,652 行
│   │   │   │   ├── data/                     CourseRepository、CourseList、MiniJson、
│   │   │   │   │                             SemesterJson、BellScheduleJson，1,826 行
│   │   │   │   ├── rule/                     CourseQuery、CourseConflicts、
│   │   │   │   │                             CourseFormValidator、BellScheduleEditor、
│   │   │   │   │                             WeekGridPlan，997 行
│   │   │   │   ├── model/                    Course、Semester、ScheduleUtils、
│   │   │   │   │                             BellSchedule、BellSchedules，859 行
│   │   │   │   ├── importer/                 TimetableGrid、TimetableImportParser、
│   │   │   │   │                             ExcelTimetableReader、
│   │   │   │   │                             TimetableImportException，524 行
│   │   │   │   └── design/                   Palette、Colors，271 行
│   │   │   ├── res/
│   │   │   │   ├── anim/            7 个：dialog_in/out、page_in/out/still、sheet_slide_up/down
│   │   │   │   ├── drawable/        10 个 Material 矢量图标（全部 24dp vector）
│   │   │   │   ├── mipmap-anydpi/   ic_launcher.xml、ic_launcher_round.xml（自适应图标，三层）
│   │   │   │   ├── mipmap-{m,h,xh,xxh,xxxh}dpi/  各 5 个 PNG（legacy 方/圆 + 自适应三层）
│   │   │   │   ├── values/          animations.xml、colors.xml、strings.xml、themes.xml
│   │   │   │   └── values-v27/      themes.xml（完整副本 + windowLightNavigationBar）
│   │   │   └── AndroidManifest.xml  47 行，7 个 Activity（全在根包，用相对名注册）
│   │   └── test/java/com/weihe/schedule/    18 个 .java，3,928 行，239 个 @Test
│   │       ├── (根包)                WeekGridRowsTest（与 WeekGridActivity 同包）
│   │       └── model/ rule/ data/ importer/ design/  与所在生产包一一对应
│   ├── build.gradle                 86 行
│   └── proguard-rules.pro           1 行注释
├── build/                           根项目构建产物（.gitignore）
├── gradle/wrapper/                  gradle-wrapper.jar + .properties
├── tools/                           开发工具（不打包进 APK）
│   ├── IconForge.java               从 icon.png 生成全部 mipmap（自适应三层 + legacy 两种 + 遮罩对照图）
│   ├── IconProbe.java               量取原始插画的背景/包围盒/配色
│   ├── PaletteCheck.java            离线跑配色对比度（与 PaletteContrastTest 同一套算式）
│   ├── Pixel.java                   读截图上指定坐标的颜色
│   ├── dump-ui.ps1                  adb uiautomator dump → 打印带 bounds 的节点树
│   └── tap-ui.ps1                   按 text/content-desc 查 bounds 再点击（不猜坐标）
├── .gitignore
├── ARCHITECTURE.md                  项目原有文档（中文，含踩坑记录与上机回归清单）
├── build.gradle                     4 行，只声明 AGP 版本
├── gradle.properties
├── gradlew / gradlew.bat
├── icon.png                         1254×1254 原始 App 图标（透明底），IconForge 的输入
├── local.properties                 sdk.dir=D:\Sdk（.gitignore）
├── settings.gradle                  仓库配置 + include ':app'
├── PROJECT_ARCHITECTURE.md          本文档
└── PROJECT_ANALYSIS.md              配套的完整分析报告
```

**不存在**的目录（已确认）：`app/src/main/assets/`、`app/src/main/kotlin/`、`app/src/main/res/layout/`、`app/src/androidTest/`。

### 2.1 源码清单（按包分组，组内按行数降序）

| 文件 | 行数 | 包 | 层 |
|---|---|---|---|
| `MainActivity.java` | 1339 | `com.weihe.schedule` | 界面 |
| `WeekGridActivity.java` | 939 | `com.weihe.schedule` | 界面 |
| `EditCourseActivity.java` | 806 | `com.weihe.schedule` | 界面 |
| `SettingsActivity.java` | 523 | `com.weihe.schedule` | 界面 |
| `EditScheduleActivity.java` | 457 | `com.weihe.schedule` | 界面 |
| `SemesterListActivity.java` | 371 | `com.weihe.schedule` | 界面 |
| `ScheduleListActivity.java` | 256 | `com.weihe.schedule` | 界面 |
| `UiKit.java` | 1435 | `.ui` | 界面组件层（**最大类**） |
| `UiTokens.java` | 142 | `.ui` | 设计系统（尺寸） |
| `Insets.java` | 75 | `.ui` | 界面基础设施（系统栏） |
| `CourseRepository.java` | 1073 | `.data` | 存储 |
| `MiniJson.java` | 453 | `.data` | 存储 |
| `BellScheduleJson.java` | 128 | `.data` | 存储 |
| `CourseList.java` | 105 | `.data` | 存储（列表语义） |
| `SemesterJson.java` | 67 | `.data` | 存储 |
| `WeekGridPlan.java` | 389 | `.rule` | 规则（网格排布/分道） |
| `BellScheduleEditor.java` | 257 | `.rule` | 规则（作息表单） |
| `CourseFormValidator.java` | 140 | `.rule` | 规则（表单校验） |
| `CourseQuery.java` | 119 | `.rule` | 规则（按周/天查课） |
| `CourseConflicts.java` | 92 | `.rule` | 规则（时间冲突判定） |
| `BellSchedule.java` | 307 | `.model` | 领域模型（作息与时间） |
| `BellSchedules.java` | 193 | `.model` | 领域模型（作息解析顺序） |
| `ScheduleUtils.java` | 170 | `.model` | 领域模型（周次/节次/日期） |
| `Semester.java` | 120 | `.model` | 领域模型 |
| `Course.java` | 69 | `.model` | 领域模型 |
| `TimetableImportParser.java` | 370 | `.importer` | 导入 |
| `TimetableGrid.java` | 87 | `.importer` | 导入 |
| `ExcelTimetableReader.java` | 55 | `.importer` | 导入（jxl，唯一的二进制依赖） |
| `TimetableImportException.java` | 12 | `.importer` | 导入 |
| `Palette.java` | 194 | `.design` | 设计系统（颜色） |
| `Colors.java` | 77 | `.design` | 设计系统（对比度运算，纯逻辑） |

**合计 31 个文件 / 10,820 行。** 「层」一列是概念分层，「包」一列是它在磁盘上的位置；
两者一一对应，包结构就是 `ARCHITECTURE.md` §一 那张分层图。

---

## 三、页面结构

### 3.1 全部用户可见页面 = 7 个 Activity（无 Fragment、无 Compose Screen）

| # | 页面 | 类文件 | 行数 | 是否是入口 | 屏幕方向 |
|---|---|---|---|---|---|
| 1 | 我的课表（首页 / 当天课程） | `MainActivity.java` | 1324 | ✅ `LAUNCHER`，`exported=true` | 竖屏锁定 |
| 2 | 课表总览（周网格 / 学期网格） | `WeekGridActivity.java` | 907 | 否 | 竖屏锁定 |
| 3 | 编辑课程 / 添加课程 | `EditCourseActivity.java` | 795 | 否 | 竖屏锁定 + `adjustResize` |
| 4 | 设置 | `SettingsActivity.java` | 511 | 否 | 竖屏锁定 + `adjustResize` |
| 5 | 学期列表 | `SemesterListActivity.java` | 364 | 否 | 竖屏锁定 |
| 6 | 编辑作息表 / 新建作息表 | `EditScheduleActivity.java` | 434 | 否 | 竖屏锁定 + `adjustResize` |
| 7 | 作息时间列表 | `ScheduleListActivity.java` | 251 | 否 | 竖屏锁定 |

证据：`AndroidManifest.xml:10-45`（7 个 `<activity>`，全部 `android:screenOrientation="portrait"`）。

### 3.2 非 Activity 的可见界面元素

| 类型 | 实现位置 | 说明 |
|---|---|---|
| 底部弹层（BottomSheet） | `UiKit.bottomSheet()` `UiKit.java:951` | 手写：`Dialog` + `FEATURE_NO_TITLE` + 贴底 + 只圆上两角 + 拖拽把手 + 遮罩点击关闭 + `R.style.SheetAnimation` |
| 对话框 | `UiKit.dialog()` `UiKit.java:1094` | 手写：圆角 24dp、左右各留 32dp、最宽 400dp、遮罩 0.38、`R.style.DialogAnimation` |
| 提示框 | `UiKit.message()` `UiKit.java:1176` | 单按钮对话框 |
| 周次选择器 | `MainActivity.showWeekPicker()` `MainActivity.java:~380` | 呼出 `UiKit.bottomSheet`，内部是 4 列 × 18 周的 `LinearLayout` 网格 |
| 冲突确认框 | `EditCourseActivity` 保存路径 | 通过 `CourseConflicts.findConflicts` + `UiKit.dialog` |
| 删除确认框 | `MainActivity.showDeleteDialog()` `MainActivity.java:1307`、`EditCourseActivity`、`EditScheduleActivity`、`SemesterListActivity` | `UiKit.Action.destructive`（红色文本按钮） |
| 自定义 View | `UiKit.PressFeedback` `UiKit.java:~700` | `LinearLayout` 子类，重写 `dispatchTouchEvent` 做按压缩放（**不装 OnTouchListener**，见 ARCHITECTURE.md 第 1 条） |
| 自定义 ScrollView | `WeekGridActivity.SyncedHorizontalScrollView` / `SyncedVerticalScrollView` `WeekGridActivity.java:879, 894` | 重写 `onScrollChanged` 驱动冻结表头/节次列 |
| 无 Adapter / 无 RecyclerView | — | 列表一律 `LinearLayout.addView` |

---

## 四、数据结构

### 4.1 核心领域模型

```
Course  (app/src/main/java/com/weihe/schedule/model/Course.java, 69 行, implements Serializable)
├── long   id              稳定标识；0 = 尚未分配（Course.java:24）
├── String name            课程名（Course.java:25）
├── String teacher         任课教师（Course.java:26）
├── String location        上课地点，**同时是作息表解析的输入**（Course.java:27）
├── int    dayOfWeek       1=周一 … 7=周日（Course.java:28）
├── int    startPeriod     起始节次，1..15（Course.java:29）
├── int    endPeriod       结束节次，1..15（Course.java:30）
├── int    startWeek       起始周，1..18（Course.java:31）
├── int    endWeek         结束周，1..18（Course.java:32）
├── int    parity          PARITY_ALL=0 / PARITY_ODD=1 / PARITY_EVEN=2（Course.java:6-8, 33）
├── int    color           课程色，ARGB（Course.java:34）
├── String scheduleName    显式指定的作息表名，空 = 按地点自动决定（Course.java:33-34）
└── String timeRange       派生字段："08:15 - 09:35"，**不落盘**（Course.java:35-43）
                            由 CourseRepository.applyTimeRange() 在构建快照时写入

Course.copy()  Course.java:48   —— 深拷贝所有字段（timeRange 一起带走）
```

```
Semester  (Semester.java, 120 行)
├── long      id        毫秒时间戳，永不为 0；存储的学期（Semester.java:35-39）
├── LocalDate start     **始终归一化到周一**（构造函数里调 ScheduleUtils.mondayOf）
└── String    name      用户手填的标签；空 = 由日期推导（Semester.java:66-71）
   label()      →  "2026 秋季学期"（start 月份 ≥ 8 为秋季，否则春季）
   seasonName() →  秋季学期 / 春季学期
   lastDay()    →  start + 18 周 − 1 天
   nextStart()  →  秋→次年 2/18 前后最近的周一；春→当年 8/28 前后最近的周一
```

```
BellSchedule  (model/BellSchedule.java, 307 行，**不可变**)
├── String       name              作息表名
├── int[]        starts            每节开始时间，**分钟数**（index 0 = 第 1 节）
├── int[]        ends              每节结束时间，分钟数
└── List<String> locationKeywords  自动匹配用的地点关键词
   PERIODS = ScheduleUtils.PERIODS_PER_DAY = 15
   MAX_MINUTE = 30*60-1 = 1799（允许 24:00–29:59，跨午夜的晚课）
   startMinute/endMinute(period)  越界自动 clamp
   timeRange(from,to)             "08:15 - 09:35"，end 不早于 start
   matchesLocation(location)      去空白/`-`/`·`/`_` + 转小写后 contains
   label(int) / parseLabel / parseLoose   时间文本互转
```

```
BellSchedules  (BellSchedules.java, 193 行，静态工厂 + 解析规则)
├── STANDARD_NAME = "教学楼作息"    → 教学楼 A、F、J301 室；也是默认表
├── BC_NAME       = "B/C 楼作息"    → 教学楼 B、C、J302 室 + 其他教学场所
├── DE_NAME       = "D/E 楼作息"    → 教学楼 D、E、J303 室
├── standard() / bcBuilding() / deBuilding()   三张硬编码时间表
├── presets()                    [standard, deBuilding, bcBuilding]
├── byName(list, name)           按名字查，trim 后精确匹配
├── forLocation(list, location)  第一个关键词命中的表
└── resolve(list, scheduleName, location)   ← **唯一的解析入口**，见 §4.4
```

```
WeekGridPlan  (rule/WeekGridPlan.java, 389 行，纯逻辑，零 Android 依赖)
├── static final class Cluster  一次"列簇"：同一天的若干课程按车道排布
│     day() startPeriod() endPeriod() periodSpan() laneCount() cells() blocks()
├── static final class Cell     簇内一个"起始节次"格
│     day() startPeriod() endPeriod() periodSpan() laneCount() blocks()
├── static final class Block    一门课在网格里的最终落点
│     course() day() startPeriod() endPeriod() periodSpan() lane() laneCount() sharesColumn()
├── plan(courses)      → List<Cluster>   整周排布
├── blocks(courses)    → List<Block>     摊平
├── cells(courses)     → List<Cell>
├── onDay(courses, d)  → List<Cluster>
└── maxLaneCount(courses) → int          同时段最多几门课
```

```
TimetableGrid  (TimetableGrid.java, 87 行，导入用内存工作表)
├── String[][]  cells      单元格文本
├── boolean[][] topLeft    是否合并区左上角
├── int[][]     endRows / endColumns   合并区右下角
└── put / cell / merge / isTopLeft / mergedEndRow / mergedEndColumn
    checkCell 对四个方向做 IndexOutOfBoundsException 检查
```

### 4.2 设计系统数据（非业务）

| 类 | 内容 |
|---|---|
| `Palette.java` (192) | 全部颜色角色：品牌（PRIMARY/ON_PRIMARY/PRIMARY_CONTAINER…）、表面阶梯（SURFACE → SURFACE_CONTAINER_HIGHEST）、文字（ON_SURFACE/ON_SURFACE_VARIANT/OUTLINE/OUTLINE_VARIANT）、页头（HEADER_*）、状态（ERROR/SUCCESS 各 4 项）、9 个课程色 `COURSE_COLORS`、混合系数（COURSE_TINT=0.90 / COURSE_BORDER_TINT=0.55 / COURSE_TEXT_MIX=0.20 / COURSE_RAIL_SOFTEN=0.15）、16 个 `@Deprecated` 别名 |
| `UiTokens.java` (140) | 间距 6 档（4/8/12/16/24/32，SPACE_UNIT=4）、圆角 6 档（4/8/12/16/24/28）、阴影 4 档（0/1/3/6dp）、描边 2 档、字阶 9 档（10–28sp）+ 2 个行距、控件尺寸（TOUCH_TARGET=48、BUTTON_HEIGHT、FIELD_HEIGHT、CHIP_HEIGHT、ICON_BUTTON_SIZE=40、DAY_CELL_HEIGHT=56、COURSE_RAIL_WIDTH=4、FAB_SIZE=56、ICON_SIZE=22） |
| `Colors.java` (77) | WCAG 对比度算术：`blend` / `contrastRatio` / `meets` / `luminance` / `red`/`green`/`blue`。纯算术，无 Android API |

### 4.3 对象关系图

```
Semester (1) ──── 拥有 ────► (N) Course          每个学期一套 courses:<id> 存储键
    │                              │
    │                              │ location / scheduleName
    │                              ▼
    │                     BellSchedules.resolve(...)
    │                              │
    │                              ▼
    │                        BellSchedule (1)
    │                              │
    │                              ▼
    │                        course.timeRange  ("08:15 - 09:35")
    │
    └── start (周一) ──► ScheduleUtils ──► currentWeek / dateOf / occursInWeek
                                                │
                                                ▼
                                          CourseQuery / CourseConflicts / WeekGridPlan

BellSchedule 是**全局共用**的（不属于任何学期）—— 存储键 "bell_schedules" 无学期后缀
```

### 4.4 作息解析规则（`BellSchedules.resolve`，`BellSchedules.java:176-192`）

```
resolve(schedules, scheduleName, location):
  1. byName(schedules, scheduleName)       课程显式指定且该名字还存在 → 用它
  2. forLocation(schedules, location)      第一个关键词命中的表 → 用它
  3. byName(schedules, BC_NAME)            没命中 → 用 "B/C 楼作息"（= 学校表格里的"其他教学场所"）
  4. byName(schedules, STANDARD_NAME)      B/C 也不存在 → 用 "教学楼作息"
  5. 兜底 standard()                        连标准表都没有 → 返回内置标准表
```

默认三张表的关键词（`BellSchedules.java:66, 80, 95`）：

| 表 | 关键词 |
|---|---|
| 教学楼作息（A、F、J301） | `a楼`、`f楼`、`j301` |
| B/C 楼作息（B、C、J302 + 其他） | `b楼`、`c楼`、`j302` |
| D/E 楼作息（D、E、J303） | `d楼`、`e楼`、`j303` |

匹配算法（`BellSchedule.matchesLocation`，`BellSchedule.java:97-128`）：
把地点与关键词**双方**都做 `squeeze`（删除所有空白、`-`、`·`、`_`，并转小写），然后 `haystack.contains(needle)`。
所以 `J301 室` → `j301室` 命中 `j301`；`教学楼F楼F106` → `教学楼f楼f106` 命中 `f楼`。

---

## 五、数据流

### 5.1 存储介质：`SharedPreferences`（无数据库）

```
Context.getSharedPreferences("weihe_schedule", MODE_PRIVATE)     CourseRepository.java:15, 88-90
```

| 键 | 类型 | 内容 | 归属 |
|---|---|---|---|
| `semesters` | String(JSON) | 学期数组 `[{"id":…,"start":"2026-08-31","name":""}]` | 全局 |
| `active_semester` | long | 当前学期 id | 全局 |
| `courses:<semesterId>` | String(JSON) | 该学期全部课程数组 | **按学期** |
| `semester_start:<semesterId>` | String | 该学期第 1 周周一 `yyyy-MM-dd` | **按学期** |
| `bell_schedules` | String(JSON) | 作息表数组 `[{"name":…,"keywords":[…],"starts":[…],"ends":[…]}]` | 全局 |
| `courses` | String(JSON) | **旧版遗留键**，迁移后 `remove` | — |
| `semester_start` | String | **旧版遗留键**，迁移后 `remove` | — |
| `courses_backup` | String | 损坏载荷的备份（`backupDamaged`，`CourseRepository.java:506-508`） | 全局 |

### 5.2 完整数据流（以"首页显示当天课程"为例）

```
SharedPreferences("weihe_schedule")
   │  getString("courses:<semesterId>")
   ▼
CourseRepository.loadCourses()                       CourseRepository.java:413-430
   │  无值时：若 semesterId == seededSemesterId → createSampleCourses() 写入 9 门样例课
   ▼
CourseRepository.readCourses(raw)                    CourseRepository.java:465-491
   │  MiniJson.parseArray(raw)  →  List<Object>
   │  逐条 MiniJson.objectAt() → Map<String,Object> → fromJson() → Course
   │  单条坏数据：跳过并继续（damaged 标记 → backupDamaged）
   │  按 startPeriod 排序
   ▼
CourseRepository.applyTimeRange(course)              CourseRepository.java:499-503
   │  BellSchedules.resolve(getBellSchedules(), course.scheduleName, course.location)
   │      .timeRange(course.startPeriod, course.endPeriod)
   │  → course.timeRange = "08:15 - 09:35"
   │  ⚠ 这一步在**构建快照时做一次**，不是每张卡片做一次
   ▼
cachedCourses（volatile 字段，首次读取后发布）        CourseRepository.java:38, 391-402
   │  getCourses() 每次返回 **深拷贝**（copyOf），调用方改不到缓存
   ▼
MainActivity.onResume() → repository.reload() → refreshAll()      MainActivity.java:88-102
   ▼
MainActivity.renderCourses()                         MainActivity.java:~1040
   │  CourseQuery.onDay(repository.getCourses(), selectedWeek, selectedDay)
   │      → 过滤 dayOfWeek + ScheduleUtils.occursInWeek(course, week)（周次窗口 + 单双周）
   │      → 按 startPeriod/endPeriod 排序
   ▼
CourseConflicts.conflictingIds(visible)              CourseConflicts.java:69-82
   │  O(n²) 两两比对，返回有冲突的 course.id 集合
   ▼
MainActivity.createCourseCard(course, hasConflict)   MainActivity.java:~1090
   │  UiKit.styleAsCard / courseRail / courseTint / courseBorder / courseInk
   │  UiKit.enter(card, min(index,5)*35L)   ← 错落入场动画
   ▼
courseContainer（LinearLayout）→ ScrollView → 屏幕
```

### 5.3 写入流（新增 / 编辑一门课）

```
EditCourseActivity 表单
   │  用户输入 → CourseFormValidator.check(name, startWeek, endWeek, startPeriod, endPeriod)
   │      → List<Problem>{field, reason}（不抛异常，只报数据）
   │  无 problem 时组装 Course
   ▼
EditCourseActivity.validateAndSave()                 EditCourseActivity.java:~750
   │  course.timeRange = repository.scheduleFor(course).timeRange(...)   行 756
   │  CourseConflicts.findConflicts(原有课程, course) → 有交集则弹确认框
   ▼
CourseRepository.upsertCourse(course)                CourseRepository.java:535-540
   │  CourseList.ensureId(courses, course, System.currentTimeMillis())  ← id=0 时向前找空位
   │  CourseList.upsert(courses, course)   ← **存的是 copy()**
   ▼
CourseRepository.saveCourses(courses)                CourseRepository.java:830-833
   │  writeCourses(coursesKey(), courses)
   │      → toJson(course) → MiniJson.write(array)
   │      → preferences.edit().putString(...).apply()
   │  cachedCourses = null                ← 丢弃快照
   ▼
setResult(RESULT_OK); finish()                       EditCourseActivity.java:773-776
   ▼
回到 MainActivity.onResume() → reload() → 重新读
```

### 5.4 导入流（唯一的"外部数据进入"通道）

```
SettingsActivity「选择文件导入」按钮                  SettingsActivity.java:196
   ▼
startActivityForResult(ACTION_OPEN_DOCUMENT, 3001)   SettingsActivity.java:389
   ▼
onActivityResult → uri                              SettingsActivity.java:69-75
   ▼
SettingsActivity.importTimetable(uri)               SettingsActivity.java:392
   │  弹「正在导入」对话框（UiKit.dialog）
   │  new Thread(() -> { ... })                      SettingsActivity.java:397   ← 唯一的后台线程（活的那份）
   │     getContentResolver().openInputStream(uri)
   ▼
ExcelTimetableReader.read(input)                    ExcelTimetableReader.java:17-53
   │  jxl.Workbook.getWorkbook(input)      ← 只支持 .xls（BIFF），不支持 .xlsx
   │  sheet.getRows() / getColumns() → TimetableGrid
   │  grid.put(row, column, sheet.getCell(column,row).getContents())
   │      ⚠ jxl 是 (column,row)，网格是 (row,column)，靠这一行转置
   │  sheet.getMergedCells() → 6 道边界检查 → grid.merge(...)
   │  finally { workbook.close() }
   ▼
TimetableImportParser.parse(grid)                   TimetableImportParser.java
   │  找表头行 → 定位 7 个星期列 → 逐格解析课名/教师/地点/周次/节次
   │  合并单元格 → 跨节课程；一格多课 → 拆成多门
   │  周次语法："2-5周"、"2,3,4,5"、"单周"、"双周"
   │  颜色：colorFor(seed) 由课名哈希从 Palette.COURSE_COLORS 里选
   └─ 不合法 → throw TimetableImportException
   ▼
runOnUiThread → progress.dismiss() → confirmImport(parsed)   SettingsActivity.java:404-408
   │  弹确认框：显示识别到的门数/条数 + 起始周 Spinner
   ▼
TimetableImportParser.shiftWeeks(courses, sourceFirstWeek)   SettingsActivity.java:485
   │  平移周次 + 单双周翻转 + 越界则抛异常
   ▼
CourseRepository.replaceCourses(shifted)            CourseRepository.java:549-563
   │  重新分配 id；**清空 scheduleName**（导入文件只有节次，作息留到后面按地点决定）
   ▼
saveCourses → SharedPreferences → 各界面 onResume → reload()
```

### 5.5 谁调用谁（关键调用边）

| 调用方 | 被调用方 | 方法 |
|---|---|---|
| 7 个 Activity | `CourseRepository` | `getCourses` / `getSemesters` / `getBellSchedules` / `getSemesterStart` / `reload` |
| `MainActivity` | `CourseQuery` | `onDay` (`MainActivity.java:~1043`) |
| `MainActivity` | `CourseConflicts` | `conflictingIds` (`MainActivity.java:~1054`) |
| `MainActivity` | `ScheduleUtils` | `currentWeek` / `weekStart` / `dateOf` / `weekRange` / `formatDate` / `dayName` / `weekRule` / `parityName` |
| `MainActivity` | `CourseRepository` | `scheduleFor`（卡片上的作息标签）、`timeRangeOf` |
| `EditCourseActivity` | `CourseFormValidator` | `check` |
| `EditCourseActivity` | `CourseRepository` | `scheduleFor`（作息预览）、`upsertCourse`、`deleteCourse` |
| `EditScheduleActivity` | `BellScheduleEditor` | `problems` / `overlappingPeriods` / `build` |
| `EditScheduleActivity` | `CourseRepository` | `saveBellSchedules` / `renameSchedule` / `clearScheduleReferences` |
| `SettingsActivity` | `ExcelTimetableReader` → `TimetableImportParser` | `read` / `parse` / `shiftWeeks` |
| `SettingsActivity` | `CourseRepository` | `replaceCourses` / `setSemesterStart` |
| `WeekGridActivity` | `WeekGridPlan` | `plan` / `blocks` / `onDay` / `maxLaneCount` |
| `WeekGridActivity` | `CourseQuery` | `inWeek` / `all` |
| `SemesterListActivity` | `CourseRepository` | `getSemesters` / `setActiveSemester` / `addSemester` / `copyCourses` / `deleteSemester` / `countCoursesOf` / `coursesOf` |
| 所有 Activity | `UiKit` / `Palette` / `UiTokens` / `Insets` | 全部界面构建 |
| `CourseRepository` | `MiniJson` / `SemesterJson` / `BellScheduleJson` / `CourseList` / `BellSchedules` | 序列化与解析 |

---

## 六、模块依赖（分层）

包名就是层名。下面这张图与磁盘上的目录一一对应，也等同于 `ARCHITECTURE.md` §一。

```
┌──────────────────────────────────────────────────────────────────────────┐
│  com.weihe.schedule —— 界面层（Android）—— 只读规则，不实现规则            │
│  MainActivity · WeekGridActivity · EditCourseActivity · SettingsActivity   │
│  SemesterListActivity · ScheduleListActivity · EditScheduleActivity        │
│  （7 个 Activity 留在根包，AndroidManifest 用相对名注册）                   │
├──────────────────────────────────────────────────────────────────────────┤
│  .ui —— 组件库与尺寸令牌（Android）                                        │
│  UiKit（1435 行，组件工厂）· UiTokens（间距/圆角/字阶/控件尺寸）· Insets      │
├──────────────────────────────────────────────────────────────────────────┤
│  .data —— 存储适配与编解码（仅 CourseRepository 依赖 Android）              │
│  CourseRepository（SharedPreferences）· CourseList（列表语义）              │
│  MiniJson · SemesterJson · BellScheduleJson                                │
├──────────────────────────────────────────────────────────────────────────┤
│  .rule —— 规则层（零 Android 依赖，全部有 JVM 测试）                        │
│  WeekGridPlan │ CourseQuery │ CourseConflicts │ CourseFormValidator        │
│  BellScheduleEditor                                                        │
├──────────────────────────────────────────────────────────────────────────┤
│  .importer —— 外部读取（零 Android 依赖，唯一用 jxl 的包）                  │
│  ExcelTimetableReader（只读 .xls）→ TimetableImportParser → TimetableGrid   │
│  TimetableImportException                                                  │
├──────────────────────────────────────────────────────────────────────────┤
│  .design —— 设计系统数据（零 Android 依赖）                                 │
│  Palette（颜色角色）· Colors（对比度运算，纯算术）                          │
├──────────────────────────────────────────────────────────────────────────┤
│  .model —— 领域模型与日历运算（零 Android 依赖，叶子包）                    │
│  Course · Semester · ScheduleUtils · BellSchedule · BellSchedules          │
└──────────────────────────────────────────────────────────────────────────┘
```

**包之间的依赖，全部单向，无环：**

```
com.weihe.schedule（根包）  ──►  .ui  .data  .rule  .importer  .design  .model
.ui                          ──►  .design
.data                        ──►  .model  .design
.rule                        ──►  .model
.importer                    ──►  .model  .design
.design                      ──►  .model
.model                       ──►  （叶子，不依赖任何其它包）
```

`ScheduleUtils` 不知道 `CourseQuery`；`CourseQuery` 不知道 Activity；`MiniJson` 不知道 `SharedPreferences`。

**两处例外，都是刻意划的边界，不是分层失误：**

| 位置 | 情况 | 原因 |
|---|---|---|
| `.ui` → `.design` | `UiTokens` 与 `Palette` 分在两个包 | `UiTokens.px(Context,dp)` 委托 `UiKit.dp`，而 `UiKit` 又用 `UiTokens` 的常量。两者放一起才不会让 `.design` 与 `.ui` 互相 import |
| `.data` / `.importer` → `.design` | 存储层与导入器都读 `Palette` | `CourseRepository.createSampleCourses` 与 `TimetableImportParser.colorFor` 都要"取一个课程颜色"。把颜色放进 `.ui` 会让它们依赖界面层，所以单独立 `.design` |

**打破"零 Android 依赖"的地方**（已在代码注释中说明）。整理成包之后，这一列同时说明了
**哪些包无法整体脱离 Android 测试**：

| 类 | 所在包 | 打破方式 | 原因 |
|---|---|---|---|
| `CourseRepository` | `.data` | `android.content.Context` / `SharedPreferences` | 存储适配层本来就该依赖平台 |
| `UiTokens` | `.ui` | `px(Context,float)` 委托给 `UiKit.dp` | 唯一的越界方法，`UiTokens.java:139-141`。也是它留在 `.ui` 而不是进 `.design` 的原因 |
| `Palette` / `Colors` | `.design` | **不打破**，纯 `int` 与算术 | 这正是 `PaletteContrastTest` 能在 JVM 跑的原因 |
| `MiniJson` | `.data` | **不打破**，手写而非用 `org.json` | `org.json` 在 JVM 上是 null 桩，不可测 |
| `.model` / `.rule` / `.importer` 全部 14 个类 | 各自 | **不打破** | 全部可在 JVM 上直接测试 |

**测试与生产的边界**：`CourseRepository` 有一个包级静态测试缝
`CourseRepository.useStoreForNextInstance(SharedPreferences)`（`data/CourseRepository.java:87`），
背后是 `private static final ThreadLocal<SharedPreferences> pendingPreferences`（同文件 `:91`），
由 `CourseRepository(Context)`（同文件 `:65`）在 `context == null` 时消费并 `remove()`。
这是让 `CourseRepositoryTest` 能跑到**真实构造函数**（含迁移逻辑）的唯一手段。

正因为这个缝是**包级私有**的，`CourseRepositoryTest` 与 `FakePreferences` 必须与
`CourseRepository` 同包——这就是它们位于 `app/src/test/java/com/weihe/schedule/data/` 的原因。
Java 的包级私有访问按包名判定，测试源集与主源集包名相同即可，**不需要把任何成员放宽成 `public`**。
同理，`WeekGridRowsTest` 留在根包，因为 `WeekGridActivity.lastPeriodInUse` 与 `dayColumnRows`
也是包级静态方法，供测试直接调用。

---

## 七、导航关系

### 7.1 关系图

```
                        ┌─────────────────────────────┐
                        │  MainActivity               │
                        │  「我的课表」（LAUNCHER）    │
                        └──────────────┬──────────────┘
                                       │
      ┌────────────────────┬───────────┼────────────────────┬──────────────────┐
      │ startActivity      │           │                    │                  │
      │ (无 extra)         │           │ EXTRA_WEEK         │ EXTRA_COURSE_ID  │
      ▼                    ▼           ▼                    ▼   + EXTRA_DAY    │
┌───────────────┐  ┌──────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│SettingsActivity│ │SemesterList  │  │ WeekGridActivity │  │EditCourse      │ │
│  「设置」      │ │Activity      │  │  「课表总览」     │  │Activity        │ │
└───────┬───────┘ │  「学期」     │  └────────┬─────────┘  │「编辑/添加课程」│ │
        │         └──────┬───────┘           │            └────────────────┘ │
        │                │                   │ EXTRA_COURSE_ID + EXTRA_DAY   │
        │                │ setResult(         │  (openEditor, 见下)           │
        │                │  RESULT_SWITCHED)  └───────────────────────────────┘
        │                │ finish()
        │                ▼
        │          (回到 MainActivity，onResume 里 reload())
        │
        ├── startActivity ──► SemesterListActivity     SettingsActivity.java:210
        ├── startActivity ──► ScheduleListActivity     SettingsActivity.java:241
        │                          │
        │                          │ startActivityForResult(EXTRA_INDEX, 2001)
        │                          ▼
        │                     EditScheduleActivity       ScheduleListActivity.java:215-216
        │                          │ setResult(RESULT_OK); finish()
        │                          ▼ onActivityResult → render()   ScheduleListActivity.java:55-57
        │
        └── startActivityForResult(ACTION_OPEN_DOCUMENT, 3001)   SettingsActivity.java:389
                                   ▼
                            系统文件选择器（SAF）
                                   │ onActivityResult → importTimetable(uri)
                                   ▼ SettingsActivity.java:69-75
```

### 7.2 跳转清单（全部实际调用点）

| 从 | 到 | 方式 | 传递 | 证据 |
|---|---|---|---|---|
| `MainActivity` | `SettingsActivity` | `startActivity` | 无 | `MainActivity.java:281` |
| `MainActivity` | `SemesterListActivity` | `startActivity` | 无 | `MainActivity.java:743` |
| `MainActivity` | `WeekGridActivity` | `startActivity` | `EXTRA_WEEK = "week"`（int，当前选中周） | `MainActivity.java:736-738` |
| `MainActivity` | `EditCourseActivity` | `startActivity` | `EXTRA_COURSE_ID = "course_id"`（long，0=新建）、`EXTRA_DAY = "day"`（int） | `MainActivity.java:1300-1304` |
| `MainActivity` | 系统文件选择器 | `startActivityForResult` | `REQUEST_IMPORT_TIMETABLE = 1001` | `MainActivity.java:1169` ⚠ **无 `onActivityResult` 接收方，见 PROJECT_ANALYSIS.md** |
| `WeekGridActivity` | `EditCourseActivity` | `startActivity` | `EXTRA_COURSE_ID`（0=新建）、`EXTRA_DAY` | `WeekGridActivity.java:307-318` |
| `SettingsActivity` | `SemesterListActivity` | `startActivity` | 无 | `SettingsActivity.java:210` |
| `SettingsActivity` | `ScheduleListActivity` | `startActivity` | 无 | `SettingsActivity.java:241` |
| `SettingsActivity` | 系统文件选择器 | `startActivityForResult` | `REQUEST_IMPORT_TIMETABLE = 3001` | `SettingsActivity.java:389` |
| `ScheduleListActivity` | `EditScheduleActivity` | `startActivityForResult` | `EXTRA_INDEX = "index"`（int，−1=新建）、`REQUEST_EDIT = 2001` | `ScheduleListActivity.java:215-216` |
| 所有子页 | 上一页 | `UiKit.detailAppBar(this, title, subtitle, view -> finish())` 的返回箭头 | — | 见 `UiKit.java:575` |

### 7.3 返回值约定

| 常量 | 值 | 定义处 | 语义 |
|---|---|---|---|
| `SemesterListActivity.RESULT_SWITCHED` | `RESULT_FIRST_USER` | `SemesterListActivity.java:32` | 切换了当前学期，调用方应重读 |
| `EditCourseActivity` 结果 | `RESULT_OK` | `EditCourseActivity.java:773, 787` | 课程已保存 / 已删除 |
| `EditScheduleActivity` 结果 | `RESULT_OK` | `EditScheduleActivity.java:341, 375` | 作息表已保存 / 已删除 |
| `MainActivity.REQUEST_IMPORT_TIMETABLE` | 1001 | `MainActivity.java:42` | ⚠ 无接收方 |
| `SettingsActivity.REQUEST_IMPORT_TIMETABLE` | 3001 | `SettingsActivity.java:36` | 文件选择器 |
| `ScheduleListActivity.REQUEST_EDIT` | 2001 | `ScheduleListActivity.java:26` | 作息表编辑器 |

### 7.4 返回逻辑

- **全部靠系统返回**：只有 `MainActivity` 是 `exported=true` 的 LAUNCHER Activity；其余 6 个都在 `MainActivity` 之上，`finish()` 即返回。
- **返回箭头**：7 个页面里 6 个子页用 `UiKit.detailAppBar(...)` 统一画返回箭头，`MainActivity` 无返回箭头（它是根）。
- **`onBackPressed` 没有任何重写**（全仓库 0 处）。
- **没有 `onSaveInstanceState` / `onRestoreInstanceState` 重写**（全仓库 0 处）→ 见 PROJECT_ANALYSIS.md 的生命周期问题。
- **没有 Deep Link、没有 `<intent-filter>` 除 LAUNCHER 外任何一项**（`AndroidManifest.xml:41-44`）。
- **没有 Navigation Component、没有 FragmentManager、没有 Fragment**。

### 7.5 状态刷新约定

每个可被返回的 Activity 在 `onResume()` 里调 `repository.reload()` 再重绘，这是本项目处理"另一个界面改了数据"的统一手段：

| Activity | 是否有 `reload()` | 证据 |
|---|---|---|
| `MainActivity` | ✅ | `MainActivity.java:88-102` |
| `WeekGridActivity` | ✅ | `WeekGridActivity.java:131-142` |
| `SettingsActivity` | ✅ | `SettingsActivity.java:57-66`（`onCreate` 不调用 `refresh()`，首次绘制来自 `onResume`） |
| `SemesterListActivity` | ✅ | `SemesterListActivity.java:48-57` |
| `ScheduleListActivity` | ✅ | `ScheduleListActivity.java:43-52`（`onCreate:40` 也 render 一次，所以首次会画两遍） |
| `EditCourseActivity` | ❌ 无 `onResume` | 对话框式编辑器，刻意不重读以免覆盖用户输入 |
| `EditScheduleActivity` | ❌ 无 `onResume` | 同上 —— **但它是按 index 写入的，缓存不刷新就成了正确性问题，见 PROJECT_ANALYSIS.md** |

### 7.6 结果回传的实际使用情况

`setResult` 在 4 处被调用，但**只有 1 处真正被接收**：

| 设置方 | 值 | 调用方是否 `startActivityForResult` | 是否被接收 |
|---|---|---|---|
| `SemesterListActivity:202, 295` | `RESULT_SWITCHED` | ❌ 两处都是 `startActivity` | **否**，靠调用方 `onResume` + `reload()` |
| `EditCourseActivity:773, 787` | `RESULT_OK` | ❌ 两处都是 `startActivity` | **否**，同上 |
| `EditScheduleActivity:341, 375` | `RESULT_OK` | ✅ `ScheduleListActivity:216` | ✅ `ScheduleListActivity:55-57` |
| `MainActivity:1169` | 请求码 1001 | — | **无 `onActivityResult`，且 `openImportPicker()` 无调用点**（死代码） |


---

## 八、资源清单

| 类型 | 数量 | 说明 |
|---|---|---|
| `anim/` | 7 | `dialog_in`、`dialog_out`、`page_in`、`page_out`、`page_still`、`sheet_slide_up`、`sheet_slide_down` |
| `drawable/` | 10 | 全部 24dp `vector`，Material 图标：`ic_add`、`ic_back`、`ic_chevron_left`、`ic_chevron_right`、`ic_grid`、`ic_location`、`ic_person`、`ic_school`、`ic_semester`、`ic_settings`、`ic_today`（共 11 个文件） |
| `mipmap-anydpi/` | 2 | `ic_launcher.xml`、`ic_launcher_round.xml`（`<adaptive-icon>` 三层：background/foreground/monochrome） |
| `mipmap-{mdpi…xxxhdpi}/` | 25 | 每密度 5 个 PNG：`ic_launcher`、`ic_launcher_round`、`ic_launcher_background`、`ic_launcher_foreground`、`ic_launcher_monochrome` |
| `values/` | 4 | `animations.xml`（3 个 style）、`colors.xml`（3 个颜色）、`strings.xml`（**只有 1 条 `app_name` = 拾课**）、`themes.xml` |
| `values-v27/` | 1 | `themes.xml`，**完整副本** + `windowLightNavigationBar`（原因见 ARCHITECTURE.md 第 2 条：style 不跨配置目录合并） |
| 合计 | 50 个文件 | |

---

## 九、本次分析的方法与限制

**方法**
1. 逐文件通读 `app/src/main/java/` 全部 31 个源文件（重点文件由本人直接读，编辑器/列表页与周网格/导入由子代理并行全读并回报带行号的结论）。
2. 逐文件通读 `app/src/test/java/` 全部 18 个文件（子代理全读并逐条计数）。
3. 只读检查构建配置：`build.gradle`×2、`settings.gradle`、`gradle.properties`、`gradle-wrapper.properties`、`local.properties`、`AndroidManifest.xml`、`proguard-rules.pro`、`.gitignore`。
4. 只读运行检查：`adb devices`、`adb shell run-as com.weihe.schedule cat .../shared_prefs/weihe_schedule.xml`（读取真实设备上的 23 门课与两张作息表配置）、`adb shell uiautomator dump` + `tools/dump-ui.ps1` / `tools/tap-ui.ps1` 逐屏核对。

**限制**
- 未运行 Gradle（遵守"只分析、不构建"的约束），因此"221 项测试全绿 / lint 零问题"引用的是仓库内 `ARCHITECTURE.md:86-89` 记录的上一次结果，本次未复现。
- 真机运行检查使用的是当前模拟器上的数据（23 门课，与首次安装的 9 门样例课不同）。
- `SettingsActivity` / `SemesterListActivity` / `ScheduleListActivity` 的 `onResume` 是否调用 `reload()` 未逐一核对，标注为"待确认"。
- 未做深色模式、横屏、刘海屏的实机检查。
