# AGENT_HANDOFF — 拾课（WeiheSchedule）当前状态交接

> ## 权威声明
>
> - **当前项目状态：以本文件为准。**
> - **长期开发规范：以 [`AGENTS.md`](AGENTS.md) 为准。**
> - **历史记录：[`AGENT_HANDOFF_ARCHIVE.md`](AGENT_HANDOFF_ARCHIVE.md)（仅作背景参考，其中的完成度判断已过期）。**
> - 项目介绍与构建方式：[`README.md`](README.md)。
> - **最新一轮：见文末「AM. 2026-10-18 备注按钮改到右上角（修正 AL）」**（备注卡标题与「添加备注/编辑」同一行、按钮在右上角；AL 的左侧方案已被取代）。AK 及此前的组件规则仍然有效。
>
> 本文件由核对**当前源代码、Git 状态与实测结果**后重写（2026-10-12）。
> 上一版 1338 行的全文（含 E0–E4 逐轮开发日志）已**整段并入归档**，未删除任何内容。
> **任何"已完成"的判断都必须打开代码确认；本文件也可能滞后。**

---

## A. 更新时间与核实范围

| 项 | 值 |
|---|---|
| 更新时间 | **2026-10-14（文档同步轮）**（上一版 2026-10-14 六阶段复审） |
| 核实方式 | `git branch/log/status/diff`、四个项目文档逐节比对**实际事实**、复跑测试与构建、`apksigner verify` 复核签名 |
| 本轮实际执行 | 图标替换（`IconForge` 重跑 + 页内 Logo + `values-v31` 启动画面主题）、模拟器目视验证桌面图标、Debug/Release 双构建、`--rerun-tasks testDebugUnitTest assembleDebug`、文档同步 |
| 本轮代码改动 | `AboutActivity.java`（改用 `ic_brand_logo`）、新增 `res/values-v31/themes.xml`、新增 `res/drawable-nodpi/ic_brand_logo.png`；**25 个 mipmap PNG 经比对与新生成结果逐字节相同，未产生实际改动** |
| 未验证 | **启动画面未拍到**（启动窗口过短）；**API 26–30 从未运行过**（只在 API 37 模拟器验证）；备份恢复与提醒的失败路径仍无测试 |

> **2026-10-14 复核要点（重要）**：前六轮"修复"里**只有四轮真的留下了代码**
> （`MiniJson` 深度限制、`BackupCodec` 版本校验、`ReminderScheduler` 注册顺序、
> `ExcelTimetableReader` 规模校验）。**"备份恢复一致性"那一轮当轮即被整体撤销**，
> 后补上能安全落地的一半 —— 详见 **B-2**，那是阶段修复的权威状态表。
> 图标与打包的状态见 **B-3**。上一版关于 `gradlew` CRLF 的历史结论**已被证伪**，见 B-2.1。

---

## B. 当前项目状态（Git）

| 项 | 值 |
|---|---|
| 分支 | `main` |
| HEAD | **`f0995e4`**（`update`, 2026-10-14） |
| 已暂存 | 无 |
| 未跟踪 | **无** |
| 已修改（未暂存） | **无** —— 工作区**干净**（六阶段修复、图标资源、`.gitattributes`、四份文档**均已提交**） |
| 提交节奏 | `9702b8b` → `dc3f798` → **`f0995e4`**。每轮结束后由用户提交，**Agent 全程未 commit / 未 push** |

> 本节的数字会随用户提交而变化得很快。**接手时请自己跑一遍 `git status --short`
> 与 `git log -1`，不要直接采信这里的哈希。**

### B-3. 图标与打包状态（2026-10-14 核实）

| 项 | 状态 | 依据 |
|---|---|---|
| **桌面 Launcher Icon** | **已实现并已验证** ✅ | 自适应图标三层齐全 ✓ APK 内五个密度各 5 个文件 ✓ 模拟器应用列表**已目视确认**为新设计（`tools/out/icon-drawer.png`）✓ |
| 图标资源来源 | 单一来源 ✅ | `tools/IconForge.java` 从根目录 `icon.png` 生成；**重跑结果与仓库既有资源逐字节相同**（27 个文件 MD5 全部一致）→ 原"旧图标"问题**实为启动器缓存**，用 `am force-stop` 清缓存后即显示新图标 |
| **启动画面图标** | **部分实现** ⚠️ | 此前项目**零配置** ✗ → 新增 `res/values-v31/themes.xml`，显式指定 `windowSplashScreenAnimatedIcon=@mipmap/ic_launcher_foreground` 与 `windowSplashScreenBackground=@color/surface` ✓ **但启动瞬间的画面未拍到，效果未目视验证** ✗ |
| 页内品牌 Logo | 已实现 ✅ | `AboutActivity` 改用 `R.drawable.ic_brand_logo`（原图裁边后等比 512×495）+ `FIT_CENTER` ✓ 原因：`R.mipmap.ic_launcher` 是自适应图标，平台只保证中间 72dp 可见，放进 `ImageView` 会偏小 ✓ |
| **Debug APK** | **可安装** ✅ | `app/build/outputs/apk/debug/app-debug.apk` **1136 KB** ✓ `apksigner verify` **退出码 0** ✓ 签名者 `CN=Android Debug`（证书 SHA-256 `d623aa4a…`）✓ 个人自用推荐此包 ✓ |
| **Release APK** | **不可安装** ✗ | `app-release-unsigned.apk` **866 KB**，`release {}` 无 `signingConfig` → **未签名** ✗ 因 `minifyEnabled false`，其代码行为与 Debug 一致 ✓ |
| 换签名的风险 | 已向用户说明 ⚠️ | 签名不同则无法覆盖安装 ✗ 唯一办法是卸载 ✗ 而卸载会清空课表 ✗ → **必须先「设置 → 课表数据 → 导出备份」并存到手机之外** ✓ |

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
   git log -2 --oneline                      # 期望 HEAD = 5e5f161（K 节之后仍是；如用户已提交则以实际为准）
   git status --porcelain                    # K-6 列出的未提交改动仍在（若用户尚未提交）
   git rev-list --count origin/main..HEAD    # 期望 0（无未推送）
   ```
3. **打开代码抽查**（不要只信文档）：`ui/Pickers`、`rule/CourseColorSync`、
   `EditCourseActivity` 的颜色选择器（单排 / `RADIUS_S` / 无 `ic_check`）、
   `model/BellSchedule` 的 `DEFAULT/MIN/MAX_PERIOD_MINUTES`、
   `WeekGridActivity` 的 `dayColumnRows`/`lastPeriodInUse`、`CourseRepository.recolorCourses`。
4. **跑基线**（务必 `--rerun-tasks`）：期望 **999 项全通过 + lint `0 errors, 2 warnings`
   （仅 `IconXmlAndPng`）**。若 `processDebugResources` 报 `Error 15100`，
   先看 K-1 —— 那是 AAPT2 路径含空格的老问题，修复在 `gradle.properties`。
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

---

## K. 2026-10-15 接手轮：构建解封 + 优先级 2（新装空课表）

> 本节记录**最新一轮**的实际改动，与 A–J 各节冲突时**以本节为准**。
> 上一版判断的核验基线为 HEAD `f0995e4`；**实际 HEAD 已是 `5e5f161`**（文档滞后，已数次出现）。

### K-1. 阻断项：`processDebugResources` 失败（已修复）

**现象**：`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` 在
`:app:processDebugResources` 失败 —— `failed to load XML file: Error 15100 while retrieving message for error 13`。

**根因（已用实验证实）**：AGP 从 Maven 解出的 AAPT2 位于
`.gradle-local/home/caches/8.13/transforms/b0d4ad2d6eaada99e3f4a6e0d731fcfa/transformed/aapt2-8.13.0-13719691-windows/aapt2.exe`，
该路径含**空格**（`D:/vibe coding\...`）。同一份字节
（SHA-256 `6ccecfce7d1b1e2789dff9d6ac0ac2d07be7c98d8111ecc712aa4c0d3e0a0593`，5397504 字节）
从**无空格**目录运行 rc=0 成功，从该缓存目录运行失败；SDK 自带的 aapt2 在任何路径都正常。
**不是**清单内容、不是行尾、不是 argfile 内容问题。

**修复**：`gradle.properties` 新增

```properties
android.aapt2FromMavenOverride=D\:\\\\Sdk\\\\build-tools\\\\35.0.0\\\\aapt2.exe
```

反斜杠与盘符冒号**双重转义**是必需的：`.properties` 解析一层，Gradle 作为 project property
传给 AGP 时再解一层；少一层会被 lint 的 `PropertyEscape` 拒绝或得到坏路径。
日志应出现 `AAPT2 35.0.0 Daemon #0: starting`（不再出现 `aapt2-8.13.0-13719691-windows`）。

### K-2. 优先级 2：新安装课程表必须为空（已实现）

**改动**：`data/CourseRepository.java`

- `loadCourses()` 删除"首个学期自动播种 9 门示例课"分支 —— `raw == null` 直接返回空表
- 删除 `seededSemesterId` 字段及 `migrateLegacyStorage()` 中的赋值
- 删除 `createSampleCourses()` / `sample(...)`
- 删除不再使用的 `import com.weihe.schedule.design.Palette`
- 修正 `migrateLegacyStorage` 过期 javadoc

**四种场景的区分**（未改变的部分）：

| 场景 | 结果 | 依据 |
|---|---|---|
| 全新安装 | **空课表** ✅ | `raw == null` → 返回空表，且**不写任何键** |
| 老版本升级 | **数据原样保留** ✅ | 该路径有 `courses:<id>` 键，`raw != null`，走 `readCourses` |
| 新建学期 | **空课表** ✅ | 新学期无键 |
| 恢复备份 | **以备份为准** ✅ | `restoreFrom` 用备份里的课程重建 |

**测试同步**（用户选择"改代码 + 同步更新这些测试"）：删除播种后 **50 项测试失败**，
全部依赖"默认有 9 门课"的隐含前提。按类补显式播种：

| 测试类 | 失败数 | 处理 |
|---|---|---|
| `CourseRepositoryTest` | 9 | 新增 `seedNineCourses`；`aFirstRunWritesTheSampleCourses` → `aFirstRunStartsWithAnEmptyTimetable` |
| `CourseRepositoryStorageTest` | 8 | 新增 `seedNineCourses`，逐个补播种 |
| `RestoreProtocolTest` | 25 | 改 `seededRepository` 辅助方法内部播种 |
| `BackupPreviewTest` | 4 | 同上 |
| `BackupCodecTest` | 2 | 两处显式播种 |
| `ActiveSemesterReloadTest` | 2 | 新增 `seed(...)` |

播种数据统一为 **id 1..9、跨周一到周五、起始节次互不相同**，以复现旧示例课表的排序行为。

### K-3. 优先级 3：默认作息表与备份文件（已核对一致）

备份文件**已找到**：
`C:/Users/tuchengcheng/xwechat_files/wxid_x32m652hoxn722_68bf/msg/file/2026-10/weihe-backup-20261014-1547.json`
（6170 字节，mtime 2026-10-14 15:48）。逐字段校验结果：

| 字段 | 备份值 | 代码现状 | 一致？ |
|---|---|---|---|
| format / version | `weihe-schedule-backup` / `1` | 同 | ✅ |
| A/F 楼作息 id | **`schedule-standard`** | `BellSchedules.STANDARD_ID` | ✅ |
| A/F 楼作息 名称 | **`A/F 楼作息`** | `STANDARD_NAME` | ✅ |
| B/C 楼作息 id | `schedule-bc` | `BC_ID` | ✅ |
| D/E 楼作息 id | `schedule-de` | `DE_ID` | ✅ |
| **defaultScheduleId** | **`schedule-bc`** | `legacyDefaultId()` **BC 优先** | ✅ |
| 三表 starts/ends | 与源码逐项相同，durationMinutes=40 | 同 | ✅ |

**重要**：此前工作区未提交的 `STANDARD_ID = "schedule-af"` 改名是**错的**（备份证明应为
`schedule-standard`），**已按用户确认改回**；`STANDARD_NAME = "A/F 楼作息"` 的改名**保留**。
`DefaultScheduleTest` 同步更新一处内联 JSON 里的名称。

### K-4. 优先级 1：上课提醒（已定位，结论如下）

**排除了什么**：提醒链路**不是"代码缺失"**。已逐行核对
`ReminderScheduler` / `ReminderReceiver` / `ReminderSchedule` / `ReminderNotifications` /
`ReminderSettings` / `ReminderRestoreReceiver` / `AndroidManifest.xml` / `MainActivity` /
`SettingsActivity` 的接线，内部自洽；`rule/ReminderPlanTest`(68) + `reminder/ReminderScheduleTest`(34)
共 102 项计划计算测试全绿。

**用真实生产类做的端到端探针**（JVM，`rule/` 无 Android 依赖）：

- 正常周一（大一数学 1-2 节、大学英语 3-4 节），学期首日 07:00：
  **产生 2 条提醒**，起始 08:15 / 09:55，提前量 10 分钟 → 08:05 / 09:45 ✅
- 传入**空作息表**：0 条提醒 + `NO_SCHEDULES` 原因（刻意拒绝，而非静默）✅
- 默认作息解析到 B/C 组（与备份一致）✅

**结论**：**计划计算正确**。提醒不生效的原因**不在这一层**，但本环境
（`adb devices` 为空，无设备）**无法验证**以下 Android 侧环节，需真机：
`AlarmManager.setAndAllowWhileIdle` 实际注册、`PendingIntent` 身份、
通知权限授予后 `canPost` 的翻转、厂商省电策略对非精确闹钟的抑制。

**已知设计取舍（非缺陷）**：使用**非精确**闹钟（`setAndAllowWhileIdle`，无需
`SCHEDULE_EXACT_ALARM`），代价是手机深度休眠时提醒可能**晚几分钟** —— 设置页已如实说明。

### K-5. 本轮门禁（全绿）

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

| 项 | 结果 |
|---|---|
| 单元测试 | **999 项 / 0 失败 / 0 错误 / 0 跳过** ✅ |
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` **1,162,496 字节** ✅ |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`）✅ |
| 耗时 | BUILD SUCCESSFUL in 3m 1s（49 tasks） |

### K-6. 本轮工作区状态（**未提交**）

| 文件 | 说明 |
|---|---|
| `gradle.properties` | AAPT2 修复（+14） |
| `data/CourseRepository.java` | 删除播种（+？/−101 行净减） |
| `model/BellSchedules.java` | `STANDARD_NAME` 改名（未提交工作，保留） |
| `data/DefaultScheduleTest.java` | 同步名称 |
| 6 个测试类 | 播种同步 |
| 未跟踪 | `.workbuddy-ai/`（记忆目录）、`app/release/` |

**HEAD `5e5f161`；Agent 全程未 commit / 未 push。**

### K-7. 交给下一轮

1. **优先级 1 的真机验证**：需 `adb devices` 有设备后，开提醒 → 加一门当天稍后的课 →
   观察 `adb shell dumpsys alarm | grep weihe` 是否有闹钟、以及通知是否到达。
2. **优先级 4（数据安全审计）尚未开始**：`commit()` 失败、导入/恢复一致性、
   损坏数据保留、`allowBackup` 隐私。
3. 归档追加：本轮日志已写入 `.workbuddy-ai/memory/2026-10-15.md`。

---

## L. 2026-10-16 课表总览：自适应宽度 + 双指缩放（当前状态）

> 与 A–K 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **Q 节**。

### L-1. 改了什么

只动 **`WeekGridActivity.java`**（原实现 + 缩放/自适应，未重写），新增
**`rule/GridZoom.java`**（纯几何规则）、**`rule/GridZoomTest.java`**（19 项）、
**`res/drawable/ic_remove.xml`**（缩小图标）。**未改**数据格式、模型、规则层其它文件、
配色常量、课程卡片的内容/字体/颜色/点击、提醒、导入、备份恢复。

| 项 | 改前 | 改后 |
|---|---|---|
| 每天列宽 | 固定 `DAY_WIDTH_DP = 118dp`（7 列 826dp → 必须横滚） | `(视口宽 − 网格内缩) / 7 × 缩放`，实测 **134px × 7 + 左栏 = 1080px，正好铺满** |
| 节次栏宽 | 固定 `42dp` | 屏宽的 10%，夹在 `[26dp, 42dp]`（本机 ≈ 41dp） |
| 行高 | 固定 `54dp` | `54dp × 缩放` |
| 缩放 | 无 | 双指捏合 + 底部胶囊 `[−] [适应] [+]`，范围 `[1.0, 2.4]`，步进 1.25 |
| 缩放下限 | 可无限缩小 | **就是「适应屏幕」本身**：下限＝七天同屏，再没有比这更小的档位 |
| 显示策略 | 无 | **适应**＝七天同屏，既是默认也是最小档（用户明确要求：最多只能到适应屏幕的比例） |

### L-2. 缩放是怎么实现的

**真实重排布局，不是对成品图做变换。** 缩放只改变 `dayWidthPx` / `rowHeightPx`，
随后按新尺寸重建网格。这样文字按最终字号绘制（放大后不糊），且触摸命中与视觉一致
（Android 按视图自身的变换矩阵反算触点，而这里根本没有变换，是真实尺寸）。

- 手势：`ScaleGestureDetector` 挂在 **Activity 的 `dispatchTouchEvent`** 上——
  挂在网格里会被某个消费了 DOWN 的块或滚动容器截胡，永远收不到第二根手指。
- 平滑：`queueRender()` 用 `postOnAnimation` 把一帧内的多次 `onScale` 合并成**一次**重建。
- 焦点保持：`setZoomAround()` 按缩放前后列宽比换算目标滚动位置，捏合处的内容不会滑走。
- 不误触：双指落下时 `requestDisallowInterceptTouchEvent(true)` 阻止两个 ScrollView 抢手势；
  手势结束时向子视图补发 `ACTION_CANCEL`，避免抬手被当成点击而误开课程。
- 自适应尺寸：`vertical` 的 `OnLayoutChangeListener` 在视口变化（旋转、窗口变化）时
  重算基线并保留用户当前缩放倍数；首次布局前用「屏宽 − 节次栏」估算，首帧就是对的。

### L-3. 保留的契约（未动）

`EXTRA_WEEK`、`static lastPeriodInUse`、`static dayColumnRows`、`MIN_ROWS = 10`、
`HorizontalScrollView` 横向滚动机制与表头 `translationX` 同步、`WeekGridPlan` 分簇分道、
`CourseQuery` 过滤、课程点击 → `CourseDetailActivity`、FAB → `EditCourseActivity`、
卡片的三行内容与 `courseTint`/`courseInk` 配色、`CourseColorSync` 规则。

### L-4. 门禁（全绿）

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

| 项 | 结果 |
|---|---|
| 单元测试 | **1018 项 / 0 失败 / 0 错误 / 0 跳过**（原 999，+19 为 `GridZoomTest`） |
| `assembleDebug` | `app-debug.apk` **1,167,404 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### L-5. 设备验证（模拟器 `emulator-5554`，1080×2424 @420）

| 验收项 | 结果 |
|---|---|
| 默认七天同屏、无横滚 | ✅ UI dump：7 个列头 x 边界 140/274/408/542/676/810/944/1078，**正好 1080** |
| 点「放大」×3 | ✅ 列宽 134 → 262px（= 1.25³），横滚按预期回来，显示 4 天 |
| 放大后课程可读 | ✅ 卡片恢复完整三行（如「数字图像处理 / 第2-9周 · 每周 / 教学楼F楼F406多 · 商」） |
| 点「适应」 | ✅ 回到 134px × 7，七天同屏（也是缩放下限，点「缩小」到底不会更小） |
| 放大后连按「缩小」到底 | ✅ 停在 134px × 7，**不会比「适应屏幕」更小**（原「整表」已删除） |
| 缩放后点击课程 | ✅ 点周一 4-5 节 → 打开「数字图像处理 · 周一 · 第4-5节 · 第2-9周」，命中准确 |
| 单周课表 | ✅ 日期 10/12–10/18、七天同屏、上一周/下一周/回到本周均正常 |
| 总课表 ↔ 单周课表切换 | ✅ |

### L-6. 已知问题 / 未验证（**下一轮请注意**）

> **2026-10-16 追加（用户明确要求）**：删掉了原先的「整表」按钮，
> `GridZoom.MIN` 由 `0.6` 改为 **`= FIT`（1.0）**，同时删除 `zoomToFitPeriods()`
> 及其 4 项测试。理由：下限就是「适应屏幕」，再小没有任何可看的东西——
> 七列已经铺满宽度，更小只会让行矮到读不动课名。
> 因此 `[−] [适应] [+]` 三个控件、「缩小」到最底也只是回到七天同屏。

