# DATA_BACKUP_RESTORE_PLAN.md — 「拾课」数据导出、备份与恢复方案

> **本轮只做代码审查、需求分析与方案设计。**
> 未修改生产代码、测试代码、Gradle 配置或依赖；未新增除本文档以外的文件。
> 当前代码中**不存在**任何导出、备份或恢复能力——下文所有流程均为**待实现的设计**。
>
> **依据**：分支 `refactor/project-structure`，HEAD `efe5ace`。
> 所有结论标注 `文件:行号`。**本轮未执行构建、测试或真机操作**；凡未实际跑过的一律标注 **待确认**。

---

## 〇、审查前状态与高风险缺陷标记

### 0.1 Git 与工作区（原样保护，未做任何清理）

```
分支   refactor/project-structure
HEAD   efe5ace docs: record the new package structure and the refactor that produced it

 D ARCHITECTURE.md                                          ← 既有
 D ARCHITECTURE_REFACTOR_PLAN.md                            ← 既有
 M app/src/test/.../importer/TimetableImportParserTest.java  ← 既有
?? DATA_BACKUP_DESIGN.md                                    ← 既有
?? NEXT_OPTIMIZATION_ROADMAP.md                             ← 既有
?? app/src/test/.../data/CourseRepositoryStorageTest.java   ← 既有
?? app/src/test/.../importer/ExcelTimetableReaderTest.java  ← 既有
?? app/src/test/.../importer/TimetableGridTest.java         ← 既有
```

开工前已记录以上 **6 个文件**（`DATA_BACKUP_DESIGN.md`、`NEXT_OPTIMIZATION_ROADMAP.md`、
`CourseRepositoryStorageTest.java`、`ExcelTimetableReaderTest.java`、`TimetableGridTest.java`、
`TimetableImportParserTest.java`）的 MD5，收工时逐一比对证明未被触碰。
`git diff --quiet HEAD -- app/src/main` → **unchanged**，生产代码自上次审查以来无改动。
工作区共 **8 项**未提交改动，本轮全部原样保留。

> **文档重复提示**：根目录另有 `DATA_BACKUP_DESIGN.md`（上一轮相近需求的产物，未跟踪）。
> 两份内容有重叠，本文档按本轮要求的 10 个小节重新组织并补充了导出/恢复流程细节、
> 可复用能力清单与文件清单。**建议保留本文档、由你决定是否删除前者**——本轮未删除任何文件。

### 0.2 高风险缺陷标记（复核结论，未修复）

上一轮存储测试补强通过**临时探针实际执行**发现两个缺陷。本轮**已重新逐行复核当前源码，两者都仍然存在**：

| # | 缺陷 | 当前源码证据 | 与备份/恢复的关系 |
|---|---|---|---|
| **H1** | `getBellSchedules()` **返回内部缓存列表本体**（`getCourses()` 返回深拷贝、`getSemesters()` 返回浅拷贝，三者契约不一致） | `CourseRepository:583` `return snapshot;`（缓存命中路径）；`:596-597` `cachedSchedules = schedules; return schedules;`。上一轮实测：对返回列表 `clear()` 后 `getBellSchedules().size()` 由 3 变 0，而**存储里仍有 3 张** | **直接影响导出**：导出若持有该列表并延后使用，可能读到被同进程其它页面改动的实例。设计对策见 §4.4 |
| **H2** | 占位作息表替换会**连带覆盖用户自建的作息表** | `CourseRepository:623-624` `schedules.clear(); schedules.addAll(BellSchedules.presets());`——只要发现**任意一张**占位表就**整表替换**。上一轮实测：存储 `[实验楼作息, 我的作息表]` → 读取后变成 3 张预设，用户的表消失 | **影响恢复**：恢复后若备份里的作息表命中旧版占位签名，下一次读取会再次整表替换。设计对策见 §5.4 校验规则 V7 |

**按本轮要求：不擅自修复，也不假定其仍然存在——以上结论均来自本轮对当前源码的复核。**
另有两条低风险项（`MiniJson` 载荷长度阈值 `readCourses:475` 造成的备份误判、`getCourses()` 列表顺序在首次写入后改变）记录在 §10。

---

## 一、当前数据存储结构及对应源文件

### 1.1 唯一的存储介质

```
data/CourseRepository.java:21   private static final String PREFS = "weihe_schedule";
data/CourseRepository.java:95   return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
```

实测确认：全项目**只有这一处** `getSharedPreferences`；没有第二份偏好文件、没有文件存储、
没有数据库、没有 `DataStore`。`AndroidManifest.xml` **未声明任何权限**（含无 `INTERNET`）。

### 1.2 完整的键清单（逐键实测）

| 键 | 类型 | 写入点 | 读取点 | 归类 |
|---|---|---|---|---|
| `courses` | String(JSON 数组) | 迁移时 `remove`（`:312`） | `:302`（迁移） | 旧版遗留，迁移即删 |
| `semester_start` | String(日期) | 迁移时 `remove`（`:316`） | `:292`（迁移） | 旧版遗留，迁移即删 |
| `semesters` | String(JSON 数组) | `:304`、`:350` | `:112`、`:289` | ✅ **用户数据**：学期清单 |
| `active_semester` | long | `:150`、`:305` | `:129`、`:357` | ✅ **用户数据**：当前学期 |
| `bell_schedules` | String(JSON 数组) | `:706` | `:585` | ✅ **用户数据**：作息表（全局，不分学期） |
| `courses:<学期id>` | String(JSON 数组) | `:467`、`:939` | `:420`、`:457`、`:936` | ✅ **用户数据**：该学期课程 |
| `semester_start:<学期id>` | String(日期) | `:237`、`:315`、`:318`、`:903` | `:878` | ✅ **用户数据**：该学期第 1 周周一 |
| `courses_backup` | String(原始载荷) | `:513` | **无任何读取点** | ❌ **内部诊断产物** |

键名拼接：`CourseRepository:31` `SUFFIX_SEPARATOR = ":"`；`coursesKeyFor`/`startKeyFor`（`:369-384`）。
**课程与学期的关联完全靠键名里的 id 表达**，没有外键字段。

### 1.3 数据模型（`model/` 包，均为纯 Java，零 Android 依赖）

**`Course`**（`model/Course.java`）——12 个持久化字段 + 1 个派生字段：

| 字段 | 类型 | 默认值 | 备注 |
|---|---|---|---|
| `id` | long | `0` | `0` = 未分配，由 `CourseList.ensureId:99-104` 补齐 |
| `name` `teacher` `location` | String | `""` | |
| `dayOfWeek` | int | `1` | 1=周一 … 7=周日 |
| `startPeriod` / `endPeriod` | int | `1` / `2` | 1..15（`ScheduleUtils.PERIODS_PER_DAY:11`） |
| `startWeek` / `endWeek` | int | `1` / `18` | 1..18（`ScheduleUtils.SEMESTER_WEEKS:10`） |
| `parity` | int | `0` | `PARITY_ALL=0` / `PARITY_ODD=1` / `PARITY_EVEN=2`（`Course:6-8`） |
| `color` | int | `Course.DEFAULT_COLOR` | **存储时写成 long**（`toJson:986`：避免被 JSON 双精度吃掉），无符号 32 位 ARGB |
| `scheduleName` | String | `""` | 作息表**名字**（不是索引）；空 = 按地点自动匹配 |
| ~~`timeRange`~~ | String | `""` | **派生值**（`Course:47`），由 `applyTimeRange:505-509` 在读取时盖章，**不持久化** |

