# PROJECT_ANALYSIS.md — 拾课（WeiheSchedule）完整项目分析

> **本次仅分析，未修改、未新增、未删除任何项目代码、Gradle 配置、资源或依赖。**
> 本文所有结论均来自实际代码，逐条标注 `文件:行号`。无法从代码确证的，明确写「待确认」，不做推测。
>
> **分析对象**：工作区 `D:\vibe coding\Weihe\Weihe`，git HEAD = `21f20ec`（分支 `main`），工作树干净。
> 需求指定的路径 `D:\Weihe` 是早期快照且对本次会话不可写；经比对，活的项目是工作区（有 `.git`、`icon.png`、`tools/`，`app_name` 已是「拾课」）。两份文档写入工作区根目录。
>
> 配套文档：`PROJECT_ARCHITECTURE.md`（只描述技术栈/结构/页面/数据/数据流/依赖/导航）。
>
> **方法**：31 个生产源文件 + 18 个测试文件逐文件通读（编辑器/列表页、周网格/导入管线由子代理并行全读并回报带行号结论）；只读检查全部构建配置；只读运行检查（`adb` 读设备上真实 `SharedPreferences`、`uiautomator dump` 逐屏核对）。
> **未运行 Gradle**，因此构建/测试结果引用仓库内 `ARCHITECTURE.md:84-91` 记录的上一次执行，本次未复现。

> ### ⚠️ 时效说明（后续更新，正文未改）
>
> 本文是 **git HEAD `21f20ec` 时点的分析快照**。此后项目发生了两批改动，正文中的部分数字与
> 结构描述已不再准确，**阅读时请以本说明与 `PROJECT_ARCHITECTURE.md` 为准**：
>
> 1. **5 个 P0 正确性修复**（提交 `1213ef7`）：地点→作息表匹配、作息表重命名级联、禁止删除最后一张
>    作息表、作息表改用稳定标识定位、`WeekGridActivity` 总览页最后一行高度。测试数由
>    **221 增至 239**。
> 2. **包结构整理**（见 `ARCHITECTURE_REFACTOR_PLAN.md`）：31 个类由单包拆为 7 个包
>    ——根包（7 个 Activity）+ `.model` `.rule` `.data` `.importer` `.design` `.ui`。
>    **纯移动 + 补 import，未改任何行为。**
>
> 因此正文中下列说法已过时：第 41/58/592/775 行的「221 个测试」现为 **239**；
> 第 51/63-65 行的「31 个类全部在 `com.weihe.schedule` 单包内」现为 **7 个包**；
> 全部 `文件:行号` 引用在两次改动后可能有偏移。
> **§十六「结构评价」提出的问题已在第 2 项改动中解决。**

---

## 二、技术栈分析（实际配置）

| # | 项目 | 实际值 |
|---|---|---|
| 1 | Android Gradle Plugin | **8.13.0**（`build.gradle:2`） |
| 2 | Gradle | **8.13**（`gradle/wrapper/gradle-wrapper.properties`，阿里云镜像，`validateDistributionUrl=false`） |
| 3 | compileSdk | **34**（`app/build.gradle:13`） |
| 4 | targetSdk | **34**（`app/build.gradle:22`） |
| 5 | minSdk | **26**（`app/build.gradle:21`） |
| 6 | Java 版本 | 源码/目标 **17**（`app/build.gradle:58-59`）；本机构建 JDK 20 |
| 7 | Kotlin 版本 | **无**。无 Kotlin 插件、无 `.kt`、无 `kotlin-stdlib` |
| 8 | 主要语言 | **100% Java**，31 文件 / 10,538 行 |
| 9 | UI 方式 | **既非 XML 布局，也非 Compose**：纯 Java 构建 View 树。`res/layout/` 不存在 |
| 10 | AndroidX / Material | **都未使用**。`gradle.properties:4` 显式 `android.useAndroidX=false`；唯一二进制依赖是 `libs/jxl-2.6.12.jar`（300 KB 级，读 .xls） |
| 11 | applicationId | **com.weihe.schedule** |
| 12 | namespace | **com.weihe.schedule**（与 applicationId 相同） |

**其他重要配置**：`versionCode 1` / `versionName '1.0'`；`rootProject.name = 'WeiheSchedule'`；
`android.nonTransitiveRClass=true`；lint `abortOnError true` 且禁用 7 项检查（`app/build.gradle:62-75`）；
`unitTests.returnDefaultValues = false`（`app/build.gradle:31`，**刻意的架构约束**，见 §十二）；
release 不混淆（`minifyEnabled false`）。

**技术栈评价**：这是一套**去依赖化**的技术选型。没有 AndroidX、没有 Material 库、没有 Retrofit/OkHttp/Gson/Room，
连 JSON 都是手写的（`MiniJson`，453 行）。原因写在 `ARCHITECTURE.md:351-355`：构建环境里那些库不可用。
代价是**所有 Material 3 组件手写**（`UiKit` 1432 行），收益是**整个规则层可以在纯 JVM 上测试**——
这直接决定了本项目 221 个单元测试能跑在毫秒级、不需要模拟器。

---

## 三、项目目录结构

完整目录树见 `PROJECT_ARCHITECTURE.md` §二。要点：

| 关注点 | 实际情况 |
|---|---|
| `app/src/main/java` | 31 个文件，10,538 行，全部在 `com.weihe.schedule` 单包内（**时点快照；现已拆为 7 个包，见文首时效说明**） |
| `app/src/main/kotlin` | **不存在** |
| `app/src/main/res/layout` | **不存在** |
| `app/src/main/res/drawable` | 11 个 Material 矢量图标（24dp vector） |
| `app/src/main/res/values` | 4 个文件；`strings.xml` **只有 1 条**（`app_name` = 拾课） |
| `app/src/main/res/mipmap-*` | 6 个目录 / 27 个文件（自适应三层 + legacy 两种 × 5 密度） |
| `app/src/main/assets` | **不存在** |
| `app/src/test` | 18 个文件，3,533 行，221 个 `@Test` |
| `app/src/androidTest` | **不存在**（无仪器测试） |
| `app/libs` | `jxl-2.6.12.jar` |
| `tools/` | 6 个开发工具（图标生成、配色校验、截图取色、UI dump/点击），不打包 |

**结构评价**：单包 31 个类，靠类名而非包名分层。对 10K 行规模够用，但已到边界——
`com.weihe.schedule` 里同时住着 Activity、设计系统、JSON 解析器和网格算法，
仅靠命名约定（`*Activity` / `*Json` / `*Plan`）区分。**详见 §十六。**
（**该问题已解决**：31 个类现分居 7 个包，见文首时效说明与 `ARCHITECTURE_REFACTOR_PLAN.md`。）

---

## 四、页面结构分析

### 4.1 全部页面：7 个 Activity（0 个 Fragment、0 个 Compose Screen）

```
MainActivity 「我的课表」  ← LAUNCHER，唯一 exported
├── SettingsActivity 「设置」
│   ├── SemesterListActivity 「学期」
│   └── ScheduleListActivity 「作息时间」──► EditScheduleActivity 「编辑/新建作息表」
├── SemesterListActivity（首页学期整行）
├── WeekGridActivity 「课表总览」──► EditCourseActivity 「编辑/添加课程」
├── EditCourseActivity（首页课程卡片 / 长按删除）
└── 系统文件选择器（SAF）——⚠ 死路径，见 M1
```

| 页面 | 文件 | 行数 | 代码密度评价 |
|---|---|---|---|
| 我的课表 | `MainActivity.java` | **1324** | 过重（见 §十三） |
| 课表总览 | `WeekGridActivity.java` | **907** | 过重 |
| 编辑课程 | `EditCourseActivity.java` | 795 | 偏重 |
| 设置 | `SettingsActivity.java` | 511 | 偏重 |
| 编辑作息表 | `EditScheduleActivity.java` | 434 | 可接受 |
| 学期 | `SemesterListActivity.java` | 364 | 可接受 |
| 作息时间 | `ScheduleListActivity.java` | 251 | 合理 |

### 4.2 非 Activity 的可见界面

底部弹层（周次选择器）· 对话框（导入确认、删除确认、冲突确认、日期输入）·
自定义 `PressFeedback`（按压缩放）· 两个自定义 ScrollView（冻结表头/节次列）。
**没有 Adapter、没有 RecyclerView**——列表一律 `LinearLayout.addView`。

### 4.3 页面结构评价

- ✅ **层级扁平**，最深 3 层（首页 → 设置 → 作息列表 → 作息编辑），返回逻辑天然简单。
- ✅ **无 Fragment** 意味着没有 Fragment 生命周期问题、没有事务、没有返回栈陷阱。
- ⚠️ **无统一导航层**：7 个 Activity 各自 `new Intent`，路由散落在 11 处调用点（清单见 `PROJECT_ARCHITECTURE.md` §7.2）。
  改一个 extra key 要同时改发起方和接收方，编译器不帮忙。
- ⚠️ **端口锁死**：7 个 Activity 全部 `screenOrientation="portrait"`（`AndroidManifest.xml:10-40`），
  平板/折叠屏/横屏全部是黑边或拉伸。这是**产品决策还是权宜之计没有记录**，标为待确认。

---

## 五、功能分析

### 5.1 功能总览（15 项对照需求）

| # | 功能 | 状态 | 入口 | 主要文件 | 核心方法 | 数据来源 | 最终显示 |
|---|---|---|---|---|---|---|---|
| 1 | 课程表（当天视图） | ✅ 已实现 | App 图标 | `MainActivity` | `renderCourses()` | `CourseRepository.getCourses()` | 首页课程卡片列表 |
| 2 | 周次（1–18 周） | ✅ | 首页周卡片 | `ScheduleUtils` `MainActivity` | `currentWeek` `weekStart` `occursInWeek` | `semesterStart` + `LocalDate.now()` | 周卡片标题、日期条 |
| 3 | 日期切换 | ✅ | 首页日期条 7 格 | `MainActivity` | `renderDayStrip` `selectDay` | `ScheduleUtils.dateOf` | 日期条 + 当天课程列表 |
| 4 | 上一周 / 下一周 | ✅ | 周卡片两个箭头 + 左右滑动 | `MainActivity` | `stepWeek` `weekSwipeListener` | 同上 | 整页重绘 |
| 5 | 课程详情 | ⚠️ **无独立详情页** | — | — | — | — | 信息全部平铺在课程卡片上；点卡片直接进编辑页 |
| 6 | 教师信息 | ✅ | — | `Course.teacher` | — | `CourseRepository` | 课程卡片次级信息行（带人像图标） |
| 7 | 教室信息 | ✅ | — | `Course.location` | — | 同上 | 课程卡片次级信息行（带定位图标）+ **作息解析的输入** |
| 8 | 上课时间 | ✅ | — | `BellSchedule` `BellSchedules` | `resolve` `timeRange` | `bell_schedules` + 课程地点 | 课程卡片时间块（如 `08:15 / 09:35`） |
| 9 | 学期 | ✅ | 首页学期整行 / 设置 | `Semester` `SemesterListActivity` | `label` `nextStart` | `semesters` | 学期列表卡片、首页页头 |
| 10 | 设置 | ✅ | 首页右上齿轮 | `SettingsActivity` | `refresh` `shiftSemesterStart` `editSemesterStart` | 全部 | 4 张卡片 |
| 11 | 数据导入 | ✅ | 设置 →「选择文件导入」 | `ExcelTimetableReader` `TimetableImportParser` `SettingsActivity` | `read` `parse` `shiftWeeks` | 教务系统 `.xls` | 覆盖当前学期课表 |
| 12 | 数据编辑 | ✅ | 课程卡片 / 总览 `+` | `EditCourseActivity` | `validateAndSave` `applyForm` | 表单 | `courses:<id>` |
| 13 | 通知 | ❌ **未实现** | — | — | — | — | — |
| 14 | 提醒（上课提醒） | ❌ **未实现** | — | — | — | — | — |
| 15 | 其他 | 见下 | | | | | |

