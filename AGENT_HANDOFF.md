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
| 更新时间 | **2026-10-12** |
| 核实方式 | 逐项对照当前源码、`git` 状态与实跑结果；未照抄旧文档 |
| 本轮实际执行 | `git status/branch/log/diff`、8 项重点功能源码核查、`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` |
| 本轮代码改动 | **无**（只写文档：新增 `README.md`，重写本文件，扩充归档） |

---

## B. 当前项目状态（Git）

| 项 | 值 |
|---|---|
| 分支 | `main` |
| HEAD | **`f0765a6`（`update`）—— 本地提交，⚠️ 尚未推送** |
| `origin/main` | `bdd7d01` —— HEAD **领先远端 1 个提交** |
| 已暂存 | 无 |
| 未跟踪 | `README.md`（本轮新建） |
| 已修改（未暂存） | **7 项：6 个文档被删除 + 归档被本轮追加** |

### ⚠️ 最重要的一条：6 个项目文档被从工作区删除（未暂存）

```
 D COURSE_REMINDER_PLAN.md          749 行
 D DATA_BACKUP_DESIGN.md            721 行
 D DATA_BACKUP_RESTORE_PLAN.md      790 行
 D NEXT_OPTIMIZATION_ROADMAP.md     635 行
 D PROJECT_ANALYSIS.md              988 行
 D PROJECT_ARCHITECTURE.md          699 行
                      合计 4,582 行被删
```

- 这 6 份是**设计 / 规划 / 分析文档**，**不是**交接文档，与本轮交接无关。
- **内容没有丢失**：它们完整存在于提交 `f0765a6` 中。
- 删除**未暂存、未提交**，也**不是本轮 Agent 所为**（本轮开始时即已如此）。
- **属于谁的改动、是否有意为之：待确认。** 本轮**没有**恢复、也没有提交这些删除。
- 若确认是误删，可在**征得用户同意后**用
  `git checkout -- COURSE_REMINDER_PLAN.md DATA_BACKUP_DESIGN.md DATA_BACKUP_RESTORE_PLAN.md NEXT_OPTIMIZATION_ROADMAP.md PROJECT_ANALYSIS.md PROJECT_ARCHITECTURE.md`
  恢复。**未经确认不要执行。**

### 最近提交

```
f0765a6 (HEAD -> main)  update     ← 58 个文件；把此前积累的全部未提交工作一次性提交
bdd7d01 (origin/main)   update
61d9da5                 update
6875549                 update
9b40146                 update
```

`f0765a6` 提交了此前跨多轮、由**多位 Agent** 留下的全部改动（配色、每节时长与动态节次、
默认作息表、导入开学日期与学期总周数、滚轮选择器、同名课程颜色同步等），
以及 `AGENTS.md`、两份交接文档与 `.workbuddy-ai/memory/`。
**因此现在工作区已无未提交的代码改动** —— 与上一版交接文档记录的状态完全不同。

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
| 4 | **左侧节次编号显示不全** | **本轮未复现；判定为已修复** | 见下方「D-4 专项结论」 |
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
| 22 | Excel 导入与异常报告 | 已实现但尚未充分验证 | `ExcelTimetableReaderTest`、`TimetableImportParserTest`、`TimetableImportRangeTest`；**本轮及近几轮均未做导入的设备实测** |
| 23 | 备份导出 / 预览 / 恢复（快照 + 回滚） | 已实现但尚未充分验证 | `BackupCodecTest`；设备实测过入口与预览，**未真正写盘到用户文件并恢复** |
| 24 | 上课提醒（计算 / 调度 / 重启恢复） | 已实现但尚未充分验证 | `ReminderPlan`、`ReminderRestoreTest`；**未做通知到点的设备实测** |
| 25 | 日期与时刻统一选择器 | 已实现并已验证 | `ui/Pickers`；全项目系统 `Date/TimePickerDialog` 使用数 **= 0**；设备实测（中文按钮、24 小时制、日历/时分滚轮） |
| 26 | 统一按钮层级 / 危险操作命名 / 空状态 | 已实现并已验证 | 5 处「删除」改为明确动词；首页区分"本学期没有课程"与"这天没有安排"（设备实测后者） |
| 27 | 设置页功能顺序 | 已实现并已验证 | 设备实测：学期管理 → 学期起始日期 → 上课提醒 → 上下课时间 → 课表数据 → 关于 |
| 28 | 作息表保存前的重叠校验（写盘前拦截） | 已实现并已验证 | 设备实测：制造重叠 → 保存被拒 → **存储载荷未变** |