1. **双指捏合手势本身未在设备上模拟过。** `adb root` 在本机生产版模拟器上不可用，
   无法用 `sendevent` 注入两指事件。已通过代码走查 + 按钮走同一条几何路径（已实测）覆盖，
   但**手势识别与 `dispatchTouchEvent` 的多指分支没有真实触控验证** —— 需要用户手测。
2. **默认（适应宽度）下课程名会被省略号截断。** 这是「七天同屏」的必然代价：
   411dp 屏 → 每列约 51dp，中文课名一行只放得下 3–4 字。**两门课重叠共用一列时**
   每道只剩约 25dp，名字会被截到 1 个字。放大即可完整阅读。
   若要默认就好看，需要**按列宽自适应卡片文字**（窄列只显示课名、宽列才显示周次/地点），
   但那属于改「文字规则」，本轮**按用户要求未动**，留待确认。
3. 极小屏（<360dp）、横屏、平板未验证。
4. 缩放倍数**未持久化**（重进页面回到「适应」）。这是有意的：任务要求避免不必要的持久化。
5. **纵向现在一定会滚动**：行高固定 54dp × 缩放，不再有「整表」那种把行压矮以塞下全部节次的档位。
   13 节的数据在默认比例下末两节需滚动才能看到（本机实测）。

### L-7. 工作区状态

改动：`WeekGridActivity.java`、`rule/GridZoom.java`(新)、`rule/GridZoomTest.java`(新)、
`res/drawable/ic_remove.xml`(新)、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`（均为本轮之前就有）。
**Agent 未 commit / 未 push。**

---

## M. 2026-10-16 课表总览：不同周次课程的叠放展示（当前状态）

> 与 A–L 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **R 节**。

### M-1. 解决什么问题

总课表里，同一天同一时间段但**分属不同周次**的课程，原来每门各占一条横向子列，
把星期列挤成窄条、课名被截成竖排。现在这些课程合并为**一个叠放卡片**：主卡片显示课名，
后方露出卡片边缘，并用「+N」表示还有几门；点击弹出该时间段的全部课程列表。

### M-2. 判定规则（关键）

**只有同时满足以下三条才叠放**：

1. 星期相同；
2. 起止节次**完全相同**（起始节次相同但结束不同 → 不同时间段，不叠放）；
3. **实际开课周次互不重叠** —— 用 `ScheduleUtils.occursInWeek` 逐周判断，因此单双周
   （同为 1–18 周但奇偶互斥）**可以**叠放。

**只要有一周同时上课，就不叠放**，仍各占一条子列 —— 真实冲突不会被藏起来。
判定逻辑在 `rule/CourseStacking.java`，与 UI 无关、可在 JVM 上直接测。

**单周课表不受影响**：它按选定周次过滤后，同一周内同时间的两门课必然冲突，
`canStack` 恒为 false，因此不会出现叠放。这是逻辑上的保证，不是额外开关。

### M-3. 改了什么

| 文件 | 改动 |
|---|---|
| `rule/CourseStacking.java` | **新增**。`sameSlot` / `weeksMeet` / `canStack` / `partition` / `unitsOf` |
| `rule/WeekGridPlan.java` | `Block` 由「一门课」改为「一门课或一组替代课程」：新增 `courses()`、`courseCount()`、`isStack()`；`course()` 仍返回主课程。分道打包的输入换成 `unitsOf()`，于是一个叠放组只占一条子列 |
| `WeekGridActivity.java` | 叠放卡片渲染、`+N` 徽标、点击弹底部面板、面板行点击进详情 |
| `rule/CourseStackingTest.java` | **新增** 22 项 |
| `rule/WeekGridPlanTest.java` | +8 项（含「冲突仍各占一条子列」） |

**未动**：课程数据格式、任何课程记录、起止节次/周次/教室/教师、颜色持久化与
`CourseColorSync`、新增/编辑/删除/导入/备份恢复/提醒、冲突检测规则、
七天列宽（`computeDayWidthPx` 未变）、缩放功能（`[−] [适应] [+]` 与范围 `[1.0, 2.4]`）。

### M-4. 视觉与交互

- 叠放组**仍只占一个子列**，卡片在左、上各内缩 `stackPeekPx()`（随行高缩放），
  后方卡片只在左、上露出边缘 —— 不会压到相邻日期或下一节。
- **只露一条边缘**，不随门数增加条数；门数由「+N」表达。
- 叠放卡片**省略地点/教师行**，改为显示「+N」：七列宽度下没有空间，且「还有几门」更有用。
- 点击叠放卡片 → 底部面板（复用 `UiKit.bottomSheet`），标题「周五 · 第 8—9 节」，
  副标题「3 门课程，分属不同周次」，每行「课名 + 周次规则」。
- 点击某行 → 该课程原有详情页；返回后课表与缩放状态保持。
- 只有一门课的普通卡片**行为不变**，直接进详情，不弹面板。

### M-5. 门禁（全绿）

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

| 项 | 结果 |
|---|---|
| 单元测试 | **1047 项 / 0 失败 / 0 错误 / 0 跳过**（上一轮 1017，+30） |
| `assembleDebug` | `app-debug.apk` **1,170,128 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### M-6. 设备验证（模拟器 `emulator-5554`，1080×2424 @420）

| 验收项 | 结果 |
|---|---|
| 原被拆成两列的时段 | ✅ 周一 1–3「检测技术与信号处理 第2-5周」+「+1」，一张卡片占满整列 |
| 三门替代课程 | ✅ 周五 8–9「自然辩证法概论」+「+2」，一个子列 |
| 真实冲突仍分开 | ✅ 周二 10–11「最优化方法」与「模式识别原理」同为第2-9周 → 仍两条子列 |
| 点击叠放卡片 | ✅ 面板「周五 · 第 8—9 节 / 3 门课程，分属不同周次」，三行（自然辩证法概论·第2-9周、工程管理与实验安全·第10-16周、工程管理与实验安全·第17周） |
| 面板行点击 | ✅ 打开「工程管理与实验安全 · 第10-16周」详情，返回后课表状态保持 |
| 放大 ×2 | ✅ 叠放边缘与「+N」位置正确（列宽 134→209px，徽标仍在各自列内） |
| 单周课表 | ✅ 无叠放、无「+N」，卡片仍显示地点+教师，上/下一周正常 |
| 七天列宽 | ✅ 未被叠放数量改变 |

### M-7. 已知问题 / 未验证

1. **双指捏合手势仍未真机验证**（`adb root` 在生产版模拟器不可用）。
2. **默认（适应宽度）下课名仍会被截断**：411dp 屏每列约 51dp，中文一行 3–4 字。
   叠放已缓解「挤成两三条窄列」的问题，但单列宽度没变。
3. 极小屏、横屏、平板未验证；缩放倍数未持久化（有意）。

### M-8. 工作区状态

改动：`rule/CourseStacking.java`(新)、`rule/WeekGridPlan.java`、`WeekGridActivity.java`、
`rule/CourseStackingTest.java`(新)、`rule/WeekGridPlanTest.java`、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`（均为本轮之前就有）。
**Agent 未 commit / 未 push。**（本轮改动之前，缩放功能已由用户提交为 `cc3b0c4`。）

---

## N. 2026-10-16 缩放同步放大文字 / 总课表精简信息 / 当前周置顶（当前状态）

> 与 A–M 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **S 节**。
> 开工前 HEAD `b29dc2f`（叠放功能已提交）、工作区干净，直接在其上修复。

### N-1. 三项改动

**① 卡片文字随缩放同步放大。** 改前只有网格几何（列宽/行高）随缩放变，卡片内文字
字号、内边距、圆角、行间距全是写死的 —— 放大后"大卡片套小字"。

现在 `applyZoomPixels()` 一次性算出**几何 + 卡片内部全部尺寸**：
列宽、行高、课名字号、周次字号、`+N` 字号、卡片内边距、圆角。
计算公式集中在 `rule/GridZoom.java`（`blockNameSp` / `blockMetaSp` / `blockBadgeSp` /
`blockPaddingDp` / `blockRadiusDp`），全是"基准值 × 缩放"，可 JVM 直测。

**关键：每个值都由 `zoom` 重新算出，而不是在上次值上累乘**，所以反复缩放不会累积、
「适应」能把文字也一并复位（实测：放大 ×3 后点适应，课名高度回到 90px = 基准值）。

**② 总课表隐藏地点与教师，只留课名 + 周次。** 总课表卡片现在只有两行：
课程名称 + 周次规则（`ScheduleUtils.weekRule` = "第2-9周 · 每周"，已含单周/双周/每周）。
地点与教师那行**只保留在单周课表**（那里"哪个楼的铃"是有用信息）。
**数据一行未删**，课程详情页也照旧。

**③ 当前周课程置顶。** 叠放组内，**当前学期周次正在上课的那门**排最上层（即主卡片）。
当前周取自 `ScheduleUtils.currentWeek(semesterStart, 今天)` —— 与 App 其它地方同一个口径，
不是自然周号。判断"这门课这周是否上课"用 `ScheduleUtils.occursInWeek`，因此周次范围与
单双周都算进去。

### N-2. 判定与排序

**叠放条件不变**（星期相同 + 起止节次完全相同 + 周次互不重叠）。

排序键改为：**当前周在上课的排最前** → 其余按开课周次升序 → 结束周次 → id。
末三项是稳定次序的保证，所以"没有当前周课程"时顺序可预测、刷新不变。

- 当前周落在所有课程范围之外（学期未开始/已结束）→ 没有课程"当前周在上课"，
  自动退回按周次序，**不会乱认一门**。
- 一个叠放组里**最多一门**课程在任一给定周次上课（这是"周次互不重叠"的直接推论），
  所以"当前周那门"是唯一答案，不存在平票。已加测试逐周验证。
- 同一时段**真有多门同时上课**时，它们是两条子列（冲突），**都不会被藏起来**；
  点叠放区仍可"查看全部"。

### N-3. 改了什么

| 文件 | 改动 |
|---|---|
| `rule/GridZoom.java` | +`blockNameSp`/`blockMetaSp`/`blockBadgeSp`/`blockPaddingDp`/`blockRadiusDp` 及基准/下限常量 |
| `rule/CourseStacking.java` | `partition`/`unitsOf` 增加 `currentWeek` 参数；新增 `runsIn()`；排序键改为"当前周优先" |
| `rule/WeekGridPlan.java` | 新增 `plan(courses, currentWeek)` 重载（原 `plan(courses)` 委托 `NO_WEEK`） |
| `WeekGridActivity.java` | 新增 5 个字段存当前缩放下的小尺寸，在 `applyZoomPixels()` 中赋值；总课表删地点/教师行；`blockDetail` 简化为只返回教师；`render()` 传入 `leadWeek` |
| `rule/GridZoomTest.java` | +5 项 |
| `rule/CourseStackingTest.java` | +7 项 |
| `rule/WeekGridPlanTest.java` | +6 项 |

**未动**：课程数据与任何课程记录、起止节次/周次/教室/教师、颜色持久化与 `CourseColorSync`、
新增/编辑/删除/导入/备份恢复/提醒、冲突检测、七天列宽、`[−] [适应] [+]` 与缩放范围 `[1.0, 2.4]`、
叠放判定规则、底部面板、单周课表的展示。

### N-4. 门禁（全绿）

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

| 项 | 结果 |
|---|---|
| 单元测试 | **1065 项 / 0 失败 / 0 错误 / 0 跳过**（上一轮 1047，+18） |
| `assembleDebug` | `app-debug.apk` **1,170,872 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### N-5. 设备验证（模拟器 `emulator-5554`，1080×2424 @420）

| 验收项 | 结果 |
|---|---|
| 总课表不再显示地点/教师 | ✅ 逐个文本检查，0 处残留 |
| 周次范围与单双周 | ✅ 「第2-17周 · 单周」「第2-9周 · 每周」等准确 |
| 文字随缩放同步放大 | ✅ 课名 90px → 146px、周次 77px → 117px（缩放 1.0 → 1.5625） |
| 放大 ×3 后点「适应」 | ✅ 课名高度回到 **90px**（基准值），无累积、无漂移 |
| 叠放徽标 | ✅ `+1`/`+2` 仍在，且随缩放变大 |
| 当前周课程置顶 | ✅ 周五 8–9 组面板顺序为「自然辩证法概论(第2-9周)」在前 —— 第5周正在上这门 |
| 单周课表 | ✅ 地点+教师照旧显示，无 `+N` 徽标，上一周/下一周正常 |

### N-6. 已知问题 / 未验证

1. **双指捏合手势仍未真机验证**（`adb root` 在生产版模拟器不可用）。
2. **默认（适应宽度）下课名仍会被截断**：411dp 屏每列约 51dp，中文一行 3–4 字。
   缩放只是等比放大，不改变一行能放几个字。
3. 极小屏、横屏、平板未验证；缩放倍数未持久化（有意）。
4. 字号下限（`MIN_BLOCK_*`）在当前缩放范围 `[1.0, 2.4]` 内**不会触发** —— 是为将来
   调低下限预留的保护，非当前行为。

### N-7. 工作区状态

改动：`rule/GridZoom.java`、`rule/CourseStacking.java`、`rule/WeekGridPlan.java`、
`WeekGridActivity.java`、3 个测试类、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`（均为本轮之前就有）。
**Agent 未 commit / 未 push。**

---

## O. 2026-10-16 「回到本周」移至右下角悬浮操作区（当前状态）

> 与 A–N 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **T 节**。

### O-1. 改了什么

「回到本周」从页眉的「总课表 / 单周课表」切换栏里移出，改为右下角的**悬浮胶囊按钮**，
与「添加课程」FAB **垂直堆叠**成一个统一容器。页眉现在只有：返回、标题+周次信息、
上一周/下一周、总课表/单周课表切换栏。

| 项 | 改前 | 改后 |
|---|---|---|
| 页眉 | 三个控件挤在一行（总课表/单周课表/回到本周） | 只剩两个切换项，各占一半宽度 |
| 右下角 | 只有「添加课程」FAB | 「回到本周」胶囊在上，「添加课程」在下 |
| 底部留白 | `FAB_CLEARANCE_DP = 78` | `ACTION_CLEARANCE_DP = 110`（覆盖两个按钮） |
| 左下角缩放控件 | 不变 | 不变 |

### O-2. 排布与防遮挡

- **一个容器两个按钮**：`LinearLayout`（vertical, `Gravity.END`）装胶囊 + FAB，
  间距固定 `ACTION_GAP_DP = 6`。这样无论文字多长、屏幕多窄都不会重叠或互相挤压。
- **右对齐**：两者右边缘对齐在 `x = 屏宽 − SPACE_M`（实测 1048px）。
- **层级**：FAB 仍是填充品牌色（主操作），胶囊是浅色面 + 细边框（次操作）。
- **不遮挡课程**：底部留白从 78dp 提到 **110dp**，正好覆盖「12dp 边距 + 56dp FAB
  + 6dp 间距 + 34dp 胶囊 = 108dp」。滚到底部实测：最后一节课文字结束在 y=1714，
  胶囊顶在 y=2077，中间空 147dp。**没有缩窄星期列、没有改行高或缩放比例。**
- **与左下角缩放控件不冲突**：缩放控件 x=58..368，胶囊 x=892..1048，
  水平间距 524px（200dp），触控区不可能相交。

### O-3. 「回到本周」行为

- 复用 `backToThisWeek()`：`wholeSemester = false` + `selectedWeek = currentWeek` + `render()`。
  **周次来自 `ScheduleUtils.currentWeek(semesterStart, 今天)`** —— 与页眉周次读数和
  MainActivity 的「回到本周」同一口径，不是自然周号。
- `render()` 会真的重画网格，不只是改标题。
- **三态**（沿用并细化原 chip 的启用规则）：
  - 总课表模式 → 隐藏（没有「周」可回，与周次步进器一起隐藏）
  - 已在本周 → 可见但禁用、视觉变淡（**不隐藏**，避免按钮在用户拇指下跳动）
  - 在别的周 → 可用，强调色底

### O-4. 改了什么（文件）

只改 **`WeekGridActivity.java`**：

- 常量：`FAB_CLEARANCE_DP(78)` → `ACTION_CLEARANCE_DP(110)`；新增
  `TODAY_PILL_HEIGHT_DP(34)`、`ACTION_GAP_DP(6)`
- 字段：`todayChip` 现在是胶囊里的**文字**；新增 `todayPill`（胶囊容器）
- `buildRoot()`：FAB 与胶囊组成一个 `LinearLayout` 容器
- 新增 `createTodayPill()`（图标 + 文字）、`backToThisWeek()`
- `createModeBar()`：删掉第三个 chip，切换栏只剩两项
- `styleTodayChip()` → `styleTodayButton()`：改样式胶囊，三态
- `render()`：可见性判断改用 `todayPill`
- 两处 `dp(GRID_PAD_DP + FAB_CLEARANCE_DP)` → `ACTION_CLEARANCE_DP`

**未动**：总课表页面的布局、课程数据、增删改、颜色同步、提醒、备份恢复、
缩放功能（`[−] [适应] [+]` 与范围 `[1.0, 2.4]`）、七天列宽、单周课表的内容展示。

### O-5. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1065 项 / 0 失败 / 0 错误 / 0 跳过**（本轮为纯布局改动，未新增规则逻辑） |
| `assembleDebug` | `app-debug.apk` **1,171,260 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### O-6. 设备验证（模拟器 `emulator-5554`）

| 验收项 | 结果 |
|---|---|
| 页眉不再有「回到本周」 | ✅ 只剩 返回/标题+周次/上一周·下一周/总课表·单周课表 |
| 胶囊在 FAB 上方、右对齐、不重叠 | ✅ 胶囊 y=2077..2166、FAB y=2182..2329，右边缘都在 1048 |
| 「回到本周」切到当前学期周次 | ✅ 从第10周(11/2-11/8) 点按 → 回到 本周·10/12-10/18 |
| 「添加课程」 | ✅ 打开编辑器 |
| 左下角缩放控件 | ✅ 位置与交互未变 |
| 滚到底不遮挡课程 | ✅ 末节文字止于 y=1714，胶囊顶 y=2077 |

### O-7. ⚠️ 本轮发生的一次数据事故（务必告知用户）

为了排除「装的是旧包」的疑虑，我执行了 `adb uninstall` 再重装。
**`adb uninstall` 会连应用的私有数据一起删除**，模拟器上的课表数据（23 门课程）
因此被清空。已用用户自己的备份文件
`weihe-backup-20261014-1547.json`（微信文件目录）通过应用内
「设置 → 课表数据 → 从备份预览 → 覆盖恢复」**完整恢复**：

- 首页确认：`2026 秋季学期 · 9/14 开学`、课程逐条回来（矩阵论/自然辩证法概论/…）
- 恢复结果页确认：`已恢复 1 个学期 · 23 门课程 · 3 张作息表`

**注意**：该备份是 **10-14** 导出的。若用户在 10-14 之后又在设备上改过课表，
那部分改动**无法找回**（无更新备份）。请用户核对一遍。

另外：我把备份文件 `adb push` 到了设备的 `/sdcard/Download/` 下以便恢复，
该文件仍留在那里（是用户自己的备份，无害）。

**教训**：验证 UI 时**只用 `install -r`，绝不 `uninstall`**。

### O-8. 工作区状态

改动：`WeekGridActivity.java`、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`。
**Agent 未 commit / 未 push。**

---

## P. 2026-10-16 「回到本周」改为真正的按钮外观（当前状态）

> 与 A–O 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **U 节**。
> 上一轮（O 节）只是把按钮**挪了位置**，外观仍是"浅底 + 深字"的文字标签感，辨识度不足；
> 本轮只改视觉样式与尺寸，位置与行为不变。

### P-1. 改了什么

只改 **`WeekGridActivity.java`** 里胶囊的样式与三个尺寸常量：

| 项 | 改前 | 改后 |
|---|---|---|
| 底色（可用态） | `SURFACE_CONTAINER_HIGH`（浅灰绿，几乎与页面同色） | **`SURFACE_CONTAINER`（纯白 `#FFFFFF`）**，与薄荷色页面 `#F3F7F3` 明确区分 |
| 图标+文字色 | `ON_ACCENT_CONTAINER`（深棕） | **`Palette.PRIMARY`（深绿 `#11584E`）** —— 项目主题色 |
| 阴影 | 无 | 可用态 `ELEVATION_MEDIUM`（3dp），禁用态归零 |
| 上下内边距 | **0**（图标文字贴边） | `SPACE_S`（8dp）四边一致 |
| 高度 | 34dp | **42dp**（更像按钮、点击区更大，仍明显低于 FAB 的 56dp） |
| 与 FAB 间距 | 6dp | **8dp** |
| 底部留白 | 110dp | **122dp**（= 12dp 边距 + 56dp FAB + 8dp 间距 + 42dp 胶囊 = 118dp，留 4dp 余量） |