**其他已实现功能**：总课表 / 单周课表双模式（`WeekGridActivity`）· 作息表自定义与多套管理
（`EditScheduleActivity` + `ScheduleListActivity`）· 课程冲突检测（`CourseConflicts`）·
单双周（`Course.parity`）· 课程颜色（9 色）· 学期复制（`copyCourses`）· 课程长按删除 ·
滑动切换周次（`GestureDetector`）· 周次选择底部弹层。

**明确缺失**：通知/提醒（无 `NotificationManager`、无 `AlarmManager`、无 `WorkManager`、Manifest 无任何权限声明）·
课程详情页 · 课程搜索 · 桌面小组件 · 数据导出/备份（`allowBackup="true"` 但无自建导出）·
**深色模式**（主题固定 `Theme.Material.Light.NoActionBar`）· 多语言（`strings.xml` 仅 1 条）。

### 5.2 逐个功能的实现要点（选 4 个最关键的）

**① 课程表首页（`MainActivity`）**
`onCreate:63-83` 建仓库 → 算当前周/日 → `buildRoot()` 搭视图 → `refreshAll()`。
`refreshAll():724-781` 依次刷新周卡片、日期条、课程列表。`renderCourses():1040-1060`
调 `CourseQuery.onDay(repository.getCourses(), selectedWeek, selectedDay)` 过滤，
`CourseConflicts.conflictingIds` 标冲突，`createCourseCard():1090-1150` 逐张建卡并 `UiKit.enter` 错落入场。
**业务逻辑全部在 `CourseQuery`/`CourseConflicts`/`ScheduleUtils` 里，Activity 只做装配**——这是本项目最好的部分。

**② 周网格总览（`WeekGridActivity`）**
排布算法在 `WeekGridPlan`（388 行，纯逻辑）：
一天内按开始节次排序 → 把时间上相连的课聚成 `Cluster` → 簇内按「第一个尾部不重叠的车道」贪心分配 `lane`
（`WeekGridPlan:333-338`）→ 每个不同开始节次是一个 `Cell` → 每门课是一个 `Block`。
渲染在 `render():419-491`：每列一个 `FrameLayout`，簇内按 `rowTop(period)` 绝对定位。
滚动架构是**只有表身可滚**，表头用 `setTranslationX`、节次列用 `scrollTo` 跟随（`WeekGridActivity:879-905`）。

**③ Excel 导入**
`SettingsActivity.openImportPicker():389` → SAF → `onActivityResult:69-75` → `importTimetable:392`
→ 后台线程 → `ExcelTimetableReader.read:17`（jxl 读 BIFF，含合并单元格几何）
→ `TimetableImportParser.parse`（找表头行 → 定位星期列 → 解析课名/教师/地点/周次语法）
→ `shiftWeeks` 平移周次并翻转单双周 → `replaceCourses` 覆盖当前学期。
**导入是唯一的外部数据入口，也是唯一使用后台线程的地方（`SettingsActivity:397`）。**

**④ 作息时间（本项目的核心差异化）**
三张表硬编码在 `BellSchedules:60-96`，解析顺序在 `resolve:176-192`。
详见 §十一。

### 5.3 功能评价

- ✅ **规则层与界面层分离得非常干净**，这是本项目最有价值的架构资产。
- ⚠️ **功能广度偏窄**：没有提醒、没有详情页、没有搜索、没有导出。对"能正式发布的大学课程表 App"而言，
  **上课提醒通常是最被需要的一个功能**（也是课程表类 App 的主要留存手段），当前完全没有。
- ⚠️ **无课程详情页**：卡片已承载名称/时间/教室/教师/周次/冲突标记 6 类信息，
  `MainActivity.createCourseCard` 里 `name` 限 2 行、`location`/`teacher` 各限 1 行并省略号截断——
  信息更长的课程（如"教学楼F楼F406多"这类长地点串）会被截断，且点进去是**编辑**而不是**查看**，
  对只想确认信息的用户是误操作风险。

---

## 六、数据模型分析

完整类图见 `PROJECT_ARCHITECTURE.md` §四。实际代码里**只有 5 个业务模型类**，没有 DTO/Entity/Bean 之分：

| 类 | 行数 | 角色 | 可测性 |
|---|---|---|---|
| `Course` | 69 | 唯一的业务实体（课程 = 一条安排） | 纯 Java |
| `Semester` | 120 | 学期 | 纯 Java，有 10 个测试 |
| `BellSchedule` | 242 | 一张作息表（不可变） | 纯 Java，有 17 个测试 |
| `BellSchedules` | 193 | 三张预设表 + 解析规则 | 纯 Java，有 14 个测试 |
| `TimetableGrid` | 87 | 导入用的内存工作表 | 纯 Java，仅作测试夹具 |

**没有的模型**：`Teacher`、`Classroom`、`Exam`、`Homework`、`Grade`。
教师和教室是 `Course` 的**两个 String 字段**，不是实体——所以没有"同一老师的所有课"这种查询能力。

**`Course` 的字段（`Course.java:21-43`）**：
`id`(long) / `name` / `teacher` / `location` / `dayOfWeek`(1-7) / `startPeriod` / `endPeriod` /
`startWeek` / `endWeek` / `parity`(0/1/2) / `color`(int) / `scheduleName` / `timeRange`(派生，不落盘)。
**注意 `location` 是双重职责**：既是显示文本，又是作息表自动匹配的输入。

**模型评价**：
- ✅ 字段扁平、无继承、无循环引用，`Course.copy()` 手写深拷贝，序列化显式（`toJson`/`fromJson` 各 17 行），
  没有反射、没有注解——**数据格式完全可控**，这对"不改变数据库"的约束是好事。
- ⚠️ **`Course` 是可变 public 字段的裸 DTO**（无 getter/setter、无 equals/hashCode）。
  `CourseRepository.getCourses()` 返回深拷贝来兜底（`:396, :402`），
  但 `getBellSchedules()` **返回缓存实例本身**（`:577`）——**两个方法契约不一致**，见 M7。
- ⚠️ **`timeRange` 是混进持久化模型的派生字段**：注释明确说"不落盘"（`Course.java:35-43`），
  但它和落盘字段并列，且 `copy()` 会带走它。语义上它属于视图层。
- ⚠️ **`Semester.id` 用 `System.currentTimeMillis()`**（`Semester.java:35-39`，`CourseRepository:203, 320`），
  理论上同一毫秒创建两个学期会撞 id；`nextSemesterId:319-325` 有向前找空位的保护，实际安全，但设计脆弱。
- ⚠️ **无 `Exam` 模型**，但"大学课程表 App"通常要显示考试安排；当前无此能力。

---

## 七、数据来源与数据流

**本项目没有网络层。** 无 Retrofit/OkHttp/HttpURLConnection/任何 `INTERNET` 权限。
数据只有两个来源：

```
① 首次安装的样例数据（9 门课）        CourseRepository.createSampleCourses()  :936-967
② 教务系统导出的 .xls 文件            ExcelTimetableReader → TimetableImportParser
③ 用户手工输入                        EditCourseActivity / EditScheduleActivity
```

完整链路（以首页为例）：

```
SharedPreferences("weihe_schedule")
  └─ getString("courses:<semesterId>")
       ↓
     CourseRepository.loadCourses()          :413-430   ← 无值时首次写入 9 门样例课
       ↓
     CourseRepository.readCourses()          :465-491   ← MiniJson 逐条解析，坏数据跳过+备份
       ↓
     applyTimeRange()                        :499-503   ← BellSchedules.resolve(...).timeRange(...)
       ↓  course.timeRange = "08:15 - 09:35"（**每个快照只算一次**，不是每张卡算一次）
     cachedCourses（volatile）                 :38, 391-402
       ↓  getCourses() 返回**深拷贝**
     MainActivity.onResume → reload() → refreshAll()   :88-102
       ↓
     renderCourses()  →  CourseQuery.onDay(...)         :1043
                      →  CourseConflicts.conflictingIds(...)  :1054
       ↓
     createCourseCard()  →  UiKit.styleAsCard / courseRail / courseTint / courseInk
       ↓
     courseContainer(LinearLayout) → ScrollView → 屏幕
```

**写入链路**：表单 → `CourseFormValidator.check` → `CourseConflicts.findConflicts` → `upsertCourse`
→ `CourseList.ensureId`/`upsert`（**存的是 copy**）→ `MiniJson.write` → `preferences.edit().apply()`
→ `cachedCourses = null` → 返回后调用方 `onResume` 重读。

**数据流评价**：
- ✅ **单向、可追踪、无隐式全局状态**。每个类只调下层，没有回环。
- ✅ **时间解析只做一次**（`applyTimeRange` 在构建快照时），避免了 N 张卡片 N 次解析——这个优化是显式写在注释里的。
- ✅ **测试缝设计**（`useStoreForNextInstance` + `ThreadLocal`）让存储层能在 JVM 上跑真实构造函数，是干净的做法。
- ⚠️ **所有 SharedPreferences 读写都在 UI 线程**。`reload()` 只清缓存，真正的重读发生在 `render()`/`refreshAll()` 里
  （`WeekGridActivity:426`、`MainActivity:1043`）。数据量小（23 门课）时无感，
  但导入 200+ 门课后 `MiniJson` 解析 + `applyTimeRange`（每门课一次 `resolve`）会在主线程可见地卡顿。
- ⚠️ **没有 ViewModel / 没有单一数据源**。7 个 Activity 各自 `new CourseRepository(this)`，
  靠"每次 onResume 手动 reload"维持一致。这是 `ARCHITECTURE.md:235-244` 记录的方案，
  能用但脆弱：**任何忘记调 `reload()` 的新页面都会静默显示旧数据**。
- ⚠️ **`EditScheduleActivity` 完全没有 `onResume`**，而它是**按 index 写入**的（见 M12）。

---

## 八、数据库和本地存储

### 8.1 存储方案：只有 `SharedPreferences`

| 方案 | 是否使用 | 证据 |
|---|---|---|
| SQLite | ❌ | 无 `SQLiteOpenHelper`、无 `android.database` |
| Room | ❌ | 无依赖、无注解 |
| DataStore | ❌ | 无依赖 |
| **SharedPreferences** | ✅ 唯一 | `CourseRepository.java:15, 88-90` |
| JSON 文件 | ❌ | `MiniJson` 的结果存进 SharedPreferences 的 String |
| Gson / org.json | ❌ | 手写 `MiniJson`（453 行），因为 `org.json` 在 JVM 上是 null 桩 |
| Retrofit / OkHttp | ❌ | 无网络层 |

### 8.2 存了什么、存在哪、谁读写