### D-4 专项结论：左侧节次编号（2026-10-12 实测）

**结论：在当前代码上无法复现，判定为已修复。**

- 结构性成因确实存在：`labelColumn` **不在纵向滚动容器内**，靠
  `SyncedVerticalScrollView.onScrollChanged` 里的 `labelColumn.scrollTo(0, y)` 跟随；
  `scrollTo` 会被自身内容高度夹取，**只有内容高度与网格完全相等时才跟得上**。
- 但该等式现在成立：`rows = lastPeriodInUse(...)` 同时决定编号格数（1..periods）与
  日列高度 `rowSpan(dayColumnRows(periods))`，而 `dayColumnRows(periods) == periods`。
- 源码注释（`WeekGridActivity` L526-532）明确记录该"网格比编号栏矮一行 → 滚动范围带不出最后一行"
  的缺陷**曾被修复**，`WeekGridRowsTest` 即为它而写。
- **设备实测**：总课表与单周课表，编号 1–13 与课程行**逐行对齐**，滚到底 Δ=0，
  最后一行完整可见、未被 FAB 遮挡。该学期最晚只用到**第 13 节**，所以只到 13 是正确行为。
- **未在设备上验证 >13 行**（数据里没有更晚的节次）。
- 遗留风险见 G-4。

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
| — | 左侧节次编号核查 | **未复现，判定已修复**（见 D-4） | 本文件 D-4 |

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
| `testDebugUnitTest` | **54 个测试类 / 971 项 / 971 通过 / 0 失败 / 0 错误** |
| `assembleDebug` | **BUILD SUCCESSFUL**，产物 `app/build/outputs/apk/debug/app-debug.apk` ✓ |
| `lintDebug` | **`No issues found.`** |

- **结果对应的代码状态**：`f0765a6` + 本轮工作区的 6 个 `.md` 删除（文档增删**不影响**编译、测试与 lint）✓
- **必须带 `--rerun-tasks`**，否则会读到被跳过任务的旧结果。
- 环境：Gradle 8.13 / JDK 20.0.1 / AGP 8.13.0 / `ANDROID_HOME=D:\Sdk`。

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

**G-1 ⚠️ 6 个项目文档被删除且未提交** —— 见 B 节。归属与意图**待确认**；内容可从 `f0765a6` 恢复；
**未经用户确认不要恢复、也不要提交这个删除**。

**G-2 ⚠️ `f0765a6` 未推送** —— HEAD 领先 `origin/main` 一个提交。
是否推送**由用户决定**，Agent 不得擅自 push。

**G-3 已确认（设计取舍）** —— 作息表的**结束时间在界面上只读**（每节 40 分钟自动算出，
时长可在 1–180 分钟内配置）；`BellScheduleEditor` 底层仍支持任意起止。

**G-4 已确认（结构脆弱，未修）** —— 节次编号栏靠**自身 `scrollTo`** 跟随网格，
**这个机制天然脆弱**：将来任何让网格内容比编号栏高的改动（日列多一行内边距、
课程画到最后一节之外等）都会**静默重现**裁切 ✗ 而**现有测试不覆盖两个视图的高度相等**
（`WeekGridRowsTest` 只覆盖行数算术）✗
建议二选一：把编号栏移入同一纵向滚动容器（从机制上消除夹取），或补一个断言两视图内容等高的测试。

**G-5 未验证的设备场景（按重要性）**
1. 网格 **>13 行**（15/16/20 节）的实际滚动与对齐 —— 只有单测覆盖
2. **Excel 导入**的完整设备流程（选文件 → 预览 → 确认 → 结果）
3. **备份真正写盘到用户文件并恢复**（此前只验证到入口与预览打开选择器）
4. **提醒到点通知**与重启后恢复
5. 课程复制、备份恢复的端到端走查

