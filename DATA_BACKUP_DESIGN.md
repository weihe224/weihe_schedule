# DATA_BACKUP_DESIGN.md — 「拾课」数据导出、备份与恢复设计

> **本轮只做设计。** 未修改任何生产代码、测试代码、Gradle 配置或依赖；未新增除本文档以外的文件。
>
> **依据**：分支 `refactor/project-structure`，HEAD `efe5ace`，工作区含 7 项未提交改动（见 §0.1，全部原样保留）。
> 所有结论均标注 `文件:行号`。**本轮未执行任何构建、测试或真机操作**，凡未实际跑过的一律标注 **待确认**。

---

## 〇、执行前核查

### 0.1 Git 与工作区（原样保护，未清理）

```
分支   refactor/project-structure
HEAD   efe5ace docs: record the new package structure and the refactor that produced it

 D ARCHITECTURE.md                                           ← 开工前既有
 D ARCHITECTURE_REFACTOR_PLAN.md                             ← 开工前既有
 M app/src/test/.../importer/TimetableImportParserTest.java   ← 开工前既有
?? NEXT_OPTIMIZATION_ROADMAP.md                              ← 开工前既有
?? app/src/test/.../data/CourseRepositoryStorageTest.java    ← 开工前既有
?? app/src/test/.../importer/ExcelTimetableReaderTest.java   ← 开工前既有
?? app/src/test/.../importer/TimetableGridTest.java          ← 开工前既有
```

开工前已记录这 5 个文件的 MD5，收工时可逐一比对证明未被触碰。

### 0.2 本轮实际阅读的源码

`data/CourseRepository.java`（1073 行，全文）· `data/CourseList.java` · `data/MiniJson.java`（读写两侧）·
`data/SemesterJson.java` · `data/BellScheduleJson.java` · `model/Course.java` · `model/Semester.java` ·
`model/BellSchedule.java`（常量部分）· `model/ScheduleUtils.java`（常量部分）·
`AndroidManifest.xml` · 7 个 Activity 的 `onResume`/`reload` 调用点 ·
`test/.../CourseRepositoryTest.java`（学期起止日期相关用例）。

已读文档：`PROJECT_ARCHITECTURE.md`、`PROJECT_ANALYSIS.md`、`NEXT_OPTIMIZATION_ROADMAP.md`，
以及从 HEAD 取出的 `ARCHITECTURE.md`。**旧文档中的问题均按当前源码重新核对**，未直接沿用。

### 0.3 结论摘要

| 项 | 现状 |
|---|---|
| 现有导出/备份/恢复能力 | **完全没有**。全项目无 `ACTION_SEND`、无 `ACTION_CREATE_DOCUMENT` 写入用法 |
| 数据量 | 单一 `SharedPreferences` 文件，8 类键，全部由 `CourseRepository` 一处访问 |
| 权限 | **Manifest 未声明任何权限**（含无 `INTERNET`），这是本方案最好的起点 |
| 能否一次事务写完 | **能力具备但未被使用**：`migrateLegacyStorage:303-320` 已用「一个 Editor 多次 put + 一次 apply」；但公开的保存方法各自开 Editor，恢复若直接串起来就是 5 次以上独立提交 |
| 最大风险 | 恢复不是原子的 → 中途中断会留下「新学期 + 旧课程」这类半成品状态 |

---

## 一、当前数据存储结构与代码依据

### 1.1 唯一的存储介质

全项目只有一个 `SharedPreferences` 文件，且只有一个访问点：

```
CourseRepository.java:21   private static final String PREFS = "weihe_schedule";
CourseRepository.java:95   return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
```

已确认全项目**没有**第二处 `getSharedPreferences`、没有 `openFileOutput`、没有数据库、没有 `DataStore`、
没有任何 `ACTION_SEND`/`ACTION_CREATE_DOCUMENT` 写入。**没有账号、没有服务器、没有网络依赖。**

### 1.2 完整的键清单（实测，非文档推断）

`CourseRepository.java:22-31` 声明了 6 个常量键，另有 2 个按学期拼接的键：

| 键 | 类型 | 写入点 | 读取点 | 是否属于用户数据 |
|---|---|---|---|---|
| `courses` | String(JSON 数组) | 仅迁移时 `remove`（`:312`） | `:302`（迁移） | ❌ **旧版遗留键**，迁移后即删除 |
| `semester_start` | String(日期) | 仅迁移时 `remove`（`:316`） | `:292`（迁移） | ❌ **旧版遗留键** |
| `semesters` | String(JSON 数组) | `:304`、`:350` | `:112`、`:289` | ✅ 学期清单 |
| `active_semester` | long | `:150`、`:305` | `:129`、`:357` | ✅ 当前学期 |
| `bell_schedules` | String(JSON 数组) | `:706` | `:585` | ✅ 作息表（全局，不分学期） |
| `courses_backup` | String(原始载荷) | `:513` | **无任何读取点** | ❌ **损坏数据的诊断副本**，见 §1.4 |
| `courses:<学期id>` | String(JSON 数组) | `:467`、`:939` | `:420`、`:457`、`:936` | ✅ 该学期的课程 |
| `semester_start:<学期id>` | String(日期) | `:237`、`:315`、`:318`、`:903` | `:878` | ✅ 该学期的第 1 周周一 |

键名拼接规则：`CourseRepository.java:31` `SUFFIX_SEPARATOR = ":"`，`coursesKeyFor(id)` / `startKeyFor(id)`（`:369-384`）。

### 1.3 三个实体的载荷结构（来自序列化代码，逐字段）

**课程**（`CourseRepository.toJson:973-989` ↔ `fromJson:991-1008`）：

```json
{"id":1,"name":"高等数学","teacher":"张老师","location":"教学楼 A203",
 "day":1,"startPeriod":1,"endPeriod":2,"startWeek":1,"endWeek":16,
 "parity":0,"color":4282149518,"scheduleName":""}
```

字段来源与默认值（`fromJson` 的 fallback）：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `id` | long | `0` | 0 视为「未分配」，由 `CourseList.ensureId` 补 |
| `name` `teacher` `location` | String | `""` | |
| `day` | int | `1` | 1=周一 … 7=周日 |
| `startPeriod` / `endPeriod` | int | `1` / `2` | 1..15（`ScheduleUtils.PERIODS_PER_DAY`） |
| `startWeek` / `endWeek` | int | `1` / `18` | 1..18（`ScheduleUtils.SEMESTER_WEEKS`） |
| `parity` | int | `0` | `PARITY_ALL=0` `PARITY_ODD=1` `PARITY_EVEN=2`（`Course:6-8`） |
| `color` | long | `Course.DEFAULT_COLOR` | **写成 long**（`toJson:986` 注释：为了不被 JSON 双精度吃掉），无符号 32 位 ARGB，范围 0..4294967295 |
| `scheduleName` | String | `""` | 作息表**名字**；空 = 按地点自动匹配 |