| 键 | 内容 | 归属 | 写 | 读 |
|---|---|---|---|---|
| `semesters` | 学期数组 JSON | 全局 | `saveSemesters:341` | `getSemesters:105` |
| `active_semester` | long | 全局 | `setActiveSemester:143` | `getActiveSemester:123` |
| `courses:<semesterId>` | 课程数组 JSON | **按学期** | `saveCourses:830, 841` | `loadCourses:414` |
| `semester_start:<semesterId>` | `yyyy-MM-dd` | **按学期** | `updateSemester:230` | `getSemesterStart:791` |
| `bell_schedules` | 作息表数组 JSON | **全局**（不按学期） | `saveBellSchedules:697` | `getBellSchedules:579` |
| `courses_backup` | 损坏载荷备份 | 全局 | `backupDamaged:506` | 无代码读取 |
| `courses` / `semester_start` | 旧版遗留键 | — | 迁移后 `remove` | `migrateLegacyStorage:282` |

设备实测（`adb shell run-as ... cat .../weihe_schedule.xml`）：23 门课、2 个学期、3 张作息表，与代码一致。

### 8.3 生命周期与数据丢失风险

| 风险 | 严重度 | 代码依据 |
|---|---|---|
| **删掉最后一张作息表 → 仓库静默重建 presets** | **高** | `EditScheduleActivity:327-344` 无 `size()<=1` 保护；`CourseRepository:581-585` 空列表时 `BellSchedules.presets()` 重新写入。用户"删除"的作息表下次读又回来了 |
| **卸载即全部丢失** | 中 | 只有 SharedPreferences，`allowBackup="true"` 但无自建导出/导入备份；设置页自己写了这句提示（`SettingsActivity:255-256`）。Android 自动备份是否含 SharedPreferences **待确认** |
| **无 `onSaveInstanceState`（全仓库 0 处）** | 中 | 编辑课程页 30 个字段的表单在进程被杀后全部丢失；`EditScheduleActivity` 的 15 节 × 2 个时间输入同理 |
| **`getBellSchedules()` 返回缓存实例** | 中 | `CourseRepository:577` 直接 `return snapshot`（对比 `getCourses():396` 返回 `copyOf`）。调用方若原地修改会污染缓存——当前两个编辑页都用 `BellSchedules.copyOf` 兜住了，但契约本身不一致 |
| **坏数据有备份，但备份从没被读过** | 低 | `backupDamaged:506` 写入 `courses_backup`，全仓库无任何代码读取它，用户也无界面可恢复 |
| **`replacePlaceholderSchedules` 一次性迁移无测试** | 中 | `CourseRepository:606-694`，按「名字 + 时间」双重匹配以免误删用户编辑。测试套件对该路径**零覆盖**（见 §十二） |

---

## 九、UI 架构分析

### 9.1 公共 UI 组件（`UiKit`，1432 行，78 个 public static 方法）

| 分组 | 方法 |
|---|---|
| 颜色派生 | `courseTint` / `courseBorder` / `courseInk` / `courseRail` |
| 形状 | `elevate` / `fill` / `outline` / `setRoundedBackground` |
| 表面 | `card` / `bareCard` / `sheet` / `styleAsCard` |
| 文字 | `text`×2 / `title` / `body` / `caption` / `placeholder` / `sectionLabel` |
| 按钮 | `filledButton` / `tonalButton` / `outlinedButton` / `textButton` / `primaryAction` / `actionBar` |
| 图标按钮 | `fab` / `fabTonal` / `headerIcon` / `iconButton` / `backButton` |
| 标签 | `chip` / `styleChip` / `badge` / `pill` |
| 结构 | `column` / `stack` / `weighted` / `divider` / `emptyState`×2 |
| 页头 | `appBar` / `appBarTitle` / `appBarSubtitle` / `appBarRow` / `detailAppBar` / `appBarSubtitleOf` |
| 反馈 | `ripple` / `pressable` / `pressScale` / `PressFeedback` |
| 动效 | `enter` / `fadeIn` / `swapIn` |
| 弹层 | `bottomSheet` / `dialog` / `message` / `Action` / `Success` |
| 表单 | `field` / `checkbox` / `Check` / `radioRow` / `Choice` |
| 工具 | `dp` / `space` / `inlineIcon` |

**设计系统三件套**：`Palette`（192 行，颜色角色 + 9 个课程色 + 4 个混合系数）、
`UiTokens`（140 行，间距 6 档 / 圆角 6 档 / 阴影 4 档 / 字阶 9 档 / 控件尺寸）、
`Colors`（77 行，WCAG 对比度算术）。

### 9.2 哪些页面共享组件

`UiKit.detailAppBar` 被 6 个子页共用 · `UiKit.card` 被设置页的 5 张卡共用 ·
`UiKit.styleAsCard` 被首页卡片/周卡片/日期条共用 · `UiKit.dialog` 被 4 处确认框共用 ·
`UiKit.pill`/`badge` 被两处共用 · `UiKit.fab` 被 4 处共用。

### 9.3 样式重复与硬编码（实际测量）

| 文件 | `dp(<字面量>)` 次数 | 裸 sp 字号 | 说明 |
|---|---|---|---|
| `EditCourseActivity` | **73** | 6 | 最严重 |
| `EditScheduleActivity` | 29 | 5 | |
| `WeekGridActivity` | 24 | 2 | 同时混用圆角字面量（`2`/`4`/`8`）与 `UiTokens.RADIUS_*` |
| `ScheduleListActivity` | 14 | 3 | 卡片内边距 15/13/15/14，与 `UiKit.card` 的 16 全向不一致 |
| `MainActivity` | 10 | 0 | 已基本 token 化 |
| `SettingsActivity` | 10 | 0 | |
| `SemesterListActivity` | **0** | 0 | **唯一完全 token 化的页面** |

**颜色硬编码**：全仓库**没有一处裸 `0xRRGGBB`** 出现在 `Palette`/`UiKit`/`Colors`/`CourseRepository` 之外，
只用了框架的 `Color.TRANSPARENT` 作为"无描边/无填充"哨兵值（12 处）。**这一点做得很干净。**

**未统一管理的具体项**（有代码依据）：
1. **卡片内边距 5 种**：14/13/14/14（`EditCourseActivity:168`）、15/14/15/15（`EditScheduleActivity:117`）、
   16 全向（`UiKit.card`）、16/14/16/14（`SemesterListActivity:132`）、15/13/15/14（`ScheduleListActivity:142`）。
2. **卡片构建 3 种写法**：手写 `UiKit.outline` 无阴影（两个编辑页）· 同上 + `setElevation(dp(1))`（`ScheduleListActivity:145`）·
   `UiKit.card()`（设置页）· `UiKit.styleAsCard` + 条件覆盖（学期页）。
3. **输入框 3 种外观**：`UiKit.field`（描边，minHeight 52，设置页/学期页用）·
   `EditCourseActivity.input`（填充**无描边**，minHeight 46）· `EditScheduleActivity.timeInput`（描边，RADIUS_S，padding 6）。
4. **同一个"列表摘要行"两种字号**：`SemesterListActivity:78` 用 `UiKit.caption`(11sp)，
   `ScheduleListActivity:80` 用 `LABEL_LARGE`(12sp)。
5. **FAB 留白两种值**：`SemesterListActivity:76` 用 `FAB_SIZE + SPACE_XXL`(88dp)，
   `ScheduleListActivity:78` 硬编码 `dp(96)`。
6. **"主操作"按钮两种高度**：`UiKit.primaryAction` 48dp（两个编辑页）vs 设置页 `actionRow` 强制的 40dp（`SettingsActivity:271`）。
7. **同一个新建/删除操作两套实现**：`SettingsActivity` 的 137 行导入代码与 `MainActivity:1161-1295` 逐行重复（后者是死代码）。
8. **`private int dp(float)` 在 5 个 Activity 里各复制一份**（`EditCourse:792`、`EditSchedule:431`、`Settings:508`、`SemesterList:361`、`ScheduleList:248`）。
9. **`UiKit.badge` 被 `ScheduleListActivity:208-211` 重新实现了一遍**（参数、结果完全相同）。
10. **`UiKit.chip`/`styleChip` 一个页面都没用**——`EditCourseActivity:342-348, 396-403` 手写了另一套选中态
    （`PRIMARY`/`ON_PRIMARY` + `RADIUS_S`），与 `UiKit.styleChip` 的 `PRIMARY_CONTAINER`/`RADIUS_FULL` 视觉不一致。

### 9.4 UI 架构评价

- ✅ **设计系统覆盖率高**：颜色 100% 走 `Palette`，圆角/间距/字阶在主要页面已 token 化，
  `PaletteContrastTest` 用算术钉住了每一对文字/背景的可读性——**这是很多成熟项目都没做到的**。
- ✅ **手写 Material 3 的完整度高**：卡片、芯片、对话框、底部弹层、FAB、ripple、按压缩放、错落入场、页面转场都有，
  且每个组件的"为什么这样写"都留了注释（如 `PressFeedback` 为什么不用 `OnTouchListener`）。
- ⚠️ **`UiKit` 1432 行、78 个 public 方法，本身已经是一个小型框架**，却零测试、零预览。
  加一个组件要读 1400 行才能确认没有重复。
- ⚠️ **程序化 UI 的固有代价**：没有布局预览、没有 `@Preview`、改一个间距必须编译装机看效果。
  本项目用 `tools/dump-ui.ps1`（uiautomator dump + bounds 比对）来补，但那是运行时手段。
- ⚠️ **动画未做无障碍适配**：没有检查 `Settings.Global.ANIMATOR_DURATION_SCALE`，
  关闭系统动画的用户仍会看到 `UiKit.enter` 的 220ms 错落入场。

---

## 十、导航结构

关系图与完整跳转清单见 `PROJECT_ARCHITECTURE.md` §七。要点评价：

- ✅ **返回栈天然扁平**：只有 `MainActivity` 是 LAUNCHER，其余都在它之上，`finish()` 即返回；
  没有 `onBackPressed` 重写（全仓库 0 处），没有 Fragment 返回栈陷阱，没有 Deep Link 冲突。
- ⚠️ **11 处 `startActivity` 各自 new Intent**，没有路由表。extra key 是 `public static final String` 常量
  （`EditCourseActivity.EXTRA_COURSE_ID` 等），比裸字符串好，但仍是**编译期不检查的约定**。
- ⚠️ **结果回传大部分是死代码**：`setResult` 被调用 4 次，只有 1 次真正被接收
  （详见 `PROJECT_ARCHITECTURE.md` §7.6）。`SemesterListActivity.RESULT_SWITCHED` 是**完全死的契约**。
- ⚠️ **`MainActivity` 有一个永远收不到结果的 `startActivityForResult`**（`:1169`），
  因为该类没有 `onActivityResult`，而且 `openImportPicker()` 没有任何调用点（见 M1）。
- ⚠️ **返回后是否刷新不一致**：3 个可返回的列表页 `onResume` 里 `reload()`；
  两个编辑页没有 `onResume`（合理）；但 `EditScheduleActivity` 按 index 写入，不刷新就成了正确性问题。

---

## 十一、上课时间逻辑（重点核对）

### 11.1 结论：**已完整实现，且与需求给出的学校规则逐节完全一致**

三张表硬编码在 `BellSchedules.java:60-96`，单位是「当日 0 点起的分钟数」。

#### A、F 楼及 J301 室 —— `BellSchedules.standard()`，表名「教学楼作息」

`starts` = `{495, 535, 595, 640, 680, 800, 840, 900, 940, 995, 1035, 1090, 1130, 1175, 1220}`
`ends` = `{535, 575, 635, 680, 720, 840, 880, 940, 980, 1035, 1075, 1130, 1170, 1215, 1260}`