### P-2. 三态（实测取色）

| 状态 | 底色 | 图标/文字 | 阴影 | 可点 |
|---|---|---|---|---|
| 可用（在别的周） | `#FFFFFF` | `#11584E` 深绿 | 3dp | 是 |
| 已在本周 | `#E8EFEA` | `#4E5C57` 灰 | 无 | 否（**仍可见**，避免按钮在拇指下跳动） |
| 总课表模式 | — 隐藏（与周次步进器一起） | — | — | — |

描边两种状态都是 `#DFE7E1`（`OUTLINE_VARIANT`），所以禁用态也读得出"这是个按钮，只是不能用"。

### P-3. 排布（实测坐标，1080×2424 @420）

- 胶囊：右边缘 **1048px**、y≈2051..2161（高 42dp=110px），宽约 106dp
- FAB：右边缘 **1048px**、y=2182..2329 —— **两者右对齐、右安全边距一致（12dp）**
- 垂直间距 **8dp**（21px），不重叠不贴合
- 左下角缩放控件 x=58..368，与胶囊（x≈892..1048）水平距 524px，触控区不相交
- 滚到底：末行（第13节）止于 y=2025，胶囊顶 y=2051，**空 26px（10dp）**，不遮挡课程

### P-4. 行为（未改）

`backToThisWeek()` 仍是 `wholeSemester=false` + `selectedWeek=currentWeek` + `render()`，
周次来自 `ScheduleUtils.currentWeek(semesterStart, 今天)`。实测：第7周(10/26-11/1) 点按 →
回到 本周·10/12-10/18，且网格真的重画。「添加课程」FAB 样式与功能未动（实测底色仍
`#11584E`）。

### P-5. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1065 项 / 0 失败 / 0 错误 / 0 跳过**（纯视觉改动，未新增规则逻辑） |
| `assembleDebug` | `app-debug.apk` **1,171,320 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### P-6. 设备验证（模拟器 `emulator-5554`，**只用 `install -r`**）

| 验收项 | 结果 |
|---|---|
| 独立按钮外观 | ✅ 白底 + 深绿图标文字 + 细描边 + 阴影，四边内边距一致 |
| 与 FAB 垂直排列、右对齐、不重叠 | ✅ 右边缘都在 1048，间距 8dp |
| 点击区 | ✅ 42dp 高 × 约 106dp 宽 |
| 禁用态 | ✅ 灰底灰字、无阴影，但仍是按钮形状 |
| 行为 | ✅ 第7周 → 本周，标题与课程同步更新 |
| 缩放控件 | ✅ 放大 134→168px、适应回到 7 天 134px |
| 滚到底不遮挡 | ✅ 末行 y2=2025，胶囊顶 2051 |
| 数据完好 | ✅ 23 门课程在（上一轮恢复后未再动） |

### P-7. 未验证

极小屏、横屏、平板、三键导航模式未实测（本机为手势导航，控件边距从内容区底部起算）。

### P-8. 工作区状态

改动：`WeekGridActivity.java`、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`。**Agent 未 commit / 未 push。**

---

## Q. 2026-10-16 「回到本周」改为纯图标圆形 FAB（当前状态）

> 与 A–P 各节冲突时**以本节为准**。**P 节描述的"白底胶囊 + 文字"已不存在**，
> 被本节取代。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **V 节**。

### Q-1. 改了什么

「回到本周」不再是有文字标签的胶囊，改为**与「添加课程」完全同款的圆形 FAB**，
只有内部图标不同；界面上不再出现任何按钮文字。

| 项 | 改前（P 节） | 改后 |
|---|---|---|
| 形态 | 胶囊（LinearLayout + 图标 + **文字**） | **圆形 FAB**，`UiKit.fab(...)` |
| 尺寸 | 42dp 高 × 约 106dp 宽 | **56dp × 56dp**（= `UiTokens.FAB_SIZE`） |
| 底色（可用） | 纯白 `#FFFFFF` | **`Palette.PRIMARY` `#11584E`**（与下方 FAB 相同） |
| 图标/文字 | 深绿 `#11584E` | **白色 `ON_PRIMARY`**（与下方 FAB 相同） |
| 文字 | 「回到本周」 | **无**（仅 `contentDescription = "回到本周"`，供无障碍） |
| 阴影 | 3dp | `ELEVATION_HIGH`（6dp，`UiKit.fab` 自带） |
| 底部留白 | 122dp | **136dp** = 12 + 56 + 8 + 56（+4dp 余量） |

**关键点：两个按钮都走 `UiKit.fab(...)`，所以圆角、直径、配色、阴影、涟漪、
点击区全部由同一段代码产生，不可能不一致**；差别只在传入的图标。

- 上方：`R.drawable.ic_today`（日历 + 已标记的日期方块，即项目自带的"今天"字形，
  符合"今日日期 / 日历"的要求，也与其它页面的日历语义一致）
- 下方：`R.drawable.ic_add`（"+"，功能未动）

### Q-2. 布局（实测坐标，1080×2424 @420）

| 元素 | 位置 | 尺寸 |
|---|---|---|
| 回到本周 FAB | x=901..1048, y=2014..2161 | **147×147px = 56×56dp** |
| 添加课程 FAB | x=901..1048, y=2182..2329 | **147×147px = 56×56dp** |

- **右边缘都在 1048px**（右边距 12dp，`SPACE_M`）
- **垂直间距 21px = 8dp**（`ACTION_GAP_DP`），不重叠不贴合
- 左下角缩放控件 x=58..368、y=2208..2308 —— 与右侧按钮组水平相距 533px，触控区不相交
- 滚到底：第 13 行止于 y=1988，回到本周 FAB 顶 y=2014 → **空 26px（10dp）**，不遮挡课程

### Q-3. 三态

| 状态 | 表现 | 实测 |
|---|---|---|
| 可用（在别的周） | 标准 FAB：`#11584E` 底 + 白色图标 + 6dp 阴影 | ✅ 与下方 FAB 逐像素同色 |
| 已在本周 | 压平：`#E8EFEA` 底 + 灰图标 + 无阴影，**位置与尺寸不变** | ✅ 圆仍在原处，不会在拇指下跳动 |
| 总课表模式 | 隐藏（与周次步进器一起，没有"周"可回） | ✅ |

> 关于"完全一致"：**两个按钮只要都处于可用态，就是同一段 `UiKit.fab` 画出来的同一个圆**。
> 已在本周时上方按钮转为压平的禁用态 —— 这是**状态**而非样式差异，且是上一轮
> "已在本周应隐藏或禁用"要求的落实。若用户希望更严格（例如已在本周直接隐藏，
> 与 `MainActivity.syncReturnControls` 的既有规则一致），改 `styleTodayButton()`
> 一处即可，**容器是底部对齐的，隐藏上方按钮不会让下方 FAB 移动**。

### Q-4. 行为（未改）

- 上方按钮 → `backToThisWeek()`：`wholeSemester=false` + `selectedWeek=currentWeek` + `render()`，
  周次来自 `ScheduleUtils.currentWeek(semesterStart, 今天)`。实测：第7周(10/26-11/1) → 本周·10/12-10/18 ✅
- 下方按钮 → `openEditor(null)`，实测打开「添加课程」编辑器 ✅
- 无障碍：`contentDescription = "回到本周"`，界面上无文字节点 ✅（dump 验证 0 处）

### Q-5. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1065 项 / 0 失败 / 0 错误 / 0 跳过** |
| `assembleDebug` | `app-debug.apk` **1,171,092 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### Q-6. 设备验证（模拟器 `emulator-5554`，**只用 `install -r`**）

两个 FAB 尺寸/右对齐/间距/同色 ✅；界面无文字节点 ✅；点击回本周 ✅；
点「+」开编辑器 ✅；禁用态压平且不移位 ✅；缩放控件未受影响 ✅；
滚到底不遮挡（空 10dp）✅；数据完好（23 门课程）✅。

### Q-7. 未验证

极小屏、横屏、平板、三键导航模式未实测。图标若想换成"日历 + 定位标记"，
只需新增一个 drawable 并改 `R.drawable.ic_today` 一处。

### Q-8. 工作区状态

改动：`WeekGridActivity.java`、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`。**Agent 未 commit / 未 push。**

---

## R. 2026-10-16 首页「回到今天」改为圆形 FAB（当前状态）

> 与 A–Q 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **W 节**。
> 只改 **`MainActivity.java`**（首页），未动单周课表页面已完成的纯图标 FAB 设计。

### R-1. 改了什么

首页日期的「回到今天」原来是**日头（day header）里的文字 chip**，现在改为
**右下角圆形 FAB**，放在「课表总览」FAB **正上方**。

| 项 | 改前 | 改后 |
|---|---|---|
| 形态 | 日头里的文字 chip（`navChip("回到今天")`） | **圆形 FAB**，`UiKit.fab(this, R.drawable.ic_today, "回到今天", …)` |
| 位置 | 「周一 · 10/12」那一行的右侧 | **右下角**，「课表总览」FAB 正上方 |
| 文字 | 「回到今天」 | **无**（仅 `contentDescription = "回到今天"`） |
| 尺寸 | `CHIP_HEIGHT`(36dp) 高 × 文字宽 | **56dp × 56dp**（= `FAB_SIZE`） |
| 列表底部留白 | `FAB_SIZE + SPACE_XXL` = 88dp（只够一个 FAB） | **150dp** = `16 + 56 + 10 + 56 + 12` |

**两个按钮都走 `UiKit.fab(...)`** —— 圆、尺寸、底色、阴影、涟漪、点击区由同一段代码产生，
只有图标不同（上 `ic_today`，下 `ic_grid`）。

### R-2. 布局（实测坐标，1080×2424 @420）

| 元素 | 位置 | 尺寸 |
|---|---|---|
| 回到今天 FAB | x=891..1038, y=1999..2146 | **147×147px = 56×56dp** |
| 课表总览 FAB | x=891..1038, y=2172..2319 | **147×147px = 56×56dp** |

- **右边缘都在 1038px**（右边距 16dp，`SPACE_L`）
- **垂直间距 26px = 10dp**（`FAB_GAP_DP`），不重叠
- 取色：两个都是 `#11584E` 底 + 白色图标（`PRIMARY` / `ON_PRIMARY`）
- 滚到底：最后一张课程卡片内容止于 y=1761，回到今天 FAB 顶 y=1999 → **空 238px**，不遮挡
- 容器 `Gravity.END | Gravity.BOTTOM`，**所以隐藏上方按钮不会让下方 FAB 移动**

### R-3. 行为（复用既有逻辑，未改）

- 点击 → `goToToday()`：`TodayTarget.week/day(...)` 算出目标周次与星期 →
  `selectedWeek`/`selectedDay` → `refreshAll()` + `swapIn(...)`。
  **周次与星期都来自既有 `TodayTarget`**，没有新写一套。
  实测：周一(10/12) 点按 → 标题变「今天 · 10/16 周五」、**课程列表同步换成周五的 3 门课**
  （矩阵论 / 自然辩证法概论 / 最优控制与状态估计），不是只改标题。
- 可见性沿用既有规则 `syncReturnControls()`：**已显示今天时隐藏**（现在改判 `todayFab`）。
  实测：在「今天」时只有课表总览 FAB；切到周一后回到今天 FAB 出现在其上方。
- 「课表总览」FAB 图标与功能未动，实测仍能打开总览、返回后两个按钮都在。

### R-4. 一个已知的取舍（用户需知）

首页左上角的**品牌标记**（`createBrandMark()`）用的也是 `ic_today`。
原先移除这个 FAB 的理由之一就是"和品牌标记同一个日历字形，一个画面两处同图"。
本轮按用户要求"沿用项目现有的图标资源、用日历或今日图标"，仍用了 `ic_today`，
所以这个重复又回来了（品牌标记是 34dp 圆角方块在顶栏，FAB 是 56dp 圆在右下角）。
若要区分，最省事的做法是新增一个"日历 + 定位点"的 drawable 给 FAB 用，改一处引用即可。

### R-5. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1065 项 / 0 失败 / 0 错误 / 0 跳过**（纯布局改动） |
| `assembleDebug` | `app-debug.apk` **1,171,136 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### R-6. 设备验证（模拟器 `emulator-5554`，**只用 `install -r`**）

| 验收项 | 结果 |
|---|---|
| 首页不再显示返回按钮文字 | ✅ 全页文本节点中 0 处「回到今天」 |
| 返回按钮为圆形纯图标 FAB | ✅ 147×147px = 56dp，`#11584E` 底 |
| 与右下角按钮样式统一 | ✅ 同一段 `UiKit.fab`，逐像素同色 |
| 位于现有按钮上方、垂直排列不遮挡 | ✅ 右边缘都在 1038，间距 10dp |
| 点击仍执行返回当前日期 | ✅ 周一 → 今天·10/16 周五，列表同步 |
| 首页其他功能与单周课表不受影响 | ✅ 课表总览正常；23 门课程在 |
| 不长期遮挡课程卡片 | ✅ 滚到底空 238px |

### R-7. 未验证

极小屏、横屏、平板、三键导航模式未实测。

### R-8. 工作区状态

改动：`MainActivity.java`、`WeekGridActivity.java`（上一轮遗留的未提交改动）、
本文件、`AGENT_HANDOFF_ARCHIVE.md`。未跟踪：`.workbuddy-ai/`、`app/release/`。
**Agent 未 commit / 未 push。**

---

## S. 2026-10-16 首页移除周次卡片里的「回到本周」（当前状态）

> 与 A–R 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **X 节**。
> 只改 **`MainActivity.java`**（首页）。**右下角的日历 FAB（回到今天）未动。**

### S-1. 删掉的是哪一个（两个控件必须分清）

首页上有**两个**"返回"控件，本轮只删第一个：

| 控件 | 位置 | 处置 |
|---|---|---|
| `backToThisWeek`「回到本周」文字 chip | **周次切换卡片**里，第N周与 `<` `>` 之间 | **本轮删除** ✅ |
| `todayFab` 日历图标圆形 FAB | **页面右下角** | **保留，未改** ✅ |

### S-2. 改动

1. **`createWeekCard()`**：删掉 `backToThisWeek` 的创建与 `card.addView(...)`，
   连同那段 `backParams`。卡片现在只有：周次/单双周/日期块 → `本周` 状态 pill →
   `上一周` `下一周`。**没有留下空占位**（原本就是 `WRAP_CONTENT` + `leftMargin`，
   删掉即自然收紧；实测 `<` 仍在 x=723..849、`>` 在 x=870..996，位置未变）。
2. **字段 `backToThisWeek`** 删除。
3. **`syncReturnControls()`** 里针对它的可见性分支删除；方法现在只维护 `todayFab`，
   javadoc 由"two controls"改成单数。
4. **`navChip()` 删除** —— 全项目只有 `backToThisWeek` 用它，删完即成死代码。
   （`UiKit.ripple` / `setRoundedBackground` 仍被其它页面使用，未动。）
5. `goToToday()` / `isShowingToday()` / `TodayTarget` / `stepWeek()` **全部保留未动**。

### S-3. ⚠️ 一个能力损失（用户需知）

被删掉的 chip 做的是「回到本周，但**保留当前正在看的星期几**」——
从第12周的周三按它，会到第6周的周三。右下角的日历 FAB 做的是
「回到本周 **且** 回到今天这个星期几」（`goToToday()`）。
**删掉 chip 后，"回本周但保留星期几"这个动作就没有入口了**，
只剩 `上一周`/`下一周` 逐周走。这是本次删除唯一的代价，已在卡片处留注释说明。

### S-4. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1065 项 / 0 失败 / 0 错误 / 0 跳过**（纯 UI 删除，无新增规则逻辑） |
| `assembleDebug` | `app-debug.apk` **1,170,756 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### S-5. 设备验证（模拟器 `emulator-5554`，**只用 `install -r`**）

| 验收项 | 结果 |
|---|---|
| 周次卡片里不再有「回到本周」 | ✅ 切到第7周（过去会显示）后，全页文本节点 0 处 |
| 卡片布局不留空白 | ✅ 周次块 → `本周` pill → `<` `>`，`<` `>` 位置与删前一致 |
| 上一周 / 下一周 功能 | ✅ 第7周 →(上一周) 第6周 →(下一周) 第7周 |
| 右下角日历 FAB 保留 | ✅ 仍在 x=891..1038、y=1999..2146（56dp 圆），上方于「课表总览」 |
| 点日历 FAB 仍能回今天 | ✅ 第7周 → 第5周·本周，课程列表同步为今天 3 门课（矩阵论/自然辩证法概论/最优控制与状态估计） |
| 已在本周时日历 FAB 隐藏 | ✅ 只剩「课表总览」 |
| 不遮挡课程卡片 | ✅ 滚到底最后一张卡片内容止于 y=1903，日历 FAB 顶 y=1999 |
| 其它页面 | ✅ 总课表 / 单周课表未动；23 门课程完好 |

### S-6. 未验证

极小屏、横屏、平板、三键导航模式未实测。

### S-7. 工作区状态

改动：`MainActivity.java`、`WeekGridActivity.java`（前几轮遗留的未提交改动）、
本文件、`AGENT_HANDOFF_ARCHIVE.md`。未跟踪：`.workbuddy-ai/`、`app/release/`。
**Agent 未 commit / 未 push。**

---

## T. 2026-10-17 课程共享备注（当前状态）

> 与 A–S 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **Y 节**。

### T-1. 课程身份：复用既有规则，不另造一套

**同一门课 = 同一学期内、去掉首尾空格后名称完全相同** —— 就是 `CourseColorSync.sameCourseAs`
已经在用的那条规则（`CourseColorSync.normalize`）。星期、节次、周次、教室、教师**刻意不参与**
匹配，因为那些差异正是同一门课被拆成多条记录的原因。仓库的 `getCourses()` 只返回当前学期，
所以跨学期天然隔离，不需要额外判断。

### T-2. 备注存在哪：每条记录上，由仓库一次写全组

`Course.note` 是记录字段（和 `color` 完全一样的做法）。仓库新增两个方法：

| 方法 | 作用 |
|---|---|
| `noteOf(Course)` | 读这门课的备注：自己的非空就用，否则取同名兄弟的第一个非空 |
| `saveCourse(Course, String note)` | **一次提交**写入记录 + 把备注同步到所有同名安排 |

**为什么不做"按课程名建一张备注表"**：改名会把一门课拆成两组，按名字建的表会让刚改名的
那条记录指向一个已不存在的键，**备注当场丢失**。存在记录上则改名时备注跟着走，
留在原处的安排保留自己那份，谁都不会静默丢东西。这也让备份、删除、学期隔离
全部无需特殊处理。

同步时只动 `note`，**其余字段一个不碰** —— 改备注绝不会移动一节课。

### T-3. 保存语义（防误清空）

`CourseNotes.noteToStore(typed, groupNote, isNew)`：

- 字段有内容 → 存它（首尾空白裁掉，**中间换行保留**）
- 字段为空 **且是编辑已有记录** → 存空（用户是看着预填内容主动清掉的）
- 字段为空 **且是新建记录** → 若该课已有备注则**沿用**（新安排打开时字段本来就是空的，
  把这份"空"写下去等于抹掉用户没看见的备注）

### T-4. 改了什么

| 文件 | 改动 |
|---|---|
| `model/Course.java` | 新增 `note` 字段 + `copy()` 带上 |
| `rule/CourseNotes.java` | **新增**（纯逻辑）：`isBlank` / `groupNote` / `noteToStore` |
| `data/CourseRepository.java` | 新增 `noteOf` / `saveCourse`；`toJson`/`fromJson` 读写 `note`；新增对 `rule` 的依赖（方向正确：rule 保持纯净） |
| `data/BackupCodec.java` | `courseToJson` / 读取 补 `note`（**与仓库的映射必须逐字段一致**） |
| `rule/CourseDraft.java` | 新增 `note`（表单内容的一部分，旋转后不丢） |
| `EditCourseActivity.java` | 新增「课程备注」多行输入区；预填 `noteOf`；草稿存取；保存改走 `saveCourse` |
| `CourseDetailActivity.java` | 新增「课程备注」卡片（有备注才显示） |
| `rule/CourseNotesTest.java` | **新增** 11 项 |
| `data/CourseNoteStorageTest.java` | **新增** 15 项 |
| `rule/CourseDraftTest.java` | 9 处构造补 `null` 参数 |

