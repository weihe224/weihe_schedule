> **历史版本（已归档）** —— 本文件是 2026-10-11 之前累积的交接记录，内容包含大量
> 已被取代的过程描述与过期的状态判断，**不再作为项目状态的依据**。
>
> **当前有效文档：[`AGENT_HANDOFF.md`](AGENT_HANDOFF.md)**（2026-10-11 重新核实并重写）
> **长期开发规范：[`AGENTS.md`](AGENTS.md)**
>
> 保留本文件仅为留存历史过程。其中任何"已完成/未完成"的判断都可能已经过期，
> 请以 `AGENT_HANDOFF.md` 与真实代码为准。

---
# AGENT_HANDOFF.md — 「拾课」(WeiheSchedule) 项目交接文档

> 本文由接管 Agent 于 **2026-10-11** 依据**当前工作区真实源码、Git 记录与本轮实际执行结果**编写。
> 所有结论均标注证据位置；无法从源码或实际执行中确认的内容一律标注 **「待核实」**，不做推测。
> 本轮**只做接管与验证**：未修改任何生产代码、测试代码、Gradle 配置或依赖；未执行任何破坏性 Git 操作。
>
> 上一轮文档（`PROJECT_ANALYSIS.md` / `PROJECT_ARCHITECTURE.md`）记录的是 **HEAD `21f20ec`** 时点的快照，
> 与本轮实测存在系统性偏差，见 §11.1。**阅读时以本文与当前源码为准。**

---

## 1. 项目基本信息与实际技术栈

| 项 | 实际值 | 证据 |
|---|---|---|
| 项目名 | 拾课 / WeiheSchedule | `settings.gradle:25`、`res/values/strings.xml`（`app_name`） |
| 类型 | Android 课程表应用（单模块 `:app`） | `settings.gradle:26` |
| applicationId / namespace | `com.weihe.schedule` | `app/build.gradle:12,20` |
| 版本 | versionCode **1** / versionName **1.0** | `app/build.gradle:23-24` |
| Android Gradle Plugin | **8.13.0** | `build.gradle:2` |
| Gradle | **8.13**（阿里云镜像，`validateDistributionUrl=false`） | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk / minSdk | **34 / 34 / 26** | `app/build.gradle:13,21,22` |
| Java 源码/目标版本 | **17** | `app/build.gradle:58-59` |
| 本机实际 JDK | **20.0.1**（`C:\Program Files\Java\jdk-20`），本轮构建与测试均通过 | `java -version` |
| 语言 | **100% Java**，无 Kotlin（无 `.kt`、无 kotlin 插件） | 源码树 |
| UI 实现 | **纯 Java 构建 View 树**；`res/` 下**没有 `layout/` 目录**，无 Compose | `app/src/main/res/` 目录列表 |
| AndroidX | **关闭**（`android.useAndroidX=false`） | `gradle.properties:4` |
| 依赖（生产） | 仅 `files('libs/jxl-2.6.12.jar')` | `app/build.gradle:79` |
| 依赖（测试） | 仅 `junit:junit:4.13.2` | `app/build.gradle:85` |
| 单元测试策略 | `unitTests.returnDefaultValues = false`（刻意约束：框架无关层不得触碰 Android） | `app/build.gradle:27-32` |
| lint | `abortOnError true`，`checkReleaseBuilds false`，禁用 7 项（`HardcodedText`/`SetTextI18n`/`MissingTranslation`/`LockedOrientationActivity`/`DiscouragedApi`/`OldTargetApi`/`GradleDependency`） | `app/build.gradle:62-75` |
| 代码规模 | 生产 **44 个 .java / 15,344 行**；测试 **27 个 .java / 9,543 行 / 539 个 `@Test`** | `find`+`wc -l`、`grep -c @Test` |
| Android SDK 路径 | `sdk.dir=D:\Sdk` | `local.properties:8` |

**不存在**（已确认）：`app/src/androidTest/`、`app/src/main/res/layout/`、`app/src/main/kotlin/`、`app/src/main/assets/`。

---

## 2. 当前 Git 分支、HEAD 与工作区状态

```
工作区     D:/vibe coding/Weihe/Weihe   （git rev-parse --show-toplevel 确认）
分支       main（本地唯一分支；远程仅 origin/main）
HEAD       61d9da54771a7790a5f11ee184883c9ef0327324  ("update", 2026-10-11T12:08:22+08:00)
远程       origin  https://github.com/weihe224/weihe_schedule.git
工作区     **不再干净**（2026-10-11 后续一轮的工作尚未提交，见下方"未提交修改"）
          `build/`、`.gradle/`、`.android-home/`、`tools/out/` 已被 .gitignore 忽略
```

**最近 10 条提交**（`git log`）：

| 提交 | 时间 | 说明 |
|---|---|---|
| `61d9da5` | 2026-10-11 12:08 | 上课提醒（`rule/Reminder`、`rule/ReminderPlan`、`reminder/*`）+ 课程详情页（`CourseDetailActivity`、`rule/CourseDetail`）+ `COURSE_REMINDER_PLAN.md` + Manifest 权限/receiver |
| `6875549` | 2026-10-09 23:10 | 备份与恢复（`BackupCodec`、`BackupPreview`、`RestoreReport`、`BackupPreviewActivity`）+ 3 份设计文档 + 测试 |
| `9b40146` | 2026-10-09 23:10 | `SettingsActivity` 重写、`CourseRepository`、`FakePreferences`、导入解析测试；**删除** `ARCHITECTURE.md` / `ARCHITECTURE_REFACTOR_PLAN.md` |
| `efe5ace` | 2026-10-09 21:08 | 文档：记录新的包结构与重构 |
| `a159e7e` `0d7b930` `73ceb71` `0523e5d` `15ad8d4` `87df6dc` | 2026-10-09 21:00–21:05 | 包结构整理（6 个 `refactor(structure)` 提交，逐个迁移到 `.ui`/`.importer`/`.data`/`.rule`/`.design`/`.model`） |

**未提交修改**（截至 2026-10-11 后续一轮复核时，**12 个已修改 + 5 项未跟踪**，共 +1935 / −54 行）：

| 类别 | 文件 |
|---|---|
| 已修改·生产 | `AndroidManifest.xml`、`SettingsActivity.java`、`data/CourseRepository.java`、`data/FakePreferences.java`(测试基建)、`importer/ExcelTimetableReader.java`、`importer/TimetableImportParser.java`、`rule/CourseQuery.java` |
| 已修改·测试 | `data/BackupCodecTest.java`、`data/CourseRepositoryStorageTest.java`、`importer/ExcelTimetableReaderTest.java`、`importer/TimetableImportParserTest.java`、`rule/CourseQueryTest.java` |
| 未跟踪·生产 | `data/DamagedCourseCopy.java`、`reminder/ReminderRestoreReceiver.java` |
| 未跟踪·测试 | `data/DamagedCourseCopyTest.java`、`reminder/ReminderRestoreTest.java` |
| 未跟踪·文档/工具 | `AGENT_HANDOFF.md`、`.workbuddy-ai/` |

这些改动实现了 §9.1 / §9.2 / §9.5 / §9.6 四项，并新增了课程搜索的规则层（详见 §15）。
**本轮（重新接管）只读核查，未 stash、未 commit、未 reset、未 clean、未改动上述任何文件。**

---

## 3. 实际目录结构与关键类职责

```
app/src/main/java/com/weihe/schedule/
├── (根包) 9 个 Activity，用相对名在 Manifest 注册
│   MainActivity            「我的课表」LAUNCHER，当天课程列表 + 周/日切换 + 周选择器
│   WeekGridActivity        「课表总览」周网格/学期网格，冻结表头+节次列，自绘滚动同步
│   CourseDetailActivity    「课程详情」按 id 查课，只读展示 + 编辑入口
│   EditCourseActivity      「编辑/添加课程」表单 + 冲突确认 + 删除
│   SemesterListActivity    「学期」列表：切换/新增/复制课程/改名/删除
│   SettingsActivity        「设置」导入、备份导出、恢复预览入口、学期、开学日、作息表、上课提醒、关于
│   ScheduleListActivity    「作息时间列表」
│   EditScheduleActivity    「编辑/新建作息表」15×2 时间输入 + 地点关键词
│   BackupPreviewActivity   「恢复预览」只读预览 + 确认恢复
├── ui/        UiKit（组件工厂）、UiTokens（间距/圆角/字阶/控件尺寸）、Insets（系统栏）
├── data/      CourseRepository（唯一 SharedPreferences 适配）、CourseList（列表语义）、
│              MiniJson（手写 JSON，零 Android）、SemesterJson、BellScheduleJson、
│              BackupCodec（备份格式 v1）、BackupPreview、RestoreReport
├── rule/      CourseQuery、CourseConflicts、CourseFormValidator、WeekGridPlan、
│              BellScheduleEditor、Reminder、ReminderPlan、CourseDetail
├── model/     Course、Semester、ScheduleUtils、BellSchedule、BellSchedules
├── importer/  ExcelTimetableReader（jxl，只读 .xls）→ TimetableImportParser → TimetableGrid
│              + TimetableImportException
├── design/    Palette（颜色角色 + 9 个课程色）、Colors（WCAG 对比度算术）
└── reminder/  ReminderSettings（独立 store）、ReminderScheduler（唯一注册/取消点）、
               ReminderSchedule（slot 标识 + 账本编解码）、ReminderReceiver、ReminderNotifications

app/src/test/java/com/weihe/schedule/     27 个测试类，与生产包一一对应
tools/    IconForge/IconProbe/PaletteCheck/Pixel(.java) + dump-ui.ps1/tap-ui.ps1（不打包进 APK）
```

**分层与依赖方向**（全部单向、无环，已按 import 核对）：

```
根包(Activity) → .ui / .data / .rule / .importer / .design / .reminder / .model
.reminder → .data .model .rule        .data → .model .design
.rule → .model                        .importer → .model .design
.ui → .design .model                  .design → .model      .model → 叶子
```

**测试缝（必须保留）**：`CourseRepository.useStoreForNextInstance(SharedPreferences)` 是**包级私有**静态方法，
配合 `private static final ThreadLocal<SharedPreferences> pendingPreferences`，
让 `CourseRepository(Context)` 在 `context == null` 时消费替身。这是 `CourseRepositoryTest` 能跑到**真实构造函数（含迁移逻辑）**的唯一手段；
因此 `CourseRepositoryTest` / `FakePreferences` 必须与 `CourseRepository` 同包（`app/src/test/.../data/`）。
`WeekGridActivity.dayColumnRows` / `lastPeriodInUse` 同为包级静态方法，故 `WeekGridRowsTest` 留在根包。

---

## 4. 主要页面与数据流

### 4.1 导航

```
MainActivity (LAUNCHER)
 ├─ startActivity → SettingsActivity ──┬─→ SemesterListActivity（切换后 setResult(RESULT_SWITCHED)，靠 onResume 生效）
 │                                     ├─→ ScheduleListActivity ──startActivityForResult(2001)──→ EditScheduleActivity
 │                                     ├─startActivityForResult(3001)→ SAF 选 .xls → importTimetable(uri)
 │                                     ├─startActivityForResult(3002)→ SAF 建 .json → writeBackup(uri)
 │                                     └─startActivityForResult(3003)→ SAF 选 .json → BackupPreviewActivity
 ├─ startActivity → SemesterListActivity
 ├─ startActivity(EXTRA_WEEK) → WeekGridActivity ──startActivity(EXTRA_COURSE_ID, EXTRA_DAY)──→ EditCourseActivity
 └─ startActivity(EXTRA_COURSE_ID) → CourseDetailActivity ──startActivity(EXTRA_COURSE_ID)──→ EditCourseActivity
```

全部 9 个 Activity 在 Manifest 中 `screenOrientation="portrait"`；仅 `MainActivity` 为 `exported="true"`。
**没有** `onBackPressed` 重写、**没有** `onSaveInstanceState` 重写（见 §9.3）、**没有** Fragment / Navigation / Deep Link。

### 4.2 状态刷新约定

各页面在 `onResume()` 调 `repository.reload()` 后重绘——这是本项目处理"另一个界面改了数据"的统一手段
（`MainActivity:140`、`SettingsActivity:114`、`CourseDetailActivity:71`、`WeekGridActivity`、`SemesterListActivity`、`ScheduleListActivity`）。
`EditCourseActivity` / `EditScheduleActivity` **刻意不 reload**（避免覆盖用户正在输入的内容）。

`MainActivity.onResume()` 另外调用 `ReminderScheduler.sync(this)`（`MainActivity:152`），是提醒计划的**主要重建时机**。

### 4.3 首页渲染链路（真实调用）

```
repository.getCourses()
  → CourseQuery.onDay(courses, selectedWeek, selectedDay)   // 过滤 dayOfWeek + occursInWeek（周次窗口+单双周）+ 排序
  → CourseConflicts.conflictingIds(visible)                 // O(n²) 两两比对
  → createCourseCard(course, hasConflict)
      → repository.scheduleFor(course)                      // BellSchedules.resolve
      → course.timeRange（快照构建时已由 applyTimeRange 写入）
```

### 4.4 写入链路（新增/编辑课程）

```
EditCourseActivity.validateAndSave()
  → CourseFormValidator.check(...) → List<Problem>{field, reason}（纯数据，不含文案）
  → CourseConflicts.findConflicts(...) → 有交集则弹确认框
  → repository.upsertCourse(course)
      → CourseList.ensureId(courses, course, System.currentTimeMillis())  // id=0 时向前找空位
      → CourseList.upsert(courses, course)   // 存的是 copy()
      → saveCourses → writeCourses(coursesKey(), ...) → MiniJson.write → edit().apply()
      → cachedCourses = null
```

### 4.5 导入链路（唯一外部数据入口）

```
SettingsActivity「选择文件导入」→ SAF(3001) → onActivityResult → importTimetable(uri)
  → 后台线程 getContentResolver().openInputStream(uri)
  → ExcelTimetableReader.read(input)     // jxl 只支持 .xls(BIFF)；jxl 是 (column,row)，靠循环转置成 (row,column)
  → TimetableImportParser.parse(grid)    // 找表头行→7 个星期列→节次行→逐格解析；合并单元格=跨节；周次语法"1-4周/单周/双周"
  → confirmImport(parsed)                // 确认框：识别到 N 门课 / M 条安排 + 起始周 Spinner
  → TimetableImportParser.shiftWeeks(courses, sourceFirstWeek)   // 平移周次 + 奇数偏移翻转单双周 + 越界抛异常
  → CourseRepository.replaceCourses(shifted)   // 重新分配 id；清空 scheduleName
  → refresh()
```

**注意**：`MainActivity` 内另有一份**完全相同的导入代码**（`openImportPicker` / `importTimetable` / `confirmImport` / `showImportError`），
但它**没有任何调用点，且 `MainActivity` 未重写 `onActivityResult`** —— 属于死代码，见 §9.4。

### 4.6 备份/恢复链路

```
导出：SettingsActivity.exportBackup() → buildBackup() → BackupCodec.of(...)
      → 自检 BackupCodec.parse(BackupCodec.write(document)) → SAF(3002) → writeBackup(uri)
恢复：SettingsActivity.openBackupPicker() → SAF(3003) → BackupPreviewActivity（只读预览，不写）
      → 确认 → startRestore()（后台线程，重新读文件）
      → new CourseRepository(getApplicationContext()).restoreFrom(document)
      → 单个 Editor.commit() 原子替换 + 写 restore_snapshot + restore_pending 标记
      → verifyStored(expectation) 读回校验 → 通过则清标记；不通过则 rollBackTo(snapshot)
      → RestoreReport{RESTORED|ROLLED_BACK|UNCHANGED|NEEDS_ATTENTION}
中断自愈：任何一次 `new CourseRepository(...)` 的构造函数会调 resolvePendingRestore()，
      发现残留 restore_pending 就重新校验/回滚（CourseRepository:1190）
```

### 4.7 提醒链路

```
设置页开关/提前分钟 → ReminderSettings(独立 store "weihe_schedule_reminders")
MainActivity.onResume / SettingsActivity → ReminderScheduler.sync(context)
  → ReminderSettings.isEnabled？否→cancelAll；canPost？否→cancelAll（不烧电）
  → ReminderPlan.upcoming(courses, semester, schedules, today, 7 天, lead, now, 上限 10)
  → ReminderSchedule.entriesFor(reminders, zone)   // slot 1000..1009，唯一标识
  → AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)   // 刻意不用精确闹钟，Manifest 无 SCHEDULE_EXACT_ALARM
触发 → ReminderReceiver.onReceive
  → 按 slot+triggerAt 与账本比对（stale 直接丢弃）
  → 从**当前**课表重查该课（ReminderPlan.forCourseOnDay），查不到就静默
  → 已过开课时间 / 已通知过（markNotified）/ 无法发通知 → 静默
  → ReminderNotifications.post(...) → markNotified
```

---

## 5. 当前核心业务规则

### 5.1 周次与学期

- `ScheduleUtils.SEMESTER_WEEKS = 18`、`PERIODS_PER_DAY = 15`（**编译期常量**，全项目共用）。
- 学期开始日**始终归一化到周一**（`Semester` 构造函数调 `mondayOf`）。
- `ScheduleUtils.currentWeek(start, today)` 把结果 **clamp 到 [1,18]**（表头友好）。
- `ReminderPlan.rawWeekOf` **刻意不 clamp**，`isWithinSemester` 是闸门 —— 否则"学期前一周"会读成第 1 周并提前提醒（见 `ReminderPlan` 类注释）。
- 单双周：`Course.PARITY_ALL=0 / ODD=1 / EVEN=2`，判定 `week % 2`（`ScheduleUtils.occursInWeek`）。
- 导入平移周次时，**偏移为奇数则翻转单双周**（`TimetableImportParser.shiftWeeks`）。

### 5.2 作息表解析（唯一的解析入口）

```
BellSchedules.resolve(schedules, scheduleName, location)
  1. byName(scheduleName)                课程显式指定且该名字仍存在
  2. forLocation(location)               第一个关键词命中的表
  3. byName("B/C 楼作息")                学校表格里"其他教学场所"归属该组
  4. byName("教学楼作息")
  5. 内置 standard()
```

- 三张预置表：`教学楼作息`(a楼/f楼/j301)、`B/C 楼作息`(b楼/c楼/j302)、`D/E 楼作息`(d楼/e楼/j303)。
- 匹配算法 `BellSchedule.matchesLocation`：双方 squeeze（去空白/`-`/`·`/`_` + 转小写）后 `contains`；
  另加一条**只对"单个 ASCII 字母 + 楼"** 生效的补充规则（`教学楼 D305` 也命中 `d楼`），不会把 `j301` 误判成"J 楼"。
- 时间以**分钟数**存储，`MAX_MINUTE = 1799`（允许 24:00–29:59 的跨午夜晚课）；`label`/`parseLabel` 必须共用该上界，
  否则保存过的作息表会在下次读取时被"修复"掉。

### 5.3 冲突判定

同一星期 + 节次区间重叠 + **存在至少一个共同的教学周**（逐周代入单双周规则），
`CourseConflicts.overlaps` / `findConflicts`（编辑时排除自身）/ `conflictingIds`。

### 5.4 表单校验

`CourseFormValidator.check` 返回 `List<Problem>{Field, Reason}`，**只报数据不报文案**（文案由 Activity 决定）：
必填、非数字、越界（周 1–18 / 节 1–15）、起始周晚于结束周、起始节晚于结束节。

### 5.5 网格排布

`WeekGridPlan` 把一周课程排成 `Cluster`（时间上相连的一段）→ `Cell`（簇内一个起始节次）→ `Block`（一门课的落点）。
簇内用**顺序贪心分道**（可能比最优多一列，换来实现可测），同一簇内所有 cell 共享 `laneCount` 以保证竖直对齐。

### 5.6 提醒计算

`ReminderPlan` 是**纯函数库**：无 Android API、无 Alarm、无通知、**无时钟**（当前时间由调用方传入）。
无法确定开始时间时**拒绝而非猜测**，并给出 `Skipped` 原因（`OUTSIDE_SEMESTER` / `NO_SCHEDULES` / `INVALID_PERIOD` / `TRIGGER_IN_THE_PAST` / `UNSUPPORTED_LEAD_TIME`）。
提前分钟仅支持 **5 / 10 / 15**（`MIN/DEFAULT/MAX_LEAD_MINUTES`）；单次计划最多 **10** 条、前瞻 **7** 天。

---

## 6. 数据存储格式与兼容性约束

**存储介质**：`SharedPreferences`，文件 `weihe_schedule`（`Context.MODE_PRIVATE`）。**无数据库**。

| 键 | 类型 | 内容 | 归属 |
|---|---|---|---|
| `semesters` | String(JSON) | `[{"id":…,"start":"2026-08-31","name":""}]` | 全局 |
| `active_semester` | long | 当前学期 id | 全局 |
| `courses:<semesterId>` | String(JSON) | 该学期课程数组 | **按学期** |
| `semester_start:<semesterId>` | String | 第 1 周周一 `yyyy-MM-dd` | **按学期** |
| `bell_schedules` | String(JSON) | `[{"name","keywords":[],"starts":[],"ends":[]}]` | 全局 |
| `courses_backup` | String | 损坏载荷的原始副本（**只写不读**，见 §9.5） | 全局 |
| `restore_snapshot` | String(JSON) | 恢复前的完整快照（用于回滚） | 全局 |
| `restore_pending` | String(JSON) | 恢复期望值；仅存在于"commit 之后、校验之前" | 全局 |
| `courses` / `semester_start` | String | **旧版遗留键**，迁移后 `remove` | — |

**课程 JSON 字段**：`id, name, teacher, location, day, startPeriod, endPeriod, startWeek, endWeek, parity, color, scheduleName`。
`color` 以 **long** 写入（`color & 0xFFFFFFFFL`）以躲开 JSON 双精度；`timeRange` 是**派生字段，不落盘**。

**备份格式**：`BackupCodec.FORMAT = "weihe-schedule-backup"`，`FORMAT_VERSION = 1`，`MAX_SUPPORTED_VERSION = 1`，`MAX_BYTES = 2 MiB`（另有条目数上限）。
备份是**明文 JSON**（含教师姓名与上课地点），UI 已明确提示用户存放位置。

**不可随意改变的约束（回归风险）**：

1. `courses:<id>` / `semester_start:<id>` / `bell_schedules` / `semesters` 的**字段名与语义** —— 备份、恢复、迁移三处都依赖。
2. `color` 的 long 编码；`timeRange` 不落盘。
3. 作息时间上界 `MAX_MINUTE=1799` 与 `parseLabel` 的 hours≤29 必须一致。
4. `BellSchedules.resolve` 的 5 步顺序与 `BC_NAME` 兜底 —— 改顺序会改变"某门课几点上课"。
5. `CourseRepository` 的迁移逻辑：`migrateLegacyStorage` 只在 `!contains("semesters")` 时运行，并且**必须移除**旧键，
   否则新装会读到别人的数据（代码注释已说明）。
6. `isScopedKey` 是**正向白名单**：恢复只替换这些键，`courses_backup` 与未来的新键**必须保持不被清理**。
7. 提醒设置存在**独立 store** `weihe_schedule_reminders`（`enabled` / `lead_minutes` / `scheduled` / `notified_<courseId>`），
   刻意与课表数据分离，使恢复课表不影响提醒开关。

---

## 7. 测试、构建与 lint 的执行方法

```bash
cd "D:/vibe coding/Weihe/Weihe"

# 全部 JVM 单元测试（无需模拟器/真机）
./gradlew testDebugUnitTest                 # 常规
./gradlew testDebugUnitTest --rerun         # 强制真跑（缓存命中时会显示 UP-TO-DATE）

# Debug 编译
./gradlew assembleDebug                     # 产物 app/build/outputs/apk/debug/

# lint
./gradlew lintDebug                         # 报告 app/build/reports/lint-results-debug.{html,txt}
./gradlew lintDebug --rerun-tasks           # 完全重跑分析任务

# 测试报告（JUnit XML，可统计用例数）
app/build/test-results/testDebugUnitTest/*.xml
```

**测试组织**：27 个测试类与生产包一一对应；`FakePreferences` 是内存版 `SharedPreferences`，
支持 `commitCount()` 与 `failCommitNumber(n)`（用于断言"恢复是一次事务"和"commit 失败不得谎报成功"）。
`ExcelTimetableReaderTest` 在测试内用 jxl **现场生成**最小 `.xls`，不往仓库塞二进制夹具。

**本环境限制**：
- 无真机/模拟器介入，**Android 系统行为（通知、闹钟、权限、SAF）本轮未验证**，需设备验证。
- `tools/dump-ui.ps1` / `tap-ui.ps1` 需要 `adb` + 可写的 `%USERPROFILE%\.android`，本轮未运行。

---

## 8. 本次实际执行的验证结果（2026-10-11，本轮真实执行）

> **⚠️ 下方原始表格记录的是 12:24 那次运行（HEAD `61d9da5`、539 项）。**
> 后续一轮实现了 §9.1/§9.2/§9.5/§9.6 与搜索规则层，测试数量已增长。
> **以 §8.1 的最新实测为准**；原始表格保留作为历史记录。

### 8.1 重新接管后的实测（2026-10-11 后续，工作区含未提交改动）

| 检查 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `--rerun-tasks testDebugUnitTest` | **BUILD SUCCESSFUL**；**28 个测试类 / 620 项 / 620 通过 / 0 失败 / 0 错误 / 0 跳过** |
| Debug 编译 | `assembleDebug` | **BUILD SUCCESSFUL**（APK 829,541 字节量级） |
| lint | `lintDebug` | **BUILD SUCCESSFUL**；`app/build/reports/lint-results-debug.txt` = **`No issues found.`** |
| Git 工作区 | `git status --porcelain` | 12 修改 + 5 未跟踪（**本轮未改动其中任何一个**） |
| 设备 | `adb devices` | **无设备**（列表为空）→ 通知、广播、SAF、提醒触发等 Android 行为**本轮全部未验证** |

**测试数量对账（可复现的增长链）**：539（`61d9da5`）→ 553（+14 导入撤销）→ 569（+16 导入统计）
→ 588（+19 损坏副本）→ 604（+16 提醒恢复）→ **620**（+16 课程搜索规则层）。
每一步的增量都与对应测试类中的 `@Test` 数吻合，**无凭空增长**。

**按测试类分布（当前实测，共 620）**：

| 测试类 | 用例 | 测试类 | 用例 |
|---|---|---|---|
| `data.BackupCodecTest` | 64 | `rule.CourseConflictsTest` | 13 |
| `rule.ReminderPlanTest` | 62 | `importer.ExcelTimetableReaderTest` | 13 |
| `data.CourseRepositoryStorageTest` | 46 | `data.CourseListTest` | 12 |
| `importer.TimetableImportParserTest` | 42 | `data.BellScheduleJsonTest` | 11 |
| `data.CourseRepositoryTest` | 34 | `data.DamagedCourseCopyTest` | 11 |
| `rule.CourseQueryTest` | 31 | `importer.TimetableGridTest` | 10 |
| `reminder.ReminderScheduleTest` | 31 | `model.SemesterTest` | 10 |
| `data.BackupPreviewTest` | 27 | `data.MiniJsonTest` | 9 |
| `data.RestoreProtocolTest` | 26 | `reminder.ReminderRestoreTest` | 16 |
| `rule.CourseDetailTest` | 24 | `rule.WeekGridPlanTest` | 16 |
| `model.BellSchedulesTest` | 21 | `rule.CourseFormValidatorTest` | 15 |
| `model.ScheduleUtilsTest` | 19 | `design.PaletteContrastTest` | 14 |
| `model.BellScheduleTest` | 17 | `data.SemesterJsonTest` | 4 |
| `rule.BellScheduleEditorTest` | 17 | `WeekGridRowsTest` | 5 |

**原始记录（12:24 那次运行，HEAD `61d9da5`，539 项）**：

| 检查 | 命令 | 结果 | 证据 |
|---|---|---|---|
| 单元测试 | `./gradlew testDebugUnitTest --rerun` | **BUILD SUCCESSFUL**；**26 个测试类 / 539 项 / 0 失败 / 0 错误 / 0 跳过** | `app/build/test-results/testDebugUnitTest/*.xml`（逐文件解析统计） |
| Debug 编译 | `./gradlew assembleDebug` | **BUILD SUCCESSFUL**（34 tasks，源码未变故全部 UP-TO-DATE，未报错） | 控制台输出 |
| lint | `./gradlew lintDebug --rerun-tasks` | **BUILD SUCCESSFUL**；28 tasks 全部 executed；报告为 **`No issues found.`** | `app/build/reports/lint-results-debug.txt`（12:24 重写） |
| Git 工作区 | `git status --short --untracked-files=all` | 构建前后均为**空**（无新增/修改/未跟踪文件） | 控制台输出 |

**说明（避免误读）**：
- 首次执行 `testDebugUnitTest` 时任务为 `UP-TO-DATE`（未真正跑），因此**强制 `--rerun` 重新执行**后才记录上表结果。
- `assembleDebug` 的 34 个任务全部 `UP-TO-DATE`：源码自上次构建未变，Gradle 判定无需重做；这不是"跳过了编译"，而是输入未变。
- lint 首次 `--rerun` 仍命中分析缓存（`lintAnalyzeDebug UP-TO-DATE`），因此改用 **`--rerun-tasks`** 让 28 个任务全部重跑，报告时间戳更新为 12:24。

**按测试类分布（本轮实测）**：

| 测试类 | 用例 | 测试类 | 用例 |
|---|---|---|---|
| `data.BackupCodecTest` | 62 | `rule.CourseDetailTest` | 24 |
| `rule.ReminderPlanTest` | 62 | `rule.BellSchedulesTest` | 21 |
| `data.CourseRepositoryTest` | 34 | `model.ScheduleUtilsTest` | 19 |
| `reminder.ReminderScheduleTest` | 31 | `model.BellScheduleTest` | 17 |
| `importer.TimetableImportParserTest` | 28 | `rule.BellScheduleEditorTest` | 17 |
| `data.BackupPreviewTest` | 27 | `rule.WeekGridPlanTest` | 16 |
| `data.RestoreProtocolTest` | 26 | `rule.CourseFormValidatorTest` | 15 |
| `data.CourseRepositoryStorageTest` | 25 | `rule.CourseQueryTest` | 15 |
| `design.PaletteContrastTest` | 14 | `rule.CourseConflictsTest` | 13 |
| `data.CourseListTest` | 12 | `data.BellScheduleJsonTest` | 11 |
| `importer.ExcelTimetableReaderTest` | 12 | `importer.TimetableGridTest` | 10 |
| `model.SemesterTest` | 10 | `data.MiniJsonTest` | 9 |
| `WeekGridRowsTest` | 5 | `data.SemesterJsonTest` | 4 |

**测试覆盖的实际边界**：`.model` / `.rule` / `.data` / `.importer` / `.design` / `.reminder` 的**框架无关部分全部有 JVM 测试**；
9 个 Activity 与 `UiKit` / `Insets` / `CourseRepository` 的 Android 侧**没有任何自动化测试**（无 `androidTest`）。

---

## 9. 已确认的问题及证据

> 以下均为**从当前源码可直接确认**的问题，已区分"确定性缺陷"与"设计取舍/一致性隐患"。
> 未做设备验证的风险单独列在 §9.9。

### 9.1 导入覆盖不可撤销（数据安全 · 已确认 → **2026-10-11 后续已修复**）

