# COURSE_REMINDER_PLAN.md — 「拾课」本地上课提醒设计方案

> 本文只做分析与设计，**不修改任何生产代码、不新增依赖、不调整 Gradle 配置**。
> 所有结论以当前源码为准（`main` @ `6875549`）；凡与旧文档冲突处，以代码为准并已在文中标注。
> 涉及的权限与系统行为中，**未在本轮实测的部分已逐条标注**（见 §10.6 与 §11 的验收标准）。

---

## 〇、开工前的实际状态核查

| 项目 | 实测结果 | 依据 |
|---|---|---|
| 分支 / HEAD | `main` @ `6875549 update` | `git log --oneline -1` |
| 工作区 | **干净**（`git status --porcelain` 无输出）；前三阶段的备份/恢复/预览产物均已提交 | `git ls-tree -r HEAD` |
| 文档 | `PROJECT_ANALYSIS.md`(988 行)、`PROJECT_ARCHITECTURE.md`(699 行)、`DATA_BACKUP_DESIGN.md`、`DATA_BACKUP_RESTORE_PLAN.md`、`NEXT_OPTIMIZATION_ROADMAP.md`(635 行) 均在 | — |
| `ARCHITECTURE.md` / `ARCHITECTURE_REFACTOR_PLAN.md` | **已不存在**（工作区与 HEAD 都没有）——用户在提交时删除。本文不引用之 | `Test-Path` + `git ls-tree` |
| 单元测试基线 | **23 个测试类 / 422 项 / 0 失败 / 0 错误** | `testDebugUnitTest` 实跑 |
| 通知基建 | `NotificationManager` / `AlarmManager` / `PendingIntent` / `BroadcastReceiver` 在全部源码中出现 **0 次**；Manifest **0 权限、0 receiver、0 service** | 全量 grep |
| 既有结论 | `NEXT_OPTIMIZATION_ROADMAP.md:243` 已把「上课提醒」列为 **P1**，并指出不能用 WorkManager（`androidx.useAndroidX=false`）；本文在其基础上给出可实施的设计 | — |

**本轮不触碰备份/恢复功能**：它已实现、已提交、测试全绿。本文只分析提醒系统与它的交界处（§6.9）。

---

## 一、当前数据结构与源码依据

### 1.1 课程 —— `model/Course.java`

| 字段 | 行 | 说明 |
|---|---|---|
| `id` (long) | `:21` | 唯一标识，导出/恢复都用它 |
| `name` / `teacher` / `location` | `:22-24` | 通知要显示的三项真实信息 |
| `dayOfWeek` | `:25` | **1 = 周一 … 7 = 周日**（与 `LocalDate.getDayOfWeek().getValue()` 同构） |
| `startPeriod` / `endPeriod` | `:26-27` | **1 基**节次 |
| `startWeek` / `endWeek` | `:28-29` | **1 基**教学周，默认 1..18 |
| `parity` | `:30` | `PARITY_ALL=0` / `PARITY_ODD=1` / `PARITY_EVEN=2`（`:6-8`） |
| `scheduleName` | `:37` | 用户显式指定的作息表名；空串 = "按地点自动判断" |
| `color` | `:31` | 通知小图标着色可用 |
| `timeRange` | `:47` | **派生字段，不持久化**，由 `applyTimeRange` 填充 |

### 1.2 学期 —— `model/Semester.java`

- `start()` `:51`：第 1 教学周的起始日（`setSemesterStart` 会先归一到周一，见 §3.1）
- `lastDay()` `:80`：`start.plusWeeks(18).minusDays(1)`
- `label()` `:66` / `hasCustomName()` `:61`：通知里可以用学期名做副标题
- 不可变值对象 + `renamed` / `startingOn` / `next`

### 1.3 时间规则 —— `rule/ScheduleUtils.java`

| 成员 | 行 | 值 / 语义 |
|---|---|---|
| `SEMESTER_WEEKS` | `:10` | **18** |
| `PERIODS_PER_DAY` | `:11` | **15** |
| `mondayOf(date)` | `:22` | `previousOrSame(MONDAY)` |
| `currentWeek(start, today)` | `:26` | `floorDiv(DAYS.between(mondayOf(start), today), 7) + 1`，**再夹紧到 `[1, 18]`** |
| `weekStart(start, week)` | `:33` | `mondayOf(start) + (week-1) 周` |
| `dateOf(start, week, day)` | `:46` | `weekStart + (day-1)`，两个入参都夹紧 |
| **`occursInWeek(course, week)`** | `:52` | `startWeek ≤ week ≤ endWeek` **且**单双周匹配。**这就是"这一周上不上这门课"的唯一权威判据** |
| `dayName(day)` | `:87` | 「周一」…「周日」，夹紧 |
| `periodTime(startPeriod, endPeriod)` | `:129` | **恒用标准作息表**，不适用于真实课程（注释 `:120-127` 明确说明），提醒**不得**使用 |

### 1.4 课程查询 —— `rule/CourseQuery.java`

```java
public static List<Course> onDay(List<Course> courses, int week, int day)   // :40
```

内部即 `course.dayOfWeek == day && ScheduleUtils.occursInWeek(course, week)`（`:43-44`），并按 `startPeriod` 排序（`:48`）。
**这是提醒系统"某天有哪些课"的现成、已测（`CourseQueryTest` 15 项）入口，必须复用，不得重写。**

同包 `CourseConflicts.overlaps(first, second)` `:32` 可用于识别同一时段的重课。

### 1.5 作息表 —— `model/BellSchedule.java` / `model/BellSchedules.java`

| 成员 | 行 | 说明 |
|---|---|---|
| `BellSchedule.PERIODS` | `:23` | = `PERIODS_PER_DAY` = 15 |
| `BellSchedule.MAX_MINUTE` | `:34` | **1799**（29:59）——作息表允许跨午夜 |
| **`startMinute(period)`** | `:77` | **1 基，越界自动夹紧**（`clampPeriod`），永不抛异常 |
| `endMinute(period)` | `:82` | 同上 |
| `matchesLocation(location)` | `:111` | 宽松的地点匹配（间距/大小写不敏感） |
| `timeRange(from, to)` | `:202` | 显示用文本，如 `08:15 - 08:55` |
| `BellSchedules.resolve(schedules, scheduleName, location)` | `:176` | **课程 → 作息表的唯一权威解析**，见 §3.3 |
| `BellSchedules.presets()` / `copyOf()` | `:107` / `:122` | 预设三张（教学楼 / B/C 楼 / D/E 楼） |

### 1.6 仓库读写入口 —— `data/CourseRepository.java`

**读**（提醒只用这些）：
`getSemesters():144`、`getActiveSemester():165`、`getSemesterStart():915`、`getCourses():434`、`coursesOf(Semester):488`、`getBellSchedules():618`、**`scheduleFor(Course):847`**、`timeRangeOf(Course):837`、`reload():569`。

`scheduleFor` 的契约是"**永不为 null**"（`:846-853`，`resolve` 末步回落到 `standard()`），这让提醒端不必处理"没有作息表"的空值分支。

**写**（提醒的触发点，§6 逐一对应）：
`upsertCourse:579`、`deleteCourse:586`、`replaceCourses:593`、`copyCourses:241`、`addSemester:197/213`、`updateSemester:262`、`deleteSemester:289`、`setActiveSemester:183`、`setSemesterStart:935`、`saveBellSchedules:741`、`saveBellSchedule:767`、`renameSchedule:863`、`deleteBellSchedule:798`、`clearScheduleReferences:891`、`restoreFrom:1133`。