**存储兼容**：`note` **仅在非空时写入**。所以没有备注的课程存出来的 JSON 与改动前逐字节相同，
旧备份（没有 `note` 键）照常读取为空，不会被拒绝。

### T-5. 删除与备份

- 删一条安排 → 备注在记录上，其他安排不受影响
- 删最后一条 → 备注随记录一起消失，**无残留元数据**（因为没有独立的元数据）
- 备份导出/恢复 → `note` 随课程字段走，同名安排恢复后仍是同一组
- 旧备份（无 `note`）→ 恢复为空备注，不报错

### T-6. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1091 项 / 0 失败 / 0 错误 / 0 跳过**（上一轮 1065，+26） |
| `assembleDebug` | `app-debug.apk` **1,171,940 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### T-7. 设备验证（模拟器 `emulator-5554`，**只用 `install -r`**）

| 验收项 | 结果 |
|---|---|
| 编辑页有独立多行备注区 | ✅ 「课程备注」+ 提示「记录作业、考试安排、学习资料等」，3 行高 |
| 键盘弹出时仍可保存 | ✅ 输入时「保存课程」仍在键盘上方可见 |
| 换行能输入 | ✅ dump 中为 `hw1－ch3&#10;final－nov20`（`&#10;` 即换行） |
| 详情页展示备注 | ✅ 「课程备注」卡片显示两行原文 |
| **同课程另一安排共享** | ✅ 周四 1-2 节写入 → 周一 2-3 节（不同星期/节次/教室）详情页读到同样内容 |
| 重启后仍在 | ✅ `force-stop` + 重启 + 重新进入，备注仍在 |
| 编辑页预填 | ✅ 打开编辑页时备注框已填好 |
| 课表卡片不含备注 | ✅ 总课表、单周课表全文搜索 0 处 |
| 清空备注 | ✅ 清空保存后，两条安排的备注卡片都消失 |
| 其他功能 | ✅ 23 门课程、周次、颜色、叠放均正常 |

### T-8. 未验证 / 限制

1. **中文备注没有在设备上实打实输入过** —— `adb shell input text` 无法输入非 ASCII，
   设备上验的是 ASCII + 换行；中文、标点、多行的存取由 `CourseNoteStorageTest`
   的中文/特殊字符用例覆盖。
2. 极小屏、横屏、平板未验证。
3. 复制课程（`复制课程`）会复制备注——因为备注是记录字段。这是符合"备注属于课程"的预期，
   但没有单独做设备验证。

### T-9. 工作区状态

改动：`model/Course.java`、`rule/CourseNotes.java`(新)、`data/CourseRepository.java`、
`data/BackupCodec.java`、`rule/CourseDraft.java`、`EditCourseActivity.java`、
`CourseDetailActivity.java`、3 个测试类、本文件、`AGENT_HANDOFF_ARCHIVE.md`。
未跟踪：`.workbuddy-ai/`、`app/release/`。**Agent 未 commit / 未 push。**

---

## U. 2026-10-18 课程详情页备注区常显 + 一键定位到备注框（当前状态）

> 与 A–T 各节冲突时**以本节为准**。逐轮细节见 `AGENT_HANDOFF_ARCHIVE.md` 的 **Z 节**。
> **只改展示与交互，备注的共享/持久化/备份逻辑一行未动**（见 T 节）。

### U-1. 改了什么

| 项 | 改前 | 改后 |
|---|---|---|
| 备注卡片是否显示 | **只有备注非空才显示** | **始终显示** |
| 空备注 | 整块卡片不存在 | 标题 + 「添加备注」+ 「暂无备注」+ 提示 |
| 有备注 | 标题 + 正文 | 标题 + 「编辑」+ 正文（换行保留） |
| 进入编辑页 | 从表单顶部开始 | 点「添加备注」/「编辑」→ **直接滚到备注框、聚焦、弹键盘** |

### U-2. 空状态与有备注状态

两者共用一张卡片，**只有标题右侧的按钮和标题下方的内容不同**：

```
空：  课程备注                        添加备注
      暂无备注
      可记录作业、考试安排、学习资料等

有：  课程备注                        编辑
      <备注原文，保留换行>
```

- 入口用 `UiKit.textButton`（项目里最轻的按钮样式），**不用实心按钮** ——
  这一屏唯一的主操作仍然是「编辑课程」
- 空状态整卡约 130dp，不是大块留白；「暂无备注」用 `ON_SURFACE_VARIANT`，
  提示用更小一号的 `LABEL_MEDIUM` + `OUTLINE`（编辑器里 hint 用的同一色）
- 有备注时**不显示「暂无备注」**，也不显示提示语

### U-3. 一键定位到备注框

新增 `EditCourseActivity.EXTRA_FOCUS_NOTE`（只决定光标落在哪，不影响读写）：

- `CourseDetailActivity.openEditor(true)` 带上它
- `EditCourseActivity.focusNote()` 在 `formScroll.post(...)` 里：`requestFocus()` →
  按**两者在窗口中的实际位置**算出滚动偏移（不是 `getTop()`，那是相对父容器的，
  会被上方几个分区的高度带偏）→ `smoothScrollTo` → `showSoftInput`

复用的是**同一个编辑页、同一个输入框、同一套保存逻辑**，没有新建备注专用界面。

### U-4. 刷新与失败处理

- 详情页 `onResume()` 本来就 `repository.reload()` + `render()`，
  所以从编辑页返回会立刻显示新备注 —— 未新增任何刷新机制
- 保存失败沿用既有路径：`commitSave` 里只有真写到仓库才提示成功，失败走原有错误处理
- 取消（返回不保存）不会碰备注 —— 实测验证

### U-5. 改了什么（文件）

| 文件 | 改动 |
|---|---|
| `CourseDetailActivity.java` | 备注卡片改为常显并分两态；`openEditor(boolean focusNote)` |
| `EditCourseActivity.java` | 新增 `EXTRA_FOCUS_NOTE`、`formScroll` 字段、`focusNote()` |

**未动**：`Course.note`、`CourseNotes`、`CourseRepository.noteOf/saveCourse`、
`BackupCodec`、`CourseDraft`、课表/缩放/叠放/颜色/周次/提醒/导入/备份。

### U-6. 门禁（全绿）

| 项 | 结果 |
|---|---|
| 单元测试 | **1091 项 / 0 失败 / 0 错误 / 0 跳过**（纯 UI 改动，未新增规则逻辑，测试数不变） |
| `assembleDebug` | `app-debug.apk` **1,172,804 字节** |
| `lintDebug` | **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`） |

### U-7. 设备验证（模拟器 `emulator-5554`，**只用 `install -r`**）

| 验收项 | 结果 |
|---|---|
| 空备注仍显示备注区 | ✅ 「课程备注 / 添加备注 / 暂无备注 / 可记录…」四项齐全 |
| 点「添加备注」直达备注框 | ✅ 进入编辑页即**已滚到备注框、EditText `focused=true`、键盘弹出** |
| 保存后详情页立即刷新 | ✅ 两行备注立刻出现，「暂无备注」消失，出现「编辑」 |
| 有备注时显示内容 + 编辑入口 | ✅ 换行保留（dump 中 `&#10;`） |
| 取消编辑不覆盖 | ✅ 返回详情页备注原样 |
| 清空后回到空状态 | ✅ 保存空备注后回到「暂无备注 + 添加备注」 |
| 跨安排共享 | ✅ 周一 2-3 节写入 → 周四 1-2 节详情页同样内容 |
| 重启后仍在 | ✅ `force-stop` 重启后仍在 |
| 长/多行备注 | ✅ 两行正常换行，卡片高度自适应，无错位 |

**清理**：验证用的测试备注已清空，设备数据恢复原状。

### U-8. 未验证

1. 本轮**没有**在设备上重新验证「不同课程备注互不覆盖」与「长备注（十几行）」——
   前者是仓库同一段代码、上一轮已验并有单元测试覆盖；后者只验了两行。
2. 极小屏、横屏、平板未验证。
3. `adb` 无法输入中文，设备上验的是 ASCII + 换行；中文由单元测试覆盖。

### U-9. 工作区状态

改动：`CourseDetailActivity.java`、`EditCourseActivity.java`（含 T 节遗留的未提交改动）、
本文件、`AGENT_HANDOFF_ARCHIVE.md`。未跟踪：`.workbuddy-ai/`、`app/release/`、
`rule/CourseNotes.java`、两个新测试类。**Agent 未 commit / 未 push。**

---

## V. 2026-10-18 上课提醒可靠性修复（当前状态）

> **与 A–U 各节冲突时以本节为准。** 逐轮细节见归档 **AA 节**。
> 起因：用户在**真实设备**上装包后，上课提醒没有正常触发。

### V-1. 根因（按影响排序，均有代码依据）

| # | 根因 | 依据（修复前） | 造成的现象 |
|---|---|---|---|
| **1** | **迟到即丢弃**：接收器在 `now >= 课程开始` 时直接 `return`，什么都不发 | 旧 `ReminderReceiver.announce()` | 闹钟只要晚到（哪怕 1 秒）就**永远不提醒**。这是「完全收不到」最直接的代码原因 |
| **2** | **只有非精确闹钟**：`setAndAllowWhileIdle`，清单里连精确闹钟权限都没声明 | 旧 `ReminderScheduler.register()`、旧 `AndroidManifest.xml` 注释「SCHEDULE_EXACT_ALARM is deliberately absent」 | Doze / 厂商省电下可晚几分钟到几十分钟 → 命中根因 1 → 静默无提醒 |
| **3** | **没有精确闹钟权限流程**：不检查 `canScheduleExactAlarms()`、无授权入口、无「权限恢复后重排」 | 全项目 grep 无 `canScheduleExactAlarms` | 永远走非精确，且用户无从改善 |
| **4** | **数据变更后不重排**：保存/删除/导入/恢复/学期切换都不调用 `sync()`，只有首页 `onResume`、设置页开关与闹钟触发时才重排 | 旧 `EditCourseActivity`、`BackupPreviewActivity`、`SemesterListActivity`、导入路径均无 `ReminderScheduler.sync` | 改课/删课后旧闹钟继续响到下一次重启或回首页；新建课程可能完全没有闹钟 |
| **5** | **权限恢复后不重排**：设置页 `onResume` 只刷新文本不重排；精确闹钟权限变化无监听 | 旧 `SettingsActivity.onResume` | 在系统设置里打开通知权限后回到应用，界面显示「已开启」但**一条闹钟都没有** |
| **6** | **没有任何自检手段** | 旧设置页只有「已安排 N 条提醒」 | 用户与开发者都无法区分「没安排」「被权限挡」「渠道被关」 |

> 说明：根因 1+2+3 会**同时**导致「真机上完全收不到」，且都不会崩溃、不会报错，只表现为静默。模拟器上（几乎不进入 Doze）历轮验证是绿的，这正是它一直没被发现的原因。

### V-2. 修复内容

| 项 | 修复后 |
|---|---|
| 精确度 | 默认 `setExactAndAllowWhileIdle`；**每次注册前**检查 `canScheduleExactAlarms()`；未授权才退回 `setAndAllowWhileIdle`；调用时被拒（`SecurityException`）就地降级并记日志 |
| 权限 | 清单声明 `SCHEDULE_EXACT_ALARM`（**只这一个**，未使用 `USE_EXACT_ALARM`）；设置页有「允许精确闹钟」入口直达系统页；监听 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，授权/撤销后**后台自动重排** |
| 迟到 | 迟到 ≤ `Reminder.MAX_LATE_MINUTES`（20 分钟）内仍然提醒，文案改为「已到上课时间（提醒延迟 N 分钟）」；超过才丢弃。20 分钟 < 一节课，保证不会提醒「已经错过的课」 |
| 生命周期 | 加 `sync()`：编辑页保存/删除、详情页删除、备份恢复成功、导入成功、学期切换/新建/删除、学期起始日期改动、设置页 `onResume`、精确闹钟权限变化、开机/改时间/改时区/应用更新 |
| 界面（上线形态） | 设置页回到原来的四点：开启勾选、一行状态（已关闭 / 通知权限未开启 / 已开启 · 提前 N 分钟 · 已安排 M 条提醒）、提前量色块、一句说明；**下面只留一个按钮**，按当前缺什么自动变成「去开启通知权限」或「允许精确闹钟」，两样都不缺时整行隐藏。不显示诊断数据，也没有测试按钮 |
| 诊断（日志） | 界面上不暴露诊断信息，改为日志：`ReminderScheduler` / `ReminderReceiver` / `ReminderNotification` / `ReminderRestore` 四个 tag 覆盖计划条数、每条闹钟的 slot/courseId/触发时刻/精确与否、取消的旧槽位、触发时的 slot/迟到分钟/是否发布、被丢弃的原因、重排失败原因。**只记 id 与时刻，不记课名/教室/教师** |

#### V-2.1 诊断界面被移除（上线要求，2026-10-18 同日）

排查阶段曾把「通知权限 / 通知渠道 / 精确闹钟 / 下一条提醒 / 上次重排失败」做成设置页里的三行状态，
并提供了「发送测试通知」「测试提醒（1 分钟后）」两个按钮与 `TEST_SLOT=999` 的测试闹钟链路。
这些是**排查工具**，用户看到真机截图后要求「把界面恢复到可上线状态」，因此**已全部删除**：

- 设置页只剩：勾选、一行状态、提前量色块、一句说明、**一个**自适应权限按钮；
- `ReminderScheduler.scheduleTest/cancelTest/testScheduledAt`、`ACTION_TEST_REMIND`、`postTest()`、
  `ReminderSettings.last_sync_*`/`test_at` 一并删除（没有调用方的代码不留）；
- **保留**精确闹钟入口（未授权时按钮变成「允许精确闹钟」）——这不是调试信息，而是让"提醒可能延迟"可被修复的入口；
- 排查手段改为日志（V-2 最后一行）。**不要为了"方便调试"再把测试按钮加回设置页**；需要时用
  `adb shell am broadcast` 或临时构建验证，而不是随包发布。

### V-3. 审计十问（结论）

1. **触发时间**＝`课程开始（按作息表解析到分钟）− 提前量`，见 `ReminderPlan.onDate`。
2. **是**：提前量、真实开始时间、学期周次、单双周都由 `ScheduleUtils`/`CourseQuery` 统一计算，未复制逻辑。
3. **修复前**非精确；**现在**精确优先、未授权时降级为 `setAndAllowWhileIdle` 并在界面明示。
4. **权限**：`POST_NOTIFICATIONS`（13+ 运行时）+ `SCHEDULE_EXACT_ALARM`（12+ 可用户授予）+ `RECEIVE_BOOT_COMPLETED`（安装即授予）。
5. **是**：`sync()` 捕获 `RuntimeException` 并记 `sync failed: <类型>: <原因>`；闹钟级失败记 slot 与原因。界面上不再显示失败原因（见 V-2.1），排查靠日志。
6. **是**：设备实测接收器在目标时刻 +39 ms 收到并发布通知（见 V-5）。
7. **是**：通知权限/应用总开关/渠道 importance 三处都会导致不显示；设置页现在分别显示；`canPost()` 为假时**不注册任何闹钟**。
8. **是**：重启、改时间/时区、应用更新、精确闹钟授权变化、课程增删改、导入、恢复、学期改动都会重排。
9. **否**：`PendingIntent` 用「槽位（小整数）+ 固定 action + 组件」标识，槽位到课程的映射存在 `scheduled` 里；通知 id = `hash(courseId, epochDay)`。同一门课的多条安排是不同 `courseId`，不会互相覆盖（已有测试锁定）。
10. **上限 10 条 / 前瞻 7 天**：会，但**不会漏**——每次触发、每次开屏、每次数据变更都会重排，10 条是「同时挂起的上限」，不是「每天的上限」。前提是接收器能跑（见 V-6）。

### V-4. 改动文件

| 文件 | 改动 |
|---|---|
| `AndroidManifest.xml` | +`SCHEDULE_EXACT_ALARM`；恢复接收器 +`SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` |
| `rule/Reminder.java` | +`MAX_LATE_MINUTES`、`isLate`、`isStillUsefulAt`、`lateMinutes`（纯函数，可单测） |
| `reminder/ReminderScheduler.java` | 精确/非精确决策与降级、`canScheduleExact()`、逐步日志 |
| `reminder/ReminderReceiver.java` | 处理 `ACTION_TEST_REMIND`；迟到宽限；每个丢弃分支都有日志 |
| `reminder/ReminderNotifications.java` | `post(..., deliveredAt)`、迟到文案、失败日志 |
| `reminder/ReminderRestoreReceiver.java` | 识别精确闹钟权限变化广播 |
| `reminder/ReminderSettings.java` | 未改存储键（`enabled`/`lead_minutes`/`scheduled` 之外没有新增键） |
| `SettingsActivity.java` | `onResume` 先重排；一行状态 + 单个自适应权限按钮（通知 / 精确闹钟）；提示文案更新 |
| `EditCourseActivity.java` / `CourseDetailActivity.java` | 保存/删除后 `sync()` |
| `BackupPreviewActivity.java` / `MainActivity.java` / `SettingsActivity.java`（导入） | 恢复/导入成功后 `sync()` |
| `SemesterListActivity.java` | 切换/新建/删除学期后 `sync()` |
| 测试 | 新增 `rule/ReminderTimingTest`（6 项）；`ReminderScheduleTest` +2（迟到文案）；`ReminderRestoreTest` 更新两处事件清单 |

### V-5. 实测（模拟器 `emulator-5554` / API 37 / 1080×2424 @420，**不是真机**）

| 场景 | 结果 |
|---|---|
| 未授权通知时开启提醒 | 状态行「通知权限未开启，提醒不会显示」；`scheduled=[]`；系统无任何本应用闹钟 |
| 授权通知后自动重排 | `sync: planned=10 registered=10`；设置页「已开启 · 提前 10 分钟 · 已安排 10 条提醒」 |
| 未授权精确闹钟 | 日志 `exact=false`；设置页出现「允许精确闹钟」入口 |
| 授予精确闹钟（`cmd appops set ... allow`） | **系统广播触发重排**：`SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED: 已重建 10 条提醒`；日志 `exact=true`；`dumpsys alarm` 显示 `window=0 exactAllowReason=permission` |
| **测试闹钟（验证用，已移除）** | 当时的构建带「测试提醒（1 分钟）」按钮：计划 07:34:06.067 → 接收器 **07:34:06.076**（+9 ms）；应用在后台、**屏幕关闭**；通知入栏；一次性。这个按钮是排查工具，**上线前按用户要求连同诊断行一起删除**（见 V-2.1），现在的构建没有它 |
| **真实课程提醒** | 插入周日第 1 节探针课（A/F 楼作息 08:15-08:55，提前 34 分钟）→ 计划 07:41:00.000 → 接收器 **07:41:00.039**（+39 ms）；通知标题「上课提醒 · ReminderProbe」、正文「10月18日 周日 08:15 · 第 1 节 · 教学楼F楼F101多 · Probe Teacher / 34 分钟后开始」 |
| 改课（周次 1-18 → 1-4，今天第 5 周） | 保存后立即重排，槽位 1000 重新指向别课，探针从 `scheduled` 消失；`dumpsys alarm` 中旧时刻已不存在 |
| 重启 | `BOOT_COMPLETED: 已重建 10 条提醒`；重启后系统里 10 条精确闹钟与重启前一致 |
| 撤销通知权限 | `sync: notifications blocked`；闹钟全部取消；界面显示「通知权限未开启，提醒不会显示」+ 入口 |
| 恢复通知权限 | 重启应用后自动回到 10 条精确闹钟 |
| 数据还原 | 测试用的探针课程与提前量已清除，`weihe_schedule.xml` 与测试前**逐字节相同**（MD5 `5812d6eb…`）；通知权限恢复为拒绝、精确闹钟 appop 恢复 default |

### V-6. 未验证 / 系统限制（必须告知用户）

1. **没有真机验证**：全部结论来自模拟器 API 37。厂商 ROM（小米/华为/OPPO/vivo）的省电策略会额外限制后台与闹钟，**代码无法绕过**，只能在系统设置里给应用「自启动 / 无限制电池」。
2. **被用户「强行停止」后不会提醒**：Android 会取消该应用的闹钟与广播，直到用户再次打开应用。这是系统行为，不是缺陷。
3. **锁屏上的显示形态未验证**（屏幕关闭状态下通知确实已发布，但没看锁屏界面）。
4. **非精确路径的送达时间未实测**：只验证了「未授权时确实以 inexact 注册」。
5. **`SecurityException` 就地降级分支未复现**（代码审阅通过；权限在上架/撤销瞬间失效才会走到）。
6. 新设置卡片在**大字号/极小屏**下的排版未验证。
7. 同一门课**同一天两段**（两条记录）各自提醒——由独立 `courseId` 与独立通知 id 保证，有单元测试，但未做设备验证。

