# AGENT_HANDOFF — 拾课（WeiheSchedule）当前状态交接

> ## 权威声明
>
> - **当前项目状态：以本文件为准。**
> - **长期开发规范：以 [`AGENTS.md`](AGENTS.md) 为准。**
> - **历史记录：[`AGENT_HANDOFF_ARCHIVE.md`](AGENT_HANDOFF_ARCHIVE.md)（仅作背景参考，其中的完成度判断已过期）。**
> - 项目介绍与构建方式：[`README.md`](README.md)。
>
> 本文件由核对**当前源代码、Git 状态与实测结果**后重写（2026-10-12）。
> 上一版 1338 行的全文（含 E0–E4 逐轮开发日志）已**整段并入归档**，未删除任何内容。
> **任何"已完成"的判断都必须打开代码确认；本文件也可能滞后。**

---

## A. 更新时间与核实范围

| 项 | 值 |
|---|---|
| 更新时间 | **2026-10-14**（上一版 2026-10-13） |
| 核实方式 | 逐项对照当前源码、`git status/diff` 与**实跑结果**；六阶段修复**逐个文件确认是否真的落地**，不采信任何一轮的自述 |
| 本轮实际执行 | `git status/diff/numstat`、逐阶段关键代码标记核查、`--rerun-tasks testDebugUnitTest assembleDebug lintDebug`、仓库 blob 换行字节核查 |
| 本轮代码改动 | **1 个生产文件**（`CourseRepository.restoreFrom` 的拒绝提交路径）+ `.gitattributes`；其余为复审结论 |
| 未验证 | **模拟器与真机全部未跑**；六阶段中有两处失败路径无测试（见 B-2） |

> **2026-10-14 复核要点（重要）**：前六轮"修复"里**只有四轮真的留下了代码**
> （`MiniJson` 深度限制、`BackupCodec` 版本校验、`ReminderScheduler` 注册顺序、
> `ExcelTimetableReader` 规模校验）。**"备份恢复一致性"那一轮当轮即被整体撤销**，
> 本轮才补上能安全落地的一半 —— 详见 **B-2**，那是当前唯一权威的阶段状态表。
> 另：上一版关于 `gradlew` CRLF 的历史结论**已被证伪**，见 B-2.1。

---

## B. 当前项目状态（Git）

| 项 | 值 |
|---|---|
| 分支 | `main` |
| HEAD | **`9702b8b`**（未新建提交、未推送） |
| `origin/main` | 上一版的 `8ee1a04` 之后项目又有提交，`9702b8b` 为本轮起点 |
| 已暂存 | 无 |
| 未跟踪 | **2 项**：`.gitattributes`（新增，待提交）、`weihe_schedule_audit.zip`（非本 Agent 创建，**未触碰**） |
| 已修改（未暂存） | **7 个文件**，合计 **+463 / −36**（逐项见 B-2） |

### B-2. 六阶段修复的当前真实状态（2026-10-14 逐个文件核实）

> ⚠️ **本节取代 D / E / G / I 各节中与此冲突的描述。** 判断依据是**当前源码里的代码**
> 与**实跑结果**，不是任何一轮的交付说明。

| 阶段 | 结论 | 代码依据 | 测试证据 | 未解决 |
|---|---|---|---|---|
| **一 备份恢复一致性** | **部分实现** ⚠️ | `data/CourseRepository.java` **+24/−4**：`restoreFrom()` 在 `!editor.commit()` 后先 `captureRestoreScope().equals(snapshot)` 核验 ✓ 不一致则 `reload()` + `RestoreReport.needsAttention(...)` ✓ **刻意未实现回滚**（回滚是又一次可能同样被拒的提交） | `data.RestoreProtocolTest` **26 项通过** —— 其中"干净拒绝仍报 unchanged"的既有用例仍绿，证明整洁路径未被破坏 | **P1**：`FakePreferences` **无法**模拟"拒绝但内存已变"，该分支**零测试覆盖**。另 `CourseRepository` **L715 / L766** 注释仍写 *"an editor applies all-or-nothing"* —— **同源缺陷**，导入起始日期与 `courses_backup` 写入**未修** |
| **二 JSON 深度** | **已实现并已验证** ✅ | `data/MiniJson.java` **+68/−0**：`MAX_DEPTH = 32`；`readObject()`/`readArray()` 先 `enter()` 再调 `readObjectBody()`/`readArrayBody()`，正常返回时 `leave()` 回退 | `data.MiniJsonTest` **9 → 16 项通过**：恰好 32 层可解、33 层被拒、**5000 层被拒不溢出**、扁平 128 项不误判为深 | 调用方 `BackupCodec.parse` 把 `IllegalArgumentException` 转为 `null` 并给出「文件不是合法的 JSON 文本」—— **源码已确认** ✓ |
| **三 版本号溢出** | **已实现并已验证** ✅ | `data/BackupCodec.java` **+42/−7**：新增 `wholeNumberOrNull()`（只收 JSON 整数，拒字符串/布尔/null/容器/小数/NaN/Inf）；**先按 `long` 比较，再 `(int)` 收窄** | `data.BackupCodecTest` **67 → 79 项通过**：`4294967297`、2³²、2×2³²+1、1e30、−1、0、1.5、`"1"`、null、布尔全部被拒 | `asLong()` 对**其它字段**仍宽容（接受字符串、截断小数）—— 各字段另有范围校验，未发现可利用绕过 |
| **四 提醒调度** | **已实现但尚未充分验证** ⚠️ | `reminder/ReminderScheduler.java` **+70/−21**：**先注册、后仅取消新计划未复用的槽位**（`usesSlot`）；台账只记录**实际写入成功**的条目；`sync()` 改为返回 `register()` 的实际条数 | `reminder/ReminderScheduleTest` **34** ✓ `rule.ReminderPlanTest` **68** ✓ —— **但都是计划计算**，失败路径**零覆盖** | **P1**：`AlarmManager`/`PendingIntent` 无法在 JVM 测试中伪造（`unitTests.returnDefaultValues = false`），需先引入注入缝隙 |
| **五 导入 / 构建 / 发布** | **部分实现** ⚠️ | `importer/ExcelTimetableReader.java` **+49/−4**：`MAX_ROWS = 500` / `MAX_COLUMNS = 100` / `MAX_CELL_LENGTH = 1000`，**在 `new TimetableGrid(...)` 之前检查**（该构造会分配 4 个 `rows×cols` 数组）；`.gitattributes` 新增 | `importer.ExcelTimetableReaderTest` **13** ✓ `importer.TimetableImportParserTest` **42** ✓ —— **新增边界用例未写** | **P2**：超限行/列、超长单元格、空表的用例待补 |

#### B-2.1 阶段五历史结论更正：`gradlew` **不是** CRLF

| 历史说法 | 实测事实 |
|---|---|
| "归档中的 `gradlew` 使用 CRLF，Linux 下会报 `$'\r'`" | **不成立** ✗。`git show HEAD:gradlew` **不含 CR**（blob 8,752 字节）；工作区 `gradlew` 为**纯 LF（251 LF / 0 CRLF）**；`gradlew.bat` 的 **blob 也不含 CR**（工作区因检出而是 CRLF，正确） |