**`Semester`**（`model/Semester.java:26-28`）——不可变，仅 3 个字段：`id`(long) / `start`(LocalDate) / `name`(String)。
`name` 为空表示「按日期推导标签」（`label()`），不是缺数据。

**`BellSchedule`**（`model/BellSchedule.java`）——不可变，**恰好 4 个字段**（`:36-39`）：
`name` / `starts` / `ends` / `locationKeywords`。`PERIODS = 15`（`:23`），`MAX_MINUTE = 1799`（`:34`，允许跨午夜的 29:59）。
**实测该类没有 `room` 之类的其它字段**——每节只有开始与结束两个分钟值。

### 1.4 序列化格式（三个 codec，全部走 `MiniJson`）

**课程**（`CourseRepository.toJson:973-989` ↔ `fromJson:991-1008`）：

```json
{"id":1,"name":"高等数学","teacher":"张老师","location":"教学楼 A203",
 "day":1,"startPeriod":1,"endPeriod":2,"startWeek":1,"endWeek":16,
 "parity":0,"color":4282149518,"scheduleName":""}
```

**学期**（`SemesterJson:15-17` schema 注释 ↔ `:51-66`）：

```json
[{"id":1757000000000,"start":"2026-08-31","name":""}]
```

**作息表**（`BellScheduleJson:14-21` schema 注释 ↔ `:65-81`）：

```json
[{"name":"教学楼作息","keywords":["a楼","f楼","j301"],
  "starts":[495,535,…15 个],"ends":[535,575,…15 个]}]
```

三者读取都是**宽容**的：`SemesterJson:41-43` 跳过 id 为 0 或日期不可解析的条目；
`BellScheduleJson:98-102` 在 `starts`/`ends` 长度不符时**整张表退化为内置标准作息**；
`readCourses:475-491` 跳过非对象条目并备份原文。

### 1.5 迁移与损坏处理（恢复流程必须与之共存）

| 机制 | 位置 | 行为 |
|---|---|---|
| 旧版迁移 | `migrateLegacyStorage:288-323` | `semesters` 不存在时执行一次，**用一个 Editor** 写 3~5 个键 |
| 损坏备份 | `backupDamaged:512-514` | 原始载荷写进 `courses_backup`（只写不读） |
| 空载荷播种 | `loadCourses:419-436` | `courses:<id>` 为 `null` **且**该学期是「安装出生学期」→ 写 9 门样例课 |
| 空清单兜底 | `getSemesters:113-120` | `semesters` 为空 → 造一个新学期 |
| 空作息兜底 | `getBellSchedules:587-591` | `bell_schedules` 为空 → 写入 `BellSchedules.presets()` |
| 占位表替换 | `replacePlaceholderSchedules:612-626` | 见 **H2** |

---

## 二、当前实现可以直接复用的能力

这一节是本方案能做到「小改动」的前提——**大部分零件已经存在**。

| 能力 | 现成位置 | 复用于 |
|---|---|---|
| JSON 读写（含嵌套对象/数组） | `MiniJson.write:138` 支持 `Map`/`List`/`String`/`Boolean`/`Number`/`null`；`parse:40-56` 顶层对象返回 `Map`，非法文本返回 `null` | 备份载荷的构建与解析，**无需改 `MiniJson`、无需新依赖** |
| 稳定的键输出顺序 | `MiniJson.object():128-130` 返回 `LinkedHashMap` | 同一数据连续导出得到**相同字节**，便于测试断言 |
| 课程 JSON 映射 | `CourseRepository.toJson:973-989` / `fromJson:991-1008` | 备份里的课程段可直接复用同一套字段语义 |
| 学期 JSON 映射 | `SemesterJson.write:51` / `parse:30` | 同上 |
| 作息表 JSON 映射 | `BellScheduleJson.write:53` / `parse:40` | 同上 |
| **一个 Editor 写多个键** | `migrateLegacyStorage:303-320` | **这是恢复原子性的既有先例**，见 §5.3 |
| 枚举现有键 | `SharedPreferences.getAll()`（标准 API；`FakePreferences:37-39` 已实现且返回副本） | 恢复时计算需要删除的过期键 |
| 读取任意学期的课程而不切换当前学期 | `coursesOf:450-459` | 导出多学期数据 |
| 全量替换课程 | `replaceCourses:555-569` | 参考其「清空 `scheduleName`」的既有语义 |
| **文件选择** | `SettingsActivity.openImportPicker:393-402`：`ACTION_OPEN_DOCUMENT` + `CATEGORY_OPENABLE` + `EXTRA_MIME_TYPES`；`REQUEST_IMPORT_TIMETABLE = 3001`（`:48`）；`onActivityResult:81-90` | 恢复的选文件流程**照抄即可** |
| **取消即无副作用** | 同上：`resultCode != RESULT_OK` 直接什么都不做 | 「用户取消不改数据」已有先例 |
| 后台线程 + 进度对话框 | `SettingsActivity.importTimetable:404-431`（`new Thread` + `runOnUiThread` + `isFinishing()` 判断） | 导出/恢复的 I/O 部分 |
| 纯 JVM 测试基础设施 | `FakePreferences`（实现 `SharedPreferences`，含 `getAll`/`snapshot`/`edit`/`commit`）；`CourseRepository.useStoreForNextInstance` 测试缝（`:87-89`） | 导出/恢复可在 JVM 上完整验证，不需要模拟器 |
| 对话框与反馈组件 | `UiKit.dialog` / `UiKit.message` / `Toast`（`SettingsActivity:491-506,517`） | 恢复确认框与结果反馈 |

**因此本方案只需要新增 1 个生产类**（见 §8），其余都是复用。

---

## 三、备份 JSON 格式设计

### 3.1 应备份的内容（逐项对照源码确认）

| 内容 | 是否备份 | 依据 |
|---|---|---|
| 课程全部 12 个持久化字段 | ✅ | §1.3 |
| 课程 `id` | ✅ | 学期内唯一，恢复后保持稳定（`CourseList` 的既有不变式） |
| 学期 `id` / `start` / `name` | ✅ | §1.3 |
| 当前学期 | ✅ `activeSemesterId` | `active_semester` 键 |
| 用户自定义作息表（名字/关键词/30 个时间值） | ✅ | `bell_schedules` 键 |
| 课程与作息表的关联 | ✅ 即 `scheduleName` | 按**名字**关联 |
| 课程与学期的关联 | ✅ **用嵌套结构表达** | 内部靠 `courses:<id>` 键名，见 §3.3 |
| 课程颜色 / 教师 / 教室 / 周次 / 单双周 | ✅ | §1.3 |
| `Course.timeRange` | ❌ | 派生值，读取时重算（`applyTimeRange:505-509`） |
| `courses_backup` | ❌ | 内部诊断产物，只写不读；导出它等于把已知损坏的数据当正常数据分发 |
| `courses` / `semester_start` 遗留键 | ❌ | 迁移即删 |
| 界面状态（当前周、选中日、总览/单周切换） | ❌ | 实测**未持久化**，仅在 Activity 内存中 |
| 主题/通知/账号等设置 | ❌ **不存在** | 全项目无此类键；`SettingsActivity` 的 5 张卡片（`:201/215/229/246/264`）全部走 `CourseRepository` |

**结论：没有需要额外脱敏的应用配置**；但课程含**教师姓名与教室**，仍需向用户明示（§4.5）。

### 3.2 格式示例（字段全部来自实际源码，无编造字段）