**课程上有一个字段不持久化**：`Course.timeRange`（`Course.java:47`）是显示用的派生值，
由 `applyTimeRange:505-509` 在每次读取快照时盖章。它**不在** `toJson` 里，恢复时也不需要写——
恢复后第一次读取会按当时的作息表重新算出来。

**学期**（`SemesterJson:15-17` 的 schema 注释 ↔ `:51-66`）：

```json
[{"id":1757000000000,"start":"2026-08-31","name":""}]
```

`Semester` 是不可变值对象，只有 `id`(long) / `start`(LocalDate) / `name`(String) 三个字段（`Semester.java:26-28`）。
`name` 为空表示「按日期自动推导标签」（`Semester.label()`），**不是**缺数据。

读写都是宽容的：`SemesterJson.parse:41-43` 对 `id==0` 或日期不可解析的条目**跳过**。

**作息表**（`BellScheduleJson:14-21` 的 schema 注释 ↔ `:65-81`）：

```json
[{"name":"教学楼作息","keywords":["a楼","f楼","j301"],
  "starts":[495,535,...],"ends":[535,575,...]}]
```

- `starts` / `ends`：每节开始/结束的**分钟数**，长度 = 节数 = `BellSchedule.PERIODS` = 15
- 允许跨午夜：`BellSchedule.MAX_MINUTE = 1799`（即 29:59，`:34`）
- `BellScheduleJson.parse:98-102`：两数组长度不一致或为空时，**整张表退化为内置标准作息**（保留名字与关键词），
  而不是抛异常

### 1.4 必须排除在备份之外的三类数据

| 数据 | 排除理由 |
|---|---|
| `courses_backup` | 它是**上一次损坏载荷的原文**（`backupDamaged:512-514`），而且全项目**没有任何读取点**。把它写进备份文件等于把已知损坏的数据当成正常数据分发；恢复它只会把坏数据放回去 |
| `courses` / `semester_start` | 旧版遗留键，迁移时就被 `remove`（`:312`、`:316`）。现代安装里根本不存在 |
| `Course.timeRange` | 派生值，不是用户数据；恢复后自动重算（§1.3） |

**没有需要脱敏的应用配置**：全项目不存在主题偏好、通知开关、账号标识、设备标识或统计开关这类键——
`SettingsActivity` 的 5 张卡片（`:201/215/229/246/264`）全部通过 `CourseRepository` 读写同一批业务键。
因此备份内容 = 业务数据本身，不需要「过滤敏感配置」这一步；但由于课程含**教师姓名与教室**，仍需向用户明示（§3.3）。

### 1.5 现有的迁移与损坏处理策略（恢复流程要与之共存）

| 机制 | 位置 | 行为 |
|---|---|---|
| 旧版迁移 | `migrateLegacyStorage:288-323` | `semesters` 键不存在时执行一次：造出第一个学期，把旧 `courses`/`semester_start` 搬到 `courses:<id>`/`semester_start:<id>`，删掉旧键。**整个过程用一个 Editor** |
| 宽容解析 | `readCourses:471-497` | 非数组载荷 → 备份原文 + 返回空；数组内非对象条目 → 跳过 + 备份原文；其余照常读 |
| 损坏备份 | `backupDamaged:512-514` | 把原始载荷原样写进 `courses_backup` |
| 空列表播种 | `loadCourses:419-436` | `courses:<id>` 为 `null` **且**该学期是「安装出生时的学期」→ 写入 9 门样例课；否则返回空 |
| 空列表兜底 | `getSemesters:113-120` | `semesters` 读出来为空（被手工清掉）→ 造一个新学期 |
| 空列表兜底 | `getBellSchedules:587-591` | `bell_schedules` 为空 → 写入 `BellSchedules.presets()` |
| 占位表替换 | `replacePlaceholderSchedules:612-626` | 只要发现**任意一张**旧版占位表，就**整表替换**为预设 |

> ⚠️ 最后一条与本设计相关：恢复之后如果备份里的作息表恰好命中「旧版占位」签名，
> 下一次读取会把它们换掉。恢复时必须把这一层考虑进去（§4.2 校验规则 V7）。

### 1.6 现有写入的原子性实测（本设计的核心依据）

`SharedPreferences.Editor` 的公开语义是：**同一个 Editor 上的全部改动作为一次事务生效**；
不同的 Editor 是不同的事务。按此核对当前代码：

| 方法 | 位置 | 用了几个 Editor | 结论 |
|---|---|---|---|
| `migrateLegacyStorage` | `:303-320` | **1 个**（3~5 次 put/remove + 一次 `apply()`） | ✅ **既有先例**：本项目已经在用「一次事务写多个键」 |
| `saveCourses` | `:917-920` → `writeCourses:462-468` 或 `:928-941` | 1 个 | ✅ 单事务 |
| `saveBellSchedules` | `:703-709` | 1 个 | ✅ 单事务 |
| `saveSemesters` | `:347-353` | 1 个 | ✅ 单事务 |
| `setActiveSemester` | `:145-153` | 1 个 | ✅ 单事务 |
| **`updateSemester`** | `:232` + `:236-239` | **2 个** | ❌ 两次提交：先 `semesters`，再 `semester_start:<id>` |
| **`deleteSemester`** | `:265-268` + `:269` + `:273` | **2~3 个** | ❌ 先删两个键，再存学期清单，再可能切当前学期 |
| 全部写入 | 10 处 `apply()`，**0 处 `commit()`** | — | 写入是异步落盘的 |

**因此：「先解析文件，再依次调用若干保存方法」不是原子恢复。**
一次完整恢复至少要碰 `semesters`、`active_semester`、`bell_schedules`、N 个 `courses:<id>`、
N 个 `semester_start:<id>`，按现有 API 串起来就是 **2N+3 次独立提交**。
在第 1 次和第 2 次提交之间进程被杀，用户就会看到「学期列表是新的、课程还是旧的」这种混合状态。

---

## 二、备份 JSON 格式