### V-7. 下一步建议

| 优先级 | 任务 |
|---|---|
| P0 | **在真机上复测「真实课程提醒」**（挑一条十几分钟后的课，或临时把提前量调大）；厂商 ROM 需先给应用「无限制电池」 |
| P1 | 锁屏形态、非精确路径实际延迟、`SecurityException` 分支 |
| P1 | 若用户反馈仍不响：`adb logcat -s ReminderScheduler ReminderReceiver ReminderNotification ReminderRestore`（界面已不再显示诊断，日志是唯一途径） |
| P2 | 通知点击后返回栈（当前 `FLAG_ACTIVITY_NEW_TASK`）在真机上的表现 |

### V-8. 工作区状态（本轮结束）

改动：`AndroidManifest.xml`、`rule/Reminder.java`、`reminder/` 下 4 个类（`ReminderSettings` 未新增存储键）、
`SettingsActivity.java`、`EditCourseActivity.java`、`CourseDetailActivity.java`（仅此前的详情页 UI + 本轮 2 行 sync）、
`BackupPreviewActivity.java`、`SemesterListActivity.java`、`MainActivity.java`、
2 个既有测试 + 1 个新测试类（`rule/ReminderTimingTest`）、本文件、归档。
**Agent 未 commit / 未 push。** 未跟踪：`app/release/`（构建产物）。