> **状态更新**：已实现自动快照与一键撤销。新增存储键 `courses_previous:<semesterId>`
> （信封 JSON `{"present":bool,"raw":原始载荷}`，用于区分"键不存在"与"合法空课表 `[]"`）；
> `CourseRepository.replaceCourses` 改为把"新课程 + 快照"放进**同一次 `Editor.commit()`** 并返回 `boolean`；
> 新增 `hasPreviousCourses()` / `restorePreviousCourses()`；`SettingsActivity` 导入卡片内有撤销入口
> （仅在有快照时可见）。生命周期：`deleteSemester` 清该学期快照；`copyCourses` 清目标快照；
> `isScopedKey` 纳入 `courses_previous:`，使完整备份恢复会清空撤销状态并把它计入回滚材料。
> 原问题描述与证据保留如下，作为历史记录。

- **现象**：`SettingsActivity` 的"覆盖导入"会**直接替换**当前学期全部课程，没有自动快照、没有撤销入口。
- **证据**：`CourseRepository.replaceCourses`（`data/CourseRepository.java:593-607`）→ `saveCourses(courses)`；
  全仓库 grep `courses_previous` / `hasPreviousCourses` / `restorePreviousCourses` = **0 处**。
- **重要更正**：`NEXT_OPTIMIZATION_ROADMAP.md` §6.1 断言"全项目也没有导出可以把数据提前拿走" —— **该结论已过时**：
  `6875549` 已实现备份导出与恢复，用户可手动"先导出再导入"。但这是**手动且需要预知**的操作，
  且恢复是**整体替换**（不是撤销本次导入），所以风险被降低、**未被消除**。
- **影响**：一次误点导入 = 当前学期课表被替换。当前唯一的挽回手段是"之前恰好导出过备份"。

### 9.2 导入会静默丢弃行（正确性 · 已确认 → **2026-10-11 后续已修复**）

> **状态更新**：新增 `TimetableImportParser.parseWithReport(TimetableGrid)`（`parse` 委托给它，二者不会漂移）
> 与 `ExcelTimetableReader.readWithReport`，返回带 `Report` 的结果。四类计入异常：`UNATTRIBUTED_LINE`、
> `UNRECOGNISED_DETAIL`、`UNREADABLE_WEEKS`、`INCOMPLETE_FIELDS`；空白单元格、合并单元格非左上角、
> 课程头行等**正常忽略、不计数**。示例上限 `MAX_ISSUES = 5`（先每类一条再重复），计数不受上限影响。
> 真实教务文件实测 **0 异常**。`SettingsActivity.confirmImport(Report)` 显示"另有 K 处内容未能识别"+
> 最多 5 条"第 N 行 周X：原因"；完全无课程时改为 `showNothingRecognised` 对话框。
> **解析规则未放宽**：单空格按歧义处理（实测真实文件用两空格分隔，而单空格是地点名里的合法字符）。
> 原问题描述与证据保留如下，作为历史记录。

- **现象**：解析器有 **8 处 `continue;`**，无任何计数器；UI 只显示"识别到 N 门课程，共 M 条安排"。
  "导入一半"与"全部导入"在界面上**完全一样**。
- **证据**：`TimetableImportParser.java` 的 `continue;` 位于第 **60、64、220、225、228、232、236、292** 行；
  汇总文案在 `SettingsActivity.java:858`。`ExcelTimetableReaderTest` 现有 12 项测试，但**不覆盖"丢弃计数"**。
- **已知具体形态**：`parseCell` 用 `detail.group(2).trim().split("\\s{2,}")` 拆分"教师 地点"，
  即**单空格分隔的细节行会被整行丢弃**（第 232 行 `if (!detail.matches()) continue;` 与 236 行的周次不可解析分支同理）。
- **影响**：用户无法判断导入是否完整，且无法定位是文件问题还是解析器问题。

### 9.3 表单状态在进程回收后丢失（正确性 · 已确认）

- **证据**：全项目 grep `onSaveInstanceState` / `onRestoreInstanceState` / `setId(` = **0 处**（生产源码）。
- **受影响面**：`EditScheduleActivity` 持有 `EditText[15]` × 2 = **30 个时间输入框**（`EditScheduleActivity.java:54-55`）；
  `EditCourseActivity` 有 5 个命名输入 + 30 个周次 chip 的选中集合 + 单双周 + 颜色。
- **说明**：`screenOrientation="portrait"` 只挡住了旋转，**挡不住后台进程回收**（开发者选项"不保留活动"即可复现）。
  且因为 `setId` 也是 0 处，**框架自动的 View 状态保存同样失效**，没有任何兜底。

### 9.4 `MainActivity` 中的导入流程是死代码（可维护性 · 已确认）

- **证据**：`MainActivity.java:60` 定义 `REQUEST_IMPORT_TIMETABLE = 1001`，`:1212` 定义 `openImportPicker()`，
  但全项目 grep `openImportPicker` **只有定义、没有调用点**；`MainActivity` **未重写 `onActivityResult`**。
- 因此 `MainActivity` 的 `openImportPicker` / `importTimetable` / `confirmImport` / `showImportError`（约 `:1210-1346`，**约 137 行**）**不可达**。
  真正生效的导入在 `SettingsActivity`（请求码 3001）。
- **风险**：两份几乎相同的代码并存，未来若有人"修好了 MainActivity 那份"却改错了地方，会得到行为不一致的两个入口。
  （`PROJECT_ARCHITECTURE.md` 已指出该问题，本轮复核**依然存在**。）

### 9.5 `courses_backup`（损坏载荷副本）只写不读（数据安全 · 已确认 → **2026-10-11 后续已修复**）

> **状态更新**：新增 `data/DamagedCourseCopy`（值对象 + 流写入，四态 `ABSENT`/`EMPTY`/`PRESENT`/`UNREADABLE`，
> 只有 `PRESENT` 可导出，其余 `writeTo` 抛 `IOException`）与 `CourseRepository.damagedCopy()`
> （**只 `getString`、绝不解析**——载荷之所以在这里正因为它解析不了；槽里非文本时返回 `UNREADABLE`）。
> `SettingsActivity` 新增「无法识别的课程数据」卡片，**仅当副本存在时可见**，导出前有说明用途的确认框，
> 文件名 `weihe-damaged-courses-<时间戳>.txt`。`backupDamaged` 保留策略与 `isScopedKey` 均未改动；
> 设备实测导出文件与内部副本**逐字节一致**（178 字符 / 无 BOM）。
> 原问题描述与证据保留如下，作为历史记录。

- **证据**：唯一写入点 `CourseRepository.java:551`（`backupDamaged`）；全项目**没有读取点**（`grep` 确认，`app/src/test` 中亦仅用于断言写入）。
- **影响**：代码承诺"原始载荷被保留、可人工恢复"，但 App 内**没有任何路径能取回它**。
  用户看到的是"课程变成空的"，而救命的副本静静躺在 SharedPreferences 里。

### 9.6 重启后提醒不会自动恢复（功能缺口 · 已确认 → **2026-10-11 后续已修复**）

> **状态更新**：新增 `reminder/ReminderRestoreReceiver`，Manifest 增加 `RECEIVE_BOOT_COMPLETED` 权限与
> 一个 `exported="false"` 的 receiver，监听 `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` /
> `MY_PACKAGE_REPLACED`，`onReceive` 内**同步**调用 `ReminderScheduler.sync(app)`（不复制任何周次/作息逻辑，
> 不用 `goAsync()`）。`ACTION_DATE_CHANGED` **刻意不加**（不在平台豁免清单内，加了也收不到；跨日由
> "闹钟触发后重建 + 每次 onResume 重建"覆盖）。设备已逐项实测（开机 / 改时区 / 改时间 / 覆盖安装），
> 并将 4 次重建后的活跃闹钟数核到与账本一致（3 条）。详见 §15.4。
> **仍未实现**：`COURSE_REMINDER_PLAN.md` 提到的 `CourseRepository` 变更钩子 / `ReminderBinding`
> （全项目仍无 `ReminderBinding`），目前靠"各页面 `onResume` 兜底 + 设置页显式 `sync`"。
> 原问题描述与证据保留如下，作为历史记录。

- **证据**：全项目 grep `BOOT_COMPLETED` = **0 处**（`app/src/main`）；Manifest 中**无** `RECEIVE_BOOT_COMPLETED`、
  **无** `ReminderRestoreReceiver`（`AndroidManifest.xml` 只有 1 个 `ReminderReceiver`，`:73-75`）。
- **对比设计文档**：`COURSE_REMINDER_PLAN.md` 的 **P2 阶段**明确要求 `ReminderRestoreReceiver`（开机/改时间/改时区/升级）
  与 `RECEIVE_BOOT_COMPLETED`（§4 表 341/362 行、§11 P2）。**结论：提醒功能实际处于"P0+P1 已完成、P2 未实现"状态。**
- **实际行为**：`AlarmManager` 的闹钟在重启后**全部丢失**（系统行为），而当前唯一的重建时机是
  `MainActivity.onResume()`（`:152`）与设置页操作 —— 即**用户必须至少手动打开一次 App**，提醒才会重新排上。
- **同样缺失**：`COURSE_REMINDER_PLAN.md` P2 提到的 `CourseRepository` 变更钩子 / `ReminderBinding` 接线到 5 个 Activity
  （全项目无 `ReminderBinding`），目前靠"各页面 onResume 兜底 + 设置页显式 sync"。

### 9.7 `BackupPreviewActivity` 类注释与实现不一致（文档漂移 · 已确认）

- **证据**：类注释（`BackupPreviewActivity.java:37-39`）写"restore action is present but **disabled**, because the write itself is **not implemented yet**"；
  但同一文件 `:271` 的按钮点击 → `confirmRestore()`（`:287`）→ `startRestore()`（`:311`）→ `repository.restoreFrom(...)`（`:332`）**已完整接线**。
- **影响**：读者会被注释误导，以为恢复还没做。

### 9.8 `getBellSchedules()` 交出内部缓存列表（一致性隐患 · 已确认，暂无实际故障）

- **证据**：`CourseRepository.getBellSchedules()`（`:618-636`）在缓存命中时 `return snapshot;`（`:621`）**直接返回内部列表**；
  而 `getCourses()`（`:434-447`）与 `getSemesters()`（`:144-162`）都返回**拷贝**。
- **现状**：本轮核对了全部 12 个生产调用点，**没有任何调用方修改**返回的列表
  （`SettingsActivity.buildBackup` 甚至显式做了 `BellSchedules.copyOf` 并在注释里说明了原因），
  且 `BellSchedule` 本身不可变。因此**当前不是 bug**，但契约与另两个 getter 不一致，是易被踩的坑。

### 9.9 本轮未验证 / 待核实

| 项 | 状态 |
|---|---|
| 通知能否真实弹出、`setAndAllowWhileIdle` 的实际延迟 | **未验证**（无设备） |
| Android 13+ `POST_NOTIFICATIONS` 拒绝/授予后的行为 | **未验证**（代码路径已读：`SettingsActivity:606-640`） |
| SAF 导入/导出在选择器取消、只读位置、空间不足时的表现 | **未验证**（代码有分支，见 `SettingsActivity:129-141, 349-380`） |
| 恢复流程在真机"进程被杀"下的中断自愈（`restore_pending`） | **未验证**（JVM 侧有 `RestoreProtocolTest` 26 项覆盖） |
| `parseWeeks` 对乱序/重复周次区间（如 `"3-5,1-2周"`）是否会产生重叠课程 | **待核实**（区间未排序、未去重；本轮未构造用例验证） |
| 深色模式、横屏、大屏、无障碍的实际表现 | **未验证**（全项目无 `values-night`，锁定竖屏） |
| `tools/` 下的 UI 冒烟脚本 | **未运行**（需 `adb` 与可写的 `.android` 目录） |

---

## 10. 功能状态总表（依据当前源码、测试、Manifest、资源与 Git 核实）

| 功能 | 状态 | 证据 |
|---|---|---|
| Java 包结构整理 | **已实现并有证据** | 6 个 `refactor(structure)` 提交（`87df6dc`…`a159e7e`）；`ui/data/rule/model/importer/design/reminder` 目录与 import 方向已核对，无环 |
| 核心课表：课程增删改 | **已实现并有证据** | `EditCourseActivity` + `CourseRepository.upsertCourse/deleteCourse` + `CourseListTest`(12) |
| 核心课表：多学期 | **已实现并有证据** | `SemesterListActivity`、`semesters`/`active_semester`/按学期键；`SemesterTest`(10)、`SemesterJsonTest`(4) |
| 核心课表：单双周 | **已实现并有证据** | `Course.parity` + `ScheduleUtils.occursInWeek`；`ScheduleUtilsTest`(19) |
| 核心课表：自定义作息时间 | **已实现并有证据** | `EditScheduleActivity` + `BellScheduleEditor` + `BellScheduleJson`；`BellScheduleTest`(17)、`BellSchedulesTest`(21)、`BellScheduleEditorTest`(17) |
| 课表总览（周/学期网格） | **已实现并有证据** | `WeekGridActivity` + `WeekGridPlan`；`WeekGridPlanTest`(16)、`WeekGridRowsTest`(5) |
| 课程冲突检测 | **已实现并有证据** | `CourseConflicts` + 编辑时确认框；`CourseConflictsTest`(13) |
| 数据异常处理（损坏载荷） | **已实现并有证据** | 逐条跳过 + `courses_backup` 留档 + **`DamagedCourseCopy` 查询与安全导出**（`CourseRepository.damagedCopy()`、设置页卡片）；`DamagedCourseCopyTest`(11) |
| 数据导出（备份） | **已实现并有证据** | `BackupCodec` + `SettingsActivity.exportBackup` + SAF；`BackupCodecTest`(62)、`BackupPreviewTest`(27) |
| 数据恢复 | **已实现并有证据**（注释过期） | `BackupPreviewActivity` → `CourseRepository.restoreFrom`（原子 commit + 校验 + 回滚 + 中断自愈）；`RestoreProtocolTest`(26) |
| Excel 导入 | **已实现并有证据** | `ExcelTimetableReader`(jxl) → `TimetableImportParser` → `shiftWeeks` → `replaceCourses`；**`parseWithReport`/`readWithReport` 给出丢弃计数与四类原因**，`MAX_ISSUES=5`；`TimetableImportParserTest`(42)、`ExcelTimetableReaderTest`(13)、`TimetableGridTest`(10) |
| 导入撤销（自动快照） | **已实现并有证据** | `courses_previous:<semesterId>` 信封 JSON + `hasPreviousCourses()`/`restorePreviousCourses()` + 设置页撤销入口；快照与写入同一次 `commit`；`isScopedKey` 已纳入该前缀 |
| 自动化测试 | **已实现并有证据** | **28 类 / 620 项** JVM 测试本轮全绿（§8）；**无 androidTest，9 个 Activity 与 `UiKit`/`Insets` 零自动化** |
| 上课提醒（计算层） | **已实现并有证据** | `ReminderPlan`(62 项) + `Reminder`；纯函数、无时钟 |
| 上课提醒（通知与调度） | **已实现并有证据** | `ReminderScheduler`/`ReminderReceiver`/`ReminderNotifications`/`ReminderSettings`；`ReminderScheduleTest`(31)；Manifest `POST_NOTIFICATIONS` + receiver |
| 上课提醒（重启/改时区自愈） | **已实现并有证据** | `reminder/ReminderRestoreReceiver` + Manifest `RECEIVE_BOOT_COMPLETED` 与 4 个 action 的 receiver；`ReminderRestoreTest`(16)；设备已逐项实测 |
| 课程详情页 | **已实现并有证据** | `CourseDetailActivity` + `rule/CourseDetail`；`CourseDetailTest`(24)；按 id 查询、`onResume` 重读、缺失态可表达 |
| 课程搜索 | **部分实现（规则层已实现，UI 未接线）** | `rule/CourseQuery.search(courses, keyword)` 已实现并**无任何生产调用点**；`CourseQueryTest` 15→31（新增 16 项搜索用例）。详见 §15.5 |
| UI 设计系统 | **已实现并有证据** | `Palette`/`UiTokens`/`UiKit`/`Colors`；9 个 Activity 共引用 `UiKit` 288 处、`UiTokens` 253 处；**Activity 中硬编码颜色字面量为 0**；`PaletteContrastTest`(14) |
| UI 优化（字体缩放/自适应） | **部分实现** | 日格与时间块用 auto-size + 最小宽度（`MainActivity:938-947`、`createTimeBlock`），并有注释记录 130% 字体缩放的实测；**无设备复核** |
| 发布准备（图标/版本） | **部分实现** | 图标齐全（adaptive 三层 + 5 密度 legacy PNG，`tools/IconForge.java` 可再生成）；**versionCode/Name 仍为 1 / "1.0"**；`minifyEnabled false`、`proguard-rules.pro` 仅 1 行注释 |

---

## 11. 历史设计决策与需要避免的回归风险

### 11.1 文档与源码的冲突（本轮实测）

| 文档 | 文档记录 | 本轮实测 | 处理 |
|---|---|---|---|
| `PROJECT_ARCHITECTURE.md` / `PROJECT_ANALYSIS.md` | HEAD `21f20ec`；31 个生产文件 / 10,820 行；18 个测试文件 / 239 项；**7 个 Activity** | HEAD `61d9da5`；**44 个 / 15,344 行**；**27 个 / 539 项**；**9 个 Activity** | 以本文为准；两份文档保留作历史快照，**不要据此判断现状** |
| `NEXT_OPTIMIZATION_ROADMAP.md` | 基线 `efe5ace`；测试 239；"无导出能力" | 测试 **539**；**导出/恢复已实现**（`6875549`） | §五 P1-1（课表导出）、P1-2（`courses_backup` 入口**仍未做**）、P1-5（详情页**已做**）、P2-3（提醒**已做 P0+P1**）需重新评估 |
| `COURSE_REMINDER_PLAN.md` | 基线 `6875549`；测试 422；P0/P1/P2 分阶段 | P0/P1 **已实现**；**P2 未实现** | 该文档的 P0/P1 验收标准可作为已完成部分的复核依据；P2 仍是待办 |
| `PROJECT_ANALYSIS.md` §7.2 | `MainActivity` 导入无接收方 | **依然成立**（§9.4） | 保留 |

**处理原则**：不把不同文档的提交号/测试数合并成一个"确定结论"；上表已分别标注各自基线。**以源码为准。**

### 11.2 代码注释中记录的历史踩坑（必须保留、不得回归）

这些在源码注释里有明确记载，都是"改回去就会重犯"的坑：

1. **`MiniJson` 取代 `org.json`**：`org.json` 来自 `android.jar`，在 JVM 上是 null 桩，
   配合 `returnDefaultValues` 会静默返回 null —— 这正是 `unitTests.returnDefaultValues = false` 的原因。
2. **`Course.timeRange` 不落盘**：它是派生字段，快照构建时算一次（`applyTimeRange`），避免每张卡片重算。
3. **`Course.scheduleName` 存**名字**而非索引**：否则在编辑器里重排序作息表会静默改变已有课程的作息。
4. **`saveBellSchedule` 按名字寻址**（不是按编辑器打开时的 index）：曾因索引失效而覆盖了另一张表。
5. **`deleteBellSchedule` 拒绝删除最后一张**：因为空列表会被读成"从未存储过"并重新写入预置表，删除会"自己撤销"。
6. **`getCourses()` 每次返回深拷贝**：调用方（编辑器）会就地改对象，不能污染缓存。
7. **`CourseList.ensureId` 从 preferred 起向前找空位**：裸 `System.currentTimeMillis()` 会在同毫秒内碰撞。
8. **`reload()` 必须由 `onResume` 调用**：每个 Activity 各建一个 repository 且各自缓存，
   只靠写入方失效会让"总览已更新、首页仍是旧数据"。
9. **`BellSchedule.MAX_MINUTE=1799` 与 `parseLabel` hours≤29 必须一致**：否则保存过的作息表会在读回时被"修复"。
10. **`isPlaceholder` 同时比对"名字 + 时间"**：只比时间会误判"用户只改了上午"的作息表并丢弃其修改。
11. **`replacePlaceholderSchedules` 只替换从未被编辑过的表**：这是数据修正，不是数据覆盖。
12. **`ReminderPlan.rawWeekOf` 不 clamp**：`currentWeek` 的 clamp 会让学期前后各一天算出错误的提醒时刻。
13. **`ReminderReceiver` 从当前课表重建通知**，而不是用注册时保存的副本：过期闹钟因此静默失效而非播报错误课程。
14. **`ReminderReceiver` 的每一步都朝"什么都不做"失败**：整个 `onReceive` 被包住，广播抛异常会带走进程。
15. **刻意不申请 `SCHEDULE_EXACT_ALARM`**：targetSdk 34 下默认被拒，会强迫用户进系统设置；
   改用 `setAndAllowWhileIdle`，代价是可能晚几分钟，设置页已如实告知。
16. **`isScopedKey` 是正向白名单**：恢复只替换白名单键，`courses_backup` 与未来新键必须保留。
17. **`restoreFrom` 必须是单个 `Editor.commit()`**：跨 commit 的进程死亡会留下"新学期 + 旧课程"的无人可修状态。
18. **`UiTokens` 留在 `.ui` 而非 `.design`**：`UiTokens.px(Context,dp)` 委托 `UiKit.dp`，分开会让两包互相 import。
19. **无 XML 布局、无 Adapter/RecyclerView**：列表一律 `LinearLayout.addView`；`UiKit.PressFeedback` 用
   `dispatchTouchEvent` 做按压反馈而**不装 `OnTouchListener`**（历史上装了会吞掉子 View 的点击）。

### 11.3 明确的技术边界（除非有明确理由并获确认，否则不做）

保持 Java；不迁 Kotlin；不迁 Compose；不迁 Room；不引入大型框架/新依赖；
不改 `courses:<id>` 与 `semesters` 的存储格式；不为减少行数重写已稳定逻辑；不大范围重构已正常运行的课表布局。

---

## 12. 推荐的下一阶段任务

> **⚠️ 2026-10-11 后续更新**：下方 §12.2 表格中列出的候选，**前四项已经全部实现**：
>
> | 原优先级 | 任务 | 现状 |
> |---|---|---|
> | P0 | 导入安全网（自动快照 + 一键撤销） | ✅ 已实现（§9.1、§15.2） |
> | P0 | 导入丢弃行统计与提示 | ✅ 已实现（§9.2、§15.3） |
> | P1 | `courses_backup` 恢复/导出入口 | ✅ 已实现（§9.5、§15.4） |
> | P1 | 提醒重启自愈（`BOOT_COMPLETED`） | ✅ 已实现（§9.6、§15.5） |
> | P1 | 表单状态保存 | ❌ **仍未实现**（`onSaveInstanceState`/`setId(` 全项目 0 处） |
> | P2 | 清理 `MainActivity` 死导入代码 | ❌ 仍未实现（`MainActivity:1212` 起） |
> | P2 | 课程搜索 | 🟡 **规则层已实现、UI 未接线**（§15.5） |
>
> 因此 **旧 §12.2 的"建议先做的唯一任务"已作废**，新的建议见 **§12.1**。
> 原表格与原 §12.3 保留作为历史记录（其中的实施步骤与测试清单已被实际实现超越）。

### 12.1 新的建议：课程搜索的 UI 接线（`CourseQuery.search` 已就绪）

**为什么是它**
1. **规则层已完成且有 16 项测试**（`rule/CourseQuery.search`，`CourseQueryTest` 15→31），
   但**生产代码中零调用点**——已经投入的成本没有产生任何用户价值。这是当前"投入/收益"落差最大的一项。
2. 它是纯函数、无副作用、不改存储格式、不改任何既有规则，**回归风险低**。
3. 与 §9.3（表单状态）相比，它不需要触碰 `EditScheduleActivity` 那种 30 输入框的复杂表单。

**必须注意的既有约束**
- `search` **不修改**入参与 `Course` 对象，返回新列表且顺序稳定（day → period → id），UI 不得依赖此顺序做去重。
- 空关键词**匹配零条**（不是全部）——UI 需要有独立的"未输入"状态，不能把空结果当"没有课程"。
- 搜索**只能看到调用方传入的列表**，即**当前学期**；跨学期搜索需要调用方自己决定列表。
- 结果顺序已由 `BY_DAY_THEN_PERIOD_THEN_ID` 保证，**不要在 UI 里再排一次**（会与测试断言不一致）。
- 现有 `MainActivity` 顶部已有周切换 chip 行（`UiKit.chip` + `styleChip`），可复用其交互与视觉。

**完成标准**：新增测试（UI 层若无法自动化则至少补纯逻辑测试）；620 + N 项全绿；`assembleDebug` / `lintDebug` 干净；
不改课程/学期/作息存储格式；不引入新依赖。

### 12.2 候选任务排序（原先的评估，保留作历史记录）

| 优先级 | 任务 | 当前状态 | 相关文件 | 实际问题与证据 | 用户收益 | 难度/回归风险 | 最小修改范围 | 必须增加的测试 | 完成标准 |
|---|---|---|---|---|---|---|---|---|---|
| **P0** | **导入安全网：自动快照 + 一键撤销** | 未实现 | `data/CourseRepository.java:593`、`SettingsActivity.java:262-273` | 覆盖导入不可逆；无 `courses_previous` 键（§9.1） | 误操作可挽回，这是**唯一一条不可逆覆盖路径** | 低 / 低（不动课程格式） | `CourseRepository` 加 1 个键 + 2 个方法；`replaceCourses` 写盘前存快照；`SettingsActivity` 导入卡加一个动作 | 见 12.2 | 539 + 6 项全绿；`assembleDebug`/`lintDebug` 干净；撤销后 `courses:<id>` 与导入前**字符串相等** |
| **P0** | 导入丢弃行统计与提示 | 未实现 | `importer/TimetableImportParser.java`（8 处 `continue`）、`SettingsActivity.java:858` | "导入一半"与"全导入"界面无差别（§9.2） | 让不完整导入可被看见 | 低-中 / 低 | 新增 `Report` 类型 + `parseWithReport`（保留 `parse` 兼容）；确认框追加丢弃数与前几条原因 | 7 项（见历史文档 §6.2） | 现有 28 项导入测试断言一字不改；新增统计用例全绿 |
| **P1** | `courses_backup` 恢复入口 | 未实现 | `CourseRepository.java:551`、`SettingsActivity` | 副本只写不读（§9.5） | 把已存在的兜底数据变成可用的 | 低 / 低 | 新增 `hasDamagedBackup()` / 导出该副本的方法；设置页在存在时显示 | 3 项 | 副本能被取出且不改动 `courses:<id>` |
| **P1** | 表单状态保存（`onSaveInstanceState`） | 未实现 | `EditScheduleActivity.java:54-55`、`EditCourseActivity` | 全项目 0 处（§9.3） | 30 个输入框不再因进程回收归零 | 中 / 中 | 先抽纯类（Bundle ↔ 半成品文本），再在两个编辑页接线 | 5 项（见历史文档 §6.3） | 半成品文本 `"8.0"` / `"8："` 原样往返；不引入 ViewModel |
| **P1** | 提醒重启自愈（`BOOT_COMPLETED`） | 计划中 | Manifest、新增 `ReminderRestoreReceiver` | `BOOT_COMPLETED` 0 处（§9.6） | 重启后提醒自动恢复 | 中 / 中（新增 receiver + 权限，需设备验证） | Manifest 加 `RECEIVE_BOOT_COMPLETED` + receiver，内部只调 `sync()` | 接收器薄壳，逻辑已有测试 | 真机重启后提醒恢复（**需设备**） |
| **P2** | 清理 `MainActivity` 死导入代码 | — | `MainActivity.java:60, 1210-1346` | 约 137 行不可达（§9.4） | 消除双份实现的分叉风险 | 极低 / 极低 | 删除该方法块与常量 | 无需新增 | 编译/lint/测试全绿；`SettingsActivity` 导入不受影响 |
| **P2** | 课程搜索 | 计划中 | 新增 `CourseQuery.search` 纯函数 + UI | 无搜索实现 | 课程多时的可检索性 | 中 / 低 | 纯函数优先，UI 后置 | 纯函数用例 | 待单独立项 |
| **P2** | 深色模式 / 横屏 / 无障碍 | 计划中 | `Palette`（本轮实测 **53 个 `public static final int` 常量、312 处 `Palette.XXX` 引用**）、全部 Activity | `res/values-night` **不存在**、全部 Activity 锁定竖屏 | 体验与可达性 | **高** / **高** | 需先做原型与视觉回归手段 | — | 必须先有 UI 回归手段再启动 |

**不推荐**：同时开工多项、或把 UI 重构与数据模型修改混在一轮。

### 12.3 原建议：导入安全网（自动快照 + 一键撤销）—— **已实现，以下为历史记录**

**为什么是它**
1. 它是全 App **唯一一条"一次误操作 = 当前学期课表被永久替换"** 的路径（§9.1）；
2. 修改面**只涉及 2 个文件**，且**不触碰课程/学期的存储格式**，符合 §11.3 的边界；
3. 可以用现有 `FakePreferences`（含 `commitCount()` / `failCommitNumber()`）在 **JVM 上完整验证**，无需设备；
4. 它是 §9.2（丢弃行）的**前提**：先有可撤销，导入的失败模式才是可恢复的。

**具体实施步骤（最小可行）**

1. `data/CourseRepository` 新增键 `courses_previous:<semesterId>`，值与 `courses:<id>` 同构（课程数组 JSON 字符串），
   **不新增格式**；新增 `public boolean hasPreviousCourses()` 与 `public boolean restorePreviousCourses()`。
2. `replaceCourses`（`:593`）在 `saveCourses(courses)` **之前**，把 `preferences.getString(coursesKey(), null)`
   **原样字符串**写入 `courses_previous:<id>`（不做 JSON 往返，才能保证"逐字节相同"）。
   快照只保留最近一份，直接覆盖写。
3. `restorePreviousCourses()`：读快照 → **先把当前值也存进快照**（否则"撤销错了再撤销"会二次丢失）→
   写回 `courses:<id>` → 清空快照 → `reload()` → 返回是否成功。
4. `SettingsActivity.createImportCard`（`:262`）在 `hasPreviousCourses()` 为真时多显示一个动作，
   文案说明"只能撤销一次"与"将恢复为导入前的课表"；恢复后走现有 `refresh()`。
5. **必须处理的交互（否则会引入新问题）**：
   - `isScopedKey`（`:1313`）是正向白名单，`courses_previous:<id>` **默认不会被恢复流程清理**。
     若不处理，恢复备份后仍会看到一份"恢复前的课表"快照，撤销会回到错误状态。
     **建议**：把 `courses_previous:<id>` 纳入 `isScopedKey`，并在 `BackupCodec` 的导出侧确认它**不会**被写进备份
     （`BackupCodecTest:236` 已有"内部产物不得进入备份"的同类断言，需为其补一条）。
   - 删除学期（`deleteSemester`，`:289`）与 `copyCourses` 也要考虑该键的生命周期（至少不能残留到别的学期）。

**必须增加的测试**（`data/CourseRepositoryTest` / `CourseRepositoryStorageTest`，沿用 `FakePreferences` 与 `repositoryFor(...)` 夹具）

| # | 测试 | 断言 |
|---|---|---|
| 1 | `importingKeepsThePreviousTimetableRecoverable` | 3 门课 → `replaceCourses` 换成 1 门 → `hasPreviousCourses()` 为真 → 恢复后 3 门课逐字段回来 |
| 2 | `theRecoveredPayloadIsByteIdenticalToWhatWasStored` | 恢复后 `preferences.getString("courses:<id>")` 与导入前**字符串相等** |
| 3 | `onlyTheMostRecentImportCanBeUndone` | 连续导入两次 → 撤销一次成功 → 再撤销返回 false |
| 4 | `theSnapshotBelongsToOneSemester` | 学期 A 导入 → 切到 B → B 看不到 A 的快照，且恢复失败时**不改动 B 的数据** |
| 5 | `afailedCommitDoesNotClaimSuccess` | 用 `failCommitNumber` 让快照写入失败 → `replaceCourses` 不得留下"已导入"的状态 |
| 6 | `aRestoreDoesNotLeaveAStaleUndoSnapshot` | 执行一次 `restoreFrom(document)` 后，`hasPreviousCourses()` 为 false |

**完成标准**：539 + 6 = **545** 项测试全绿（`--rerun` 真跑）；`assembleDebug` 与 `lintDebug`（`--rerun-tasks`）干净；
`git status` 只出现预期的源码与测试改动；**不新增任何依赖、不改动任何存储格式字段名**。

---

## 13. 后续开发工作规范（本项目约定）

1. **一次只处理一个明确目标**：每轮改动有清楚的目标、范围与验收条件；不把 UI 重构、数据模型修改、新功能、依赖升级混在一轮。
2. **修改前先定位调用链**：确认方法定义处、全部调用点、数据读写去向、依赖该行为的测试、受影响的页面。
   本项目大量使用包级私有静态测试缝（`CourseRepository.useStoreForNextInstance`、`WeekGridActivity.dayColumnRows`），
   改动这些方法的签名会直接破坏测试的可见性。
3. **遵守 §11.3 的技术边界**；复用已有的 `.rule` / `.data` / `.ui` 组件，不创建功能重复的实现。
4. **修改后必须验证**：新增/修改的测试 → 全部 JVM 单元测试（`--rerun`）→ `assembleDebug` → `lintDebug` → `git diff` 与工作区检查。
   需要 Android 系统行为时，明确安排设备验证步骤（本环境无法执行）。
   **不得把历史执行结果当作本轮结果**；**不得把未执行的检查报告为通过**。
5. **严格保护用户工作**：不执行破坏性 Git 命令，不覆盖未提交修改，不擅自 stash/commit/push。
   提交 Git **仅在用户明确要求或授权时**进行。
6. **发现问题不掩盖**：先说明现象 → 指出文件/调用链 → 尽可能复现 → 分析根因与影响 → 给出最小修复方案。
   不得通过删除测试、弱化断言或吞掉异常来制造通过结果。
7. **不要用改注释来"修"文档漂移**：像 §9.7 这类注释与实现不一致，应当**改注释使其与代码一致**，
   或按实际状态补齐功能；不得反向把注释当作设计意图去改代码。

---

## 14. 本轮边界声明

- **允许并已做**：只读检查 Git/源码/测试/Manifest/资源/文档；运行现有测试、Debug 编译与 lint；新增本文档 `AGENT_HANDOFF.md`。
- **未做**：未修改任何生产业务代码、测试代码、Gradle 配置或依赖；未新增依赖；未重构；
  未实现任何新功能；未 stash / commit / push / reset / clean；未运行 `git checkout --` 等覆盖性命令。
- 交接完成后**停下等待用户确认下一项任务**，再开始修改业务代码。

---

## 15. 重新接管复核（2026-10-11 后续，只读）

> 前任 Agent 的工具不可用后重新接管。**本轮未修改任何生产代码/测试/配置**，只读核查 + 更新本文档。

### 15.1 基准如何确定（不猜测）

旧会话最后熟悉的 HEAD 是 `6875549`。**没有把它当作基准**，而是从当前 Git 历史重建时间线：

```
5122548 第一次提交
097c729 21f20ec 1213ef7              （早期迭代）
87df6dc 15ad8d4 0523e5d 0d7b930     包结构整理（6 个 refactor(structure)）
        a159e7e efe5ace
9b40146 → 6875549                    备份与恢复 + SettingsActivity 重写
        ↑ 这一段之后旧会话记忆中断
61d9da5 update (2026-10-11 12:08)    上课提醒 + 课程详情页（旧会话的最后两轮，已入库）
        ↓ 以下为前任 Agent 的工作，**尚未提交**
12:27   AGENT_HANDOFF.md 建立
13:07   ExcelTimetableReader / TimetableImportParser 改动
13:19   DamagedCourseCopy 新增
13:20   CourseRepository 改动
13:21   SettingsActivity 改动
13:33   ReminderRestoreReceiver 新增
13:35   ReminderRestoreTest 新增
13:52   .workbuddy-ai/memory/2026-10-11.md 最后写入（记到第 4 项）
14:08   rule/CourseQuery.java 改动  ← **超出该日志记录范围**
```

**结论**：`61d9da5` 之后再无提交；工作区内的 12 改 + 5 增就是前任 Agent 的全部产出。
**`CourseQuery.search`（14:08）晚于工作日志的最后一次写入（13:52），未被该日志记录** —— 这是本次复核新发现的一项。

### 15.2 前任 Agent 实现的四项（均已核对源码，不是照抄日志）

| 项 | 关键证据（已逐个打开确认） |
|---|---|
| 导入撤销 | `CourseRepository:63` `KEY_COURSES_PREVIOUS = "courses_previous"`；`:448 previousCoursesKeyFor`；`:693 hasPreviousCourses()`；`:716 restorePreviousCourses()`；`:272`/`:328` 的 `remove(previousCoursesKeyFor(...))`（`copyCourses` / `deleteSemester` 生命周期）；`SettingsActivity:336/352` 两个调用点 |
| 导入统计 | `TimetableImportParser:39 MAX_ISSUES = 5`；`:66 parseWithReport`；`:46 parse` 委托；`:236 Report`；`ExcelTimetableReader:20 read` 委托 `:30 readWithReport`；`SettingsActivity:1077` 调用点 |
| 损坏副本 | `data/DamagedCourseCopy`：`:32 enum State`（4 态）、`:59/:64/:69/:78` 四个工厂、`:95 isExportable()`、`:128 writeTo(OutputStream)`；`CourseRepository:591 damagedCopy()`；`SettingsActivity` 35 处引用 + `:… REQUEST_EXPORT_DAMAGED = 3005` |
| 提醒恢复 | `reminder/ReminderRestoreReceiver`；Manifest 新增 `RECEIVE_BOOT_COMPLETED` + `exported="false"` 的 receiver，intent-filter 含 `BOOT_COMPLETED`/`TIME_SET`/`TIMEZONE_CHANGED`/`MY_PACKAGE_REPLACED` 四项 |

**导入链路实测证据（日志记载，本轮未复跑设备）**：真实教务文件 `school.xls` = 14 门 / 23 条 / **0 异常**。

### 15.3 未因日志记载就采信的事项

- 日志称"604 项"是它最后一轮的实测；**本轮重新跑出 620**，差异 16 项恰好等于 `CourseQueryTest` 新增的搜索用例数，
  且各测试类的 `@Test` 计数与 JUnit XML 逐类吻合 → 数量对账成立，**未沿用历史数字**。
- 日志称单空格分隔"按歧义处理、记警告"；已核对 `TimetableImportParser` 仍在用 `\s{2,}` 拆分，**说法与代码一致**。

### 15.4 包结构与依赖方向（本轮实测）

```
model      → （无内部依赖）        ← 叶子
design     → model
rule       → model
ui         → design
data       → design, model         ← 唯一"稍显意外"的一条：CourseRepository:1241 用 Palette.COURSE_COLORS
                                      （新课程的默认配色轮转），存储层因此依赖了设计系统
importer   → design, model
reminder   → data, model, rule
(根包 9 Activity) → data, design, importer, model, reminder, rule, ui
```

**无环**。生产 46 个类：根包 9（6,249 行）、data 9、rule 8、reminder 6、model 5、importer 4、ui 3、design 2。

### 15.5 本次复核新发现：`CourseQuery.search` 已实现但无 UI

- `rule/CourseQuery.search(List<Course>, String)` 已实现：三字段（名称/教师/地点）大小写不敏感子串匹配、
  `Locale.ROOT` 小写化、空关键词**匹配零条**、结果按 day → period → **id** 全序稳定、不修改入参。
- **`grep 'CourseQuery.search'` 在生产代码中 = 0 处调用**；没有任何搜索入口、Activity 或设置项。
- 因此 §10 的"课程搜索"从"**计划中但尚未实现**"更正为"**部分实现（规则层已实现，UI 未接线）**"。
- 这也使 §12 的推荐发生变化，见 **§12.1**。

### 15.6 本轮实测基线

见 **§8.1**：**28 类 / 620 项 / 0 失败 / 0 错误 / 0 跳过**；`assembleDebug` 成功；`lintDebug` = `No issues found.`。
**`adb devices` 为空 → 本轮没有任何 Android 系统行为被验证**（通知、广播、SAF、提醒触发、开机恢复均未复跑）。

### 15.7 前任 Agent 修复后仍然存在的问题（本轮逐条复核，**均仍存在**）

| 原编号 | 问题 | 本轮复核证据 |
|---|---|---|
| §9.3 | 表单状态进程回收后丢失 | `grep onSaveInstanceState` = **0**、`grep 'setId('` = **0**（`app/src` 全量） |
| §9.4 | `MainActivity` 死导入代码 | `MainActivity:60 REQUEST_IMPORT_TIMETABLE`、`:1212 openImportPicker`、`:1220 startActivityForResult` 仍在；`:1325-1327` 的 `replaceCourses` 调用**忽略新返回的 boolean**（不可达，无用户影响） |
| §9.7 | `BackupPreviewActivity` 类注释漂移 | `:38` 仍写 "the write itself is not implemented yet"，而实现早已接线 |
| §9.8 | `getBellSchedules()` 交出内部缓存 | `CourseRepository:797-815`：`:800 return snapshot;`（即 `cachedSchedules` 本身）、`:807/:811/:814 return schedules;` 均为内部列表；变量名 `snapshot` 有误导性，并非拷贝 |


---

# 归档：2026-10-12 的 AGENT_HANDOFF.md 全文（历史记录）

> 以下整段是**当时**的当前状态交接文档，现已被重写为更短的版本。
> 其中 **E0–E4 与 K 节是逐轮开发日志**，记录了滚轮选择器、每节时长与动态节次、
> 自定义默认作息表、导入开学日期与学期总周数、课程配色（含颜色选择器布局恢复）等
> 各轮工作的根因、修法与当轮测试结果 —— **这些细节只在这里保留**，请当作背景参考。
>
> ⚠️ 其中的「当前状态 / 完成度 / 未提交修改」判断**均已过期**。
> **当前状态一律以 `AGENT_HANDOFF.md` 为准。**
# AGENT_HANDOFF — 拾课（WeiheSchedule）当前状态交接

## A. 文档信息

| 项 | 值 |
|---|---|
| 更新时间 | 2026-10-12 |
| 用途 | **本文件是项目当前状态的唯一有效交接文档** |
| 核实方式 | 逐项对照当前源代码、`git` 状态与实际测试结果重新核对，未照抄历史文档 |
| 长期规范 | [`AGENTS.md`](AGENTS.md) |
| 历史版本 | [`AGENT_HANDOFF_ARCHIVE.md`](AGENT_HANDOFF_ARCHIVE.md)（**已过期，其中的完成度判断不可信**） |

> ⚠️ 阅读顺序：先 `AGENTS.md`（怎么写代码），再本文件（现在是什么状态），
> 最后**打开代码确认**。本文件描述的完成度均有依据，但代码永远优先。

---

## B. 当前项目状态

| 项 | 值 |
|---|---|
| 分支 | `main` |
| HEAD | `bdd7d01`（`update`），**与 `origin/main` 相同**（已推送） |
| **工作区** | **有未提交修改**：30 个已修改、8 个未跟踪（**多轮累积，均未提交**） |
| 未提交修改 | 四个连续任务：**①滚轮循环边界 ②自定义课时长+动态节次 ③自定义默认作息表 ④Excel 导入改为「第 1 周固定对应」+ 开学日期选择**（见 E0 / E1 / E2 / E3），尚未 `git commit` |

**核实命令与输出**（本次交接实际执行）：

```
git branch --show-current     → main
git log -5 --oneline          → bdd7d01 / 61d9da5 / 6875549 / 9b40146 / efe5ace
git status --porcelain        → 30 个 M、8 个 ??（见下）
```

### 本轮未提交改动清单

**未跟踪（`??`，8 个）**

```
AGENTS.md                                       （长期规范，未纳入版本库）
AGENT_HANDOFF_ARCHIVE.md                        （历史交接归档）
.workbuddy-ai/memory/2026-10-12.md              （工作日志）
app/src/main/java/com/weihe/schedule/ui/WheelColumnView.java        ← 滚轮修复（E0），未改
app/src/test/java/com/weihe/schedule/ui/WheelColumnViewTest.java    ← 滚轮修复（E0），未改
app/src/test/java/com/weihe/schedule/rule/PeriodLengthAndCountTest.java     ← 课时长/节次（E1）
app/src/test/java/com/weihe/schedule/data/NonDefaultScheduleFlowTest.java   ← 课时长/节次（E1）
app/src/test/java/com/weihe/schedule/importer/TimetableImportRangeTest.java ← 课时长/节次（E1）
app/src/test/java/com/weihe/schedule/data/DefaultScheduleTest.java          ← 默认作息表（E2）
app/src/test/java/com/weihe/schedule/data/ImportStartDateTest.java          ← 导入日期（E3，本轮）
```

**已修改（`M`，30 个）**

**E3（本轮，导入日期）**：改动落在**已有的** 4 个文件上 ——
`SettingsActivity`、`MainActivity`（两个导入弹窗重写）、
`data/CourseRepository`（新增 `replaceCoursesAt`）、
`importer/TimetableImportParser`（**删除** `shiftWeeks` / `flipParity`）、
`AGENT_HANDOFF.md`（本文件）。**无新增生产文件。**

**E2（默认作息表）生产代码 15 个**：
`MainActivity`、`ScheduleListActivity`、`SettingsActivity`、`CourseDetailActivity`、
`CourseSearchActivity`、`data/CourseRepository`、`data/BackupCodec`、`data/BellScheduleJson`、
`model/BellSchedule`、`model/BellSchedules`、`rule/CourseDetail`、`rule/ReminderPlan`、
`reminder/ReminderScheduler`、`reminder/ReminderReceiver`、`AGENT_HANDOFF.md`（本文件）。

**E2 测试 3 个**：`data/DefaultScheduleTest`（**新增，27 项**）、
`model/BellSchedulesTest`、`data/BellScheduleJsonTest`。

**E0 / E1（前两轮，仍未提交）**：`EditCourseActivity`、`EditScheduleActivity`、`WeekGridActivity`、
`importer/TimetableImportParser`、`model/ScheduleUtils`、`rule/BellScheduleEditor`、
`rule/CourseFormValidator`、`rule/ScheduleDraft`、`ui/Pickers`、`WeekGridRowsTest`、
`BackupCodecTest`、`ScheduleDraftStorageTest`、`BellScheduleEditorTest`、`BellScheduleEndTest`。

**E0 / E1（前两轮，仍未提交）**：`EditCourseActivity`、`EditScheduleActivity`、`WeekGridActivity`、
`importer/TimetableImportParser`、`model/ScheduleUtils`、`rule/BellScheduleEditor`、
`rule/CourseFormValidator`、`rule/ScheduleDraft`、`ui/Pickers`、`WeekGridRowsTest`、
`BackupCodecTest`、`ScheduleDraftStorageTest`、`BellScheduleEditorTest`、`BellScheduleEndTest`。

> ⚠️ **保护声明**：以上未提交改动**全部是本次连续对话产出的工作**，
> 不属于他人的改动。**不要 `git checkout` / `git reset` / `git clean` / 擅自 `commit`。**

---

## C. 项目技术架构

### 技术栈（以 `app/build.gradle` / `build.gradle` 实际配置为准）

| 项 | 值 |
|---|---|
| 语言 | **纯 Java**（`app/src` 下 `.kt` 文件数 = **0**） |
| UI | Android Views，**全部在 Java 中构建** —— `res/layout` 目录**不存在** |
| AGP / Gradle | **8.13.0** / **8.13**（`gradlew.bat` 与 `.gradle-local/bin/gradle.bat` 均可用） |
| JDK | 主机 **20.0.1**；`sourceCompatibility`/`targetCompatibility` = **Java 17** |
| SDK | `compileSdk` **34** / `targetSdk` **34** / `minSdk` **26** |
| 包名 | `com.weihe.schedule`（`versionName 1.0`，`versionCode 1`） |
| 依赖 | **仅两个**：`files('libs/jxl-2.6.12.jar')`（Excel 读取）、`junit:junit:4.13.2`（测试） |
| 未使用 | AndroidX / Material / Kotlin / Compose / Room / 网络库 / 任何第三方 UI 依赖 |

`res/` 内容：4 个 values 文件（`colors/strings/themes/animations`）、13 个 drawable、**无 layout**。

### 包结构

| 包 | 类数 | 职责 |
|---|---|---|
| （根）`com.weihe.schedule` | 10 个 Activity | 页面与导航 |
| `model/` | 5 | `Course`、`Semester`、`BellSchedule`、`BellSchedules`、`ScheduleUtils` |
| `data/` | 9 | `CourseRepository`（**唯一数据入口**）、`MiniJson`、`BackupCodec`、`SemesterJson`、`DamagedCourseCopy` 等 |
| `rule/` | 15 | 纯逻辑：查询、冲突、校验、草稿、提醒计划、周次、学期命名、选择器日历 |
| `reminder/` | 6 | 提醒计算、调度、广播接收 |
| `importer/` | 4 | Excel 读取与课表解析 |
| `ui/` | 4 | `UiKit`(1435 行)、`UiTokens`、`Pickers`(486 行)、`Insets` |
| `design/` | 2 | `Palette` 等 |

### Activity（根包，行数为当前实际值）

```
MainActivity            1438   首页：周次/星期导航、当天课程、导入
SettingsActivity        1349   设置：学期管理 / 起始日期 / 上课提醒 / 上下课时间 / 课表数据
EditCourseActivity      1129   新增·编辑·复制课程
WeekGridActivity         950   课表总览（周网格）
EditScheduleActivity     880   作息时间表编辑（课时长 + 动态节次数量）
BackupPreviewActivity    478   备份预览与恢复
SemesterListActivity     403   学期列表、新建、切换、删除
CourseDetailActivity     375   课程详情
CourseSearchActivity     356   课程搜索
ScheduleListActivity     256   作息表列表
```

### 数据保存方式（**不要改动格式**）

| 项 | 值 |
|---|---|
| 主存储 | `SharedPreferences("weihe_schedule")` |
| 唯一访问点 | `data/CourseRepository.java` |
| 提醒存储 | `SharedPreferences("weihe_schedule_reminders")`（`reminder/ReminderSettings.java`） |
| 序列化 | 手写 `data/MiniJson` |

**键一览**（`CourseRepository`）：`courses`、`semester_start`、`semesters`、`active_semester`、
`bell_schedules`、`courses_previous`、`courses_backup`、`restore_snapshot`、`restore_pending`；
另有**按学期分键**：`courses:<semesterId>`、`semester_start:<semesterId>`。

**时间在存储中的表示**：作息表的 `starts`/`ends` 是**自午夜起的整数分钟**（`int[]`，如 `495` = 08:15），
**不是** `HH:mm` 字符串。晚课以 `24:xx` 表示（上界 `MAX_MINUTE = 29:59`）。
课程与学期用 `yyyy-MM-dd` 字符串表示日期。

**★ 2026-10-12 本轮新增字段（向后兼容）**：作息表 JSON 顶层新增**可选**键
`"durationMinutes"`（int，分钟，1–180）。**缺失即 40** —— 旧数据的 `bell_schedules` 载荷
一个字都不改也能正常读入，且**不会被回写**（设备实测确认：线上存储仍无该键）。
**节次数量不新增字段**，沿用 `starts` 数组长度。

### 重要业务约束

| 常量 | 值 | 位置 |
|---|---|---|
| `SEMESTER_WEEKS` | 18 | `model/ScheduleUtils` |
| `PERIODS_PER_DAY` / `BellSchedule.PERIODS` | 15 | 同上 / `model/BellSchedule`。**★ 已降级为「默认值」语义**：仅用于新建默认作息表、设置页空列表兜底、以及 `@Deprecated` 的 `CourseFormValidator` 5 参重载。**不再是上限** |
| `ScheduleUtils.MAX_PERIODS` | **30** | **新的节次上限**，所有范围校验/几何行数/导入与备份检查都用它 |
| `BellSchedule.MIN_PERIODS` | 1 | 节次下限（一天不能变成 0 节） |
| `BellSchedule.DEFAULT_PERIOD_MINUTES` | **40** | 每节课的**默认**时长（旧数据缺失时的读入值）。`PERIOD_MINUTES` 保留为别名 |
| `BellSchedule.MIN_PERIOD_MINUTES` / `MAX_PERIOD_MINUTES` | **1 / 180** | 每节课时长的合法区间；越界不抛异常，**按 40 修复** |
| `BellSchedule.MAX_MINUTE` | `30*60-1`（29:59） | 时刻上界（跨零点仍不支持） |
| `Course.PARITY_ALL/ODD/EVEN` | 0 / 1 / 2 | 每周 / 单周 / 双周 |
| `Course.DEFAULT_COLOR` | `0xFF3C6A8E` | |

**地点匹配规则**（`BellSchedule.matchesLocation`）：先归一化（小写 + 去掉空白/`-`/`·`/`_`）
→ 子串包含；此外「单个 ASCII 字母 + 楼」型关键字（如 `d楼`）还接受「该字母紧跟数字」的写法。
解析优先级：**课程指定名 → 关键字命中（列表顺序，先命中者胜）→ B/C 楼 → 标准作息**。

---

## D. 功能完成清单

图例：✅ 已实现且有测试/设备证据 ｜ ⚠️ 已实现但验证不充分 ｜ 🟡 部分实现 ｜ ❌ 未实现

| 功能 | 状态 | 证据 |
|---|---|---|
| 首页：周次/星期导航、当天课程、回到今天 | ✅ | `TodayTargetTest`(18)；设备实测 |
| 课表总览（周网格） | ⚠️ | 代码完整；本次会话未回归验证 |
| 课程搜索 | ✅ | `CourseSearchFlowTest`、`CourseSearchSummaryTest`；设备实测（回车/清空焦点缺陷已修） |
| 课程详情（分层、颜色侧轨、缺失字段占位） | ✅ | 设备实测；无效 ID → 空状态不崩溃 |
| 课程编辑（分组、周次结果行、快捷填充、草稿恢复） | ✅ | `CourseSuggestionsTest`、`CourseDraftTest`；设备实测 |
| 课程复制（新 ID、原课不变、标题「添加副本」） | ✅ | `CourseCopyTest`(4)；**未做端到端设备验证** |
| 课程删除（危险按钮命名、二次确认） | ✅ | 统一为「删除课程」；弹窗即单次点击，无法重复触发 |
| 学期创建/切换/删除 | ✅ | 设备实测 |
| **新建学期名称自动顺延** | ✅ | `rule/SemesterNaming` + `SemesterNamingTest`(13)；按**开始日期**取最新（非列表末位、非当前选中）；自定义名时不臆造；**未做设备实测** |
| 学期起始日期（周一归一化） | ✅ | `SemesterCreationStartTest`(5) 覆盖「新建学期写入自己的开始日期」这一已修缺陷 |
| Excel 导入与异常报告 | ✅ | `ExcelTimetableReaderTest`、`CourseRepositoryStorageTest`、`ImportStartDateTest`(15)、`TimetableImportParserTest`；**设备实测整条链路**（生成 .xls → 识别 4 门课/5 条安排 → 选日期 → 二次确认 → 覆盖导入 → 落盘 → 撤销） |
| **导入周次固定对应（文件第 1 周 = 学期第 1 周）** | ✅ | 旧 `shiftWeeks`/`flipParity` **已彻底删除**；设备实测「1-16」仍是 1-16、「1-16 单周」保留 parity=1、「1-8,11-16」拆成两行、「2-16 双周」保留 parity=2 |
| **导入改为选择开学日期（不再有周次下拉）** | ✅ | 复用既有 `Pickers.dateField`；默认取**目标学期已存日期**而非今天；非周一按 `mondayOf` 归一化并显示「生效日期」；设备实测周日/周二选中均正确归一化 |
| **导入的日期与课程合为一次提交** | ✅ | `CourseRepository.replaceCoursesAt`；选择/取消均不写盘（设备端对照 `shared_prefs` 逐字节确认）；失败则日期与课程都不落 |
| **改开学日期时二次确认** | ✅ | 仅当日期真的变了且该学期已有课程；设备实测弹出「修改开学日期…新的第 1 周周一：2026-09-07」 |
| 备份导出 / 预览 / 恢复（快照+回滚） | ✅ | `BackupCodecTest`；设备实测导出与预览入口可用 |
| 上课提醒（计算、调度、恢复） | ⚠️ | `ReminderRestoreTest`；**未做通知的设备实测** |
| 作息表：15 节、开始时间可选 | ✅ | 设备实测 |
| **作息表每节课时长可配置（1–180 分钟，默认 40）** | ✅ | `rule/PeriodLengthAndCountTest`；`EditScheduleActivity` 的「每节课时长」数字输入；**设备实测改 50 → 全部结束时间即时重算且开始时间未动** |
| **结束时间只读（由开始 + 本表时长算出）** | ✅ | 设备实测：结束列灰绿只读、无涟漪；改开始或时长即重算 |
| **每日节次数量可增可减（1–30，默认 15）** | ✅ | 「添加一节 / 减少一节」；`BellScheduleEditor.addPeriod/removePeriod`；**设备实测添加得第 16 节并点名删除**；被课程引用的节次**拒绝删除并列出课程** |
| **保存时校验作息表合法性** | ✅ | **重叠检查已从「先写盘后提示」改为「写盘前拦截」**；设备实测：制造重叠 → 保存被拒 → **存储载荷逐字节未变** |
| **作息表列表显示「默认」徽标（按 ID，与排序无关）** | ✅ | `data/DefaultScheduleTest`(27)；**设备实测徽标落在第 3 行 B/C 楼作息，证明非第一张** |
| **「设为默认」一键切换（点击即生效 + 提示）** | ✅ | 设备实测：点教学楼作息 → 徽标迁移、B/C 徽标消失、**任意时刻恰好一个默认**；不触发卡片编辑 |
| **默认作息表可自选并持久化（`default_schedule_id`）** | ✅ | **冷启动后仍有效**；ID 存于 `SharedPreferences` 独立键，与列表排序无关 |
| **地点匹配优先、未匹配才用默认表** | ✅ | **设备实测：D102 课程仍解析为 D/E 楼作息（默认是教学楼作息）**；无地点课程用教学楼作息 |
| **老用户升级默认沿用旧回退（B/C 组），时间不跳变** | ✅ | `adoptLegacyDefaultIfUnset`；单测覆盖「课程/学期/关键词/时间/节数均不受扰动」 |
| **删除默认表自动改选并点名继任者** | ✅ | 设备实测确认弹窗写明「将改用『D/E 楼作息』」，删除后无悬空 ID |
| **备份携带并恢复默认表选择，老备份兼容** | ✅ | `BackupCodecTest` + `DefaultScheduleTest`；**备份导出→恢复的设备往返未验证** |
| **日期/时间输入统一为选择器** | ✅ | `ui/Pickers`；全项目 `new DatePickerDialog`/`new TimePickerDialog` 使用数 = **0**；两者共用 `UiKit.dialog` 外框 |
| **选择器外观优化（中文按钮、24 小时制、去时钟盘）** | ✅ | 设备实测并截图：`选择时间 / 08:15 / 24 小时制` + 时分两列 + `取消/确定`；日期为月历 |
| **设置页功能顺序调整** | ✅ | 设备实测：学期管理 → 学期起始日期 → 上课提醒 → 上下课时间 → 课表数据 → 关于 |
| 统一按钮层级 / 危险操作命名 | ✅ | 5 处「删除」→「删除课程/作息表/学期」；2 处「已删除」补全对象 |
| 统一空状态 | ✅ | 首页区分「本学期还没有课程」与「这天没有安排」（**设备实测了后者**） |
| 首页「回到本学期」 | ✅ | 原先只是标签，现已直接切换；无包含今天的学期时隐藏 |

**任务清单里"计划过"的项目，实际状态是**：新建学期名称顺延 ✅、日期/时间统一选择器 ✅、
选择器外观 ✅、作息时长与节次数量可配置 ✅、设置页顺序 ✅、**自定义默认作息表 ✅**。
**但其中多项只有单元测试与一次人工设备走查，没有自动化回归。**

---

## E0. 上一次 Agent 的工作：滚轮时间选择器与循环边界（2026-10-12，**未提交**）

**范围**：先把时间选择器从小时/分钟列表改为**真正的滚轮**（Wheel Picker），再把滚轮改为
**真循环**（`00` 上方显示 `22/23`、`23` 下方显示 `00/01`）。上一节的 E 节描述的是更早、
已推送的会话。

**改动文件（3 个，全部在工作区未提交）**

| 状态 | 文件 | 说明 |
|---|---|---|
| `M` | `app/src/main/java/com/weihe/schedule/ui/Pickers.java` | `showTimePicker` 的两列由 `ScrollView`+`TextView` 换为 `WheelColumnView`；删除旧 `column()` 辅助方法与 `ScrollView` import；顶部预览改用 `TITLE_LARGE`；`COLUMN_HEIGHT_DP=168`/`ROW_HEIGHT_DP=40` → `WHEEL_HEIGHT_DP = 44 * 5`。**`showDatePicker` 未改动。** |
| `??` | `app/src/main/java/com/weihe/schedule/ui/WheelColumnView.java`（638 行） | 新建滚轮组件（循环版） |
| `??` | `app/src/test/java/com/weihe/schedule/ui/WheelColumnViewTest.java`（466 行） | 27 项几何/循环单测 |

> `Pickers.java` 的 diff 属于**第一段（列表→滚轮）**改动；第二段（循环边界）**只动了
> `WheelColumnView.java` 与其测试**，未再触碰 `Pickers.java`。

### E0.1 为什么旧实现在结构上无法居中

`ScrollView` 没有「中心行」这个概念，选中项只能被 `scrollTo` 到「大约三分之一处」（旧代码注释
原文：*A third down the column rather than at its edge*），在短列上还会被裁掉上边缘。这不是样式
问题，而是布局本身没有中心。

**滚轮设计**：`WheelColumnView` 自己持有 `scrollPx` 作为**唯一真相源**——
`y = centre + round(i * row - scrollPx)`，数值取最近行，居中因此是**坐标系的定义**而非需要持续
维持的行为。「显示的数值 ≠ 返回的数值」和「忘记自己在哪」这两种失效状态变成**不可表示**。
`ROW_HEIGHT_DP=44`、`VISIBLE_ROWS=5`（奇数，保证有正中行）。

### E0.2 设备端发现并修复的缺陷一：甩动后永久卡在两行之间

`computeScroll()` 原先的写法是

```java
if (flingScroller.computeScrollOffset()) { setScroll(...); postInvalidateOnAnimation(); }
else { settleTo(nearestRow(...) + from); }   // ← 起第二段动画
```

`setScroll()` **不吸附**，所以甩动的最后一帧把 `scrollPx` 留在任意像素上；`else` 分支虽然起了
第二段 `startScroll()`，但它只 `invalidate()` 而没有 `postInvalidateOnAnimation()` **驱动帧循环**，
第二段动画永远拿不到帧。结果：**甩动结束后滚轮永久卡在两行之间**——数值已提交为 `07`，
但 `07` 只露出半截、并未居中。三张截图 md5 完全相同，证明是卡死而非动画进行中。

修法（四步）：① `fling()`/`settleTo()` 改用 `postInvalidateOnAnimation()` 启动帧循环；
② `computeScroll()` 收尾**改为直接赋值** `scrollPx = offsetFor(nearestRow(...))`，不再起第二段动画
（收尾是算术，算完即定）；③ 新增 `snapTo(int)`，供 `ACTION_CANCEL` 与无 scroller 场景**即时**吸附；
④ 补 3 项单测（任意像素点吸附后必落在整行、吸附不得改变选中值、底部吸附不越界）。

### E0.3 缺陷二（本轮）：滚轮不是循环 —— 根因与修法

**根因**：第一版是**有限列表**，类注释原文明写 *"It does not wrap around"*，且三处都在
**截断**而不是回绕：

1. `maxOffset() = (count - 1) * rowHeightPx`，`setScroll()` 做 `max(0, min(maxOffset, px))` —— 首尾截断；
2. `nearestRow()` 做 `if (row < 0) return 0;` 与 `min(row, count - 1)` —— 索引截断；
3. 完全没有取模，索引本身就是行号。

所以滚到 `00` 时上方**是空白**（修复前已在设备上拍照存证：`00` 上方两行全空）。
被报为「`00` 上方出现 `13、14`」的，其实是**分钟列**——`00:15` 时分钟列显示
`13/14/15/16/17`，两列左右相邻，那两行正好位于小时列上方两行的位置，容易被读串。
这**不是**显示文字问题，改文字只会掩盖。

**修法：无界虚拟索引 + 归一化取模。**

- 新增 `static int mod(int index, int modulus)` —— **Euclidean modulo**，`-1 % 24` 得 `23`。
  Java 的 `%` 保留被除数符号（`-1 % 24 == -1`），往上滚一格就会得到负数小时，这是必须绕开的点。
- `valueAt(index) = from + mod(index, count)`：索引可为任意整数，输出恒在 `[from, to]`。
- `nearestIndex(offsetPx, rowHeightPx)` 不再有上下钳制。
- `INDEX_ORIGIN = 100_000` 行作为起点（约 4,500 圈小时 / 1,700 圈分钟，手拖不可能触到 0）。
- `onDraw` 按**索引带宽**绘制（`centreIndex ± (VISIBLE_ROWS/2 + OVERDRAW_ROWS)`），没有首尾行。
- `fling()` 仍须给边界（`OverScroller` **没有**「无界」的 4 参数重载），取
  `here ± rowHeightPx * FLING_RANGE_ROWS`（10,000 行）。

**实现该修法时自己引入、又被测试与设备抓出的两个缺陷**（留作前车之鉴）：

1. **甩动变成缓慢蠕动**：最初把边界写成 `Integer.MIN_VALUE/4 ~ Integer.MAX_VALUE/4`，但
   `INDEX_ORIGIN = 1<<24` 使 `scrollPx ≈ 1.9e9`，**大于** `Integer.MAX_VALUE/4 ≈ 5.4e8`，
   于是命中 `OverScroller` 内部 `if (start > max || start < min)`，走了 `startAfterEdge()`
   分支——表现为**长时间缓慢爬行**（连拍 5 帧各不相同）。修法：`INDEX_ORIGIN` 降到 100,000，
   并改用**以当前 offset 为中心**的边界，保证 `start` 永远落在范围内。
2. **打开时定位错位**：`indexForValue(v) = INDEX_ORIGIN + (v - from)`，而 `100000 % 24 = 16 ≠ 0`，
   于是 `valueAt(index) ≠ v`，打开时并非从原时间开始。修法：先把原点**向下取整到 `count` 的
   整数倍**（`turns * count + (v - from)`）。这条由新单测
   `theIndexChosenForAValueMapsBackToThatValue` 抓出。

**未做 / 有意不做**：未动 40 分钟规则、作息数据格式、提醒与学期逻辑；未改布局、颜色、圆角、
中心高亮与 `HH:mm` 24 小时制；未引入任何第三方依赖，未使用反射/隐藏 API；未新增 `res/layout`。

---

## E1. 本轮工作：自定义每节课时长 + 动态每日节次数量（2026-10-12，**未提交**）

**范围**：让「每节课 40 分钟」与「每天 15 节」从**编译期常量**变成**每张作息表自己的配置**。
默认值不变（新表仍是 40 分钟 / 15 节），旧数据一个字都不用改。

**需求原文的两条硬约束**（决定了后面所有取舍）：

1. 「结束时间为只读的计算结果，不再允许单独手动编辑」；
2. 「不要机械地把项目中所有数字 15 替换成动态值……不能影响 15 分钟提醒选项、月份、学期周次等无关逻辑」。

### E1.1 数据格式：只加一个可选键，不加就不变

| 项 | 决定 |
|---|---|
| 时长存哪 | `BellScheduleJson` 的顶层新增可选键 `"durationMinutes"`（int，分钟） |
| 旧表怎么读 | `MiniJson.optInt(object, "durationMinutes", 40)` —— **缺失即 40**，不做迁移、不回写 |
| 节次数量存哪 | **不新增字段**。沿用 `starts` 数组长度（`BellSchedule` 本来就是按数组长度工作的） |
| 上限 | `ScheduleUtils.MAX_PERIODS = 30`；下限 `BellSchedule.MIN_PERIODS = 1` |
| 合法时长 | `MIN_PERIOD_MINUTES = 1` ~ `MAX_PERIOD_MINUTES = 180`；越界不抛异常，**按 40 修复**（旧数据损坏时仍能开） |

`BellSchedule.PERIODS_PER_DAY = 15` **保留但降级为「默认值」**，`MAX_PERIODS = 30` 是新的「上限」。
这两个常量名字相近、含义不同，是本轮最容易误用的地方：

- `PERIODS_PER_DAY` → 只在**新建默认表**、`SettingsActivity` 空列表兜底、以及被 `@Deprecated` 的
  `CourseFormValidator` 5 参重载里出现；
- `MAX_PERIODS` → 所有**校验上限**、几何/行数上限、导入与备份的范围检查。

### E1.2 改了什么（按「原先硬编码在哪 → 现在读什么」）

| 模块 | 原先 | 现在 |
|---|---|---|
| `model/BellSchedule` | `PERIOD_MINUTES = 40` 编译期常量，`endOf(start)` 只有 1 参 | 新增 `durationMinutes` 字段与 5 参构造；`endOf(start, duration)`；`computedEndMinute(period)`。4 参构造仍按 40 走，`renamed`/`withTimes`/`withLocationKeywords` **都保留** `durationMinutes` |
| `rule/BellScheduleEditor` | `String[] texts`（定长数组，隐含 15） | `List<String> texts`；`periodCount() = texts.size() / 2`；新增 `durationText` 与 `durationMinutes()`/`effectiveDurationMinutes()`；新增 `addPeriod()`/`removePeriod()`；`build()` 用「开始 + 本表时长」重算**每一个**结束时间 |
| `rule/ScheduleDraft` | 节次数量**从数组长度推断** | 显式 `periodCount` + `NOT_CARRIED = -1`；见 E1.3 |
| `rule/CourseFormValidator` | `periodCount` 写死 15 | 新增 6 参重载带 `int periodCount`，上限按**该课程适用的作息表**；旧 5 参重载委派并标 `@Deprecated` |
| `rule/ReminderPlan` | `hasUsablePeriods` 用 `PERIODS_PER_DAY` 判界 | 改用 `MAX_PERIODS`（原写法让第 16 节以后的提醒计划分支**永远不可达**） |
| `EditScheduleActivity` | `TextView[15]` 数组、结束时间可编辑 | `List<TextView>`；「每节课时长」数字输入；结束时间改为 `Pickers.readOnlyField` 的**只读**行，任何开始/时长变化都立即 `refreshEnds()` 重算；「添加一节 / 减少一节」；保存按钮在最后一行之下 |
| `EditCourseActivity` | 节次下拉框固定 1–15 | `syncPeriodSpinners(applied)` 按该课程适用作息表的 `periodCount()` 重建 `ArrayAdapter`；`syncingPeriodSpinners` 守卫防止重建时误触发保存逻辑 |
| `importer/TimetableImportParser` | 超出 15 的节次行**静默丢弃**（`continue`） | 接受至 `MAX_PERIODS`；超出者记入 `Reason.PERIOD_OUT_OF_RANGE` 并在提示里**点名上限**，绝不静默截断 |
| `data/BackupCodec` | 备份里 `periods != 15` 直接判非法 | 下限 `< MIN_PERIODS` 判非法、上限 `> MAX_PERIODS` 判非法，中间**全部接受**；课程节次上限改 `MAX_PERIODS`；写出 `durationMinutes`，读入缺失即 40 |
| `WeekGridActivity` | `rowTopPx` 定长 15+2，行列钳到 15 | 尺寸与钳制都改 `MAX_PERIODS` |
| `Rule/SettingsActivity` | 覆历表摘要写「每节 N 节」 | 改「每天 N 节」（本轮顺手改的**文案错误**，非重构） |

**没有改的**：15 分钟提醒提前量、月份、学期周次、`SharedPreferences` 的 key 与整体 JSON 结构、
`res/layout`（项目本来就没有）、依赖清单。`ui/Pickers.java`、`ui/WheelColumnView.java`、
`ui/WheelColumnViewTest.java` 属上一轮滚轮工作，**本轮一行未动**。

### E1.3 本轮最大的坑：`ScheduleDraft` 的「片段 vs 整表」

`ScheduleDraft` 是「把页面上的输入搬到 `BellScheduleEditor`」的载体。它有一个 4 参方便构造器，
测试大量使用它传入**只有 1~2 个元素的短数组**（表示「我只关心前两节」）。

本轮给它加了「显式节次数量」后，我犯的错是：让方便构造器把**数组长度**当作节次数量。
于是「传 1 个元素的片段」被读成「总共只应有 1 节」，`applyTo` 把一个 15 节的编辑器裁到 1 节，
两个既有测试当场变红（`aShorterDraftLeavesTheRemainingPeriodsAlone`、
`anAbsentNameLeavesTheEditorsOwnNameAlone`）。

根因不是委托写法，而是**语义**：短数组说的是**片段**，不能拿它推断总数。修法是把语义写进类型：

```java
public static final int NOT_CARRIED = -1;   // 这个状态对节次数量没有任何主张

public ScheduleDraft(String name, String keywords, String[] starts, String[] ends) {
    this(name, keywords, null, NOT_CARRIED, starts, ends);   // 片段，不携带数量
}
public ScheduleDraft(String name, String keywords, String durationText,
        String[] starts, String[] ends) {
    this(name, keywords, durationText, NOT_CARRIED, starts, ends);   // 同样是片段
}
public ScheduleDraft(String name, String keywords, String durationText,
        int periodCount, String[] starts, String[] ends) { ... }     // 只有这里能定数量
```

只有 `of(editor)` 和 `EditScheduleActivity.draftFrom(Bundle)` 会传真实数量。
`applyTo` 仅在 `carriesPeriodCount()` 时才去增删行，且只从**列表末尾**增删，
中间行的开始时间与编号绝不被重排。之后我把只用于推断长度的 `countOf()` 删了（已是死代码）。

调试方式值得记下：打印 `d.periodCount()` / `d.carriesPeriodCount()` 的一个临时探针测试
（`ProbeDraftTest`，已删）才让我看到字段真的是 1 —— 在此之前我改了三版都在改错的对象。

### E1.4 一个走错了又退回来的决定

我一度把 `BellScheduleEditor.problems()` 里「结束时间早于开始时间」的校验删掉，
理由是「结束时间已经是算出来的，不是输入」。这直接弄红三个既有测试
（`allProblemsAreReportedAtOnceInRowOrder`、`aPeriodThatEndsBeforeItStartsIsReported`、
`anEndBeforeItsStartIsStillReported`）。回头核实后确认：`EditScheduleActivity` 在**每次**
开始时间/时长变化时都会把 `editor.setEndText` 同步为算出的值，所以「存的结束时间是旧的」
这种状态在真实流程里不会出现，但校验本身对**从 saver 恢复出来的旧草稿**仍然有用。
于是**回退**，改为修正我自己写错的测试夹具（`endOf` 辅助方法现在会像页面一样调
`setEndText(suggestedEndText)`）。结论：这不是旧测试过时，是我的修改方向错了。

### E1.5 与既有测试冲突的三处（按 `AGENTS.md` 停下来报告）

以下三个测试**原先编码了「结束时间可独立编辑」这一前提**，而本轮需求明确改为「结束时间只读计算」。
按 `AGENTS.md` 的要求，我没有删测试、也没有弱化断言，而是**改写断言以匹配新规则**，
并在测试内注释里写明原因。请知悉这三处是**需求驱动的语义变更**，不是为了让测试通过而放宽：

| 测试 | 旧断言 | 新断言 |
|---|---|---|
| `BellScheduleEndTest.aFortyFiveMinutePeriodIsStillRepresentable` | 手输 45 分钟结束时间应被保留 | 改名 `aFortyFiveMinutePeriodComesFromTheScheduleLength`：设 `setDurationText("45")` 后才得到 45；另加 `aTypedEndIsReplacedByTheComputedOne` 明确「手输的结束时间会被算出的值取代」 |
| `BellScheduleEditorTest.everyTimeFormatPeopleTypeIsAccepted` | `endMinute(2) == 580`、`endMinute(15) == 650`... | 改为算出的值：`endMinute(2) == 575`、`endMinute(15) == 1530` |
| `ScheduleDraftStorageTest.savingAfterARestoreStillWorks` | 结束时间 = 手输的常量 | 结束时间 = `editor.suggestedEndText(3)`，断言 `10*60+5+stored.durationMinutes()` |

另有两个测试编码了「上限 = 15」这个旧规则，已按新规则重写（断言强度不变，只是指向 `MAX_PERIODS`）：
`WeekGridRowsTest` 的两项、`BackupCodecTest` 的 `aScheduleWithTheWrongNumberOfPeriodsIsRefused`
（曾拒绝一切非 15 张数，现拆为「20 节可读 / 低于下限拒 / 高于上限拒」三项）。

### E1.6 实际执行结果

```
--rerun-tasks testDebugUnitTest assembleDebug lintDebug  →  BUILD SUCCESSFUL
```

- 单测：**47 个类 / 849 项 / 0 失败**（基线 44 / 803 → **+3 类 / +46 项**）
- `lintDebug`：**No issues found.**（无新增警告）
- `assembleDebug`：产出 `app-debug.apk`（859,591 字节；追加 E1.9 的按钮高度修复后为 **859,575** 字节）
- 残留数字审计：主代码里剩下的 `40` 全部是 UI 尺寸（`dp(40)`、`BUTTON_HEIGHT_COMPACT`、
  `ICON_BUTTON_SIZE`）；除合法默认常量外无游离的 `15`；`PERIODS_PER_DAY` 只剩下述几处
  「默认值」语义的引用（见 E1.1）。

新增三个测试类：`rule/PeriodLengthAndCountTest`（18 项）、
`importer/TimetableImportRangeTest`（7 项）、`data/NonDefaultScheduleFlowTest`（12 项；
以 20 节 / 50 分钟的作息表贯穿「第 17–18 节课程能否存储、首页显示、详情、提醒、序列化」）。

### E1.7 设备端验证（`emulator-5554`，Pixel_10 / API 37 / 1080x2424）

已验证：

- 设置页显示「作息时间表 · 3 个作息表 · **每天 15 节**」；作息列表三张表均为
  「第1节 08:15 - 08:55 · **共 15 节**」→ **旧数据完好**。
- 编辑页出现「每节课时长 [40] 分钟」及提示；「每节上下课时间 · 共 15 节」；结束时间呈
  **灰色只读**（与开始时间的输入框对比明显）；三列「节次 / 开始 / 结束」；15 个学校作息时间全对；
  列表末尾有「添加一节 / 减少一节」，均在「保存作息表」之上。
- 把时长改为 **50**：全部结束时间立即重算（08:15→09:05、08:55→09:45、09:55→10:45、
  10:40→11:30、11:20→12:10），**每一个开始时间未动**。改为 **45** 时第 4 节变 10:40→11:25，
  第 5/7/9/11/13 节出现重叠提示（按设计只是提示，不阻止保存）。
- 「添加一节」→ 追加第 16 节 21:00 - 21:40（开始时间取自上节结束时间），标题变「共 16 节」。
- 「减少一节」→ 弹确认并**点名第 16 节**（「确定删除第 16 节吗？它的开始时间会一并移除。」），
  删除后回到 15 行且第 15 节仍为 20:20 - 21:00。返回后已存的作息表**未被修改**。
- **最强兼容证据**：`run-as cat shared_prefs/weihe_schedule.xml` 显示线上存储的 JSON
  **没有 `durationMinutes` 键**、`starts`/`ends` 各 15 个元素、第 1 节 495→535，
  三张表各自保留自己的开始时间 —— 即旧数据能读、且未被回写改变。

**未能验证**：保存按钮无法用 `input tap` 点中。它的真实边界是 `[550,1926]-[1004,2015]`，
而底栏是 `[0,2045]-[1080,2361]`；在 2140 / 1970 / 2200 三个 y 位点击都没能触发。
保存路径改由单测（`ScheduleDraftStorageTest`、`NonDefaultScheduleFlowTest`）
加直接读取 `shared_prefs` 来覆盖。
另未在设备上跑：20 节作息表在课程编辑页的节次下拉框、Excel 导入的超范围提示、
非默认作息表的备份导出/导入。

### E1.8 本轮有意未做 / 剩余限制

- 跨零点不支持（沿用旧规则：结束时间钳在 `MAX_MINUTE = 30*60-1`，即不超过 29:59），
  页面未对此给专门提示。
- 非默认时长在设备上的**保存**流程未走通（见 E1.7），仅由单测覆盖。
- 未重构无关 UI、未动学期管理、未动数据层结构；未新增依赖、未用反射/隐藏 API、未新增 `res/layout`。
- 未执行任何 `git commit`（按要求需明确指令）。

### E1.9 追加修复：增删节按钮的水平/垂直截断（设备端发现）

**用户报告**：截图显示「添加一节 / 减少一节」两个胶囊按钮看起来被切断。

**实测定位**（像素级，`screencap` + 逐像素扫描，非肉眼判断）：

| 位置 | 修复前 | 修复后 |
|---|---|---|
| 按钮高度 | **89px**（≈34dp） | **105px**（= 40dp = `BUTTON_HEIGHT_COMPACT`）✓ |
| 胶囊圆角 | 顶/底被压平，字几乎贴边 | 上下对称的完整圆弧 |

**根因**：`createPeriodButtons()` 里按钮栏和按钮**都是 `WRAP_CONTENT` 高度**，
只靠 `TextView.setMinHeight` 撑高。两个按钮都带 `weight=1f`，
加权子项在 wrap-content 行里最终按**文字行高**定尺寸，`minHeight` 未生效。

**修法**：改为**按钮栏显式 `dp(UiTokens.BUTTON_HEIGHT_COMPACT)` 高度、按钮 `MATCH_PARENT`** ——
这正是 `CourseDetailActivity` 复制课程那一行已经在用的写法（`rowParams` 显式高度 +
按钮 `MATCH_PARENT`），两处因此一致。`topMargin` 也随之统一为 `UiTokens.SPACE_M`。

**设备复验**（装新 APK 后）：两按钮 bounds 均为 `h=105`；
像素扫描确认圆角对称（中间 `76..528`，顶 `97..507`，底 `100..504`），
卡片内边 x=1004、按钮外边 x=1003，**未越界**。
功能同时复验：「添加一节」→ 第 16 节 `21:00-21:40`；
「减少一节」→ 弹窗点名第 16 节 → 删除后回到 15 节；
`shared_prefs` 里 `bell_schedules` **仍无 `durationMinutes` 键**、
三张表 `starts` 各 15 元素、首值 495 → **数据未被改动**。

**门禁**（`--rerun-tasks`）：**47 类 / 849 项 / 0 失败**；`assembleDebug` SUCCESSFUL；
`lintDebug` = `No issues found.`。首次修复后曾出现一次 uiautomator 报 65px，
经复测确认是**滚动过程中的瞬态裁剪**（ScrollView 视口底边），非布局缺陷。

**教训**：`setMinHeight` 在带 `weight` 的 wrap-content 子项上不可靠；
这类等宽并排按钮应当**给容器显式高度 + 子项 `MATCH_PARENT`**。

---

## E2. 本轮工作：自定义默认作息表（2026-10-12，**未提交**）

**需求**：多套作息表靠「适用地点关键词」匹配课程地点；匹配不到时系统「默认使用列表第一张」。
用户希望能**自己指定**默认表，未匹配课程改用该表；且**默认表与列表排序无关、重启后仍有效**。

### 开工前的关键发现（需求前提与代码不符，已上报并获确认）

> ⚠️ **未匹配回退并不是 `schedules.get(0)`**。实际实现是一条**硬编码名字链**：
> `BellSchedules.resolve()` 里依次尝试 `BC_NAME` → `STANDARD_NAME` → 内置 `standard()`。
> 并且 **`BellSchedule` 当时完全没有稳定 ID**。

因此「默认 = 第一张」是**错误描述**；真正的旧回退是 **B/C 楼作息**。
四条冲突点经 `AskUserQuestion` 全部按「推荐」确认：

| 冲突 | 采用方案 |
|---|---|
| 回退基线 | **用「用户指定默认表」替换 `BC_NAME` 硬编码回退**（若改成「第一张」，未匹配课程会被搬到 A/F 时间轴上，3–5 节偏移 20 分钟） |
| 删除默认表 | 删除后**自动改选列表剩余第一张**并明确提示继任者 |
| 持久化 | `SharedPreferences` **新增独立键 `default_schedule_id`**（不塞进作息表 JSON 数组内的 `isDefault` 字段） |
| 作息表 ID | **新增持久化 `id` 字段**，首次读取时按名字补齐 |

### 数据格式变更（兼容契约）

| 位置 | 变更 |
|---|---|
| `bell_schedules` 数组每项 | **新增可选键 `"id"`**；为 `null` 时不写出 |
| `weihe_schedule` 顶层 | **新增键 `default_schedule_id`**（独立字符串，不在数组中） |
| `BackupCodec` | 文档新增**可选**顶层 `"defaultScheduleId"`；每张表新增**可选** `"id"`；`FORMAT_VERSION` **仍为 1** |

内置 ID 固定为 `schedule-standard` / `schedule-bc` / `schedule-de`，
其余表按名字派生（`derivedId`）并去重（`-2`、`-3`…）。

### 七个解析调用点全部打通（**这是本轮最容易漏的地方**）

原先 `BellSchedules.resolve` 的调用点分散在 **7 处**，任何一处漏掉就会出现
「首页用默认表、详情/提醒仍用第一张」的分裂。全部改为透传 `defaultId`：

1. `CourseRepository.applyTimeRange`（行 ~594）
2. `CourseRepository` 时间求解器（行 ~1236）
3. `CourseRepository.timeRangeOf`（行 ~1259）
4. `CourseRepository.scheduleFor`（行 ~1277）
5. `rule/CourseDetail.of(course, semester, schedules, defaultId)`
6. `rule/ReminderPlan.scheduleFor/startOf/onDate/upcoming/forCourseOnDay`（全部新增重载）
7. `reminder/ReminderScheduler.planFor`、`reminder/ReminderReceiver.lookup`

**所有旧签名一律保留为「委托重载」**（尾部委托给新签名并传 `null` → 退回 `legacyDefaultId`），
因此没有数据层的既有调用者与既有测试被破坏。

### 设备端发现的真实缺陷（本轮唯一一个）

装新 APK 后在模拟器上车检查出：**课程详情页解析路径与卡片不一致**。
用户已把「教学楼作息」设为默认，但一张**无地点**课程的详情页仍显示 `按「B/C 楼作息」作息`。

- **根因**：`rule/CourseDetail.of(...)` 与 `rule/ReminderPlan.scheduleFor(...)` 各自直接调用
  **3 参数的 `BellSchedules.resolve`**，该重载使用 `legacyDefaultId` ——
  这是我漏掉的**第 3、第 4 条解析路径**（前两条在 `CourseRepository` 里已改）。
- **修法**：把 `defaultId` 形参一路穿到 `CourseDetail`、`ReminderPlan`、`ReminderScheduler`、
  `ReminderReceiver`、`CourseDetailActivity`、`CourseSearchActivity`。
- **复验**：重建安装后同一课程改为 `按「教学楼作息」作息`，与首页卡片一致。
  并补了一条**回归测试** `theDetailDescriptionUsesTheSameDefaultAsTheCard`。

### 默认表的维护规则（`CourseRepository`）

| 时机 | 行为 |
|---|---|
| 首次读取、键缺失且有作息表 | `adoptLegacyDefaultIfUnset`：把 **B/C 组**（即旧硬编码回退）写成默认并落盘 → **老用户升级后时间不跳变** |
| 完全没有作息表 | 落内置预设，仍走同一套采纳逻辑 |
| 表缺 ID | `assignMissingIds` 按名字补 ID 并去重，**其余字段一字不动** |
| `setDefaultSchedule(id)` | 仅当该 ID 能在当前列表解析成功才写入；否则拒绝 |
| 删除的是默认表 | 自动改选 `schedules.get(0).id()`；删除前确认弹窗**点名继任者** |
| 删除最后一张表 | **拒绝**（沿用原有空态/新建流程） |
| `restoreFrom` | 先 `assignMissingIds`，再解析默认：文档值 → 否则 `legacyDefaultId`；**作息表与 `default_schedule_id` 在同一 `commit` 写入**，失败整体回滚 |
| `verifyStored` | 新增校验：刚写入的默认 ID 必须在刚写入的作息表里可解析 |

### 备份兼容

- **新备份**：写出每张表的 `id` 与顶层 `defaultScheduleId`。
- **老备份**（无这两个字段）：`parse` 返回 `defaultScheduleId() == null`，
  恢复时**回退到 `legacyDefaultId`**（即 B/C 组），并在 `notes` 里给出说明。
- **备份点名了它没带来的表**：丢弃该指针并加一条 note，**绝不留下悬空 ID**。
- `FORMAT_VERSION` 未升版，`MAX_SUPPORTED_VERSION` 不变 → **老版本可继续读新备份的非新字段**。

### 门禁结果（`--rerun-tasks` 实际执行）

```
testDebugUnitTest  → 48 类 / 884 项 / 0 失败 / 0 跳过   （基线 47/849 → +1 类 / +35 项）
assembleDebug      → BUILD SUCCESSFUL
lintDebug          → BUILD SUCCESSFUL
app/build/reports/lint-results-debug.xml → <issues format="6" by="lint 8.13.0"> 内 0 个 <issue>  →  完全干净
```

### 设备实测（`emulator-5554`，`pm clear` 后全新安装重建 APK）

- 作息表列表页：**默认徽标落在第 3 行「B/C 楼作息」** → 证明**与位置无关**（不是第一张）；
  另两行在关键词那一行各挂一个「设为默认」按钮，**卡片没有因此多出一行**。
  截图：`build/schedule_list_before.png`。
- 点「教学楼作息」的「设为默认」→ 徽标移到第 1 行、B/C 的徽标消失、B/C 长出按钮 → **任意时刻恰好一个默认**。
  截图：`build/schedule_list_after.png`。
- `shared_prefs/weihe_schedule.xml`：`bell_schedules` 每项都带稳定 `id`
  （`schedule-standard` / `schedule-de` / `schedule-bc`），**时间/关键词/时长/节数逐字节未变**；
  同文件另有 `<string name="default_schedule_id">schedule-standard</string>`。
- 强制停止 + 冷启动：首页两张种子课程只显示 `第1-16周 · 每周`，**不再追加作息表名**
  （因为默认已换成 A/F）；切换前它们是 `… · 教学楼作息` → **重启后仍然有效**。
- 详情页 **线性代数 @ 教学楼 D102** → `按「D/E 楼作息」作息`，而默认是教学楼作息
  → **地点匹配优先于默认**。截图：`build/detail_venue_vs_default.png`。
- 详情页 **新加的无地点课程**（修复后）→ `按「教学楼作息」作息` → **未匹配时用默认**，与卡片一致。
- 长按当前默认 → `确定删除“教学楼作息”吗？\n它是当前的默认作息表，地点匹配不到时将改用“D/E 楼作息”。`
  确认后徽标移到 **D/E 楼作息**、列表剩 2 张、存储指针改写为 `schedule-de` → **无悬空 `schedule-standard`**。
  截图：`build/schedule_list_after_delete.png`。

### **未在设备上验证**（只由 JVM 单测覆盖）

- 备份**导出 → 恢复往返**；
- 提醒**通知真正弹出**；
- Excel 导入（本轮未改动这条链路）。

### 有意不做

未动任何作息表的**时长/节数/开始时间**，未动学期·星期·单双周规则，
未改地点匹配算法本身，未做无关重构；未新增依赖 / Kotlin / Compose / `res/layout`；**未执行 `git commit`**。

---

## E3. 本轮工作：Excel 导入改为「文件第 1 周 = 学期第 1 周」+ 开学日期选择（2026-10-12，**未提交**）

**需求**：消除导入时「课表第 1 周对应本学期第几周」的歧义 —— 固定**文件第 1 周 = 本学期第 1 周**，
把原来的**周次下拉**改成**开学日期选择**，课程实际日期一律由学期起始日期推导。

### 开工前的关键发现

| 发现 | 结论 |
|---|---|
| 周次偏移在哪 | `TimetableImportParser.shiftWeeks(courses, sourceFirstWeek)`（+ 私有 `flipParity`） |
| 谁在调用 | **只有 2 处**：`MainActivity.confirmImport`、`SettingsActivity.confirmImport`；**无其他导入路径** |
| 覆盖测试 | `TimetableImportParserTest` 中 3 项 |
| 下拉是谁 | 两个 Activity **各自**建了一个 `Spinner` + `String[] starts` + 「文件最后一周…」提示 |
| `Semester.start()` 语义 | **已经是「第 1 周周一」**（构造时经 `ScheduleUtils.mondayOf` 归一化）→ **正是新需求要的语义，无需新增字段** |
| 事务基础 | `replaceCourses` **本就是单次 commit + 导入前快照**（`previousEnvelope` / `readPrevious`） |
| 超范围校验 | 在 **`parseWeeks`**（`end > SEMESTER_WEEKS` → null → `UNREADABLE_WEEKS`），**不依赖 `shiftWeeks`**，删掉偏移后仍有效 |

四条冲突点经 `AskUserQuestion` 全部按「推荐」确认：

| 冲突 | 采用方案 |
|---|---|
| 日期归属 | **直接改学期起始日期**，复用 `semester_start:<id>` 与 `updateSemester`；**不新增**独立导入日期字段 |
| 影响已有课程 | 日期**真的变了**且该学期**已有课程**时，**先弹二次确认再写入** |
| 旧偏移逻辑 | **彻底删除** `shiftWeeks` + `flipParity`，改写那 3 项测试为「固定对应」语义 |
| 非周一 | 沿用 `mondayOf` 归一化，并在预览区**显式显示最终生效日期** |

### 核心改动：`CourseRepository.replaceCoursesAt(List, Semester, LocalDate)`

一次 `editor` 同时写入 **4 样东西**，失败则一样都不落：

```
coursesKeyFor(targetId)          ← 导入后的课程
previousCoursesKeyFor(targetId)  ← 导入前快照（撤销用）
startKeyFor(targetId)            ← 归一化后的第 1 周周一
KEY_SEMESTERS                    ← 学期记录（startingOn(monday)），并 sortByStart
```

写后 `reload()` 重采纳缓存（学期列表 / 目标学期键 / 课程缓存三者都变了，局部丢缓存会残留旧态）。
`target` 用 `id` 定位，**导入到非当前学期不会误改当前学期**；目标学期不是已存储学期 →
**整体拒绝**（否则课程会挂在一个任何界面都到不了的 id 下）。

### 删除的旧逻辑（已确认无残留）

```
grep -rn "shiftWeeks\|flipParity" app/src/   →  无任何匹配
```

两个 Activity 的 `Spinner` / `String[] starts` / 「文件最后一周」提示一并删除；
`MainActivity` 顺带移除 `ArrayAdapter`、`Spinner`、`TimetableImportParser` 三个已无用 import。

### 设备端发现并修复的缺陷（**TextWatcher 读到的 Monday 是上一周**）

首版用 `dateField.addTextChangedListener(...)` 同步「生效日期」行。设备上实测：
选 `2026-09-17` 后，输入框显示 `2026-09-17`，但下面那行**仍是 `2026-08-31`** —— 两处自相矛盾。

**根因**（读 `ui/Pickers.dateField` 源码）：回调顺序是

```java
field.setText(formatDate(chosen));   // ① 先写文本 → 触发 TextWatcher
onPicked.accept(chosen);             // ② 之后才把我的 chosen[0] 归一化成 Monday
```

`afterTextChanged` 在 ① 里执行，此时 `chosen[0]` **还是老值**，所以永远渲染上一周。

**修法**：不再依赖 `TextWatcher`，改为**在 `onPicked` 回调内部**更新那一行（此处 `chosen[0]` 已归一化）。
两个 Activity 都改；并把那行文案抽成同名的 `effectiveDateText(LocalDate monday)`，避免两份措辞漂移。

### 测试

- **改写**（`TimetableImportParserTest`）：`theFirstWeekOfTheFileIsTheFirstWeekOfTheTerm`、
  `aSecondWeekInTheFileStaysTheSecondWeekInTheTerm`、`aWeekRangeBeyondTheSemesterIsDroppedWhileParsing`。
- **新增** `data/ImportStartDateTest`（15 项）：默认取目标学期已存日期、第 1/2 周固定对应、
  1-16 不变、单双周/非连续周次不变、改日期只动日历不动周次、周中归一化（周三→周一、**周日往前**）、
  单 commit、**失败写入不落日期也不落课程**、取消不改、改 A 学期不动 B 学期、
  导入非当前学期写该学期、非已存学期被拒、详情页与提醒用同一日期、首页周次跟随、撤销快照覆盖被替换课程。

### 设备端验证（`emulator-5554`，Pixel_10 / API 37 / 1080x2424）

用项目自带 `app/libs/jxl-2.6.12.jar` 生成 `timetable.xls`（表头「星期一…」、A 列 1-based 节次、
单元格「编码【课名】\n周次  老师  地点」），先由 `ExcelTimetableReader + TimetableImportParser`
在 JVM 上读回确认 **4 门课 / 5 条安排 / 0 issues**，再推到 `/sdcard/Download/` 走真机。

| 验证项 | 实测结果 |
|---|---|
| 弹窗默认日期 | **`2026-08-31`**（= 设置页「学期起始日期」，**不是今天**） ✅ |
| 点击日期区 | 打开项目**既有** `选择日期` 选择器，停在 2026-08 月，选中 08-31 ✅ |
| 选周日 08-30 | 输入框 `2026-08-30`，生效行 → **`2026-08-24，即本学期第 1 周周一`**（往前归一化） ✅ |
| 选周二 09-08 | 输入框 `2026-09-08`，生效行 → **`2026-09-07，即本学期第 1 周周一`**（往后归一化） ✅ |
| 选择后未确认 | `semester_start` 仍 `2026-08-31`、`courses` / `semesters` **逐字节不变** ✅ |
| 点「取消」 | 全部键与基线**逐字节相同** ✅ |
| 覆盖导入 | 弹出 **`修改开学日期`** 二次确认：「…会改变本学期课程对应的实际日期及相关提醒时间。新的第 1 周周一：2026-09-07」 ✅ |
| 继续导入后落盘 | `semester_start:1791793712068` → **`2026-09-07`**；`semesters` 记录同步 ✅ |
| **周次未被偏移** | 文件「1-16」→ **1-16**；「1-16 单周」→ **1-16 parity=1**；「1-8,11-16」→ **1-8 + 11-16**；「2-16 双周」→ **2-16 parity=2** ✅ |
| 单 commit | 同一次写入里生成 `courses_previous:1791793712068` 快照 ✅ |
| 全局一致性 | 首页头 `9/7 开学`、周次条 `10/19-10/25`（原 `10/12-10/18`，整体 +1 周）、
课程详情 `第1-16周 · 教学楼作息`；设置页 `9/7 - 1/10 · 共 18 周`、`第 1 周：2026-09-07` ✅ |
| 撤销上次导入 | 9 门示例课**按名字/周次/单双周排序后与导入前基线完全一致**；快照键被消费（每次导入只能撤销一次） ✅ |

> **撤销不回收日期**是**有意**的：日期是用户对学期的设置，不属于导入负载。

**门禁**（`--rerun-tasks`）：**49 类 / 905 项 / 0 失败 / 0 错误 / 0 跳过**（基线 48/884 → +1 类 / +21 项）；
`assembleDebug` SUCCESSFUL（APK 864,635 B）；`lintDebug` SUCCESSFUL，`lint-results-debug.xml` **0 个 `<issue>`**。

### 有意不做

未动课时长/节次/开始时间，未动学期·星期·单双周规则，未改地点匹配算法，未做无关重构；
未新增依赖 / Kotlin / Compose / `res/layout`；**未执行 `git commit`**。

---

## E4. 本轮工作：导入弹窗新增「学期总周数」设置（2026-10-12，**未提交**）

**需求**：在 E3 已完成的导入弹窗里，**开学日期下方**再加一个「学期总周数」设置。
默认读目标学期**已保存**的周数；用户增减后**必须真正落到目标学期**，并与课程周次校验保持一致。
E3 的开学日期功能**一行不动**。

### 开工前的关键发现（需求前提与代码不符，已上报并获确认）

| 发现 | 结论 |
|---|---|
| 有没有「每学期周数」字段 | **完全没有**。`ScheduleUtils.SEMESTER_WEEKS` 是硬编码 `static final int = 18`，被 **~25 处**引用 |
| 这个 18 同时是什么 | ① 学期结束日跨度 ② **导入解析器的周次上限**（`parseWeeks`: `end > SEMESTER_WEEKS` → null → `UNREADABLE_WEEKS`）③ 课程表单周次上限 ④ 首页/周视图的周次选择范围 |
| `Semester.lastDay()` | 原为 `start.plusWeeks(SEMESTER_WEEKS).minusDays(1)` |
| `SemesterJson` | schema 原为 `[{"id":…,"start":"…","name":""}]`，**没有 weeks** |
| `CourseRepository` | 只存 `semester_start:<id>`（第 1 周周一） |

**结论**：要让周数「真正生效」，必须**按学期存**，而不是再加一个全局常量。

三条冲突点经 `AskUserQuestion` 全部按「推荐」确认：

| 冲突 | 采用方案 |
|---|---|
| 周数上限的作用域 | **仅学期结束日期与该校验**；`SEMESTER_WEEKS = 18` 保留为「未选择时的默认值」；**不动** `WeekSelection` / `TodayTarget` / `ReminderPlan` |
| 减小周数会挤掉已有课程 | **提示并阻止，不改课程**；绝不静默截断或删除 |
| 导入文件的课程周次超出新总周数 | **明确提示，允许继续**；周次原样保留 |

### 数据格式变更（兼容契约）

`semesters` 增加**可选**键 `weeks`：

```json
[{"id":1791793712068,"start":"2026-09-07","name":"","weeks":17}]
```

- **等于默认 18 时不写该键** → 旧版本看到的仍是它一直看到的形状，**逐字节兼容**。
- 读到 `weeks` 时校验范围 `[1, 18]`；**越界一律「修复为默认」而非钳制**（手改的 `900` 读回诚实的 18）。
- `Semester` 保留 3 参构造（委托给 4 参并传默认值），`clampWeeks` 统一钳制。
- 新增 `withWeeks(int)`；`startingOn` / `renamed` 都**保留周数**。

### 周数范围为什么是 1–18（不是需求建议的 1–52）

`Semester.MAX_WEEKS = ScheduleUtils.SEMESTER_WEEKS = 18` 是**故意的**：这个常量同时是
**所有课程周次的上限**，允许比它更长的学期只会产生「课表填不满的周」。
需求原文是「优先遵循项目已有的周数校验范围」，因此取 **1–18**。

### 核心改动：`CourseRepository.replaceCoursesAt(List, Semester, LocalDate, int)`

4 参重载把**周数塞进同一个 commit**；3 参版本委托给 4 参并传 `target.weeks()`：

```java
.putString(coursesKeyFor(targetId), coursesJsonOf(courses))
.putString(previousCoursesKeyFor(targetId), previousEnvelope(...))
.putString(startKeyFor(targetId), monday.toString())
.putString(KEY_SEMESTERS, SemesterJson.write(updated))   // ← 其中目标学期是 startingOn(monday).withWeeks(safeWeeks)
```

→ 周数与课程**要么一起落盘，要么一起不落**，不存在「只存了周数、课程没进来」的中间态。

### 新增规则类：`rule/SemesterWeeks`

纯逻辑、不碰存储：`DEFAULT`、`isValid(int)`、`of(Semester)`、`lastDay(LocalDate, int)`、
`maxWeekOf(List<Course>)`、`countBeyond(List<Course>, int)`、`wouldStrandCourses(List<Course>, int)`。
**有意不返回起始日期** —— 周数与日期各司其职。

### 两处弹窗的确认链路（`MainActivity` / `SettingsActivity` 各一份）

```
读取目标学期已存周数 → 步进器（− / N 周 / +）
   ↓ 点「覆盖导入」
① weeksShrank && wouldStrandCourses(existing, requested)
      → 「周数不足」：N 门课程排在第 X 周之后……请先调整这些课程，或改回 Y 周   ← 直接 return，不写盘
② (dateMoved || requested != stored) && !existing.isEmpty()
      → 「修改学期设置」：只列出**真正变了**的那一项（如「学期总周数：18 周 → 17 周」）
      → 取消 / 继续导入 → runImport(courses, target, weekOne, requestedWeeks)
③ runImport → replaceCoursesAt(..., weeks) → 单 commit
```

`weeksShrank` 用 `<` 而非 `!=` → **加长**学期永远不会触发「挤掉课程」的拒绝。

### 步进器的实现要点（`ui/Pickers.SimpleTextWatcher`）

- `−` / `+` 用 `UiKit.pill`，`setContentDescription("减少一周"/"增加一周")`；`−` 钳在 `MIN_WEEKS`，`+` 钳在 `MAX_WEEKS`。
- 「课程可排范围：M/d - M/d，共 N 周」由**读数上的 `SimpleTextWatcher`** 刷新，
  **不放在步进器回调里** —— 因为 E3 已确认 `Pickers.dateField` 是**先 `setText` 再 `onPicked`**，
  回调时序不可靠；另在 `dateField` 上挂一个 watcher，日期一变就重算范围。
- 新增 `Pickers.SimpleTextWatcher`（`beforeTextChanged`/`onTextChanged` 为 `final` 空实现），
  避免两个 Activity 各写三份空回调。

### 测试

- **新增** `data/SemesterWeeksTest`（**27 项**）：弹窗默认显示学期已存周数 / 无存储值回落默认 /
  两学期各自独立 / `lastDay` 跟随周数 / 与共享算术一致（1..18 全循环）/ 1 周学期 6 天后结束 /
  越界拒绝 / **越界存储值修复而非保留** / 构造时钳制 / **周数永不改写课程周次** /
  缩短会挤掉课程 / 空学期不挤 / maxWeek / 非默认值跨重启 / **只在与默认不同时才写键** /
  旧负载无键仍可读 / **周数与课程同一 commit** / **写失败两者都不落** / 取消不改 /
  导 A 不动 B / 导入非当前学期写该学期 / 非已存学期被拒 / null 被拒 / 传入越界是钳制不是存储 /
  **改长度后第 1 周仍是第 1 周** / 详情页与提醒计划仍一致 / 起始日期不被长度变更改动。
- **扩充** `data/SemesterJsonTest`（+4 项）：非默认长度往返、默认长度不写出、
  无长度键读作默认、越界长度（0 / 400 / `"many"`）被修复。

> 首轮 **25/27 通过**，2 项失败是**我自己的测试期望写错**：`twoTermsKeepTheirOwnWeekCounts` 与
> `importingIntoOneTermDoesNotMoveAnothersCount` 原本断言 20 周，但 `MAX_WEEKS = 18`，
> 被自己的钳制合法地变成 18。改成 16 vs 17、18 vs 16 后 **27/27 通过**。

### 门禁结果（`--rerun-tasks` 实际执行）

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **50 类 / 936 项 / 0 失败 / 0 错误 / 0 跳过**（E3 的 49/905 → +1 类 / +31 项） |
| `assembleDebug` | **BUILD SUCCESSFUL**（APK 869,439 B） |
| `lintDebug` | **BUILD SUCCESSFUL**，`lint-results-debug.xml` **0 个 `<issue>`** |

### 设备实测（`emulator-5554`，Pixel_10 / API 37 / 1080x2424 @420）

复用 E3 生成的 `timetable.xls`（4 门课 / 5 条安排 / 0 issues），经 MainActivity 齿轮 → 设置 → 导入课表。

| 验证项 | 实测结果 |
|---|---|
| 弹窗默认周数 | **`18 周`**（= 目标学期已存值，不是写死） ✅ |
| 步进器布局 | 单行放下：`−`(205–331) / `18 周`(居中 331–749) / `+`(749–875)，1080 宽无截断 ✅ |
| 范围行 | `课程可排范围：9/7 - 1/10，共 18 周`，**实时**跟随 ✅ |
| 点 `−` | `18 周` → **`17 周`**，范围 → **`9/7 - 1/3，共 17 周`**（结束日提前一周） ✅ |
| 点 `+` 到顶 | 连点两次仍停在 **`18 周`**，范围不变 → **上限钳制生效** ✅ |
| 缩到 11 周后导入 | 弹 **「周数不足」**：「本学期已有 **7 门课程**排在第 11 周之后，改成 11 周会让它们落在学期之外。请先减少或调整这些课程，或改回 18 周。」 ✅ |
| 该提示后的存储 | 全部键与基线**逐字节相同**（`semesters` 仍无 `weeks`，`高等数学` 仍 `1-16`） ✅ |
| 17 周时导入 | 弹 **「修改学期设置」**：**只列变动项** —— `学期总周数：18 周 → 17 周`，未提日期 ✅ |
| 继续导入后落盘 | `semesters` → `[{…,"start":"2026-09-07","name":"","weeks":17}]`（**加上了 weeks 键**） ✅ |
| 起始日期未被扰动 | `semester_start:1791793712068` 仍 **`2026-09-07`** ✅ |
| **课程周次未被改动** | `高等数学` 仍 **`startWeek:1, endWeek:16`**，无截断/无偏移/无单双周改写 ✅ |
| 单 commit | 周数与课程在同一次 `editor` 写入 ✅ |
| 设置页同步 | 学期管理 **`9/7 - 1/3 · 共 17 周 · 进行中`**（结束日 1/10 → 1/3）；起始日期行 **`第 1 周：2026-09-07 · 共 17 周 · 今天是第 6 周`** ✅ |
| 首页第 1 周语义 | 周次选择器 **`第1周 9/7 - 9/13`** → **文件第 1 周 = 学期第 1 周，无偏移** ✅ |
| 跨重启持久化 | `am force-stop` 后重开，`semesters` 仍 `"weeks":17`，设置页仍显示 **`共 17 周`** ✅ |
| E3 功能未回归 | 开学日期行 `2026-09-07` / `生效日期：2026-09-07，即本学期第 1 周周一` 原样 ✅ |

### **未在设备上验证 / 已知限制**

1. **周次选择器仍可浏览到第 18 周**（截图实测：17 周学期下仍列出 `第18周 1/4 - 1/10`）。
   这是**设计使然**：该范围由硬编码 `ScheduleUtils.SEMESTER_WEEKS` 驱动，本次按确认的范围边界
   **有意不动** `WeekSelection`。若后续要让选择器也跟随学期周数，需单独一轮。
2. 未做「导入文件最大周次 > 新学期周数」的允许继续分支的设备实测（JVM 层已有覆盖）；
   测试文件最大周次为 16，低于 17，因此设备上走的是正常分支。
3. 未测非周一开学日期 + 改周数 的组合（E3 已单独覆盖归一化）。
4. `adb shell input` 对系统文件选择器的点击**偶发不生效**（本轮遇到多次），
   需按 `uiautomator dump` 的实际 `bounds` 重新取坐标。

### 有意不做

未重新实现、修改或重构 E3 的开学日期功能；未新增与现有字段重复的学期周数/起止日期字段；
未把改总周数转成课程周次偏移；未动课时长/节次/开始时间、作息表、提醒逻辑、地点匹配；
未改课程存储格式与备份兼容逻辑；未新增依赖 / Kotlin / Compose / `res/layout`；**未执行 `git commit`**。

---

## E. 更早一次 Agent 的工作

**范围**：从品牌/UI 打磨一路做到作息表 40 分钟规则，跨越多个连续任务。
**全部改动已包含在 `bdd7d01` 中并已推送**（当时的说法；当前工作区已不干净，见 E0 与 H 节）。

主要落地内容：

- **新增规则类**：`rule/SemesterNaming`、`rule/WeekSelection`、`rule/CourseSuggestions`、
  `rule/CourseDraft`、`rule/ScheduleDraft`、`rule/TodayTarget`、`rule/CourseSearchSummary`
- **新增公共 UI**：`ui/Pickers`（日期与时间选择器，共用 `UiKit.dialog` 外框）
- **新增图标**：`res/drawable/ic_search.xml`、`ic_download.xml`
- **修改**：10 个 Activity、`data/CourseRepository`、`model/BellSchedule`、`rule/BellScheduleEditor`
- **修复的真实缺陷**：
  1. `CourseRepository.reload()` 清缓存但未重新采纳 `semesterId` → 首页不显示当天课程
  2. **`addSemester` 未写入 `semester_start:<新id>`** → 新学期的开学日期被「今天的默认值」覆盖
     （这也是「两个学期同名」的根因）
  3. **`EditScheduleActivity` 的 `TextView[]` 用 `new EditText[...]` 初始化** → 打开作息编辑页
     即 `ArrayStoreException` 闪退（数组协变使它能通过编译，测试也拦不住）
  4. 作息表保存**先写盘再校验重叠** → 已改为写盘前拦截
  5. 搜索页回车导致返回、清空抢焦点
  6. 首页「查看当前学期」只是标签、点击无实效
- **未完成 / 主动未做**：
  - 非 40 分钟作息表在**界面上**已无法表达（编辑器底层仍支持；设备上现有数据全部正好 40 分钟）
  - 「相邻两节不能重叠」只在违反时提示，未做事前说明
  - 多处功能只做过单元测试，**未做设备端到端验证**（见 F 节）

---

## F. 测试与构建

### 实际执行的命令与环境

```powershell
cd 'D:\vibe coding\Weihe\Weihe'
$env:GRADLE_USER_HOME='D:\vibe coding\Weihe\Weihe\.gradle-local\home'
$env:ANDROID_HOME='D:\Sdk'; $env:ANDROID_SDK_ROOT='D:\Sdk'
& '.\.gradle-local\bin\gradle.bat' "-Dorg.gradle.vfs.watch=false" `
    --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

> `--rerun-tasks` 是必要的：否则 Gradle 可能跳过未变化的测试任务，读到旧结果。

环境：Gradle 8.13 / JVM 20.0.1 / `ANDROID_HOME=D:\Sdk` / AGP 8.13.0。

### 结果

**基线（针对 `bdd7d01` 的干净工作区，接手时实测）**

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **43 个测试类 / 776 项 / 776 通过 / 0 失败 / 0 错误** |
| `assembleDebug` | **BUILD SUCCESSFUL** |
| `lintDebug` | **`No issues found.`**（0 errors, 0 warnings） |

**滚轮改动后（工作区含 E0 的 3 个文件，本次实测 —— 含循环边界修复）**

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **44 个测试类 / 803 项 / 803 通过 / 0 失败 / 0 错误 / 0 跳过**（776 基线 + 27 项滚轮测试） |
| `assembleDebug` | **BUILD SUCCESSFUL**（APK 853,735 字节） |
| `lintDebug` | **`No issues found.`**（0 errors, 0 warnings） |

**时长 + 节次数量改动后（工作区含 E1 的全部改动，本次实测）**

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **47 个测试类 / 849 项 / 849 通过 / 0 失败 / 0 错误 / 0 跳过**（803 + 46 新项） |
| `assembleDebug` | **BUILD SUCCESSFUL**（APK 859,575 字节，含 E1.9 按钮高度修复） |
| `lintDebug` | **`No issues found.`**（0 errors, 0 warnings） |

> 本轮新增 3 个测试类：`rule/PeriodLengthAndCountTest`（18 项）、
> `importer/TimetableImportRangeTest`（7 项）、`data/NonDefaultScheduleFlowTest`（12 项）。
> 另有 5 个既有测试类因规则变更被改写（见 E1.5），断言强度未降低。
> 所有三个门禁均为 `--rerun-tasks` 强制重跑，不存在「up-to-date 假装通过」。

**默认作息表改动后（工作区含 E2 的全部改动，本次实测）**

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **48 个测试类 / 884 项 / 884 通过 / 0 失败 / 0 错误 / 0 跳过**（849 + 35 新项） |
| `assembleDebug` | **BUILD SUCCESSFUL**（APK 863,659 字节） |
| `lintDebug` | **BUILD SUCCESSFUL**；`app/build/reports/lint-results-debug.xml` 内 `<issues format="6" by="lint 8.13.0">` **0 个 `<issue>`** |

> E2 新增：`data/DefaultScheduleTest`（**27 项**，含「第 1/2/3 张都能设为默认」「任意时刻恰好一个默认」
> 「重排不改身份」「地点匹配胜过默认」「空地点→默认」「不可匹配地点→默认」「时间计算/详情/提醒三者一致」
> 「删非默认无影响」「删默认不留悬空 ID」「删最后一张被拒」「老用户初始化沿用 B/C 组」
> 「重启后仍有效」「改名不失效」「新备份携带并恢复」「老备份兼容并回退 B/C」
> 「备份点名了它没带来的表 → 丢弃指针并加 note」「恢复替换作息表后不留旧指针」
> 「文件格式往返」以及回归测试 `theDetailDescriptionUsesTheSameDefaultAsTheCard`）；
> `model/BellSchedulesTest` 追加 6 项（稳定 ID、显式默认替换旧回退、旧回退就是 B/C 组、
> `assignMissingIds` 确定性、`defaultOf` 按 ID 不按名字/位置、`copyOf` 带 ID）；
> `data/BellScheduleJsonTest` 追加 2 项（ID 往返 / 无 ID 时省略、老数据读回无 ID）。

> 滚轮单元测试 `WheelColumnViewTest` 共 **27 项**，全部覆盖循环取值与几何换算：
> 负索引取模、模运算值域、退化列长、`00`/`23`/`59` 各边界的上下相邻值、跨边界步进、
> 整圈不跳不重、两天交界互推、列间独立、索引原点余量、索引↔取值互逆、索引↔偏移互逆、
> 吸附落点精确、吸附不改取值、退化行高、单值列、非零下界列。

**导入日期改动后（工作区含 E0–E3 的全部改动，本次实测）**

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **49 个测试类 / 905 项 / 905 通过 / 0 失败 / 0 错误 / 0 跳过**（884 + 21 新项） |
| `assembleDebug` | **BUILD SUCCESSFUL**（APK 864,635 字节） |
| `lintDebug` | **BUILD SUCCESSFUL**；`app/build/reports/lint-results-debug.xml` **0 个 `<issue>`** |

> E3 新增：`data/ImportStartDateTest`（**15 项**，见 E3 的测试清单）；
> `importer/TimetableImportParserTest` 中 3 项**被改写**（原来的 3 项偏移测试删除，
> 换成「文件第 1 周 = 学期第 1 周」「文件第 2 周 = 学期第 2 周」「超范围周次仍在解析期被丢弃」）。
> 改写是**等量替换**：断言强度未降低，`+21` 项来自 15 项新类 + 解析器处净增 6 项。

**学期总周数改动后（工作区含 E0–E4 的全部改动，本次实测）**

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **50 个测试类 / 936 项 / 936 通过 / 0 失败 / 0 错误 / 0 跳过**（905 + 31 新项） |
| `assembleDebug` | **BUILD SUCCESSFUL**（APK 869,439 字节） |
| `lintDebug` | **BUILD SUCCESSFUL**；`app/build/reports/lint-results-debug.xml` **0 个 `<issue>`** |

> E4 新增：`data/SemesterWeeksTest`（**27 项**）+ `data/SemesterJsonTest` 追加 **4 项**。
> 首轮 25/27 通过，2 项失败是**测试期望写错**（断言 20 周，但 `MAX_WEEKS=18` 会合法钳制），
> 修正为范围内的 16/17 后 27/27 通过 —— **不是**产品代码缺陷。

### 滚轮的设备端验证（`emulator-5554`，Pixel_10 / API 37 / 1080x2424 @420）

两列实测 bounds `[147,1011][524,1589]` 与 `[556,1011][933,1589]`：**等高、上下严格对齐**，
高 578px = 220dp = 5 × 44dp，几何中心 y = **1300**。

| 场景 | 结果 |
|---|---|
| 打开即居中 | `08:15`，`08`/`15` 各自居中 |
| 居中精度（像素测量） | **7 个不同帧**（小时含 08/09/06/23/00，分钟含 15/00/02）两列胶囊恒为 `centre=1299.5`、高 116px——高亮从不漂移 |
| 拖小时列 | `08→09→06`，分钟恒为 `15` → 列间状态独立 |
| 拖分钟列 | `15→17→00→01→02`，小时不受影响 |
| **小时列 `00` 边界（循环）** | 中心 `00`，自上而下 **`22 / 23 / 00 / 01 / 02`** ✓（修复前该位置为**空白**，见 E0.3） |
| **小时列 `23` 边界（循环）** | 中心 `23`，自上而下 **`21 / 22 / 23 / 00 / 01`** ✓ |
| **分钟列 `00` 边界（循环）** | 中心 `00`，自上而下 **`58 / 59 / 00 / 01 / 02`** ✓ |
| **分钟列 `59` 边界（循环）** | 中心 `59`，自上而下 **`57 / 58 / 59 / 00 / 01`** ✓ |
| **跨边界无截断** | 小时上滑 `19 → 11`（经由 `23 → 00` 连续环绕），**未出现钳制在 `00` 的现象** ✓ |
| 快速甩动 | 正常吸附居中；2 秒间隔连拍两张 `md5sum` **相同** → 无缓慢爬行（E0.3 的 `OverScroller` 越界缺陷已修） |
| `确定` | 字段 → `11:00`，结束时间自动算出 `11:40`（40 分钟规则与数据格式未受影响） |
| `取消` | 字段保持 `11:00` 不变，未写盘 |
| 反复开关 3 次 | 每次都从**保存值**（`11:00`）居中开始，无残留滚动状态 |

> **未验证项（如实标注）**：`23:59 → 00:00` 与 `00:00 → 23:59` 的**双列同时越界**只在单元测试中覆盖
> （`rollingFromTheEndOfTheDayToTheStartKeepsBothColumnsAgreeing`、
> `rollingBackwardsFromTheStartOfTheDayReachesTheEnd`），设备上手势未精确构造出该组合。

**注意**：验证用的临时截图与脚本已清理，工作区只剩上表 3 个源码文件。

### 未运行的测试

- **没有 `androidTest` 源集** —— 项目**不存在**仪器测试，任何"UI 自动化验证"都无从运行。
- **没有真机验证**。所有设备结论来自 `emulator-5554`（`adb` 在 `D:\Sdk\platform-tools\adb.exe`，
  **不在 PATH 上**）。
- 滚轮的手势未做**多指/长按**等边缘交互验证；拖动结论来自 `input swipe` 合成的单指序列。

### 已知环境陷阱（会浪费大量时间）

- **PowerShell**：`(...)` 内用 `if` 是语法错误；单引号 here-string 是 LF 而行是 CRLF（用 `[regex]` 加 `\r?\n`）；
  `git show > file` 在 PowerShell 里写成 UTF-16；**多余的一个 `)` 会让整个脚本不执行**（易误判为"已修改"）。
- **模拟器**：`adb shell input text` **会丢掉 `.`** 且无法输入非 ASCII；`input swipe` 做长按容易拉下通知栏；
  `BACK` 按多会退到桌面；UI dump 里是**真实中文**，用 `\uXXXX` 匹配必然找不到。
- **模拟器黑屏**：窗口全黑但应用正常（系统 Settings 也一样黑）→ WindowManager 缺陷，`adb reboot` 可解，**不是应用 bug**。
- **`adb` 不在 PATH 上**：位于 `D:\Sdk\platform-tools\`，每次用前 `export PATH="/d/Sdk/platform-tools:$PATH"`。
- **Git Bash 路径改写（很坑）**：`adb shell screencap -p /sdcard/x.png` 会被 MSYS 改写成
  `D:/Program Files/WorkBuddy/.../sdcard/x.png` 而报 "No such file"。**必须**
  `export MSYS_NO_PATHCONV=1`，且本地目标路径写成 `D:/...` 风格（`/tmp/...` 会失败）。
- **非 exported 的 Activity 不能直启**：`am start -n .EditScheduleActivity` 会抛
  `SecurityException`。必须走 UI 导航（主页齿轮 → 上下课时间 → 作息时间表 → 第一张表 → 第一节课开始时间）。
- **`dumpsys` 里中文匹配不到**：`grep "选择时间"` 恒为 0；用
  `grep -oE "com\.weihe\.schedule/[A-Za-z]+"` 判断当前 Activity。
- **快速连发 `input swipe` 会丢事件**：循环里不等 `sleep` 的话大部分手势无效。且 `input swipe` 的
  起止点**必须在目标控件 bounds 内**，起点在控件外则整个手势被忽略（容易被误读成"滚轮没反应"）。
- **截图要等动画结束**：甩动后立刻截图会拍到**动画中间帧**，看起来像 bug。判断是否真的卡死：
  间隔 2-3 秒连拍 3 张，`md5sum` 相同才是卡死；不同则可能只是动画在跑。

---

## G. 已知问题与技术风险

前三条**已确认**，其余标「待确认」。

1. **已确认** — `EditScheduleActivity.timeInput()` 是**死代码**（30 个时间框改用 `Pickers` 后无人调用）。
2. **已确认** — 删除作息页脚注后，`EditScheduleActivity` 第 227-228 行留下**一个多余空行**（无害）。
3. ~~**已确认** — 删除学期会留下**孤儿键** `semester_start:<已删id>`（设备上 2 个学期却有 4 个该键）。~~
   **★ 2026-10-12 更正：本条已过期。** 当前 `CourseRepository.deleteSemester()` 已经调用了
   `.remove(startKeyFor(semester.id()))`，**新的删除不会再产生孤儿键**。设备上观察到的多余键是
   **修复前的历史残留**，不会再累积。原文的措辞会误导下一位 Agent 去"修"一个已经修好的问题。
4. ~~**已确认（设计取舍）** — 作息表结束时间在界面上**只读**，因此**非 40 分钟**的作息表无法再输入；
   已有这类表保存后会被改成 40 分钟。编辑器底层（`BellScheduleEditor`）**仍支持任意长度**，只是 UI 到不了。~~
   **★ 2026-10-12 本轮更正：本条已解决。** 现在编辑页有「每节课时长」数字输入（1–180 分钟），
   结束时间由「开始 + 本表时长」自动算出并保持只读——只读**不再**意味着无法表达非 40 分钟。
   时长按**每张作息表**存储（`BellScheduleJson` 的可选键 `durationMinutes`，缺失即 40），
   编辑一张表不影响其他表。旧表打开后仍是 40 分钟 / 15 节，且不会被回写。
5. **已确认** — 「相邻两节不能重叠」**只在被违反时**（保存被拒）才告知，无事前说明。
6. **待确认** — 学期自定义名被手改过（如 `大三上`）时，新建学期**不推算**名称，回到空输入框（设计如此）。
7. **待确认** — **非连续周次在模型上不可能表达**（`startWeek..endWeek` + 单双周）。
   任何"逐周勾选"需求都必须先改模型与存储格式。
8. **待确认** — **首页没有"跳转到指定日期"入口**（首页用周次网格 + 星期栏）。把它当既有功能是误判。
9. **待确认** — 缺陷 #2 修复**不追溯**已有数据：某学期的 `semester_start` 键可能仍是错误日期，
   需手动重设或重建该学期。
10. ~~**已确认** — 无地点（或地点匹配不到任何关键词）的课程，其作息表是**硬编码的 B/C 楼作息**。~~
    **★ 2026-10-12 本轮（E2）更正：本条已解决。** 现在回退目标由用户在作息表列表页**自选**
    （独立键 `default_schedule_id`），不再硬编码。旧用户升级后**默认仍被初始化为 B/C 组**，
    所以**已有课程的时间不会跳变**。地点匹配仍然**优先于**默认表。
11. **已确认（E2，新）** — 若用户在**备份文件手工编辑**里把 `defaultScheduleId` 写成一张
    它没携带的表的 ID，恢复时该指针会被**静默丢弃**并在恢复说明里加一条 note；
    这是有意行为（绝不留下悬空 ID），但备份预览页**是否展示了这条 note 未在设备上验证**。

---

## H. 当前工作区说明

**★ 2026-10-12 本轮实测**（`git status --short --untracked-files=all`）：
分支 `main`，HEAD `bdd7d01`（= `origin/main`，**未提交任何东西**）。

```
--- 本轮（E1：时长 + 节次数量）修改 22 个已跟踪文件 ---
 M AGENT_HANDOFF.md                                                ← 文档（本文件）
 M app/src/main/java/com/weihe/schedule/EditCourseActivity.java    ← 节次下拉框按适用作息表重建
 M app/src/main/java/com/weihe/schedule/EditScheduleActivity.java  ← 课时长输入 + 只读结束时间 + 增删节
 M app/src/main/java/com/weihe/schedule/SettingsActivity.java      ← 摘要「每天 N 节」
 M app/src/main/java/com/weihe/schedule/WeekGridActivity.java      ← 行数/钳制改 MAX_PERIODS
 M app/src/main/java/com/weihe/schedule/data/BackupCodec.java      ← 备份读写 durationMinutes，范围改 MIN/MAX
 M app/src/main/java/com/weihe/schedule/data/BellScheduleJson.java ← 可选键 durationMinutes
 M app/src/main/java/com/weihe/schedule/data/CourseRepository.java ← coursesUsingPeriodsFrom()
 M app/src/main/java/com/weihe/schedule/importer/TimetableImportParser.java ← 超范围不再静默丢弃
 M app/src/main/java/com/weihe/schedule/model/BellSchedule.java    ← durationMinutes 字段/5 参构造/endOf(2 参)
 M app/src/main/java/com/weihe/schedule/model/ScheduleUtils.java   ← MAX_PERIODS + periodLabels(count)
 M app/src/main/java/com/weihe/schedule/rule/BellScheduleEditor.java ← List 化 + 增删节 + 时长
 M app/src/main/java/com/weihe/schedule/rule/CourseFormValidator.java ← 6 参重载带 periodCount
 M app/src/main/java/com/weihe/schedule/rule/ReminderPlan.java     ← PERIODS_PER_DAY → MAX_PERIODS
 M app/src/main/java/com/weihe/schedule/rule/ScheduleDraft.java    ← 显式 periodCount + NOT_CARRIED
 M app/src/main/java/com/weihe/schedule/ui/Pickers.java            ← 【上一轮滚轮，本轮未动】
 M app/src/test/java/com/weihe/schedule/WeekGridRowsTest.java      ← 上限断言改 MAX_PERIODS
 M app/src/test/java/com/weihe/schedule/data/BackupCodecTest.java  ← 非 15 节接受/越界拒
 M app/src/test/java/com/weihe/schedule/data/BellScheduleJsonTest.java ← durationMinutes 读写
 M app/src/test/java/com/weihe/schedule/data/ScheduleDraftStorageTest.java ← 结束时间取算出的值
 M app/src/test/java/com/weihe/schedule/rule/BellScheduleEditorTest.java  ← 结束时间取算出的值
 M app/src/test/java/com/weihe/schedule/rule/BellScheduleEndTest.java     ← 45 分钟改由时长驱动

--- 本轮新增 4 个文件 ---
?? app/src/test/java/com/weihe/schedule/rule/PeriodLengthAndCountTest.java   ← 18 项
?? app/src/test/java/com/weihe/schedule/importer/TimetableImportRangeTest.java ← 7 项
?? app/src/test/java/com/weihe/schedule/data/NonDefaultScheduleFlowTest.java ← 12 项（20 节/50 分钟全流程）
?? .workbuddy-ai/memory/2026-10-12.md                                       ← 本轮工作日志

--- 上一轮（E0：滚轮）遗留 + 更早会话遗留，同样未提交 ---
?? AGENTS.md
?? AGENT_HANDOFF_ARCHIVE.md
?? app/src/main/java/com/weihe/schedule/ui/WheelColumnView.java
?? app/src/test/java/com/weihe/schedule/ui/WheelColumnViewTest.java
```

**★ E2（默认作息表）追加**：已跟踪文件 **+8**（`MainActivity`、`ScheduleListActivity`、
`CourseDetailActivity`、`CourseSearchActivity`、`model/BellSchedules`、`rule/CourseDetail`、
`reminder/ReminderScheduler`、`reminder/ReminderReceiver`）；
新跟踪文件 **+1**：`app/src/test/java/com/weihe/schedule/data/DefaultScheduleTest.java`（27 项）。

**★ E3（导入日期）追加**：**无新增生产文件**，改动全部落在**已修改的现有文件**上
（`MainActivity`、`SettingsActivity`、`data/CourseRepository`、`importer/TimetableImportParser`，
这 4 个本就已在 E0–E2 的 `M` 列表里）；新跟踪文件 **+1**：
`app/src/test/java/com/weihe/schedule/data/ImportStartDateTest.java`（15 项）。
合计 **30 M + 8 ??**（`AGENTS.md` 与 `AGENT_HANDOFF_ARCHIVE.md` 计入 `??`）。

> **没有业务代码处于"改了但未验证"的状态**：E0 的滚轮 3 文件、E1 的时长/节次改动、
> E2 的默认作息表改动、E3 的导入日期改动都已跑完 **905 项**单测 + `assembleDebug` + `lintDebug`
> （lint 0 issue），并在 `emulator-5554` 上做了设备端验证（E1 的验证范围见 E1.7，
> E2 的验证范围与**未验证项**见 E2 节末尾，E3 的验证范围见 E3 的设备端表格）。
> 下一位 Agent 可安全地在此基础上继续。

| 项 | 说明 |
|---|---|
| 已修改文件 | 30 个（其中 `ui/Pickers.java` 属滚轮轮，E1/E2/E3 **一行未动**） |
| 已暂存 | **无**（`git add` 从未执行） |
| 新跟踪文件 | 8 个（5 个新测试类 + 记忆日志 + `AGENTS.md` + 归档文档 + `WheelColumnView.java`） |
| 更早会话新增 | `AGENTS.md`（长期规范）、`AGENT_HANDOFF_ARCHIVE.md`（旧文档原文 + 过期声明） |

> ⚠️ **上一版此处曾写「已修改文件：无」，与同节下方内容及 `git status` 自相矛盾**，
> 已按实测更正。原始 `AGENT_HANDOFF.md` 内容**完整保留**在归档文件中。

`tools/out/`（77 个临时截图与 UI dump）已被 `.gitignore:27` 忽略，不影响仓库。
本轮验证用的临时截图与 `shared_prefs` 导出**已清理**。
`tools/` 下另有 5 个**已跟踪**的辅助脚本（`IconForge.java`、`dump-ui.ps1` 等），与运行时无关。

> **E3 的临时产物**（`build/importtest/`：`MakeXls.java`、`VerifyXls.java`、生成的 `timetable.xls`）
> 位于 `build/` 下，该目录已被忽略，**不会进仓库**。真机用的 `timetable.xls` 留在
> `/sdcard/Download/` 供后续复测。

---

## I. 下一步开发建议（按优先级）

1. **补齐本轮新配置在设备上的端到端验证（最高优先，零代码风险）**
   - 前置：模拟器可用（`adb devices` 见 `emulator-5554`）；APK 已在设备上
   - 范围（E1.7 / E2 明确列为**未验证**的三项）：
     ① 保存按钮的点击（`input tap` 对它无效，需手动点或用其他方式），确认改时长/增减节后
     `shared_prefs` 里真的写入 `durationMinutes`；
     ② 20 节作息表在**课程编辑页**的节次下拉框是否出现 16–20；
     ③ ~~Excel 导入超范围时的提示文案是否可读~~ → **E3 已覆盖**：导入链路已整条设备实测，
     含「识别到 N 门课程 / 共 M 条安排」「文件内容已全部识别」「覆盖导入」二次确认与撤销；
     超范围周次在**解析期**被丢弃这条由 `ImportStartDateTest` / `TimetableImportParserTest` 覆盖，
     但**真机上未专门构造**一份含超范围周次的文件去读那句提示文案（如需可补）；
     ④ **备份导出 → 恢复往返**（E2 新默认表字段 + E1 时长字段都要走一遍），
     确认新备份能恢复默认表、老备份能兼容回退到 B/C 组、恢复替换作息表后不留悬空 ID；
     ⑤ 提醒**通知真正弹出**（E2 只验证了计算一致，没验证通知到达）。
2. **补齐其他设备端到端验证（零代码风险）**
   - 范围：课程复制全流程、提醒到点通知、新建学期名称顺延、周网格总览
   - 验收：每项在设备上走通并截图
3. **清理已确认的小问题**：删 `timeInput()`、清多余空行、清理孤儿 `semester_start:` 键
   - 前置：无。验收：**905 项**仍全绿、lint 干净
   - 注意：滚轮已改为**无界虚拟索引**，`timeInput()` 删除**不影响**滚轮；但删前先确认
     `Pickers.showTimePicker` 是唯一入口（`Grep` 一遍 `timeInput`)。
   - E3 相关：导入会写 `startKeyFor(target)`，若目标学期此前没有扁平键，导入会**顺手补上**；
     「孤儿 `semester_start:` 键」的清理逻辑要注意**别删掉刚导入写入的那个**。
4. **补一句事前说明：滚轮无"跳到"入口**（可选）——当前只能拖动，不能直接跳年月日/时分；
   若用户要求快速定位，应加"输入框"而非改造滚轮
5. **给「相邻两节不能重叠」补一句事前说明**（加在作息页分组说明后）
   - 前置：与用户确认是否需要（现为"只在报错时说"）
   - 本轮相关：改了课时长后重叠提示会成串出现（设备上改 45 分钟时第 5/7/9/11/13 节齐亮），
     说明**事前说明**比以往更有必要
6. **把重复 UI 沉淀进 `UiKit`**：候选 `SettingsActivity.settingRow()`/`groupCard()`、
   `SemesterListActivity.dateBox()`
   - 前置：先确认确有第二处使用，避免为统一而抽象
7. **需产品决策才能动（先问用户，不要自行开工）**：非连续周次、自定义学期名的顺延规则、
   首页"跳转到指定日期"是否存在需求、**是否支持跨零点作息**（现为钳在 29:59，无提示）、
   **是否允许「无作息表时禁止删除最后一张」之外的空态策略**（现为拒绝删除最后一张）

---

## J. 下一位 Agent 的接手步骤

1. **先读** `AGENTS.md`（规范）→ 本文件（状态）→ 需要时查 `AGENT_HANDOFF_ARCHIVE.md`
   （**仅历史，判断不可信**）。
2. **再核 Git**：
   ```
   git branch --show-current      # 期望 main
   git log -3 --oneline           # 期望 HEAD 至少含 bdd7d01
   git status --short             # 期望 30 M + 7 ??（见 H 节）；或为空，若已被提交
   ```
3. **核对代码**：抽查 `ui/Pickers.java`、`rule/SemesterNaming.java`、
   `model/BellSchedule.java` 的 `DEFAULT_PERIOD_MINUTES`/`MAX_PERIOD_MINUTES`/`endOf` 两个重载、
   `model/ScheduleUtils.java` 的 `MAX_PERIODS`（新上限）与 `PERIODS_PER_DAY`（旧默认，语义已降级）、
   `rule/ScheduleDraft.java` 的 `NOT_CARRIED`、
   `EditScheduleActivity` 的「每节课时长」输入与只读结束时间、写盘前重叠校验、
   `ScheduleListActivity` 的默认徽标/「设为默认」按钮、`ScheduleListActivity.chooseDefault` →
   `CourseRepository.setDefaultSchedule`。
4. **跑一次基线**（务必带 `--rerun-tasks`）：工作区含 E0+E1+E2+E3 改动时，期望
   **905 项全部通过**（干净 `bdd7d01` 为 776 项；只含 E0 时为 803 项；含 E0+E1 时为 849 项；
   含 E0+E1+E2 时为 884 项）、lint `No issues found.`、`assembleDebug` 成功
   —— 若不是，**先报告，不要在其上叠加改动**。
5. **保护工作区**：**禁止** `git reset --hard`、`git clean`、强制切分支、`git checkout -- <file>`；
   **未经用户明确要求不得 commit 或 push**（`bdd7d01` 已推送，不要制造分叉）。
6. **暂停并向用户报告的情形**：
   - 测试或构建失败且原因不明
   - 发现工作区出现**你没做过**的修改
   - 需求会**改变存储格式、课程模型或学期周次计算**
   - 需求与代码现状冲突（例如要求"逐周勾选"、"首页跳到指定日期"）
   - 需要真机/通知等本环境无法验证的能力
7. **容易踩的三处新坑**：
   - **`PERIODS_PER_DAY`（=15）已不再是上限**，上限是 `ScheduleUtils.MAX_PERIODS`（=30）。
     任何新的范围校验都该用后者，用前者会把 16–30 节误判为非法。
   - **`ScheduleDraft` 的 4/5 参构造器是"片段"，不携带节次数量**。若需要控制数量，
     必须用 6 参构造器显式传入；不要从数组长度推断（E1.3 记录了这个坑的完整过程）。
   - **`BellSchedules.resolve` 有 3 参与 4 参两个重载，语义不同**。
     3 参重载用 `legacyDefaultId`（兼容旧行为，**仅供无数据层的调用者与老测试使用**）；
     4 参重载才认用户选择的默认表。**任何新增的解析路径都必须传 `getDefaultScheduleId()`**，
     否则又会造出"首页用默认、详情/提醒仍用 B/C"的第五、第六条路径（E2 已因此吃过一次亏）。
8. **不要**仅凭本文件或任何文档认定某功能已完成 —— **打开代码确认**。


---

## K. 追加记录 — 2026-10-12：课程配色选择器布局恢复

> 本节由**后一位 Agent 追加**，不改动以上任何内容。上方 A–J 节描述的是 `bdd7d01` 的状态；
> 自那之后**另一位 Agent 留下了一批未提交的改动**（见本节末尾），本节只记录其中配色布局这一项。

### 完成的事

把课程编辑页的颜色选择器从「3×3 大圆形网格 + 勾号」**恢复到用户要求的单排圆角矩形**：

- `EditCourseActivity.createColorSection()`：`colorRow` 由 `VERTICAL` 改回 `HORIZONTAL`，
  外层包一个 `HorizontalScrollView`（隐藏横向滚动条、`setClipToPadding(false)`）。
  **容器、标题「课程颜色」、位置、内边距 14/13/14/14 与 `SURFACE_CONTAINER`+`RADIUS_L` 描边全部原样未动。**
- `renderColorSwatches()`：单排渲染，无换行；`swatchLayoutParams()` 与勾号逻辑一并删除。
- `colorSwatch()`：**圆角矩形**（`UiTokens.RADIUS_S` = 8dp），**移除勾号**；
  选中态是**色块自身 2dp 描边**（`Palette.ON_SURFACE`），未选中为 0dp 描边以保持等宽。
  没有额外的选中颜色文字，色块下方没有任何重复展示。
- 新增常量：`SWATCH_DP = 30`、`SWATCH_GAP_DP = 4`、`SWATCH_RING_DP = 2`。
- 删除孤立资源 `res/drawable/ic_check.xml`（删除前确认全项目仅 `EditCourseActivity` 引用它；
  原为 24dp 白色 Material 对勾 vector，如需还原照此重建即可）。

### 尺寸依据（实测，不是估算）

设备为 1080×2424 @ **420dpi → 411dp 宽**。色块区可用宽度 = 411 − 14×2（body 内边距）
− 14×2（卡片内边距）= **355dp**。

**设备实测渲染结果**（UI dump 的 `content-desc` 边界）：

| 项 | 实测值 |
|---|---|
| 色块数量 / 行数 | **9 个 / 恰好 1 行** |
| 单个色块 | **30.1 × 30.1 dp** |
| 色块间距 | **4.2 dp** |
| 整排占用 | 首块 x=74px → 末块 x=873px ＝ **304dp** |
| 最后一个常规色块 | **完整可见**，右侧尚余约 51dp |
| 选中态 | 色块自身的深色描边，无勾号、无重复展示 |

九色需 9×30 + 8×4 = 302dp ≤ 355dp ✓。**第 10 个（旧颜色兼容）需 336dp，在本机也放得下**；
更窄的屏幕才需要横向滚动——因此**没有**把色块缩小去硬挤，`HorizontalScrollView` 是溢出时的唯一手段。

### 未改动（刻意保留）

`design/Palette.java`（九色与顺序）、`UiKit` 的 `courseTint/courseCard/courseRail/courseInk`、
`Course.DEFAULT_COLOR` 与旧颜色兼容、首页/周课表/详情页的配色应用、
`Palette.swatchesFor()` 的「旧颜色追加为第 10 个」行为，以及三个既有配色测试——**全部原样**。

### 测试与验证（本次修改之后实际执行）

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **52 个测试类 / 951 项 / 951 通过 / 0 失败 / 0 错误** |
| `assembleDebug` | **BUILD SUCCESSFUL** |
| `lintDebug` | **`No issues found.`** |

改动范围：**只有 `EditCourseActivity.java`**（修改文件总数 41，与本次动手前一致），
外加删除未跟踪的 `ic_check.xml`。

### 未验证 / 限制

- **没有为布局补回归测试**。项目无 `androidTest` 源集，且 `unitTests.returnDefaultValues = false`
  让 Activity 内的 View 布局无法在 JVM 中实例化——**做不到就不伪造**。
  `CoursePaletteShapeTest` 锁的是调色板内容与颜色角色层级，**不锁色块布局**：
  把单排改回网格它依然会通过。**这是当前的真实缺口。**
- **第 10 个色块的视觉效果未做设备验证**。设备上 23 门课中有 7 种颜色属于旧调色板
  （如「矩阵论」`0xFF525F6B`），**确实存在会显示 10 个色块的课程**，但
  `adb shell input text` 无法输入中文、当天课程列表为空，未能导航到那门课。
  已核实的是：`HorizontalScrollView` 确实存在于实时层级中，且 `CoursePaletteShapeTest`
  的 `aColourThePaletteNoLongerOffersStillGetsOneSwatch` 断言旧颜色被追加为最后一个。
- 点击区域为 30dp（小于 48dp 建议值），这是"保留原尺寸"与"可点击性"之间的取舍，
  按用户要求优先保留了原尺寸。

### 同批次的其他未提交工作（**非本次改动，请勿误认为是本次产物**）

工作区仍有 **41 个已修改文件 + 15 个未跟踪文件**，来自更早的一位 Agent，主题包括
`rule/SemesterWeeks`、`ui/WheelColumnView`、节次时长与数量、导入日期范围、默认作息表、
学期周数、提醒与备份等。**本次一行未碰。** 其中 `.workbuddy-ai/memory/2026-10-12.md`
记录了 `WheelColumnView` 那一轮，**与配色布局无关**。

---

## L. 交接核对 + 左侧节次编号的完整设备验证（2026-10-13）

**本轮性质**：**接手核对 + 验证**，**没有改任何业务代码**。改动只有 `AGENT_HANDOFF.md`（文档更正）。

### 接手时的工作区状态（与上一版交接文档不一致，已更正）

| 项 | 上一版交接文档写的 | 2026-10-13 实测 |
|---|---|---|
| HEAD | `f0765a6`，**未推送** | **`8ee1a04`**，且 `origin/main` = `8ee1a04` → **无未推送提交** |
| `git status --porcelain` | 7 项（6 个 `D` 删除 + 归档 `M`） | **0 行**（完全干净） |
| 6 个设计文档的删除 | **未暂存**，归属待确认，勿恢复 | **已提交**在 `8ee1a04`，与「重写交接文档 + 新建归档 + 新增 README」同一次**有意的文档整理** |
| `README.md` | 未跟踪（上一轮新建） | 已提交，9400 B |

结论：上一版的 G-1（文档删除待确认）与 G-2（未推送）**都已自然解决**，不需要再问用户。

### 基线门禁（干净工作区复跑，`--rerun-tasks`）

| 检查 | 结果 |
|---|---|
| `testDebugUnitTest` | **54 类 / 971 项 / 0 失败 / 0 错误** |
| `assembleDebug` | **BUILD SUCCESSFUL**，APK 871,447 B |
| `lintDebug` | **`No issues found.`**（`<issue ` 计数 0） |

> 首次执行报过一次 `BUILD FAILED`（错误里列一堆 `.class` 路径）。原因是**上一轮会话被中断**，
> `--rerun-tasks` 删了输出目录没重建完；**重跑即通过**，与代码无关。

### 任务：左侧节次编号显示不全

**结论：在 13 / 20 / 30 行、总课表与单周课表两个视图上均无法复现 → 判定已修复。**

真实根因（早已修掉）：日列高度曾用 `rowTop(periods)`（第 p 行的**上边缘**）计算，
使每个日列比它要定位的行**矮一整行**，网格内容因此比编号栏矮一行，
纵向滚动范围带不出最后一行。修复是 `dayColumnRows(periods)`，
引入提交 **`1213ef7`**（远早于任何现存截图）。`WeekGridRowsTest` 为该缺陷而写。

### 本次更正了上一版交接文档的一处**错误机制解释**

上一版 D-4 写「`scrollTo` 会被自身内容高度夹取，只有内容高度与网格完全相等时才跟得上」。
**两句都不成立**：

1. `View.scrollTo` **不做任何 clamp**（AOSP 实现只写 `mScrollX/mScrollY`），
   编号栏跟得上与两栏是否等高**无关**；
2. 两栏内容高度**并不相等**——编号栏每行 `dp(54)` 各自取整后相加，
   网格日列对总高一次取整 `dp(periods × 54)`。实测差 3px(13 行) / 5px(20 行) / 7px(30 行)，
   编号栏偏高；差值符号取决于屏幕密度。这是**外观瑕疵**（≤15px），**不是**原缺陷。

### 设备验证方法（可复用）

1. `adb shell run-as com.weihe.schedule tee <prefs 路径> < 本地文件`
   —— 这是往 debuggable 应用写 SharedPreferences 的**可行路径**；
   `run-as ... sh -c 'cat > ...'` 与 `run-as ... cp /sdcard/...` 都**不行**（SELinux 拒绝）。
   注意：`adb shell` 的 stdin **会做 LF→CRLF 转换**，回读时又转回来，XML 解析不受影响。
2. 注入「探针」课程（第 15 / 20 / 30 节，放在**周日**列以免打乱既有布局），
   `am force-stop` 后重启，用 `uiautomator dump` 读**真实屏幕坐标**逐行核对（不靠目测）。
3. 测完**把原始 prefs 写回**，确认页面恢复「23 条安排」。

### 实测数据

| 规模 | 结果 |
|---|---|
| 13 行 | 编号 1–13 齐全；块顶 1006 vs 编号 6 顶 1007 → **Δ=1px** |
| 20 行 | 编号 1–20 齐全、行高**均匀 142px**；探针15 块顶 1844 vs 编号 15 顶 1847 → **Δ=3px** |
| 30 行（`MAX_PERIODS`） | 编号 1–30 齐全，探针30 与编号 30 对齐，**最后一行完整可见** |
| 底部遮挡 | 30 行时末行底边 **y=2145**，FAB 顶边 **y=2172** → 未遮挡（`FAB_CLEARANCE_DP=78` = FAB 22+56dp，精确吻合） |
| 滚动同步 | 滚到底 / 中段 / 横向滚到周五周六周日，编号栏始终跟随，Δ ≤ 3px |
| 视图切换 | 总课表 ↔ 单周课表反复切换（含行数 30→10 的收缩路径）**未崩溃** |

### 未验证 / 限制

- 只有模拟器 `emulator-5554`，**没有真机**。
- **未实测改系统字号后的网格**（`periodCell` 用 `BODY_LARGE=14sp` + 固定 142px 行高，理论上不裁切）。
- 两栏内容高度不等这件事**仍无测试覆盖**（见 G-4）。
- Excel 导入的设备流程见 **M 节**（本轮随后完成）。

---

## M. Excel 导入全流程设备验证（2026-10-13，**首次走通**）

**本轮性质**：**验证**，**没有改任何业务代码**。样本沿用 E3/E4 留下的
`build/importtest/timetable.xls`（`build/` 被 gitignore，**仓库里没有 `.xls` 样本**）。

### 样本（JVM 侧先读回确认）

`MakeXls.java` 生成：表头 `星期一`..`星期日`，A 列 1-based 节次 1–4，4 个课程单元格。
JVM 上 `ExcelTimetableReader.readWithReport` 结果：
`usable=true courses=4 issues=0`，5 条安排：

```
高等数学  w1-16 parity=0  day1 p1  陈老师 A101
大学英语  w1-16 parity=1  day1 p2  李老师 B202
数据结构  w1-8  parity=0  day3 p1  王老师 C303
数据结构  w11-16 parity=0 day3 p1  王老师 C303
线性代数  w2-16 parity=2  day5 p3  赵老师 D404
```

### 设备实测（`emulator-5554`，Pixel_10 / API 37 / 1080×2424 @420）

入口：设置 → **课表数据 → 导入课表** → 系统 `ACTION_OPEN_DOCUMENT`。

| 环节 | 结果 |
|---|---|
| 文件选择器 | 打开即停在 **Downloads**，`timetable.xls` 可见可点 ✅ |
| 预览弹窗 | `识别到 4 门课程，共 5 条安排`、`文件内容已全部识别。`、`导入将覆盖当前课表` |
| 默认开学日期 | `2026-09-14`（= 设置页已存值，不是今天）；生效行 `2026-09-14，即本学期第 1 周周一` |
| 默认总周数 | `18 周`（= 目标学期已存值）；`课程可排范围：9/14 - 1/17，共 18 周` |
| 覆盖导入 | 日期与周数均未改 → **不弹二次确认**，直接落盘 |
| **落盘内容** | **与文件逐字段一致**（周次 / 单双周 / 星期 / 节次 / 教师 / 地点 / 颜色）✅ |
| 非连续周次 | `1-8,11-16周` → **两条记录**，未被拉平成 `1-16` ✅ |
| 同课同色 | 两条「数据结构」颜色相同 ✅ |
| 学期设置 | `semester_start` 仍 `2026-09-14`；`semesters` **未新增 `weeks` 键** ✅ |
| 撤销快照 | 写入 `courses_previous:<id>`，内含导入前 **23 条** ✅ |
| 首页 | `9/14 开学`、`第5周 · 单周`、今天 10/13 周二无课 → 正确空状态 ✅ |
| 总课表 | `5 条安排`；大学英语带 `· 单周`；数据结构显示为 `第1-8周` + `第11-16周` 两块 ✅ |
| **第 5 周（单周）** | 大学英语**在**、线性代数**不在**（周五列空）✅ |
| **第 6 周（双周）** | 线性代数**在**（周五 D404）、大学英语**不在** ✅ |
| 撤销上次导入 | 二次确认文案完整；确认后回到 **`23 条安排`** ✅ |
| 撤销的数据等价性 | 与导入前**逐字段集合相等**；`semester_start`/`semesters`/`bell_schedules`/`default_schedule_id` **逐字节相同**；**唯一差异是快照键按设计被消费** ✅ |

### 本轮发现的两条新问题（**均为既有问题，未修**）

- **G-11 导出摘要把"条安排"说成"门课程"**：设置页副标题 `N 门课程` 里的 N 是
  `BackupCodec.courseCount()` = 各学期 `courses.size()` 之和，即**安排条数**。
  导入前 23 条/14 个课名 → 显示 `23 门课程`；导入后 5 条/4 个课名 → 显示 `5 门课程`。
  **同一份代码在导入弹窗里是对的**（`4 门课程，共 5 条安排`），所以是措辞不一致。
- **G-12 模拟器截图是缩放显示的**：`screencap` 是 1080×2424，预览窗口按 480×1073 显示
  （比例 **2.25**）。**按截图目测坐标点屏幕会点到 2.25 倍偏移处**
  （本次因此误点了选择器的 "Large files" 筛选条，导致文件列表变空）。
  **一律用 `uiautomator dump` 的 `bounds` 取真实设备坐标。**

### 未验证

- 导入**损坏 / 不可读**文件的分支（设备上有 `broken.xls`、`unreadable.xls`，
  以及真实学校文件 `2026-2027第一学期-学期课表.xls`，均未走）。
- `.xlsx`（jxl 只支持旧式 `.xls`，代码即如此设计）。
- 导入**到非当前学期**（归档 E3 已覆盖，本轮未复测）。

---

## N. 上课提醒：核实通知形态 + 自定义提前分钟数（2026-10-13）

**本轮性质**：**核实 + 新功能**。改了 4 个生产文件 + 3 个测试文件，全部在提醒模块内。

### 一、改动前通知的**实际**形态（读代码得出，不是照抄文档）

- **系统通知**，渠道 `class_reminders`「上课提醒」，`IMPORTANCE_HIGH`，`enableVibration(true)`，
  描述「在课程开始前提醒你，包含课程、时间与教室。」；`minSdk 26` 所以没有任何版本分支。
- **标题 = 课程名本身**；正文 = `M月d日 EEEE HH:mm · 地点 · 教师` + 换行 + `N 分钟后开始`；
  `BigTextStyle` 可展开；`setWhen(上课时刻)` + `setShowWhen(true)` → 通知头显示「in 5h」。
- **点击只打开 `MainActivity`，不带任何 extra** —— 与那门课无关。
- 通知 id = `notificationId(courseId, epochDay)`；计划窗口 7 天、上限 10 条。
- 调度用 `AlarmManager.setAndAllowWhileIdle`（**故意非精确**，因此不申请 `SCHEDULE_EXACT_ALARM`）；
  每次 `sync()` 先按旧 ledger 取消**全部**槽位再重注册 → 幂等、无重复；`ReminderRestoreReceiver`
  在 `BOOT_COMPLETED` 等事件后重建。
- 提前分钟数存 `SharedPreferences("weihe_schedule_reminders")` 的 `lead_minutes`（int），
  与课程存储**分开**（备份/恢复不碰提醒设置）。

### 二、新增：1–180 分钟自定义提前时间

- `ReminderPlan.MIN_LEAD_MINUTES` 5 → **1**，`MAX_LEAD_MINUTES` 15 → **180**。
- **新增 `QUICK_LEAD_MINUTES = {5,10,15}`**：快捷项原来由 `{MIN, DEFAULT, MAX}` 推导，
  带宽一变就会变成「1 分钟」「180 分钟」。现在两者分开。
- **新增纯函数 `parseLeadMinutes(String)`**：拒绝而非修复（空、空白、单词、`2.5`、`-10`、
  `25 分`、`0`、`181`、超 int 全部 null）。可 JVM 测试，不依赖设备。
- `SettingsActivity`：第 4 个「自定义」色块 + 数字输入对话框（`TYPE_CLASS_NUMBER`），
  校验失败沿用项目既有的 `EditText.setError` + `Action.validated` 返回 false（对话框不关）。
- **没有引入「自定义模式」这个状态**：只有一个数字，快捷项与手输都写同一个键。因此
  没有新键、没有迁移、老用户的 5/10/15 原样读回（5..15 ⊂ 1..180）。

### 三、通知内容与点击（按需求做的可用性改进）

- 标题加前缀：`上课提醒 · <课程名>`（通知栏上方已显示应用名，光有课程名看不出要发生什么）。
- 正文加**节次**：`第 X-Y 节`（单节写 `第 X 节`，不写 `3-3`）。为此 `rule/Reminder`
  新增 `startPeriod`/`endPeriod`（含 equals/hashCode）——它是每次从课程现算的 DTO，
  **不是存储格式**，闹钟 ledger 里仍只有 slot/courseId/epochDay/triggerAt。
- **点击改为打开 `CourseDetailActivity` 并带 `EXTRA_COURSE_ID`**（稳定课程 id，不用名字或列表位置）。
  **必须去掉 `FLAG_ACTIVITY_CLEAR_TOP`**：详情页在 `onCreate` 读 extra 且**没有 `onNewIntent`**，
  带 CLEAR_TOP 会把新 id 交给一个不会重读的旧实例 —— 用户点 A 课却看到 B 课。
- 未动通知渠道、权限策略与精确闹钟策略。

### 四、本轮发现并**修掉**的既有缺陷

**正文星期用 `EEEE`，跟随设备语言。** 本机（英文 locale）实测是 `10月13日 Tuesday 10:40 …`。
中文界面里混英文星期，且显示成什么由设备语言决定。改为复用项目自己的
`ScheduleUtils.dayName()`，并加测试锁住「正文不得出现任何英文星期名」。

### 五、与本轮需求冲突的既有测试（**已更新，特此报告**）

| 测试 | 改动 | 原因 |
|---|---|---|
| `NonDefaultScheduleFlowTest.theDefaultReminderLeadIsStillFifteenMinutesOrLess` | 改名 `theReminderLeadBandIsStillItsOwnSetting`，断言改为「带宽自洽 + 仍拒绝带外 + MAX > 一节时长」 | 需求要求 1–180，`supports(20)` 必须为真 |
| `ReminderPlanTest.aLeadTimeOutsideTheSupportedBandIsRefused` | 带内 1/5/10/15/20/180；带外 0/−10/181/1000 | 同上 |
| `ReminderPlanTest.theDefaultLeadTimeIsOneOfTheThreeOffered` | 改名 `theDefaultLeadTimeIsInsideTheBand`（断言不变） | 带宽不再是三个值 |
| `ReminderScheduleTest.anEmptyVenueOrTeacherLeavesNoGapBehind` / `aVenueWithoutATeacherStillReadsProperly` | 分隔符 0→**1**、1→**2**，保留「不出现 `· ·`」断言 | 正文新增了真实的「节次」字段，必然占一个分隔符 |

**四处都没有为了让代码通过而放宽**：断言要么更严格，要么原意（空字段不留多余分隔符）完整保留。

### 六、设备实测（`emulator-5554`，Pixel_10 / API 37 / 1080×2424 @420）

| 项 | 结果 |
|---|---|
| 四个色块 | `5 分钟`(79–230) `10 分钟`(251–419) `15 分钟`(440–610) `自定义`(631–788)，右侧余 ~210px |
| 对话框 | 标题/正文/预填当前值 15；**数字键盘** |
| `999` | `请填写 1–180 之间的整数`，对话框不关，存储不变 |
| 空 | `请填写提前提醒的分钟数`，同上 |
| 保存 25 | 存储 15→25；`已开启 · 提前 25 分钟 · 已安排 10 条提醒`；第 4 色块变 `25 分钟` 且选中 |
| **重新调度** | ledger 仍 10 条、槽位与课程不变，**每条触发精确前移 10 分钟** |
| 重启 | `force-stop` 后仍 25 |
| **通知真实内容** | `dumpsys notification --noredact`：channel `class_reminders`、`BigTextStyle`、`AUTO_CANCEL`、importance 4；标题 `上课提醒 · 最优控制与状态估计`；正文 `10月13日 周二 13:20 · 第 6-7 节 · 教学楼F楼F106多 · 李海波` + `25 分钟后开始` |
| **点击** | 打开 `CourseDetailActivity`，内容与通知那门课完全一致 |
| **重复抑制** | 同槽位再广播 → 不产生第二条 |
| **关闭提醒** | `scheduled` 立即 `[]`；再广播 → 通知数 0 |
| 恢复原状 | 写回原 prefs，页面回到 `提前 15 分钟 / 10 条` |

**触发通知的可行手法**（`exported="false"` 的接收器）：

```
adb shell run-as com.weihe.schedule am broadcast --user 0 \
  -a com.weihe.schedule.action.REMIND \
  -n com.weihe.schedule/.reminder.ReminderReceiver \
  --ei slot <槽位> --el trigger_at <毫秒>
```

`--user 0` 必须显式给，否则报 `INTERACT_ACROSS_USERS`。槽位与毫秒从 `scheduled` 键里取。

### 七、未验证

- 通知是**广播手动触发**的，**没有等真实 `AlarmManager` 到点**（下一次真实触发在设备时间 10:15）。
- `BOOT_COMPLETED` 后的自动重建未实测。
- **自定义作息表**下的提醒时刻未单独实测（当前 23 门课都命中「教学楼作息」一张表）。
- 锁屏形态未测；大字号下四色块是否换行未测。
- `ReminderSettings` **仍无 JVM 测试**（需要 `Context`，项目没有对应测试接缝）；持久化与老用户
  兼容性是设备实测的。

---

## O. 追加记录 — 2026-10-14：六阶段修复的整合复审（逐文件核实）

> 本轮**不采信任何前轮自述**，全部以当前源码与实跑结果为准。结论见
> [`AGENT_HANDOFF.md`](AGENT_HANDOFF.md) 的 **B-2** 节（当前权威状态表）。

### O-1 复核结果：六轮里只有四轮真的留下了代码

| 阶段 | 声称 | 实际 |
|---|---|---|
| 一 备份恢复 | 修复 `commit()` 失败的数据一致性 | **当轮即被整体撤销**，`git diff` 为零。本轮才补上"先核验再报告"的一半 |
| 二 JSON 深度 | `MAX_DEPTH` 限制 | 落地 ✅ `MiniJson.java` +68 |
| 三 版本号溢出 | 先按 `long` 比较 | 落地 ✅ `BackupCodec.java` +42/−7 |
| 四 提醒调度 | 先注册后取消 | 落地 ✅ `ReminderScheduler.java` +70/−21 |
| 五 导入/构建 | 规模校验 + `.gitattributes` | 落地 ✅ `ExcelTimetableReader.java` +49/−4、`.gitattributes` 新增 |

### O-2 阶段一的完整来龙去脉（避免下一个 Agent 重走）

1. 原始缺陷：`restoreFrom()` 的 `if (!editor.commit())` 分支**不核验**就返回
   `RestoreReport.unchanged("写入失败，当前数据保持不变")`。注释声称
   *"SharedPreferences applies an editor all-or-nothing"* —— **与平台行为不符**：
   `EditorImpl.commit()` **先**把 editor 应用到内存 `mMap`，**再**写盘；写盘失败时
   **不回滚内存**。于是可能出现"内存已是新数据、文件还是旧数据"，而用户被告知"数据未变"。
2. 第一次尝试：加核验 + 失败则 `rollBackTo()` 回滚 + `FakePreferences.failedCommitKeepsChanges()` +
   5 个回归测试。**测试失败**：数据确实回滚了，但被拒的那次提交**顺带写入了
   `restore_pending` / `restore_snapshot`**，而这两个键**不在恢复范围内**（`isScopedKey` 是正向白名单），
   于是回滚后的 `verifyStored()` 不通过，状态落在 `NEEDS_ATTENTION` 而非 `ROLLED_BACK`。
   → 判定"修复不完整"，**整体撤销**（未放宽断言）。
3. 本轮采用**只做能证明的那一半**：核验一致 → 仍报 `unchanged`（保持既有契约）；
   核验不一致 → `reload()` + `needsAttention`，**不做回滚**（回滚是又一次可能同样被拒的提交，
   半途而废的回滚比一个说清楚的状态更危险）。
4. **遗留**：`FakePreferences` 仍无法模拟"拒绝但内存已变"，该分支**无测试**。
   下一步若要做回滚，**必须先解决 `restore_pending`/`restore_snapshot` 的清理**。

### O-3 同源缺陷（未修，P1）

`CourseRepository` **L715 与 L766** 的注释仍写 *"an editor applies all-or-nothing, so a refused
write cannot save the date…"* —— 与阶段一**同一个错误假设**，涉及导入学期起始日期与
`courses_backup` 的写入。**与阶段一同源，应一并处理。**

### O-4 阶段五历史结论的证伪

"归档中的 `gradlew` 使用 CRLF" —— **不成立**。仓库 blob 与工作区均为 **LF**（`gradlew` 8,752 字节，
不含 CR）。真实隐患是 `core.autocrlf = true` 且仓库无 `.gitattributes`，已用 `.gitattributes` 处理；
生效证据：git 警告由 "LF → CRLF" 变为 "CRLF → LF"。

### O-5 本轮自伤（如实记录）

本轮第一个脚本**先写入 `FakePreferences.java`、随后在下一个锚点抛错**，导致测试源集
**一度编译失败**；此时 Gradle 输出的 "999 项通过" 是**上一轮的陈旧 XML**，不是真实结果。
已确认该文件在本阶段前**无任何本地改动**，用 `git checkout --` 精确还原，未丢失他人工作。
**教训**：多步脚本必须**先完成全部锚点定位再写入任一文件**（AGENTS.md §8 已有同类记录）。

### O-6 本轮实跑结果

```
& '.\.gradle-local\bin\gradle.bat' "-Dorg.gradle.vfs.watch=false" --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```
**54 个测试类 / 999 项 / 999 通过 / 0 失败 / 0 错误**；`assembleDebug` BUILD SUCCESSFUL；
`lintDebug` `No issues found.`。**模拟器与真机均未执行。**

### O-7 真机回归清单（13 项，全部待做）

备份恢复与恢复后强杀重启；损坏 / 深层嵌套 / 未来版本备份的拒绝；Excel 正常导入、取消、
超限文件；课程增删改后的提醒；学期切换；重启设备后的提醒恢复；撤销通知权限的表现；
API 26 与 API 34 上的权限弹窗、SAF 选择器与状态栏。**完整表格见本轮交付报告。**


---

## P. 追加记录 — 2026-10-14：图标替换与个人自用打包

### P-1 图标问题：不是资源旧，是启动器缓存

用户反馈"桌面图标和启动画面还是旧的"。逐项核实后推翻了最初假设：

1. **`tools/IconForge.java` 已存在**（529 行，自带头部注释说明所有图层由它从源图生成）。
   用它针对根目录 `icon.png` 重跑，输出与仓库中**既有资源逐字节相同**（27 个文件 MD5 全部一致），
   `git status` 中 `app/src/main/res` 也**无任何改动**。
   → 结论：**仓库里的图标资源本来就是这张 `icon.png` 生成的**，不存在"旧资源没替换"。
2. 于是"仍是旧图标"的原因只能是 **启动器图标缓存**。用
   `am force-stop com.google.android.apps.launcher` 清缓存后重装，
   应用列表里「拾课」立即显示为新设计（米白圆底 + 深绿日历 + 橙色块 + 右下角时钟），
   完整、不裁切、无黑块。**已目视确认**（证据：`tools/out/icon-drawer.png`）。
3. 页内 Logo 是**另一个**问题：`AboutActivity` 用了 `R.mipmap.ic_launcher`，
   而它是 `<adaptive-icon>`，平台只保证中间 72dp 可见，放进 `ImageView` 会显得偏小。
   改为专用的 `R.drawable.ic_brand_logo`（同一原图裁掉透明边距后**等比**缩放到 512×495）+ `FIT_CENTER`。

### P-2 启动画面：项目此前根本没有配置

全项目检索 `windowSplashScreen*` **零命中** → Android 12+ 的启动画面是系统拿应用图标自行拼的。
新增 `res/values-v31/themes.xml`，**完整重写** `Theme.WeiheSchedule`
（含父样式、全部既有条目、并重复 v27 的 `windowLightNavigationBar`，因为该文件在 API 31+ 会整体取代 v27 的定义），
再显式指定 `windowSplashScreenAnimatedIcon = @mipmap/ic_launcher_foreground` 与
`windowSplashScreenBackground = @color/surface`（与 `windowBackground` 同色，避免闪色）。

**教训**：首次构建 `mergeDebugResources FAILED`，原因是我在 XML 注释里写了字面量 `--`（XML 注释禁止）。
构建失败而 `install` 仍成功 → 装的是旧包 → 据此拍的截图**全部无效**。修正注释重建后才取得有效证据。

### P-3 个人自用打包

- 应用身份：`com.weihe.schedule` / `versionCode 1` / `versionName 1.0` / `minSdk 26` / `targetSdk 34`。
- **Debug APK**：1136 KB，`apksigner verify` **退出码 0**，签名者 `CN=Android Debug`
  （证书 SHA-256 `d623aa4a...`）→ **可安装，个人自用推荐**。
- **Release APK**：866 KB `app-release-unsigned.apk`，**未签名、装不上**（`release {}` 无 `signingConfig`）。
- 因 `minifyEnabled false` 两边一致，Debug 包与 Release 行为相同。
- **换签名必须卸载重装，而卸载会清空课表** → 已要求用户先「导出备份」。

---

## Q. 追加记录 — 2026-10-16：课表总览的自适应宽度与双指缩放

### Q-1 需求

「课表总览」默认把周一至周日七列完整显示在手机可用宽度内，并加双指缩放与缩放按钮，
让用户能放大看清课名。**不重新设计课表 UI**，保留卡片风格/颜色/文字规则/交互。

### Q-2 先做的事：确认回退已完成

任务开始前先查 `git status` / `git diff` / `git log`：分支 `main`、HEAD `0ef0f82`、
工作区干净（只有既有的未跟踪项 `.workbuddy-ai/`、`app/release/`）、无暂存、无 stash。
`WeekGridActivity.java` 是 961 行的原实现（`DAY_WIDTH_DP = 118`、`HorizontalScrollView` 都在）。
→ 上一次紧凑化改造确已完全回退，可以直接在其上开发，无需保护其他未提交改动。

### Q-3 定位：改的是哪一层

课表用 **View 布局**（`GridLayout` + `FrameLayout` + `TextView`），不是 Canvas 自绘。
所以缩放有两条路：对成品做变换，或按新尺寸真实重排。**选了后者**：

- 变换（`setScaleX`）在放大时文字是拉伸的，糊；而「放大是为了看清课名」，糊就白放了。
- 真实重排后文字按最终字号绘制，且**触点命中与视觉天然一致**（没有变换需要反算）。

代价是缩放期间要重建视图，用 `postOnAnimation` 把一帧内的多次 `onScale` 合并成一次重建。

### Q-4 改动清单

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/weihe/schedule/rule/GridZoom.java` | **新增**。纯几何：缩放范围/步进、按视口算列宽、按缩放算列宽与行高、是否需要横滚 |
| `app/src/test/java/com/weihe/schedule/rule/GridZoomTest.java` | **新增** 18 项 |
| `app/src/main/res/drawable/ic_remove.xml` | **新增**，缩小图标（与 `ic_add` 同款字形） |
| `app/src/main/java/com/weihe/schedule/WeekGridActivity.java` | 列宽/行高改为视口驱动；`ScaleGestureDetector`；缩放按钮胶囊 `[−] [适应] [+]` |

> **2026-10-16 追加修改（用户要求）**：删掉「整表」按钮，
> `GridZoom.MIN` 由 `0.6` 改为 `= FIT`（1.0），同时删除 `zoomToFitPeriods()` 及其 4 项测试。
> 理由：下限就是「适应屏幕」，再小没有任何可看的东西 —— 七列已经铺满宽度，
> 更小只会让行矮到读不动课名。控件只剩 `[−] [适应] [+]`，「缩小」按到底也只是回到七天同屏。

**未动**：数据格式、模型、其他规则层文件、配色常量、卡片内容与字体、点击行为、
提醒、导入、备份恢复、单周课表的数据与周次逻辑。

### Q-5 关键实现点（给下一轮的坑）

1. `ScaleGestureDetector` **必须挂在 Activity 的 `dispatchTouchEvent`**。挂在网格上时，
   某个消费了 DOWN 的课程块或 ScrollView 会把后续事件吃掉，检测器永远收不到第二根手指。
2. 双指落下时 `requestDisallowInterceptTouchEvent(true)` 阻止两个 ScrollView 抢手势
   （框架在下一个 DOWN 自动清标志，所以单指拖动不受影响）；手势结束补发 `ACTION_CANCEL`，
   否则最后一根手指抬起会被当成点击、误开课程。
3. **视口宽度用「屏宽 − 节次栏」估算**，不要用视口自身去算节次栏宽度 —— 那是个环。
   首帧用估算值，`OnLayoutChangeListener` 拿到实测值后重算，守卫是宽度真的变了。
4. `rowSpan()` 由 `dp(n * 行高)` 改成 `n * rowHeightPx`。原来的写法在行高变化后会与
   `rowTop()` 的取整不一致，让块边缘相对格线漂移。
5. 表头行宽 = 视口 + `maxHorizontalScroll()`，**缩放后要重算**（放大后能滚动的范围变大了），
   否则左滑时后面的星期会被裁掉。

### Q-6 门禁与设备验证

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

- **1017 项 / 0 失败 / 0 错误 / 0 跳过**（999 + 18 新增）
- `app-debug.apk` **1,167,008 字节**；lint **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`）

模拟器 `emulator-5554`（1080×2424 @420）实测：

| 项 | 结果 |
|---|---|
| 默认七天同屏 | ✅ 列头 x 边界 140/274/408/542/676/810/944/1078 → 正好铺满 1080px |
| 放大 ×4 | ✅ 列宽 134→322px（1.25⁴），显示 3 天，卡片恢复完整三行 |
| 再连按缩小 ×8 | ✅ **停在 134px × 7、七天同屏，不会比「适应屏幕」更小** |
| 缩放后点课程 | ✅ 命中准确，打开对应课程详情 |
| 单周课表 / 上下一周 / 回到本周 / 模式切换 | ✅ 全部正常 |

### Q-7 未验证与已知问题

1. **双指捏合手势本身没在设备上真跑过。** `adb root` 在生产版模拟器上不可用
   （`adbd cannot run as root in production builds`），无法用 `sendevent` 注入两指事件。
   按钮走的是同一条几何路径（已实测），但**手势识别本身需要用户手测**。
2. **默认（适应宽度）下课名会被截断**：411dp 屏每列约 51dp，中文一行只放 3–4 字；
   两门课重叠共用一列时每道约 25dp，名字被截到 1 个字。放大即可读全。
   这是「七天同屏」的必然结果，不是实现缺陷。若要默认就好读，需让**卡片文字随列宽自适应**
   （窄列只显示课名），但那会改到「文字规则」，本轮按要求未动。
3. 极小屏、横屏、平板未验证。缩放倍数未持久化（有意）。
4. **纵向一定会滚动**：行高 = 54dp × 缩放，已删除「整表」这种压矮行高的档位。
   13 节的数据在默认比例下末两节需滚动可见。

---

## R. 追加记录 — 2026-10-16：总课表里不同周次课程的叠放展示

### R-1 需求与定位

总课表（`WeekGridActivity` 的 `wholeSemester` 模式）里，同一天同一时间段但分属不同周次的课程，
原来每门各占一条横向子列，把星期列挤成窄条。改为**叠放卡片**：主卡片显示课名 + 后方边缘 +
「+N」，点击弹底部面板列出全部课程。

开工前先查 `git status`：HEAD `cc3b0c4`（缩放功能已被用户提交）、工作区干净，
所以本轮的缩放成果是安全的，直接在其上开发。

### R-2 实现选型：改在哪一层

课表是 View 布局。叠放有两条路：① 在渲染层把多个块画在一起；② **在分道之前就把
「互斥周次的替代课程」合并成一个单元**。

选了 ②。原因：分道打包（lane packing）本来就会把「起止节次相同」的课程分到不同子列，
把它改成「一个叠放组占一条子列」，就同时修好了**列宽**问题 —— 不是画得好看一点，
而是根本不需要那么多子列。而且这条路径与缩放天然兼容：缩放只改像素尺寸，
叠放改的是结构，两者正交。

### R-3 判定规则（最重要的一条）

**三条同时满足才叠放**：

1. 星期相同；
2. 起止节次**完全相同**；
3. **实际开课周次互不重叠**，用 `ScheduleUtils.occursInWeek` 逐周判断
   （所以同为 1–18 周但奇偶互斥的两门课**可以**叠放）。

只要有一周同时上课就不叠放，仍各占子列 —— **真实冲突绝不被藏起来**。

**单周课表为什么自动免疫**：它按选定周次过滤，同一周内同时间的两门课必然冲突，
`canStack` 恒 false。所以不需要为「不要套用到单周课表」加任何开关。

### R-4 改动清单

| 文件 | 改动 |
|---|---|
| `rule/CourseStacking.java` | **新增**（纯逻辑）：`sameSlot` / `weeksMeet` / `canStack` / `partition`（按周贪心分组）/ `unitsOf`（按时间段切段后分组） |
| `rule/WeekGridPlan.java` | `Block` 从「一门课」改为「一门课或一组替代课程」，新增 `courses()` / `courseCount()` / `isStack()`；`course()` 仍返回主课程；`clusterOf()` 的分道输入换成 `unitsOf()` |
| `WeekGridActivity.java` | 叠放渲染、`+N` 徽标、底部面板、行点击 |
| `rule/CourseStackingTest.java` | **新增** 22 项 |
| `rule/WeekGridPlanTest.java` | +8 项 |

**未动**：课程数据、任何课程记录、起止节次/周次/教室/教师、颜色持久化与 `CourseColorSync`、
新增/编辑/删除/导入/备份恢复/提醒、冲突检测、七天列宽、缩放功能。

### R-5 实现要点与坑

1. `unitsOf()` 依赖输入已按 `(startPeriod, endPeriod)` 排序：同一时间段的课程在排序后**连续**，
   所以一趟扫描即可切段，不必先建 map。**起始节次相同但结束不同是两个时间段**，
   切段条件必须同时比 start 和 end。
2. `partition()` 按周排序后贪心：每门课加入「第一组与之周次全不冲突的栈」。
   与 `WeekGridPlan` 的分道一样是贪心而非最优，但一个时段很少有多到能看出差别。
   栈内顺序即主卡片顺序，按开课周次升序 —— 主卡片显示最先开课的那门。
3. **`Block` 构造加空校验**：`courses` 为空会让 `course()` 抛
   `IndexOutOfBounds`，而这只会出现在将来的调用方写错时，早抛比晚崩好。
4. 叠放卡片**在左、上内缩**而不是右、下：向右会压到相邻日期列，向下会压到下一节。
5. **只露一条边缘**，不随门数增加条数；门数交给「+N」。
6. 叠放卡片**用「+N」换掉地点/教师行**：七列宽度下放不下，且「还有几门」更有用。
7. 面板用 `UiKit.bottomSheet`（既有组件），行点击前先 `dismiss()` 再 `startActivity`，
   与 `MainActivity.showWeekPicker` 的 `Dialog[]` 持有者写法一致。
8. `stackPeekPx()` 随行高缩放（`rowHeightPx / 12`，夹在 2–8dp），
   这样缩放到很小或很大时叠放都还看得见。

### R-6 门禁与设备验证

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

- **1047 项 / 0 失败 / 0 错误 / 0 跳过**（上一轮 1017，+30 = 新增 22 + 8）
- `app-debug.apk` **1,170,128 字节**；lint **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`）

模拟器 `emulator-5554`（1080×2424 @420）实测：

| 项 | 结果 |
|---|---|
| 原拆成两列的时段 | ✅ 周一 1–3「检测技术与信号处理 第2-5周 · 每周 +1」，一张卡片占满整列 |
| 三门替代课程 | ✅ 周五 8–9「自然辩证法概论」+「+2」 |
| 真实冲突仍分开 | ✅ 周二 10–11「最优化方法」与「模式识别原理」同为第2-9周 → 仍两条子列 |
| 面板 | ✅ 标题「周五 · 第 8—9 节」、副标题「3 门课程，分属不同周次」、三行含周次 |
| 面板行点击 | ✅ 打开对应课程详情，返回后课表状态保持 |
| 放大 ×2 | ✅ 列宽 134→209px，叠放边缘与「+N」位置仍正确 |
| 单周课表 | ✅ 无叠放、无「+N」，仍显示地点+教师，上/下一周正常 |

### R-7 未验证 / 已知问题

1. **双指捏合手势仍未真机验证**（`adb root` 在生产版模拟器不可用，注入不了两指）。
2. **默认（适应宽度）下课名仍会被截断**：411dp 屏每列约 51dp，中文一行 3–4 字。
   叠放只解决了「被挤成多条窄列」，没有改变单列宽度。
3. 极小屏、横屏、平板未验证；缩放倍数未持久化（有意）。

---

## S. 追加记录 — 2026-10-16：缩放同步放大文字、总课表精简信息、当前周置顶

### S-1 三项需求

1. 放大课表时卡片文字必须同步放大（改前只有几何变，文字写死）。
2. 总课表隐藏地点与教师，只留课名 + 周次（含单双周）。
3. 叠放组里"当前周正在上课的那门"排最上层。

开工前 HEAD `b29dc2f`（叠放功能已被用户提交），工作区干净。

### S-2 关键实现点

1. **`applyZoomPixels()` 是唯一的换算点。** 几何（列宽/行高）与卡片内部（三个字号、
   内边距、圆角）都在这里从 `zoom` 算出，Activity 用字段存、渲染时读。
   **每个值都是"基准 × zoom"重算，不是在上次值上累乘** —— 这是"不累积"的根因。
   所有公式放在 `rule/GridZoom.java`，纯函数、可 JVM 测。
2. **字号层级在任何缩放下都成立**：课名 11 > `+N` 10 > 周次 9（基准值）。
   已加测试逐缩放点断言。
3. **字号下限是预留保护**：当前缩放下限 = FIT（1.0），所以 `11 × 1.0 = 11` 永远高于下限 8。
   下限是为将来调低下限时不至于变成糊成一团。
4. **当前周判定口径**：`ScheduleUtils.currentWeek(semesterStart, LocalDate.now())`，
   与 App 其它地方（本周标签、今天高亮）同一个函数，**不是自然周号**。
   它本身会 clamp 到 `[1, 18]`，所以学期未开始时返回 1 —— 这是项目既有口径，
   不是本轮新发明的边界处理。
5. **"这门课这周上课吗"用 `ScheduleUtils.occursInWeek`**，周次范围 + 单双周都算进去，
   没有自己再实现一遍。
6. **排序键**：`当前周在上课 ? 0 : 1` → 开课周次 → 结束周次 → id。
   后三项保证"没有当前周课程"时次序稳定可预测。
7. **一个叠放组里最多一门课在任一给定周次上课** —— 这是"周次互不重叠"的直接推论，
   所以"当前周那门"是唯一答案。已加测试逐周（1..18）扫一遍验证。
8. **单周课表为什么不用改**：它按周过滤后每张卡片本来就只显示一门课，
   且地点/教师行按需求保留。`render()` 传的 `leadWeek` 在单周模式下是 `selectedWeek`，
   与总课表同一套代码路径，无需分支。

### S-3 改动清单

| 文件 | 改动 |
|---|---|
| `rule/GridZoom.java` | +5 个卡片缩放函数 + 基准/下限常量 |
| `rule/CourseStacking.java` | `partition`/`unitsOf` 增加 `currentWeek`；新增 `runsIn()`；排序改为当前周优先 |
| `rule/WeekGridPlan.java` | 新增 `plan(courses, currentWeek)` 重载 |
| `WeekGridActivity.java` | 5 个新字段 + `applyZoomPixels` 赋值；总课表删地点/教师行；`blockDetail` 简化；`render()` 传 `leadWeek` |
| 3 个测试类 | +18 项 |

`blockDetail` 原来的 `wholeSemester` 分支已成死代码（总课表不再调它），
已简化为只返回教师，并在 javadoc 里说明"数据未删，只是不显示"。

### S-4 门禁与设备验证

```
gradlew.bat -Dorg.gradle.vfs.watch=false --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

- **1065 项 / 0 失败 / 0 错误 / 0 跳过**（上一轮 1047，+18）
- `app-debug.apk` **1,170,872 字节**；lint **0 errors, 2 warnings**（仅既有 `IconXmlAndPng`）

模拟器 `emulator-5554`（1080×2424 @420）实测：

| 项 | 结果 |
|---|---|
| 总课表地点/教师 | ✅ 逐文本检查 0 处残留 |
| 周次与单双周 | ✅ 「第2-17周 · 单周」等准确 |
| 文字随缩放放大 | ✅ 课名 90→146px、周次 77→117px（缩放 1.0→1.5625） |
| 放大×3 后点适应 | ✅ 课名回到 90px（基准值），无累积 |
| 当前周置顶 | ✅ 周五 8–9 组「自然辩证法概论(第2-9周)」在前（第5周在上这门） |
| 单周课表 | ✅ 地点+教师照旧，无 `+N`，上一周/下一周正常 |

### S-5 未验证 / 已知问题

1. **双指捏合手势仍未真机验证**（`adb root` 生产版模拟器不可用）。
2. **默认宽度下课名仍截断**：411dp 屏每列约 51dp，中文一行 3–4 字。
   缩放是等比放大，不改变一行能放几个字。
3. 极小屏、横屏、平板未验证；缩放倍数未持久化（有意）。
4. 字号下限在当前范围不触发（预留保护）。

---

## T. 追加记录 — 2026-10-16：「回到本周」移到右下角悬浮操作区

### T-1 需求与改动

「回到本周」从页眉切换栏移出，改为右下角悬浮胶囊，与「添加课程」FAB 垂直堆叠。
只改 `WeekGridActivity.java`，未动总课表布局、数据、缩放、单周课表内容。

### T-2 关键实现点

1. **一个容器装两个按钮**：`LinearLayout`(vertical, `Gravity.END`) 装胶囊 + FAB，
   间距 `ACTION_GAP_DP = 6`。不要各自贴角摆放 —— 那样文字一长或屏幕一窄就会重叠。
2. **底部留白要覆盖整堆**：`FAB_CLEARANCE_DP(78)` → `ACTION_CLEARANCE_DP(110)`。
   算法：`12dp 边距 + 56dp FAB + 6dp 间距 + 34dp 胶囊 = 108dp ≤ 110dp`。
   留白加在**滚动内容**上（grid/labelColumn 的 bottom padding），所以末节课程
   滚到底依然完整可见，不靠缩窄列宽或改行高。
3. **胶囊三态**（沿用原 chip 的启用规则并细化）：
   总课表 → 隐藏（与周次步进器一起，没有「周」可回）；
   已在本周 → **可见但禁用**（不隐藏，避免按钮在拇指下跳动）；
   别的周 → 可用 + 强调色底。
4. **`styleTodayChip()` → `styleTodayButton()`**：原来样式的是一个 chip，
   现在要样式整个胶囊（背景 + enabled + clickable），文字颜色单独设。
   `todayChip` 字段语义变了 —— 现在是胶囊里的**文字**，胶囊本身是新字段 `todayPill`。
5. **`createTodayPill()` 用 `UiKit.inlineIcon`** 拿 ImageView，不用新 import。
6. **方法命名**：一开始我把方法和字段都叫 `todayPill`（Java 合法但极易看错），
   改成 `createTodayPill()`，与文件里 `createHeader`/`createModeBar` 的惯例一致。

### T-3 门禁与设备验证

- **1065 项 / 0 失败 / 0 错误 / 0 跳过**（纯布局改动，未新增规则逻辑）
- `app-debug.apk` **1,171,260 字节**；lint **0 errors, 2 warnings**

模拟器 `emulator-5554`（1080×2424 @420）实测：

| 项 | 结果 |
|---|---|
| 页眉 | ✅ 只剩 返回/标题+周次/上一周·下一周/总课表·单周课表 |
| 胶囊位置 | ✅ y=2077..2166，FAB y=2182..2329，右边缘都在 1048，间距 6dp |
| 回到本周 | ✅ 第10周(11/2-11/8) → 本周·10/12-10/18 |
| 添加课程 | ✅ 打开编辑器 |
| 缩放控件 | ✅ 左下角位置与交互未变（x=58..368，与胶囊水平距 524px） |
| 滚到底 | ✅ 末节文字止于 y=1714，胶囊顶 y=2077，空 147dp |

### T-4 ⚠️ 数据事故（必须记住）

为排除「装的是旧包」的疑虑执行了 `adb uninstall` 再重装 ——
**`adb uninstall` 会连应用私有数据一起删掉**，模拟器上 23 门课程被清空。
已用用户的 `weihe-backup-20261014-1547.json` 经应用内
「设置 → 课表数据 → 从备份预览 → 覆盖恢复」完整恢复
（首页确认 9/14 开学、课程逐条回来；结果页确认 1 学期 · 23 门 · 3 张作息表）。

**该备份是 10-14 的**，若用户在 10-14 之后又在设备上改过课表，那部分无法找回。

**教训：验证 UI 只用 `install -r`，绝不 `uninstall`。**
另外把备份 push 到了设备 `/sdcard/Download/`，文件仍留在那里。

---

## U. 追加记录 — 2026-10-16：「回到本周」的按钮外观

### U-1 问题

上一轮（T 节）把「回到本周」**挪到了右下角**，但外观没动：`SURFACE_CONTAINER_HIGH`
浅灰绿底 + `ON_ACCENT_CONTAINER` 深棕字、**上下内边距为 0**、无阴影 —— 读起来仍像
"图标 + 文字标签"，不像按钮。本轮只改样式与尺寸。

### U-2 改法（只动 `WeekGridActivity.java`）

1. **底色**：`SURFACE_CONTAINER_HIGH`（`#E8EFEA`，几乎与页面同色）→
   **`SURFACE_CONTAINER`（纯白 `#FFFFFF`）**。页面是薄荷色 `#F3F7F3`，白底能明确浮出来。
2. **图标+文字**：`ON_ACCENT_CONTAINER`（深棕 `#4A2008`）→
   **`Palette.PRIMARY`（深绿 `#11584E`）**，与绿色主题一致（用户明确要求"不要过浅的灰"）。
   图标要单独改色 → 新增 `todayIcon` 字段存 ImageView，`styleTodayButton()` 里
   `setColorFilter(ink)`。
3. **阴影**：`UiKit.elevate(pill, RADIUS_FULL, live ? ELEVATION_MEDIUM : ELEVATION_NONE)`。
   禁用态归零，配合灰底传达"不可用"。
4. **内边距**：原来是 `dp(SPACE_S), 0, dp(SPACE_M), 0` —— **上下为 0**，文字贴边。
   改成四边一致 `dp(SPACE_M), dp(SPACE_S), dp(SPACE_M), dp(SPACE_S)`。
5. **高度 34 → 42dp**：既更像按钮、点击区更大，又明显低于 FAB 的 56dp（保持主次）。
6. **间距 6 → 8dp**，**底部留白 110 → 122dp**。
   留白算法要跟着尺寸走：`12dp 边距 + 56dp FAB + 8dp 间距 + 42dp 胶囊 = 118dp`，
   取 122dp 留 4dp 余量。**改尺寸必须同步改留白**，否则会遮挡末节课程。

### U-3 三态与实测取色（1080×2424 @420）

| 状态 | 底色 | 图标/文字 | 阴影 |
|---|---|---|---|
| 可用 | `#FFFFFF` | `#11584E` | 3dp |
| 已在本周 | `#E8EFEA` | `#4E5C57` | 无 |
| 总课表 | 隐藏 | — | — |

描边两态都是 `#DFE7E1`（`OUTLINE_VARIANT`），所以禁用态仍是"按钮形状"。

### U-4 布局实测

- 胶囊右边缘 1048px、y≈2051..2161（高 110px）；FAB 右边缘 1048px、y=2182..2329
  → **右对齐、右边距一致（12dp）、垂直间距 8dp（21px）**
- 缩放控件 x=58..368，与胶囊 x≈892..1048 水平距 524px
- 滚到底：第 13 行止于 y=2025，胶囊顶 y=2051，空 26px

### U-5 门禁与设备验证

- **1065 项 / 0 失败 / 0 错误 / 0 跳过**（纯视觉改动，无新增规则逻辑）
- `app-debug.apk` **1,171,320 字节**；lint **0 errors, 2 warnings**

模拟器实测（**只用 `install -r`**，见 T-4 的教训）：
按钮外观/右对齐/间距/点击区/禁用态/行为（第7周 → 本周，课程同步刷新）/
缩放（放大 134→168px、适应回到 7 天 134px）/滚到底不遮挡 / 数据完好（23 门）全部通过。

### U-6 未验证

极小屏、横屏、平板、三键导航模式未实测（本机为手势导航）。

---

## V. 追加记录 — 2026-10-16：「回到本周」改为纯图标圆形 FAB

### V-1 需求

把「回到本周」改成与「添加课程」**完全同款的圆形 FAB**，只换内部图标，
界面不显示任何文字；两个按钮右下角垂直排列、右对齐、不重叠、不遮挡课程。

### V-2 接手时的状态（重要）

**本轮接手时，工作区里的代码已经实现了这项改动**（`M WeekGridActivity.java`，未提交）。
`createTodayPill()` / `TODAY_PILL_HEIGHT_DP` 都已不存在，取而代之的是
`todayFab = UiKit.fab(this, R.drawable.ic_today, "回到本周", view -> backToThisWeek())`，
`ACTION_CLEARANCE_DP` 也已按两个 56dp FAB 调成 136。

但 **`AGENT_HANDOFF.md` 的 P 节仍在描述旧的"白底胶囊 + 文字"**，已与代码脱节。
本轮做的是：**核对代码是否满足全部要求 → 构建 → 设备实测 → 把文档改成与代码一致**。

### V-3 实现要点（代码现状）

1. **两个按钮都走 `UiKit.fab(...)`** —— 圆角、直径、配色、阴影、涟漪、点击区
   全部由同一段代码产生，结构上不可能不一致；差别只在传入的图标。
   上方 `ic_today`（日历 + 标记日期），下方 `ic_add`（+）。
2. **一个容器装两个**：`LinearLayout`(vertical, `Gravity.END`)，间距
   `ACTION_GAP_DP = 8`。容器在 FrameLayout 里是 `END | BOTTOM`，
   所以**隐藏上方按钮不会让下方 FAB 移动**（底部对齐）。
3. **底部留白 136dp** = `12dp 边距 + 56 + 8 + 56 + 4dp 余量`。
   加在滚动内容的 bottom padding 上，所以末节课程滚到底仍完整可见。
4. **三态**：可用 → 标准 FAB；已在本周 → 压平成 `SURFACE_CONTAINER_HIGH` +
   灰图标 + 无阴影（**位置尺寸不变**，不会在拇指下跳动）；总课表模式 → 隐藏。
5. **无障碍**：`contentDescription = "回到本周"`；界面无文字节点（dump 实测 0 处）。

### V-4 设备实测（1080×2424 @420）

| 元素 | 位置 | 尺寸 |
|---|---|---|
| 回到本周 FAB | x=901..1048, y=2014..2161 | 147×147px = **56×56dp** |
| 添加课程 FAB | x=901..1048, y=2182..2329 | 147×147px = **56×56dp** |

- 右边缘都在 1048（右边距 12dp）；垂直间距 21px = 8dp
- 取色：可用态两个都是 `#11584E`；已在本周时上方转 `#E8EFEA`，下方仍 `#11584E`
- 滚到底：第 13 行止于 y=1988，FAB 顶 y=2014 → 空 26px（10dp）
- 缩放控件 x=58..368，与按钮组水平距 533px
- 行为：第7周 → 本周 ✅；点「+」开编辑器 ✅；数据完好（23 门）✅

### V-5 门禁

- **1065 项 / 0 失败 / 0 错误 / 0 跳过**
- `app-debug.apk` **1,171,092 字节**；lint **0 errors, 2 warnings**

### V-6 关于「完全一致」的取舍（留给下一轮判断）

任务要求"上下两个按钮具有完全一致的圆形样式和尺寸"。当前实现里，
**可用态下两者确实是同一段代码画出的同一个圆**；但"已在本周"时上方会压平变灰。
这是**状态**而非样式差异，落实的是上一轮"已在本周应隐藏或禁用"的要求。

若要更严格（已在本周直接**隐藏**），改 `styleTodayButton()` 一处即可，
且因为容器底部对齐，**隐藏上方按钮不会让下方 FAB 位移**。
另一种参考：`MainActivity.syncReturnControls()` 的既有规则就是"已经在本周就隐藏"。

---

## W. 追加记录 — 2026-10-16：首页「回到今天」改为圆形 FAB

### W-1 需求

首页课程列表上方的「回到今天」改成与单周课表同款的**纯图标圆形 FAB**，
放在右下角「课表总览」（四宫格）FAB 的正上方，无文字。

### W-2 定位：改哪一个控件

首页上其实有**两个**"返回"控件：

| 控件 | 位置 | 行为 |
|---|---|---|
| `todayChip`「回到今天」 | **日头**（课程列表正上方那行） | `goToToday()` —— 回到本周 + 今天这个星期几 |
| `backToThisWeek`「回到本周」 | 顶部的周次卡片里 | 只回到本周，**星期几刻意不动** |

用户说的是"课程列表上方"的「回到今天」，且首选名就是"回到今天" → 改 `todayChip`。
`backToThisWeek` **未动**（它是另一个语义：保留当前星期几）。

### W-3 改法（只动 `MainActivity.java`）

1. **字段**：`View todayChip` → `android.widget.ImageButton todayFab`
2. **`createDayHeader()`**：删掉那段 `navChip("回到今天")`（含 `todayParams`）
3. **`createFloatingActions()`**：改成 `LinearLayout`(vertical, `Gravity.END`) 装两个
   `UiKit.fab`，间距 `FAB_GAP_DP = 10`，整体 `END | BOTTOM` + `SPACE_L` 边距
4. **`syncReturnControls()`**：`todayChip` → `todayFab`（隐藏规则不变：已显示今天就隐藏）
5. **列表底部留白**：`FAB_SIZE + SPACE_XXL`(88dp) → **`FAB_CLEARANCE_DP = 150dp`**
   = `16 + 56 + 10 + 56 + 12`
6. 新增常量 `FAB_GAP_DP`、`FAB_CLEARANCE_DP`（带推导注释）

`goToToday()`、`isShowingToday()`、`TodayTarget` 全部复用，**没有新写周次/日期逻辑**。

### W-4 顺手修正了过期的注释

`createFloatingActions()` 原来的 javadoc 花了一整段解释"为什么这个日历 FAB 被删掉了"
（字形与品牌标记重复、压住列表、与日头重复）。现在它回来了，注释已改写成
"为什么回来 + 当初三个理由各自怎么被解决"（成对出现 / 列表留出空间 / 日头不再有副本）。

### W-5 一个诚实的遗留

**品牌标记（`createBrandMark()`）用的也是 `ic_today`**，所以"一个画面两处同图"
这个当初的顾虑又回来了。用户本轮明确要求"沿用项目现有的图标资源、用日历或今日图标"，
所以仍用 `ic_today`；要区分只需新增一个 drawable 改一处引用。

### W-6 门禁与设备实测（1080×2424 @420）

- **1065 项 / 0 失败 / 0 错误 / 0 跳过**；`app-debug.apk` **1,171,136 字节**；
  lint **0 errors, 2 warnings**

| 元素 | 位置 | 尺寸 |
|---|---|---|
| 回到今天 FAB | x=891..1038, y=1999..2146 | 147×147px = **56×56dp** |
| 课表总览 FAB | x=891..1038, y=2172..2319 | 147×147px = **56×56dp** |

- 右边缘都在 1038（右边距 16dp）；垂直间距 26px = 10dp；两者同色 `#11584E`
- 全页文本节点 0 处「回到今天」（只有 contentDescription）
- 在「今天」时上方 FAB 隐藏、只剩课表总览；切到周一后它出现
- 点它：周一(10/12) → 「今天 · 10/16 周五」，**课程列表同步换成周五 3 门课**
- 滚到底：最后一张卡片止于 y=1761，FAB 顶 y=1999，空 238px
- 课表总览 FAB 功能未变；返回后两个按钮都在；23 门课程完好

---

## X. 追加记录 — 2026-10-16：首页移除周次卡片的「回到本周」

### X-1 需求

删掉首页**周次切换卡片**里的「回到本周」文字按钮（在第N周与 `<` `>` 之间），
**保留**右下角日历图标 FAB（回到今天）。用户特别强调"不要再次删错"。

### X-2 先分清两个控件（这是本轮最容易出错的地方）

| 控件 | 位置 | 行为 | 处置 |
|---|---|---|---|
| `backToThisWeek`「回到本周」chip | 周次卡片内 | 回本周，**保留当前星期几** | **删** |
| `todayFab` 日历 FAB | 右下角 | 回本周 **+** 回今天这个星期几 | **留** |

两者都在"首页"、都叫"回到本周/今天"，只有位置和行为不同。动手前先按
`grep -n "backToThisWeek\|todayFab\|navChip"` 把边界划清楚。

### X-3 改动（只动 `MainActivity.java`）

1. `createWeekCard()`：删掉 chip 的创建 + `card.addView(backToThisWeek, backParams)`
2. 字段 `backToThisWeek` 删除
3. `syncReturnControls()`：删掉它的可见性分支；javadoc "two controls" → 单数
4. **`navChip()` 删除** —— 全项目只有这一个调用点，删完就是死代码。
   删前用 `grep -rn "navChip" app/src/main/java/` 确认过。
5. 保留：`goToToday()`、`isShowingToday()`、`TodayTarget`、`stepWeek()`、
   `parityPill`（那个「本周」状态 pill 是另一个东西，不是按钮）

**没留空占位**：chip 本来就是 `WRAP_CONTENT` + `leftMargin` 加进横向 LinearLayout 的，
删掉即自然收紧。实测 `<` 仍在 x=723..849、`>` 在 x=870..996，与删前一致。

### X-4 能力损失（已告知用户）

chip 的独特价值是"回本周但**保留星期几**"（第12周周三 → 第6周周三）。
日历 FAB 是 `goToToday()`，会连星期几一起回到今天。
删掉 chip 后这个动作没有入口了，只能靠 `上一周`/`下一周` 逐周走。
已在 `createWeekCard()` 里留注释说明这个取舍。

### X-5 门禁与设备实测（1080×2424 @420）

- **1065 项 / 0 失败 / 0 错误 / 0 跳过**；`app-debug.apk` **1,170,756 字节**；
  lint **0 errors, 2 warnings**

| 验收项 | 结果 |
|---|---|
| 卡片无「回到本周」 | ✅ 切到第7周（过去会显示）后全页文本节点 0 处 |
| 布局不留空白 | ✅ `<` `>` 位置与删前一致 |
| 上一周/下一周 | ✅ 第7周 → 第6周 → 第7周 |
| 日历 FAB 保留 | ✅ x=891..1038、y=1999..2146（56dp 圆），在「课表总览」上方 |
| 日历 FAB 功能 | ✅ 第7周 → 第5周·本周，列表同步为今天 3 门课 |
| 已在本周时隐藏 | ✅ 只剩「课表总览」 |
| 不遮挡卡片 | ✅ 滚到底最后卡片内容止于 y=1903，FAB 顶 y=1999 |
| 其它页面 | ✅ 总课表/单周课表未动；23 门课程完好 |

---

## Y. 追加记录 — 2026-10-17：课程共享备注

### Y-1 需求要点

一门课因为时间/周次/教室/教师不同被拆成多条记录，这些记录要共享**同一份**备注；
备注要真持久化、要进备份、要兼容旧数据、不能出现在课表卡片上。

### Y-2 先查清楚的三件事

1. **课程身份**：`CourseColorSync.sameCourseAs` 已经定义好了 —— 同一学期内
   `normalize(name)`（trim 后完全相同）即同一门课，id 只用来排除自己。
   星期/节次/周次/教室/教师刻意不参与，因为它们正是拆记录的原因。**直接复用，不另造。**
2. **存储**：每学期一个 key `courses:<semesterId>`，值是课程 JSON 数组；
   `saveCourses` 整表写。备份与存储**各有一套课程映射**
   （`CourseRepository.toJson` 与 `BackupCodec.courseToJson`），注释里明写"必须逐字段一致"
   —— 加字段**两处都要改**，这是本轮最大的坑。
3. **表单**：`EditCourseActivity` 把各分区 add 到 body，字段在 `applyForm` 里汇总，
   保存走 `commitSave`；草稿 `CourseDraft` 承载旋转时的表单内容。

### Y-3 方案选择：为什么不做"按名字的备注表"

任务倾向"课程组级数据、单一权威来源"，我选了**记录上存 + 仓库同步**，理由是改名：

- 按名字建表：把「高数」改名成「高等数学」后，那条记录指向的新键不存在 → **备注当场消失**。
  而它原来和另外两条共享同一份，没法判断该不该"搬走"。
- 存在记录上：改名时备注跟着记录走，留下的安排保留自己那份。**谁都不会静默丢东西。**

这和 `color` 的处理**完全一致**（颜色也是每记录一份、由 `recolorCourses` 批量同步），
符合 AGENTS.md「业务规则不得有第二份实现」。代价是磁盘上多存几份（每门课通常 1–3 条）。

### Y-4 实现

- `Course.note`（`copy()` 带上）
- `rule/CourseNotes`：`isBlank` / `groupNote`（自己的优先，否则取兄弟的第一个非空）/
  `noteToStore`（有内容就存；空 + 编辑 = 清空；空 + 新建 = 沿用，防误清空）
- `CourseRepository.noteOf(course)`、`saveCourse(course, note)`（**一次 `saveCourses`**
  同时落记录和全组备注，避免两次提交之间进程死亡导致半同步）
- `toJson`/`fromJson` 与 `BackupCodec` 两处都加 `note`，**仅非空时写**（旧 JSON 逐字节不变）
- `EditCourseActivity`：`createNoteSection()`（多行、minLines 3 / maxLines 8、
  `IME_FLAG_NO_ENTER_ACTION` 让回车插入换行）、预填 `noteOf`、草稿存取、保存改走 `saveCourse`
- `CourseDetailActivity`：`noteCard(note)`，有备注才加，放在「地点与教师」之后
- `CourseDraft` 加 `note`（表单内容的一部分；9 处测试构造补 `null`）

### Y-5 踩到的坑

1. **两套课程 JSON 映射**。只改仓库那套的话，备份里不会有备注；
   只改备份那套的话，存储里不会有。两处都改了，并且都"仅非空时写"。
2. **`addSemester` 不会切换当前学期**。测试里我以为它会，结果第二条课程写进了第一个学期，
   备注按同名同步把第一条也改了 —— 测试失败暴露了这点。加 `setActiveSemester` 后正确。
3. **`data` 包原本不依赖 `rule`**。这次让 `CourseRepository` 用 `CourseColorSync`/`CourseNotes`，
   方向是对的（rule 保持无 Android、无 data 依赖），但确实是新的一条边。
4. 详情页多了备注卡片后，**「编辑课程」按钮下移约 240px**，我按旧坐标点空了一次 ——
   设备验证时坐标要重新 dump，不要沿用上一轮的。

### Y-6 门禁与设备验证

- **1091 项 / 0 失败 / 0 错误 / 0 跳过**（+26 = CourseNotesTest 11 + CourseNoteStorageTest 15）
- `app-debug.apk` **1,171,940 字节**；lint **0 errors, 2 warnings**

模拟器实测（**只用 `install -r`**）：

| 项 | 结果 |
|---|---|
| 编辑页备注区 | ✅ 标题 + 提示 + 3 行高 |
| 键盘弹出时保存可达 | ✅ 「保存课程」在键盘上方 |
| 换行 | ✅ dump 里 `&#10;` |
| 详情页展示 | ✅ 两行原文 |
| **跨安排共享** | ✅ 周四 1-2 写入 → 周一 2-3（不同星期/节次/教室）读到同样内容 |
| 重启后仍在 | ✅ force-stop 后重进仍在 |
| 编辑页预填 | ✅ |
| 课表卡片不含备注 | ✅ 总课表/单周课表 0 处 |
| 清空 | ✅ 两条安排同时清空 |
| 其他功能 | ✅ 23 门课程/周次/颜色/叠放正常 |

设备上验的是 ASCII + 换行；**中文/标点/多行的存取由单元测试覆盖**（adb 无法输入非 ASCII）。

### Y-7 未验证

极小屏、横屏、平板；「复制课程」是否应复制备注（当前会复制，因为它是记录字段）。

---

## Z. 追加记录 — 2026-10-18：详情页备注区常显 + 一键定位备注框

### Z-1 需求

详情页原来"备注非空才显示备注卡片"，导致**从没用过备注的用户根本不知道有这个功能**。
要求：始终显示备注区；空/有内容两种形态；「添加备注」能直接进到备注输入框；
共享与持久化逻辑一行不许改。

### Z-2 改动（只碰两个文件）

1. **`CourseDetailActivity`**
   - `render()` 里去掉 `if (!note.isEmpty())`，无条件 `addView(noteCard(note))`
   - `noteCard(note)` 分两态，共用一张卡片：
     - 头部行：`section("课程备注")` 权重 1 + `UiKit.textButton(…)`（空→「添加备注」，有→「编辑」）
     - 空：`暂无备注`（`ON_SURFACE_VARIANT`）+ 提示（`LABEL_MEDIUM` / `OUTLINE`）
     - 有：正文（`BODY_MEDIUM`，`setLineSpacing(dp(3),1f)`，换行原样保留）
   - `openEditor()` → `openEditor(false)`；新增 `openEditor(boolean focusNote)`
2. **`EditCourseActivity`**
   - 新增 `EXTRA_FOCUS_NOTE`
   - 新增 `formScroll` 字段（在 `buildRoot` 里赋值）
   - `onCreate` 末尾：带该 extra 就 `focusNote()`
   - `focusNote()`：`formScroll.post` → `requestFocus` → 算偏移 → `smoothScrollTo` → `showSoftInput`

**没动**：`Course.note` / `CourseNotes` / `noteOf` / `saveCourse` / `BackupCodec` / `CourseDraft`，
以及课表、缩放、叠放、颜色、周次、提醒、导入、备份。

### Z-3 几个实现细节值得记住

1. **滚动偏移不能用 `getTop()`**。备注框的 `getTop()` 是相对它所在分区容器的，
   不是相对 ScrollView 内容 —— 会少算上方所有分区的高度。用
   `getLocationInWindow` 同时取备注框和 ScrollView 的位置再相减，才是对的。
2. **必须 `post`**。`onCreate` 里刚 `setContentView`，什么都没测量，滚到"还没有位置"的
   View 等于没滚。
3. **`textButton` 自带 48dp 最小高度**（`TOUCH_TARGET`），所以头部行会有 48dp，
   空状态整卡约 130dp —— 这是"紧凑但不拥挤"的合理值，不要再压。
4. **不要用实心按钮做「添加备注」**：这一屏的主操作是「编辑课程」，两个实心按钮会打架。

### Z-4 门禁与设备验证

- **1091 项 / 0 失败**（纯 UI 改动，测试数不变 —— 备注语义的 26 项在 T 节那轮已覆盖）
- `app-debug.apk` **1,172,804 字节**；lint **0 errors, 2 warnings**

模拟器实测（**只用 `install -r`**）：

| 项 | 结果 |
|---|---|
| 空备注仍显示备注区 | ✅ 标题/添加备注/暂无备注/提示 四项齐全 |
| 「添加备注」直达备注框 | ✅ 编辑页打开即滚到备注框、`focused=true`、键盘已弹出 |
| 保存后立即刷新 | ✅ 两行备注出现，「暂无备注」消失，「编辑」出现 |
| 有备注态 | ✅ 内容 + 换行（`&#10;`）+ 编辑入口 |
| 取消编辑 | ✅ 备注原样（注意：键盘弹着时第一次 BACK 只收键盘，要按两次） |
| 清空 | ✅ 回到空状态 |
| 跨安排共享 | ✅ 周一 2-3 → 周四 1-2 同样内容 |
| 重启 | ✅ 仍在 |

### Z-5 踩的坑（验证脚本层面）

1. **清空备注时 `KEYCODE_MOVE_END` 在多行 EditText 里只到"当前行末"**，
   30 次 DEL 只删掉了第一行，第二行还在。我第一遍的断言写的是
   `not any('readch5' in x)` —— 第一行删掉后这个条件就成立了，**断言假阳性**，
   实际没清空。改成直接 dump EditText 的 `text` 属性才看出来。
2. 键盘弹着时 **第一次 BACK 只收键盘**，不是退出页面。验"取消"要按两次。
3. 详情页加了备注卡片后「编辑课程」下移，坐标要重新 dump（上一轮已踩过一次）。

### Z-6 未验证

- 本轮没重新验「不同课程互不覆盖」和「十几行长备注」；前者有单元测试 + 上一轮设备验证，
  后者只验了两行。
- 极小屏/横屏/平板未验。
- adb 输不了中文，设备上验的是 ASCII + 换行。

---

## AA. 追加记录 — 2026-10-18：上课提醒可靠性修复

### AA-1 触发起点
用户报告：真机安装后「上课提醒」没有正常实现。要求从课程数据 → 计划 → 系统闹钟 → 通知显示整链排查，且必须区分
「单元测试通过 / 闹钟已安排 / 通知已发布 / 真机端到端通过」。

### AA-2 审计发现（修复前代码）
- `ReminderReceiver.announce()`：`if (!LocalDateTime.now().isBefore(startAt)) return;` —— **迟到即丢弃**。
  闹钟只要晚到一瞬，通知就永远不发。这是「完全收不到」的最直接原因。
- `ReminderScheduler.register()` 只用 `setAndAllowWhileIdle`；`AndroidManifest.xml` 里连
  `SCHEDULE_EXACT_ALARM` 都没有，注释写着「deliberately absent」。Doze / 厂商省电下必然晚到，
  再被上一条丢弃 → 静默无提醒。
- 无 `canScheduleExactAlarms()`、无授权入口、无权限变化后的重排。
- `ReminderScheduler.sync()` 的调用点只有：`MainActivity.onResume`、设置页的开关/提前量、两个接收器。
  **编辑课程、删除课程、导入、备份恢复、学期切换/新建/删除、学期起始日期改动都不重排。**
- `SettingsActivity.onResume` 只 `refresh()` 不 `sync()`：在系统设置里授予通知权限后回到应用，
  界面显示「已开启」，但 `scheduled` 为空。
- 设置页没有通知权限/渠道/精确闹钟/下一条/最近一次失败的任何显示。

### AA-3 修复
1. **精确优先**：`setExactAndAllowWhileIdle`；每次注册前 `canScheduleExactAlarms()`；
   未授权 → `setAndAllowWhileIdle`；调用时 `SecurityException` → 就地降级并记日志。
   清单只加 `SCHEDULE_EXACT_ALARM`（明确不用 `USE_EXACT_ALARM`）。
2. **权限生命周期**：设置页「允许精确闹钟」→ 系统页；`ReminderRestoreReceiver` 监听
   `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，授权/撤销后后台自动重排。
3. **迟到宽限**：`rule/Reminder` 新增 `MAX_LATE_MINUTES=20`、`isLate`、`isStillUsefulAt`、`lateMinutes`；
   接收器用宽限窗口替代「到点即丢」；文案改为「已到上课时间（提醒延迟 N 分钟）」，不再复述已过期的倒计时。
4. **生命周期补齐**：编辑页保存/删除、详情页删除、备份恢复成功、两处导入成功、学期切换/新建/删除、
   学期起始日期、设置页 `onResume`、权限变化、开机/改时间/改时区/应用更新，全部 `sync()`。
5. **诊断与自检**：设置页三行状态（通知权限 / 通知渠道 / 精确闹钟）+ 下一条提醒 + 最近一次重排失败原因；
   「发送测试通知」验证权限与渠道；「测试提醒（1 分钟后）」用与真实提醒相同的 `AlarmManager` 调用，
   槽位 999（真实槽位 1000+ 之外）、动作 `TEST_REMIND`、通知 id `0x7E57`，一次性，触发后自动撤销。
6. **日志**：4 个 tag，只记 courseId/slot/时刻/结果，不记课名教室教师。

### AA-4 测试
- 新增 `rule/ReminderTimingTest` 6 项：未迟到、恰好在开始时算迟到、窗口内仍可用、窗口外丢弃、
  `now=null` 不当成准时、窗口必须短于一节课。
- `ReminderScheduleTest` +2：准时文案保留倒计时；迟到文案写明延迟且不再出现「分钟后开始」。
- `ReminderRestoreTest`：事件清单加 `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，
  忽略清单加 `TEST_REMIND`。
- 门禁：`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL；
  **1099 项 / 0 失败 / 0 错误**（基线 1091，+8）；APK 1,179,188 B；lint **0 errors, 2 warnings**（既有 IconXmlAndPng）。

### AA-5 设备实测（模拟器 API 37，1080×2424 @420）
| 场景 | 实测 |
|---|---|
| 测试提醒 | 计划 07:34:06.067 → 触发 07:34:06.076（**+9 ms**），应用后台 + 屏幕关闭，通知入栏，`test_at` 归零 |
| 真实课程提醒 | 周日第 1 节探针课（08:15-08:55，提前 34 分钟）：计划 07:41:00.000 → 触发 **07:41:00.039**（+39 ms）；通知「上课提醒 · ReminderProbe / 10月18日 周日 08:15 · 第 1 节 · 教学楼F楼F101多 · Probe Teacher / 34 分钟后开始」 |
| 精确/非精确 | 未授权时日志 `inexact`，`dumpsys alarm` 无 `exactAllowReason`；授权后 `exact=true`，`window=0 exactAllowReason=permission` |
| 权限变化重排 | `adb shell cmd appops set ... allow` → 系统广播 → `已重建 10 条提醒` |
| 改课 | 周次 1-18 → 1-4（今天第 5 周）：保存即重排，探针从 `scheduled` 消失，旧触发时刻在系统闹钟里消失 |
| 重启 | `BOOT_COMPLETED: 已重建 10 条提醒`，10 条闹钟时刻与重启前一致 |
| 撤销通知权限 | 全部闹钟取消，`last_sync_error="notifications are blocked"`，界面红字 + 「去开启通知权限」 |
| 恢复 | 数据逐字节还原（MD5 `5812d6eb…`），通知权限回拒绝、精确闹钟 appop 回 default |

### AA-6 未验证 / 限制
- **只有模拟器，没有真机**；厂商 ROM 的省电策略无法用代码绕过。
- 强行停止后系统会取消闹钟与广播，直到用户再次打开应用——系统行为。
- 锁屏形态、非精确路径的实际延迟、`SecurityException` 降级分支均未实测。
- 新设置卡片的大字号/极小屏排版未验证。

### AA-7 踩坑
1. **清单注释里不能出现 `--`**：在 `AndroidManifest.xml` 的注释里写了 `route -- this is…`，
   `processDebugMainManifest` 直接报 `MergeFailureException: Error parsing AndroidManifest.xml`，
   错误信息不指向具体行，容易误判成编码问题。
2. **清单里新加的权限是升级安装**：`install -r` 后 appop 仍是 `default`，而 targetSdk 34 在 API 37 上
   实际按「未授权」处理（`canScheduleExactAlarms()=false`）——这正是需要降级路径的原因。
3. **`cmd appops set ... allow` 会触发系统广播**，可以用来验证「授权后自动重排」，比点系统设置页快得多。
4. **A/F 楼作息第 1 节是 08:15**（不是 08:00/08:55）：构造探针课程时若按猜的时间算提前量，
   提醒会因为「已经过去」被正确跳过，看起来像 bug。时间一律从详情页读，不要猜。
5. **往 `courses:<学期id>` 里插探针课程**时，插入点必须放在现有数组的 `[` 之后并保留逗号；
   写成 `[{…` 会产生 `[[{…` 的嵌套数组，解析失败 → 整学期课程都读不出来（界面显示 0 条安排）。
### AA-8 后续（同日）：诊断界面按上线要求移除

- 排查期曾在设置页加了三行诊断（通知权限/渠道/精确闹钟/下一条/上次失败）与两个自检按钮
  （发送测试通知、测试提醒 1 分钟），并实现了 `TEST_SLOT=999` 的测试闹钟链路。
- 用户看到真机截图后要求「把界面恢复到可上线状态」，因此：诊断行、两个按钮、
  `scheduleTest/cancelTest/testScheduledAt`、`ACTION_TEST_REMIND`、`postTest()`、
  `ReminderSettings.last_sync_*`/`test_at` **全部删除**（无调用方即不留）。
- **保留**精确闹钟入口：未授权时那颗按钮变成「允许精确闹钟」，授权后整行隐藏——这是修复路径，不是诊断信息。
- 排查改为日志（4 个 tag）；门禁重跑：**1099 项 / 0 失败**，APK **1,175,784 B**，lint **0 errors, 2 warnings**。
- 设备复查：卡片回到「勾选 + 一行状态 + 橙色度色块 + 一句说明 + 单个按钮」；`exact` 未授权 →「允许精确闹钟」在，
  授权后消失；提醒仍为 10 条。测完把设备恢复到提醒关闭、通知拒绝、精确闹钟 appop default。

---

## AB. 2026-10-18 桌面小组件（今日课程）

**任务**：给拾课加一个桌面小组件——紧凑时显示下一节课/正在上的课 + 时间 + 地点 + 倒计时，
大尺寸再多显示今天接下来的 2–3 门课；覆盖上课中 / 即将开始 / 今天没课 / 没有课表 / 学期外
五种状态；点开进应用；保存、删除、导入、恢复、切学期、改作息表、跨零点、重启、改时间时都要刷新。

**约束**：`AppWidgetProvider` + `RemoteViews`，纯 Java，不引入 Kotlin/Compose/Glance，
不加依赖，不复制周次/单双周/作息表逻辑；倒计时用 `Chronometer`；`updatePeriodMillis` 不得低于 30 分钟；
不得把提醒账本（上限 10 条）当作课表数据。

### AB-1. 结构

- `rule/TodayPlan`（纯 JVM）：吃 `List<Course>` + `Semester` + `List<BellSchedule>` + 默认作息表 id +
  当前时刻，产出 `state` / `current` / `next` / `following` / `laterToday` / `headline()` /
  `countdownAt()` / `nextRefreshAt()`。周次与单双周走 `ScheduleUtils.occursInWeek`，
  时间走 `BellSchedules.resolve` + `BellSchedule.startMinute/endMinute`，筛选走 `CourseQuery`。
- `widget/` 五个类：Provider（生命周期 + 自定义 `WIDGET_TICK`）、DataSource（只读一次仓库）、
  Renderer（两个布局 + 文案 + 点击 + 尺寸判断）、Updater（`refreshAll`）、RefreshScheduler（一个重画闹钟）。
- 资源：表头单独一个 layout 由两个尺寸 `<include>`，行是嵌套 RemoteViews 用 `addView` 加进去，
  所以"今天有几门课"决定行数，而不是在 XML 里预放三行再逐个隐藏。

### AB-2. 根因级问题（真机才暴露）

1. **倒计时显示 497864:44:48。** `RemoteViews.setChronometer(id, base, …)` 的 base 是
   `SystemClock.elapsedRealtime()` 基准；第一版传了 `LocalDateTime` 的 epoch 毫秒，
   `Chronometer` 用"开机毫秒"去减，差值自然是 57 年。
   修法：`elapsedRealtime() + (目标epoch - 当前epoch)`，并在 `bindCountdown` 的 javadoc 里写明原因。
2. **选择器预览里出现 `00:00`。** 静态预览不会执行渲染代码，`Chronometer` 就露出默认值。
   修法：XML 里 `visibility="gone"`，由渲染器在有目标时打开。
3. **`res/values/widget.xml` 与 manifest 的注释里出现 `--`** → AAPT 直接报
   `注释中不允许出现字符串 "--"`。这个坑本项目上次踩在 manifest 上，这次一次踩在两个文件里。
4. **lint 多了 5 条警告**：`previewLayout`/`targetCell*` 是 API 31 属性（`UnusedAttribute`），
   `\uXXXX` 转义的中文字符串被扫成拼写错误（`Typos`）。修法：`tools:targetApi="s"`；
   资源里的中文直接写（和 `strings.xml` 的 `app_name` 一致），Java 源码里仍用转义。

### AB-3. 顺带修掉的既有缺口

作息表的四个写入点（`EditScheduleActivity` 保存/删除、`ScheduleListActivity` 设为默认/删除）
此前**只写数据不重排提醒**：改了下课时间，当天的提醒还按旧时刻发。本轮一并补了
`ReminderScheduler.sync(...)`，同时给这 4 处和另外 6 处数据写入补了 `CourseWidgetUpdater.refreshAll(...)`。

### AB-4. 当轮结果

- 门禁：`--rerun-tasks testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL；
  1135 项 / 0 失败 / 0 错误，61 个测试类；lint 0 errors, 2 warnings（既有）；APK 1,195,415 B。
- 设备（API 37 模拟器）：选择器可发现、可添加、可移除；今天没课 / 正在上课 / 即将开始三种状态截图；
  倒计时自行走动；边界闹钟精确对准下课、下一节开始、次日 00:05；编辑课程后立即刷新；
  重启后重画并重挂闹钟。
- 未验证：宽版多行在发布口径下的裁切（Launcher 手势无法用 adb 复现，用临时放宽预算的构建拍到一行后回退）、
  点击跳转、`TIME_SET`/`TIMEZONE_CHANGED`、深色模式。

### AB-5. 给下一轮的提醒

- 想验证宽版：手动长按小组件 → 拖右下角把手拉高到 3 行，看第三行是否被裁。
- 判断"小组件是否在更新"，看日志就够：`adb logcat -s CourseWidget`，
  每次刷新一行 `refreshed=<成功数>/<总数> state=… following=…`，闹钟一行 `refresh alarm at=… exact|inexact`。

---

## AC. 2026-10-18 小组件第二轮：倒计时靠右、上下翻页按钮

**背景**：第一版小组件（见 AB）交付后，用户要求：把「距上课/距下课」放到右侧；当天有多节课时能翻动查看后续课程；
若翻动不好做就改成醒目的上下按钮。

### AC-1. 走过的弯路（记录以免下一轮重犯）

1. **`AdapterViewFlipper` + `RemoteViewsService` 方案**：页面能绑定并渲染（说明服务与工厂都通了），
   但在 Pixel Launcher（API 37）上左右滑动不翻页，卡片点击也不生效——
   点击启动的是 `act=MAIN cat=LAUNCHER`，即桌面自己的「点小组件打开应用」兜底。
   说明触摸没有进入集合项；`setOnClickFillInIntent` + `setPendingIntentTemplate` 与直连 PendingIntent 都一样。
   → **结论：这个桌面不给小组件集合视图派发手势。**
2. 其间还试过：`input swipe` 三种速度、`input motionevent` 分段拖拽、横屏/放大逻辑屏幕（`wm size`）找 Add 按钮。
   横屏与放大屏幕都不改变结论，只用来把「添加小组件」的 Add 按钮从折叠区里弄出来。

### AC-2. 最终实现

- **页码状态放在应用侧**：`widget/CourseWidgetPageStore.java`，独立偏好文件 `weihe_schedule_widget`，
  键 `page:<widgetId>` 与 `day:<id>`（跨天自动归零）。不碰 `weihe_schedule` 的存储契约。
- **上下按钮**：`res/drawable/widget_nav_button.xml`（`Palette.PRIMARY` 实心圆）+ `widget_ic_up/down.xml`
  （Material chevron 矢量，白色），32dp，右侧竖排，中间夹页码 `1 / 2`，`AdapterViewFlipper` 那套全部删除。
- **按钮点击**：`PendingIntent.getBroadcast`（request code = `widgetId * 2 + 方向`）→ `CourseWidgetProvider`
  收到 `WIDGET_PAGE_PREV/NEXT` → 改页码 → `CourseWidgetUpdater.refreshAll`。循环翻页。
- **倒计时靠右**：从「右侧一列」改为**标题行右侧**。右侧一列会压缩时间/地点两行，实测把
  `第 4-6 节` 与 `教学楼F楼F102多 · 凌铭` 都截断了；放到标题行只压缩课名。
- **紧凑布局**：日期 + 卡片 +（右侧）上下按钮列；正文垂直居中；`minResize` 提到 180×130dp。

### AC-3. 当轮验证

- 门禁：1140 项 / 0 失败 / 0 错误；lint 0 errors, 2 warnings（既有）；APK 1,200,804 B。
- 设备：第 1 页 `dd`（`距下课 19:20`，`1 / 2`）→ 点 ▼ → `Aa`（`距上课 1:27:16`，`2 / 2`）→ 点 ▲ 回到第 1 页；
  点卡片打开 `CourseDetailActivity`；重装后页码保留；日志有 `page turn widget=7 pages=2 intent=…`。
- 顺带修掉自己引入的 3 条 lint 警告（`DisableBaselineAlignment`、`NestedWeights`、未使用的 `widget_text_pager`），
  以及两处 XML 注释里的 `--`（AAPT 直接报错，本项目已踩过三次）。

### AC-4. 留给下一轮

- 若以后换到支持集合手势的桌面，可评估把滑动加回来；当前按钮方案在所有桌面都能用。
- 深色模式仍未做。

---

## AD. 2026-10-18 小组件第三轮：两节课卡 + 今日课表入口

**任务（用户 7 条）**：2×3 组件最多两张课卡；按当前时间动态选（上课中=当前+下一节；没在课上=接下来最早两节）；
不足两节不占位；底部始终有「查看今日全部 N 节课程」入口，N 按当天实际课程算；点入口进今日课表；
开始/结束/跨日/改数据后重算；无论尺寸、字体、课名多长都不得出现第三张卡。

**做法**

- `CourseWidgetRenderer.MAX_CARDS = 2`；卡片用 `addView` 加进容器，**加几张由代码决定**，布局里没有第三张卡的位置，
  所以「不得出现第三节」不依赖任何尺寸判断。
- 选卡规则只有一条：`headline + following 的第一个`。headline 在「正在上课」时就是当前课，否则是下一节，
  于是「当前+下一节」和「接下来最早两节」用同一条规则同时成立。
- 新增 `TodayPlan.todayCount()`（当天全部课程数，含已结束），入口文案只用它，不用卡片数量。
- 入口整行是 `PendingIntent` → `MainActivity`；单张卡片是 `PendingIntent` → `CourseDetailActivity`。

**删掉的**：翻页（`CourseWidgetPageStore`、上下按钮、翻页广播）、宽版布局与行（`widget_course_wide/row`）、
`isWide`/`rowBudget`/`MAX_FOLLOWING`、紧凑/日期两个布局，合并为单一 `widget_course.xml`。

**真机坑（值得记）**：布局里用 `<View layout_height="0dp" layout_weight="1"/>` 当弹簧，桌面直接
`Can't load widget`，异常是 `Class not allowed to be inflated android.view.View` ——
`RemoteViews` 只允许白名单视图类。改成把上半部分包进 weight=1 的 `LinearLayout`，入口自然落底。

**验证**

- 门禁：1144 项 / 0 失败 / 0 错误；lint 0 errors, 2 warnings（既有）；APK 1,193,756 B。
- 设备（09:25，`dd` 正在上）：两张卡（`dd` 距下课 09:12 / `Aa` 距上课 1:14:12）+ 底部「查看今日全部 2 节课程」；
  点入口打开 `MainActivity`；点第二张卡打开 `CourseDetailActivity`；字体 1.3 倍仍是两张卡。
- 未验证：字体 ≥1.3 时第二张卡的第二行会被裁；当天只有 2 节课，「没在上课时显示最早两节」只有单测覆盖。

**下一轮可做**：按 `fontScale` 动态收缩第二张卡内容，避免大字号下裁行；深色模式。

---

## AE. 2026-10-18 新增 1×2 小组件（极简下一节课提醒卡）

**任务**：在已有 3×2 组件的基础上新增 1×2 尺寸，视觉一致但布局独立；信息优先级
课名 > 开始时间 > 教室 > 倒计时 > 状态；状态区分下一节/上课中/今日结束/今日无课/空课表/学期外；
复用现有小组件技术栈与课程数据，不复制单双周、作息表、时间计算逻辑；不影响现有尺寸；跑测试+构建+Lint。

**做法**

- 新增第二个 Provider `NextClassWidgetProvider` + 独立布局 `widget_next_class.xml` + 元数据
  `next_class_widget_info.xml`。一个 Provider 的尺寸范围是整体声明的，塞不下两种布局。
- 数据与刷新共用：一次 `CourseWidgetDataSource.read`、一个边界闹钟、同一批数据写入点；
  `CourseWidgetUpdater.refreshAll` 同时遍历两个 Provider 的 id。
- 顺手修掉共存缺陷：3×2 的 `onDisabled` 原来无条件取消闹钟，现在走 `refreshAll`，
  任一尺寸还在桌面就保留闹钟。

**真机坑（两条，都值得记）**

1. **名义 1×2 被放成 2×2**：`minWidth=110dp` 是平台公式 `70n-30` 里的「2 格」值，
   桌面据此按 2 格宽放置，选择器显示 `2 × 2`。改 `minWidth=40dp` 后显示 `1 × 2`。
2. **`getAppWidgetOptions` 是区间不是当前值**：1×2 报 `minH=99dp` / `maxH=203dp`，实际约 152dp；
   取 `min` 会把教室和倒计时全隐藏。改取较大值，并日志输出 `minH/maxH`。

**验证**

- 门禁：1153 项 / 0 失败 / 0 错误（62 类）；lint 0 errors, 2 warnings（既有）；APK 1,198,798 B。
- 设备：选择器出现第二个入口「下一节」，显示 `1 × 2`；预览为窄卡、文字换行正常；
  活动实例渲染出「下一节 / Aa / 10:40 / 教学楼F楼F102多 / 距上课 1:02:15」；点击打开 App；
  移除后实例消失而闹钟保留；两个尺寸同时刷新（`refreshed=2/2 … nextClass=1`）；
  09:35 边界闹钟让两者从「正在上课」切到「下一节」。
- **未验证**：没有拍到「1 格宽」的活动实例——该条目的 `+ Add` 位于选择器底部浮动标签栏之下，
  `adb` 的点击/拖拽够不到（试过 `wm size` 放大、滚动、`draganddrop`、`motionevent`）；
  其余四种状态文案、缩放、横屏均只有单测或未覆盖。**需要人工在桌面添加一次 1×2 组件确认**。

**下一轮可做**：横屏高度处理（取较大值可能让倒计时略微超出）、深色模式；若能自动化放置，
补一张 1 格宽的真机截图。

---

## AF. 2026-10-18 尺寸修正：1×2 改为 2×1（横向）

**任务**：上一轮把「下一节」做成竖向 1×2，实际需求是横向 2×1。只改这个组件，3×2 必须原样保留，不提前做 2×2。

**改动**（4 个文件 + 1 组尺寸常量）

- `res/xml/next_class_widget_info.xml`：`minWidth 110dp` / `minHeight 40dp` / `minResize 110×40` /
  `targetCellWidth 2` / `targetCellHeight 1`（平台公式 `70n−30`：2 格宽 110dp，1 格高 40dp）。
- `res/layout/widget_next_class.xml`：重写为横版三行——① 状态 + 倒计时同行；② 课名（占位，单行省略）+
  开始时间（22sp，右）；③ 教室（整行，可隐藏）。左侧 3dp 课程色条，沿用 3×2 的白底卡片与圆角。
- `NextClassWidgetRenderer`：只保留「教室」的高度阈值（88dp）；**删掉倒计时的高度阈值**——横版里倒计时与状态同行，
  不占额外高度，只有教室需要独立一行。
- `values/widget.xml`：新增 `widget_next_text_name 16sp`。
- `NextClassWidgetRendererTest`：阈值断言改为「只有教室占高度，88dp ≤ 单格实测 99dp」。

**未改**：`CourseWidgetRenderer`、`widget_course.xml`、`widget_course_card.xml`、`course_widget_info.xml`、
`TodayPlan`、刷新与闹钟机制、数据写入点。

**设备验证**

- 选择器显示 `下一节 2 × 1`，预览为横版窄条。
- 放置：选择器里的 `+ Add` 被底部浮动标签栏遮挡，`adb` 点不到；改用 `input draganddrop` 从预览拖到桌面，成功。
- `options widget=11 minH=43 maxH=96` → `height=96dp`；渲染出「下一节 / 距上课 44:01 / Aa / 10:40 / 教学楼F楼F102多」，
  无裁切、无重叠。
- 点击 → `MainActivity`；同屏 3×2 行为不变；日志 `refreshed=2/2 … nextClass=1`。

**未验证**：缩放到更矮时隐藏教室行（仅单测）、其余四种状态文案（仅单测）、横屏、深色模式。

**门禁**：1153 项 / 0 失败 / 0 错误；lint 0 errors, 2 warnings（既有）；APK 1,198,822 B。

**下一阶段**：2×2（本阶段按任务要求未开发）；深色模式；若启动器仍遮挡 `+ Add`，可考虑在 App 内提供
「添加到桌面」入口（`requestPinAppWidget`）。
### AF-1. 风格对齐（同日追加）

用户指出 2×1 与 3×2 风格不一致。差异根源只有一个：2×1 少了 3×2 的浅绿课块，且时间用了 22sp 自成一档。
修法：2×1 在白色卡片内套同一层 `widget_headline_bg` 绿块，色条移进绿块内部，字号改回 3×2 的
11/15/12sp 体系，删掉专为 2×1 加的四条尺寸。3×2 未改动。复核截图 `widget-111`；
最终门禁 1153 项 / 0 失败，lint 0 errors 2 warnings，APK 1,198,638 B。
---

## AG. 2026-10-18 新增 2×2 小组件（接下来的课）

**任务**：在已有 3×2、2×1 之上新增 2×2，方形，展示当前/下一节 + 紧随其后的一节；保持前两种尺寸不变。

**新增**：`SquareWidgetProvider`、`SquareWidgetRenderer`、`res/layout/widget_square.xml`、
`res/xml/square_widget_info.xml`（110×110dp，targetCell 2×2）、`WidgetChronometer`（共享倒计时，
`labelFor` + `bind`，含 `elapsedRealtime` 换算）、`SquareWidgetRendererTest`（10 项）。
**修改**：Manifest 第三个 receiver、`CourseWidgetUpdater`（第三个 id 数组 + `square=` 日志）、
`NextClassWidgetRenderer`（倒计时委托给共享类，行为不变）、`values/widget.xml`（两条字符串）。

**规则复用**：主卡/次卡都来自 `CourseWidgetRenderer.cards(plan)`（headline + 其一后），
不复制单双周/作息表/教室逻辑；空态文案复用 `NextClassWidgetRenderer.emptyTitle/emptyHint`。
刷新复用一次读取 + 一个边界闹钟 + 十处写入点，日志 `refreshed=3/3 … square=1`。

**设备**：选择器「3 widgets」出现第三个入口「接下来的课」，预览为方形；用 `input draganddrop` 放置成功（id=12，
启动器给的尺寸 137×200dp）；渲染 `周日 · 第5周 单周 / 下一节 / 距上课 32:51 / Aa / 10:40 - 14:00 · 教学楼…`；
点击打开 `MainActivity`；三种尺寸同屏同时刷新。

**未验证**：次要课程行（今天只有 2 节课，`cards=1`）、其余四种状态、缩放阈值、横屏、深色模式。

**门禁**：1163 项 / 0 失败 / 0 错误（63 类）；lint 0 errors, 2 warnings（既有）；APK 1,203,000 B。

**下一阶段**：2×2 的次要行需要 3 节课的一天才能真机确认；深色模式；若启动器继续遮挡 `+ Add`，
可考虑在 App 内加「添加到桌面」（`requestPinAppWidget`）。
---

## AH. 2026-10-18 小组件半透明

用户要求减少对壁纸的遮挡。只改 `res/values/widget.xml` 三个共用颜色：
`widget_surface` #FFFFFFFF → #E0FFFFFF（88%）、`widget_headline` #FFCFE5DE → #C7CFE5DE（78%）、
`widget_outline` #FFDFE7E1 → #99DFE7E1（60%）。三种尺寸共用同一组 drawable，改一处全生效；
文字色一律保持不透明，因此对比度不受影响。

设备复核截图 `widget-130`：壁纸明暗从卡面透出，文字/绿块/倒计时清晰。
未验证：极端壁纸、深色模式。门禁 1163 项 / 0 失败，lint 0 errors 2 warnings，APK 1,203,000 B。
---

## AI. 2026-10-18 第二张卡片用「已结束」补位

用户反馈 3×2/2×2 放得下两节课却只显示一节。根因是选择规则：`cards()` 只取未结束的课，
当只剩一节未上时第二张空着，而入口写着「查看今日全部 2 节课程」。

改法：`TodayPlan` 增加 `Session.ended(now)` 与 `previous()`（当天最近结束的一节）；
`cards()` 在仍未满两张时用 `previous()` 补位（未上的课永远优先）；`WidgetChronometer.bind` 对已结束的课
显示「已结束」并隐藏 Chronometer；3×2 的倒计时改走该共享类；2×2 次要行改为 `已结束 09:35 · dd`；2×1 不变。

设备复核（`widget-140`）：3×2 两张卡 `Aa 距上课 22:31` + `dd 已结束`；2×2 主卡 + `已结束 09:35 · dd`；2×1 不变。
测试更新 5 项，共 1164 项全绿；lint 0 errors 2 warnings；APK 1,203,112 B。
---

## AJ. 2026-10-18 三种尺寸视觉重做

用户给出完整设计规范（玻璃背景、4dp 状态条、三级字阶、等宽数字、更大间距、截断规则），要求三个尺寸分别实现。

**做法**：重写 `values/widget.xml` 令牌与 4 个布局、3 个渲染器与共享倒计时助手；业务规则（headline → 后续 → 已结束补位、刷新链路、点击目标）未动。
新增 `widget_pill_bg.xml`、`widget_badge_bg.xml`，删除 `widget_headline_bg.xml`。
颜色只用在 4dp 状态条（主题色=未上/进行中，灰色=已结束）；时间与倒计时加 `fontFeatureSettings="tnum"`。

**与规范的差异**：主题色沿用品牌绿而非蓝；倒计时徽章放在课程名右侧（180dp 放不下状态行 + 徽章，实测截断）；名/时间不截断，只有地点/教师行省略。

**设备验证**：三尺寸渲染、长文本、等宽对齐、点击（课程项→详情、整卡/入口→主页）、
10:40 边界刷新把 UPCOMING 切成 IN_CLASS（倒计时变「距下课」）均真机确认。
修掉三个布局溢出问题（3×2 入口被挤出、2×1 地点被切、徽章挤压状态行）。

**门禁**：1159 项 / 0 失败 / 0 错误；lint 0 errors 2 warnings（既有）；APK 1,217,637 B。
**未验证**：极端壁纸、窄尺寸/横屏、深色模式、第三方 ROM 的 tnum。
---

## AK. 2026-10-18 视觉微调（用户五点反馈）

1. 4dp 色条改回**课程自身颜色**（`session.color()`，与详情页一致）；状态交给「已结束」文字与灰字。
2. 2×2 细线下**固定显示 `plan.previous()`**（最近结束的一节），单行 `dd (09:35结束)`，灰字 + 课程色条；
   `secondaryText` 改名 `finishedText`，去掉「有时显示下一节」的双重语义。
3. 2×1 时间去掉加粗、改次要色。
4. 2×1 / 2×2 不再显示周次与单双周（`statusLine` 只返回状态词），周次胶囊只留 3×2。
5. 3×2 列表结构未动（仍是有未上的课优先，空位才用已结束的补）。

设备复核 `widget-170`：两节课各自颜色（蓝 / 红）、2×2 底部 `dd (09:35结束)`、2×1 灰色不加粗时间。
门禁：1159 项 / 0 失败；lint 0 errors 2 warnings；APK 1,205,130 B。
遗留：3×2 列表与 2×1 的课程名仍会省略（高度预算只有一行）。
---

## AL. 2026-10-18 倒计时改到右上、色条避让状态行、去掉秒

1. 2×1 / 2×2 改为「顶部状态行（状态 + 徽章）→ 课程块」两段式，徽章回到状态行右侧；顺带让课程名拿到整行宽度。
2. 4dp 色条只覆盖课程块，不再贯穿到「正在上课」那一行。
3. 倒计时不再用 `Chronometer`（其格式固定带秒），改为 `WidgetChronometer.remainText` 生成 `3:08` / `12 分`（最后一分钟显示 `1 分`），
   并由 `CourseWidgetRefreshScheduler` 在**有倒计时时**把同一闹钟拉到下一个整分；无倒计时时保持原边界唤醒。
   三个布局的倒计时都改成 `TextView` + `tnum`。

设备复核 `widget-181`：徽章与「正在上课」同行、色条从课程名开始、两分钟内 `3:03 → 3:01`，日志显示闹钟间隔一分钟。
门禁：1166 项 / 0 失败（新增 `WidgetChronometerTest` 7 项）；lint 0 errors 2 warnings；APK 1,206,451 B。
风险：上课期间每分钟一次刷新；跨小时的 `h:mm` 真机显示未验证。
---

## AM. 2026-10-18 去除全部倒计时

用户要求彻底去掉倒计时。删除：三个布局里的倒计时视图与徽章容器、`WidgetChronometer` 整类及其 7 项单测、
`countdownLabelOf`、`widget_badge_bg.xml`、`widget_on_accent`、`widget_text_badge`、以及调度器里的分钟对齐逻辑。
保留状态文字、课程名/时间/地点、已结束行与入口。

连带效果：闹钟回到只在边界唤醒（设备日志下一次 15:00）；3×2 的课程名获得整行宽度。
门禁：1158 项 / 0 失败；lint 0 errors 2 warnings。
---

## AN. 2026-10-18 顶行统一、2×2 补下一节行

1. 2×1 / 2×2 顶行改为与 3×2 一致：日期左、`第5周 (单周)` 胶囊右；`statusLabel/statusLine` 与状态词一并删除，
   顺带修掉「学期外仍显示周次」的边界（`pillText` 现在要求 state != OUTSIDE_TERM）。
2. 2×2 从「主课 + 已结束」扩成「主课 + 下一节行 + 卡底已结束行」：中间行取 `plan.following().get(0)`
   （不用 `cards()`，避免与已结束行重复），新增 `widget_square_spacer` 把已结束行压到卡片底边。
   下部大片空白因此变成「内容 + 页脚」。
3. 新增 `nextText` 与三条用例；`NextClassWidgetRendererTest` 改写为头部/空态/文本行三组。

门禁：1160 项 / 0 失败；lint 0 errors 2 warnings（另修掉 UnusedResources 与 UselessLeaf 两条新警告）；APK 1,203,096 B。
未验证：真 2×2 尺寸（184×184dp）下的紧凑程度，当前实例是 137×200dp。
---

## AO. 2026-10-18 2×2 改为两行课程 + 卡底已结束

用户要的是「像 3×2 那样显示两节课，最后放已上完的」。做法：把 `CourseWidgetRenderer.itemView` 改为包内可见，
2×2 直接复用它把 headline + `following().get(0)` 两行加进 `widget_square_cards`，底部保留已结束页脚。
`items()` 刻意不用 `cards()`（后者会用已结束的课补空位，与页脚重复）。空出位置由 `widget_square_spacer` 吸收，页脚贴卡底。

设备复核 `widget-210`：两行各有课程色条（蓝/红）+ 名称/时间/地点，底部 `dd (09:35结束)`。
门禁：1161 项 / 0 失败；lint 0 errors 2 warnings；APK 1,202,488 B。
---

## AP. 2026-10-18 空态左上对齐、2×1 无色带

1. 3×2 / 2×2 的空态块从「weight=1 + 垂直居中」改回 wrap_content + 顶部对齐，标题与提示紧贴头部下方左侧。
2. 2×1 空态隐藏 `widget_next_bar`（无课却有色带）并隐藏空的时间行，避免标题与提示被拉开一行；正常态改为顶部对齐。
3. 顺带修掉 `NestedWeights`（空态块不再有第二个权重）。

设备复核 `widget-230`：设备当前课表确实没有今天的课，三个组件均为真实空态，文字左上对齐、2×1 无色带。
（过程中为截图曾临时清空数据源，已还原。）
门禁：1161 项 / 0 失败；lint 0 errors 2 warnings；APK 1,202,480 B。
---

## AQ. 2026-10-18 备注卡按钮改到左侧

`CourseDetailActivity.noteCard`：footer 由 `Gravity.END` 改 `Gravity.START`，并把 `textButton` 的标签侧内边距置 0，
让「添加备注/编辑」与上方正文同一左边缘；48dp 触摸高度保留。没有移到标题行——按钮比标题高，会顶低这一张卡的标题、破坏三卡对齐（原注释里的取舍保留并更新）。

设备复核 `detail-d3`（矩阵论详情）：备注卡四行左对齐，右侧不再有孤立按钮与整条空白。
未验证：有备注时的「编辑」形态（同一段代码，仅文案不同）。
门禁：1161 项 / 0 失败；lint 0 errors 2 warnings；APK 1,202,540 B。
---

## AR. 2026-10-18 备注按钮改到右上角（修正 AQ）

用户澄清要右上角而非左侧。`CourseDetailActivity.noteCard` 改为：标题（weight=1）与「添加备注/编辑」同一行的两端，
删除底部 footer 行；按钮末侧内边距置 0 让标签贴齐内容右缘；高度 48dp → 40dp（避免标题下移过多与空白带）；
备注正文上间距 SPACE_M → SPACE_S。

设备复核 `detail-note-topright2`：标题与按钮同行、按钮在右上角，标题行与正文间距正常，卡片不再有 footer 空白。
未验证：有备注时的「编辑」形态；40dp 触摸目标低于 Material 建议的 48dp，是本次的取舍。
门禁：1161 项 / 0 失败；lint 0 errors 2 warnings；APK 1,202,536 B。
## 2026-10-30 仓库分析（只读代码审查）

- 范围：本地 HEAD `9b3cc29`；GitHub 默认分支 `287393d` 相比仅修改 README.md，业务源码一致。审查开始时工作区已有 README.md 修改与未跟踪 app/release/，均保留。
- 已核实：72 个主源码 Java 文件、63 个 *Test.java 文件、静态统计 1161 个 @Test 标记；这些数量不是本轮测试通过数。
- 存在问题（源码审查，未实机复现）：CourseRepository.replaceCoursesAt 的 commit 返回 false 后仍可能读到已更新内存；SettingsActivity.runImport 仅成功分支同步提醒与组件。FakePreferences.commit 的失败模型没有模拟内存已更新但写盘失败。
- 存在问题（源码审查，未实机复现）：ReminderScheduler.register 中途注册失败后仍取消 previous 中所有不在 registered 的槽位，因此无法完整保留尚未替换的旧计划。
- 存在问题：gradle.properties 的 android.aapt2FromMavenOverride 写死 D:\Sdk，构建依赖本机目录。较大 Activity 与 CourseRepository 也增加维护成本。
- 本轮验证：按 --offline --rerun-tasks 尝试 testDebugUnitTest assembleDebug lintDebug；普通、--no-daemon 与沙箱外重试均在任务执行前被 Gradle daemon / loopback connection 错误阻断。测试、构建、Lint 均未得到本轮结果；未运行模拟器或真机。没有修改业务代码，没有 commit 或 push。
- 下一步：优先补齐真实写盘失败模型与提醒注册失败注入，再修复上述异常路径；恢复本机构建环境后重新执行三项检查。

## 2026-10-30 可靠性修复（不新增功能）

根因：导入/撤销的 commit=false 不能证明内存不变，页面仅成功分支刷新衍生状态；提醒注册失败仍清理旧计划，且 FLAG_UPDATE_CURRENT 会先改旧 PendingIntent 的触发参数。

修复：SettingsActivity 在两种提交结果下同步提醒与小组件；ReminderScheduler 接入无 Android 依赖的 AlarmRegistration 协调器，仅完整注册后清理旧项，失败保留混合台账并尝试恢复旧参数；取消失败也保存已完成部分的台账。无 AlarmManager 时返回失败，保留旧台账。CourseRepository 只修正文档注释，不改变 JSON/键或写入协议。

测试：新增 AlarmRegistrationTest 6 项（完整替换、首次失败、中途失败/参数恢复、重试收敛、取消失败、空计划），FakePreferences 新增内存已更新的失败模式，导入/撤销/备份恢复各补 1 项。既有测试期望未修改。最终全源码重新 javac 编译并直接运行 JUnitCore，1170 项全部通过（原 1161 + 新 9）。

限制：使用现有生成 R.jar，验证 Java 编译与 JVM 逻辑，未覆盖资源重新生成与 APK 打包。Gradle --rerun-tasks 三项检查在任务运行前因 loopback 连接失败中断；ADB 亦失败。没有修改布局，没有新 APK 安装，没有设备/UI 自动化结果。改动未提交，未触碰用户 app/release/。完整构建与 Android 系统交互仍待验证。


## 2026-10-30 构建环境恢复与设备回归

根因分两层：普通沙箱 TCP 连接报 WinError 10013；获准沙箱外运行后，Gradle SocketConnection 的 Selector 初始化仍在 UnixDomainSockets.connect 报 Invalid argument。读取本机 JDK 源码确认 Windows 优先使用 TEMP；指定 jdk.net.unixdomain.tmpdir 到项目短目录后完整构建成功。新增 tools/verify-project.ps1，将临时设置限于当前执行且 finally 恢复环境，README 补充命令。ADB 使用沙箱外执行可连接 emulator-5554（API 37）。

验证过程中发现并修复设置页两个错误：未来学期被笼统标记为已结束、非教学期仍称今天是第 1/末周；备份摘要只数活动学期。SemesterSummary 纯规则增加 4 项日期边界测试；CourseRepository.totalCourseCount 及跨学期测试增加 1 项。保留所有既有测试期望。

Lint 原有 IconXmlAndPng 两项源于有意保留旧 PNG 与自适应 XML。尝试 v26 资源限定会产生 ObsoleteSdkInt，已撤回，未留下图标改动；app/lint.xml 对两个确切 launcher PNG 名称的目录路径设置例外，其余资源仍检查。最终完整重跑：65 类、1175 项通过，0 失败/错误；assembleDebug 成功；lintDebug No issues found。最终 APK 已覆盖安装。构建仍有 AAPT2 实验性属性与 Gradle 弃用提示，未对其作无依据的消除。

设备路径：保留原 1 学期/23 条安排；临时新建 2027 春季学期，不复制原课程，导入生成的 1 门 XLS，检查识别预览与 24 门跨学期总数；导出备份、撤销回到空学期且总数 23、预览并覆盖恢复备份，报告 2 学期/24 门/3 作息表成功。三种小组件未来学期显示“学期还没开始”，恢复原课表后显示本周及下一门课程，截图已目视检查。权限通过 finally 恢复：POST_NOTIFICATIONS 拒绝->0 条，重新授予->10 条；SCHEDULE_EXACT_ALARM uid 临时 deny->10 条且 exact=false，恢复 allow->10 条。没有模拟磁盘写满，也未声称设备上验证过写盘故障或通知到点投递。

清理：测试前三份 preferences 在 .gradle-local/device-verification/ 备份；结束时 force-stop 后逐字节还原三份，再启动应用重排；最终 weihe_schedule.xml 的全部键值与原备份一致。测试下载文件 weihe-verification.xls 与 weihe-backup-20261030-1138.json 已删除。保留私有本地验证截图、permission-results.json；均被 Git 忽略。用户 app/release/ 未改，未 commit/push。

待确认：恢复预览文件名使用了数字文档 ID（82），本轮不扩大到 SAF 元数据处理；API 26–36、真实设备后台限制、重启恢复、真实存储失败仍待验证。


## 2026-10-30 备份文件名修复与真实提醒验证

范围：只处理既有备份预览名称错误与提醒验证，不增加功能。SettingsActivity 原 displayNameOf 只截取 URI 最后一段（Downloads 常为数字 ID），且 BackupPreviewActivity 信任该 extra。改为预览读取备份的工作线程查询 OpenableColumns.DISPLAY_NAME；元数据异常不阻断文件解析。新增 data/BackupFileName，Query 接缝配合 Cursor 接口代理在 JVM 覆盖真实名/中文与空格、null cursor、空行、缺列、空白/null 名、SecurityException、读中断及关闭资源，7 项。未更改现有断言。

完整验证：1182 项（原 1175 + 7）、66 个测试类、0 失败/错误；--rerun-tasks testDebugUnitTest assembleDebug lintDebug 全部成功，Lint No issues found。最终 APK 已覆盖安装。在 Downloads 选择原有 weihe-backup-20261014-1547.json，预览显示完整真实名称；截图 .gradle-local/round3/preview-name.png 已目视核对，未对该备份执行恢复。

提醒验证使用备份后的隔离测试数据，不修改生产存储契约或系统时间。首次临时数据误用内存字段 dayOfWeek，实际存储字段为 day，课程因此被排到周一；该次只能说明重启重排，不能说明到点投递。纠正为 day 后，明确断言 epochDay 等于当天且 triggerAt 与预期毫秒时间戳一致，重新进行真实 reboot。最终有效证据：boot-recovery-corrected.log 的 BOOT_COMPLETED 自动恢复 2 条；alarms-after-reboot-corrected.txt 系统实际两条闹钟；未打开应用。

最终两门课程分别在模拟器 UTC 11:58:00.017 与 12:00:00.023 投递（北京时间 19:58:00.017、20:00:00.023），计划触发分别是整分，lateMinutes=0。ReminderNotification 和 ReminderReceiver 日志均报告成功，cmd notification get 读取到 VerifyAlarm1/2，通知栏截图 notifications-both.png；两门各发布一次。第一门触发后，通过 run-as 当前应用 UID、--user 0 重发其原 slot/trigger_at 三次，均因 no matching entry 被丢弃，无重复通知。读取到 notified_990301 与 notified_990302；系统自动生成的第三条为分组摘要，不算额外课程提醒。此实验没有单独注入 alreadyNotified 分支，也没有证明深度休眠与厂商设备上的表现。

测试数据保护与清理：测试前在 .gradle-local/round3/ 保存三份 preferences；结束时 force-stop 后逐字节还原三份，再启动原应用重排。原 weihe_schedule.xml 全部键值与备份一致；系统列表不再有测试通知，测试 notified_ 标记清除。未改变权限或系统时钟。证据 verification-results.json、delivery-final.log、duplicate-delivery.log、通知详情与截图均在 Git 忽略的 round3/。未触碰用户 app/release/，未 commit/push。

后续：SDK 绝对路径与大类整理仍按计划后置；API 26–36、云端文件提供方实机、厂商后台/深度休眠、磁盘故障不在本轮已验证范围。


## 2026-10-30 构建可移植性：标准用户配置与 Wrapper 统一

移除共享 gradle.properties 中 D: SDK AAPT2 路径及历史强制覆盖说明；本机的可选覆盖保存到 Git 忽略的 .gradle-local/home/gradle.properties，使用正斜杠避免二次转义。新增 gradle-user.properties.example，只含注释样例。其他机器默认使用 AGP AAPT2，不需要编辑共享配置。README 与 AGENTS.md 的构建入口均改为 Wrapper，说明 Studio 的 Gradle JDK、JAVA_HOME 与 Gradle user home 如何一致，不修改 IDE/全局设置。

验证脚本移除自动改用 .gradle-local/bin 的逻辑，始终调用 gradlew.bat；GradleUserHome 参数缺省继承环境变量，未提供则由 Gradle 按标准规则处理。失败检查发现旧脚本 SetEnvironmentVariable(null) 在当前 PowerShell/.NET 下将缺省变量变成空串；修正为原值为 null 时 Remove-Item Env: 对应键，其余保留。正常构建、嵌套工作目录下无效 JDK 拒绝、无效用户目录引发的真实 Gradle 非零退出，均验证环境变量与工作目录恢复，未改系统执行策略。

尝试过 settings.gradle 读取自定义本地配置后修改 StartParameter；实测 ProviderFactory 与 AGP 看不到该值，因此已完整撤回，不属于最终实现。最终直接使用 Gradle 自带的用户 gradle.properties 机制。实际属性探针确认默认值 null、本地覆盖时 D:/Sdk/.../aapt2.exe 可见。默认和覆盖两模式分别完整重跑三项检查；每次 1182 项/66 类、0 失败/错误，assembleDebug 成功，Lint No issues found。默认方式本轮成功，因此旧“该机必需覆盖”的结论不再适用。

一次辅助子进程使用 Windows PowerShell 5 时遇到本机 AllSigned 策略拒绝脚本，未修改策略；后续在当前 PowerShell 中完成验证，README 说明受策略限制时可直接用 Wrapper。此辅助失败不计为项目构建失败。启用 AAPT2 覆盖时的实验性提示与 Gradle 弃用提示未隐藏。

最后用默认 AAPT2 产物覆盖安装 API 37 模拟器，首页原课表与三种桌面小组件正常，刷新日志 3/3。截图 main.png/widgets.png、widget-refresh.log、期望失败日志和属性探针均留在被忽略的 .gradle-local/portability/，未新增业务测试或生产功能。未改课表/权限，未动 app/release/，未 commit/push。

限制：没有在其他机器、macOS/Linux、无缓存新克隆或 Android Studio 内点击同步验证。项目 Wrapper 配置与完整构建已实际执行。下一项可按用户确认后再拆分大类，本轮不开始重构。


## 2026-10-30 SettingsActivity 备份流程小步整理

新增 data/BackupService，使用无 Android UI 依赖的 Source<T>、Result<T>、Loaded 和 Executor/Consumer 接口承接流与后台结果。默认每项操作使用 BackupIO 线程，回调由页面传入 runOnUiThread 执行器。prepare 保持选择目标前的快照与自检顺序；write 仅在输出流成功关闭后返回成功；load 包含校验与预览构造；restore 重新打开来源，不复用预览文档，最终写入仍委托 CourseRepository。两个 Activity 只传应用级 resolver/仓库工厂及 UI 回调，保留原中文反馈、确认步骤与 JSON 格式。Excel 导入未动，损坏副本仅改为调用共享错误文案函数。

提取时发现原恢复代码在 try-with-resources 中先写仓库，随后 close 若失败会误报“恢复未开始”。新实现先完成读/close，再打开仓库；这是本轮一处异常顺序修正，正常路径与 UI 文案不变。新测试明确断言 close 抛 IOException 时连仓库工厂都不调用。

BackupServiceTest 新增 14 项：打开选择器期间快照不受后续编辑影响；后台执行/关闭/回调顺序；null 输出、权限拒绝、写入与 close 失败；成功预览、无效预览不查元数据、空 IOException 文案；恢复重读最新文件、无效恢复不碰仓库、输入关闭失败、仓库提交拒绝的 RestoreReport、跨学期快照与活动选择。每个异步测试还核对回调次数，避免未回调时断言空跑。所有既有测试断言未改。

最终完整检查实际执行 --rerun-tasks testDebugUnitTest assembleDebug lintDebug：67 类、1196 项（原 1182 + 14）、0 失败/错误；Debug APK 成功；Lint No issues found。AAPT2 实验性选项与 Gradle 弃用提示为既有信息，未隐藏。最终 Debug 包已覆盖安装。

API 37 设备回归：测试前备份三份 preferences；导出取消后原课表与下载列表均无变化；实际导出 weihe-backup-20261030-1223.json，解析确认格式版本 1、1 学期、23 条课程、3 作息表；取消预览选择器不改课表。为证明恢复生效，临时在一条课程备注加入 __BackupServiceVerification__；恢复确认取消后该备注仍存在且无 restore_pending，确认覆盖恢复后活动课程里该备注消失，23 条课程恢复成功。恢复前安全快照中保留旧备注属于现有协议，验证时区别于活动课程，未误当成恢复失败。再选取本轮无效 JSON，页面显示“文件不是合法的 JSON 文本，可能已损坏”“当前课表没有任何改动”。已有预览截图和失败截图已目视核对，未声称执行 instrumentation/UI 自动化测试。

清理：三份 preferences 逐字节回写原备份，再安装最终包并启动，原 weihe_schedule.xml 全部键值一致。仅删除本轮导出的 JSON 和 weihe-backup-refactor-invalid.json，下载列表与原列表一致。清理辅助脚本曾因默认 GBK 读取含中文文件名的清单失败，已改用显式 UTF-8 完成核对；应用原数据在此之前已成功还原。证据 results.json、导出内容、preview.png/invalid-preview.png 仅在被忽略的 .gradle-local/backup-refactor/。未修改原文件/权限、未 commit/push。

限制：真实磁盘写满、云端提供方、系统杀死页面等未实机注入；流/仓库故障由 JVM 检查覆盖。本轮只做备份部分，Excel 流程后置。


## 2026-10-30 界面对齐与间距修正（纯 UI）

用户要求检查 App 内 UI/文字不对齐及不协调之处并修复，不新增功能。

根因与改法：

1. SettingsActivity 的 margins(int) 内部已经 dp(top)，调用又传 margins(dp(16))，在 2.625 密度下把 16dp 放大成约 42dp；外部 LayoutParams 还覆盖 sectionLabel 的底边距。改为传原始 token，标题修改已有参数保留 SPACE_S 下间距，组间复用 SPACE_M，页面横边距统一 SPACE_L。
2. UiKit.sectionLabel 的 4dp 左缩进使关于页标题与正文错位，移除此隐式缩进。新增 infoRow：左标签 WRAP_CONTENT，右侧 0 宽度 + weight 分配余下空间，保留 SPACE_L 间隔，并以首行基线排版。AboutActivity/BackupPreviewActivity 共用，避免长文件名把左标签挤没；导出时间 T 改显示空格，日期与时间之间可换行，序列化不变。
3. EditCourseActivity 地点选项三列全部易省略，改两列；教师保留三列。选项最多两行、统一 token 内边距，水平行内 MATCH_PARENT 高度使长短选项等高。周次输入从固定 42dp 改 WRAP_CONTENT + TOUCH_TARGET 最小高度；setMinHeight 放在 setSingleLine 后，避免 TextView 的行数设置覆盖像素最小高度。关闭范围行 baselineAligned、数字关闭额外字体 padding，使数字和“至”纵向居中。色块与标题增加 SPACE_S。
4. CourseSearchActivity.guidance 用 card 再包已经有背景的 emptyState，截图出现两层白框，改为直接返回共用空状态。
5. ScheduleListActivity 的 footnote 外层是 WRAP_CONTENT，即使内层使用 weight 也不会占满卡片，按钮随标签长度漂移；外层改 MATCH_PARENT，按钮统一靠右。
6. MainActivity 原时间栏只设最小宽度，1.3 倍字体下 10-11 节的卡片撑宽，课程标题 x=289，而其他卡片 x=266。按当前文字 paint 测量 88-88 节并计入内边距，为所有卡片预留同样宽度；最小宽度 56dp 集中为 UiTokens.COURSE_TIME_MIN_WIDTH。正常/1.3 倍字体最终截图确认同列对齐，节次完整。

验证与边界：

- 最终脚本实际执行 --rerun-tasks testDebugUnitTest assembleDebug lintDebug，BUILD SUCCESSFUL，49 tasks executed，67 类 1196 项，0 failures/errors/skipped；lint-results-debug.txt 为 No issues found。保留既有 AGP 本地 AAPT2 实验性和 Gradle 弃用提示。git diff --check 通过。
- ADB 覆盖安装，API 37 / emulator-5554 / 1080x2424 / 420 dpi；实际打开首页、设置、关于、课程详情、编辑课程、搜索、课表总览、学期列表、作息列表、编辑作息与恢复预览。修改页面正常字体目视复核，首页/设置/关于/编辑课程/作息列表/备份预览另核对 font_scale=1.3；不是仪器测试或 UI 自动化测试套件。
- 临时将原 weihe-backup-20261014-1547.json 复制为 80 字符长文件名，只读预览验证换行，不执行恢复。备份时间大字体下按日期/时间换行，左标签完整。临时副本清理完成。
- 三份 preferences 均先备份；检查前后键值一致，清理时逐字节与原副本核对，重新启动后仍一致；font_scale 恢复原 1.0。未保存课程/作息更改，未卸载、未改权限。
- 证据在 .gradle-local/ui-review/（Git 忽略）：各 *before.png/*after.png、main-final.png/main-final-font130.png、course-final-font130.png/course-bottom-final*.png、schedules-final*.png、backup-final.png/backup-final-long-font130.png，以及 cleanup-result.json。
- 未验证其他 API/厂商手机、横屏/分屏、2.0 倍字体与所有弹窗/异常状态。未新增功能、依赖或测试源码；未改存储契约。
- 修改文件：MainActivity.java、SettingsActivity.java、AboutActivity.java、BackupPreviewActivity.java、CourseSearchActivity.java、EditCourseActivity.java、ScheduleListActivity.java、ui/UiKit.java、ui/UiTokens.java，以及两份交接文档。开始时 main/365fb2f 干净，全部本轮变更留在工作区，未暂存/commit/push。