### 2.1 完整示例（用 App 自带样例数据的形状）

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
        { "id": 2, "name": "大学英语", "teacher": "李老师", "location": "教学楼 F305",
          "day": 1, "startPeriod": 3, "endPeriod": 4,
          "startWeek": 1, "endWeek": 16, "parity": 0,
          "color": 4280561219, "scheduleName": "D/E 楼作息" }
      ]
    },
    {
      "id": 1760000000000,
      "start": "2027-02-22",
      "name": "大二下",
      "courses": []
    }
  ],
  "schedules": [
    { "name": "教学楼作息", "keywords": ["a楼", "f楼", "j301"],
      "starts": [495, 535, 595, 635, 680, 800, 840, 900, 940, 995, 1035, 1090, 1130, 1175, 1220],
      "ends":   [535, 575, 635, 675, 720, 840, 880, 940, 980, 1035, 1075, 1130, 1170, 1215, 1260] },
    { "name": "D/E 楼作息", "keywords": ["d楼", "e楼", "j303"],
      "starts": [615, 655, 715, 755, 800, 920, 960, 1020, 1060, 1115, 1155, 1210, 1250, 1295, 1340],
      "ends":   [655, 695, 755, 795, 840, 960, 1000, 1060, 1100, 1155, 1195, 1250, 1290, 1335, 1380] },
    { "name": "B/C 楼作息", "keywords": ["b楼", "c楼", "j302"],
      "starts": [495, 535, 595, 635, 680, 800, 840, 900, 940, 995, 1035, 1090, 1130, 1175, 1220],
      "ends":   [535, 575, 635, 675, 720, 840, 880, 940, 980, 1035, 1075, 1130, 1170, 1215, 1260] }
  ]
}
```

> 上表中的时间数组是**示意值**，用于说明结构；真实数值以 `BellSchedules.presets()` 为准，
> 导出实现不得硬编码（`BellSchedules.java` 是唯一权威）。

### 2.2 字段表：必要性、类型、空值、默认值

**顶层**

| 字段 | 必需性 | 类型 | 缺失/空值时的处理 | 说明 |
|---|---|---|---|---|
| `format` | **必需** | String | 缺失或 ≠ `"weihe-schedule-backup"` → 拒绝，提示「不是拾课的备份文件」 | 防止用户选错文件（例如选成教务系统的 `.xls`） |
| `formatVersion` | **必需** | int | 缺失或非数字 → 视为版本 1（见 §2.4）；> 支持上限 → 拒绝 | |
| `exportedAt` | 写出时必需，读入时可选 | String (ISO-8601 带时区偏移) | 缺失 → 摘要里不显示时间，不阻断恢复 | 只用于展示，不参与逻辑 |
| `appVersionName` | 可选 | String | 缺失 → 不显示 | 纯诊断信息 |
| `activeSemesterId` | 可选 | long | 缺失/对不上任何学期 → 回落到 `semesters[0]` | 与 `getActiveSemester:127-137` 的既有回落一致 |
| `semesters` | **必需** | Array | 空数组 → 拒绝（App 不变式要求至少 1 个学期，`deleteSemester:256`） | **课程嵌套在其中** |
| `schedules` | **必需** | Array | 空数组 → 拒绝（`getBellSchedules:587-591` 会把空当「没存过」并重新播种，等于用户的作息表被预设覆盖） | 全局，不按学期分 |

**`semesters[]`**

| 字段 | 必需性 | 类型 | 缺失/异常时的处理 |
|---|---|---|---|
| `id` | **必需** | long | `0` 或缺失 → 拒绝（`SemesterJson:41-43` 是跳过，但备份导入应当报错而不是静默少一个学期） |
| `start` | **必需** | String `yyyy-MM-dd` | 不可解析 → 拒绝 |
| `name` | 可选 | String | 缺失 → `""`（即按日期自动推导标签） |
| `courses` | 可选 | Array | 缺失 → 该学期 0 门课（合法状态） |

**`semesters[].courses[]`**：字段同 §1.3 的课程表。全部**可选**，缺失取该表的默认值，
但 `id` 若为 `0` 或与本学期内其它课程重复 → 重新分配（`CourseList.nextId`）并计入摘要。

**`schedules[]`**

| 字段 | 必需性 | 类型 | 缺失/异常时的处理 |
|---|---|---|---|
| `name` | **必需** | String（去空白后非空） | 空 → 拒绝；重名 → 拒绝（`indexOfSchedule:776-790` 按名字定位，重名会让编辑/删除指向错误对象） |
| `keywords` | 可选 | Array of String | 缺失 → `[]`（合法：表示该表不做地点匹配） |
| `starts` / `ends` | **必需** | Array of int，**长度必须相等且 = 15** | 不等或 ≠15 → **拒绝整个文件**。理由：内部 `BellScheduleJson` 对这种情况是「静默退化为标准作息」，那对**读取自己的存储**是合理兜底，但对**用户主动导入的文件**是无声的数据替换，必须让用户知道 |

### 2.3 与现有 SharedPreferences 格式的区别（为什么不能直接暴露内部存储）

| 维度 | 内部存储 | 备份格式 |
|---|---|---|
| 顶层形状 | 键值对（一个键一个 JSON **数组**） | 单个 JSON **对象** |
| 学期与课程的关联 | 靠**键名拼接** `courses:<id>` | 靠**结构嵌套**（`semesters[].courses[]`） |
| 版本标识 | 无 | `format` + `formatVersion` |
| 内部产物 | 含 `courses_backup`、旧版遗留键 | 不含（§1.4） |
| 派生字段 | `timeRange` 不存，读取时盖章 | 同样不存 |
| 消费者 | 只有 `CourseRepository` | 用户、其它工具、未来版本 |

**明确的设计决定**：备份**复用**课程/学期/作息表的**字段名**（`id`/`name`/`start`/`starts`…），
但**不复用容器结构**。字段名从 `formatVersion 1` 起即为**对外契约**：今后内部存储若改名或改结构，
必须在映射层吸收，**不得**让备份格式跟着变。
（备选方案是为备份另起一套字段名，但那会凭空多一层翻译且无收益，故不采用。）

### 2.4 版本兼容策略

**写**：始终写当前 `formatVersion`（首版为 `1`）。

**读**（下表即为实现的判定顺序）：

| `formatVersion` | 处理 |
|---|---|
| 缺失 / 非数字 / `0` | 按 `1` 处理，记一条「未标注版本」提示 |
| `1` .. `MAX_SUPPORTED`(=1) | 正常恢复 |
| `> MAX_SUPPORTED` | **拒绝**，提示「该备份由更新版本的拾课导出，请先升级 App」。**绝不尝试部分读取** |
| `< MIN_SUPPORTED` | 首版无更旧版本，恒不触发；保留该判定以便将来 |

**字段级向后兼容**：读取时**只取自己认识的键**，未知键一律忽略（`MiniJson` 的取值 API 天然如此）。
因此**新增可选字段不需要提升 `formatVersion`**。

**何时必须提升 `formatVersion`**：
1. 删除字段、改名、或改变字段含义；
2. 新增**必需**字段（旧文件缺少它时无法正确恢复）；
3. 改变数值语义（例如 `color` 从无符号 long 改成有符号 int）。

**降级保护**：不提供「另存为旧版本」的导出选项（首版用户基数不支持这个需求）；
若将来需要，再加 `formatVersion` 参数。

---

## 三、导出流程

### 3.1 流程图

```
[设置页 · 新增「备份与恢复」卡片]
        │  点「导出备份」
        ▼