**G-6 待确认** —— 学期自定义名（如 `大三上`）时，新建学期**不推算**名称而回到空输入框（看似有意）。
**G-7 待确认** —— **非连续周次在数据模型上不可能表达**（`startWeek..endWeek` + 单双周）；
任何"逐周勾选"需求都必须先改模型与存储格式。
**G-8 待确认** —— **首页没有"跳转到指定日期"入口**（用周次网格 + 星期栏）。把它当既有功能是误判。
**G-9 待确认** —— 早期缺陷「`addSemester` 未写入 `semester_start:<新id>`」的修复**不追溯**已有数据：
某个旧学期的 `semester_start` 键可能仍是错误日期，需手动重设或重建该学期。
**G-10 待确认** —— `EditScheduleActivity.timeInput()` 是否已随 E1 的改动清理（本轮未复核）。

---

## H. 当前工作区保护说明

| 类别 | 内容 | 处理要求 |
|---|---|---|
| **已删除（未暂存）** | 6 个项目文档（B 节） | **不要恢复、不要提交、不要 `git add`**，先问用户 |
| **已修改（本轮）** | `AGENT_HANDOFF_ARCHIVE.md`（追加了旧交接全文） | 本轮产物，可保留 |
| **未跟踪（本轮）** | `README.md` | 本轮产物，可保留 |
| **已提交未推送** | `f0765a6`（58 文件） | **不要 push**，由用户决定 |
| **代码改动** | 本轮**无** | —— |

**禁止**：`git reset --hard`、`git clean`、强制切分支、`git checkout -- <file>`（除用户明确要求）、
`git add`/`commit`/`push` 未经用户同意。工作区可能含他人的修改。

---

## I. 下一步优先任务

| 优先级 | 任务 | 前置条件 | 验收标准 |
|---|---|---|---|
| **P0** | **确认 6 个文档的删除是否有意**（G-1） | 只需问用户 | 用户给出结论；据此决定恢复或提交删除 |
| **P0** | 确认是否推送 `f0765a6`（G-2） | 用户决定 | —— |
| **P1** | 补设备验证 G-5 的 1–2 项（网格 >13 行 / Excel 导入） | 模拟器可用；导入需一个 `.xls` 文件 | 走通并截图；发现的问题记录而非当场大改 |
| **P2** | 加固节次编号栏跟随机制（G-4） | 先与用户确认是否需要 | 编号栏与网格内容等高**由机制保证**，或有测试断言 |
| **P3** | 清理 G-10 的死代码、G-9 的孤儿键 | 无 | 971 项测试仍全绿、lint 干净 |
| **P4** | 需产品决策才能动（G-6/7/8） | **先问用户** | 不得自行开工 |

---

## J. 下一位 Agent 的接手步骤

1. **先读**（顺序）：[`README.md`](README.md)（项目是什么）→ [`AGENTS.md`](AGENTS.md)（怎么写）
   → **本文件**（现在什么状态）→ 需要背景时查 [`AGENT_HANDOFF_ARCHIVE.md`](AGENT_HANDOFF_ARCHIVE.md)
   （**仅历史，其完成度判断不可信**）。
2. **核对 Git**：
   ```
   git branch --show-current     # 期望 main
   git log -3 --oneline          # 期望 HEAD = f0765a6
   git status --short            # 期望：7 项（6 个 D 删除 + 归档 M）+ README.md 未跟踪
   git rev-list --count origin/main..HEAD   # 期望 1（未推送）
   ```
3. **打开代码抽查**（不要只信文档）：`ui/Pickers`、`rule/CourseColorSync`、
   `EditCourseActivity` 的颜色选择器（单排 / `RADIUS_S` / 无 `ic_check`）、
   `model/BellSchedule` 的 `DEFAULT/MIN/MAX_PERIOD_MINUTES`、
   `WeekGridActivity` 的 `dayColumnRows`/`lastPeriodInUse`、`CourseRepository.recolorCourses`。
4. **跑基线**（务必 `--rerun-tasks`）：期望 **971 项全通过 + lint `No issues found.`**。
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