**真实隐患与已采取的措施**：本机 `core.autocrlf = true`，而仓库原本**没有 `.gitattributes`** ——
换一台机器 clone，`gradlew` 的字节就取决于该机器的 Git 配置。已新增 `.gitattributes`
（`* text=auto eol=lf`；`*.bat`/`*.cmd` → `eol=crlf`；`gradlew`/`*.sh` → `eol=lf`；二进制扩展名标 `binary`）。
**生效证据**：加该文件后 git 警告由 "LF will be replaced by CRLF" 变为 **"CRLF will be replaced by LF"**。

#### B-2.2 发布阻断项（未修，禁止擅自处理）

| # | 问题 | 依据 |
|---|---|---|
| 1 | **`release` 无 `signingConfig`** | `app/build.gradle` 的 `release {}` 只有 `minifyEnabled false` 与 proguard 配置；`signingConfigs` 只有 `localDebug` → `assembleRelease` 产出**未签名 APK**，不可安装、不可上传 |
| 2 | **`targetSdk 34` 的 Play 合规性未核实** | 本轮抓取官方页面**网络失败**，故**不对现行要求的版本号或日期下任何断言**。当前：`compileSdk 34` / `targetSdk 34` / `minSdk 26` / `versionCode 1` / `versionName 1.0`；AGP 8.13.0、Gradle 8.13、Java 17 |

#### B-2.3 本轮实跑结果

```
& '.\.gradle-local\bin\gradle.bat' "-Dorg.gradle.vfs.watch=false" --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

**54 个测试类 / 999 项 / 999 通过 / 0 失败 / 0 错误**；`assembleDebug` BUILD SUCCESSFUL；
`lintDebug` `No issues found.`。**模拟器与真机均未执行。**

---

### ✅ 上一版的两个遗留问题均已解决（2026-10-13 复核）

| 上一版记录 | 现在的事实 |
|---|---|
| 「6 个项目文档被删除且**未暂存**」（旧 G-1） | **删除已提交**在 `8ee1a04` 中，与「重写 `AGENT_HANDOFF.md` + 新建 `AGENT_HANDOFF_ARCHIVE.md` + 新增 `README.md`」同属一次**有意的文档整理提交**。`git ls-tree HEAD` 中已无这 6 个文件。内容仍可从 `f0765a6` 取出 |
| 「`f0765a6` 未推送」（旧 G-2） | **已推送**。`f0765a6` 与 `8ee1a04` 都在 `origin/main` 上，**没有任何未推送提交** |

**最近提交**

```
8ee1a04 (HEAD -> main, origin/main, origin/HEAD)  update   ← 文档整理：改 AGENTS.md、重写本文件、
                                                             新建归档与 README、删除 6 份设计文档
f0765a6                                           update   ← 58 个文件；把此前积累的全部未提交工作一次性提交
bdd7d01                                           update
61d9da5                                           update
6875549                                           update
```

`f0765a6` 提交了此前跨多轮、由**多位 Agent** 留下的全部改动（配色、每节时长与动态节次、
默认作息表、导入开学日期与学期总周数、滚轮选择器、同名课程颜色同步等），
以及 `AGENTS.md`、两份交接文档与 `.workbuddy-ai/memory/`。

---

## C. 项目技术架构

| 项 | 值（以 `app/build.gradle` / `build.gradle` 为准） |
|---|---|
| 语言 / UI | **纯 Java**（0 个 `.kt`）；Android Views，**全部在 Java 中构建**，`res/layout` **不存在** |
| AGP / Gradle | **8.13.0** / **8.13** |
| Java | `sourceCompatibility`/`targetCompatibility` = **17**（本机 JDK 20 构建通过） |
| SDK | `compileSdk` **34** / `targetSdk` **34** / `minSdk` **26** |
| 依赖 | **仅两个**：`libs/jxl-2.6.12.jar`、`junit:junit:4.13.2` |
| 未使用 | AndroidX / Material / Kotlin / Compose / Room / 网络库 / 第三方 UI |

### 模块划分

```
app/src/main/java/com/weihe/schedule/
  *Activity.java   10 个页面（根包）
  model/   5 类  Course / Semester / BellSchedule / BellSchedules / ScheduleUtils
  data/    9 类  CourseRepository（唯一数据入口）/ MiniJson / SemesterJson /
                 BellScheduleJson / BackupCodec / DamagedCourseCopy …
  rule/   17 类  纯逻辑：查询、冲突、校验、草稿、学期命名与周数、颜色同步、周网格计划 …
  reminder/ 6 类 提醒计算、调度、广播接收
  importer/ 4 类 Excel 读取与课表解析
  ui/      5 类 UiKit / UiTokens / Pickers / WheelColumnView / Insets
  design/  2 类 Palette / Colors（WCAG 对比度）