| 节 | 代码 start→end（分钟） | 换算 | 需求给定 | 一致 |
|---|---|---|---|---|
| 1 | 495 → 535 | 08:15 – 08:55 | 第1、2节 08:15-09:35 | ✅ |
| 2 | 535 → 575 | 08:55 – 09:35 | ↑（合并为一块） | ✅ |
| 3 | 595 → 635 | **09:55 – 10:35** | 第3节 09:55-10:35 | ✅ |
| 4 | 640 → 680 | **10:40 – 11:20** | 第4、5节 10:40-12:00 | ✅ |
| 5 | 680 → 720 | **11:20 – 12:00** | ↑ | ✅ |
| 6 | 800 → 840 | 13:20 – 14:00 | 第6、7节 13:20-14:40 | ✅ |
| 7 | 840 → 880 | 14:00 – 14:40 | ↑ | ✅ |
| 8 | 900 → 940 | 15:00 – 15:40 | 第8、9节 15:00-16:20 | ✅ |
| 9 | 940 → 980 | 15:40 – 16:20 | ↑ | ✅ |
| 10 | 995 → 1035 | 16:35 – 17:15 | 第10、11节 16:35-17:55 | ✅ |
| 11 | 1035 → 1075 | 17:15 – 17:55 | ↑ | ✅ |
| 12 | 1090 → 1130 | 18:10 – 18:50 | 第12、13节 18:10-19:30 | ✅ |
| 13 | 1130 → 1170 | 18:50 – 19:30 | ↑ | ✅ |
| 14 | 1175 → 1215 | 19:35 – 20:15 | 第14节 19:35-20:15 | ✅ |
| 15 | 1220 → 1260 | 20:20 – 21:00 | 第15节 20:20-21:00 | ✅ |

关键词：`a楼`、`f楼`、`j301`（`BellSchedules.java:66`）。

#### B、C 楼、J302 室及其他教学场所 —— `BellSchedules.bcBuilding()`，表名「B/C 楼作息」

| 节 | 换算 | 需求给定 | 一致 |
|---|---|---|---|
| 1–2 | 08:15–08:55 / 08:55–09:35 | 08:15-09:35 | ✅ |
| 3 | **09:55 – 10:35** | 第3、4节 09:55-11:15 | ✅ |
| 4 | **10:35 – 11:15** | ↑ | ✅ |
| 5 | **11:20 – 12:00** | 第5节 11:20-12:00 | ✅ |
| 6–15 | 与标准表**逐节相同** | 与需求相同 | ✅ |

关键词：`b楼`、`c楼`、`j302`（`:80`）。

#### D、E 楼及 J303 室 —— `BellSchedules.deBuilding()`，表名「D/E 楼作息」

| 节 | 换算 | 需求给定 | 一致 |
|---|---|---|---|
| 1–2 | 08:15–09:35 | 08:15-09:35 | ✅ |
| 3 | **10:15 – 10:55** | 第3、4节 10:15-11:35 | ✅ |
| 4 | **10:55 – 11:35** | ↑ | ✅ |
| 5 | **11:40 – 12:20** | 第5节 11:40-12:20 | ✅ |
| 6–15 | 与标准表**逐节相同** | 与需求相同 | ✅ |

关键词：`d楼`、`e楼`、`j303`（`:95`）。

### 11.2 关于"块"与"单节"的推导（需求给的是块）

需求给的是**块**（如「第4、5节：10:40-12:00」），代码存的是**单节**。拆分规则是
"一节 40 分钟，同一块内两节相邻，块与块之间的间隔由表格本身给定"：

- 标准表第 4、5 节：10:40 + 40 = 11:20 = 第 5 节开始 ✅，11:20 + 40 = 12:00 = 块结束 ✅
- B/C 表第 3、4 节：09:55 + 40 = 10:35 = 第 4 节开始 ✅，10:35 + 40 = 11:15 = 块结束 ✅
- D/E 表第 3、4 节：10:15 + 40 = 10:55 ✅，10:55 + 40 = 11:35 ✅

**每一个多节块的内部边界都能被表格自身的块结束时间反推验证，因此"40 分钟一节"不是猜测，是被表格证实的事实。**
（`BellSchedules.PERIOD_MINUTES = 40` 常量就写在那里，`:45`。）

### 11.3 解析顺序（`BellSchedules.resolve`，`:176-192`）

```
① 课程显式指定的作息名（scheduleName 非空且该名仍存在）
② 按地点关键词匹配（forLocation → BellSchedule.matchesLocation）
③ 兜底到「B/C 楼作息」= 需求里的"其他教学场所"
④ 再兜底到「教学楼作息」
⑤ 最后兜底到内置 standard()
```

第 ③ 步做成了代码里的规则而不是关键词，理由写在 `:164-171`：做成关键词就必须排在最后，而排在最后永远不会赢。

### 11.4 ⚠️ 这里有一个真实缺陷（S3）

**匹配算法**（`BellSchedule.matchesLocation:97-128`）把地点和关键词**双方**都做 `squeeze`
（删除所有空白、`-`、`·`、`_`，转小写），然后 `haystack.contains(needle)`。

因此关键词 `d楼` 只能匹配**字面上含 `d楼` 相邻两字**的地点串：

| 地点写法 | squeeze 结果 | 是否命中 `d楼` | 实际使用的作息 | 应该是 |
|---|---|---|---|---|
| `教学楼D楼D305` | `教学楼d楼d305` | ✅ | D/E 楼作息 | D/E ✅ |
| `教学楼 D305` | `教学楼d305` | ❌ | **B/C 楼作息** | D/E ❌ |
| `D305` | `d305` | ❌ | **B/C 楼作息** | D/E ❌ |
| `四教D305` | `四教d305` | ❌ | **B/C 楼作息** | D/E ❌ |

**后果**（第 3–5 节，D/E 教室被错判为 B/C）：

| 节 | 显示（B/C） | 实际（D/E） | 误差 |
|---|---|---|---|
| 3 | 09:55 – 10:35 | 10:15 – 10:55 | **早 20 分钟** |
| 4 | 10:35 – 11:15 | 10:55 – 11:35 | **早 20 分钟** |
| 5 | 11:20 – 12:00 | 11:40 – 12:20 | **早 20 分钟** |

A/F 教室被错判为 B/C 时只有第 4 节差 5 分钟（10:35 vs 10:40）。

**这不是理论问题，有三条代码证据**：
1. **App 自己的示例课程就用不匹配的写法**：`CourseRepository.createSampleCourses():939-965` 里
   `教学楼 A203`、`教学楼 F305`、`教学楼 D102`、`教学楼 E302`、`教学楼 A201`、`教学楼 E402`
   全部不含 `a楼`/`f楼`/`d楼`/`e楼`，**9 门样例课里只有 `J301 室` 一门能正确解析**，其余全部落到 B/C 兜底。
2. **App 自己的验收清单也用不匹配的写法**：`ARCHITECTURE.md` 上机回归第 6 条写
   "新建一门「教学楼 D305」的课：作息选「自动」，时间应按 D/E 楼作息显示"——按当前代码，
   它**不会**显示 D/E，这条验收项今天是失败的。
3. **设备实测佐证**（上一轮任务中的截图）：首页「大学物理 / 教学楼 A201」的周次标签显示
   `第1-16周 · 双周 · B/C 楼作息`。

**设备上 23 门真实课程的情况**：地点为 `教学楼F楼F106多`、`教学楼E楼E107多` 等，
**含 `f楼`/`e楼`，能正确匹配**；`现代工业训练中心训2307`、`现代交通工程中心交7509` 不匹配任何关键词，
落到 B/C——按需求「其他教学场所」属于 B/C 组，**这是正确的**。

所以缺陷的准确定义是：**匹配规则只认"字母+楼"紧邻的写法，而 App 自己的文档、示例和表单提示用的是
"教学楼 X###" 写法；后者静默得到 B/C 的时间。** 用户手输 `D305` 或 `教学楼 D305` 时会看到错误时间，
且界面上没有任何提示说明"这条按 B/C 楼的钟走"——编辑页的作息预览会显示解析结果（`EditCourseActivity:560-563`），
但用户得先知道正确时间才能发现它不对。

---

## 十二、测试分析

数据由子代理逐文件通读统计，与 `ARCHITECTURE.md:84-91` 的记录一致。

### 12.1 规模

| 项 | 数值 |
|---|---|
| 测试文件 | **18**（17 个测试类 + 1 个测试替身 `FakePreferences`） |
| 源文件行数 | 3,533 行 |
| `@Test` 方法 | **221** |
| 断言调用 | 678（平均每测试 3.1 条） |
| 覆盖的生产类 | 31 个中 **21 个**有直接或间接覆盖，**10 个零覆盖** |

### 12.2 覆盖分布

**直接、接近完整覆盖（12 个类）**：`BellSchedule`(17) · `BellScheduleEditor`(17) ·
`BellScheduleJson`(11) · `BellSchedules`(14) · `CourseConflicts`(13) · `CourseFormValidator`(15) ·
`CourseList`(12) · `CourseQuery`(15) · `MiniJson`(9) · `Semester`(10) · `SemesterJson`(4) · `WeekGridPlan`(16)。

**直接但部分覆盖（6 个类）**：`CourseRepository`(25，但 `deleteCourse` / `backupDamaged` /
`replacePlaceholderSchedules` 全未测) · `ScheduleUtils`(19，16 个 public 方法中 6 个从未被测试引用) ·
`Palette`(14) · `UiTokens`(仅 `Type.*` 与 `SPACE_*`) · `Colors`(仅 4 个成员) · `TimetableImportParser`(7)。

**零覆盖（10 个类，约 5,750 行）**：
`MainActivity`(1324) · `UiKit`(1432) · `WeekGridActivity`(907) · `SettingsActivity`(511) ·
`EditCourseActivity`(795) · `EditScheduleActivity`(434) · `SemesterListActivity`(364) ·
`ScheduleListActivity`(251) · `Insets`(74) · `ExcelTimetableReader`(54)。

### 12.3 测试基础设施

| 项 | 情况 |
|---|---|
| JUnit | **4.13.2**（唯一测试依赖） |
| Mockito / MockK / EasyMock / PowerMock | **全无**（全仓库搜索 0 命中） |
| Robolectric | **无** |
| Espresso / Compose UI Test / androidx.test | **无** |
| UI Automator（测试框架） | **无**。`tools/dump-ui.ps1` / `tap-ui.ps1` 是 adb 脚本，未接入任何 Gradle 任务，属人工核对 |
| `androidTest` 源集 | **不存在** |
| 测试替身 | 仅 `FakePreferences`（186 行，替 `SharedPreferences` + `Editor`） |
| `@Before`/`@After`/`@Rule`/`@RunWith` | **全仓库 0 处**，每个测试自建夹具 |
| `@Ignore`/`assumeTrue` | **0 处**，没有跳过的测试 |

**`unitTests.returnDefaultValues = false` 的意义**：它的值其实是 AGP 默认值，功能上不改变任何东西；
写作明示是**架构声明**——"框架无关层绝不能碰 Android 类，stub 静默返回 null 正是这条设置要遮住的失败"。
它的直接后果是：任何触碰未打桩 `android.jar` 方法的测试会**大声失败**而不是静默拿到 0/null。
这也是 `MiniJson` 必须手写（`org.json` 在 JVM 上是 null 桩）、`Colors` 必须存在、
`CourseQuery`/`CourseConflicts`/`CourseList`/`CourseFormValidator`/`WeekGridPlan` 必须从 Activity 里
抽出来的根本原因。**这是一条约束反过来塑造了架构的正面案例。**