[在内存里构建备份对象]  ← 只读：getSemesters() / coursesOf(s) / getBellSchedules() / 活动学期
        │
        ▼
[MiniJson.write(object)] ──► String（UTF-8 字节）
        │
        ▼
[Intent.ACTION_CREATE_DOCUMENT]  (mime "application/json", 建议文件名
        │                          weihe-backup-20261009-2132.json)
        │
   ┌────┴────┐
   │ 用户取消 │──► 什么都不做，不写任何数据，回设置页
   └────┬────┘
        │ 选定位置
        ▼
[ContentResolver.openOutputStream(uri)] ──► 写出字节 ──► flush/close
        │
   ┌────┴──────────────┐
   │ 成功               │ 失败（IOException / 返回 null）
   ▼                    ▼
[Toast：已导出 N 门课程 /  M 个学期 / K 张作息表]     [UiKit.message：导出失败 + 原因]
```

### 3.2 步骤与代码落点（实施阶段）

1. **构建对象**：新增一个纯 Java 类（建议 `data/BackupJson.java` 或 `data/BackupCodec.java`），
   `write(List<Semester>, Map<Long,List<Course>>, List<BellSchedule>, long activeId, String exportedAt)` → `String`。
   它只依赖 `MiniJson` 与模型，**零 Android 依赖**，因此可以在 JVM 上完整测试。
2. **读数据**：用 `getSemesters()`、`coursesOf(semester)`（`:450-459`，**不切换当前学期**）、
   `getBellSchedules()`、`getActiveSemester().id()`。
   ⚠️ `coursesOf` 对**当前**学期走 `getCourses()`（返回深拷贝），对**其它**学期直接读存储并解析——
   两条路都安全，但要注意 `getBellSchedules()` 返回的是缓存本体（见 §8 待确认 3）。
3. **选位置**：`ACTION_CREATE_DOCUMENT` + `setType("application/json")` +
   `putExtra(Intent.EXTRA_TITLE, name)`。**不需要任何权限**（SAF 由系统授予单文件访问）。
4. **写文件**：`ContentResolver.openOutputStream(uri)`，`write(bytes)`，`flush()`，`close()`。
   必须在后台线程做（可复用 `SettingsActivity.importTimetable:409-430` 的 `new Thread` +
   `runOnUiThread` 模式；同页已有先例，不引入新库）。
5. **反馈**：成功给摘要 Toast；失败走 `UiKit.message(this, "导出失败", detail)`。
6. **入口位置**：`SettingsActivity` 新增第 6 张卡片（现有 5 张见 `:201/215/229/246/264`）。

### 3.3 权限、隐私与用户提示

- **权限**：导出用 `ACTION_CREATE_DOCUMENT`、恢复用 `ACTION_OPEN_DOCUMENT`，
  两者都是 SAF，**不需要 `READ/WRITE_EXTERNAL_STORAGE`，也不需要 `MANAGE_EXTERNAL_STORAGE`**。
  Manifest 目前**一个权限都没声明**（实测），本方案应保持这一点。
- **不上云**：无 `INTERNET` 权限，无服务器、无账号、无统计。这是可以在 Manifest 层面被验证的强承诺。
- **隐私提示**：备份文件是**明文 JSON**，含课程名、**教师姓名**、**上课地点**、作息表。
  导出前应在确认框里明说，例如：
  > 「备份文件包含你的课程、教师姓名和教室信息，以明文保存。请存放在你自己信任的位置，不要随手上传到公开网盘。」
- **日志**：任何 `Log` 调用都**不得**输出备份内容（`exportedAt`／数量可以）。
  当前生产代码没有 `Log` 调用，实施时也不要引入。
- **文件名**：只含日期时间，不含教师/课程名等个人信息。

---

## 四、恢复流程

### 4.1 流程图

```
[设置页 · 「从备份恢复」]
        │
        ▼
[ACTION_OPEN_DOCUMENT]  (mime "application/json")
        │
   ┌────┴────┐
   │ 用户取消 │──► 直接返回，数据零改动
   └────┬────┘
        ▼
[读入字节 → String]          文件为空 / 读失败 ──► 拒绝：无法读取所选文件
        ▼
[MiniJson.parse(text)]       null（非法 JSON） ──► 拒绝：文件不是合法的 JSON
        ▼
[检查 format / formatVersion]  不是本 App 的备份 / 版本过新 ──► 拒绝
        ▼
[解析到独立的临时对象]  ← 关键：此时不碰 SharedPreferences 的任何一个键
   BackupDocument { semesters, coursesBySemester, schedules, activeId, exportedAt }
        ▼
[校验（V1..V9，见 §4.2）]   任何硬性失败 ──► 列出前几条原因，拒绝；当前数据完好
        ▼
[修正可修项]  ← 悬空 scheduleName 置空、重复/为 0 的课程 id 重分配、activeSemesterId 回落
        ▼
[摘要确认框]
   「备份时间：2026-10-09 21:32
     学期 2 个 · 课程 23 门 · 作息表 3 张
     ⚠️ 恢复会覆盖当前全部课程、学期与作息表」
   [取消]  [覆盖恢复]
        │
   ┌────┴────┐
   │ 用户取消 │──► 直接返回，数据零改动
   └────┬────┘
        ▼