```json
{
  "format": "weihe-schedule-backup",
  "formatVersion": 1,
  "exportedAt": "2026-10-09T21:32:10+08:00",
  "appVersionName": "1.0",
  "activeSemesterId": 1757000000000,
  "semesters": [
    {
      "id": 1757000000000,
      "start": "2026-08-31",
      "name": "",
      "courses": [
        { "id": 1, "name": "高等数学", "teacher": "张老师", "location": "教学楼 A203",
          "day": 1, "startPeriod": 1, "endPeriod": 2,
          "startWeek": 1, "endWeek": 16, "parity": 0,
          "color": 4282149518, "scheduleName": "" },
        { "id": 5, "name": "程序设计实验", "teacher": "陈老师", "location": "教学楼 E302",
          "day": 3, "startPeriod": 5, "endPeriod": 6,
          "startWeek": 1, "endWeek": 16, "parity": 1,
          "color": 4291836994, "scheduleName": "D/E 楼作息" }
      ]
    },
    { "id": 1760000000000, "start": "2027-02-22", "name": "大二下", "courses": [] }
  ],
  "schedules": [
    { "name": "教学楼作息", "keywords": ["a楼", "f楼", "j301"],
      "starts": [495, 535, 595, 635, 680, 800, 840, 900, 940, 995, 1035, 1090, 1130, 1175, 1220],
      "ends":   [535, 575, 635, 675, 720, 840, 880, 940, 980, 1035, 1075, 1130, 1170, 1215, 1260] }
  ]
}
```

> **注意两件事**：
> 1. `schedules[].starts/ends` 上面的数值是**示意**，用于说明数组形状与长度（必须为 15）。
>    实现时**必须**从 `BellSchedules.presets()` / 用户存储取值，**不得硬编码**。
> 2. `color` 用**无符号 long**（示例 `4282149518` = `0xFF3C6A8E`）。
>    这是内部存储已有的表示法（`toJson:986` 及其注释），沿用它可以避免有符号/无符号转换错误。

### 3.3 字段规则表

**顶层**

| 字段 | 必需性 | 类型 | 缺失 / 异常处理 |
|---|---|---|---|
| `format` | **必需** | String | ≠ `"weihe-schedule-backup"` → 拒绝（防止选错文件，例如选成教务系统的 `.xls`） |
| `formatVersion` | **必需** | int | 缺失/非数字 → 按 `1` 处理并提示「未标注版本」；`> 支持上限` → 拒绝 |
| `exportedAt` | 写出必需 / 读入可选 | String（ISO-8601 带偏移） | 缺失 → 摘要显示「未标注导出时间」，不阻断 |
| `appVersionName` | 可选 | String | 缺失 → 不显示。纯诊断信息 |
| `activeSemesterId` | 可选 | long | 对不上任何学期 → 回落到 `semesters[0]`（与 `getActiveSemester:127-137` 一致） |
| `semesters` | **必需** | Array | 缺失/空 → 拒绝（App 不变式要求 ≥1 个学期，`deleteSemester:256`） |
| `schedules` | **必需** | Array | 缺失/空 → 拒绝（`getBellSchedules:587-591` 会把空当「没存过」并重新播种，等于用户的作息表被预设顶掉） |

**`semesters[]`**：`id` 必需(long, >0)；`start` 必需(`yyyy-MM-dd`)；`name` 可选(缺省 `""`)；`courses` 可选(缺省 `[]`)。

**`semesters[].courses[]`**：§1.3 的 12 个字段，**全部可选**，缺失取默认值；
但 `id` 为 `0` 或与同学期重复时**重新分配**（`CourseList.nextId:82-88`）并计入摘要。

**`schedules[]`**：`name` 必需（去空白非空且不重名）；`keywords` 可选(缺省 `[]`)；
`starts`/`ends` **必需，长度必须相等且等于 15**。

> 最后一条是**刻意比内部读取更严格**：内部 `BellScheduleJson:98-102` 对长度不符是**静默退化**为标准作息，
> 那对「读自己的存储」是合理兜底，但对「用户主动导入的文件」是无提示的数据替换，必须报错。

### 3.4 为什么选择这个格式

1. **对比的是容器，而不是字段名。** 内部存储是「扁平的键值对 + 键名里嵌 id」；备份是「单个对象 + 结构嵌套」。
   最实质的差别是**课程归属**：内部靠 `courses:<id>` 这个**键名**，备份靠 `semesters[].courses[]` 这个**结构**。
   这样备份文件不依赖内部键名约定，将来内部实现改动不会泄漏到公共格式。
2. **顶层对象而非数组**，因为需要携带 `format`/`formatVersion`/`exportedAt` 这类元数据；
   `MiniJson.parse` 支持顶层对象（`:271-272` → `readObject:288`），无需改 `MiniJson`。
3. **字段名复用内部命名**（`day`/`startPeriod`/`parity`/`scheduleName`…），
   从 `formatVersion 1` 起它们成为**对外契约**：内部若改名，必须在映射层吸收，不得让备份格式跟着变。
   （备选是另起一套字段名，但会凭空多一层翻译且无收益。）
4. **`color` 沿用无符号 long 表示**，与内部一致，避免转换 bug。

### 3.5 向后兼容与版本升级策略

**读**：

| `formatVersion` | 处理 |
|---|---|
| 缺失 / 非数字 / `0` | 按 `1` 处理 |
| `1` .. `MAX_SUPPORTED`（首版 = 1） | 正常恢复 |
| `> MAX_SUPPORTED` | **拒绝**，提示「该备份由更新版本的拾课导出，请先升级 App」。**绝不尝试部分读取** |

**未知字段**：一律忽略（`MiniJson` 的取值 API 天然只取自己认识的键）。
因此**新增可选字段不需要提升 `formatVersion`**。

**必须提升 `formatVersion` 的三种情况**：① 删除/改名/改变字段含义；② 新增**必需**字段；
③ 改变数值语义（例如 `color` 改成有符号 int）。

**降级导出**：首版不提供「另存为旧版本」。若将来需要，加一个 `formatVersion` 参数即可。

---

## 四、导出流程设计

### 4.1 流程图

```
[设置页新增「备份与恢复」卡片] ── 点「导出备份」
        │
        ▼ ① 主线程：读数据
  getSemesters()  ·  coursesOf(每个学期)  ·  BellSchedules.copyOf(getBellSchedules())
  getActiveSemester().id()  ·  OffsetDateTime.now()
        │   （失败：UI 上不可能失败；见 §4.4 的一致性分析）
        ▼ ② 主线程：序列化
  BackupCodec.write(...)  →  String（几 KB）
        │
        ▼ ③ 选位置
  Intent(ACTION_CREATE_DOCUMENT) + CATEGORY_OPENABLE
     setType("application/json")
     putExtra(EXTRA_TITLE, "weihe-backup-20261009-2132.json")
     startActivityForResult(..., REQUEST_EXPORT_BACKUP)
        │
   ┌────┴─────────────────┐
   │ resultCode != OK      │──► 用户取消：什么都不做，不写任何键
   │ 或 data == null       │
   └────┬─────────────────┘
        │ RESULT_OK + uri
        ▼ ④ 后台线程：写文件
  openOutputStream(uri, "wt") → write(UTF-8 bytes) → flush → close
        │
   ┌────┴───────────────────────────────┐
   │ 成功                                │ IOException（含存储空间不足）
   ▼                                     ▼
 ⑤ Toast：                            UiKit.message("导出失败", 原因 + 建议)
   「已导出 2 个学期 · 23 门课程 ·
     3 张作息表 → weihe-backup-….json」
```

**步骤 ① 与 ② 的顺序是关键**：先一次性把数据读成内存快照，再序列化，
**不在持有仓库引用的情况下做文件 I/O**。

### 4.2 关键设计点