```

### Activity（行数为当前实际值）

`MainActivity` 首页 · `WeekGridActivity` 课表总览与单周课表 · `EditCourseActivity` 课程编辑 ·
`CourseDetailActivity` 课程详情 · `CourseSearchActivity` 搜索 · `SettingsActivity` 设置 ·
`EditScheduleActivity` 作息表编辑 · `ScheduleListActivity` 作息表列表 ·
`SemesterListActivity` 学期列表 · `BackupPreviewActivity` 备份预览与恢复

### 数据存储（**不要改动格式**）

| 项 | 值 |
|---|---|
| 主存储 | `SharedPreferences("weihe_schedule")`，**唯一访问点** `data/CourseRepository` |
| 提醒存储 | `SharedPreferences("weihe_schedule_reminders")`（`reminder/ReminderSettings`） |
| 序列化 | 手写 `data/MiniJson` |

**键**：`courses`、`semester_start`、`semesters`、`active_semester`、`bell_schedules`、
`courses_previous`、`courses_backup`、`restore_snapshot`、`restore_pending`；
按学期分键 `courses:<id>`、`semester_start:<id>`。

**格式要点**
- 日期为 `yyyy-MM-dd` 字符串；作息表起止时间为**自午夜起的整数分钟**（`495` = 08:15）；
  晚课写作 `24:xx`（上限 `MAX_MINUTE = 29:59`）
- 课程颜色是每条记录自带的 ARGB 整数（调色板变化**不会**重绘已存课程）
- `bell_schedules` 的每节时长是**可选键**：缺失时按默认 40 分钟解释，不报错

### 重要业务约束（当前值）

| 常量 | 值 | 位置 |
|---|---|---|
| `PERIODS_PER_DAY` | 15（默认展示节次） | `model/ScheduleUtils` |
| **`MAX_PERIODS`** | **30**（网格与解析的硬上限） | `model/ScheduleUtils` |
| `DEFAULT_PERIOD_MINUTES` / `PERIOD_MINUTES` | **40** | `model/BellSchedule` |
| `MIN_PERIOD_MINUTES` … `MAX_PERIOD_MINUTES` | **1 … 180**（每节时长可配置） | `model/BellSchedule` |
| `Course.PARITY_ALL/ODD/EVEN` | 0 / 1 / 2 | `model/Course` |
| `Course.DEFAULT_COLOR` | `0xFF2878B5` | `model/Course` |
| `Palette.COURSE_COLORS` | **9 色**（默认色在首位） | `design/Palette` |

**地点匹配与作息表解析**：`BellSchedules.resolve(schedules, 指定名, 地点, 默认表id)`
→ ① 课程明确指定的作息表 → ② 地点关键字命中（**列表顺序，先命中者胜**）→
③ 用户指定的**默认作息表**（`CourseRepository.getDefaultScheduleId()`）→ ④ 内置标准表。
关键字匹配前先归一化（小写 + 去空白/`-`/`·`/`_`）再做子串包含；
「单个 ASCII 字母 + 楼」型关键字（如 `d楼`）额外接受「该字母紧跟数字」。

---

## D. 功能完成状态

状态取值：**已实现并已验证** / **已实现但尚未充分验证** / **部分实现** / **尚未实现** / **存在问题** / **待确认**

| # | 功能 | 状态 | 依据 |
|---|---|---|---|
| 1 | 首页：当天课程、周次与星期导航、回到今天/本周 | 已实现并已验证 | 设备实测；`rule/TodayTarget` + 测试 |
| 2 | 课表总览（周网格，周一到周日 × 节次） | 已实现并已验证 | 设备实测（本轮滚动测量，见 D-4） |
| 3 | 单周课表（显示每列具体日期） | 已实现并已验证 | 设备实测；与总览共用同一网格 |
| 4 | **左侧节次编号显示不全** | **未复现；判定为已修复（含 20/30 行实测）** | 见下方「D-4 专项结论」 |
| 5 | 课程新增 / 编辑 / 删除 | 已实现并已验证 | 设备实测；`CourseFormValidator` + 测试 |
| 6 | 课程复制（新 ID，原课不变） | 已实现并已验证 | `data/CourseCopyTest`；设备实测过编辑页 |
| 7 | 课程搜索 | 已实现并已验证 | `CourseSearchFlowTest`；设备实测 |
| 8 | 课程详情（分层、颜色侧轨、缺失字段占位） | 已实现并已验证 | 设备实测 |
| 9 | **课程颜色区分度优化（9 色）** | **已实现并已验证** | `Palette.COURSE_COLORS`（9）+ `Course.DEFAULT_COLOR=0xFF2878B5`；`design/PaletteContrastTest`(17) |
| 10 | **颜色选择器：横向单排小圆角矩形** | **已实现并已验证** | 代码：`SWATCH_DP=30`、`SWATCH_GAP_DP=4`、`RADIUS_S`、`HorizontalScrollView`、**无勾号**；设备实测 **9 个 / 1 行 / 30.1dp / 间距 4.2dp** |
| 11 | **同名课程批量同步颜色** | **已实现并已验证** | `rule/CourseColorSync` + `CourseRepository.recolorCourses`；`CourseColorSyncTest`(10) + `CourseColorSyncStorageTest`(10)；设备端到端：两条「矩阵论」同步为同一色 |
| 12 | 教师 / 地点快捷填充 | 已实现并已验证 | `rule/CourseSuggestions` + 测试 |
| 13 | 编辑表单状态恢复（重建 / 进程回收） | 已实现并已验证 | `rule/CourseDraft`、`rule/ScheduleDraft` + 测试 |
| 14 | 学期创建 / 切换 / 删除 | 已实现并已验证 | 设备实测 |
| 15 | 新建学期名称自动顺延 | 已实现并已验证 | `rule/SemesterNaming` + `SemesterNamingTest`；**未做设备实测** |
| 16 | 学期起始日期（周一归一化） | 已实现并已验证 | `SemesterCreationStartTest`（含"新建学期写入自己的开始日期"这一已修缺陷） |
| 17 | **每节课时长自定义（1–180 分钟）** | **已实现并已验证** | `BellSchedule.DEFAULT/MIN/MAX_PERIOD_MINUTES`；逐轮细节见归档 E1 |
| 18 | **动态每日节次数量（上限 30）** | **已实现并已验证** | `ScheduleUtils.MAX_PERIODS`、`WeekGridActivity.lastPeriodInUse/dayColumnRows`、`WeekGridRowsTest` |
| 19 | **自定义默认作息表 + 地点匹配回退** | **已实现并已验证** | `CourseRepository.getDefaultScheduleId()`、`BellSchedules.resolve(..., defaultId)`；细节见归档 E2 |
| 20 | **导入指定开学日期** | **已实现并已验证** | `CourseRepository.replaceCoursesAt(List, Semester, LocalDate)`；细节见归档 E3 |
| 21 | **导入指定学期总周数** | **已实现并已验证** | `replaceCoursesAt(..., int)`；`rule/SemesterWeeks` + `SemesterWeeksTest`；细节见归档 E4 |
| 22 | Excel 导入与异常报告 | **已实现并已验证（2026-10-13 设备端全流程）** | `ExcelTimetableReaderTest`、`TimetableImportParserTest`、`TimetableImportRangeTest` + **设备实测：选文件 → 预览 → 覆盖导入 → 撤销导入**，见 D-5 |
| 23 | 备份导出 / 预览 / 恢复（快照 + 回滚） | 已实现但尚未充分验证 | `BackupCodecTest`；设备实测过入口与预览，**未真正写盘到用户文件并恢复**；2026-10-13 顺带发现导出摘要**把"条安排"说成"门课程"**，见 G-11 |
| 24 | 上课提醒（计算 / 调度 / 重启恢复 / 通知内容 / 自定义提前分钟数） | **已实现并已验证（2026-10-13 设备端全流程）** | `ReminderPlan` / `ReminderScheduler` / `ReminderReceiver` / `ReminderNotifications` / `ReminderSettings`；**本轮新增 1–180 分钟自定义提前时间**，见 D-6 |
| 25 | 日期与时刻统一选择器 | 已实现并已验证 | `ui/Pickers`；全项目系统 `Date/TimePickerDialog` 使用数 **= 0**；设备实测（中文按钮、24 小时制、日历/时分滚轮） |
| 26 | 统一按钮层级 / 危险操作命名 / 空状态 | 已实现并已验证 | 5 处「删除」改为明确动词；首页区分"本学期没有课程"与"这天没有安排"（设备实测后者） |
| 27 | 设置页功能顺序 | 已实现并已验证 | 设备实测：学期管理 → 学期起始日期 → 上课提醒 → 上下课时间 → 课表数据 → 关于 |
| 28 | 作息表保存前的重叠校验（写盘前拦截） | 已实现并已验证 | 设备实测：制造重叠 → 保存被拒 → **存储载荷未变** |

### D-4 专项结论：左侧节次编号（2026-10-12 初查，2026-10-13 复核并扩展到 30 行）

**结论：在 13 / 20 / 30 行三种规模、两个视图上均无法复现，判定为已修复。**

#### 真实根因（已在代码中修掉）

`labelColumn` **不在纵向滚动容器内**（`bodyRow` 里与 `ScrollView` 并列），靠
`SyncedVerticalScrollView.onScrollChanged` 里的 `labelColumn.scrollTo(0, y)` 跟随网格。
两个视图共用同一份网格实现（`render()` + `dayColumnRows` / `lastPeriodInUse`），
所以**不需要两套补丁**。

当年的缺陷是：日列高度用 `rowTop(periods)` 计算，而 `rowTop(p)` 是第 p 行的**上边缘**，
于是每个日列都比它要定位的行**矮整整一行** → 网格内容比编号栏矮一行 →
纵向滚动范围带不出最后一行。修复即 `dayColumnRows(periods)`（见 `WeekGridActivity` 中
`dayColumnRows` 的 javadoc），`WeekGridRowsTest` 为它而写。
引入提交为 **`1213ef7`**（早已进入历史，远早于任何现存截图）。

#### ⚠️ 上一版 D-4 的机制解释是**错的**（本次实测更正）

上一版写「`scrollTo` 会被自身内容高度夹取，**只有内容高度与网格完全相等时才跟得上**」。
两句都不成立：

1. **`View.scrollTo` 不夹取**。它直接写 `mScrollX/mScrollY`（AOSP 实现无 clamp）；
   按内容高度夹取是 `ScrollView` 自己的行为。因此编号栏**跟得上，且与两栏内容高度是否相等无关**。
2. **两个内容高度并不相等**，实测（`1080×2424 @420`，`dp(54) = 142px`）：

   | 行数 | 编号栏内容高 | 网格内容高 | 差 |
   |---|---|---|---|
   | 13 | `13 × dp(54) = 1846` | `dp(13 × 54) = 1843` | 编号栏**高 3px** |
   | 20 | `20 × dp(54) = 2840` | `dp(20 × 54) = 2835` | 编号栏**高 5px** |
   | 30 | `30 × dp(54) = 4260` | `dp(30 × 54) = 4253` | 编号栏**高 7px** |

   原因：编号栏的每一行由 `rowHeight()` 单独取整后**相加**，网格的日列由
   `rowSpan()` 对总高**一次取整**。两者只差几个像素，且**符号取决于屏幕密度**
   （`round(54×density)` 与 `54×density` 的大小关系）——见 G-4。

#### 设备实测（`emulator-5554`，Pixel_10 / API 37 / 1080×2424 @420）

用 `run-as` 写入探针课程（第 15 / 20 / 30 节，周日列，避开既有数据），
再用 `uiautomator dump` 读**真实屏幕坐标**逐行核对（不是目测）。测完已把数据恢复原状。

| 验证项 | 实测结果 |
|---|---|
| 13 行（当前学期最晚第 13 节） | 编号 1–13 齐全；`最优控制与状态估计` 块顶 1006 vs 编号 6 顶 1007 → **Δ=1px** |
| 20 行（探针在第 15、20 节） | 编号 1–20 齐全、每行**均匀 142px**；`探针15` 块顶 1844 vs 编号 15 顶 1847 → **Δ=3px**；`探针20` 与编号 20 对齐 |
| 30 行（上限，探针在第 30 节） | 编号 1–30 齐全；`探针30` 与编号 30 对齐，**最后一行完整可见** |
| 总课表 | 滚到底、滚到中段、横向滚到周五/周六/周日，编号栏**始终跟随**，Δ ≤ 3px |
| 单周课表 | 同上，编号 10–20 与课程行对齐，末行完整 |
| 底部遮挡 | 30 行时最后一行底边 **y=2145**，FAB 顶边 **y=2172** → **未被遮挡**（`FAB_CLEARANCE_DP=78` 与 FAB 几何 22+56=78dp 精确吻合） |
| 视图切换 | 总课表 ↔ 单周课表反复切换（含行数由 30 降到 10 的收缩路径）**未崩溃** |
| 星期标题 | 横向滚动时随网格同步滑动，未造成节次错位 |

- **未在真机验证**（只有模拟器）。
- **未验证**：改系统字号后的网格（`periodCell` 用 `BODY_LARGE=14sp`，固定 142px 行高，
  200% 字号下文字约 74px，理论上不裁切，但**未实测**）。
- 遗留的结构脆弱性见 G-4。

### D-5 专项结论：Excel 导入全流程（2026-10-13 设备实测，首次走通）

样本沿用 E3/E4 留下的 `build/importtest/timetable.xls`（**仓库里没有 `.xls` 样本**，
`build/` 被 gitignore）。先在 JVM 上用 `ExcelTimetableReader + TimetableImportParser`
读回确认，再推到 `/sdcard/Download/` 走设备。

| 环节 | 实测结果 |
|---|---|
| JVM 预校验 | `usable=true courses=4 issues=0`，5 条安排；周次/单双周/节次全部符合文件 |
| 入口 | 设置 → **课表数据 → 导入课表**（`当前共 23 条安排 · 从 Excel 文件导入`）→ 系统 `ACTION_OPEN_DOCUMENT` |
| 选择器 | 打开即停在 **Downloads**，`timetable.xls` 可见可点 ✅ |
| 预览弹窗 | **`识别到 4 门课程，共 5 条安排`**、**`文件内容已全部识别。`**（0 issues）、`导入将覆盖当前课表`、`导入到：2026 秋季学期 · 文件第 1 周对应本学期第 1 周` |
| 默认开学日期 | **`2026-09-14`**（= 设置页已存值，**不是今天**）✅；生效行 `2026-09-14，即本学期第 1 周周一` |
| 默认总周数 | **`18 周`**（= 目标学期已存值）；范围行 `9/14 - 1/17，共 18 周` |
| 覆盖导入 | 因日期与周数均未改，**不弹二次确认**，直接落盘 |
| **落盘内容（逐字段核对）** | 高等数学 `w1-16 parity=0` 周一 p1 A101；大学英语 `w1-16 parity=1` 周一 p2 B202；数据结构 `w1-8` + `w11-16`（**两条**）周三 p1 C303；线性代数 `w2-16 parity=2` 周五 p3 D404 —— **与文件逐字段一致** ✅ |
| 非连续周次 | `1-8,11-16周` → **两条记录**（不是一条被拉平的 `1-16`）✅ |
| 同课同色 | 两条「数据结构」颜色相同（`colorFor(code+name)` 哈希）✅ |
| 学期设置未被改动 | `semester_start` 仍 `2026-09-14`；`semesters` 仍 `[{"id":…,"start":"2026-09-14","name":""}]`（**未新增 `weeks` 键**）✅ |
| 撤销快照 | 写入 `courses_previous:1791793712068`，内含**导入前的 23 条** ✅ |
| 首页 | `2026 秋季学期 · 9/14 开学`；`第5周 · 单周`；今天（10/13 周二）无课 → 正确空状态 ✅ |
| 总课表 | `5 条安排`；大学英语带 **`· 单周`**；数据结构显示为 `第1-8周` 与 `第11-16周` **两块** ✅ |
| **单双周过滤（第 5 周 / 单周）** | 大学英语**在**、线性代数**不在**（周五列空）✅ |
| **单双周过滤（第 6 周 / 双周）** | 线性代数**在**（周五 D404）、大学英语**不在** ✅ |
| 撤销上次导入 | 二次确认文案完整（含「每次导入只能撤销一次」）；确认后回到 **`23 条安排`** ✅ |
| 撤销的数据等价性 | 与导入前**逐字段集合相等**（含颜色、周次、单双周、教师、地点）；`semester_start` / `semesters` / `bell_schedules` / `default_schedule_id` **逐字节相同**；**唯一差异是快照键按设计被消费** ✅ |

**结论：Excel 导入的「选文件 → 预览 → 确认 → 结果 → 撤销」全流程在设备上走通，
且导入结果与文件内容逐字段吻合、单双周与周次规则正确、学期设置未被波及。**

- 未验证：导入**损坏/不可读**文件（设备上有 `broken.xls` / `unreadable.xls` 样本，
  以及真实学校文件 `2026-2027第一学期-学期课表.xls`），未走这两条错误分支。
- 未验证：`.xlsx`（jxl 只支持旧式 `.xls`，代码即如此设计）。
- 顺带发现的**既有**缺陷见 G-11。

### D-6 专项结论：上课提醒（2026-10-13 核实 + 新增自定义提前分钟数）

#### 改动前通知的**实际**形态（以代码为准，非文档推断）

| 项 | 改动前 | 改动后 |
|---|---|---|
| 发送方式 | **Android 系统通知**（`NotificationManager.notify`），无应用内提示 | 同 |
| 渠道 | `class_reminders`「上课提醒」，`IMPORTANCE_HIGH`，`enableVibration(true)`，描述「在课程开始前提醒你，包含课程、时间与教室。」 | 未改 |
| **标题** | **只有课程名**（`reminder.courseName()`） | **`上课提醒 · <课程名>`** |
| **正文** | `M月d日 EEEE HH:mm · 地点 · 教师` + `\n` + `N 分钟后开始` | `M月d日 周X HH:mm · 第 X-Y 节 · 地点 · 教师` + `\n` + `N 分钟后开始`（**新增节次**；星期改为应用自己的中文名） |
| 展开 | `BigTextStyle`（可展开） | 同 |
| 时间戳 | `setWhen(上课时刻)` + `setShowWhen(true)` → 通知头显示「in 5h」 | 同 |
| 声音/振动 | 渠道默认声音 + `enableVibration(true)`；代码**未**设自定义 sound | 未改 |
| **点击** | 打开 **`MainActivity`**（首页），**不带任何 extra** → 与那门课无关 | **打开 `CourseDetailActivity`**，带 `EXTRA_COURSE_ID`（**稳定课程 id**） |
| 通知 id | `notificationId(courseId, epochDay)` | 未改 |
| 计划窗口/上限 | 7 天 / 最多 10 条 | 未改 |
| 调度 | `AlarmManager.setAndAllowWhileIdle`（**故意非精确**，不申请 `SCHEDULE_EXACT_ALARM`） | 未改 |
| 取消/重建 | 每次 `sync()` **先按旧 ledger 取消全部槽位**，再重新注册 → 幂等、无重复 | 未改 |
| 重启恢复 | `ReminderRestoreReceiver` 监听 `BOOT_COMPLETED`/`TIME_SET` 等重建 | 未改 |
| 提前分钟数 | `lead_minutes`（int，存于独立 `SharedPreferences("weihe_schedule_reminders")`），**只允许 5/10/15** | **1–180**，快捷项仍为 5/10/15，另有「自定义」 |

#### 本轮代码改动

1. **`rule/ReminderPlan`**：`MIN_LEAD_MINUTES` 5 → **1**，`MAX_LEAD_MINUTES` 15 → **180**；
   新增 **`QUICK_LEAD_MINUTES = {5,10,15}`**（快捷项**不再**由带宽端点推导——否则会变成
   「1 分钟」「180 分钟」）；新增纯函数 **`parseLeadMinutes(String)`**（拒绝而非修复：
   空、空白、单词、`2.5`、`-10`、`25 分`、`0`、`181`、超 int 全部返回 null）。
2. **`rule/Reminder`**：新增 `startPeriod`/`endPeriod`（含 equals/hashCode），供通知显示节次。
3. **`reminder/ReminderNotifications`**：标题加前缀；正文加节次；**星期改用
   `ScheduleUtils.dayName()`**（不再用 `EEEE`，见下）；点击目标改为课程详情，
   并**去掉 `FLAG_ACTIVITY_CLEAR_TOP`**——详情页在 `onCreate` 读 extra 且**没有 `onNewIntent`**，
   带上 CLEAR_TOP 会把新 id 交给一个不会重读的旧实例，用户点 A 课却看到 B 课。
4. **`SettingsActivity`**：第四个「自定义」色块 + 数字输入对话框（`TYPE_CLASS_NUMBER`）。
   校验失败用既有 `EditText.setError` 习惯（`Action.validated` 返回 false → 对话框不关）。

#### 兼容性：老用户不受影响

`lead_minutes` 的键与含义都没变，只是**可接受区间变宽**（5..15 ⊂ 1..180），
所以旧值仍被原样读回。**设备实测**：改动前设备上 `lead_minutes=15`，
升级后设置页仍显示 **`已开启 · 提前 15 分钟 · 已安排 10 条提醒`**，
且 `15 分钟` 色块处于选中态 ✅。`ReminderSettings.leadMinutes()` 对越界值仍回落到默认 10（未改）。

#### 设备实测（`emulator-5554`，Pixel_10 / API 37 / 1080×2424 @420）

| 验证项 | 结果 |
|---|---|
| 四个色块一行放下 | `5 分钟`(79–230) `10 分钟`(251–419) `15 分钟`(440–610) `自定义`(631–788)，右侧余 ~210px ✅ |
| 对话框 | 标题 `自定义提前时间`、正文 `提前多少分钟提醒（1–180 分钟）`、输入框预填当前值 15 ✅ |
| 键盘 | **数字键盘**（`TYPE_CLASS_NUMBER`）✅ |
| 超范围 `999` | 弹 **`请填写 1–180 之间的整数`**，**对话框不关**，`lead_minutes` 仍 15 ✅ |
| 空输入 | 弹 **`请填写提前提醒的分钟数`**，同样不关、不写 ✅ |
| 保存 25 | `lead_minutes` 15 → **25**；状态行 **`已开启 · 提前 25 分钟 · 已安排 10 条提醒`**；第 4 个色块变 **`25 分钟`** 且选中 ✅ |
| **重新调度** | ledger **仍是 10 条、槽位与课程完全不变**，**每条触发时刻精确前移 10 分钟**（25−15）→ 旧闹钟被替换而非并存 ✅ |
| 重启后 | `am force-stop` 再开：`lead_minutes=25`，页面显示 `提前 25 分钟` + `25 分钟` 色块 ✅ |
| **通知真实内容** | `dumpsys notification --noredact` 读到：channel `class_reminders`、`BigTextStyle`、`AUTO_CANCEL`、importance 4；标题 **`上课提醒 · 最优控制与状态估计`**；正文 **`10月13日 周二 13:20 · 第 6-7 节 · 教学楼F楼F106多 · 李海波`** + `25 分钟后开始` ✅ |
| 状态栏/通知栏 | 截图确认：`拾课 · in 5h` + 标题 + 两行正文 + 展开箭头 ✅ |
| **点击跳转** | 点通知 → **`CourseDetailActivity`**，显示 `新时代中国特色社会主义理论与实践 / 周二 · 第 4-5 节 / 10:40-12:00 / 教学楼F楼F420多 / 张瀚文`，**与通知那门课完全一致** ✅ |
| **重复抑制** | 同一槽位再广播一次 → **不产生第二条通知**（`markNotified` 生效）✅ |
| **关闭提醒** | 取消勾选后 `scheduled` 立即变 **`[]`**；再广播 → **通知数 0** ✅ |
| 恢复原状 | 测完把 `weihe_schedule_reminders.xml` 写回原文件，页面回到 `提前 15 分钟 / 10 条` ✅ |

**通知是怎么被触发的**：`ReminderReceiver` 是 `exported="false"`，`adb shell am broadcast` 会被
`SecurityException` 拒（shell uid）。可行路径是**以应用自身 uid 发送**：
`adb shell run-as com.weihe.schedule am broadcast --user 0 -a com.weihe.schedule.action.REMIND -n com.weihe.schedule/.reminder.ReminderReceiver --ei slot <槽位> --el trigger_at <毫秒>`
（`--user 0` 必须显式给，否则报 `INTERACT_ACROSS_USERS`）。槽位与触发毫秒从 `scheduled` 键里取。

#### 本轮发现并**修掉**的既有缺陷

**正文星期用的是 `EEEE`，跟随设备语言。** 本机（英文 locale）实测通知正文是
`10月13日 Tuesday 10:40 …` —— 中文界面里混一个英文星期，而设备语言决定了它显示成什么。
改为复用项目自己的 **`ScheduleUtils.dayName()`**（周一…周日），并加测试锁住
「正文里不得出现任何英文星期名」。这不是新引入的问题，是核实通知内容时读出来的。

#### 与本轮需求冲突的既有测试（**已按需求更新，特此报告**）

AGENTS.md 规定「既有测试与需求冲突时停下来报告」。以下 4 处冲突均由本轮需求直接导致，
更新后**断言仍然成立且更严格**，没有为了让代码通过而放宽：

| 测试 | 原断言 | 现断言 | 原因 |
|---|---|---|---|
| `NonDefaultScheduleFlowTest.theDefaultReminderLeadIsStillFifteenMinutesOrLess` | `MIN==5`、`MAX==15`、`!supports(20)` | 改名 `theReminderLeadBandIsStillItsOwnSetting`：带宽自洽、仍拒绝带外值、`MAX > 一节时长` | 需求明确要求 1–180，`supports(20)` 必须为真 |
| `ReminderPlanTest.aLeadTimeOutsideTheSupportedBandIsRefused` | `!supports(4)`、`!supports(16)` | 带内改为 1/5/10/15/20/180；带外改为 0/−10/**181**/1000 | 同上 |
| `ReminderPlanTest.theDefaultLeadTimeIsOneOfTheThreeOffered` | 名字指「三者之一」 | 改名 `theDefaultLeadTimeIsInsideTheBand`（断言不变） | 带宽不再是三个值，旧名字已不成立 |
| `ReminderScheduleTest.anEmptyVenueOrTeacherLeavesNoGapBehind` / `aVenueWithoutATeacherStillReadsProperly` | 分隔符数 0 / 1 | **1 / 2**，并继续断言不出现 `· ·` | 正文新增了「节次」这一**真实**字段，它必然占一个分隔符；「空字段不留多余分隔符」的原意保留 |

#### 未验证 / 限制

- 通知是**用广播手动触发**的，**没有等真实 `AlarmManager` 到点**（下一次真实触发在设备时间
  10:15，超过 2 小时）。`setAndAllowWhileIdle` 的「可能晚几分钟」这一行为**未实测**。
- **重启（`BOOT_COMPLETED`）后的自动重建**未实测（`ReminderRestoreReceiver` 代码未改）。
- **自定义作息表**下的提醒时刻未单独实测（当前数据 23 门课都命中「教学楼作息」一张表）；
  由 `ReminderPlanTest.theReminderNamesTheScheduleItUsed` 与未改动的 `ReminderPlan.startOf` 覆盖。
- 未测**锁屏**上的显示形态（需要锁屏界面，未走）。
- 未测**大字号**下四个色块是否换行（默认字号下右侧余 ~210px；`1.3×` 未实测）。
- `ReminderSettings` **仍无 JVM 测试**（需要 `Context`，项目没有对应的测试接缝）；
  持久化与老用户兼容性是**设备实测**的，不是单测。

---

## E. 最近几轮任务的实际进度

近几轮由**多位 Agent** 连续完成，**细节与当轮测试结果完整保留在归档**中
（归档内 E0–E4、K 节）。此处只记结论与出处：

| 轮次 | 内容 | 结论 | 归档位置 |
|---|---|---|---|
| E0 | 滚轮式时间选择器（`ui/WheelColumnView`） | 已完成；修掉"甩动后卡在两行之间"与"不循环"两个设备端缺陷 | 归档 E0 |
| E1 | **每节课时长可配置 + 动态每日节次数量** | 已完成；含"增删节按钮被截断"的追加修复；与 3 处既有测试冲突时按规范停下报告 | 归档 E1 |
| E2 | **自定义默认作息表** | 已完成；需求前提与代码不符时先上报；打通 7 个解析调用点 | 归档 E2 |
| E3 | **导入改为"文件第 1 周 = 学期第 1 周" + 开学日期选择** | 已完成；设备端修掉"`TextWatcher` 读到的 Monday 是上一周" | 归档 E3 |
| E4 | **导入弹窗新增「学期总周数」（1–18）** | 已完成；新增 `rule/SemesterWeeks` | 归档 E4 |
| K | **课程颜色选择器布局恢复**（3×3 圆形网格 + 勾 → 单排圆角矩形） | 已完成；删除孤立资源 `ic_check.xml` | 归档 K |
| — | **同名课程批量同步颜色** | 已完成；新增 `rule/CourseColorSync` + `recolorCourses` | 未入归档（本轮之前最后一轮） |
| — | 左侧节次编号核查 | **未复现，判定已修复**；2026-10-13 扩展到 20/30 行、两个视图、滚动中段，仍不可复现（见 D-4） | 本文件 D-4 |
| — | **Excel 导入全流程设备验证** | **2026-10-13 首次走通**：选文件 → 预览 → 覆盖导入 → 单双周过滤 → 撤销导入；导入结果与文件逐字段吻合（见 D-5） | 本文件 D-5 |
| — | **上课提醒：核实通知形态 + 自定义提前分钟数（1–180）** | **2026-10-13 完成**；顺带修掉「正文星期跟随设备语言」；4 处既有测试按需求更新并已在 D-6 报告（见 D-6） | 本文件 D-6 |

**注意：E0–E4 与 K 的代码均已包含在提交 `f0765a6` 中**，即已提交但未推送。

---

## F. 测试与构建（本轮实际执行）

```powershell
cd 'D:\vibe coding\Weihe\Weihe'
$env:GRADLE_USER_HOME='D:\vibe coding\Weihe\Weihe\.gradle-local\home'
$env:ANDROID_HOME='D:\Sdk'; $env:ANDROID_SDK_ROOT='D:\Sdk'
& '.\.gradle-local\bin\gradle.bat' "-Dorg.gradle.vfs.watch=false" `
    --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **54 个测试类 / 980 项 / 980 通过 / 0 失败 / 0 错误**（本轮前 971；+9 项：自定义提前时间与通知文案） |
| `assembleDebug` | **BUILD SUCCESSFUL**，产物 `app/build/outputs/apk/debug/app-debug.apk`（872,939 B）✓ |
| `lintDebug` | **`No issues found.`**（`lint-results-debug.xml` 中 `<issue ` 计数 = **0**）✓ |

- **结果对应的代码状态**：`8ee1a04` + 本轮提醒改动（工作区未提交）✓
- **必须带 `--rerun-tasks`**，否则会读到被跳过任务的旧结果。
- 环境：Gradle 8.13 / JDK 20.0.1 / AGP 8.13.0 / `ANDROID_HOME=D:\Sdk`。

> **一次假失败**：2026-10-13 首次执行门禁时报 `BUILD FAILED`（错误里列出一堆 `.class` 路径）。
> 原因是**上一轮 Agent 会话被中断**，`--rerun-tasks` 已删除输出目录但没重建完。
> **重跑即通过**，代码本身无问题。遇到同样的失败先重跑一次再判断。

### 从未验证过的部分

- **没有仪器测试**：项目不存在 `app/src/androidTest/`，**界面行为无法自动化验证**。
- **没有真机验证**：全部设备结论来自模拟器 `emulator-5554`（1080×2424 @420 → 411dp 宽）。
- 具体未做的设备场景见 G-5。

### 会浪费大量时间的坑

- **`assembleDebug` 不会安装**。改了界面若设备上看不出变化，先确认是否漏了
  `adb install -r app\build\outputs\apk\debug\app-debug.apk`
  （本项目两次因此误判"新功能没生效"）。
- **PowerShell**：`(...)` 内用 `if` 是语法错误；多余一个 `)` 会让整个脚本静默不执行；
  单引号 here-string 是 LF 而源码是 CRLF（多行匹配要用 `[regex]` + `\r?\n`，或改逐行替换，
  **逐行替换远比多行锚点可靠**）。
- **模拟器**：`adb shell input text` **会丢 `.`**、无法输入非 ASCII；
  `input swipe` 做长按容易拉下通知栏；UI dump 里是**真实中文**，用 `\uXXXX` 匹配必然找不到。
- **模拟器整屏黑屏**但应用正常（系统 Settings 一样黑）→ WindowManager 缺陷，`adb reboot` 可解，**不是应用 bug**。

---

## G. 已知问题与技术风险

**G-1 ✅ 已解决（2026-10-13 复核）** —— 6 个设计/规划文档（`COURSE_REMINDER_PLAN.md`、
`DATA_BACKUP_DESIGN.md`、`DATA_BACKUP_RESTORE_PLAN.md`、`NEXT_OPTIMIZATION_ROADMAP.md`、
`PROJECT_ANALYSIS.md`、`PROJECT_ARCHITECTURE.md`，合计 4,582 行）的删除**已提交**在 `8ee1a04`，
与同一次提交里的「重写 `AGENT_HANDOFF.md` + 新建 `AGENT_HANDOFF_ARCHIVE.md` + 新增 `README.md`」
同属**有意的文档整理**。内容仍可从 `f0765a6` 取出。**无需再问用户，也不需要恢复。**

**G-2 ✅ 已解决（2026-10-13 复核）** —— `f0765a6` 与 `8ee1a04` **都已在 `origin/main` 上**，
`git rev-list --count origin/main..HEAD` = **0**，没有任何未推送提交。

**G-3 已确认（设计取舍）** —— 作息表的**结束时间在界面上只读**（每节 40 分钟自动算出，
时长可在 1–180 分钟内配置）；`BellScheduleEditor` 底层仍支持任意起止。

**G-4 已确认（结构脆弱，未修；2026-10-13 更正描述）** —— 节次编号栏靠**自身 `scrollTo`**
跟随网格（`SyncedVerticalScrollView.onScrollChanged`）。**上一版把它描述成"会被内容高度夹取、
要求两栏等高"是错的**：`View.scrollTo` 不做任何 clamp（AOSP 实现只写 `mScrollX/mScrollY`），
所以编号栏跟得上，**与两栏是否等高无关**。

真正残留的问题是**两栏内容高度用两套取整方式算**，因此**并不相等**（实测 13 行差 3px、
20 行差 5px、30 行差 7px，编号栏偏高）：

- 编号栏：`periods × rowHeight()`，每行 `dp(54)` **各自取整后相加**；
- 网格日列：`rowSpan(dayColumnRows(periods))`，对总高 **`dp(periods × 54)` 一次取整**。

差值的**符号取决于屏幕密度**（`round(54 × density)` 与 `54 × density` 谁大）。
若某密度的 `frac(54 × density) < 0.5`，编号栏会**比网格矮**最多约 `0.5 × periods` px
（30 行时约 15px），表现为**编号列底部露出一条背景**——是**外观瑕疵**，
**不足以造成"后续节次看不到"**（后者是 `1213ef7` 已修的那个缺陷）。
当前设备（density 2.625，`54 × 2.625 = 141.75`，进位到 142）编号栏**偏高**，所以是安全的。

**现有测试仍不覆盖两栏内容高度的关系**（`WeekGridRowsTest` 只覆盖行数算术）。
若要加固，建议二选一（**先与用户确认**）：
① 让编号栏的行高也走 `rowSpan()` 那套一次性取整，使两栏**由构造保证相等**；
② 补一个断言两栏内容高度的测试（需要先把高度算式提成静态纯函数，否则 JVM 测试测不到）。

**G-5 未验证的设备场景（按重要性）**
1. ~~网格 >13 行的实际滚动与对齐~~ → **2026-10-13 已在设备上验证 13 / 20 / 30 行，见 D-4** ✅
2. ~~**Excel 导入**的完整设备流程~~ → **2026-10-13 已走通，见 D-5** ✅
   （遗留：损坏文件 / `.xlsx` 两条分支未走）
3. **备份真正写盘到用户文件并恢复**（此前只验证到入口与预览打开选择器）
4. ~~**提醒到点通知**与重启后恢复~~ → **2026-10-13 已实测通知内容、点击跳转、重复抑制、
   关闭后不再发送（见 D-6）**；遗留：**未等真实闹钟到点**、未测 `BOOT_COMPLETED` 自动重建、未测锁屏形态
5. 课程复制、备份恢复的端到端走查
6. 网格在**改过系统字号**后的表现（`periodCell` 用 `BODY_LARGE=14sp` + 固定 142px 行高，**未实测**）

**G-6 待确认** —— 学期自定义名（如 `大三上`）时，新建学期**不推算**名称而回到空输入框（看似有意）。
**G-7 待确认** —— **非连续周次在数据模型上不可能表达**（`startWeek..endWeek` + 单双周）；
任何"逐周勾选"需求都必须先改模型与存储格式。
**G-8 待确认** —— **首页没有"跳转到指定日期"入口**（用周次网格 + 星期栏）。把它当既有功能是误判。
**G-9 待确认** —— 早期缺陷「`addSemester` 未写入 `semester_start:<新id>`」的修复**不追溯**已有数据：
某个旧学期的 `semester_start` 键可能仍是错误日期，需手动重设或重建该学期。
**G-10 已确认（死代码）** —— `EditScheduleActivity.timeInput(String)` 定义在 L605，
**全项目仅此一处出现、无任何调用点**（2026-10-13 `grep` 复核）→ 确认是死代码，可清理。
清理属独立小任务，**本轮未动**（避免扩大范围）。

**G-11 新发现（既有缺陷，未修）—— 导出摘要把"条安排"说成"门课程"**
设置页「导出备份」的副标题写作
`待导出：1 个学期 · N 门课程 · 3 张作息表`，但 **N 是"安排条数"而不是"课程门数"**：

- 导入前：**23 条安排** / **14 个不同课名** → 显示 `23 门课程`
- 导入后：**5 条安排** / **4 个不同课名** → 显示 `5 门课程`

代码依据：`data/BackupCodec` 的 `courseCount()` 是各学期 `courses.size()` 之和，
即安排条数；`data/BackupPreview.summary()` 把该数字拼进 `\u95E8\u8BFE\u7A0B`（"门课程"）。
**同一份代码在导入弹窗里是对的**（`识别到 4 门课程，共 5 条安排` 分别用了
`Report.courseCount()` 与 `Report.scheduleCount()`），所以这是**措辞不一致**而非计数错误。
影响：用户按摘要估备份规模时会高估课程数；**不影响任何数据**。
修法（很小，但**本轮未做**）：把措辞改成"条安排"，或改用去重后的课名数。

**G-12 新发现（环境）—— 模拟器截图是缩放显示的**
`adb screencap` 得到 1080×2424，但预览窗口按 480×1073 显示（**比例 2.25**）。
**不要按截图上的目测位置点屏幕**——会点到 2.25 倍偏移的位置
（2026-10-13 因此误点了文件选择器的"Large files"筛选条）。
**一律用 `uiautomator dump` 里的 `bounds` 取真实设备坐标。**

---

## H. 当前工作区保护说明

**2026-10-13 状态：工作区只有本轮的提醒改动与文档更新，没有别人的东西混在里面。**

```
git status --porcelain
  M AGENT_HANDOFF.md
  M AGENT_HANDOFF_ARCHIVE.md
  M app/src/main/java/com/weihe/schedule/SettingsActivity.java
  M app/src/main/java/com/weihe/schedule/reminder/ReminderNotifications.java
  M app/src/main/java/com/weihe/schedule/rule/Reminder.java
  M app/src/main/java/com/weihe/schedule/rule/ReminderPlan.java
  M app/src/test/java/com/weihe/schedule/data/NonDefaultScheduleFlowTest.java
  M app/src/test/java/com/weihe/schedule/reminder/ReminderScheduleTest.java
  M app/src/test/java/com/weihe/schedule/rule/ReminderPlanTest.java
  ?? .workbuddy-ai/memory/2026-10-13.md