### 12.4 测试风格（3 个代表）

- `WeekGridPlanTest`：断言的是**结构性质而非期望值表**——"每门课恰好落在一格"用 `HashSet.add` 返回 false 判重，
  "同车道不重叠"做 O(n²) 两两检查。这是最抗重构的写法。
- `CourseRepositoryTest`：用 `useStoreForNextInstance` 缝 + **重新构造第二个 repository 读同一个 store** 来验往返，
  按**名字**而非下标查作息表，避免绑死 `presets()` 顺序。
- `ScheduleUtilsTest`：把越界钳制写成 `assertEquals(f(范围内), f(范围外))` 的**幂等性断言**，
  不硬编码日期、不内联 `SEMESTER_WEEKS`。

### 12.5 明显的测试空白（按风险排序）

| 风险 | 空白 | 后果 |
|---|---|---|
| **高** | `WeekGridActivity.render()` 里「先 `removeAllViews()` 再 `setRowCount()`」的顺序 | 这个顺序是 `ARCHITECTURE.md:169-174` 记录的**必崩 bug** 的修复。**把两行换回来，221 个测试全绿**——`WeekGridRowsTest` 只镜像了行数算术，且把 `MIN_ROWS = 10` 手抄了一份 |
| **高** | `ExcelTimetableReader.read` 完全无覆盖 | 其中 `grid.put(row, column, sheet.getCell(column, row)...)` 是 jxl(列,行)→网格(行,列) 的转置；**改错方向，整张课表旋转 90° 且静默** |
| **高** | `TimetableImportParser.findHeaderRow` 从未被真正执行 | 测试夹具 `grid()` 把表头预先摆在 0/1 行，恰好**预先满足了被测代码要自己去发现的东西** |
| 中 | `CourseRepository.deleteCourse` / `backupDamaged` / `replacePlaceholderSchedules` | 唯一的破坏性操作无测试；防数据丢失的备份路径无测试（`CourseRepositoryTest:40` 声明了 `KEY_BACKUP` 常量却从未使用——**这本身就是缺失测试的物证**） |
| 中 | `ScheduleUtils.parseDate` / `weekRange` / `weekPickerColumns` | `parseDate` 是**存储日期的读取路径**，回归会静默丢失开学日期 |
| 中 | `SettingsActivity` 的导入入口 | 文件选择器 → 解析 → 覆盖存储这条链路无任何测试 |
| 中 | `EditCourseActivity` 的 表单 ↔ `Course` 双向映射 | `CourseFormValidator` 本身测得很细，但**哪个问题挂到哪个字段**没有任何测试 |
| 低 | `UiKit`（78 个 public 方法）、`Insets`（含 API 26/27 分支）、`UiTokens` 的 `RADIUS_*`/`TOUCH_TARGET` | 设计系统自身无验证；`theSpacingScaleIsMultiplesOfTheBaseUnit` 把每个值设成 0 也能通过 |

### 12.6 测试质量评价

- ✅ **没有一条空断言**（`assertTrue(true)`、`assertEquals(1,1)`、裸 `fail()` 全仓库 0 处），
  没有跳过的测试，没有 `@Before` 带来的测试间隐式状态。
- ✅ 失败信息带数据（课程名、颜色十六进制、实测对比度），排障成本低。
- ⚠️ **重复夹具**：`course(id, day, fromPeriod, toPeriod, fromWeek, toWeek, parity)` 这个构造器在
  `CourseConflictsTest:22`、`CourseQueryTest:21`、`ScheduleUtilsTest:28` 各抄了一份；没有共享 fixtures 类。
- ⚠️ **`WeekGridRowsTest` 测的是自己的副本**，改 `WeekGridActivity.MIN_ROWS` 测试不会红。
- ⚠️ **日期依赖是传递性的**：没有测试直接调 `LocalDate.now()`，但 `Semester` 构造函数和
  `CourseRepository.defaultSemesterStart` 会调，所以每个 `repositoryFor(new FakePreferences())` 的
  学期起点都来自"今天"。当前断言自洽不会 flaky，但**不 hermetic**。
- ⚠️ **`FakePreferences` 与真实存储行为有差异**：类型不匹配时它返回默认值，真实 `SharedPreferences` 抛 `ClassCastException`；
  `apply()` 同步落盘（真实是异步）；变更监听是空实现。**把 int 写成 String 再读的 bug 在这里能通过、在设备上会崩。**
- ⚠️ **`ThreadLocal` 测试缝在并行 runner 下不安全**（当前 JUnit 4 串行，安全）。

---

## 十三、代码质量分析

### 13.1 严重问题（P0）

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| **S1** | **作息表重命名不级联到课程** | `EditScheduleActivity:361-369` 保存时只 `saveBellSchedules`，**从不调用** `CourseRepository.renameSchedule`（`:738-755`，全仓库只有 `CourseRepositoryTest:137` 引用它）。`BellScheduleEditor.originalName()`（`:88-90`）拿得到旧名，删除路径用了它（`:340`），重命名路径没用 | 用户把「教学楼作息」改名后，所有显式指定它的课程**静默回落到按地点解析**——可能换到完全不同的一张表。数据语义被破坏，无提示、无日志 |
| **S2** | **作息表编辑器可删掉最后一张表，仓库随即静默重建** | `EditScheduleActivity.confirmDelete:327-344` **没有** `size() <= 1` 保护；而 `ScheduleListActivity:221-225` **有**。`CourseRepository.getBellSchedules:581-585` 在读到空列表时会 `BellSchedules.presets()` 并写回 | 用户"删除"作息表后，下次任何页面读取时它**又回来了**。两个入口对同一意图给出相反结果 |
| **S3** | **地点→作息匹配只认"字母+楼"紧邻写法** | `BellSchedule.matchesLocation:97-128` + `BellSchedules:66,80,95` 的三个字关键词 | 见 §11.4。用户写 `D305` / `教学楼 D305` 时第 3–5 节最多早 20 分钟；**App 自己的样例数据和验收清单用的就是不匹配的写法** |
| **S4** | **总览页日列高度少一行** | `WeekGridActivity.render():489` 用 `rowTop(rows)` 当高度，而全文件其余位置（`:530, :687, :711`）用 `rowSpan(n)` 表示"n 行高"、`rowTop(p)` 表示"上边缘"。同处 `:517-522` 又按 `periods` 个 `rowHeight()` 摆放，内容高度实际是 `rows * rowHeight` | 每列自身的 bounds 切掉最后一行；且网格内容比节次列内容少一行，纵向滚动范围**无法把节次列最后一行完整滚出来**。`:241-244` 的注释说"对网格关掉了 clipChildren"，但代码只在 `headerRow` 上关（`:245-246`），`grid` 从未设置——这正是让 S4 从"无感"变成"可见"的原因 |
| **S5** | **作息表编辑器按 index 写入、却从不刷新缓存** | `EditScheduleActivity` 在 `:45` 取 `index`，`:336`/`:364` 直接拿它去索引；该类**没有 `onResume`**（全类只有 `onCreate`），也无 `reload()`。而 `CourseRepository.getBellSchedules:590` 会缓存 | 若另一页面在本页停留期间改过作息表列表，`index` 可能指向**另一张表**——保存会覆盖错的对象，删除会删错的对象 |

### 13.2 中等问题（P1）

| # | 问题 | 证据 |
|---|---|---|
| **M1** | **`MainActivity` 里有 137 行死导入代码** | `:1159-1295`（`openImportPicker`/`importTimetable`/`confirmImport`/`showImportError`）+ `:42` 的 `REQUEST_IMPORT_TIMETABLE`。`openImportPicker()` **没有任何调用点**，`MainActivity` **没有 `onActivityResult`**，所以 `:1169` 的 `startActivityForResult` 永远收不到结果。同时产生 5 个孤儿 import（`Uri`、`InputStream`、`ArrayAdapter`、`AdapterView`、`HashSet`）。这段与 `SettingsActivity:381-506` 逐行重复 |
| **M2** | **总览页 `currentWeek` 在 `onCreate` 冻结** | `WeekGridActivity:121` 算一次，`onResume:131-142` 只 `reload()` + `render()`，不重算。跨周后 `subtitle`(`:438`)、`styleTodayChip`(`:837`)、周切换钳制(`:412`) 全用旧值，而 `addDayHeaders:565` 用的是新的 `LocalDate.now()` → **同屏两处对"今天"的判断不一致** |
| **M3** | **作息表的节次错误永远标在"开始"字段** | `EditScheduleActivity:417-421` 对任何 `Reason`（含 `INVERTED`、`TIME_INVALID`）都 `setError` 到 `startInputs[period-1]`；`endInputs` 的错误只在 `:403-405` 被清除，**从未被设置**。用户改错"结束"栏却看到"开始"栏标红 |
| **M4** | **任何一次击键清空所有错误** | `EditScheduleActivity.watcher:306` → `clearErrors():392-407` 清掉横幅 + `nameInput` + 全部 30 个字段。用户在修正第 12 节时，第 3 节的错误提示也一起消失。`EditCourseActivity` 的做法相反（`:638-640` 只在保存时清）——同一个 App 两套错误模型 |
| **M5** | **先改内存对象，再问冲突** | `EditCourseActivity.validateAndSave:657` 先 `applyForm(...)` 写进 `course`，`:659-668` 才查冲突并弹框。用户点"返回修改"时，内存里的课程**已经带上新值**。当前无害（`course` 是仓库返回的拷贝），但只要将来有人让 `course` 与界面双向绑定就会出错 |
| **M6** | **颜色选择行可能横向溢出**（**待确认**，未渲染验证） | `EditCourseActivity.renderColorSwatches:590-611`：9 个 30dp 色块 + 每个 8dp 右间距 = 342dp；360dp 屏上卡片可用宽度约 304dp（360 − 页面 16×2 − 卡片 14×2）。`colorRow` 是无换行、无滚动的 `LinearLayout`（`:578-580`） |
| **M7** | **`getBellSchedules()` 返回缓存实例，`getCourses()` 返回深拷贝** | `CourseRepository:577` vs `:396`+`:402`。契约不一致：调用方原地修改作息表列表会污染缓存。当前两个编辑页都用 `BellSchedules.copyOf` 兜住了，但这是**靠调用方自觉** |
| **M8** | **导入线程持有 Activity、无取消** | `SettingsActivity:397` 裸 `new Thread`（无 `ExecutorService`、无取消、无 `WeakReference`），持 `this`；`runOnUiThread` 只判 `isFinishing()`（`:406, :413`）不判 `isDestroyed()`；`progress` 对话框随 Activity 销毁后仍被 `dismiss()`。`MainActivity:1177` 有一份同样的死代码 |
| **M9** | **导入解析器有多条静默丢弃路径** | `TimetableImportParser`：表头前的文本(`:224-226`)、`DETAIL` 正则要求**两个及以上 ASCII 空格或 Tab**(`:16-19, :235`——单个空格或全角空格 `U+3000` 会让整行课被丢掉)、周次不可解析/越界(`:294-296, :302-304`)。全部无计数、无提示；最终只由 UI 报"没有识别到课程"(`SettingsActivity:422-425`)，**全丢和空文件无法区分** |
| **M10** | **`periodForRow` 兜底到最后一行会虚高节次** | `TimetableImportParser:127-134`：合并区底行若不在节次行列表里，返回**最后一行**的节次而不是相近值；反过来，锚点行不在节次行列表里的合并区会被 `:56-58` **整块跳过**，文本永不读取 |
| **M11** | **"后移一周"没有上界保护** | `SettingsActivity.shiftSemesterStart:334-342` 只拦 `weeks < 0 && currentWeek <= 1`。方法注释（`:327-333`）声称"会把学期起点移到今天之后时拒绝"，但代码没实现。把第 1 周推到未来后，`ScheduleUtils.currentWeek` 钳到 1（`:30`），于是"前移一周"永远回答"已经是第 1 周了"，**用户在这个页面无法再把日期挪回来** |
| **M12** | **两个页面用数组下标定位作息表** | `EditScheduleActivity:336,364` 与 `ScheduleListActivity:238-240` 都用 `index` 索引一个可能过期的列表；`saveBellSchedules` 会把整个列表覆盖写回。列表在中途变化 → 改错/删错对象。应当按**名字**定位 |
| **M13** | **`ScheduleListActivity.onActivityResult` 用陈旧缓存渲染** | 生命周期顺序是 `onActivityResult` → `onResume`，而 `reload()` 在 `onResume:49`。`:57` 那次 `render()` 画的是编辑前的列表，正确的列表要等下一帧。结果正确但会闪一次旧数据 |
| **M14** | **`EditCourseActivity` 的 `setResult` 与 `SemesterListActivity.RESULT_SWITCHED` 是死契约** | `EditCourseActivity:773,787` 与 `SemesterListActivity:202,295` 都设了结果，但 4 个调用点（`MainActivity:1304`、`WeekGridActivity:318`、`MainActivity:743`、`SettingsActivity:210`）**全部用 `startActivity`**。删除学期路径（`SemesterListActivity:351-358`）甚至完全不设结果 |
| **M15** | **`EditScheduleActivity` 的 `TYPE_CLASS_DATETIME` 与自己的说明矛盾** | `:281` 用 `TYPE_CLASS_DATETIME`，而 `:181` 的脚注和 `BellSchedule.parseLoose:210-232` 都支持 `8.00` / `800` / 全角冒号。日期时间类输入法未必提供这些分隔符 |
| **M16** | **无 `onSaveInstanceState`（全仓库 0 处）** | `EditCourseActivity`（30 个字段）、`EditScheduleActivity`（30 个时间框）、学期/导入对话框的输入，在进程被杀或分屏尺寸变化后全部丢失。竖屏锁定只挡住了旋转这一条路径 |