[① 把当前数据序列化成同样的备份载荷 → 写入 restore_snapshot，commit()]
        │ 失败 ──► 中止，当前数据未动
        ▼
[② 单个 Editor：写入全部新键 + remove 全部过期键 → commit()]   ← §5.2
        │ 失败/返回 false ──► 当前数据未动（见 §5.3），报错
        ▼
[③ repository.reload() + 刷新本页]
        ▼
[Toast：已恢复 N 门课程 / M 个学期 / K 张作息表，(可选)提供「撤销本次恢复」]
```

### 4.2 数据校验规则

**硬性失败（拒绝整个文件，当前数据保持原样）**

| # | 规则 | 依据 |
|---|---|---|
| V1 | 载荷非空、为合法 JSON、且能解析成对象 | `MiniJson.parse:40-56` 对非法文本返回 `null` |
| V2 | `format == "weihe-schedule-backup"` | 防止选错文件类型 |
| V3 | `formatVersion` 可解析且 `≤ 1` | §2.4 |
| V4 | `semesters` 非空；每项 `id > 0` 且**全局唯一**；`start` 可解析为日期 | 不变式：至少一个学期（`deleteSemester:256`）；`id` 是 `courses:<id>` 的键 |
| V5 | 每个学期内课程 `id` **互不重复**（重分配可修，见下） | `CourseList.upsert:50-59` 按 id 匹配，重复会让一门课静默吃掉另一门 |
| V6 | `schedules` 非空；`name` 去空白后非空且**不重名** | `getBellSchedules:587-591` 与 `indexOfSchedule:776-790` |
| V7 | 每张作息表 `starts.length == ends.length == 15`，每个分钟值 ∈ [0, 1799] | `BellSchedule.PERIODS`、`MAX_MINUTE`；见 §1.5 的占位表替换风险 |
| V8 | 每门课：`day ∈ 1..7`、`1 ≤ startPeriod ≤ endPeriod ≤ 15`、`1 ≤ startWeek ≤ endWeek ≤ 18`、`parity ∈ {0,1,2}` | `ScheduleUtils.SEMESTER_WEEKS=18`、`PERIODS_PER_DAY=15`、`Course.PARITY_*` |
| V9 | 至少有一个学期能作为当前学期（`activeSemesterId` 命中，否则 `semesters[0]`） | `getActiveSemester:127-137` 的既有回落 |

**可修正项（不拒绝，修正后计入摘要并在确认框里逐条说明）**

| 情况 | 处理 | 依据 |
|---|---|---|
| 课程 `id == 0` 或与同学期课程重复 | 用 `CourseList.nextId` 重新分配 | `ensureId:99-104` 的既有语义 |
| `scheduleName` 指向备份里不存在的作息表 | **置空**（改为按地点自动匹配），统计条数 | 与 `clearScheduleReferences:853-869`（删除作息表时把引用清空）完全一致，是 App 已有的处理方式 |
| 作息表 `keywords` 缺失 | 视为 `[]` | `BellScheduleJson:87-94` 的行为 |
| 学期 `name` 缺失 | 视为 `""`（按日期推导标签） | `Semester.label()` |
| `activeSemesterId` 对不上 | 回落到 `semesters[0]` | `getActiveSemester` |

**不做的校验（刻意）**：不校验 `color` 是否属于 `Palette.COURSE_COLORS`。
`Course.color` 是自由 int，界面会照它画；拒绝一个颜色不常见的备份没有意义。

### 4.3 覆盖 vs 合并：首版选择「整库覆盖」

| | 整库覆盖（**首版采用**） | 合并 |
|---|---|---|
| 语义 | 备份内容**替换**当前全部课程/学期/作息表 | 把备份内容并入当前数据 |
| 幂等性 | ✅ **天然幂等**：同一文件导入两次结果完全相同（id 来自文件，其余全清） | ❌ 需要去重规则：同名学期？同 id 不同内容？同名作息表？同名课程算不算同一门？ |
| 失败面 | 单事务可完成（§5.2） | 需要「先算差异再写」，中途失败难以界定 |
| 用户预期 | 清晰：确认框明说「会覆盖当前全部数据」 | 模糊：用户很难预知结果 |
| 复杂度 | 低 | 高（需要冲突解决 UI） |

**结论**：首版做**整库覆盖**，并在确认框里用加粗语句写明覆盖范围。
**合并明确不做**（§7 阶段 7 列为「待评估」，不是承诺）。理由是幂等性：
覆盖式恢复重复执行永远得到同一结果，这直接消除了「重复导入造成重复课程或重复学期」这一整类问题。

### 4.4 摘要内容

确认框必须显示（全部来自**已解析的临时对象**，不是当前数据）：

- 导出时间（`exportedAt`，缺失则显示「未标注」）
- 学期数、课程总数、作息表数
- 若有可修正项：逐条列出（「3 门课程指向的作息表不在备份中，将改为按地点自动匹配」）
- 覆盖警告：明确写出将删除当前 N 个学期 / M 门课程 / K 张作息表

---

## 五、失败回滚与数据安全（重点）

### 5.1 为什么「先解析、再调用多个保存方法」不是原子操作

§1.6 已用代码逐项核对：现有公开保存方法各自开 Editor，`updateSemester` 甚至自己就用了 2 个。
一次恢复需要写 `2N+3` 个键（N = 学期数），按现有 API 串行调用就是 `2N+3` 次独立磁盘提交。
两次提交之间被杀，用户看到的是**混合状态**，而且这种状态**不会被任何现有兜底逻辑修复**：

- `getSemesters` 只在 `semesters` 为空时兜底 → 混合状态下列表非空，不触发
- `getBellSchedules` 只在为空时播种 → 不触发
- `loadCourses` 只在 `courses:<id>` 为 `null` 时播种 → 若恢复了学期却漏了课程，那一学期**永久为空**

所以必须在 Repository 里新增**一个**单事务入口，而不是把现有方法串起来。

### 5.2 单事务写入：可行，且项目里已有先例

`migrateLegacyStorage:303-320` 就是现成范例：

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

**设计**：新增 `CourseRepository` 入口（建议名 `restoreFrom(BackupDocument)`，实施阶段再定），
它做三件事，且**只开一个 Editor**：

1. `getAll().keySet()` 取出所有键，算出需要 `remove` 的过期键：
   - 所有 `courses:*` 与 `semester_start:*`（随后按新数据重新写入需要的那些）
   - 遗留的 `courses`、`semester_start`
   - **保留** `courses_backup`（它是诊断副本，与本次恢复无关，不应被恢复流程抹掉）
2. 写入新数据：`semesters`、`active_semester`、`bell_schedules`、
   每个学期的 `courses:<id>` 与 `semester_start:<id>`。
   ⚠️ **`semester_start:<id>` 必须显式写入**，即使它与 `semesters[].start` 重复——
   `getSemesterStart:877-888` 只读这个键，不读学期记录（详见 §5.7）。
3. `commit()` **一次**。

> **为什么用 `commit()` 而不是 `apply()`**：全项目目前 10 处 `apply()`、0 处 `commit()`。
> `apply()` 是异步落盘，恢复这种「用户明确点击、且体量只有几十 KB」的操作应当用 `commit()`，
> 因为它**同步返回成功/失败**，让流程能诚实地告诉用户「已恢复」还是「没写成」。
> 这与仓库其余写入保持 `apply()` 并不冲突：其它写入是可重做的增量编辑，恢复是不可重做的整体替换。

### 5.3 失败回滚

**三层保护，按发生顺序：**

| 层 | 时机 | 机制 | 失败后果 |
|---|---|---|---|
| **L1 不动数据** | 选文件 → 解析 → 校验 → 确认 | 全程在临时对象上操作，**一个 `preferences.edit()` 都不调用** | 任何失败（空文件/非法 JSON/版本不支持/校验不过/用户取消）都**不可能**影响现有数据 |
| **L2 恢复前快照** | 用户已确认、开始写之前 | 用 §三 的同一个序列化器把**当前**数据写成备份载荷，存进 `restore_snapshot` 键，`commit()` | 快照写失败 → **中止，不进行恢复**；此时数据未动 |
| **L3 单事务提交** | 写新数据 | 一个 Editor + 一次 `commit()` | `commit()` 返回 `false` 或抛异常 → **不删除快照**，报错；数据保持旧值 |

**关于 L3 的诚实说明**：
一次 `commit()` 保证「同一个 Editor 的改动全部生效」。
至于**磁盘文件层面**在写入过程中被强杀会留下旧内容还是新内容，属于 Android 实现细节，
**本轮未验证，标为待确认（§8-2）**。设计中不依赖这一层，只依赖 Editor 级原子性 + L2 快照。

**快照的用途与生命周期**：
- 恢复成功 → 快照**保留**，作为「撤销本次恢复」的一次性入口（与 `NEXT_OPTIMIZATION_ROADMAP.md`
  里「导入前快照 + 一键撤销」是同一套机制，可复用）。
- 下一次恢复 → **覆盖**快照（只保留最近一次）。
- 撤销 = 对快照执行同一个恢复流程（它本身就是合法备份载荷），因此撤销路径不需要新的写入代码。
- 快照键名建议 `restore_snapshot`，**不参与导出**（§1.4 的同类理由：它是内部产物）。
  ⚠️ 但这会略微增大 `SharedPreferences` 体积（约 2 倍课程数据），实施时需评估，
  并考虑「撤销成功/放弃后立即清除」。

### 5.4 进程中断与数据损坏

| 风险点 | 现有保护 | 本方案补充 |
|---|---|---|
| 多个键之间部分写入 | **无**（§5.1） | 单 Editor 单 `commit()` |
| 写入过程中被杀 | Android `SharedPreferences` 自身的文件写入机制 | 不依赖；L2 快照提供恢复路径 |
| 载荷被写坏 | `readCourses:475-480` 会备份原文 + 返回空；`BellScheduleJson:98-102` 会退化为标准作息；`SemesterJson:41-43` 会跳过坏条目 | 恢复前 L1 校验已排除坏数据；恢复后可加一次「读回校验」（§7 阶段 4 验收条件） |
| 应用退出 | — | 恢复是同步 `commit()`，完成后才提示成功，无「退出时丢一半」窗口 |

**注意一个真实的副作用**：损坏兜底逻辑会把坏数据**静默降级**（课程变空、作息表变标准、学期变少）。
恢复后应**主动读回并比对数量**（学期数/课程数/作息表数），不一致就报错并提示可用快照撤销，
而不是让用户以为恢复成功。

### 5.5 如何避免错误文件覆盖现有课表

1. `format` 标记 + `formatVersion` 双重检查（V2/V3）；选成 `.xls` 会被 `MiniJson.parse` 判为非法 JSON 直接拒绝。
2. 摘要在**写入前**展示，用户看到「学期 2 · 课程 0 · 作息表 3」就会发现选错了文件。
3. 覆盖语义显式写在确认按钮上（「覆盖恢复」），不与「导入」按钮混用词。
4. L2 快照 + 一键撤销，给误操作留退路。
5. **与现有 Excel 导入区分**：Excel 导入在 `SettingsActivity:493` 用的是「覆盖导入」，
   同样会 `replaceCourses:555-569` 清空课程。两者都应受益于同一套快照机制，但**卡片要分开**，
   避免用户把「恢复备份」和「导入课表」当同一件事。

### 5.6 如何防止重复导入造成重复课程或重复学期

- **首版覆盖语义天然幂等**（§4.3）：导入两次的结果与导入一次完全相同。
  依据：恢复时 `courses:<id>` 与 `semesters` 都是**整体重写**，不是追加；`id` 来自文件而非新生成。
- 因此**不需要**去重规则、不需要检测「这门课是不是已经存在」。
- 唯一的例外是**合并模式**——而首版不做合并。
- 补充保险：恢复完成后把新状态与临时对象比对数量（§5.4），数量不符即报错。

### 5.7 已知的遗留不一致：`semester_start:<id>` 的写入缺口

**由代码推得，本轮未执行验证，标为待确认（§8-1）。**

`addSemester:175-191` 与 `setActiveSemester:145-153` **都不写** `semester_start:<新id>`：

```
SemesterListActivity:293   addSemester(monday, name)      → 只写 semesters
SemesterListActivity:298   setActiveSemester(created)     → 只写 active_semester
        ↓ 页面 finish()，回到首页 onResume