git rev-list --count origin/main..HEAD       # 0（无未推送提交）
```

| 类别 | 内容 | 处理要求 |
|---|---|---|
| **已提交并推送** | 除下表以外的全部代码与文档，HEAD = `origin/main` = `8ee1a04` | 无待处理项 |
| **本轮代码改动（未提交）** | 提醒模块 4 个生产文件 + 3 个测试文件（见 D-6） | 可保留；**未经用户同意不要提交/推送** |
| **本轮文档改动（未提交）** | `AGENT_HANDOFF.md`、`AGENT_HANDOFF_ARCHIVE.md`、`memory/2026-10-13.md` | 可保留 |
| **已删除（已提交）** | 6 个设计/规划文档（G-1） | **已完成的有意整理**，不要恢复 |

**禁止**：`git reset --hard`、`git clean`、强制切分支、`git checkout -- <file>`（除用户明确要求）、
`git add`/`commit`/`push` 未经用户同意。

---

## I. 下一步优先任务

| 优先级 | 任务 | 前置条件 | 验收标准 |
|---|---|---|---|
| ~~P0~~ | ~~确认 6 个文档的删除是否有意~~ | **已完成**：删除已提交，是有意整理（G-1） | —— |
| ~~P0~~ | ~~确认是否推送 `f0765a6`~~ | **已完成**：已在 `origin/main`，无未推送提交（G-2） | —— |
| ~~P1-1~~ | ~~补设备验证网格 >13 行~~ | **已完成 2026-10-13**：13 / 20 / 30 行实测，见 D-4 | —— |
| **P1-2** | **Excel 导入的完整设备流程**（选文件 → 预览 → 确认 → 结果） | 需**先生成一个 `.xls` 样本**（仓库里没有）；系统文件选择器在 adb 下偶发点不中 | 走通并截图；发现的问题**记录而非当场大改** |
| ~~P1-2~~ | ~~Excel 导入的完整设备流程~~ | **已完成 2026-10-13**：选文件 → 预览 → 覆盖导入 → 撤销导入全流程走通，见 D-5 | —— |
| ~~P1-3~~ | ~~导入的**错误分支**设备验证~~ | **仍未做**：`broken.xls` / `unreadable.xls` / 真实学校文件 / `.xlsx` 都还在 `/sdcard/Download/` | 走通并截图；问题记录而非当场大改 |
| **P1-4** | 提醒的**真实闹钟到点**验证（等一次 `AlarmManager` 自然触发）＋ `BOOT_COMPLETED` 后自动重建 ＋ 锁屏形态 | 模拟器可用；需要一个几十分钟内的课 | 走通并截图 |
| **P2** | 加固节次编号栏跟随机制（G-4） | **先与用户确认是否需要**（当前只是 ≤15px 的外观瑕疵，不是功能缺陷） | 编号栏与网格内容等高**由构造保证**，或有测试断言 |
| **P3** | 清理 `EditScheduleActivity.timeInput()` 死代码（G-10 已确认） | 无 | 980 项测试仍全绿、lint 干净 |
| **P3** | 修正导出摘要措辞（G-11：`N 门课程` → 实际是安排条数） | 无 | 文案与计数一致；不改任何存储 |
| **P4** | 需产品决策才能动（G-6/7/8） | **先问用户** | 不得自行开工 |
| **P5** | 备份写盘恢复 / 课程复制端到端（G-5 的 3、5） | 模拟器可用 | 走通并截图 |

---

## J. 下一位 Agent 的接手步骤

1. **先读**（顺序）：[`README.md`](README.md)（项目是什么）→ [`AGENTS.md`](AGENTS.md)（怎么写）
   → **本文件**（现在什么状态）→ 需要背景时查 [`AGENT_HANDOFF_ARCHIVE.md`](AGENT_HANDOFF_ARCHIVE.md)
   （**仅历史，其完成度判断不可信**）。
2. **核对 Git**（2026-10-13 实测值）：
   ```
   git branch --show-current                 # 期望 main
   git log -2 --oneline                      # 期望 HEAD = 8ee1a04
   git status --porcelain                    # 期望：0 行（工作区干净）
   git rev-list --count origin/main..HEAD    # 期望 0（无未推送）
   ```
3. **打开代码抽查**（不要只信文档）：`ui/Pickers`、`rule/CourseColorSync`、
   `EditCourseActivity` 的颜色选择器（单排 / `RADIUS_S` / 无 `ic_check`）、
   `model/BellSchedule` 的 `DEFAULT/MIN/MAX_PERIOD_MINUTES`、
   `WeekGridActivity` 的 `dayColumnRows`/`lastPeriodInUse`、`CourseRepository.recolorCourses`。
4. **跑基线**（务必 `--rerun-tasks`）：期望 **980 项全通过 + lint `No issues found.`**。
   若不是，**先报告，不要在其上叠加改动**。
5. **保护工作区**：见 H 节。**不得** reset / clean / 强制切分支 / 未经同意 add·commit·push。
6. **暂停并向用户报告的情形**：
   - 测试或构建失败且原因不明
   - 工作区出现**你没做过**的改动（尤其 H 节的 6 个删除）
   - 需求会**改变存储格式、课程模型、学期周次计算或提醒行为**
   - 需求与代码现状冲突（如要求"逐周勾选"、"首页跳到指定日期"、"导入不覆盖"）
   - 需要真机 / 通知 / 文件系统等本环境无法验证的能力
7. **不要**仅凭任何文档认定某功能已完成 —— **打开代码确认**。
8. **每次完成任务后，更新本文件**（`AGENT_HANDOFF.md`）；逐轮开发日志请**追加到归档**，
   不要堆进本文件，也不要覆盖其他 Agent 的记录。