**关键结构性事实**：
- 仓库**没有任何变更通知机制**（无 listener / LiveData / Observable）。这是 §6 设计的核心约束。
- 仓库持有三个内存缓存（`cachedCourses` / `cachedSchedules` / `cachedSemesters`）与 `semesterId`，由 `reload():569` 清空。
- `getSemesterStart():915` 在缺失时会**写回**一个默认值（`:923-925`）——提醒端调用它是安全的，但要知道它可能写存储。

### 1.7 Manifest 与 SDK（提醒的硬约束）

```
compileSdk 34 · minSdk 26 · targetSdk 34 · versionCode 1 · versionName '1.0'
android.useAndroidX = false（无 AndroidX、无 Material、无 WorkManager）
依赖仅两个：files('libs/jxl-2.6.12.jar')、testImplementation junit:junit:4.13.2
AndroidManifest：0 权限 / 0 receiver / 0 service / 8 个 Activity，无自定义 Application 类
```

`targetSdk 34` 直接决定了两件事：**Android 13+ 必须运行时申请 `POST_NOTIFICATIONS`**；**Android 14+ 上 `SCHEDULE_EXACT_ALARM` 默认被拒**（§5.4）。
`minSdk 26` 意味着**通知渠道是必需的**（API 26 起 `NotificationChannel` 强制）。

### 1.8 测试基建（决定 §8 的分工）

- 24 个测试类 / 422 项，全部是 **JVM 单元测试**（JUnit 4.13.2），**没有 `androidTest` 源集、没有 Espresso/Robolectric**。
- `app/build.gradle` 设 `unitTests.returnDefaultValues = false` —— 这**强制**规则层不得触碰 Android API。**提醒的时间计算必须放在这一层才能被测试。**
- 现成测试替身：`data/FakePreferences.java`（内存版 `SharedPreferences`，带 `snapshot()` / `commitCount()` / `failCommitNumber(n)`）。
- 测试缝：`CourseRepository.useStoreForNextInstance(prefs)` + 包级私有构造器（测试类须位于 `com.weihe.schedule.data`）。

---

## 二、第一版功能范围

### 2.1 做（v1）

| # | 功能 | 说明 |
|---|---|---|
| F1 | **全局开关** | 设置页一个开关，默认**关**（避免未经同意就申请通知权限） |
| F2 | **提前时间 5 / 10 / 15 分钟** | 三选一，默认 **10 分钟** |
| F3 | **通知内容** | 课程名（标题）+ 开始时间 · 教室 · 教师（正文），全部取自 `Course` 真实字段 |
| F4 | **按当前学期生成** | 只针对 `getActiveSemester()` 与其 `getCourses()`，随学期切换自动改 |
| F5 | **下一节课摘要** | 设置页显示"下一节课：周三 08:15 高等数学 · 教学楼 A203（还有 2 天 5 小时）" |
| F6 | **点击通知回到今日课表** | `PendingIntent` → `MainActivity` |

### 2.2 不做（并说明理由）

- **单门课程单独关闭** → **推迟**。理由不是"复杂"，而是：`Course` 的 12 个字段是**持久化格式**，加一个 `reminderMuted` 会改格式（§9 明令禁止）。若改用提醒侧维护一个"静音课程 id 集合"，虽不碰课程格式，但要额外处理**删课后集合残留**、**导入覆盖后 id 复用**、**恢复备份后 id 全变**三种失配，并且要多一套逐课 UI。收益（少数课程不想被提醒）低于风险，放 §11 的 P3。
- 账号、服务器推送、云同步、复杂规则编辑器（"每周三提前 15 分钟、其他提前 5 分钟"之类）——明确不做。
- 铃声/震动自定义、通知分组、勿扰时段：交给系统通知渠道设置，不自己实现。

---

## 三、课程实际提醒时间的计算流程（核心）

### 3.0 总览

```
(课程, 学期, 作息表列表, 今天, 提前分钟, 视界)
        │
        ├─1. 原始教学周 rawWeek（不夹紧！）
        ├─2. CourseQuery.onDay(courses, rawWeek, dayOfWeek)   ← 复用，含单双周与起止周
        ├─3. repository.scheduleFor(course).startMinute(startPeriod)  ← 复用，含地点/名称解析
        ├─4. startAt = 当天 00:00 + startMinute 分钟
        └─5. triggerAt = startAt - 提前分钟；triggerAt > now 才保留
        ↓
   List<Reminder>（按 triggerAt 升序，受视界与条数上限约束）
```

### 3.1 教学周：**必须自己算原始周，不能用 `ScheduleUtils.currentWeek`**

`currentWeek`（`:26-31`）末尾把结果**夹紧到 `[1, 18]`**。这个夹紧对首页是正确的（超出范围时仍显示"第 1 周/第 18 周"而不是崩溃），但**对提醒是错的**：

| 场景 | `currentWeek` 返回 | 用 `onDay` 的后果 |
|---|---|---|
| 学期开始前一周（`2026-08-24`，开学 `2026-08-31`） | `0` → 夹成 **1** | **提前一周发出第 1 周的提醒** |
| 学期结束后（`2027-01-04`，`lastDay()` = `2027-01-03`） | `19` → 夹成 **18** | **假期里照常发出第 18 周的提醒** |

所以提醒端必须用**不夹紧**的原始周：

```java
static int rawWeekOf(LocalDate semesterStart, LocalDate date) {
    LocalDate firstMonday = ScheduleUtils.mondayOf(semesterStart);
    long days = ChronoUnit.DAYS.between(firstMonday, date);
    return (int) Math.floorDiv(days, 7L) + 1;          // 可能 <1 或 >18
}
static boolean isWithinSemester(LocalDate start, LocalDate date) {
    int week = rawWeekOf(start, date);
    return week >= 1 && week <= ScheduleUtils.SEMESTER_WEEKS;
}
```

这条守卫是**发不发提醒的总闸**：`isWithinSemester == false` → 当天不产生任何提醒。

**首页与提醒的一致性**：首页用 `currentWeek`（夹紧），提醒用 `rawWeekOf`（不夹紧）。两者在学期内**逐日等价**（只要 1 ≤ week ≤ 18 夹紧就是恒等），只在学期外不同——而这正是我们想要的差异。此差异必须写进代码注释，避免后来者"统一"掉。

**假期**：现有模型**完全没有**假期概念（全项目无 `holiday` / `vacation` / `停课` 相关字段）。因此提醒**无法识别法定假日或调休**，会在假期的课表日正常提醒。**这是限制，不是 bug，本轮不发明规则**（§10.1）。缓解手段是用户自行把课程的 `startWeek/endWeek` 缩短，或用 Excel 重新导入。

**用户修改学期开始日期后**：`setSemesterStart:935` 会经 `updateSemester:262` 写同一个键。日期一变，所有 `rawWeek` 全部平移，**整批提醒必须重建**（§6.4）。

### 3.2 星期与单双周：**复用 `CourseQuery.onDay`，不重写**

对某一天 `d`：`CourseQuery.onDay(courses, rawWeekOf(start, d), d.getDayOfWeek().getValue())`。

它一次性覆盖了任务列出的全部情形，且已有 15 项测试：
- **指定周次/多周课程** → `ScheduleUtils.occursInWeek` 的 `startWeek ≤ week ≤ endWeek`
- **单周/双周** → `week % 2 == 1` / `== 0`（`ScheduleUtils:56-61`）
- **不属于当前周次的课** → 天然被过滤，**不会创建提醒**（这正是"如何避免为不属于当前周次的课程创建提醒"的答案：不去创建，而不是创建后取消）