### 13.3 轻微问题（P2）

| # | 问题 | 证据 |
|---|---|---|
| L1 | 死代码 | `WeekGridActivity.tableHeader:625-643`（无调用者）、`cellParams:807-810`（全部调用点用 `cellParamsPx`） |
| L2 | 悬空 javadoc 引用 | `WeekGridActivity:29` 的 `{@link WeekGridLayout}` 指向**不存在的类**（真名是 `WeekGridPlan`）；`app/build.gradle:82` 的注释同样写错。`javac` 不报错，`javadoc` 会失败 |
| L3 | 已废弃别名仍在用 | `Palette.CARD`（`WeekGridActivity:668,832`）、`Palette.INK`（`SettingsActivity:223,225`——而且那个 `color` 参数在 `action():286-292` 里**被完全忽略**） |
| L4 | 硬编码字面量 | 见 §9.3 的实测表：`EditCourseActivity` 73 处 `dp(<字面量>)`，`WeekGridActivity` 混用圆角字面量 `2/4/8` 与 `UiTokens.RADIUS_*` |
| L5 | `private int dp(float)` 在 5 个 Activity 各复制一份 | `EditCourse:792`、`EditSchedule:431`、`Settings:508`、`SemesterList:361`、`ScheduleList:248` |
| L6 | 同类对象两套写法 | `ScheduleListActivity.badge:208-211` 重写了 `UiKit.badge`；`EditCourseActivity` 手写 chip（`:342-348, :396-403`）而 `UiKit.chip/styleChip` 无人使用 |
| L7 | 版本号硬编码进 UI | `SettingsActivity:324` 的 `"WeiheSchedule 1.0 · minSdk 26"` 与 `app/build.gradle` 的 `versionName` 无关联，改版本会忘 |
| L8 | 无用 import | `EditCourseActivity`：`ImageButton`、`RadioButton`、`RadioGroup`；`EditScheduleActivity`：`ImageButton`；`SettingsActivity`：`AlertDialog`、`ImageButton`、`Color`；`SemesterListActivity`：`Color` |
| L9 | 死常量 | `CourseRepositoryTest:40-41` 的 `KEY_BACKUP`、`KEY_SCHEDULES` 声明后从未使用 |
| L10 | 格式化瑕疵 | `UiKit.java:198` 的 `title()` 一行里塞了两句（`{        return text(...)`）；`EditScheduleActivity:430-431` 缺空行 |
| L11 | 测试夹具重复 | 同一个 `course(...)` 构造器在 3 个测试文件里各抄一份；2 组 15 元素 `int[]` 字面量在 2 个测试文件里内联 |
| L12 | `CourseQuery.maxParallel` 是 O(n²) | `CourseQuery:103-116` 对每门课重扫全表。23 门课无感，但同一模式在 `CourseConflicts.conflictingIds`（`:69-82`）也在用 |

### 13.4 线程、生命周期、内存

| 项 | 结论 |
|---|---|
| 线程 | **只有 2 处后台线程**，都在导入（`SettingsActivity:397` 活、`MainActivity:1177` 死）。无 `Handler`/`ExecutorService`/`AsyncTask`/协程。无共享可变静态状态（唯一 `ThreadLocal` 是测试缝） |
| 并发风险 | 无数据竞争——所有 UI 与存储操作都在主线程。导入线程只碰局部变量和不可变的解析结果 |
| 内存泄漏 | `SettingsActivity:397` 的线程持 Activity 引用直到解析完成；无静态引用泄漏，无未注销的监听器（`Activity` 不是 `Context` 泄漏的常见来源） |
| 生命周期 | **零 `onSaveInstanceState`**；`EditScheduleActivity` 缺 `onResume` 刷新（S5）；`WeekGridActivity` 的 `currentWeek` 冻结（M2） |
| 空指针 | 主要风险已由模型默认值兜住（`Course` 的字符串字段默认 `""`，`fromJson` 总给 String）。明确缺口：`TimetableImportParser.parse(null)`（`:26-28` 无 null 保护）、`WeekGridPlan.plan` 对列表内 null 元素（`:262, :307`）、`SemesterListActivity:216` 的 `semesters.get(size-1)` 依赖仓库非空不变式 |
| 异常处理 | 存储层是**宽容解析**（坏条目跳过 + 备份），导入层是**大写异常 + 弹框**。两条策略都合理且都写了理由。缺口：`TimetableGrid.merge` 不校验矩形方向（`:45-56`），安全性目前只靠 `ExcelTimetableReader:43-45` 的预过滤 |
| 数据一致性 | 见 §八。最薄弱的是**作息表按 index 写入 + 不刷新缓存**（S5）与**重命名不级联**（S1） |

---

## 十四、UI/UX 问题分析

### 14.1 做得好的（有代码依据）

- **颜色系统完整且可验证**：`Palette` 定义全部角色，`PaletteContrastTest` 用 WCAG 算术钉住每一对文字/背景
  （正文 ≥ 4.5:1，大字/粗体 ≥ 3:1），并且对**非文字边界做双向断言**（分隔线 1.08–2.0，课程色描边 1.4–2.5）——
  单向断言会让分隔线变成黑线，双向断言不会。
- **信息层级清晰**：首页把"标题 → 学期 → 周 → 日期 → 当天课程"按重要性从上到下排，
  课程卡内"课名最大 → 时间块 → 地点/教师次级 → 周次小标签"。
- **三态日期条**做得很干净：选中=品牌色实心块、今天=橙色描边块、其余=无底色，
  且星期小、数字大（`MainActivity.renderDayStrip`）。
- **空状态有设计**：首页无课时显示柔和日历字形 + "这天没有安排" + 可操作提示（`UiKit.emptyState` 带图标重载）。
- **动效克制**：180–220ms、`DecelerateInterpolator`、错落入场封顶 5 项 × 35ms。
- **无障碍部分到位**：图标按钮都有 `contentDescription`；日期格、周次格、作息表输入框都有
  （如 `MainActivity` 日期格 `"周四2026年10月8日，今天"`）。

### 14.2 值得优先优化的（按影响排序）

| # | 问题 | 依据 |
|---|---|---|
| 1 | **深色模式完全未适配** | 主题固定 `android:style/Theme.Material.Light.NoActionBar`（`values/themes.xml`、`values-v27/themes.xml`），`Palette` 只有一套浅色常量。系统切深色时 App 仍画浅色——不错乱，但**在深色系统下刺眼**，且 Android 13+ 用户默认期待跟随 |
| 2 | **横屏 / 大屏完全不可用** | 7 个 Activity 全部 `screenOrientation="portrait"`。平板上是巨型拉伸，折叠屏展开后无适配 |
| 3 | **无加载状态** | 导入有进度对话框，但**首页/总览的 SharedPreferences 读取没有**。课表大时 `render()` 在主线程解析 JSON；用户看到的是白屏后突然出现 |
| 4 | **无错误状态** | 存储损坏时静默跳过坏条目（好的策略），但**界面不告知**；`courses_backup` 写入了却没有任何恢复入口 |
| 5 | **列表页两次绘制** | `ScheduleListActivity.onCreate:40` + `onResume:50` 各 render 一次；`SemesterListActivity` 只在 `onResume` 画（`onCreate` 不画），两个兄弟页面行为不一致，会有一次可见闪烁 |
| 6 | **表单错误模型不统一** | 三种：字段旁 `setError` + Toast（`EditCourseActivity`）· 字段旁 `setError` + 顶部横幅（`EditScheduleActivity`）· 对话框内 `setError` 且不关闭（设置/学期）。用户要学会三套 |
| 7 | **输入框外观三种** | `UiKit.field`（描边 52dp）、`EditCourseActivity.input`（无描边 46dp）、`EditScheduleActivity.timeInput`（描边 RADIUS_S）。同一个表单里"名称"和"周次"长得不一样 |
| 8 | **卡片密度不一致** | 卡片内边距 5 种取值、卡片阴影 3 种写法（编辑页的卡片是**平的**，列表页是**浮的**）、列表摘要行 11sp vs 12sp、FAB 留白 88dp vs 96dp |
| 9 | **课程卡信息会截断** | `MainActivity.createCourseCard` 的课名限 2 行、地点/教师各限 1 行 + 省略号。真实数据里 `教学楼F楼F406多` 这类长串已经开始被截 |
| 10 | **图标与文字基线** | 元信息行用 `UiKit.inlineIcon`（20dp）配 12sp 文字，图标比字高约 4dp，在密集行里略偏上（未逐像素核对，**待确认**） |
| 11 | **动画未适配无障碍** | 未检查 `ANIMATOR_DURATION_SCALE`，关闭系统动画的用户仍会看到全部入场动画 |
| 12 | **`WeiheSchedule 1.0 · minSdk 26` 出现在"关于"里** | `SettingsActivity:324`——把内部实现细节（minSdk）暴露给终端用户，是开发者视角文案 |

---

## 十五、潜在 Bug 分析

**只列有代码依据的。** 与 §十三 的 S/M 编号对应，此处按"症状"归类。

### 15.1 时间与日期

