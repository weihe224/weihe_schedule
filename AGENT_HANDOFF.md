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