MainActivity:114/765       getSemesterStart()             → 读 semester_start:<新id> → null
CourseRepository:885-887   → defaultSemesterStart(now) + setSemesterStart(默认值)
CourseRepository:897-905   → updateSemester(startingOn(默认值)) → 改写 semesters[].start
```

即：用户新建学期时填的日期，可能在回到首页时被**默认锚点**（8 月 28 日 / 2 月 18 日那一周的周一）
替换掉。既有测试没有覆盖这条路径——
`eachSemesterKeepsItsOwnStartDate:523-535` 在切换后**显式调用了 `setSemesterStart`**，
而 `switchingSemestersSurvivesAReopen:538-549` 断言的是 `getActiveSemester().start()`（读 `semesters`），
不是 `getSemesterStart()`，所以两者都测不到这个缺口。

**对本设计的影响（重要且正面）**：
恢复流程**必须显式写 `semester_start:<id>`**，因此**恢复出来的数据不会带上这个缺口**——
这等于顺手修掉了它。同时建议在实施阶段单独确认并修复 `addSemester`，
但那属于另一个任务，不在本轮范围。

---

## 六、测试清单

全部尽量落在**纯 JVM**上：`FakePreferences` 已实现 `getSharedPreferences` 之外所需的
`getString`/`getLong`/`getAll`/`contains`/`edit`，因此「构建载荷 → 解析 → 校验 → 写入 → 重新读取」
整条链路都能用现有测试基础设施验证。

**A. 往返与一致性（`BackupCodecTest` / `CourseRepositoryBackupTest`）**

| # | 用例 | 断言 |
|---|---|---|
| A1 | 导出后立即恢复，业务数据一致 | 学期数/课程数/作息表数与**每门课的 12 个字段**逐项相同 |
| A2 | 恢复后关闭重开仓库 | `repositoryFor(同一 FakePreferences)` 读出的数据与恢复后一致（第 6 条要求） |
| A3 | 多学期往返 | 每个学期的课程归属不串（`courses:<id>` 与嵌套结构对应正确） |
| A4 | 空课程学期往返 | 一个 0 门课的学期仍存在，不被丢弃 |
| A5 | 自定义作息表往返 | 名字、关键词、30 个时间值全部保真 |
| A6 | 活动学期往返 | `active_semester` 正确；缺失时回落到第一个学期 |
| A7 | 汉字与特殊字符 | 教师/地点含引号、换行、反斜杠、全角标点不失真（`MiniJsonTest` 已有同类先例） |
| A8 | 导出可重复 | 同一数据连续导出两次得到**相同字节**（`MiniJson.object()` 是 `LinkedHashMap`，顺序稳定） |

**B. 拒绝与容错**

| # | 用例 | 断言 |
|---|---|---|
| B1 | 空文件 | 拒绝，且**存储未被写入**（逐键比对恢复前的 `getAll()`） |
| B2 | 非法 JSON | 拒绝，`MiniJson.parse` 返回 null 触发 |
| B3 | 合法 JSON 但不是本 App 的备份（如 `{"a":1}`） | 拒绝（`format` 不符） |
| B4 | 截断的 JSON | 拒绝 |
| B5 | 缺 `formatVersion` | 按 1 处理，可恢复（向后兼容） |
| B6 | `formatVersion = 99` | **拒绝**，且当前数据完好（第 4 条要求） |
| B7 | 缺 `semesters` / 空数组 | 拒绝 |
| B8 | 缺 `schedules` / 空数组 | 拒绝 |
| B9 | 作息表 `starts` 长度 ≠ 15 或与 `ends` 不等 | 拒绝（不采用内部那种静默退化） |
| B10 | 作息表重名 | 拒绝 |
| B11 | 不属于任何学期的课程 id 重复 | 重新分配 id 并计入摘要，不拒绝 |
| B12 | 悬空 `scheduleName` | 置空 + 计入摘要；恢复后该课程按地点解析（第 5 条要求） |
| B13 | `day=9` / `startPeriod=0` / `endPeriod=99` / `startWeek>endWeek` / `parity=7` | 逐条拒绝并给出定位信息 |
| B14 | 学期 `id=0` 或 `start` 不可解析 | 拒绝 |

**C. 失败回滚（第 7 条要求）**

| # | 用例 | 断言 |
|---|---|---|
| C1 | 恢复失败后原数据完整 | 用 B5..B14 的每种坏文件各跑一次，恢复前的 `getAll()` 快照与恢复后**逐键相同** |
| C2 | 写入阶段失败 | `FakePreferences` 增加「commit 返回 false」模式（现有测试已为 `apply` 有过类似改造思路）；断言数据未变、快照仍在 |
| C3 | 用户取消文件选择 | 不产生任何 `edit()`（可用 `FakePreferences` 记录写次数断言） |
| C4 | 用户取消确认框 | 同上 |
| C5 | 快照写入失败 | 中止恢复，当前数据不变 |
| C6 | 撤销恢复 | 快照可还原到恢复前的状态，且逐字段一致 |

**D. 幂等与去重语义（第 8 条要求）**

| # | 用例 | 断言 |
|---|---|---|
| D1 | 同一文件恢复两次 | 结果与恢复一次完全相同（学期数/课程数/作息表数/全部字段） |
| D2 | 覆盖语义 | 恢复一个课程更少的备份后，原有课程**确实被删除**（不是合并保留） |
| D3 | 恢复不会凭空生成样例课 | `loadCourses:425-432` 的播种逻辑不因恢复被触发 |

**E. 需要 Android 的集成测试（单独规划，不引入新依赖）**

| # | 用例 | 方式 |
|---|---|---|
| E1 | SAF 选目录并写出文件 | 手工/脚本：`tools/tap-ui.ps1` 驱动系统文件选择器；**当前项目无 `androidTest` 源集**，本轮不新建 |
| E2 | 导出的文件用文本编辑器可读、UTF-8 无 BOM | 手工检查 |
| E3 | 恢复摘要数字与备份内容一致 | 手工截屏核对 |
| E4 | 大字体/深色模式下确认框不截断 | 手工（参考 `ARCHITECTURE.md` 第 8 条踩坑记录） |

---

## 七、分阶段实施计划与验收条件

每阶段结束时的通用验收：`testDebugUnitTest` 全绿（数量只增不减）· `assembleDebug` 成功 · `lintDebug` 零问题。

| 阶段 | 内容 | 是否碰 Android | 验收条件 |
|---|---|---|---|
| **1** | **备份编解码 + 校验**：新增纯 Java 的 `BackupCodec`（写出）+ `BackupDocument`/`BackupParser`（读入 + 校验 V1..V9 + 可修正项）。零 Android 依赖、零 UI | ❌ | A1..A8、B1..B14 全绿；新增类**不 import 任何 `android.*`**（可用现有 `PaletteContrastTest` 同款断言方式或直接靠编译期保证）；测试数 +约 25 |
| **2** | **单事务恢复入口**：`CourseRepository.restoreFrom(...)`，一个 Editor + 一次 `commit()`；含 L2 快照与撤销 | 仅 `SharedPreferences`（已有测试缝） | C1..C6、D1..D3 全绿；新增测试用 `FakePreferences` 断言**只发生一次提交**（给 `FakePreferences` 加提交计数器——这是测试辅助代码，不是生产代码）；测试数再 +约 10 |
| **3** | **导出 UI**：`SettingsActivity` 新增「备份与恢复」卡片；`ACTION_CREATE_DOCUMENT` 导出；后台线程 + 成功/失败反馈 | ✅ | 真机导出成功；用文本编辑器打开是合法 JSON；Manifest **仍未新增任何权限**（`git diff` 可验证）；E1/E2 通过 |
| **4** | **恢复 UI**：`ACTION_OPEN_DOCUMENT` → 解析 → 校验 → 摘要确认框 → 恢复 → `reload()` + 刷新 | ✅ | 摘要数字与备份一致（E3）；坏文件给出可读原因；**恢复后读回比对数量**（§5.4）；E4 通过 |
| **5** | **撤销本次恢复**：快照的一次性撤销入口 + 覆盖策略 | ✅ | C6 通过；撤销后再撤销无效 |
| **6** | **文档**：把本设计落地情况写进 `PROJECT_ARCHITECTURE.md`（存储章节）与 `ARCHITECTURE.md`（回归清单） | ❌ | 文档与实现一致；回归清单加入「导出→恢复→数据一致」一条 |
| **7** | （**待评估，非承诺**）合并模式、增量备份、加密、定时自动备份 | — | 需要先确认用户需求 |

**建议顺序的理由**：1 和 2 完全可以在 JVM 上验证，风险最低、价值最高（2 直接消除了 §5.1 的不原子问题）；
3 和 4 才需要真机；5 复用 2 的机制，增量很小。

---

## 八、现有架构中需要进一步核实的问题

| # | 问题 | 本轮掌握的证据 | 需要怎么确认 |
|---|---|---|---|
| **1** | **`addSemester` 未写 `semester_start:<新id>`，新建学期后首次 `getSemesterStart()` 可能用默认锚点覆盖用户填的日期** | §5.7 的完整调用链（`:175-191`、`:145-153`、`:877-905`、`SemesterListActivity:293,298`）；既有两个相关测试都绕开了这条路径 | 加一条临时用例：`addSemester(2027-02-22)` → `setActiveSemester` → **不调用** `setSemesterStart` → 断言 `getSemesterStart()`。本轮受「只允许新增设计文档」限制未执行 |
| **2** | 单次 `commit()` 在写入过程中被强杀时，磁盘上是旧内容还是新内容 | 只确认了 Editor 级原子性（公开语义）与 `SharedPreferences` 的公开文档描述 | 需要设备上的崩溃注入或查证平台实现；设计已通过 L2 快照规避对该层的依赖 |
| **3** | `getBellSchedules()` 返回缓存本体而非副本（`getCourses()` 返回深拷贝，`getSemesters()` 返回浅拷贝） | `:580-598` 与 `:396-409`、`:106-124` 的对比；且播种分支返回的是局部列表、缓存命中分支返回缓存本体，**两种行为不一致** | 导出时若直接持有该列表并延后使用，存在被同进程其它页面改动影响的风险。实施阶段应：导出前先 `BellSchedules.copyOf(...)`，或单独修这个方法 |
| **4** | `MiniJson` 未提供 `optObject(Map, key)` 之类的嵌套取值助手 | 公开 API 只有 `parse`/`parseArray`/`objectAt`/`optLong`/`optInt`/`optString`/`optArray`/`object`/`array`/`write`（实测） | 本设计的格式用 `optArray` + 逐项 `instanceof Map` 即可，无需改 `MiniJson`；若实施中发现可读性差，再加一个**纯新增**的助手（不改现有行为） |
| **5** | 真机上 `SharedPreferences.getAll()` 是否确实返回全部 `courses:*` / `semester_start:*` 键 | `FakePreferences` 已实现 `getAll()`；生产代码从未调用过它 | 阶段 2 之前用 `adb shell run-as … cat shared_prefs/weihe_schedule.xml` 对一次（本项目此前用过这条命令） |
| **6** | 恢复后各页面能否自动刷新 | 5/7 个 Activity 在 `onResume` 调 `reload()`（`MainActivity:111`、`ScheduleListActivity:57`、`SemesterListActivity:61`、`SettingsActivity:75`、`WeekGridActivity:147`）；两个编辑页没有 `onResume` | 恢复入口在 `SettingsActivity`，它自己 `reload()` + 重绘即可；返回首页/总览时会各自 `reload()`。两个编辑页需确认「恢复时用户不会正停留在编辑页」（正常流程下不会） |
| **7** | 快照键 `restore_snapshot` 带来的体积翻倍是否可接受 | 本轮**未测量**任何真实数据的体积 | 阶段 2 时量一次：`adb shell run-as com.weihe.schedule cat shared_prefs/weihe_schedule.xml`（本项目此前用过这条命令读真实存储） |
| **8** | 导出文件名时区 | `OffsetDateTime.now()` 需要 API 26+，`minSdk` 恰为 26 | 编译期即可确认；运行时用 `tools/dump-ui.ps1` 核对一次 |

---

## 附：本轮未做的事（明确划界）

| 不做 | 原因 |
|---|---|
| 修改任何生产代码、测试代码、Gradle 配置、依赖 | 用户明确限制 |
| 新增 `DATA_BACKUP_DESIGN.md` 以外的文件 | 用户明确限制 |
| 执行构建、测试或真机验证 | 本轮为设计任务；所有未执行项已标 **待确认** |
| 清理工作区、`git reset`、`git checkout` | 工作区含 7 项既有未提交改动，已原样保留并记录指纹 |
| 设计云端同步、账号、服务端 | 与「不增加服务器、账号体系或网络依赖」冲突 |
| 设计加密备份 | 首版无密钥管理方案；明文 + 明示风险更诚实。列为阶段 7 待评估 |
| 设计合并式恢复 | 复杂度与幂等性收益不成比例（§4.3） |

---

**本文档只记录设计方案。文中所有「已实现」「可恢复」之类的表述均指设计意图，
当前代码中**不存在**任何导出、备份或恢复能力。**