| 症状 | 依据 | 触发条件 |
|---|---|---|
| **第 3–5 节时间可能早 20 分钟** | S3：`BellSchedule.matchesLocation:97-128` + 关键词 `d楼` | 地点写成 `D305` / `教学楼 D305` 而非 `D楼305` |
| **"本周"/"今天"高亮跨周后不更新** | M2：`WeekGridActivity:121` 只在 `onCreate` 算 `currentWeek` | 进程常驻跨过周日 24:00，且用户停留在总览页 |
| **总览页最后一行被切、节次列滚不到底** | S4：`WeekGridActivity:489` 用 `rowTop` 当高度 | 任何有课的周（`rows >= 11` 时更明显） |
| **合并单元格的结束节次虚高** | M10：`TimetableImportParser:127-134` 兜底到"最后一行" | 导入的 .xls 里合并区底行落在非节次行（如备注行） |
| **"后移一周"能把第 1 周推到未来且无法撤销** | M11：`SettingsActivity:334-342` 缺上界 | 用户在设置页连点"后移一周" |
| **第 1 周在未来的学期里，周次恒为 1** | `ScheduleUtils.currentWeek:26-31` 用 `floorDiv` 后 `max(1, ...)` | 由 M11 造成，或用户手工把开学日设到未来。**这是钳制的正常行为，不是 bug**，但缺少"学期尚未开始"的提示 |

### 15.2 数据一致性

| 症状 | 依据 | 触发条件 |
|---|---|---|
| **重命名作息表后，指定它的课程静默改用别的表** | S1：`EditScheduleActivity:369` 未调 `renameSchedule` | 任何一次作息表重命名 |
| **删除的作息表会自己回来** | S2：`EditScheduleActivity:327-344` 无最后一张保护 + `CourseRepository:581-585` 空列表重建 | 在作息表编辑器里删掉最后一张 |
| **可能改错/删错作息表** | S5/M12：`EditScheduleActivity:45,336,364`、`ScheduleListActivity:238` 按 index 写 | 本页停留期间另一页面改动过作息表列表 |
| **导入的部分课程被静默丢弃** | M9：`TimetableImportParser:16-19,224-230,294-296,302-304` | .xls 里分隔符是单个空格/全角空格，或周次写法超出解析器语法 |
| **`courses_backup` 只写不读** | `CourseRepository:506-508`，全仓库无读取方 | 载荷损坏后，备份在存储里但用户无从恢复 |
| **学期列表页可能 `IndexOutOfBoundsException`** | `SemesterListActivity:216` 的 `semesters.get(size-1)` 依赖 `CourseRepository.getSemesters` 的非空不变式（`:107-114`） | 仅在将来改动仓库时才会触发（**当前不可达**） |

### 15.3 生命周期与线程

| 症状 | 依据 | 触发条件 |
|---|---|---|
| **表单内容全丢** | M16：全仓库 0 处 `onSaveInstanceState` | 编辑课程/作息表时进程被系统回收（后台内存压力） |
| **导入完成前退出页面，`progress.dismiss()` 作用于已销毁窗口** | M8：`SettingsActivity:397-417` 只判 `isFinishing()` | 导入大文件时按返回键 |
| **预览/横幅在 `scheduleSpinner` 就绪前被触发** | `EditCourseActivity:544` 的 null 守卫只挡了两个字段，函数体还解引用 `scheduleValues`/`startPeriodSpinner`/`locationInput` | 当前不可达（赋值顺序恰好安全），属**脆弱守卫** |

### 15.4 崩溃风险

| 症状 | 依据 | 可达性 |
|---|---|---|
| `ClassCastException`：`WeekGridActivity:326` 的 `appBarSubtitleOf(header)` 必须在 `:333` 的 `header.addView(weekNav)` **之前**调用 | `UiKit.appBarSubtitleOf:619-621` 取"最后一个子节点的最后一个子节点"并强转 `TextView` | 顺序一改就崩。当前代码顺序正确，**属隐患不是现存 bug** |
| `NullPointerException`：`TimetableImportParser.parse(null)`（`:26-28`）、`shiftWeeks(null, ...)`（`:89`） | 只校验 int，不校验 list | 当前无调用点传 null |
| `NullPointerException`：`WeekGridPlan.plan` 列表含 null 元素（`:262, :307`） | 只处理 null **列表**，不处理 null 元素 | 当前所有调用方传的都是仓库拷贝，不可达 |
| `IndexOutOfBoundsException`：`TimetableGrid.merge` 收到反向矩形 | `:45-56` 只校验两角在界内，不校验 `bottomRow >= topRow` | 当前被 `ExcelTimetableReader:43-45` 预过滤挡住 |
| **`OutOfMemoryError`：导入超大 .xls** | `ExcelTimetableReader:30` 按 sheet 声明尺寸分配 `TimetableGrid`（4 个 `rows × cols` 二维数组，`:13-16`），无上限 | .xls 可声明 65536 × 256；低端机上可达 |

### 15.5 明确**不是** bug 的几处（避免误判）

- `CourseQuery.inWeek` 让不同周次的课程同时出现在单周视图：由 `occursInWeek` 按周次窗口 + 单双周过滤，**行为正确**。
- 总览页不显示任何时刻：`WeekGridActivity.periodCell:646-654` 的注释解释了原因——总览同时展示多个场所的课程，而各场所作息不同，写时间就是在替别的楼做主张。**这是正确的设计决策**。
- `BellSchedule.parseLabel` 接受 24:00–29:59：跨午夜的晚课需要它，且 `label()` 写出的格式与它一致（`:161-196`），**两者必须同步，代码里写明了**。
- `WeekGridPlan` 的车道数是"每簇"而非"每节"：会让连成一片的课程多占一列，但换来列对齐稳定，**这是有意的取舍**（`:322-326`）。
- 样例课程数为 9、学期数为 1：`createSampleCourses` 只在"安装时的那个学期"写入（`CourseRepository:419`），新建学期为空是**有意的**。

---

## 十六、整体架构评价

### 16.1 定级：**中等规模项目，正在向"相对成熟"过渡**

不是 Demo（有 221 个真测试、有分层、有设计系统、有错误恢复与数据迁移），
也还没到"相对成熟"（UI 层零测试、无 ViewModel/状态保存、无深色模式、Activity 过重、
存在 S1/S2 这类静默数据语义错误）。

**支撑定级的量化依据**

| 指标 | 数值 | 说明 |
|---|---|---|
| 生产代码 | 31 文件 / 10,538 行 | 中等规模 |
| 测试代码 | 18 文件 / 3,533 行 / 221 测试 | 测试:生产 ≈ 1:3（**健康**） |
| 规则层零 Android 依赖的类 | 14 个 / 约 2,700 行 | 可 JVM 测试部分占 26% |
| UI 层测试覆盖 | **0** | 约 5,750 行无覆盖 |
| 最大类 | `UiKit` 1432 行 / `MainActivity` 1324 行 | **偏大**（常见经验阈值 ~500 行） |
| 最大方法 | `SettingsActivity.confirmImport` 约 75 行、`MainActivity.refreshAll` 约 58 行 | 偏长但可读 |

### 16.2 当前架构的优点

1. **分层是真的，不是口号。** 规则层（`WeekGridPlan`/`CourseQuery`/`CourseConflicts`/`CourseFormValidator`/
   `CourseList`/`ScheduleUtils`/`BellSchedule*`/`Semester`/`TimetableGrid`/`MiniJson`）
   完全不依赖 Android，共约 2,700 行，全部有 JVM 测试。依赖方向单一，下层不知道上层。
2. **测试纪律由约束驱动而非口号驱动。** `returnDefaultValues = false` 这条设置使得"碰 Android 就失败"，
   于是 `MiniJson` 手写、`Colors` 独立、规则从 Activity 里抽出来——**约束塑造了架构**。
3. **设计系统完整且被算术验证。** 颜色角色 100%、圆角/间距/字阶 token 化、WCAG 对比度有 14 项断言。
4. **存储层有防御性设计。** 宽容解析 + 损坏备份 + 旧版迁移 + 缓存失效（`reload`），
   每条都写了"为什么"并且有测试（迁移的前身 bug 记录在 `ARCHITECTURE.md:224-233`）。
5. **文档质量罕见。** `ARCHITECTURE.md` 362 行，记录了每个设计决策的**失败历史**
   （"表头绝不能自己能动"、`GridLayout` 缩小行数必须先清空子视图、迁移要按名字+时间匹配…），
   以及一份可执行的上机回归清单。这份文档本身就是项目的核心资产。
6. **没有过度设计。** 没有 DI 框架、没有事件总线、没有 ViewModel 样板、没有为将来预留的抽象。
   在 10K 行规模上这是**正确的克制**。

### 16.3 当前架构的问题

1. **UI 层的质量完全靠人肉保证。** 5,750 行、10 个类零测试，其中包括已经出过致命 bug 的
   `WeekGridActivity.render()` 顺序和会静默旋转课表的 `ExcelTimetableReader` 转置。
2. **Activity 承担了太多职责。** `MainActivity` 1324 行同时管：视图构建、日期条渲染、课程卡构建、
   周次选择弹层、手势、导入（死代码）、删除对话框。没有 ViewModel，
   状态（`selectedWeek`/`selectedDay`/`motionDirection`）与视图生命周期绑定。
3. **一致性靠约定，没有强制。** 5 种卡片内边距、3 种输入框、3 种错误模型、2 种列表摘要字号——
   都是"每个页面各自决定"的历史残留。`UiTokens` 提供了 token，但**没有机制阻止绕过它**。
4. **`UiKit` 已经成为不可测的单体。** 1432 行 / 78 个 public 方法，既是组件库又是动画框架又是对话框框架。
   零测试意味着任何重构都是盲改。
5. **两个静默的数据语义缺陷（S1、S2）说明"写路径"缺少同等强度的审查。**
   读路径（解析、迁移、缓存）有 25 个测试；写路径（重命名级联、删除保护）一个都没有。
6. **无状态保存、无深色模式、无横屏**——三项都是"发布前必须补"的产品级缺口。

### 16.4 最值得重构的地方（按性价比排序）

| 优先 | 重构 | 理由 | 预估风险 |
|---|---|---|---|
| 1 | **修 S1/S2/S3/S4/S5 五个缺陷并各补一个测试** | 都是静默错误，用户无法自行发现 | 低（都是小改动） |
| 2 | **删 `MainActivity:1159-1295` 的死导入代码 + 5 个孤儿 import** | 净减 137 行，零行为变化 | 极低 |
| 3 | **把 `CourseRepository` 的读写契约统一**（`getBellSchedules` 也返回拷贝），并给写路径补测试 | 消除靠调用方自觉的隐患 | 低 |
| 4 | **给 UI 层补"可测的核"**：把 `MainActivity` 里纯计算的部分（日期条三态判定、课程卡标签拼接、`isShowingToday`）抽成纯函数并测试；`UiKit` 至少给颜色派生与形状计算补测试 | 用最小改动覆盖最容易回归的部分 | 中 |
| 5 | **统一表单与卡片**：合并 3 种输入框为 `UiKit.field`、5 种卡片内边距为 `UiKit.card`、3 种错误模型为一种 | 消除 §14.2 的 6/7/8 | 中（触碰多个页面，需要回归） |
| 6 | **补 `onSaveInstanceState`** | 编辑页 30 字段的表单丢失是真实用户损失 | 低 |
| 7 | **抽 `TimetableImportCoordinator`**：把导入的挑选/解析/确认/错误四个阶段从 Activity 里拿出来 | 现在 `SettingsActivity:381-506` 有 125 行不可测的流程控制 | 中 |
| 8 | 深色模式 + 横屏 | 产品级缺口 | 高（需要第二套 Palette + 布局响应式） |