门禁（删除诊断界面后重跑）：`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL；
**1099 项 / 0 失败**；APK **1,175,784 B**；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）。
设备端复查：设置页卡片恢复为「勾选 + 一行状态 + 色块 + 一句说明 + 单个权限按钮」；
`exact` 未授权时该按钮为「允许精确闹钟」，授权后整行隐藏；提醒条数照常 10 条。

---

## W. 2026-10-18 桌面小组件（今日课程）

> 本轮新增一个桌面小组件。**不改存储格式、不加依赖、不加 Kotlin/Compose**。
> `res/layout/` 与 `res/xml/` 在本项目第一次出现，**仅限小组件**：
> `RemoteViews` 只能从 XML 膨胀，Java 里拼不出来（`AGENTS.md` §1 已补例外说明）。

### W-1. 能力与状态

| 能力 | 状态 | 依据 |
|---|---|---|
| 紧凑尺寸：正在上 / 下一节课 + 时间 + 地点 + 倒计时 | **已实现并已验证** | `widget/CourseWidgetRenderer`、`res/layout/widget_course_compact.xml`；设备截图 `widget-09/10/23` |
| 大尺寸：额外显示今天接下来的 1–3 门课 | **部分实现**：行渲染已验证，尺寸切换未在设备上走通 | `rowBudget` + `RemoteViews.addView`；见 W-6 |
| 五种状态：上课中 / 即将开始 / 今天没课 / 没有课表 / 学期外 | **已实现**；设备上验到 4 种（上课中、即将开始、今天没课；"没有课表"只在选择器预览里出现过） | `rule/TodayPlan.State` |
| 点击打开应用；点某节课进课程详情 | 已实现，**未逐一点击验证** | `openApp` / `openCourse`，请求码折叠有单测 |
| 保存 / 删除 / 导入 / 恢复备份 / 切学期 / 改作息表后刷新 | **已实现并已验证一例**（编辑课程 → 立即变） | 10 个调用点见 W-4 |
| 跨零点刷新 | **已实现并已验证** | 闹钟对准次日 00:05；`dumpsys alarm` |
| 重启后刷新 | **已实现并已验证** | `BOOT_COMPLETED` 后 `refreshed=1/1`，闹钟重挂 09:55 |
| 时间 / 时区变化后刷新 | 已实现，**未单独触发验证** | 走 `ReminderRestoreReceiver` 同一入口 |
| 可被桌面发现 / 添加 / 移除 | **已实现并已验证** | 选择器列出「拾课/今日课程 3×2」、`dumpsys appwidget`、移除后 `refresh alarm cancelled` |
| 倒计时自行走动（不靠定时刷新） | **已实现并已验证** | 同一张桌面上 `1:02:40` → `54:13` |
| 数据变更不会读到提醒账本 | 已实现 | `CourseWidgetDataSource` 只经 `CourseRepository` |

### W-2. 关键决定（以及一个真机 bug）

1. **规则放 `rule/TodayPlan`（纯 JVM），Android 胶水放 `widget/`。**
   周次、单双周、作息表解析、排序**全部调用**既有 `ScheduleUtils` / `BellSchedules` /
   `CourseQuery`，没有第二份实现——这正是这个项目最容易腐烂的地方。
2. **小组件不读提醒账本。** 账本最多 10 条，把它当"今天的课表"会漏课；
   小组件每次刷新都从 `CourseRepository` 重新读。
3. **一个边界闹钟，每次刷新后重瞄。** `TodayPlan.nextRefreshAt()` = min(本课结束, 下一课开始,
   次日 00:05)；精确/非精确实沿用 `ReminderScheduler.canScheduleExact`，不复制权限判断。
   `updatePeriodMillis` 用平台下限 1800000（30 分钟）——它是兜底，不是机制：
   半小时就是两节课，靠它纠正倒计时等于一直错。
4. **`Chronometer` 的 base 只能用 `SystemClock.elapsedRealtime()`。**
   第一版把 `LocalDateTime` 的 epoch 毫秒直接传给 `RemoteViews.setChronometer`，
   真机上倒计时显示 **`497864:44:48`**（≈57 年）——`Chronometer` 比较的是
   "开机以来的毫秒"，不是墙上时钟。改为 `elapsedRealtime() + 剩余时长`，并在方法注释里写清原因。
5. **倒计时目标已过去时隐藏而不是显示 00:00。** 小组件恰好在归零那一刻被重画，
   若目标已在身后，说明有东西不对；此时一个死的 `00:00` 会是界面上唯一确定错误的部分。
6. **小组件不依赖通知权限。** 提醒被拒时它照常工作。

### W-3. 刷新调用点（共 10 处）

| 位置 | 事件 |
|---|---|
| `EditCourseActivity`（保存 / 删除） | 课程写入 |
| `CourseDetailActivity`（删除） | 课程删除 |
| `BackupPreviewActivity`（RESTORED） | 备份恢复 |
| `SettingsActivity`（`runImport` / `applySemesterStart` / `onResume`） | 导入、开学日、回到应用 |
| `SemesterListActivity`（切换 / 新建 / 删除） | 学期变更 |
| `MainActivity`（`onResume` / `runImport`） | 回到应用、导入 |
| `EditScheduleActivity`（保存 / 删除） | 作息表变更 |
| `ScheduleListActivity`（设为默认 / 删除） | 作息表变更 |
| `ReminderRestoreReceiver`（开机 / 改时间 / 改时区 / 重装 / 精确闹钟授权） | 系统事件 |

> **顺带修掉的既有缺口**：`EditScheduleActivity`（保存、删除）与
> `ScheduleListActivity`（设为默认、删除）此前**只写数据、不重排提醒**，
> 于是改了下课时间后，当天的提醒仍按旧时刻发出。本轮在这 4 处一并补了
> `ReminderScheduler.sync(...)`。

### W-4. 文件清单

**新增（Java）**

- `rule/TodayPlan.java` —— 今天怎么看（状态、当前、下一节、后续、下次该刷新的时刻），纯 JVM
- `widget/CourseWidgetProvider.java` —— `AppWidgetProvider`；`onUpdate` / `onAppWidgetOptionsChanged` / `onEnabled` / `onDisabled` / `onDeleted` + 自定义 `WIDGET_TICK`
- `widget/CourseWidgetDataSource.java` —— 只读一次 `CourseRepository`，交给 `TodayPlan`
- `widget/CourseWidgetRenderer.java` —— 两个布局、表头、行、文案、点击、尺寸判断
- `widget/CourseWidgetUpdater.java` —— `refreshAll(Context)`：取 id → 渲染 → 重瞄闹钟
- `widget/CourseWidgetRefreshScheduler.java` —— 唯一的重画闹钟（`REQUEST_CODE = 7301`）

**新增（资源，项目首次）**

- `res/layout/widget_course_header.xml`（两个尺寸共用，`<include>`）
- `res/layout/widget_course_compact.xml`、`res/layout/widget_course_wide.xml`、`res/layout/widget_course_row.xml`
- `res/xml/course_widget_info.xml`（minWidth 180dp / minHeight 110dp、可缩放、30 分钟兜底、`tools:targetApi="s"`）
- `res/drawable/widget_bg.xml`、`res/drawable/widget_headline_bg.xml`、`res/values/widget.xml`

**修改**

- `AndroidManifest.xml` —— `<receiver .widget.CourseWidgetProvider>`（`exported="false"`，两个 action + `meta-data`）
- 上表 10 个调用点的 Activity / Receiver

**测试**

- `rule/TodayPlanTest`（30 项）：五个状态、单双周、周次窗口、两套作息表、跨楼宇、重叠排序、
  `maxFollowing` 与「还有 N 门课」、节次越界丢弃、`timeRange`/`periodLabel`、`nextRefreshAt` 三种
- `widget/CourseWidgetRendererTest`（10 项）：日期/周次/单双周、五种状态文案、地点与教师拼接、
  倒计时标题、行文案、请求码折叠

### W-5. 设备实测（`emulator-5554`，API 37）

| 场景 | 结果 |
|---|---|
| 选择器发现 | 列表出现「拾课 / 1 widget」，展开显示「今日课程 3×2」+ 说明，预览能膨胀出真实布局 |
| `dumpsys appwidget` | `min=(46081x28161)`(=180×110dp)、`minResize=(28161x20481)`(=110×80dp)、`updatePeriodMillis=1800000`、`resizeMode=3`、`widgetCategory=1` |
| 今天没课 | 「10月18日 周日 · 第5周 单周」+「今天没有课」+「点开查看整周课表」，无倒计时 |
| 正在上课 | 课名 +「08:15 - 09:35 · 第 1-2 节」+「教学楼F楼F101多 · Prof. A」+「距下课 54:13」 |
| 倒计时自走 | 同屏 30 秒内 `1:02:40` → `1:02:12`（应用未运行） |
| 边界闹钟 | `RTC_WAKEUP ... origWhen=2026-10-18 09:35:00 window=0 exactAllowReason=permission`（上课中→对准下课）；改数据后变 `09:55`（对准下一节）；今天没课时变次日 `00:05` |
| 编辑课程后立即刷新 | 保存后同一秒 `refreshed=1/1 state=UPCOMING following=1`，画面从 A 变 B，倒计时改为「距上课」 |
| 重启 | `BOOT_COMPLETED` 后 `refreshed=1/1`，闹钟重挂 09:55 |
| 移除 | `refresh alarm cancelled`，`dumpsys appwidget` 不再有本应用实例 |

### W-6. 未验证 / 已知限制

1. **宽版多行没能在发布口径下验证。** 3×2 默认尺寸高约 152dp，`rowBudget` 算出 0 行
   （152 < 120 表头 + 52 行），所以默认只显示表头 + 「还有 N 门课」。
   要看到行必须把小组件拉高到 ≥180dp；Pixel Launcher 的「添加 / 缩放」手势无法用
   `adb shell input` 稳定复现（试过 `swipe`、`draganddrop`、`motionevent` 分段拖拽，
   只有横屏下偶然点到过 `+ Add`）。最终用**临时放宽行预算的构建**拍到了
   一行真实渲染（`widget-21`：粉色色条 + `09:55-11:20` + `WidgetProbeB` + `… · 第 3-4 节`），
   随后恢复常量并重装。**结论：行渲染（`addView` 嵌套 RemoteViews）在真机可用，
   但"用户把小组件拉高后是否三行都不裁切"仍未经人眼确认。**
2. 点击打开应用 / 点击某节课进详情：代码就位，未逐一点击验证。
3. `TIME_SET` / `TIMEZONE_CHANGED` 路径与提醒共用入口，本轮未单独改表再触发。
4. 「没有课表」「学期已结束」两种状态只在选择器预览里出现过（预览用的就是空状态）。
5. 深色模式、折叠屏、平板：小组件背景写死白色（`res/values/widget.xml`），未做 `values-night`。

### W-7. 下一步建议

| 优先级 | 任务 |
|---|---|
| P0 | 手动把小组件拉高到 3 行，确认宽版三行不裁切；顺带确认点击能开出对应课程 |
| P1 | `res/values-night/widget.xml`：给表面/文字一套深色值 |
| P1 | 若用户反馈"下课了没变"：`adb logcat -s CourseWidget`（每次刷新都有 `refreshed=x/y state=… following=…`） |
| P2 | 15 分钟兜底：把 `updatePeriodMillis` 保持 30 分钟不变，若发现某些 ROM 不派发 `onUpdate`，再加一个 `AlarmManager` 兜底 |

### W-8. 工作区状态（本轮结束）

改动：`AndroidManifest.xml`、10 个数据写入点（9 个 Activity + `ReminderRestoreReceiver`）、
新增 `rule/TodayPlan.java`、`widget/` 5 个类、6 个资源文件、2 个测试类、本文件、归档、`AGENTS.md` 例外说明。
**Agent 未 commit / 未 push。** 未跟踪：`app/release/`（构建产物，此前既存）。

门禁（`--rerun-tasks testDebugUnitTest assembleDebug lintDebug`）：BUILD SUCCESSFUL；
**1135 项 / 0 失败 / 0 错误**（61 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,195,415 B**。

设备已复原：`weihe_schedule.xml` 与测试前逐字节一致（MD5 `5812d6eb6fee054ebde7d49beee77007`），
小组件已移除，`accelerometer_rotation` 已恢复，`/sdcard` 上的临时 dump 已删除。

---

## X. 2026-10-18 小组件第二轮：倒计时靠右 + 上下翻页

用户在看过第一版后提了两点，随后又追加一条：倒计时放右侧、当天多节课时可翻动、**翻动不好做就改成醒目的上下按钮**。
本轮把这三条都落到了代码里，并替换掉第一版「表头 + 行」的紧凑布局。

### X-1. 现在的小组件长什么样

**紧凑尺寸（默认 3×2）**

```
10月18日 周日 · 第5周 单周
┌──────────────────────────────┐  ┌──┐
│▎dd              距下课 19:20 │  │▲ │
│ 08:15 - 09:35 · 第 1-2 节    │  │1/2│
│ 教学楼F楼F202多 · 李海波      │  │▼ │
└──────────────────────────────┘  └──┘
```

- **倒计时在标题行右侧**（`距下课 19:20` / `距上课 1:27:16`），用 `Chronometer` 自走。
- **右侧两枚实心圆按钮**（`Palette.PRIMARY` 深绿 + 白色 Material 箭头）上下排列，中间夹页码 `1 / 2`；
  只有一节课时整列隐藏，不出现按不动的按钮。
- 页面内容：当天从「现在该上/正在上的那节」开始，按开始时间排序，最多 15 页（一节课一页）。

**宽尺寸**：日期 + 同一张卡片 + 最多 3 行后续课程（不翻页，能同时显示就没必要翻）。

### X-2. 为什么最后是按钮而不是滑动

先按最正统的做法实现过一版：紧凑布局放 `AdapterViewFlipper`，页面由 `RemoteViewsService` 提供。
真机上：**页面能渲染**（卡片、倒计时、页码都正常），但

- 左右滑动**不翻页**（各种速度/距离的 `input swipe` 都试过）；
- 连**卡片点击也不生效**——点下去启动的是 `act=MAIN cat=LAUNCHER cmp=.MainActivity`，
  即桌面「点小组件打开应用」的兜底行为，说明触摸根本没进到集合项里；
  `setOnClickFillInIntent` + `setPendingIntentTemplate` 和直连 `setOnClickPendingIntent` 都一样。

结论：这个桌面（Pixel Launcher / API 37）不给集合视图手势。于是改成**应用侧页码 + 按钮广播**：
`CourseWidgetPageStore` 存 `page:<id>` 与 `day:<id>`，`CourseWidgetProvider` 收到 `WIDGET_PAGE_PREV/NEXT`
后改页码并重画。`CourseWidgetService` 与 `widget_course_empty.xml` 已删除，不留走不通的路。

### X-3. 设备实测（`emulator-5554`，用户自己的课表，未改动数据）

| 场景 | 结果 |
|---|---|
| 第 1 页 | `dd` / `08:15 - 09:35 · 第 1-2 节` / `教学楼F楼F202多 · 李海波` / 右侧 `距下课 19:20` / `1 / 2` |
| 点 ▼ | 日志 `page turn widget=7 pages=2 intent=WIDGET_PAGE_NEXT`，画面变 `Aa` / `10:40 - 14:00 · 第 4-6 节` / `教学楼F楼F102多 · 凌铭` / `距上课 1:27:16` / `2 / 2` |
| 点 ▲ | `WIDGET_PAGE_PREV`，回到第 1 页；两个方向都循环 |
| 点卡片 | `START … cmp=.CourseDetailActivity (has extras)`，打开该节课详情 |
| 倒计时 | 应用未运行时自行走动（19:20 → 17:xx…） |
| 重装 APK | 页码保留（`2 / 2` 在升级后仍在第 2 页） |
| 只有一节课 | 翻页列整列隐藏 |

### X-4. 布局调整（顺带修掉的问题）

第一版倒计时占右侧一列，加上翻页列后文字被压窄，`第 4-6 节`、`教学楼…·凌铭` 都被截断。
现在倒计时挪到**标题行右侧**（只压缩课名，课名本来就短），时间与地点两行恢复整行宽度；
紧凑布局的正文区域垂直居中，`minResizeWidth/minResizeHeight` 提到 180/130dp，
避免用户把组件压到放不下按钮的高度。

### X-5. 文件变化

**新增**：`widget/CourseWidgetPageStore.java`、`res/layout/widget_course_card.xml`、`res/layout/widget_course_date.xml`、
`res/drawable/widget_nav_button.xml`、`res/drawable/widget_ic_up.xml`、`res/drawable/widget_ic_down.xml`

**改写**：`widget_course_compact.xml`（卡片 + 右侧上下按钮列）、`widget_course_wide.xml`、
`CourseWidgetRenderer`（`bindPages` / `bindEmptyCard` / `pageAction` / `countdownTargetOf` / `countdownLabelOf`）、
`CourseWidgetProvider`（翻页动作、删除时清页码）、`CourseWidgetUpdater`（`MAX_PAGES` 读取）

**删除**：`widget_course_header.xml`、`widget_course_empty.xml`、`widget/CourseWidgetService.java`、清单里的 `<service>`

**清单**：receiver 增加 `WIDGET_PAGE_PREV` / `WIDGET_PAGE_NEXT`（由本应用自己的 PendingIntent 触发）

**测试**：`CourseWidgetRendererTest` 共 15 项（新增：页码顺序、空日无页、正在上的课是第 1 页、每页各自的倒计时、已结束的课无倒计时）

### X-6. 未验证 / 限制

1. 跨零点把页码归零：逻辑在 `CourseWidgetPageStore.page`，未等到零点实测。
2. 宽尺寸的多行渲染沿用第一轮结论（`widget-21`）；本轮未再单独截图。
3. 深色模式仍未做：`res/values/widget.xml` 是浅色一套。
4. 没有滑动手势（见 X-2）：只能点按钮，这是当前桌面的限制下的取舍。

### X-7. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1140 项 / 0 失败 / 0 错误**（61 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,200,804 B**。

设备上保留一个已放置的小组件（id=7，停在第 1 页），用户自己的课表数据未被改动。

---

## Y. 2026-10-18 小组件第三轮：两节课卡 + 今日课表入口

用户给出这一版的小组件规则（原文 7 条）：2×3 组件最多两张课卡；按当前时间动态选；不足两节就不占位；
底部始终有「查看今日全部 N 节课程」入口且 N 按当天实际课程算；点入口进今日课表；开始/结束/跨日/改数据后重算；
无论屏幕尺寸、字体放大还是课名多长，都不得出现第三张卡。

### Y-1. 规则与实现对照

| 要求 | 实现 | 依据 |
|---|---|---|
| 最多两张卡 | `CourseWidgetRenderer.MAX_CARDS = 2`；卡片是 `addView` 加进容器的嵌套 RemoteViews，加几张由代码决定 | `cards(plan)` |
| 上课中 = 当前 + 下一节 | `cards()` 取 `plan.headline()`（正在上=当前，否则=下一节）+ `following` 的第一个 | `whileAClassRunsItKeepsItsPlaceBesideTheNextOne` |
| 没在上课 = 接下来最早两节 | 同上一条规则自然成立（headline 退化为「下一节」） | `beforeTheFirstClassTheTwoSoonestAreShown` |
| 不足两节只显示存在的 | `cards()` 只按实际课程数量返回，不补空卡 | `aDayWithOneClassLeftShowsOneCardAndNoPlaceholder` |
| 底部入口 + 真实数量 | `TodayPlan.todayCount()` = 当天全部课程数（含已结束）；`entryText(n)` 生成「查看今日全部 N 节课程」，0 节时「打开今日课表」 | `theEntryCountsTheWholeDayNotTheCardsOnScreen` |
| 点入口进今日课表 | 入口 `PendingIntent` → `MainActivity` | 设备验证：点入口打开 `MainActivity` |
| 不让人误以为是全部 | 入口的数字来自当天总课数，而不是卡片数 | 同上；截图里「dd + Aa」两张卡 +「查看今日全部 2 节课程」 |
| 开始/结束/跨日/改数据后重算 | 沿用边界闹钟（下课/下一节开始/次日 00:05）+ 10 处数据写入点 + 开机等系统事件 | `CourseWidgetRefreshScheduler`、`CourseWidgetUpdater` |
| 单双周/作息表/地点正确 | 未改动 `TodayPlan` 的取数路径（`CourseQuery` + `BellSchedules.resolve`） | `TodayPlanTest` 全部保留 |
| 不得出现第三张卡 | 布局里没有第三张卡的位置，也没有按尺寸分支的宽版布局 | 字号 1.3 倍实测仍为两张 |

### Y-2. 删掉的东西（这一版是减法）

- **翻页**：`CourseWidgetPageStore`、`widget_page_up/down`、`widget_ic_up/down`、`widget_nav_button`、
  Provider 里的 `WIDGET_PAGE_PREV/NEXT` 全部删除。两节课卡一目了然，翻页不再需要。
- **宽版布局**：`widget_course_wide.xml`、`widget_course_row.xml`、`isWide`/`rowBudget`/`MAX_FOLLOWING` 删除。
  「任何尺寸都最多两张卡」意味着不存在「更高的尺寸显示更多卡」这条分支。
- **紧凑版旧布局**：`widget_course_compact.xml`、`widget_course_date.xml` 合并为单一的 `widget_course.xml`。

### Y-3. 真机上撞到的一个坑

第一版布局用 `<View layout_height="0dp" layout_weight="1"/>` 当弹簧把入口压到底部，结果桌面直接显示
**「Can't load widget」**：

```
InflateException: Binary XML file line #72 ... Class not allowed to be inflated android.view.View
```

`RemoteViews` 只允许白名单里的类（`LinearLayout`、`TextView`、`Chronometer`、`ImageView` 等），
裸 `View` 不在其中。改法：把「日期 + 卡片」包进一个 `layout_weight="1"` 的 `LinearLayout` 里，
入口自然落到最底部，不用弹簧。

### Y-4. 设备实测（`emulator-5554`，用户真实课表）

| 场景 | 结果 |
|---|---|
| 上课中（09:25，`dd` 08:15-09:35） | 卡 1 `dd` + `距下课 09:12` + `08:15 - 09:35 · 教学楼F楼F202多 · 李海波`；卡 2 `Aa` + `距上课 1:14:12` + `10:40 - 14:00 · 教学楼F楼F102多 · 凌铭`；底部 `查看今日全部 2 节课程` |
| 点入口 | 打开 `MainActivity`（今日课表页） |
| 点第二张卡 | `START … .CourseDetailActivity (has extras)`，进该节课详情 |
| 字体 1.3 倍 | 仍是两张卡，没有第三张；第二张卡的地点行被裁掉（见 Y-6） |
| 日志 | `refreshed=1/1 state=IN_CLASS cards=2 today=2` |

### Y-5. 文件变化

**新增**：`res/layout/widget_course.xml`（唯一的小组件布局）

**改写**：`res/layout/widget_course_card.xml`（两行：课名+倒计时 / 时间·地点·教师）、
`CourseWidgetRenderer`（`render(context, plan)`、`cards`、`totalOf`、`entryText`、`detailOf`、`emptyDetail`、
`stateTitle`）、`CourseWidgetUpdater`（只读两节、日志改为 `cards=`/`today=`）、`CourseWidgetProvider`（去掉翻页）、
`res/xml/course_widget_info.xml`（minHeight / minResize 180dp，单一布局）、`res/values/widget.xml`（清掉不再用的尺寸与颜色）

**删除**：`widget_course_compact.xml`、`widget_course_wide.xml`、`widget_course_row.xml`、`widget_course_date.xml`、
`widget_nav_button.xml`、`widget_ic_up.xml`、`widget_ic_down.xml`、`widget/CourseWidgetPageStore.java`

**测试**：`CourseWidgetRendererTest` 重写为 17 项（选卡 6 项、入口 3 项、卡片文案 3 项、日期/空态 3 项、倒计时 2 项）；
`TodayPlanTest` 增加 `todayCount` 两项

### Y-6. 未验证 / 限制

1. **字体 ≥1.3 倍时第二张卡的地点行会被裁掉**：卡片数量仍是 2（要求 7 满足），但第二张卡的第二行不可见。
   要彻底解决需要按 `fontScale` 动态减少内容，本轮没做。
2. 当天只有 2 节课，因此「没在上课时显示接下来最早两节」只有单测覆盖（`beforeTheFirstClassTheTwoSoonestAreShown`、
   `aLaterHourShowsTheRemainingClassesNotTheFirstOnes`），设备上没有对应的 3 节课场景可拍。
3. 加高组件不会再显示更多课（这是要求 7 的直接结果，不是缺陷），
   但用户若期待「变大看更多」，会看到空白变多。
4. 深色模式仍未做。
5. 「课程开始/结束」的重算依赖既有边界闹钟，本轮未重新测一次整点跳变（上一轮验过）。

### Y-7. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1144 项 / 0 失败 / 0 错误**（61 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,193,756 B**。

设备：小组件仍是 id=7，用户数据未改动，`font_scale` 已从 1.3 恢复到 1.0。

---

## Z. 2026-10-18 新增 1×2 小组件（极简下一节课提醒卡）

用户要求为「拾课」新增 **1×2** 尺寸的桌面组件：极窄竖向，只回答一件事——下一节课是什么、几点、在哪、还有多久。
不能直接缩放现有 3×2 布局；不能影响现有尺寸。

### Z-1. 做法：第二个 Provider，而不是在同一 Provider 里加分支

一个 `AppWidgetProvider` 的尺寸范围是整体声明的，无法同时给出 3×2 与 1×2 两种布局。
因此新增 **`NextClassWidgetProvider`**（选择器里第二个入口「下一节」），拥有自己的
`res/layout/widget_next_class.xml` 与 `res/xml/next_class_widget_info.xml`。

**共享的只有数据与刷新，不共享布局**：

- 一次 `CourseWidgetDataSource.read(...)` 同时喂给两个尺寸（同一时刻、同一份数据，不会出现两个组件对"下一节"判断不一致）。
- 同一个边界闹钟（下课 / 下一节开始 / 次日 00:05）与同一批数据写入点重画两种尺寸。
- `CourseWidgetUpdater.refreshAll` 现在同时遍历两个 Provider 的 id；**任一尺寸还在桌面时都不会取消闹钟**
  （3×2 的 `onDisabled` 原来会无条件取消，本轮一并改成走 `refreshAll`）。

### Z-2. 两个真机才暴露的问题

1. **名义 1×2 被放成 2×2。** 第一次把 `minWidth` 写成 110dp（那是平台公式里「2 格」的值：`70n-30`），
   桌面直接按 2 格宽放置，选择器也显示 `2 × 2`。改成 **`minWidth=40dp`（1 格）** 后选择器显示 `1 × 2`。
2. **`getAppWidgetOptions` 给的是区间，不是当前高度。** 1×2 实测 `minH=99dp`、`maxH=203dp`，
   而真机上的实际高度约 152dp；按 `min` 取会把教室和倒计时全部隐藏。改为取**较大值**，
   并在日志里打出 `minH/maxH` 便于下次核对。

### Z-3. 布局与信息优先级

自上而下：状态标签（下一节 / 正在上课 · 下节 10:10）→ 课名（最多两行，省略号）→ 开始时间（22sp，唯一的大数字）
→ 教室（单行）→ 精简倒计时（`Chronometer` 自走）。左侧 3dp 课程色条，白底卡片，无大面积高饱和色。

**空间不足时按优先级丢弃**：教室（<120dp）→ 倒计时（<130dp）；课名与开始时间始终保留。
状态标签里的「· 下节 HH:mm」是同一行内的追加文本，不额外占高度，因此不参与阈值。

### Z-4. 设备实测（`emulator-5554`，真实课表）

| 场景 | 结果 |
|---|---|
| 选择器入口 | 「拾课 / 2 widgets」→ 展开后两个条目：`今日课程 3×2` 与 **`下一节 1×2`**（描述「极简下一节课提醒…」） |
| 1×2 预览 | 窄卡：`下一节` + `还没有课表`，文字换行正常、无裁切（initialLayout 宽度即 1 格） |
| 实际放置实例（当时的元数据是 2 格宽） | `下一节` / `Aa` / **`10:40`** / `教学楼F楼F102多` / `距上课 1:02:15`，左侧色条为课程色 |
| 点击组件 | `START … cmp=.MainActivity`，打开 App |
| 移除 | 移除 1×2 后实例消失，**闹钟仍在**（因为 3×2 还在），10:40 仍精确对准 |
| 与 3×2 共存 | 同一屏两个组件同时正确刷新（日志 `refreshed=2/2 … nextClass=1`）；移除 1×2 后 3×2 不受影响 |
| 边界刷新 | 09:35:00 边界闹钟触发，两个组件同时从「正在上课」变为「下一节」（日志 `state=UPCOMING`） |

### Z-5. 未验证 / 已知限制

1. **没有在真机上拍到「1 格宽」的活动实例。** 元数据修正后选择器已显示 `1 × 2`，但该条目的「+ Add」
   落在桌面选择器底部浮动标签栏之下，`adb` 的点击/拖拽都够不到（本轮在这上面耗了大量时间，
   尝试过 `wm size` 放大、列表滚动、`draganddrop`、`motionevent`）。**需要人工在桌面选择器里添加一次**。
2. 「正在上课」「今日课程已结束」「今日无课」「学期外」四种文案只有单测覆盖，设备上只在 09:35 前后
   观察到「正在上课 → 下一节」的切换（那是 3×2 的刷新日志，两者共用同一 `TodayPlan`）。
3. 调整尺寸：本轮的缩放手势同样受标签栏影响未能执行；布局按高度区间自适应，逻辑有单测，但没有真机缩放截图。
4. 横屏时 `getAppWidgetOptions` 的区间语义不可靠（取较大值可能让倒计时略微超出），未在横屏下验证。
5. 深色模式仍未做。

### Z-6. 文件变化

**新增**

- `widget/NextClassWidgetProvider.java` —— 1×2 的 Provider（生命周期全部转调 `CourseWidgetUpdater`）
- `widget/NextClassWidgetRenderer.java` —— 1×2 的绘制与文案（`statusLabel` / `clockOf` / `roomOf` /
  `countdownLabelOf` / `emptyTitle` / `emptyHint`，以及三个高度阈值）
- `res/layout/widget_next_class.xml` —— 1×2 布局
- `res/xml/next_class_widget_info.xml` —— 1×2 元数据（minWidth 40dp / minHeight 110dp / targetCell 1×2）
- `app/src/test/java/com/weihe/schedule/widget/NextClassWidgetRendererTest.java` —— 9 项

**修改**

- `AndroidManifest.xml` —— 第二个 `<receiver>`（`exported="false"`，label「下一节」）
- `CourseWidgetUpdater` —— 同时刷新两个 Provider、按尺寸传高度、日志加 `cards=`/`today=`/`nextClass=`
- `CourseWidgetProvider` —— `onDisabled` 改为 `refreshAll`（不再无条件取消闹钟）
- `res/values/widget.xml` —— 1×2 的字符串与尺寸（`widget_next_*`）

**未改动**：`CourseWidgetRenderer`（3×2）、`widget_course.xml`、`course_widget_info.xml`、`TodayPlan`。

### Z-7. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1153 项 / 0 失败 / 0 错误**（62 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,198,798 B**。

设备已复原：`wm size` 回到 1080×2424，桌面只剩用户原有的 3×2 组件（id=7），课表数据未改动。

---

## AA. 2026-10-18 组件尺寸修正：1×2 → 2×1（横向）

上一轮把「下一节」组件做成了竖向 1×2（1 格宽 × 2 格高），实际需求是**横向 2×1**。本轮只改这个组件，
3×2 原样保留。

### AA-1. 改了什么

| 项 | 之前（1×2 竖向） | 现在（2×1 横向） |
|---|---|---|
| `next_class_widget_info.xml` | `minWidth 40dp` / `minHeight 110dp` / `targetCell 1×2` | **`minWidth 110dp` / `minHeight 40dp` / `targetCell 2×1`**，`minResize 110×40` |
| `widget_next_class.xml` | 单列竖排：状态 → 课名(≤2 行) → 时间(22sp) → 教室 → 倒计时 | **横向三行**：① 状态 + 倒计时（同一行，左右分置）② 课名 + 开始时间（同一行，课名占位省略、时间右对齐 22sp）③ 教室（整行） |
| `NextClassWidgetRenderer` | 教室 120dp、倒计时 130dp 两个高度阈值 | **只有教室有高度阈值 88dp**：倒计时与状态共行，不占额外高度；删掉 `COUNTDOWN_MIN_HEIGHT_DP` |
| `values/widget.xml` | 课名 15sp | 新增 `widget_next_text_name = 16sp`（横版一行更宽，可稍大） |
| `NextClassWidgetRendererTest` | 断言两个阈值 | 断言「只有教室占高度」且 88dp ≤ 单格实测高度（99dp） |

**尺寸计算**沿用平台公式 `70n − 30`：2 格宽 = 110dp，1 格高 = 40dp。上一轮把 1 格宽写成 40dp 时桌面仍按
2 格放置（`targetCellWidth=1` 未生效于该启动器），所以这次直接按 2×1 声明，选择器现在显示 **`2 × 1`**。

### AA-2. 视觉与信息优先级

- 课程名称最醒目（16sp 粗体，单行省略）；开始时间 22sp 粗体，靠右，是唯一的大数字。
- 状态（下一节 / 正在上课 · 下节 HH:mm / 今日课程已结束 / 今日无课 / 还没有课表 / 学期已结束）在左上，11sp。
- 教室单行，宽度不够时省略；高度不够（<88dp）时**整行隐藏**而不是压缩。
- 左侧 3dp 课程色条，白底卡片、16dp 圆角、10dp 内边距，与 3×2 同一套 `widget.xml` 视觉语言。
- 不显示教师、备注、完整日期。

### AA-3. 设备实测（`emulator-5554`，96dp 高）

| 场景 | 结果 |
|---|---|
| 选择器 | 「拾课 / 2 widgets」下两个条目，第二个显示 **`下一节 2 × 1`**，预览为横版窄条 |
| 放置方式 | 每条的 `+ Add` 仍落在选择器底部浮动标签栏之下，`adb` 够不到；改用 **`input draganddrop`** 把预览拖到桌面，一次成功（日志 `nextClass=1`） |
| 元数据读取 | `options widget=11 minH=43 maxH=96` → `next-class widget=11 height=96dp` |
| 实际渲染 | `下一节`（左上） · `距上课 44:01`（右上） · `Aa`（16sp）+ `10:40`（22sp，右） · `教学楼F楼F102多`（整行）；无裁切、无重叠、无大面积空白 |
| 点击 | `START … cmp=.MainActivity`（来自 uid 10237，即组件自己的 PendingIntent） |
| 与 3×2 共存 | 同屏两个组件；每次刷新日志 `refreshed=2/2 … nextClass=1`；3×2 内容与样式未变（截图同前） |
| 刷新机制 | 未新增机制：沿用 `CourseWidgetUpdater.refreshAll`（数据写入 10 处 + 边界闹钟 + 开机），两个尺寸共用一个 `TodayPlan` |

### AA-4. 3×2 回归

本轮**没有改动** `CourseWidgetRenderer`、`widget_course.xml`、`widget_course_card.xml`、
`course_widget_info.xml`、`TodayPlan`。设备上同一屏的 3×2 仍显示「Aa / 距上课 44:01 / 10:40 - 14:00 · 教学楼F楼F102多 ·
凌铭」与「查看今日全部 2 节课程」，行为与上一轮一致。

### AA-5. 未验证 / 已知限制

1. **缩放未实测**：`resizeMode` 为 `horizontal|vertical`，把高度压到 88dp 以下时应隐藏教室行——该分支只有单测，
   设备上没有被拖到更矮的截图（该启动器的缩放手势与浮动标签栏冲突）。
2. 「正在上课」「今日课程已结束」「今日无课」「学期外」四种状态只有单测覆盖；设备上只看到「下一节」。
   09:35 的边界切换在上一轮验证过（两尺寸共用 `TodayPlan`，逻辑未变）。
3. 横屏、深色模式未验证；深色模式仍未做。
4. 放置依赖拖拽：选择器里的 `+ Add` 在本启动器上被标签栏遮挡，**用户手动添加时可能需要长按预览拖到桌面**。

### AA-6. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1153 项 / 0 失败 / 0 错误**（62 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,198,822 B**。

设备：2×1（id=11）与 3×2（id=7）同时在桌面，课表数据未改动。

### AA-7. 风格对齐（同日追加，取代 AA-2 的视觉描述）

用户指出 2×1 与 3×2「风格不一致」。对比两图确认差异只有一个根源：**2×1 少了 3×2 的浅绿课块**，
色条直接贴在白卡上，且用了 22sp 的大号时间，与 3×2 的字号体系不同。

**改动**（只动 2×1，不碰 3×2）：

- `widget_next_class.xml`：在白色卡片内再套一层 **`widget_headline_bg` 浅绿块**（12dp 圆角、
  `widget_block_padding` 8dp 内边距、垂直居中），**3dp 课程色条移到绿块内部**左侧；
  正文字号改回 3×2 的那一套：状态 11sp / 课名与时间 15sp 粗体 / 教室 12sp，颜色仍用 `widget_ink`
  与 `widget_ink_muted`。
- `values/widget.xml`：删掉专为 2×1 加的四条尺寸（`widget_next_padding`、`widget_next_rail`、
  `widget_next_text_name`、`widget_next_text_time`）——现在两个尺寸共用同一组
  `widget_padding` / `widget_block_padding` / `widget_accent_width` / `widget_text_*`。
- 22sp 的时间降为 15sp 粗体、靠右，与 3×2 里倒计时的处理一致；时间没有从视觉层级里消失，
  只是回到了同一套字号。

**设备复核**（重建 + 重装后截图 `widget-111`）：2×1 现在与 3×2 同为「白卡 + 浅绿课块 + 块内色条」，
字色、字号、圆角、内边距一致；内容仍是 `下一节 / 距上课 38:17 / Aa / 10:40 / 教学楼F楼F102多`，
无裁切、无重叠。3×2 未改动。

**本轮最终门禁**：`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL；
**1153 项 / 0 失败 / 0 错误**；lint **0 errors, 2 warnings**（既有）；APK **1,198,638 B**。
（AA-6 记的 1,198,822 B 是风格对齐之前的构建。）

---

## AB. 2026-10-18 新增 2×2 小组件（接下来的课）

第三种尺寸：2 列 × 2 行方形，定位「快速查看接下来的课程」——主区域是现在/下一节，下方一行是再下一节。

### AB-1. 实现

| 新增 | 作用 |
|---|---|
| `widget/SquareWidgetProvider.java` | 2×2 的生命周期，全部转调 `CourseWidgetUpdater` |
| `widget/SquareWidgetRenderer.java` | 绘制与文案：`headerOf`（周几 + 第N周 单/双周）、`secondaryText`（下节/再下节 + 时间 + 课名）、`detailOf`（时间 · 教室） |
| `res/layout/widget_square.xml` | 头部一行 + 绿色主卡（状态/倒计时/课名/时间·教室）+ 空态块 + 次要课程行 |
| `res/xml/square_widget_info.xml` | `minWidth 110dp` / `minHeight 110dp` / `targetCell 2×2` |
| `widget/WidgetChronometer.java` | **抽出的共享倒计时**：`labelFor` + `bind`（含 `elapsedRealtime` 换算），2×1 与 2×2 都用它 |
| `SquareWidgetRendererTest` | 10 项 |