**限制（模型未表达，不自造规则）**：`parity` 以**学期周次**的奇偶为准（第 1 周为单周），不是自然周。若学校的单双周与开学周不同源，现有模型无法表达 —— 记录为限制。

### 3.3 作息时间：**必须走 `repository.scheduleFor(course)`**

```java
BellSchedule schedule = repository.scheduleFor(course);   // 永不为 null
int minute = schedule.startMinute(course.startPeriod);    // 1 基，越界夹紧
LocalDateTime startAt = date.atStartOfDay().plusMinutes(minute);
```

`BellSchedules.resolve(schedules, course.scheduleName, course.location)`（`:176-192`）的四步顺序：

1. **课程显式指定的作息表名**（`byName`，`:135`）
2. **按地点关键字匹配**（`forLocation`，`:152` → `matchesLocation`，`:111`）
3. 回落到 **`BC_NAME`（B/C 楼作息）**（`:186`）
4. 再回落到 **`STANDARD_NAME`（教学楼作息）**（`:190`）
5. 都没有 → `standard()`（`:191`）

**必须指出的一处真实歧义**（任务要求"不得默默生成错误的提醒时间"）：

- `forLocation` 返回**列表中第一个**匹配项（`:156-160`），而 `matchesLocation` 是**宽松匹配**。若两张作息表的关键字都能命中同一段地点文本，**结果取决于作息表的存储顺序**——用户在设置里调整顺序或改名，提醒时间就会静默改变。
- 第 3 步的 `BC_NAME` 回落意味着：**地点写错或写了学校未命名的教学楼时，课程会按 B/C 楼的时间提醒**，未必是学生的本意。

**处理方式（不修改现有算法，因为它同时服务于界面显示）**：
1. 提醒**只调用 `scheduleFor`**，保证「通知里的时间」与「课表上显示的时间」**永远一致**——用户若觉得时间不对，界面上看到的也是同一个错，是可诊断的，而不是只在通知里出现的幽灵错误。
2. **通知正文带上所用的作息表名**（例如 `08:15 · 教学楼 A203 · 张老师 · 按B/C楼作息`）。这样"匹配到了哪张表"是**可见的**，而不是静默的。这是把歧义暴露给用户的最低成本手段。
3. 计划在 §11 的 P2 增加一条**只读诊断**：设置页提示"有 N 门课的地点匹配到了多张作息表"，让用户自己确认。

**作息表缺失 / 节次越界 / 时间解析失败**：
- `getBellSchedules()` 为空时会**重新播种预设**（既有行为），所以不会出现"无表可用"。
- `startMinute` 对越界节次**夹紧**（`:77-79`），因此 `startPeriod = 99` 的脏数据会得到第 15 节的时间。**决定：沿用夹紧，不另做校验** —— 因为界面的 `timeRangeOf` 用的是同一个访问器，提醒与界面保持一致；若提醒另做一套判断，就会出现"界面显示 17:35、通知说数据无效"的分裂。同时 `CourseFormValidator` 与恢复校验都把节次限制在 1..15，脏数据只可能来自手工改存储。
- `minute` 允许到 `1799`（29:59，跨午夜，`MAX_MINUTE:34`）。`atStartOfDay().plusMinutes(minute)` **会自然滚到次日**（如 25:00 → 次日 01:00），这是正确语义，**显式保留**。此时提醒的"归属日"仍是课程的名义日（用于通知 id），触发时刻落在次日。

### 3.4 时间边界

| 情形 | 处理 |
|---|---|
| **提醒时刻已过** | `triggerAt > now` 才排程。已过 → **直接跳过该次**，不补发。理由：打开 App 瞬间弹出"5 分钟前该上课了"是噪声，不是帮助。 |
| **课程已开始** | 接收器**再查一次**：若 `now ≥ startAt`，只记日志、不发通知（防止迟到很久的闹钟发出无意义提醒）。 |
| **多门课同时上课** | 每门课**各自一条通知**，通知 id = `courseId` 与名义日期派生，互不覆盖；同时用 `CourseConflicts.overlaps` 在设置页提示"N 处重课"。 |
| **跨时区 / 修改系统时区** | `triggerAt` 在排程时由**当时的设备时区**把 `LocalDateTime` 转成毫秒。时区一变必须整批重建 —— 监听 `ACTION_TIMEZONE_CHANGED`（§7.2）。 |
| **手动改系统时间** | 监听 `ACTION_TIME_CHANGED` 重建；且接收器触发时用**实时重算 + `now` 守卫**，不信任排程时的旧结论。 |
| **夏令时** | 中国无夏令时；但代码仍走 `ZonedDateTime`（而非手算偏移），在有 DST 的时区下由 `java.time` 处理缺口/重叠。**不写死 +08:00**。 |
| **同一节课被排两次** | 排程入口先**整批取消旧集合**再排新集合（§6.12），且 `sync()` 幂等；通知 id 稳定 → 重复投递只会更新同一条通知。 |

---

## 四、推荐的调度架构与各组件职责

### 4.1 分层（沿用现有包结构；通知不写进 UI 类）

```
┌─ rule/ReminderPlan.java          【纯函数，零 Android 依赖】← 全部时间计算的所在地
│    输入：List<Course>, Semester, List<BellSchedule>, LocalDate now, int leadMinutes
│    输出：List<Reminder>（已排序、已过滤、已限量）
│
├─ reminder/ReminderSettings.java  【Android】独立 SharedPreferences 文件
│    enabled / leadMinutes / scheduled（待触发清单）
│
├─ reminder/ReminderScheduler.java 【Android】唯一排程入口
│    sync(context) / cancelAll(context) / scheduleAll(...)
│    负责 PendingIntent 的唯一标识、AlarmManager 调用、清单持久化
│
├─ reminder/ReminderNotifications.java 【Android】渠道创建、通知构建与发布
│
├─ reminder/ReminderReceiver.java      【Android】被闹钟唤起：发通知 → 续排
└─ reminder/ReminderRestoreReceiver.java【Android】开机/改时间/改时区：重建
```

`rule/ReminderPlan` 放在 `rule` 包，与 `CourseQuery`/`CourseConflicts`/`WeekGridPlan` 同级：**这样它才能被 JVM 测试覆盖**（`returnDefaultValues = false` 会挡住任何 Android API）。

### 4.2 数据结构

```java
public final class Reminder {
    public final long courseId;
    public final String courseName, teacher, location, scheduleName;
    public final LocalDate classDay;      // 课程的名义日期（周次+星期算出）
    public final int startMinute;         // 相对 classDay 的分钟数，可 ≥1440
    public final LocalDateTime startAt;
    public final LocalDateTime triggerAt; // startAt - leadMinutes
    public final int color;
}
```

### 4.3 调度策略：**推荐"有限视界批量 + 触发后续排 + 变更即重建"**

任务要求先比较再选择。三种候选：

| 策略 | 优点 | 缺点 | 判断 |
|---|---|---|---|
| A. 只排最近一段（如 7 天） | 闹钟数量有界、自愈 | 若长期不开 App，视界耗尽后停摆 | **采纳，但必须配"触发后续排"** |
| B. 只排最近一条，触发后重算 | 状态最少 | 单点故障：一次丢失（Doze 抑制、进程被杀、force-stop）→ **整条链断掉，永久静默**；且无法提前看到后几节 | 否 |
| C. 每次数据变化重建整学期 | 一次到位 | 18 周 × 每周 25 节 ≈ **450 个闹钟**，远超合理量，且大部分会被系统丢弃或延迟 | 否 |

**推荐 = A + 触发后续排（即 A 的自愈版）**：