### 16.5 目前**不应该动**的地方

1. **`BellSchedules` / `BellSchedule` 的时间数据与解析顺序。** 已逐节核对与学校表格一致（§11），有 31 个测试钉住。
   唯一要动的是**关键词匹配**（S3），而不是时间本身。
2. **`WeekGridActivity` 的滚动架构。** 单滚动容器 + 平移跟随的方案是踩了三次坑（表头自己会滑、
   表头被套进自己的 ScrollView、宽度算错导致周三之后消失）才定下来的，
   `ARCHITECTURE.md:176-222` 记录了完整推导。**除了 S4 的一行高度 bug，不要重构它。**
3. **`MiniJson`。** 手写 453 行换来了存储层可测；换回 `org.json` 会让 25 个 `CourseRepositoryTest` 全部失效。
4. **`CourseRepository` 的宽容解析策略。** "坏条目跳过 + 备份 + 不整体丢弃"是针对真实数据丢失 bug 的修复，
   改成抛异常会更"干净"但更危险。
5. **`values/themes.xml` 与 `values-v27/themes.xml` 的完整重复。** 看起来是重复，实际是绕过
   "style 不跨配置目录合并"这一平台行为的唯一办法（`ARCHITECTURE.md:156-167`）。**不要"优化"成继承。**
6. **程序化 UI 本身。** 10K 行规模迁移到 Compose 或 XML 的收益不足以覆盖风险；
  真正缺的是 UI 测试，不是声明式布局。

---

## 十七、后续开发路线

### 17.1 优先级

#### P0 — 必须解决（正确性 / 数据安全 / 发布阻塞）

| # | 事项 | 对应 |
|---|---|---|
| P0-1 | 修 **S3** 地点→作息匹配：关键词改为能覆盖 `教学楼 D305` / `D305` / `D楼305` 三类写法，或把"解析结果"显式显示给用户确认 | §11.4 |
| P0-2 | 修 **S1** 作息表重命名级联（保存前调 `renameSchedule(originalName, newName)`） | §13.1 |
| P0-3 | 修 **S2** 作息表编辑器删除最后一张的保护（与 `ScheduleListActivity:221-225` 对齐） | §13.1 |
| P0-4 | 修 **S5/M12** 作息表按 index 写入 → 改为按**名字**定位 | §13.1 |
| P0-5 | 修 **S4** 总览页日列高度（`rowTop(rows)` → `rowSpan(rows)`），并给 `grid` 补 `setClipChildren(false)` | §13.1 |
| P0-6 | 为 P0-1…P0-5 各补一个测试（S3/S4 可纯 JVM 测；S1/S2/S5 在 `CourseRepositoryTest` 里可测） | §12.5 |
| P0-7 | 删除 `MainActivity:1159-1295` 死导入代码 + 孤儿 import | M1 |

#### P1 — 建议近期解决（可维护性 / 用户体验）

| # | 事项 | 对应 |
|---|---|---|
| P1-1 | 修 **M2**（`WeekGridActivity` 的 `currentWeek` 在 `onResume` 重算）、**M11**（"后移一周"上界）、**M3**（节次错误挂到正确字段）、**M4**（错误不在击键时全清） | §13.2 |
| P1-2 | 补 `onSaveInstanceState`（两个编辑页 + 学期/导入对话框） | M16 |
| P1-3 | 统一 `CourseRepository` 的读写契约（`getBellSchedules` 返回拷贝），给 `deleteCourse`/`backupDamaged`/`replacePlaceholderSchedules` 补测试 | M7 / §12.5 |
| P1-4 | 给 `ExcelTimetableReader` 补一个用真实 `.xls` 夹具的 JVM 测试（jxl 是纯 JVM jar，不需要模拟器），**至少覆盖行列转置** | §12.5 |
| P1-5 | 给 `WeekGridActivity.render()` 的"先清空再改行数"顺序补一个能被打破的测试（把 `lastPeriodInUse` 提成包级静态，或在 `WeekGridPlan` 里加行数计算） | §12.5 |
| P1-6 | 统一表单与卡片：3 种输入框 → `UiKit.field`；5 种卡片内边距 → `UiKit.card`；`UiKit.chip/styleChip` 真正被用起来 | §14.2 |
| P1-7 | 修 **M6**（颜色行溢出，先渲染确认再决定换行/滚动/缩小） | §13.2 |
| P1-8 | 修 **M9/M10**（导入解析的静默丢弃：加计数与提示，`periodForRow` 兜底改近邻） | §13.2 |
| P1-9 | 抽 `TimetableImportCoordinator`，让导入流程可测 | §16.4 |
| P1-10 | 修 **M13**（列表页 `onActivityResult` 用陈旧缓存）/ **M8**（导入线程与对话框生命周期） | §13.2 |

#### P2 — 后续优化

| # | 事项 |
|---|---|
| P2-1 | 深色模式（需要 `Palette` 的第二套色 + `values-night`） |
| P2-2 | 横屏/大屏适配（至少去掉 portrait 锁，用 `layout-land` 等价的双栏或让总览横屏全屏） |
| P2-3 | 上课提醒（`AlarmManager` / `WorkManager` + 通知权限）——**功能上最值得加的下一项** |
| P2-4 | 课程详情页（点卡片看详情，长按或二级入口才进编辑），顺带解决长文案截断 |
| P2-5 | 数据导出/导入备份（JSON 分享），补上"卸载即全丢" |
| P2-6 | 字体缩放 2.0× 的完整适配（首页已验，其余 6 屏未验） |
| P2-7 | 无障碍：动画遵循 `ANIMATOR_DURATION_SCALE`，给列表卡片补 `contentDescription` |
| P2-8 | 清理 §13.3 的轻微问题（死代码、悬空 javadoc、废弃别名、重复 `dp()`、无用 import） |
| P2-9 | 文案资源化（`strings.xml` 现有 1 条，界面有约 1,560 处 `\uXXXX` 内联中文）——**收益低、改动面大，建议长期挂起** |

### 17.2 推荐顺序（结合本项目实际重新判断）

```
第 1 步  架构稳定 + 数据正确性     P0-1…P0-6   （5 个静默缺陷 + 对应测试）
第 2 步  代码清理                  P0-7        （删 137 行死代码）
第 3 步  核心链路的测试地基        P1-3、P1-4、P1-5  （存储写路径 / Excel 转置 / 网格行数顺序）
第 4 步  数据层契约统一            P1-3 续     （读写一致 + 写路径测试）
第 5 步  体验缺陷                  P1-1、P1-2、P1-7、P1-8、P1-10
第 6 步  UI 一致性                 P1-6        （合并输入框/卡片/错误模型）
第 7 步  结构改进                  P1-9        （导入协调器）
第 8 步  功能补齐                  P2-3、P2-4  （提醒 / 详情页）
第 9 步  平台适配                  P2-1、P2-2、P2-6
第 10 步 性能与发布               P2-5、P2-7、P2-8、P2-9 + 真实设备回归
```

**为什么这样排**：
- 本项目**最大的风险不是性能、不是架构不美，而是"界面显示的课程时间是错的"**（S3）和
  **"用户的操作被静默撤销"**（S1/S2）——所以数据正确性排在最前，且每修一个必须配一个测试
  （当前测试套件的规则层很扎实，写路径却很空，这个不对称必须补上）。
- **性能不需要早做**：23 门课、单机存储、无网络，除了导入 200+ 门课后主线程解析会有感知，其余无压力。
- **文案资源化放到最后**：约 1,560 处内联中文，改完对用户零可见收益，且当前 lint 已显式豁免。
- **深色模式放 P2 而非 P1**：它是产品级缺口但不影响正确性，且需要第二套完整配色 + 逐屏核对，
  是这批任务里最贵的一项。

---

## 十八、附录

### 18.1 关键证据索引

| 主题 | 位置 |
|---|---|
| 三张作息表的原始数据 | `BellSchedules.java:60-96` |
| 作息解析顺序 | `BellSchedules.java:176-192` |
| 地点关键词匹配（S3 根因） | `BellSchedule.java:97-128` |
| 样例课程的地点写法 | `CourseRepository.java:936-967` |
| 总览页日列高度（S4 根因） | `WeekGridActivity.java:489` vs `:517-522, :530` |
| 网格行数顺序修复 | `WeekGridActivity.java:474-478` |
| 作息重命名未级联（S1） | `EditScheduleActivity.java:361-369` vs `CourseRepository.java:738-755` |
| 删除最后一张作息表（S2） | `EditScheduleActivity.java:327-344` vs `ScheduleListActivity.java:221-225` vs `CourseRepository.java:581-585` |
| `MainActivity` 死导入代码（M1） | `MainActivity.java:1159-1295` |
| 存储键与读写 | `CourseRepository.java:15-25, 372-378, 886-921` |
| 测试缝 | `CourseRepository.java:81-86` |
| 设计系统 token | `UiTokens.java` 全文 |
| 颜色角色与课程色 | `Palette.java` 全文 |
| 对比度断言 | `PaletteContrastTest.java` 全文 |
| 项目自述与踩坑记录 | `ARCHITECTURE.md` 全文（尤其 §四 与 §五 回归清单） |

### 18.2 本次未能确证、标记为「待确认」的项

1. **`EditCourseActivity` 颜色选择行是否真的在 360dp 屏上溢出**——由 dp 算术推断（9×30 + 9×8 = 342dp > 可用 ~304dp），
   未在设备上渲染验证（本轮约束为只分析，未改动也未做逐屏视觉核对）。
2. **Android 自动备份是否覆盖本 App 的 SharedPreferences**——Manifest 有 `allowBackup="true"`，
   但未查 `backup_rules`（不存在）与设备上的备份配置。
3. **`android:screenOrientation="portrait"` 是产品决策还是权宜之计**——代码与文档均未说明。
4. **`.xlsx`（ZIP 容器）失败时 jxl 抛的具体异常类型与消息**——`ExcelTimetableReader` 声明 `BiffException`/`IOException`，
   而 `.xlsx` 提示靠消息文本匹配 `"zip"`（`SettingsActivity:501-503`），未实测其消息内容。
5. **`getBellSchedules()` 的缓存不对称**（新建/迁移分支不写缓存，`:581-589`）是否有实际影响——当前每次都会重新读取并写回，功能正确，只是多做一次 IO。
6. **真机/模拟器上的深色模式、横屏、刘海屏表现**——本轮未做这些场景的运行检查。

### 18.3 与仓库既有文档的一致性

`ARCHITECTURE.md` 中**已过时或与当前代码不符**的表述（本次未修改该文件）：

| 位置 | 表述 | 实际情况 |
|---|---|---|
| `:331-332` | 本机运行方式写 `D:\Weihe\...` | 活的项目在工作区 `D:\vibe coding\Weihe\Weihe` |
| `:291-317` | 上机回归清单第 6 条 | 按当前代码，「教学楼 D305」**不会**得到 D/E 作息（S3），该验收项当前失败 |
| `:85-89` | 验证状态表 | 221 项测试、lint 零问题（`PaletteContrastTest` 已从 13 项增至 14 项，表内已更新） |

---

**本次仅分析，未修改项目代码。**
未新增/删除/改动任何 `.java`、`.gradle`、`AndroidManifest.xml`、`res/`、`libs/` 或依赖；
未运行 Gradle 构建、未运行测试、未运行任何自动修复。仅新增本文件与 `PROJECT_ARCHITECTURE.md` 两份文档。