1. **入口位置**：`SettingsActivity` 新增第 6 张卡片。**不新增 Activity**——与已有的「批量导入课表」卡片同级，
   且不触碰 `AndroidManifest.xml`（无需注册、无需权限）。
2. **request code**：新增 `REQUEST_EXPORT_BACKUP`，与现有 `REQUEST_IMPORT_TIMETABLE = 3001`（`:48`）并列，
   在已有的 `onActivityResult:81-90` 里加分支。**用 `startActivityForResult`**——
   项目关闭了 AndroidX（`gradle.properties` 中 `android.useAndroidX=false`），
   没有 `ActivityResultContracts`/`registerForActivityResult` 可用；现有导入流程也是这么写的。
3. **MIME 与文件名**：`setType("application/json")` + `EXTRA_TITLE` 给默认名。
   现有导入用了 `setType("*/*")` + 宽松的 `EXTRA_MIME_TYPES`（`:394-400`），那是因为教务系统的 `.xls`
   MIME 不可靠；我们的备份是自己的 JSON，可以严格一些。
4. **文件写出**：`openOutputStream(uri, "wt")`（`"wt"` = 截断写入，避免覆盖旧文件时残留尾部字节）。
5. **UTF-8**：显式 `getBytes(StandardCharsets.UTF_8)`。**不写 BOM**——本项目在 AAPT 上踩过 BOM 的坑。
6. **文件名**：只含日期时间，**不含教师/课程等个人信息**。用 `OffsetDateTime.now()`
   （`java.time` 在 `minSdk 26` 上可用）。

### 4.3 失败与异常处理

| 情况 | 处理 |
|---|---|
| 用户取消选择 | `resultCode != RESULT_OK` → 直接返回，**不写任何键、不显示错误**（与现有导入一致） |
| `data.getData()` 为 null | 同上，静默返回 |
| `openOutputStream` 返回 null | 报「无法写入所选位置」 |
| `IOException`（含 **ENOSPC 存储空间不足**） | 报「写入失败：<原因>」。SAF 无法可靠预检剩余空间，只能捕获异常；提示里建议换一个位置 |
| 序列化后字节数异常 | 若 > 一个上限（如 §5.6 的 2 MB），仍然照常写出（导出不做输入限制），但记入摘要 |
| 写入中途失败 | **只影响目标文件，不影响 App 数据**——导出是纯只读 + 写外部文件，不碰 `SharedPreferences` |

### 4.4 是否可能读到不一致的数据

逐项核对读路径：

| 读取 | 返回 | 一致性 |
|---|---|---|
| `getSemesters():106-124` | `new ArrayList<>(snapshot)` ——**浅拷贝**；`Semester` 不可变 | ✅ 安全 |
| `coursesOf(当前学期):454-455` | 走 `getCourses()` → `copyOf(snapshot)`（`:411-417` 逐条 `course.copy()`） | ✅ **深拷贝** |
| `coursesOf(其它学期):457-458` | 直接读存储并 `readCourses` 解析出**新列表** | ✅ 安全 |
| `getActiveSemester():127-137` | 从上面的列表里取 | ✅ 安全 |
| **`getBellSchedules():580-598`** | **返回缓存列表本体**（**H1**），且播种分支与缓存分支返回的对象还不一致 | ⚠️ **风险点** |

**对策（最小改动）**：导出时**先 `BellSchedules.copyOf(repository.getBellSchedules())`** 取一份自己的副本，
再基于副本构建载荷。这既绕开 H1，也**不需要修 `CourseRepository`**。
（根本修法是让 `getBellSchedules()` 与 `getCourses()` 一样返回副本，但那是独立缺陷、不在本轮范围——见 §10-3。）

**并发方面**：`cachedCourses`/`cachedSchedules` 虽是 `volatile`，但仓库整体**没有为并发设计**——
复合操作不是原子的。因此第 ① 步的读取**必须在主线程完成**（所有 UI 与存储操作本来都在主线程），
第 ④ 步的文件 I/O 才放到后台线程。这样既不会跨线程访问仓库，也不会阻塞 UI。

### 4.5 是否需要后台线程

| 阶段 | 耗时量级 | 线程 |
|---|---|---|
| ① 读数据 + ② 序列化 | 微秒~毫秒级（23 门课的 JSON 只有几 KB） | **主线程**（必须，见 §4.4） |
| ③ 选位置 | 用户交互 | 主线程 |
| ④ `openOutputStream` + 写入 + `flush` + `close` | **不可预测**——SAF 背后的 provider 可能是云盘或慢速存储 | **后台线程** |

**结论：序列化不必上后台线程（反而必须留在主线程以保证一致性），但文件写入必须上后台线程。**
直接复用 `SettingsActivity.importTimetable:409-430` 的 `new Thread` + `runOnUiThread` +
`isFinishing()` 判断模式，不引入 `ExecutorService` 或新依赖。

### 4.6 隐私提示

导出前（或首次导出时）应明确告知：

> 「备份文件是**明文 JSON**，包含你的课程、**教师姓名**和**上课地点**。
> 请存放在你信任的位置，不要随手上传到公开网盘。」

**并且**：任何日志都不得输出备份内容；当前生产代码没有任何 `Log` 调用，实现时也不要引入。
不上传云端、无服务器、无账号——这可从 `AndroidManifest.xml` 无任何权限（含无 `INTERNET`）被客观验证。

---

## 五、恢复流程与数据保护机制

### 5.1 流程图

```
[「从备份恢复」] → ACTION_OPEN_DOCUMENT（复用 openImportPicker 的写法）
        │
   ┌────┴──────────────┐
   │ 用户取消           │──► 直接返回，数据零改动
   └────┬──────────────┘
        ▼ ① 后台线程读取（带上限，见 §5.6）
   openInputStream → 读字节 → UTF-8 String → close
        │ 空 / 超限 / 读失败 ──► 拒绝，数据零改动
        ▼ ② 解析（MiniJson.parse）
        │ 返回 null（非法 JSON）──► 拒绝
        ▼ ③ 校验 format / formatVersion
        │ 不是本 App 的备份 / 版本过新 ──► 拒绝
        ▼ ④ 解析到独立临时对象 Document（此时仍未碰 SharedPreferences 一个键）
        ▼ ⑤ 校验 + 修正（§5.4 / §5.5）
        │ 任何硬性失败 ──► 列出前几条原因，拒绝
        ▼ ⑥ 恢复预览确认框（§5.2）
        │ 用户取消 ──► 直接返回，数据零改动
        ▼ ⑦ 主线程：一个 Editor + 一次 commit()（§5.3）
        │ 失败 ──► 报错；数据未变
        ▼ ⑧ repository.reload() + 刷新本页 + 读回校验数量
        ▼ ⑨ Toast：已恢复 N 个学期 · M 门课程 · K 张作息表（+ 可选「撤销本次恢复」）
```

### 5.2 恢复预览（第 ⑥ 步）

确认框内容**全部取自已解析的临时对象**，不读当前数据、不写任何键：

```
备份时间：2026-10-09 21:32
内容：2 个学期 · 23 门课程 · 3 张作息表
将影响：当前 2 个学期 · 23 门课程 · 3 张作息表 会被覆盖
（若有修正项）3 门课程指向的作息表不在备份中，将改为按地点自动匹配
[取消]  [覆盖恢复]
```

**按钮文案用「覆盖恢复」而不是「恢复」**，与已有的「覆盖导入」（`SettingsActivity:493`）保持同一套措辞习惯。

### 5.3 原子性：现有机制的限制与最小改动方案

**限制（逐方法实测 Editor 用量）**：