- 视界：**未来 7 个自然日**
- 条数上限：**10 条**（先按 `triggerAt` 升序取前 10；超出部分留给下次续排）
- 每次 `sync()` 都**先全部取消再重排**（幂等）
- `ReminderReceiver` 发完通知后**立即再调一次 `sync()`**，把滑出视界的课程补进来

这样：常态下同时存在的闹钟 ≤ 10 个；只要闹钟还能响，链条就自动延续；任何一次 App 打开、数据变更、开机、改时间都会重建，**不依赖"只注册一次"**。

### 4.4 `AlarmManager` 方式的选择（精确 vs 非精确）

| 方式 | 精确性 | 权限 | 备注 |
|---|---|---|---|
| `setExactAndAllowWhileIdle` | **精确**，可穿透 Doze | API 31+ 需 `SCHEDULE_EXACT_ALARM`；**Android 14+ 对 `targetSdk ≥ 33` 的应用默认拒绝** | 现代设备上用户须进系统设置手动授予 |
| `setAlarmClock` | **精确**，最高优先级 | **无需权限** | 但会在状态栏常驻闹钟图标，且语义面向闹钟/日历类应用，商店政策敏感 |
| `setAndAllowWhileIdle` | **非精确**，Doze 下每 App 约 9 分钟一次节流 | **无需权限** | 可能晚几分钟 |
| `setWindow(t, w, pi)` | 非精确；API 31+ 把窗口下限抬到 10 分钟 | 无需权限 | 批量合并 |
| `set()` | 非精确，最易被合并 | 无需权限 | 不适合 |

**v1 决策：使用 `setAndAllowWhileIdle`，不声明任何精确闹钟权限。**

理由，按任务要求逐条评估：
1. 任务明令"**不要默认申请精确闹钟权限，也不要未经评估就要求用户进入系统设置**"。
2. 在 `targetSdk 34` 下，`SCHEDULE_EXACT_ALARM` 在 Android 14+ **默认被拒**，若要用精确闹钟，就**必须**把用户送去系统设置页——这正是任务要求避免的默认体验。
3. `setAlarmClock` 虽然无需权限且精确，但它会在状态栏常驻闹钟图标，且其设计意图是闹钟/日历类应用；本 App 尚未上架，**不把政策风险引入第一版**。
4. 代价是可接受且**可告知**的：`setAndAllowWhileIdle` 在设备清醒时通常接近准点，仅在深度 Doze 下被节流。

**降级与提示策略（任务要求"权限不可用时应定义清楚的降级策略与用户提示"）**：
- 设置页在开关下方固定一行说明：**「提醒不早于设定时间；设备长时间待机时可能晚几分钟。」** —— 不承诺"精确到分"。
- P2（§11）再提供**可选**的精确模式：届时才在 Manifest 声明 `SCHEDULE_EXACT_ALARM`，运行时用 `canScheduleExactAlarms()` 判断，为 false 时提供一个明确标注的「去开启精确提醒」按钮（跳 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`），并在设置页显示**当前生效的是精确还是非精确**。**不做静默跳转。**

### 4.5 `PendingIntent` 的唯一标识与取消

- 类型：`PendingIntent.getBroadcast(ctx, requestCode, intent, FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE)`
  - `FLAG_IMMUTABLE` 是 **API 31+ 的强制要求**（可变 PendingIntent 必须显式声明），这里不需要接收方改写，取不可变。
- `requestCode`：**不使用 `courseId` 哈希**（long 截断成 int 会碰撞）。改用**自有清单里分配的稳定序号**，与课程 id 解耦：
  - `ReminderSettings.scheduled` 持久化一个 JSON 数组：`[{slot, courseId, epochDay, triggerAtMillis}]`
  - `slot` ∈ `[1000, 1000+9]`，由 `sync()` 按排序结果分配
  - 取消时按清单里的 `slot` 逐个 `AlarmManager.cancel(pi)` + `PendingIntent.cancel()`
- **旧 PendingIntent 如何失效**：`sync()` 一律**先取消清单中的全部旧项**，再写新清单、排新闹钟。由于 `slot` 是复用的，同一 `slot` 上的旧闹钟会被 `cancel` 后再被新的覆盖；且 `intent` 里带 `EXTRA_TRIGGER_AT`，接收器**核对时刻不符即丢弃**，形成第二道防线（防"取消失败但仍触发"）。
- `Intent` 的 `component` 固定为 `ReminderReceiver`，`action` 用包名前缀常量，避免与系统广播冲突。

### 4.6 `BroadcastReceiver` 职责边界

| 接收器 | 触发 | 职责 | **不做** |
|---|---|---|---|
| `ReminderReceiver` | 闹钟到点 | ①核对 `EXTRA_TRIGGER_AT` 与开关状态 ②用实时时间复核该课是否仍应提醒 ③发通知 ④`sync()` 续排 | 不写课程数据、不弹 Activity |
| `ReminderRestoreReceiver` | `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` / `MY_PACKAGE_REPLACED` | 仅 `sync()` 重建 | 不发通知 |

两者都必须用 **manifest 静态注册**（不能运行时注册 —— 它们要在 App 未运行时被唤起）。
`onReceive` 内**必须同步完成**（≤10s），本文的设计只做"读偏好 + 算 ≤10 条 + 调 AlarmManager + 发 1 条通知"，无需 `goAsync()`。发射器内部一律 `try/catch`，**绝不因提醒逻辑崩溃而影响系统**。

### 4.7 `NotificationManager` 与通知渠道

- **渠道 id**：`class_reminders`；**名称**「上课提醒」；**重要性 `IMPORTANCE_HIGH`**（要 heads-up 就必须 HIGH）。
- **渠道重要性创建后不可由代码修改**（只能用户在系统设置改）。因此 v1 就定 HIGH，并在文档里说明：若用户嫌吵，应在系统里调低，而不是我们偷偷用低重要性。
- **幂等创建**：`createNotificationChannel` 对已存在的同 id 渠道是空操作，可安全重复调用。调用点：`ReminderNotifications.ensureChannel(context)`，在 `MainActivity.onCreate` 与**每次发通知前**各调一次。**不新增 Application 子类**（Manifest 目前没有，不引入新组件）。
- **重复通知**：通知 id = `(courseId, classDay.toEpochDay())` 的稳定派生值（如 `31 * courseId + epochDay` 的 int 折叠，或用清单里的 `slot`）。同一节课重复投递 → **更新同一条**，不会堆叠。
- **点击**：`contentIntent` → `MainActivity`；用 `TaskStackBuilder` 需要 AndroidX，**改用** `Intent.FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`（框架自带，不引依赖）。

---

## 五、权限、通知渠道与系统版本兼容

### 5.1 需要声明的权限（v1 两项，都是普通权限，无运行时弹窗）

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />     <!-- API 33+，运行时申请 -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" /> <!-- 普通权限，自动授予 -->
```

**不声明** `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`（§4.4）。**不声明** `WAKE_LOCK`、`FOREGROUND_SERVICE`。

### 5.2 Android 13+（API 33+）的通知运行时权限

`targetSdk 34` → `POST_NOTIFICATIONS` 必须**运行时申请**。

- **申请时机**：**用户第一次打开提醒开关时**，而不是 App 启动时。这是最小惊讶原则，也让拒绝的后果是可理解的（"你刚打开了提醒，需要允许通知"）。
- 申请结果：
  - 允许 → `enabled = true`，立即 `sync()`
  - 拒绝 → `enabled` **保持 true**（用户的意图是"我要提醒"），但**跳过排程**并按 §5.3 提示；下次进设置页或 `onResume` 时复查权限，一旦授予即自动 `sync()`（自愈）
  - 勾选"不再询问" → 提示里提供跳 `ACTION_APP_NOTIFICATION_SETTINGS` 的入口