**修改**：`AndroidManifest.xml`（第三个 receiver，label「接下来的课」）、`CourseWidgetUpdater`（第三个 id 数组 + 日志 `square=`）、
`NextClassWidgetRenderer`（倒计时改为委托 `WidgetChronometer`，行为不变）、`values/widget.xml`（两条字符串）。

**课程选择不重新实现**：主卡与次卡都来自 `CourseWidgetRenderer.cards(plan)`——即 `TodayPlan` 的 headline（上课中=当前，否则=下一节）
加其后一节。单双周、学期周次、作息表、教室规则全部沿用 `TodayPlan`。

**刷新**：仍是同一条链路——一次 `CourseWidgetDataSource.read` 同时喂三种尺寸，一个边界闹钟，
十处数据写入点。本轮日志：`refreshed=3/3 … nextClass=1 square=1`。

### AB-2. 布局与视觉

```
周日 · 第5周 单周              ← 11sp，只给周几 + 周次，不堆日期
┌──────────────────────────┐
│▎下一节        距上课 32:51 │  ← 绿块 + 块内 3dp 课程色条，与 2×1/3×2 同一套
│ Aa                        │  ← 15sp 粗体，最多两行
│ 10:40 - 14:00 · 教学楼…    │  ← 12sp 次要色
└──────────────────────────┘
下节 15:30 · 高等数学          ← 12sp 次要色，只有一节课时整行隐藏
```

高度 <150dp 时隐藏次要课程行，<130dp 时隐藏教室（只留时间）；空态用绿块显示
`今日课程已结束 / 今日无课 / 还没有课表 / 学期已结束` + 一句提示，不虚构课程。
点整块 → `MainActivity`（沿用 2×1 的跳转方式）。

### AB-3. 设备实测

| 场景 | 结果 |
|---|---|
| 选择器 | 「拾课 / **3 widgets**」→ 今日课程 3×2、下一节 2×1、**接下来的课 2×2**（预览为方形：头部 + 绿块） |
| 放置 | 每条的 `+ Add` 仍被底部浮动标签栏遮挡；继续用 `input draganddrop` 拖预览，一次成功（id=12） |
| 元数据 | `options widget=12 minH=99 maxH=203` |
| 渲染 | `周日 · 第5周 单周` / `下一节` / `距上课 32:51` / `Aa` / `10:40 - 14:00 · 教学楼…`（实际宽 137dp，故教室省略号） |
| 点击 | `START … cmp=.MainActivity`（uid 10237，组件自己的 PendingIntent） |
| 三种尺寸共存 | 同屏 3 个组件；每次刷新 `refreshed=3/3 … nextClass=1 square=1` |

放置后实例由启动器给了 **137×200dp**（拖拽布局不按格对齐），在设计允许的 resize 范围内，渲染正常。

### AB-4. 回归

- **3×2**：本轮未改动 `CourseWidgetRenderer` / `widget_course.xml` / `widget_course_card.xml` / `course_widget_info.xml` / `TodayPlan`；
  同屏截图显示内容与样式与上一轮一致。
- **2×1**：倒计时改为委托 `WidgetChronometer`（同一份逻辑搬进共享类），标签逻辑单测仍通过，设备截图与上一轮一致。

### AB-5. 未验证 / 风险

1. **次要课程行没有真实数据可拍**：今天只有 2 节课（`dd` 已结束、`Aa` 未开始），`cards=1`，所以 2×2 只显示主卡。
   次要行的内容由单测覆盖（`whileAClassRunsTheSecondLineIsTheNextClass` / `betweenClassesTheSecondLineIs…`）。
2. 「正在上课」「今日课程已结束」「今日无课」「学期外」四种状态只有单测覆盖。
3. 缩放（<150dp 隐藏次要行、<130dp 隐藏教室）未实测；横屏、深色模式未验证。
4. 放置依赖拖拽；拖出来的尺寸不按格对齐，用户可在桌面自行缩放。
5. 启动器给的尺寸大于设计尺寸时，主卡下方会留白。

### AB-6. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1163 项 / 0 失败 / 0 错误**（63 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,203,000 B**。

---

## AC. 2026-10-18 小组件半透明（减少对壁纸的遮挡）

用户要求三种尺寸都「适当增加透明度」，同时不能影响内容显示。

### AC-1. 改了什么

只改了 `res/values/widget.xml` 里的三个颜色（三种尺寸共用同一组 drawable 与颜色，所以是**一处改动、三个尺寸同时生效**）：

| 颜色 | 之前 | 现在 | 用途 |
|---|---|---|---|
| `widget_surface` | `#FFFFFFFF`（不透明白） | **`#E0FFFFFF`（88%）** | 外层白卡 `widget_bg.xml` |
| `widget_headline` | `#FFCFE5DE`（不透明浅绿） | **`#C7CFE5DE`（78%）** | 课块 `widget_headline_bg.xml`、色条底色 |
| `widget_outline` | `#FFDFE7E1` | **`#99DFE7E1`（60%）** | 卡片 1dp 描边 |

**文字颜色一律保持不透明**：`widget_ink #16201D`、`widget_ink_muted #4E5C57`、`widget_button #11584E`。
这是「不影响显示」的关键——半透明的只有背景和描边，字仍是最深的那一档。

### AC-2. 为什么取这两个比例

- 白卡 88%：在最暗的壁纸上也等效于约 `#E0`，深色正文对比约 13:1；在最亮的壁纸上就是白底，深色字同样清楚。
- 课块 78%：绿色调保留（能看出是"正在上的课"那块），但壁纸的明暗会透出来，不再像贴了一块塑料板。
- 描边降到 60%：仍然能勾出圆角边界，但不会在浅色壁纸上显得生硬。

### AC-3. 设备复核

`emulator-5554` 上重新安装后截图（`widget-130`）：三张卡片都能看到壁纸的明暗过渡从卡面透出（3×2 的右下、2×2 的下半部分尤其明显），
文字与绿块层次不变，2×1 的「下一节 / 距上课 / Aa / 10:40 / 教室」仍然清晰。
课名、时间、教室、倒计时、入口行均未改动。

### AC-4. 未验证 / 风险

1. 极端壁纸（纯黑、纯白、高频花纹）下的可读性未逐一试；按上面的等效亮度推算仍在安全范围。
2. 深色模式仍未做——若将来加 `values-night`，半透明度应单独再定，深色卡面在暗壁纸上的辨识度会下降。
3. 截图只在当前这张壁纸下验证。

### AC-5. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1163 项 / 0 失败 / 0 错误**（63 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,203,000 B**（只改颜色值，产物大小不变）。

---

## AD. 2026-10-18 第二张卡片：用「已结束」的那节课补位

用户反馈：3×2 与 2×2 明明放得下两节课，却只显示了一节。

### AD-1. 问题是什么

不是布局问题，是**选择规则**：`cards()` 只取 `headline`（正在上或下一节）+ 其后课程。
当天 18:00 之后只剩一节没上时，第二个位置就是空的——而此时底部入口正写着「查看今日全部 2 节课程」，
「2 节」和「1 张卡」放在一起就是自相矛盾。上一版定的规则（只显示未结束的课）在这一刻露出了缺口。

### AD-2. 改法

**给「最近结束的那节课」一个新的用途：补空位，并明确标注「已结束」。**

| 改动 | 内容 |
|---|---|
| `TodayPlan` | 新增 `Session.ended(now)` 与 `previous()`（当天最近结束的一节，没有则 null），构造参数与 `empty()` 同步 |
| `CourseWidgetRenderer.cards()` | 先填 headline + 后续课程；**只有仍未满两张时**才用 `previous()` 补位——有未上的课永远优先 |
| `WidgetChronometer.bind()` | 已结束的课：标签写「已结束」、只显示标签、隐藏 `Chronometer`（不再显示倒计时） |
| 3×2 | 倒计时改为走共享的 `WidgetChronometer`，于是第二张卡自动带上「已结束」 |
| 2×2 | 次要行文案：已结束 → `已结束 09:35 · dd`（用结束时间，不用开始时间） |
| 2×1 | 不变：它只显示主课，不存在第二个位置 |

**规则优先级**（写进代码注释）：正在上 > 下一节 > 再下一节 > 最近已结束。
已结束的课只在「没有更多未上的课」时出现，所以下午不会又被上午的课顶掉。

### AD-3. 设备复核

改后同屏（截图 `widget-140`）：

- **3×2**：`Aa / 距上课 22:31 / 10:40 - 14:00 · 教学楼F楼F102多 · 凌铭` + `dd / **已结束** / 08:15 - 09:35 · 教学楼F楼F202多 · 李海波`，
  与底部「查看今日全部 2 节课程」对得上。
- **2×2**：主卡 `Aa / 距上课 22:31`，次要行 `已结束 09:35 · dd`。
- **2×1**：仍是单卡（`下一节 / 距上课 22:31 / Aa / 10:40 / 教学楼F楼F102多`），行为未变。
- 日志：`refreshed=3/3 … cards=2 today=2`。

### AD-4. 测试

新增/调整 5 项（共 1164 项，全绿）：

- `aLaterHourLeadsWithTheRemainingClassNotTheFirstOne`：中午以未上的课领衔，补位的是刚结束的那节。
- `aDayWithOneClassLeftShowsItAlongsideTheFinishedOne`：只剩一节未上时，两张卡都出现（第一张是进行中的、第二张已结束）。
- `twoUpcomingClassesLeaveNoRoomForTheFinishedOne`：有两节未上时，已结束的课不出现。
- 3×2 的 `beforeTheFirstClassTheTwoSoonestAreShown` 断言补上「此时第二张不是已结束」。
- 2×2 的 `aDayWithOneClassLeftFillsTheSecondLineWithTheFinishedOne`：次要行是 `已结束 09:35 · Course 1`。

### AD-5. 未验证 / 风险

1. 「已结束」卡的视觉只在真机上看了 3×2/2×2 各一次；字号放大、窄尺寸下未复测。
2. 若一天里有两节同一时段重叠的课，`previous()` 取的是"开始更晚的那节已结束课"，这是既有冲突处理（按开始时间排序）的延伸，未单独测。
3. 深色模式仍未做。

### AD-6. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1164 项 / 0 失败 / 0 错误**（63 个测试类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,203,112 B**。

---

## AE. 2026-10-18 三种尺寸视觉重做（Glass + 4dp 状态条 + 字阶）

用户给出完整设计规范并要求三个尺寸分别实现。这是一次**视觉层重写**：布局、配色、字号、间距全部换掉，
业务规则（选择、刷新、点击目标）保持不动。

### AE-1. 设计令牌（`res/values/widget.xml` 重写）

| 令牌 | 值 | 用途 |
|---|---|---|
| `widget_glass` | `#D9F0F5F2` | 卡片底：85% 白 + 极淡品牌绿，透过壁纸 |
| `widget_outline` | `#24000000` | 14% 黑细边，玻璃边缘 |
| `widget_ink` / `_muted` / `_faint` | `#1A1A1A` / `#666666` / `#999999` | 三级文字 |
| `widget_accent` / `_tint` / `_on_accent` | `#11584E` / 12% / 白 | 状态、倒计时、周次胶囊 |
| `widget_ended` | `#B9C0BD` | 已结束的色条与文字 |
| `widget_divider` | `#14000000` | 列表分隔线 |
| 字号 | 16/13/11（列表）、16/16（紧凑名与时间）、10、9（徽章） | 三层字阶 |

**真实毛玻璃（blur）在 RemoteViews 里做不到**——平台只给系统自己的浮层提供背景模糊。
因此玻璃感来自低透明度填充 + 细边 + 让壁纸明暗透出来，这一点写进了 `widget_bg.xml` 的注释。

### AE-2. 三个尺寸的结构

**3×2 今日课程列表**（`widget_course.xml` + `widget_course_card.xml`）
```
10月18日 周日                    [第5周 (单周)]
▎Aa                              距上课 00:35      ← 4dp 主题色条
  10:40 - 14:00
  教学楼F楼F102多 · 凌铭
  ─────────────────────────────  ← 1dp 分隔线（最后一项不画）
▎dd                              已结束            ← 4dp 灰色条
  08:15 - 09:35
  教学楼F楼F202多 · 李海波
        查看今日全部 2 节课程 ›                     ← 居中浅色
```
- 点课程项 → `CourseDetailActivity`（每个 courseId 一个 requestCode）；点整卡/入口 → `MainActivity`。
- 去掉了原先的大面积浅绿块与彩色卡片，颜色只用在 4dp 状态条上。

**2×1 极简下一节**（`widget_next_class.xml`）
```
下一节 (第5周 单周)
Aa                        [距上课 00:35]   ← 徽章：实心主题色胶囊 + 白字
10:40
教学楼F楼F102多
```
**2×2 下一节 + 已结束**（`widget_square.xml`）
```
下一节 (第5周 单周)
Aa                        [距上课 00:35]
10:40 - 14:00
教学楼F楼F102多
─────────────────────────────
▎dd (09:35结束)                             ← 灰条 + 灰字
```

### AE-3. 与规范的差异（都写进了布局注释）

1. **主题色用品牌绿 `#11584E`，不是蓝**：规范里「如主题蓝」是举例，而这个 App 的主色是墨绿，
   换蓝会让组件与 App 内页面脱节。