| 方法 | 位置 | Editor 数 |
|---|---|---|
| `saveCourses` | `:917-920` → `:462-468` / `:928-941` | 1 |
| `saveBellSchedules` | `:703-709` | 1 |
| `saveSemesters` | `:347-353` | 1 |
| `setActiveSemester` | `:145-153` | 1 |
| **`updateSemester`** | `:232` + `:236-239` | **2** |
| **`deleteSemester`** | `:265-268` + `:269` + `:273` | **2~3** |
| 全部写入 | 10 处 `apply()`、**0 处 `commit()`** | — |

一次完整恢复需要写 `semesters`、`active_semester`、`bell_schedules`、
N 个 `courses:<id>`、N 个 `semester_start:<id>` = **2N+3 个键**。
按现有公开方法串行调用就是 **2N+3 次独立提交**——中间被杀会留下混合状态，
而且**现有兜底逻辑一条都不会触发**（`getSemesters` 只在列表为空时兜底；`loadCourses` 只在键为 `null` 时播种，
所以「恢复了学期却漏了课程」会让那一学期**永久为空**）。

**因此：绝不能把「先解析文件，再依次调用若干保存方法」当作原子恢复。**

**最小改动方案**：`SharedPreferences.Editor` 的公开语义是「同一 Editor 的全部改动作为一次事务生效」，
而项目里**已经有这个用法的先例**——`migrateLegacyStorage:303-320`：

```java
SharedPreferences.Editor editor = preferences.edit()
        .putString(KEY_SEMESTERS, SemesterJson.write(semesters))
        .putLong(KEY_ACTIVE_SEMESTER, first.id());
if (legacyCourses != null) {
    editor.putString(coursesKeyFor(first.id()), legacyCourses).remove(KEY_COURSES);
}
...
editor.apply();     // ← 一次事务
```

在 `CourseRepository` 新增**一个**方法（建议 `restoreFrom(BackupCodec.Document)`），只开**一个** Editor：

1. `getAll().keySet()` 算出要删除的过期键：所有 `courses:*` 与 `semester_start:*`（随后按新数据重写需要的那些）、
   遗留的 `courses`/`semester_start`；**保留 `courses_backup`**（它与本次恢复无关，不该被恢复流程抹掉）。
2. 写入新数据：`semesters`、`active_semester`、`bell_schedules`、
   每个学期的 `courses:<id>` **与 `semester_start:<id>`**。
   ⚠️ `semester_start:<id>` **必须显式写**——`getSemesterStart:877-888` 只读这个键，不读学期记录（见 §5.9）。
3. 在**同一个 Editor** 里写入恢复前快照（§5.7）。
4. `commit()` **一次**。

**为什么用 `commit()` 而不是 `apply()`**：全项目目前 10 处 `apply()`、0 处 `commit()`。
`apply()` 异步落盘、没有返回值；恢复是「用户明确点击、体量只有几十 KB」的不可重做操作，
应当用 `commit()` 拿到**同步的成功/失败**，才能诚实地告诉用户结果。
这与仓库其余写入继续用 `apply()` 并不矛盾：那些是可重做的增量编辑。

**诚实说明**：一次 `commit()` 保证「同一 Editor 的改动全部生效」。
至于**磁盘文件层面**在写入途中被强杀会留下旧内容还是新内容，属平台实现细节，**本轮未验证（§10-2）**。
设计**不依赖**这一层，只依赖 Editor 级原子性 + §5.7 的快照。

### 5.4 校验规则

**硬性失败（拒绝整个文件，当前数据保持原样）**

| # | 规则 | 依据 |
|---|---|---|
| V1 | 非空、合法 JSON、能解析成对象 | `MiniJson.parse:40-56` 非法文本返回 `null` |
| V2 | `format == "weihe-schedule-backup"` | 防止选错文件类型 |
| V3 | `formatVersion` 可解析且 ≤ 支持上限 | §3.5 |
| V4 | `semesters` 非空；每项 `id > 0` 且**全局唯一**；`start` 可解析 | ≥1 个学期是不变式 |
| V5 | 同学期内课程 `id` 互不重复 | `CourseList.upsert:50-59` 按 id 匹配，重复会让一门课静默吃掉另一门 |
| V6 | `schedules` 非空；`name` 去空白非空且**不重名** | `indexOfSchedule:776-790` 按名字定位，重名会让编辑/删除指向错误对象 |
| V7 | 每张作息表 `starts.length == ends.length == 15`，每个值 ∈ [0,1799] | `BellSchedule.PERIODS:23`、`MAX_MINUTE:34`；见 **H2** |
| V8 | 每门课：`day ∈ 1..7`、`1 ≤ startPeriod ≤ endPeriod ≤ 15`、`1 ≤ startWeek ≤ endWeek ≤ 18`、`parity ∈ {0,1,2}` | `SEMESTER_WEEKS=18`、`PERIODS_PER_DAY=15` |
| V9 | 存在可用的当前学期（`activeSemesterId` 命中，否则 `semesters[0]`） | `getActiveSemester` 的既有回落 |

**可修正项（不拒绝，修正后计入预览）**

| 情况 | 处理 | 依据 |
|---|---|---|
| 课程 `id` 为 0 或重复 | `CourseList.nextId` 重新分配 | `ensureId:99-104` |
| `scheduleName` 指向备份中不存在的作息表 | **置空**（改为按地点匹配）并统计条数 | 与 `clearScheduleReferences:853-869`（删作息表时清引用）语义一致，是 App 已有的做法 |
| `keywords` 缺失 | 视为 `[]` | `BellScheduleJson:87-94` |
| 学期 `name` 缺失 | 视为 `""`（按日期推导标签） | `Semester.label()` |
| `activeSemesterId` 对不上 | 回落 `semesters[0]` | `getActiveSemester` |

**刻意不校验**：`color` 是否属于 `Palette.COURSE_COLORS`。`Course.color` 是自由 int，界面照它画；
因一个不常见的颜色拒绝整份备份没有意义。

### 5.5 覆盖 vs 合并

| | 整库覆盖（**首版推荐**） | 合并 |
|---|---|---|
| 语义 | 备份内容替换当前全部课程/学期/作息表 | 并入当前数据 |
| 幂等性 | ✅ **天然幂等**：同一文件导入两次结果完全相同 | ❌ 需要规则：同名学期算同一个吗？同 id 不同内容取谁？同名作息表？同名课程？ |
| 原子性 | ✅ 单事务可完成 | ❌ 「先算差异再写」中途失败难以界定 |
| 用户预期 | 清晰（预览框明写覆盖范围） | 模糊 |
| 复杂度 | 低 | 高（需要冲突解决 UI） |

**推荐整库覆盖。** 决定性理由是**幂等**——它直接消除了第 8 条要求里
「重复点击恢复后生成重复课程或错误关联」这一整类问题：覆盖式恢复重复执行永远得到同一结果，
**不需要任何去重规则**。合并列为后续可选项，**本轮不做承诺**。

**重复点击的额外保险**：`UiKit.dialog` 的按钮点击后会关闭对话框，同一按钮无法连点两次；
另在恢复流程入口加一个 `boolean restoring` 重入标志（后台线程路径的廉价保险）。

### 5.6 异常输入（空文件、损坏文件、超大文件）