- `minSdk 26` → API 26–32 的设备上该权限不存在，`checkSelfPermission` 恒为已授予，代码需按 `Build.VERSION.SDK_INT >= 33` 分支。

### 5.3 权限被拒时的降级与提示

设置页提醒卡片显示**明确状态行**，三态：

| 状态 | 文案 | 附加操作 |
|---|---|---|
| 已开启且权限正常 | 「已开启 · 提前 10 分钟 · 已安排 N 条提醒」 | — |
| 已开启但通知权限被拒 | 「**通知权限未开启，提醒不会显示**」 | 「去开启」（跳系统通知设置） |
| 已关闭 | 「已关闭」 | — |

**绝不在权限缺失时假装已排程**：`sync()` 在权限缺失时只更新"下一节课"文案，不写闹钟、不发通知。

### 5.4 Android 12+ 的精确闹钟限制（v1 不涉及，但要写清楚）

- API 31 引入 `SCHEDULE_EXACT_ALARM`（特殊应用权限）。
- **Android 14 起，对 `targetSdk ≥ 33` 的应用该权限默认被拒绝**，须由用户在系统设置授予（[Android 14 行为变更：精确闹钟默认被拒](https://developer.android.google.cn/about/versions/14/changes/schedule-exact-alarms?hl=en)）。
- 未持有时调用 `setExact*` 会抛 `SecurityException` —— **必须包 try/catch**，即使 v1 不使用精确闹钟，也建议在 `sync()` 外层统一兜底。
- **本轮未在真机实测该权限模型的细节**（模拟器已下线，见 §10.6）；上述依据是官方文档的行为变更说明。**该结论列入 §11 P1 的验收项，必须真机确认后才能写进用户可见文案。**

### 5.5 版本兼容矩阵

| API | 关键差异 | 处理 |
|---|---|---|
| 26–27（minSdk） | 渠道必需；后台执行限制开始 | 渠道已建；只用 `AlarmManager` |
| 28 | 后台启动 Activity 受限 | 我们只发通知，不启动 Activity |
| 29–30 | 后台位置/唤醒限制 | 不涉及 |
| **31–32** | `PendingIntent` 必须显式可变性；`SCHEDULE_EXACT_ALARM` 出现 | 用 `FLAG_IMMUTABLE`；不申请精确权限 |
| **33** | `POST_NOTIFICATIONS` 运行时权限 | §5.2 |
| **34（targetSdk）** | 精确闹钟默认被拒 | 不申请，§4.4；`setExact*` 加 try/catch |
| 35+ | 前台服务/后台限制继续收紧 | 本方案不依赖前台服务 |

---

## 六、提醒更新与取消机制

### 6.0 机制：给 `CourseRepository` 加一个**纯附加**的变更钩子

现状：仓库**没有任何变更通知机制**，而写入口有 15 个（§1.6）。若在每个 Activity 里手写"改完调 `sync()`"，迟早漏一处 —— 正是任务警告的"后续修改课程后仍沿用旧计划"。

**方案**（附加、不改任何现有逻辑、可 JVM 测试）：

```java
// data/CourseRepository.java
public interface ChangeListener { void onTimetableChanged(); }
private ChangeListener changeListener;                 // 默认 null
public void setChangeListener(ChangeListener listener)  // 新增
private void notifyChanged() { if (changeListener != null) changeListener.onTimetableChanged(); }
```

在每个**写入方法返回前**调用 `notifyChanged()`。测试用 `FakePreferences` 即可验证（设一个计数器 listener，调 `upsertCourse`，断言触发）——**不需要 Android**。

接线：各 Activity 建仓库后 `repository.setChangeListener(v -> ReminderScheduler.syncAsync(this))`。为避免重复接线，抽一个 `reminder/ReminderBinding.java`（一行方法），在**会改数据的 5 个 Activity** 的 `onCreate` 里各加一行：`MainActivity`、`SettingsActivity`、`EditCourseActivity`、`SemesterListActivity`、`ScheduleListActivity`。

**三重保险**（任务要求"避免只在应用启动时注册一次"）：
1. **精确触发**：变更钩子 → 立即 `sync()`
2. **兜底自愈**：`MainActivity.onResume()` 也调一次 `sync()`（幂等、开销 = 读一次偏好 + 算 ≤10 条）
3. **系统事件**：开机/改时间/改时区 → 重建

### 6.1 逐场景触发点（任务要求逐一分析）

| # | 场景 | 触发调用点（文件:行） | 动作 |
|---|---|---|---|
| 1 | 新增课程 | `EditCourseActivity` → `CourseRepository.upsertCourse:579` | 钩子 → `sync()` |
| 2 | 编辑日期/节次/教学周/单双周 | 同上（`upsertCourse:579`） | 钩子 → `sync()` |
| 3 | 删除课程 | `CourseRepository.deleteCourse:586`（`EditCourseActivity` / `CourseList`） | 钩子 → `sync()`；旧通知由 id 稳定派生而不会残留 |
| 4 | 修改学期开始日期 | `CourseRepository.setSemesterStart:935` → `updateSemester:262` | 钩子 → `sync()`（**全部日期平移，整批重建**） |
| 5 | 切换学期 | `CourseRepository.setActiveSemester:183` | 钩子 → `sync()`（**课程集合整体更换**） |
| 6 | 新增/删除/更新学期 | `addSemester:197/213`、`updateSemester:262`、`deleteSemester:289` | 钩子 → `sync()` |
| 7 | 修改作息表名称或时间 | `saveBellSchedules:741`、`saveBellSchedule:767`、`renameSchedule:863`、`deleteBellSchedule:798`、`clearScheduleReferences:891` | 钩子 → `sync()`（**提醒时刻依赖 `scheduleFor`，时间一改必须重排**） |
| 8 | Excel 导入覆盖课程 | `SettingsActivity` 导入 → `CourseRepository.replaceCourses:593` | 钩子 → `sync()` |
| 9 | 备份恢复 | `CourseRepository.restoreFrom:1133`（`BackupPreviewActivity`） | 钩子 → `sync()`。**恢复回滚时钩子同样触发**，语义上也正确：无论如何都以"当前实际存储"重算 |
| 10 | 全局关闭提醒 | 设置页写 `enabled=false` | **`cancelAll()`**：取消清单中全部闹钟 + 清空清单（**不发通知**） |
| 11 | 调整提前时间 | 设置页写 `leadMinutes` | `sync()`（全部时刻平移） |
| 12 | 用户拒绝通知权限 | 权限回调 / `onResume` 复查 | 跳过排程 + 状态行提示；授予后自动 `sync()` |
| 13 | 设备重启 | `ReminderRestoreReceiver` ← `BOOT_COMPLETED` | `sync()`（**开机后 AlarmManager 全部清空**） |
| 14 | 系统时间/时区变化 | `ReminderRestoreReceiver` ← `TIME_SET` / `TIMEZONE_CHANGED` | `sync()`（**毫秒目标全部失效**） |
| 15 | App 升级 | `MY_PACKAGE_REPLACED` | `sync()` |

### 6.2 避免重复注册、失效提醒、重复通知

| 风险 | 手段 |
|---|---|
| 重复注册 | `sync()` 开头**无条件**按清单取消全部旧闹钟（即使清单为空也安全）；`AlarmManager` 侧同一 `PendingIntent` 重复 `set` 本身也是替换语义，双保险 |
| 失效提醒（改了课但旧闹钟还在） | 取消旧集合 → 排新集合，**同一次 `sync()` 内完成**；`Intent` 带 `EXTRA_TRIGGER_AT`，接收器核对不符即丢弃 |
| 同一课程重复通知 | 通知 id 由 `(courseId, classDay)` 稳定派生 → 覆盖而非堆叠；接收器发通知前检查"是否已为本节课发过"（清单里记录 `firedEpochDay`） |
| 已过期的提醒反复触发 | ①排程时 `triggerAt > now`；②接收器复核 `now < startAt`；③`sync()` 每次都从"当前时刻"重新计算，过期项自然消失 |
| 闹钟触发后清单过期 | 接收器发完通知**立即 `sync()`**，用最新时刻重算并落盘 |

### 6.3 接收器运行时数据读取失败怎么办

`ReminderReceiver.onReceive` 的结构：

```java
try {
    if (!ReminderSettings.enabled(ctx)) return;                  // 已关闭 → 静默
    Reminder r = ReminderSettings.findBySlot(ctx, slot);
    if (r == null) return;                                       // 清单里没有 → 陈旧闹钟
    if (now >= r.startAt) return;                                // 已上课 → 不发
    if (notificationsBlocked(ctx)) return;                       // 无权限 → 不发
    ReminderNotifications.post(ctx, r);                          // 发
    ReminderSettings.markFired(ctx, r);
} catch (RuntimeException e) {
    Log.w(TAG, "提醒接收器异常，已忽略", e);                       // 绝不崩溃
} finally {
    try { ReminderScheduler.sync(ctx); } catch (RuntimeException ignored) { }   // 无论如何续排
}
```

**读取失败（存储损坏）时**：现有仓库的读取器本身会修复（`readCourses` 有损坏副本机制），即便仍失败也只会走进 `catch` → **不发通知、不崩溃、下次 `sync()` 重建**。原则：**提醒是尽力而为的附加功能，任何情况下都不得影响课程数据与 App 稳定性。**

---

## 七、设备重启、时区变化与权限变化

### 7.1 开机

AlarmManager 的闹钟**在重启后全部丢失**（系统行为）。`ReminderRestoreReceiver` 监听 `BOOT_COMPLETED` 并 `sync()`。

**已知限制（必须记录）**：
- **App 被"强行停止"（force-stop）后**，AlarmManager 闹钟被清除，且**在用户再次手动打开 App 之前，系统不会向该应用派发任何广播**（含 `BOOT_COMPLETED`）。这是 Android 的既定行为，**无法绕过**。用户提示：设置页在该状态下无法自动感知，属于 §10.1 的限制。
- 部分厂商 ROM 的"自启动管理"会额外拦截 `BOOT_COMPLETED`。**不申请自启动白名单，不引导用户去改**，只记录。

### 7.2 时区 / 时间变化

`ACTION_TIMEZONE_CHANGED`、`ACTION_TIME_CHANGED`、`ACTION_DATE_CHANGED` 都会让已排程的"绝对毫秒"不再对应正确本地时刻。

- 监听 `TIMEZONE_CHANGED` + `TIME_SET` → `sync()` 全量重建（用新的 `ZoneId.systemDefault()` 重新把 `LocalDateTime` 转毫秒）。
- `DATE_CHANGED`（午夜）**不**用于重建（比要频繁），而由 `ReminderReceiver` 触发后的续排覆盖；首页 `onResume` 的兜底也覆盖。
- **不写死 `+08:00`**：一律 `ZonedDateTime.of(localDateTime, ZoneId.systemDefault())`，DST 缺口/重叠交给 `java.time`。

### 7.3 权限变化

- 用户在系统设置里**关闭**了通知 → `NotificationManagerCompat.areNotificationsEnabled()` 为 false。由于不能用 AndroidX，改用 `NotificationManager.areNotificationsEnabled()`（API 24+，minSdk 26 满足）。
- 复查时机：`SettingsActivity.onResume` + 每次 `sync()`。发现被关闭 → 跳过排程、状态行改为"通知权限未开启"。
- 用户**开启**后 → 同一次 `onResume` 复查即自动 `sync()`，无需用户再点一次开关。

---

## 八、测试计划

### 8.1 纯 JVM（可自动化，占绝大多数）

新增 `rule/ReminderPlanTest.java`（预计 30+ 项）与 `reminder/ReminderSettingsTest.java`、`data/CourseRepositoryChangeHookTest.java`。

| # | 场景 | 断言要点 | 层 |
|---|---|---|---|
| 1 | 正常工作日课程提醒时刻 | 开学 2026-08-31（周一），第 1 周周三 3-4 节 → `triggerAt = 2026-09-02 08:15 - 10min` | JVM |
| 2 | 单周课程 | `parity=ODD`，第 2 周不产生、第 3 周产生 | JVM |
| 3 | 双周课程 | `parity=EVEN`，第 1 周不产生、第 2 周产生 | JVM |
| 4 | 指定周次 `startWeek=endWeek=5` | 仅第 5 周产生 | JVM |
| 5 | 多周课程 `3..11` | 第 2/12 周不产生，第 3/11 周产生 | JVM |
| 6 | 自定义作息表时间 | 改 `startMinute` 后提醒时刻随之改变 | JVM |
| 7 | 按地点匹配作息表 | `location="D305"` → 用 D/E 楼作息的时间 | JVM |
| 8 | 显式 `scheduleName` 优先于地点 | 两者冲突时以 `scheduleName` 为准 | JVM |
| 9 | 学期开始日期修改 | 平移 `start` 后，同一课程的日期与周次同步平移 | JVM |
| 10 | **学期开始前** | `2026-08-24` → **不产生任何提醒**（防 `currentWeek` 夹紧陷阱，§3.1） | JVM |
| 11 | **学期结束后** | `2027-01-04` → **不产生任何提醒** | JVM |
| 12 | 无效节次 `startPeriod=99` | 夹紧到第 15 节，**不抛异常**，且与 `timeRangeOf` 一致 | JVM |
| 13 | `endPeriod < startPeriod` | 不崩溃，按既有夹紧语义产出确定结果 | JVM |
| 14 | 空作息表列表 | `resolve` 回落到 `standard()`，仍产生提醒 | JVM |
| 15 | 提前时间 5/10/15 | 三种取值分别验证偏移量 | JVM |
| 16 | 提醒时刻已过 | 落在过去 → 被过滤 | JVM |
| 17 | 课程已开始 | `now > startAt` → 被过滤 / 接收器守卫不发 | JVM（守卫逻辑）+ 设备 |
| 18 | 视界与条数上限 | 7 天 / 10 条上限生效，且按 `triggerAt` 升序 | JVM |
| 19 | 多门课同时上课 | 产出**两条**独立提醒（互不覆盖） | JVM |
| 20 | 跨午夜节次（`startMinute ≥ 1440`） | `startAt` 滚到次日，`classDay` 保持名义日 | JVM |
| 21 | 当天无课 | 产出空列表 | JVM |
| 22 | 空课程列表 / 空学期列表 | 产出空列表，不崩溃 | JVM |
| 23 | 排序稳定性 | 同一时刻多门课顺序稳定（可重复断言） | JVM |
| 24 | 变更钩子：编辑课程 | `upsertCourse` 后 listener 被触发 | JVM（`FakePreferences`） |
| 25 | 变更钩子：删除课程 | `deleteCourse` 后触发 | JVM |
| 26 | 变更钩子：Excel 导入 | `replaceCourses` 后触发 | JVM |
| 27 | 变更钩子：改学期开始日 | `setSemesterStart` 后触发 | JVM |
| 28 | 变更钩子：切换学期 | `setActiveSemester` 后触发 | JVM |
| 29 | 变更钩子：改作息表 | `saveBellSchedule` / `renameSchedule` 后触发 | JVM |
| 30 | 变更钩子：备份恢复 | `restoreFrom` 后触发 | JVM |
| 31 | 变更钩子未设置时 | 不 NPE（现存 422 项测试必须全绿即为此回归） | JVM |
| 32 | 待触发清单序列化往返 | `[{slot,courseId,epochDay,triggerAtMillis}]` 写完读回一致 | JVM |
| 33 | `sync()` 幂等 | 连调两次，清单与排程结果相同 | JVM / 设备 |
| 34 | 通知标题与正文构造 | 纯函数产出「高等数学」/「08:15 · 教学楼 A203 · 张老师 · 按B/C楼作息」 | JVM |
| 35 | 开关关闭 | 计划为空、`cancelAll` 被调用 | JVM |
| 36 | 权限被拒（注入桩） | 不排程、不抛异常、状态为"权限未开启" | JVM（逻辑）+ 设备 |

**边界声明**：以上全部**不触碰** Android 系统服务，因此可在 `unitTests.returnDefaultValues = false` 下运行。**`AlarmManager` 与 `NotificationManager` 的真实行为无法用 JVM 测试验证**，必须靠 §8.3 的设备测试，且**不得在汇报里声称已验证**。

### 8.2 集成测试层（**当前离线，本方案不引入**）

项目**没有 `androidTest` 源集**，任务也禁止为本次引入大型测试依赖（Robolectric/Espresso）。因此：

- **不引入**任何 Android 测试框架。
- 代价：`ReminderScheduler`、两个 `Receiver`、`ReminderNotifications` 三个类**没有自动化覆盖**。
- 缓解：把这三个类写成**薄壳**——所有判断（该不该提醒、什么时候、内容是什么）都在 `rule/ReminderPlan` 里，薄壳只做"调用系统 API"。这样未覆盖的代码量最小且逻辑最简单。这也是 §4.1 分层的核心理由。

### 8.3 人工设备回归清单（必须逐条执行并记录实际结果）

| # | 步骤 | 期望 |
|---|---|---|
| M1 | 全新安装，进设置 → 打开提醒 | 弹出通知权限请求（API 33+） |
| M2 | 拒绝权限 | 状态行显示"通知权限未开启，提醒不会显示"；**无崩溃**；无闹钟被排 |
| M3 | 去系统设置授予权限后返回 | 状态行自动变为已开启；"下一节课"出现 |
| M4 | 设提前 5 分钟，把某课改到 6 分钟后开始，息屏等待 | 通知在课前约 5 分钟出现；记录**实际偏差**（这是精确/非精确可靠性的唯一实测手段） |
| M5 | 同上但设备静置 30 分钟进入 Doze 后再测 | 记录实际偏差；验证 §5.3 的"可能晚几分钟"文案是否诚实 |
| M6 | 点通知 | 打开 App 到今日课表 |
| M7 | 改课程节次 | "下一节课"与通知内容随之更新，**旧闹钟不再触发** |
| M8 | 删除该课程 | 不再有提醒 |
| M9 | 关闭提醒开关 | "已安排"归零；等待原定时刻**不出现通知** |
| M10 | 重启设备 | 开机后"下一节课"恢复；闹钟按新时间触发 |
| M11 | 修改系统时间（+/− 数小时） | 提醒按新时间重排 |
| M12 | 修改系统时区 | 同上 |
| M13 | Excel 导入覆盖课程 | 提醒随之重建 |
| M14 | 备份恢复后 | 提醒按恢复后的课表重建 |
| M15 | 强行停止 App 后等待 | 记录现象（预期：不再提醒，直到用户手动打开）——**这是已知限制，须如实记录** |
| M16 | 同时段两门课 | 出现**两条**通知，互不覆盖 |
| M17 | 学期结束后一天 | 无提醒（§3.1 的守卫） |

### 8.4 关于设备可用性

本轮设计期间，之前会话中可用的模拟器（`emulator-5554`，API 37）**已下线**，`adb devices` 返回空。因此：

- **本轮没有任何权限或通知行为被真机验证**。
- §5.4 的 `SCHEDULE_EXACT_ALARM` 默认拒绝、§5.2 的权限弹窗时机、§4.4 的非精确偏差，**全部属于"依据官方文档的设计判断"，不是实测结论**。
- §11 把"确认权限模型"列为 P1 的**验收前置项**。

---

## 九、预计新增或修改的文件及职责

### 9.1 新增

| 文件 | 职责 | 依赖 |
|---|---|---|
| `rule/ReminderPlan.java` | **纯函数**：由课程/学期/作息表算出 `List<Reminder>`（§3 全部计算） | 仅 `java.time` + `model` + `rule` |
| `rule/Reminder.java` | 提醒值对象（§4.2） | 仅 `java.time` |
| `reminder/ReminderSettings.java` | 独立 SharedPreferences（`enabled` / `leadMinutes` / `scheduled` 清单 / `firedEpochDay`） | Android |
| `reminder/ReminderScheduler.java` | 唯一排程入口：`sync` / `cancelAll` / `syncAsync`；`PendingIntent` 标识与取消 | Android |
| `reminder/ReminderNotifications.java` | 渠道创建、通知构建与发布、权限查询 | Android |
| `reminder/ReminderReceiver.java` | 闹钟到点：守卫 → 发通知 → 续排 | Android |
| `reminder/ReminderRestoreReceiver.java` | 开机 / 改时间 / 改时区 / 升级 → 重建 | Android |
| `reminder/ReminderBinding.java` | 一行接线：把仓库变更钩子接到 `syncAsync` | Android |
| `rule/ReminderPlanTest.java` 等 3 个测试类 | §8.1 的 36 项 | JUnit 4 |

### 9.2 修改（**每处都是小改动**）

| 文件 | 改动 | 规模 |
|---|---|---|
| `data/CourseRepository.java` | 新增 `ChangeListener` 接口 + `setChangeListener` + `notifyChanged`，在 15 个写方法返回前各加一行 | ~35 行，**纯附加** |
| `AndroidManifest.xml` | 2 个 `<receiver>`（`exported="false"`，`ReminderReceiver` 另需 `android:exported="false"` + 无需 `intent-filter`；`ReminderRestoreReceiver` 需 `intent-filter` 声明 4 个 action）+ 2 个 `<uses-permission>` | ~20 行 |
| `SettingsActivity.java` | 新增一张「上课提醒」卡片：开关、5/10/15 选择、状态行、「下一节课」摘要 | ~120 行，**不改现有 5 张卡片** |
| `MainActivity.java` | `onCreate` 建渠道 + 接线；`onResume` 兜底 `sync()` | ~8 行 |
| `EditCourseActivity.java` / `SemesterListActivity.java` / `ScheduleListActivity.java` | 各 1 行接线 | 3 行 |
| `BackupPreviewActivity.java` | 恢复成功后 `sync()`（钩子已覆盖，此行保险） | 1 行 |

### 9.3 明确**不**修改

`MiniJson`、`BackupCodec`、`BackupPreview`、`RestoreReport`、`CourseRepository` 的存储键与格式、`BellSchedule`/`BellSchedules`/`ScheduleUtils` 的解析逻辑、任何 Activity 的现有布局与交互、`app/build.gradle`、`res/`。

**特别注意**：提醒的偏好放在**独立文件**，且**不进入备份范围**。原因：提醒排程是**设备状态**而非用户数据——恢复到另一台设备后，旧设备的闹钟毫无意义，必须在新设备上重建。这也与既有设计一致：`CourseRepository.isScopedKey` 是**白名单**，未知键天然被备份/恢复忽略。

---

## 十、失败场景、限制与风险

### 10.1 明确的功能限制（不发明规则的部分）

1. **无假期概念**：模型里没有节假日/停课日字段，提醒会在法定假日的课表日正常发出。用户只能自行缩短周次或重新导入。
2. **无调休概念**：周末补课无法表达（`dayOfWeek` 只有周一到周日，没有"某周周六补周一的课"）。
3. **单双周以教学周奇偶为准**：若学校口径不同，模型无法表达。
4. **强停后不再提醒**：Android 既定行为，无法绕过。
5. **厂商 ROM 的自启动拦截**：部分 ROM 会拦 `BOOT_COMPLETED`，不做适配也不引导用户改系统设置。

### 10.2 提醒时刻可能不符合预期（作息表匹配歧义）

`BellSchedules.forLocation` 取**第一个**匹配项（`:156-160`），且 `resolve` 对未识别地点回落到 **B/C 楼作息**（`:186`）。因此地点文本写得含糊或用了学校未命名的楼时，提醒时间会按 B/C 楼计算。

- **不修改该算法**（它同时决定界面显示的时间，改它会改变现有展示行为，违反约束）。
- 缓解：通知正文标注所用作息表名（§3.3），把歧义**暴露**而非隐藏。

### 10.3 非精确闹钟的可靠性风险

`setAndAllowWhileIdle` 在深度 Doze 下可能晚数分钟（官方文档说明），极端情况下可能晚更多。这是 v1 选择"零权限"的**已知代价**，必须在 UI 中如实告知（§5.3），**不得在文案里承诺准时**。

### 10.4 数据一致性风险

- 提醒清单（`scheduled`）与课程数据是**两个独立的存储**，理论上可能不同步（如清单写入后进程被杀）。缓解：`sync()` 幂等且每次全量重算；接收器带 `EXTRA_TRIGGER_AT` 与"课程是否仍存在"的复核。
- **不接受**为了提醒而修改课程持久化格式（§2.2）。

### 10.5 测试覆盖的真实缺口

`ReminderScheduler` / 两个 `Receiver` / `ReminderNotifications` **没有自动化测试**（无 `androidTest`，且不引入框架）。这四类的正确性依赖 §8.3 的人工清单。**不得声称提醒"已被完整测试"。**

### 10.6 本轮未验证的部分（必须随文档一起传递）

| 项 | 状态 |
|---|---|
| 权限模型（`POST_NOTIFICATIONS` 时机、`SCHEDULE_EXACT_ALARM` 默认拒绝） | **仅依据官方文档，未真机实测**（模拟器已下线） |
| 非精确闹钟的实际偏差 | **未实测** |
| Doze / 厂商 ROM 下的行为 | **未实测** |
| 开机广播在目标 ROM 上是否送达 | **未实测** |
| 通知渠道与 heads-up 的实际观感 | **未实测** |

---

## 十一、分阶段实施步骤与验收标准

### P0 — 纯计算层（无 Android、无权限、可完全 JVM 验证）

**做什么**：新增 `rule/Reminder.java`、`rule/ReminderPlan.java`、`rule/ReminderPlanTest.java`。不动 Manifest、不动 UI、不加权限。

**为什么先做**：任务的核心是"把课程数据变成准确的日期时间"（§四）。把这部分独立成纯函数，是唯一能把它**完整自动化验证**的方式，也让后续 Android 层退化成薄壳。

**验收标准**
1. §8.1 中标注 JVM 的 36 项全部实现并通过。
2. **全部既有 422 项测试仍为 0 失败 0 错误**（`--rerun-tasks testDebugUnitTest`）。
3. `assembleDebug` 与 `lintDebug` 通过（lint 应为 `No issues found.`）。
4. 特别验证 §3.1 的两个陷阱用例（学期前后各一天）在测试中**显式存在**。
5. 本阶段结束后，`git status` 只多出 3 个新文件。

### P1 — 通知与排程骨架（最小可用闭环）

**做什么**：`reminder/ReminderSettings`、`ReminderScheduler`、`ReminderNotifications`、`ReminderReceiver`；Manifest 加 `POST_NOTIFICATIONS` 与 `ReminderReceiver`；`MainActivity` 建渠道 + `onResume` 兜底 `sync()`；`EditCourseActivity` 接线。

**验收标准**
1. **先在真机确认权限模型**（§5.2 / §5.4 的结论必须实测复核；若与文档不符，**先改本设计文档再改代码**）。
2. 人工清单 **M1–M9、M16** 全部执行并记录实际结果（尤其 M4/M5 的**实际偏差数值**）。
3. 关闭开关后，原定时刻**确认不出现通知**（M9）。
4. 既有 422 项测试 + P0 新增测试全绿；`assembleDebug` / `lintDebug` 通过。
5. 未引入任何新依赖（`app/build.gradle` 的 `dependencies` 块零改动）。

### P2 — 系统事件与自愈

**做什么**：`ReminderRestoreReceiver`（开机/改时间/改时区/升级）+ Manifest 的 `RECEIVE_BOOT_COMPLETED` 与 `intent-filter`；`CourseRepository` 变更钩子 + `ReminderBinding` 接线到 5 个 Activity；变更钩子的 JVM 测试。

**验收标准**
1. 人工清单 **M10–M15、M17** 全部执行并记录（M15 强停现象**如实记录为已知限制**）。
2. 变更钩子的 JVM 测试（§8.1 的 24–31）通过，且**未设置 listener 时既有测试全绿**（回归证明是纯附加）。
3. 重启后提醒自动恢复；改时区后时刻正确。
4. 全量测试 + 构建 + lint 通过。

### P3 — 可选增强（**默认不做，需另行确认**）

- 精确闹钟**可选**模式（§4.4 的 P2 部分）：声明 `SCHEDULE_EXACT_ALARM`、`canScheduleExactAlarms()` 判断、明确的用户主动开启入口、显示当前模式。
- 单门课程静音（用提醒侧的 `mutedCourseIds` 集合，**不碰课程格式**）。
- 假期/停课日（需要新数据模型，属独立功能）。
- 提醒历史与"已提醒"标记。

**验收标准**：每一项单独确认收益/成本后再启动；精确闹钟必须先完成"真机确认权限模型"。

### 建议的执行顺序与工作量

P0（1 个工作单元）→ P1（2–3 个）→ P2（1–2 个）→ 视情况 P3。**P0 与 P1 之间不插入其他任务**，因为 P1 的价值完全依赖 P0 的正确性。

---

## 附：本文引用的源码位置索引

```
model/Course.java                       :6-8 :21-31 :37 :47
model/Semester.java                     :51 :61 :66 :80
model/ScheduleUtils.java                :10 :11 :22 :26-31 :33 :46 :52 :87 :129
model/BellSchedule.java                 :23 :34 :77-84 :111 :202
model/BellSchedules.java                :36 :39 :107 :122 :135 :152 :176-192
rule/CourseQuery.java                   :40-50 :56 :74 :105
rule/CourseConflicts.java               :32 :57
rule/CourseFormValidator.java           :106
data/CourseRepository.java              读:144 165 434 488 569 575 618 837 847 915
                                        写:183 197 213 241 262 289 579 586 593
                                           741 767 798 863 891 935 1133
data/FakePreferences.java               测试替身
app/build.gradle                        compileSdk 34 / minSdk 26 / targetSdk 34
app/src/main/AndroidManifest.xml        0 权限 / 0 receiver / 8 Activity
NEXT_OPTIMIZATION_ROADMAP.md            :243-254 既有提醒任务结论
```