2. **倒计时徽章放在课程名右侧，而不是右上角状态行**：180dp 宽下「下一节 (第5周 单周)」+ 徽章放不进同一行，
   实测会截断成「下一节 (第5周 …」。把徽章下移一行后两者都完整。
3. **等宽数字**：所有时间与倒计时都加了 `android:fontFeatureSettings="tnum"`，两行时间逐位对齐。
4. **课程名与时间不截断**：名称 `maxLines=1`（2×2 允许 2 行）且 `ellipsize=end`；
   只有地点/教师/房间行使用省略号（规范第 5 条）。

### AE-4. 代码改动

| 文件 | 改动 |
|---|---|
| `CourseWidgetRenderer` | 重写：列表项、分隔线、周次胶囊、`dateLine`、`metaOf`、入口文案带 ›、整卡点击、课程项点击 |
| `NextClassWidgetRenderer` | 重写：`statusLine`（带周次括号）、`weekLabel`、`pillText`；布局 id 大改（`widget_next_bar/badge/...`） |
| `SquareWidgetRenderer` | 重写：状态行 + 名称徽章 + 时间 + 房间 + 分隔线 + `dd (09:35结束)` 行 |
| `WidgetChronometer` | `bind` 返回 boolean（是否真的在倒计时），并**不再设置颜色**（颜色交给各布局） |
| 资源 | 新增 `widget_pill_bg.xml`、`widget_badge_bg.xml`；删除 `widget_headline_bg.xml`；4 个布局全部重写 |

刷新链路、`TodayPlan`、选择规则（headline → 后续 → 已结束补位）**未改动**。

### AE-5. 设备实测（`emulator-5554`）

| 场景 | 结果 |
|---|---|
| 静态渲染 | 三尺寸全部按上图显示，无裁切、无重叠；3×2 两节课 + 分隔线 + 居中出现 |
| 长文本 | 地点/教师行超宽时省略号；课程名与时间完整 |
| 等宽数字 | `10:40 - 14:00` 与 `08:15 - 09:35` 冒号逐位对齐 |
| 状态 | **下一节**（10:39 截图）与 **正在上课**（10:40 边界刷新后截图，`state=IN_CLASS`，倒计时变「距下课 3:19:10」）均真机确认 |
| 点击 | 课程项 → `CourseDetailActivity`；入口/整卡 → `MainActivity`（日志 `from uid 10237 (com.weihe.schedule)`，组件自己的 PendingIntent） |
| 刷新 | `refreshed=3/3 … cards=2 today=2 nextClass=1 square=1`，边界闹钟在 10:40 准时把 UPCOMING 切成 IN_CLASS |

**踩到并修掉的布局问题**（都有截图对照）：
1. 首版 3×2 内容超高，入口行被挤出卡片 → 收紧 padding/字号，并把卡片区改为 `weight=1` 撑开、入口贴底。
2. 首版 2×1 地点行被卡片下沿切掉 → 整体缩小一档（名/时间 16sp、内边距 7dp）。
3. 徽章占用过宽导致状态行截断 → 徽章移到名称行。

### AE-6. 测试

**1159 项 / 0 失败 / 0 错误**（63 个类）。改动集中在文案与结构：
- `CourseWidgetRendererTest` 重写：选择规则（含已结束补位）、入口计数、`metaOf`、`dateLine`、空态文案、requestCode 折叠。
- `SquareWidgetRendererTest` 重写：`dd (09:35结束)` 与 `下节 09:35 · …` 两种次要行、高度阈值。
- `NextClassWidgetRendererTest` 新增 `statusLine` / `weekLabel` / `pillText` 三条用例。
- 删除的是随 API 一起消失的用例（`dateTitle`/`detailOf`/`countdownTargetOf`），对应行为分别由 `dateLine`、`metaOf`、`WidgetChronometer.labelFor` 的现有用例覆盖。

### AE-7. 未验证 / 风险

1. **极端壁纸**（纯黑、纯白、高频花纹）下 85% 玻璃的可读性只按等效亮度推算，未逐一试。
2. **窄尺寸**（启动器把 2×1 缩到 40dp 高）与横屏未复测；高度阈值只有单测。
3. **深色模式**仍未做；若加 `values-night`，玻璃与灰阶需要重新定。
4. 2×2 下半部分（已结束行）在只显示一节时整块隐藏，视觉上会是「一张卡片 + 大片空白」，
   这是启动器给的尺寸（137×200dp）大于设计尺寸所致。
5. 字体 `tnum` 依赖系统 Roboto/思源字体的 tabular 特性，第三方 ROM 上未验证。

### AE-8. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1159 项 / 0 失败 / 0 错误**；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,217,637 B**。

---

## AF. 2026-10-18 视觉微调（课程色条、2×2 已结束行、2×1 时间、周次信息）

用户对着自己真实课表（`dd` / `AaAadadadad` / `cccZ1561165165465…`）提了五点，全部改掉。

### AF-1. 五点改动

| # | 用户要求 | 改法 |
|---|---|---|
| 1 | **颜色条与课程详情页的标记色对应** | 三个尺寸的 4dp 色条改回 `session.color()`（就是详情页那个色）。状态不再靠颜色表达，改由「已结束」文字与灰色文字承担 |
| 2 | **2×2 又只能显示一个课程；已上完的放最下面缩略** | 主卡是正在上/下一节；细线下面**固定是 `plan.previous()`**（最近结束的一节），单行 `dd (09:35结束)`，灰色文字 + 该课自己的色条 |
| 3 | **2×1 的上课时间不要加粗** | `widget_next_time` 去掉 `textStyle="bold"`，颜色改 `widget_ink_muted` |
| 4 | **2×1 / 2×2 不显示周次与单双周** | `statusLine()` 只返回状态词（`正在上课` / `下一节` / 空态文案）；周次胶囊只留在 3×2 头部 |
| 5 | （同上截图）3×2 列表样式保持 | 未改动列表结构、分隔线、入口行 |

### AF-2. 与上一版规则的差异

- **2×2 的次要行不再「有下一节就显示下一节」**：那条规则让 2×2 在课间看起来像「只有一节课 + 一个下次提醒」，
  用户明确要的是「下一节 + 已结束」的结构 —— 与最初设计稿的「中尺寸组件 B」一致。
- `SquareWidgetRenderer.secondaryText` 改名为 **`finishedText(session)`**：它现在只负责一种句子，
  名字与职责对齐，也避免再出现「有时是已结束、有时是下一节」的双重语义。
- 3×2 的补位规则不变：仍是有未上的课优先，只有空出格子时才用已结束的那节补（`cards()` 未改）。

### AF-3. 设备复核（`widget-170`）

- 3×2：`AaAadadadad` 蓝色条、`cccZ…` 红色条（两节课各自的颜色），入口「查看今日全部 3 节课程 ›」。
- 2×1：`正在上课` / `AaAada…` + 徽章 `距下课 3:11:33` / `10:40`（灰色不加粗）/ `教学楼F楼F102多`。
- 2×2：主卡同 2×1；细线下 `▎dd (09:35结束)`，色条是 dd 的绿色，文字灰色。
- 日志：`refreshed=3/3 state=IN_CLASS cards=2 today=3`。

### AF-4. 仍然存在的取舍

- **课程名仍会在 3×2 列表与 2×1 上省略**：`cccZ1561165165465…`、`AaAada…`。原因是这两个位置只有一行的高度预算
  （2×2 的主卡允许两行，可以看到完整名字）。若要彻底不截断，需要给列表项两行或压缩字号，会挤掉时间/地点行。
- 已结束行的文字用灰色 `#B9C0BD`，在浅壁纸上对比偏低（设计稿要求「整体降低透明度」），但色条保留课程颜色便于识别。

### AF-5. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1159 项 / 0 失败 / 0 错误**（63 个类）；lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`）；
APK **1,205,130 B**。

测试相应改写：`SquareWidgetRendererTest` 换成 `finishedText` + 四条新语义（课间取上一节、无已结束时不显示、两节已结束时取较晚的），
`NextClassWidgetRendererTest` 的 `statusLine` 断言改为「不含周次」。

---

## AG. 2026-10-18 倒计时改到右上、色条避让状态行、去掉秒

### AG-1. 三点改动

| # | 用户要求 | 改法 |
|---|---|---|
| 1 | **2×1 / 2×2 的倒计时放到右上角，和「正在上课」同一行** | 两个布局都改成「顶部状态行（状态 + 徽章）→ 下方课程块」两段式；徽章重新回到状态行右侧 |
| 2 | **颜色条不要覆盖到「正在上课」文本** | 4dp 色条从课程块那一行开始，不再贯穿整个卡片高度；状态行横跨全宽、不带色条 |
| 3 | **所有组件的倒计时去掉秒，只保留时和分** | 放弃 `Chronometer`，改用 `TextView` 写 `3:08` / `12 分`；`WidgetChronometer.remainText` 负责换算 |

### AG-2. 为什么必须换掉 Chronometer

`Chronometer` 输出格式固定为 `H:MM:SS` 或 `MM:SS`，`setFormat` 只能包前后缀，改不了时长本身。
要去掉秒就只能自己写文本，而文本不会自动跳秒——于是刷新策略改成：**只要屏幕上有倒计时，就把同一个边界闹钟拉到下一个整分**
（`CourseWidgetRefreshScheduler`，`WidgetChronometer.counting(plan, now)` 为真时生效）。
代价是一节课期间每分钟一次唤醒，收益是读数精确到分钟、且没有秒位在跳；没有任何倒计时时仍然只按边界唤醒。

时间文案规则（`remainText`）：
- ≥1 小时 → `3:08`
- <1 小时 → `12 分`
- 最后一分钟显示 `1 分`（不足一分钟也不出现 `0 分`）

### AG-3. 布局改动

- `widget_next_class.xml`：`[状态 + 徽章]` 一行 → `[色条 | 名称/时间/地点]` 一块；色条只覆盖课程块。
- `widget_square.xml`：同样两段式，色条覆盖名称/时间/地点，细线与已结束行保持不变。
- `widget_course_card.xml`：`Chronometer` → `TextView`（`tnum`，宽度自适应），色条仍是课程色。
- 三个布局里所有倒计时都用等宽数字；徽章文字 9sp、白字、实心主题色。

### AG-4. 设备复核（`widget-181`）

- 2×1：`正在上课` + 右上徽章 `距下课 3:01`；色条从课程名那一行开始；名称 `AaAadadadad` 完整显示（徽章移走后名称拿到整行宽度）。
- 2×2：同上，细线下 `dd (09:35结束)`。
- 3×2：条目里 `距下课 3:01` / `距上课 4:01`，同样没有秒。
- 两分钟内截图从 `3:03` 变成 `3:01`，说明分钟级刷新真的在跑；日志 `refresh alarm at=… exact`，间隔一分钟。

### AG-5. 风险与未验证

1. **耗电**：一节课期间每分钟一次 `refreshAll`（读一次课表 + 重绘三张卡）。选课/假期等无倒计时时不会触发。
   如果觉得费电，可以把分钟刻度改成"只在 2×1/2×2 存在时"或降到 5 分钟，但读数会滞后。
2. `updatePeriodMillis` 仍是 30 分钟，与分钟闹钟并存；闹钟被系统清掉时最坏退回 30 分钟粒度。
3. 设备上只观察了两分钟的跳动，没有跨小时验证 `3:08` 这种带小时格式的真机显示（单测覆盖）。
4. 名称过长时 3×2 列表仍省略（`cccZ15611651654654…`）。

### AG-6. 本轮门禁

`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → **BUILD SUCCESSFUL**；
**1166 项 / 0 失败 / 0 错误**（64 个类，新增 `WidgetChronometerTest` 7 项）；
lint **0 errors, 2 warnings**（既有 `IconXmlAndPng`；`NestedWeights` 已用 `tools:ignore` 说明原因）；
APK **1,206,451 B**。
 
---

## AH. 2026-10-18 去除全部倒计时

用户要求「去除所有组件中的倒计时显示」。倒计时相关的代码、资源与调度一并删除，不留死代码。

### AH-1. 删了什么

| 位置 | 删除内容 |
|---|---|
| 3×2 列表项 | widget_card_countdown_label + widget_card_countdown（每节课右侧的「距上课/距下课 3:08」） |
| 2×1 | 整个徽章容器 widget_next_badge 及其两个子视图 |
| 2×2 | 整个徽章容器 widget_square_badge 及其两个子视图 |
| 代码 | WidgetChronometer.java（整类删除）、WidgetChronometerTest.java（7 项）、渲染器里的 ind/visibility 调用、NextClassWidgetRenderer.countdownLabelOf |
| 资源 | widget_badge_bg.xml、widget_on_accent、widget_text_badge |
| 调度 | CourseWidgetRefreshScheduler 里的**分钟对齐逻辑**整体撤掉，回到只在上下课/跨日边界唤醒 |

**保留**：状态文字（正在上课 / 下一节 / 今日课程已结束 / 已结束 标记）、课程名、时间、地点、勤务入口、已结束行。

### AH-2. 连带效果

- **任务栏耗电回到原状**：设备日志 efresh alarm at=1792332000000 exact → 下一次唤醒是 15:00（下一节课开始），不再每分钟一次。
- **课程名拿到整行宽度**：3×2 第二行 cccZ156116516546546545… 比之前能显示更多字符（右侧不再被倒计时占用）。
- NextClassWidgetRendererTest 里与 countdownLabelOf 有关的三处断言随 API 一起删除，其余状态/空态/高度阈值用例保留。

### AH-3. 设备复核（widget-190）

- 3×2：AaAadadadad / cccZ… 两行，只有名称、时间、地点与入口，没有任何计时数字。
- 2×1：正在上课 + 名称 + 10:40 + 地点。
- 2×2：同上，细线下 dd (09:35结束)。
- 日志：efreshed=3/3 state=IN_CLASS cards=2 today=3；闹钟指向 15:00。

### AH-4. 未验证 / 风险

1. 没有倒计时后，「距离下课还有多久」只能自己算——这是需求本身的取舍，不是缺陷。
2. 其余既有未验证项不变：极端壁纸、窄尺寸/横屏、深色模式、超长课程名在 3×2 列表仍省略。

### AH-5. 本轮门禁

--rerun-tasks testDebugUnitTest assembleDebug lintDebug → **BUILD SUCCESSFUL**；
**1158 项 / 0 失败 / 0 错误**（63 个类）；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）；
APK **1205376 B**。
 
---

## AI. 2026-10-18 顶行统一为 3×2 的头部；2×2 补「下一节」行并把已结束行压到卡底

用户两点：① 2×1 / 2×2 的顶行与 3×2 一致；② 2×2 仍然只显示一节课，下部大片空白。

### AI-1. 顶行

两个小尺寸的顶行改成和 3×2 完全相同的结构：**左侧 10月18日 周日，右侧 第5周 (单周) 胶囊**。

- 原来那行 正在上课 / 下一节 状态词随之取消——3×2 列表里也没有这个词，显示的是哪节课本身就说明了状态。
- NextClassWidgetRenderer.statusLabel / statusLine 整个删除（连同它们的用例），只剩 pillText / weekLabel / clockOf / roomOf / emptyTitle / emptyHint。
- 空态仍然由原来的 name / room 两行承担（今天的课都上完了 + 点开看看今天）。
- 顺带修掉一个边界：**学期外不再显示周次胶囊**（之前 week > 0 就显示，会出现「第21周」配「学期已结束」）。

### AI-2. 2×2 的三段内容

`
10月18日 周日                    [第5周 (单周)]
▎AaAadadadad
  10:40 - 14:00
  教学楼F楼F102多
下节 15:00 · cccZ156116…
                          ← 弹性空行
────────────────────────────
▎dd (09:35结束)                ← 贴卡片底边
`

- 中间行取自 plan.following().get(0)，**不用 cards()**：那个助手会用已结束的课补空位，而 2×2 的已结束行是独立的一条。
- 新增 @+id/widget_square_spacer（weight=1）把已结束行压到卡片底边，空出来的位置落在中间，读起来是「页脚」而不是「内容没填满」。
- 高度不足（<150dp）时仍只隐藏已结束行，中间行保留。

### AI-3. 设备复核（widget-201）

- 2×1：10月18日 周日 + 第5周 (单周) / AaAadadadad / 10:40 / 教学楼F楼F102多。
- 2×2：同样的头部 + 主课 + 下节 15:00 · cccZ156116… + 底部 dd (09:35结束)，空白只剩中间那一段（当前实例是 137×200dp，比 2×2 设计尺寸高，真 2×2 下会几乎消失）。
- 3×2 未改动，仍是两节课 + 入口。

### AI-4. 未验证 / 风险

1. 当前 2×2 实例被启动器放成 137×200dp，比设计尺寸（约 184×184dp）高，中间仍有可见留白；真按 2 格高度放置时会更紧凑，未实测。
2. 学期外不再有周次胶囊，属于行为变更（3×2 同样生效）。
3. 其余既有未验证项不变：极端壁纸、窄尺寸/横屏、深色模式、超长课程名在 3×2 列表仍省略。

### AI-5. 本轮门禁

--rerun-tasks testDebugUnitTest assembleDebug lintDebug → **BUILD SUCCESSFUL**；
**1160 项 / 0 失败 / 0 错误**（63 个类）；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）；
APK **1203096 B**。
 
---

## AJ. 2026-10-18 2×2 改为「与 3×2 同样的两行课程 + 卡底已结束」

用户澄清：2×2 要的是**像 3×2 那样显示两节课**，最后再放已上完的课程。

### AJ-1. 实现方式：复用 3×2 的列表项

- CourseWidgetRenderer.itemView(...) 从 private 改为**包内可见**，2×2 直接 ddView 到自己的
  widget_square_cards 容器里。两个尺寸因此是**同一份列表项 XML 与同一段绑定逻辑**，不是第二套长得像的实现。
- SquareWidgetRenderer.items(plan)：headline + ollowing().get(0)，最多两行；
  **不用 cards()**，因为它会用已结束的课补空位，会和卡底的已结束行重复（这一点有单测钉住）。
- 结构：头部（日期 + 周次胶囊）→ 两行课程 → 弹性空行 → 细分隔线 → ▎dd (09:35结束) 贴在卡底。
- 点课程行 → CourseDetailActivity（与 3×2 同一个 itemView，点击目标也一致）；点空白/其它 → MainActivity。

### AJ-2. 设备复核（widget-210）

2×2 现在与 3×2 完全同构：

`
10月18日 周日                    [第5周 (单周)]
▎AaAadadadad
  10:40 - 14:00
  教学楼F楼F102多 · 凌铭
▎cccZ1561165165…
  15:00 - 18:50
  教学楼F楼F106多 · 凌铭
────────────────────────────
▎dd (09:35结束)
`

两条课程行各有自己的课程色条（蓝 / 红），底部已结束行是 dd 的绿色条 + 灰字。
下节 15:00 · … 那行（上一版）已被完整课程行取代。

### AJ-3. 测试

**1161 项 / 0 失败 / 0 错误**（63 个类）。SquareWidgetRendererTest 重写为两组：
- **哪些课成为行**：headline + 下一节、只剩一节时只有一行、**已结束的课不能同时成为行与页脚**、晚上无课则空、永远不超过两行。
- **页脚**：Course 1 (09:35结束)、取较晚结束的那节、第一节课前没有页脚、高度阈值。

### AJ-4. 未验证 / 风险

1. 当前实例是 137×200dp（启动器拖出来的尺寸），两行 + 页脚之后中间仍有约一行留白；按 2 格高度正常放置会更紧凑。
2. 2×2 的课程名仍是 1 行省略（cccZ1561165165…）——与 3×2 同一条列表项的结果，属已知取舍。
3. 其余既有未验证项不变：极端壁纸、窄尺寸/横屏、深色模式。

### AJ-5. 本轮门禁

--rerun-tasks testDebugUnitTest assembleDebug lintDebug → **BUILD SUCCESSFUL**；
**1161 项 / 0 失败 / 0 错误**；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）；
APK **1,202,488 B**。
 
---

## AK. 2026-10-18 空态文字左上对齐，2×1 无课时去掉色带

用户两点：① 整理没有课时的文字排版；② 2×1 里多了一条色带。

### AK-1. 改了什么

| 位置 | 之前 | 现在 |
|---|---|---|
| 3×2 空态 | 绿块紧贴头部下方，入口行被弹性空行推到卡底，中间一大片空 | 标题与提示**左上对齐**排在列表区顶部，入口仍在卡底 |
| 2×1 空态 | 左侧有一条灰色色带（无课却有色带） | **色带隐藏**；标题与提示紧凑相邻（空的时间行也一并隐藏，否则两条文案被拉开一行） |
| 2×2 空态 | 一句「今天没有课」垂直居中在卡片中部 | **左上对齐**，紧跟头部下方 |
| 2×1 正常态 | 三行垂直居中 | 改为**顶部对齐**（与 3×2/2×2 的列表一致；96dp 下差异只有几个像素） |

### AK-2. 设备复核（widget-230）

设备上当前课表确实没有今天的课（state=NO_CLASS_TODAY today=0，与用户截图一致），因此这是真实的空态截图：
三个组件的「今天没有课 / 点开看看今天」都紧贴头部左对齐；2×1 不再有色带；3×2 的入口行仍在卡片底部。

> 说明：过程中曾临时把数据源改成空课表以便截图，该改动**已还原**（CourseWidgetDataSource 现为
> TodayPlan.of(repository.getCourses(), …)），最终安装包用的是真实数据。

### AK-3. 未验证 / 风险

1. 3×2 空态下入口行固定在卡底，中间留白依旧存在（入口贴底是此前明确的要求）。
2. 其余既有未验证项不变：极端壁纸、窄尺寸/横屏、深色模式。

### AK-4. 本轮门禁

--rerun-tasks testDebugUnitTest assembleDebug lintDebug → **BUILD SUCCESSFUL**；
**1161 项 / 0 失败 / 0 错误**（63 个类）；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）；
APK **1,202,480 B**。
 
---

## AL. 2026-10-18 课程详情页「添加备注」改到左侧

用户截图指出备注卡里的「添加备注」被推到右下角，左侧一片空白。

### AL-1. 改动

CourseDetailActivity.noteCard(...)：

| 项 | 之前 | 现在 |
|---|---|---|
| 按钮所在行 | ooter.setGravity(Gravity.END)，按钮贴卡片右边缘 | **Gravity.START**，按钮与上方「暂无备注」同一左边缘 |
| 按钮内边距 | UiKit.textButton 的 12dp 四边内边距会把标签缩进 | 标签侧内边距置 0（保留上下，48dp 触摸高度不变），标签与正文严格对齐 |
| 注释 | 描述"放在备注末尾、右对齐" | 改为说明为什么放在左侧、以及为什么不放到标题行 |

**没有**把按钮移到标题行：UiKit.textButton 的 48dp 触摸目标比标题高，放进标题行会把这一张卡的标题顶低，
破坏三张卡标题的对齐（这条取舍原本就写在注释里，保留）。

### AL-2. 设备复核

路径：课表 → 周五 10/16 → 矩阵论 → 课程详情（detail-d3 截图）。备注卡现在是：

`
课程备注
暂无备注
可记录作业、考试安排、学习资料等
添加备注
`

四行全部左对齐，卡片右侧不再有孤零零的按钮和整条空白；卡片高度随内容收紧。
「编辑」状态（有备注时）走同一段代码，文案换成「编辑」，位置相同。

### AL-3. 未验证 / 风险

1. **有备注时的形态没有截图**：需要先写一条备注才能看到「编辑」，本轮只验证了「暂无备注」状态（两者共用同一段代码，仅文案不同）。
2. 48dp 触摸高度保留，所以按钮与上方提示之间仍有约 10dp 的视觉间距（触摸目标居中所致），未压缩。

### AL-4. 本轮门禁

--rerun-tasks testDebugUnitTest assembleDebug lintDebug → **BUILD SUCCESSFUL**；
**1161 项 / 0 失败 / 0 错误**；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）；
APK **1,202,540 B**。
 
---

## AM. 2026-10-18 备注按钮改到右上角（修正 AL）

AL 把「添加备注」放到了左侧；用户澄清要的是**右上角**。本节取代 AL 的位置结论。

### AM-1. 改动

CourseDetailActivity.noteCard(...)：

- 标题 课程备注 与 添加备注 合并成**同一行**：标题 weight=1 靠左，按钮靠右（Gravity.CENTER_VERTICAL）。
- 原来位于卡片底部的 footer 行**整行删除**，卡片因此比 AL 版本更矮。
- 按钮末侧内边距置 0，标签右端与卡片内容右边缘对齐（点击区域仍到边）。
- 按钮高度从 UiKit.textButton 的 48dp 降到 **40dp**（setMinHeight(dp(40)) + 上下 4dp）：
  48dp 会把这一张卡的标题顶到比其它卡低十几个 dp，并在标题与备注之间留一条空白带。
- 备注正文的上间距 SPACE_M → SPACE_S，让标题行与正文之间不再有大空隙。
- 注释里原来说明"不放到标题行"的那段改写为：放到右上角的理由、以及 40dp 这个折中。

### AM-2. 设备复核

路径：课表 → 周五 10/16 → 矩阵论 → 课程详情（detail-note-topright2）：

`
课程备注                              添加备注
暂无备注
可记录作业、考试安排、学习资料等
`

标题与按钮同一行、按钮在右上角；标题行与正文间距恢复正常，卡片底部不再有空的 footer 行。
「编辑」状态（有备注时）走同一行同一段代码，只换文案。

### AM-3. 未验证 / 风险

1. 触摸目标从 48dp 降到 40dp（仍高于 32dp，低于 Material 建议的 48dp）——为去掉标题下的空白带所做的取舍，如果更看重可达性可以改回 48dp，代价是那块空白回来。
2. **有备注时的「编辑」形态没有截图**（需先写备注，同一段代码）。
3. 标题在 40dp 行内垂直居中，因此仍比其它卡的标题低约 8dp（比 48dp 时的 ~14dp 小）。

### AM-4. 本轮门禁

--rerun-tasks testDebugUnitTest assembleDebug lintDebug → **BUILD SUCCESSFUL**；
**1161 项 / 0 失败 / 0 错误**；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）；
APK **1,202,536 B**。

## 2026-10-30 仓库分析（只读代码审查）

- 范围：本地 HEAD `9b3cc29`；GitHub 默认分支 `287393d` 相比仅修改 README.md，业务源码一致。审查开始时工作区已有 README.md 修改与未跟踪 app/release/，均保留。
- 已核实：72 个主源码 Java 文件、63 个 *Test.java 文件、静态统计 1161 个 @Test 标记；这些数量不是本轮测试通过数。
- 存在问题（源码审查，未实机复现）：CourseRepository.replaceCoursesAt 的 commit 返回 false 后仍可能读到已更新内存；SettingsActivity.runImport 仅成功分支同步提醒与组件。FakePreferences.commit 的失败模型没有模拟内存已更新但写盘失败。
- 存在问题（源码审查，未实机复现）：ReminderScheduler.register 中途注册失败后仍取消 previous 中所有不在 registered 的槽位，因此无法完整保留尚未替换的旧计划。
- 存在问题：gradle.properties 的 android.aapt2FromMavenOverride 写死 D:\Sdk，构建依赖本机目录。较大 Activity 与 CourseRepository 也增加维护成本。
- 本轮验证：按 --offline --rerun-tasks 尝试 testDebugUnitTest assembleDebug lintDebug；普通、--no-daemon 与沙箱外重试均在任务执行前被 Gradle daemon / loopback connection 错误阻断。测试、构建、Lint 均未得到本轮结果；未运行模拟器或真机。没有修改业务代码，没有 commit 或 push。
- 下一步：优先补齐真实写盘失败模型与提醒注册失败注入，再修复上述异常路径；恢复本机构建环境后重新执行三项检查。