| 输入 | 处理 |
|---|---|
| **空文件** | 读到的字符串 `trim()` 为空 → 拒绝：「文件是空的」 |
| **损坏 JSON** | `MiniJson.parse` 返回 `null` → 拒绝：「文件不是合法的 JSON」 |
| **合法 JSON 但结构不符** | `format` 不匹配或必需段缺失 → 拒绝，指明缺哪一段 |
| **超大文件** | **读取时设上限**：读取字节数超过 `MAX_BACKUP_BYTES` 即中止并拒绝，**不把整个文件读进内存**。建议上限 **2 MB**——真实备份只有几 KB 量级（23 门课的 JSON），2 MB 已远超任何合法备份。具体数值见 §10-5 |
| **zip / xls 等二进制** | 通常落在「非法 JSON」分支；若恰好解析成功也过不了 `format` |
| 读取过程中 `IOException` | 拒绝：「无法读取所选文件」 |

### 5.7 恢复前快照与回滚

**三层保护，按发生顺序：**

| 层 | 时机 | 机制 | 失败后果 |
|---|---|---|---|
| **L1 不动数据** | 选文件 → 解析 → 校验 → 预览 | 全程只操作临时对象，**一个 `preferences.edit()` 都不调用** | 任何失败（空/非法/版本不支持/校验不过/用户取消）都**不可能**影响现有数据 |
| **L2 恢复前快照** | 与 L3 同一个 Editor | 用 §3 的同一个序列化器把**当前**数据写成备份载荷，存进新键 `restore_snapshot` | 见下 |
| **L3 单事务提交** | 写新数据 | 一个 Editor + 一次 `commit()` | `commit()` 返回 `false` 或抛异常 → 报错；因为快照与新数据在**同一个 Editor** 里，未提交即**什么都没变** |

**为什么 L2 与 L3 放在同一个 Editor**：如果分两次提交（先写快照、再写数据），
两次之间被杀虽然数据未变，但会留下一个「陈旧的快照」误导后续撤销。
放在一个事务里则「要么都生效、要么都不生效」，语义更干净，也少一次磁盘提交。

**快照的用途与生命周期**：
- 恢复成功 → 快照保留，作为**一次性「撤销本次恢复」**的入口（它本身就是一份合法备份载荷，
  所以撤销路径**不需要新的写入代码**，直接复用同一个恢复流程）。
- 下一次恢复 → **覆盖**快照（只保留最近一次）。
- `restore_snapshot` **不参与导出**（它是内部产物，理由同 `courses_backup`）。

### 5.8 恢复后验证（第 ⑧ 步）

不能写完就宣布成功。`CourseRepository` 的读取路径有**多处静默降级**
（`readCourses:475-491` 丢坏条目、`BellScheduleJson:98-102` 退回标准作息、`SemesterJson:41-43` 跳过坏学期）。
因此恢复后必须：

1. `repository.reload()`（丢弃三个缓存，`:531-535`）；
2. 读回 `getSemesters()` / `getCourses()` / `getBellSchedules()`，
   **比对数量与临时对象一致**；不一致 → 报告「恢复未完全生效」并提示可用快照撤销；
3. 只有比对通过才显示「恢复成功」。

**刷新**：5/7 个 Activity 在 `onResume` 里 `reload()`（`MainActivity:111`、`ScheduleListActivity:57`、
`SemesterListActivity:61`、`SettingsActivity:75`、`WeekGridActivity:147`），
其余页面返回时会自动刷新；发起恢复的 `SettingsActivity` 自己 `reload()` + 重绘即可。

### 5.9 顺带修掉一个既有不一致（设计上的正面副作用）

由代码推得（**本轮未执行验证**，见 §10-1）：`addSemester:175-191` 与 `setActiveSemester:145-153`
**都不写** `semester_start:<新id>`，而 `getSemesterStart:877-888` **只读这个键**：

```
SemesterListActivity:293  addSemester(monday, name)   → 只写 semesters
SemesterListActivity:298  setActiveSemester(created)  → 只写 active_semester
        ↓ 回首页 onResume（MainActivity:114/765）
getSemesterStart() → 读到 null → defaultSemesterStart(now) → setSemesterStart(默认)
        → updateSemester(startingOn(默认)) → 改写 semesters[].start
```

即新建学期时填的日期可能被默认锚点替换。既有两个相关测试都绕开了这条路径
（`eachSemesterKeepsItsOwnStartDate:523` 切换后**显式调用了 `setSemesterStart`**；
`switchingSemestersSurvivesAReopen:538` 断言的是 `getActiveSemester().start()` 而非 `getSemesterStart()`）。

**对恢复的意义**：恢复流程**必须显式写 `semester_start:<id>`**，所以**恢复出来的数据不会带这个缺口**。
修 `addSemester` 属于另一个任务，本轮不动。

---

## 六、可能的数据丢失风险及解决方案

| # | 风险 | 触发条件 | 严重度 | 解决方案 |
|---|---|---|---|---|
| R1 | **部分恢复**：写了一半被杀 | 按现有 API 串行保存 | **高** | §5.3 单 Editor + 单 `commit()` |
| R2 | **错误文件覆盖课表** | 选错文件 | **高** | `format`+`formatVersion` 双校验；写入前预览让用户看到「课程 0 门」这类异常；「覆盖恢复」措辞；L2 快照 + 撤销 |
| R3 | **误操作无退路** | 用户点错确认 | 中 | L2 快照与「撤销本次恢复」 |
| R4 | **恢复后静默降级** | 载荷某处仍不合法，读取路径静默丢弃 | 中 | §5.8 读回比对数量，不一致就不报成功 |
| R5 | 导出读到被改动的作息表列表（**H1**） | 导出持有 `getBellSchedules()` 返回的缓存本体 | 中 | 导出前 `BellSchedules.copyOf(...)`（§4.4），**不改 `CourseRepository`** |
| R6 | 恢复的作息表被「占位表替换」再次整表覆盖（**H2**） | 备份里的作息表命中旧版签名 | 中 | V7 已限制长度/取值范围；另建议在预览里提示。**根治要修 H2，属独立任务** |
| R7 | 超大/恶意文件 OOM | 读到超大 JSON | 中 | §5.6 读取上限 |
| R8 | 重复点击造成重复数据 | 连点确认 | 低 | 覆盖语义天然幂等 + 对话框即关 + 重入标志 |
| R9 | 快照键使存储体积翻倍 | 快照保留期间 | 低 | 只保留最近一次；撤销/放弃后清除；数值待测（§10-7） |
| R10 | 与 OS 云备份（`allowBackup="true"`，`AndroidManifest:4`）交互 | 换机时系统恢复 + 用户再用文件恢复 | 低 | 二者独立；文件恢复是覆盖式的，结果可预期。是否要限制云备份内容列为后续议题，**本轮不改 Manifest** |

---

## 七、测试用例清单

**全部优先落在纯 JVM 上**——`FakePreferences` 已实现 `getAll()`/`edit`/`commit`（`:37-39`、`:161-164`），
`CourseRepository.useStoreForNextInstance` 提供测试缝（`:87-89`），
且 `build.gradle:31` 的 `unitTests.returnDefaultValues = false` 强制被测量的代码**不得触碰任何 Android 类**。
这正好与本方案的「编解码零 Android 依赖」设计互相印证。

### A. 导出（`BackupCodecTest`）

| # | 用例 | 断言 |
|---|---|---|
| A1 | 正常导出 | 生成的 JSON 含 `format`/`formatVersion`/`exportedAt`/`semesters`/`schedules`；学期数、课程数、作息表数正确 |
| A2 | 字段保真 | 每门课的 12 个字段与源对象逐项相同 |
| A3 | 空课表 | 0 门课的学期仍被写出，不被丢弃 |
| A4 | 多学期 | 每个学期的课程归属正确，不串 |
| A5 | 自定义作息表 | 名字、关键词、30 个时间值完整 |
| A6 | 单双周 | `parity` 0/1/2 三种都保真 |
| A7 | 活动学期 | `activeSemesterId` 等于当前学期 id |
| A8 | 特殊字符 | 教师/地点含引号、换行、反斜杠、全角标点、emoji 不失真 |
| A9 | 可重复 | 同一数据连续导出两次得到**相同字节**（`MiniJson.object()` 是 `LinkedHashMap`） |
| A10 | 不导出内部产物 | 输出中**不含** `courses_backup`、`timeRange`、`courses`/`semester_start` 遗留键 |
| A11 | 派生值不写出 | 修改内存里某门课的 `timeRange` 后导出，输出不受影响 |

