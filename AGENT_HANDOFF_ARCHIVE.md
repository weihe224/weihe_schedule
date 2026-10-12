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