### B. 恢复 —— 正常路径（追加到现有 `CourseRepositoryStorageTest`，复用其夹具）

| # | 用例 | 断言 |
|---|---|---|
| B1 | 导出后立即恢复 | 学期/课程/作息表数量与**每门课 12 个字段**逐项一致 |
| B2 | 恢复后重开仓库（对应要求「恢复成功后重新创建仓库仍能读取正确数据」） | `repositoryFor(同一 FakePreferences)` 读出的数据与恢复后一致 |
| B3 | 多学期往返 | `courses:<id>` 与嵌套结构对应正确 |
| B4 | 空课表学期往返 | 仍是 0 门课、学期仍存在 |
| B5 | 自定义作息表往返 | 30 个时间值与关键词保真 |
| B6 | 课程→作息表关联 | 恢复后 `scheduleFor(course).name()` 与备份中的 `scheduleName` 一致 |
| B7 | 恢复不触发样例课播种 | 恢复后课程数等于备份中的数量，不出现 9 门样例课（`loadCourses:425-432` 不被触发） |

### C. 恢复 —— 拒绝与容错

| # | 用例 | 断言 |
|---|---|---|
| C1 | **空文件** | 拒绝；`FakePreferences.snapshot()` 与恢复前**逐键相同** |
| C2 | **损坏 JSON** | 拒绝；数据未变 |
| C3 | 合法 JSON 但 `format` 不符 | 拒绝 |
| C4 | **缺必填字段**（`semesters` / `schedules` 缺失或为空） | 逐条拒绝 |
| C5 | **无效字段类型**（`id` 是字符串、`starts` 是对象、`day` 是数组） | 拒绝 |
| C6 | **不支持的版本**（`formatVersion = 99`） | 拒绝；当前数据完好 |
| C7 | 缺 `formatVersion` | 按 1 处理，可恢复 |
| C8 | **重复学期 id** | 拒绝 |
| C9 | **重复课程 id（同学期）** | 按修正项重分配并计数，不拒绝 |
| C10 | **重复学期名称** | 允许（学期名可重复，`name` 只是标签）；断言恢复后两个学期都在 |
| C11 | **重复作息表名称** | 拒绝（V6） |
| C12 | **无效课程引用**：`scheduleName` 指向不存在的作息表 | 置空 + 计入预览；恢复后按地点解析 |
| C13 | **无效节次/周次**：`day=9`、`startPeriod=0`、`endPeriod=99`、`startWeek>endWeek`、`parity=7` | 逐条拒绝并给出定位 |
| C14 | 作息表 `starts` 长度 ≠ 15 或与 `ends` 不等 | 拒绝（**不**沿用内部的静默退化） |
| C15 | 超大文件 | 拒绝且未读完整个文件 |

### D. 失败回滚（对应要求「恢复失败时原数据保持可用」）

| # | 用例 | 断言 |
|---|---|---|
| D1 | **恢复确认前不修改原数据** | C1..C15 每种坏文件各跑一次，恢复前 `snapshot()` 与恢复后**逐键相同**（含未新增任何键） |
| D2 | `commit()` 返回 false | 给 `FakePreferences` 加可注入的「提交失败」模式；断言数据未变、无 `restore_snapshot` 残留 |
| D3 | **用户取消文件选择** | `resultCode != RESULT_OK` 路径不产生任何 `edit()`（可用计数器断言） |
| D4 | **用户取消恢复确认** | 同上 |
| D5 | 快照与新数据同事务 | 提交失败时**两者都不存在**；成功时**两者都在** |
| D6 | 撤销恢复 | 用快照还原到恢复前状态，逐字段一致 |

### E. 幂等与重复

| # | 用例 | 断言 |
|---|---|---|
| E1 | 同一文件恢复两次 | 结果与恢复一次完全相同 |
| E2 | 覆盖语义 | 恢复一个课程更少的备份后，原有课程**确实被删除**（不是合并保留） |
| E3 | 恢复清理过期键 | 备份中不存在的旧学期，其 `courses:<旧id>` 与 `semester_start:<旧id>` 被删除 |
| E4 | 保留 `courses_backup` | 恢复不清除该键 |

### F. 需要 Android 的集成测试（单独规划，**不新建 `androidTest` 源集、不引入新依赖**）

| # | 用例 | 方式 |
|---|---|---|
| F1 | SAF 选位置并写出文件 | 手工/脚本驱动系统文件选择器（项目已有 `tools/tap-ui.ps1`） |
| F2 | 导出的文件在文本编辑器中可读、UTF-8 无 BOM | 手工检查 |
| F3 | 预览框数字与备份内容一致 | 手工截屏核对 |
| F4 | 取消选文件后列表无变化 | 手工 |
| F5 | 大字体/深色模式下对话框不截断 | 手工（参考 `ARCHITECTURE.md` 第 8 条踩坑记录） |

---

## 八、预计需要新增或修改的文件列表

**刻意压到最小**：**1 个新生产类 + 2 个改动文件 + 1 个新测试类 + 1 个改动测试类**。

| 文件 | 动作 | 内容 | 预估规模 |
|---|---|---|---|
| `app/src/main/java/com/weihe/schedule/data/BackupCodec.java` | **新增** | 唯一的编解码 + 校验类。含嵌套的 `Document`（解析结果）与 `Problem`（校验问题）。**零 Android 依赖**（只依赖 `MiniJson` 与 `model`），因此可在 JVM 上完整测试 | 约 300~350 行 |
| `app/src/main/java/com/weihe/schedule/data/CourseRepository.java` | 修改 | 新增**一个**方法 `restoreFrom(BackupCodec.Document)`：单 Editor + 单 `commit()` + 快照。**不改动任何现有方法** | +约 45 行 |
| `app/src/main/java/com/weihe/schedule/SettingsActivity.java` | 修改 | 新增「备份与恢复」卡片、两个 request code、两个 `onActivityResult` 分支、导出/恢复两个流程、预览与结果反馈 | +约 180 行 |
| `app/src/test/java/com/weihe/schedule/data/BackupCodecTest.java` | **新增** | §7 的 A 组 + C 组的纯编解码/校验部分 | 约 350 行 |
| `app/src/test/java/com/weihe/schedule/data/CourseRepositoryStorageTest.java` | 修改 | 追加 §7 的 B/D/E 组（复用该文件已有的 `repositoryFor`/`storeCourses`/`describe` 夹具） | +约 250 行 |
| `app/src/test/java/com/weihe/schedule/data/FakePreferences.java` | 可选修改 | 加「提交失败注入」与「写次数计数」，供 D2/D3 使用。纯测试辅助，不影响生产 | +约 20 行 |
| `PROJECT_ARCHITECTURE.md`、`ARCHITECTURE.md` | 收尾修改 | 存储章节补充新键与备份格式；回归清单加一条「导出→恢复→数据一致」 | 文档 |

**明确不新增**：不新增 Activity（复用 `SettingsActivity`）、不新增 `androidTest` 源集、
不新增 Gradle 依赖、不新增权限、不改 `MiniJson`、不改 `AndroidManifest.xml`、不改存储格式。

> 关于「备份 UI 放 `SettingsActivity` 还是新建 Activity」：选择前者，因为它与已有的
> 「批量导入课表」卡片同级（`:201`），且不触碰 Manifest。代价是 `SettingsActivity` 会从 523 行涨到约 700 行。
> 若你更在意单文件长度，可改为新建 `BackupActivity`（需在 Manifest 注册），这是**唯一**需要你拍板的结构选择。

---

## 九、分阶段实施步骤与验收标准

通用验收（每阶段都要过）：`testDebugUnitTest` 全绿且**测试数只增不减**（当前 **307**，20 个测试类）·
`assembleDebug` 成功 · `lintDebug` 为 `No issues found.` · `git diff` 只含本阶段声明的文件。

| 阶段 | 内容 | 触碰 Android | 验收标准 |
|---|---|---|---|
| **1. 编解码 + 校验（纯逻辑）** | 新增 `BackupCodec`：写出、解析、V1..V9 校验、修正项。零 Android、零 UI | ❌ | §7 A 组 + C 组全绿；新类**不 import 任何 `android.*`**（`returnDefaultValues=false` 会让任何 Android 调用在测试中直接失败，等于编译+运行双重保证）；测试数 +约 30 |
| **2. 单事务恢复（数据层）** | `CourseRepository.restoreFrom(...)`：单 Editor、单 `commit()`、`restore_snapshot` 快照、`reload()` | 仅 `SharedPreferences`（已有测试缝） | §7 B/D/E 组全绿；`FakePreferences` 断言**只发生一次提交**；断言恢复前 `snapshot()` 在失败路径下逐键不变；测试数再 +约 18 |
| **3. 导出 UI** | `SettingsActivity` 新增卡片 + `ACTION_CREATE_DOCUMENT` + 后台写入 + 成功/失败反馈 | ✅ | 真机导出成功；文件是合法 JSON、UTF-8 无 BOM（F1/F2）；**Manifest 仍无任何权限**（`git diff` 可验证）；取消选择不改数据 |
| **4. 恢复 UI** | `ACTION_OPEN_DOCUMENT` → 解析 → 校验 → **预览确认框** → 恢复 → 读回校验 → 反馈 | ✅ | F3/F4 通过；坏文件给出可读原因；预览数字与备份一致；§5.8 的读回比对生效 |
| **5. 撤销本次恢复** | 快照的一次性撤销入口 | ✅ | §7 D6 通过；撤销后再撤销无效 |
| **6. 文档** | 更新 `PROJECT_ARCHITECTURE.md`（存储章节）与 `ARCHITECTURE.md`（回归清单，从 HEAD 取回后编辑） | ❌ | 文档与实现一致 |
| **7.**（**待评估，非承诺**） | 合并模式、增量备份、加密、定时自动备份 | — | 需先确认用户需求 |

**推荐顺序的理由**：阶段 1、2 完全可在 JVM 上验证，风险最低而价值最高（阶段 2 直接消除 R1——
当前最严重的数据风险）；阶段 3、4 才需要真机；阶段 5 复用阶段 2 的机制，增量很小。

---

## 十、尚未能从源码确认的问题

| # | 问题 | 本轮掌握的证据 | 需要怎么确认 |
|---|---|---|---|
| **1** | **`addSemester` 未写 `semester_start:<新id>`，新建学期后首次 `getSemesterStart()` 可能用默认锚点覆盖用户填的日期** | §5.9 的完整调用链（`:175-191`、`:145-153`、`:877-905`、`SemesterListActivity:293,298`）；既有两个相关测试都绕开了该路径 | 加一条临时用例：`addSemester(2027-02-22)` → `setActiveSemester` → **不调用** `setSemesterStart` → 断言 `getSemesterStart()`。**本轮受「只在根目录生成一个文档」限制未执行** |
| **2** | 单次 `commit()` 在写入途中被强杀时，磁盘上是旧内容还是新内容 | 只确认了 Editor 级原子性（公开语义） | 需要设备上的崩溃注入或查证平台实现；设计已通过 §5.7 快照规避对该层的依赖 |
| **3** | **H1**：`getBellSchedules()` 返回缓存本体，与 `getCourses()` 返回深拷贝不一致；且播种分支返回局部列表、缓存分支返回缓存本体，**同一方法两种行为** | `:580-598` 对比 `:396-409`、`:106-124` | 已在上一轮执行验证（见 §0.2）。**设计通过导出前 `copyOf` 规避**，是否根治由你决定 |
| **4** | **H2**：占位作息表替换会覆盖用户自建作息表 | `:612-626`，上一轮执行验证：`[实验楼作息, 我的作息表]` → 读取后只剩 3 张预设 | 同上。**与恢复的交互见 R6** |
| **5** | 真实备份文件的体积量级（决定 §5.6 的读取上限取值） | 本轮**未测量** | 阶段 2 时执行 `adb shell run-as com.weihe.schedule cat shared_prefs/weihe_schedule.xml` 量一次（本项目此前用过这条命令） |
| **6** | 真机上 `SharedPreferences.getAll()` 是否确实返回全部 `courses:*`/`semester_start:*` 键 | 标准 API；`FakePreferences:37-39` 已实现且返回副本 | 阶段 2 前用上面同一条 `adb` 命令对一次 XML 里的键名 |
| **7** | `restore_snapshot` 快照保留期间的存储体积翻倍是否可接受 | 未测量 | 同 #5 |
| **8** | `MiniJson` 未提供 `optObject(Map,key)` 之类的嵌套取值助手 | 公开 API 实测只有 `parse`/`parseArray`/`objectAt`/`optLong`/`optInt`/`optString`/`optArray`/`object`/`array`/`write` | 本格式用 `optArray` + 逐项 `instanceof Map` 即可，**无需改 `MiniJson`**；若实施中发现可读性差，再加一个**纯新增**助手 |
| **9** | `Course` 是否还有本轮未注意到的持久化字段 | 已逐个核对 `toJson:973-989` 的 12 个 key 与 `Course.java` 的公开字段 | 实现阶段以 `toJson` 为准；若将来给 `Course` 加字段，**必须同时更新备份格式并评估 `formatVersion`** |
| **10** | 低风险遗留：`readCourses:475` 的 `length() > 2` 阈值会把 `"[ ]"` 误判为损坏、把 `"{}"` 漏判 | 上一轮执行验证 | 与本方案无直接冲突（备份走自己的校验），列为独立待办 |

---

## 附：本轮未做的事（明确划界）

| 不做 | 原因 |
|---|---|
| 修改任何生产代码、测试代码、Gradle 配置、依赖 | 任务明确限制 |
| 新增 `DATA_BACKUP_RESTORE_PLAN.md` 以外的文件；删除任何现有文件 | 任务明确限制 |
| 执行构建、测试或真机验证 | 本轮为设计任务；未执行项已逐条标 **待确认** |
| 清理工作区、`git reset`、`git checkout`、覆盖用户文件 | 工作区含 8 项既有改动，已原样保留并记录指纹 |
| 修复 **H1**（`getBellSchedules` 泄漏缓存）与 **H2**（占位表整表替换） | 已按要求**单独标记、不擅自修复**；设计用最小代价规避 |
| 迁移 Kotlin/Compose/Room；引入网络、账号、云同步；引入新依赖 | 任务明确限制 |
| 实现合并式恢复、加密备份 | 复杂度与收益不成比例（§5.5）；列为后续待评估 |

---

**本文档只记录方案设计。文中所有「导出」「恢复」「原子」等表述均指设计意图，
当前代码中不存在任何导出、备份或恢复能力，也没有为它们新增任何测试。**
