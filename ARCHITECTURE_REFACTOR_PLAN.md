# ARCHITECTURE_REFACTOR_PLAN.md — 包结构整理规划

> ## ✅ 已执行完毕（2026-10）
>
> 本计划已按 §九 的顺序分 6 个阶段执行完成，每个阶段一次独立提交：
>
> | 阶段 | 提交 | 内容 |
> |---|---|---|
> | 1 | `87df6dc` | `.model`：Course、Semester、ScheduleUtils、BellSchedule、BellSchedules |
> | 2 | `15ad8d4` | `.design`：Palette、Colors |
> | 3 | `0523e5d` | `.rule`：CourseQuery、CourseConflicts、CourseFormValidator、BellScheduleEditor、WeekGridPlan |
> | 4 | `73ceb71` | `.data`：CourseRepository、CourseList、MiniJson、SemesterJson、BellScheduleJson |
> | 5 | `0d7b930` | `.importer`：TimetableGrid、TimetableImportParser、ExcelTimetableReader、TimetableImportException |
> | 6 | `a159e7e` | `.ui`：UiKit、UiTokens、Insets |
>
> 采用 §四.6 的**方案 A**（`UiTokens` 进 `.ui`，未改 `px()` 方法体）。
> 7 个 Activity 留在根包，`AndroidManifest.xml` 未改动。
>
> **实际结果与计划的差异（3 处，均为执行中的发现，已处理）：**
>
> 1. `Course.java` 与 `ScheduleUtils.java` 各有一处 `{@link}` 指向别的包。若按常规补 import，
>    会凭空造出 `model → design` 与 `model → 根包` 两条**反向边**，让包图成环。
>    改为**全限定 `{@link}`**（不 import），链接照常解析，依赖图保持无环。
> 2. `PaletteContrastTest` 在第 2 阶段拿到了 `import com.weihe.schedule.UiTokens;`（当时 UiTokens 还在根包），
>    第 6 阶段 UiTokens 迁走后该 import 变陈旧，与新 import 同名冲突 → 编译失败。
>    已删除陈旧 import。**该问题在阶段 6 内修复后才提交。**
> 3. 计划 §7.1 预估"只有 1 个测试必须移动"，实际为编译所需而移动的只有这 1 个
>    （`CourseRepositoryTest` + `FakePreferences`）；其余 16 个测试是为"与生产类同包"的约定而移动。
>
> **每阶段验证结果一致：`suites=17 tests=239 failures=0 errors=0`，`assembleDebug` 成功，
> `lintDebug` 为 `No issues found.`**

> **本文件是规划与执行记录，不描述现状。** 当前结构以 `ARCHITECTURE.md` §一 与
> `PROJECT_ARCHITECTURE.md` §二/§六 为准。
>
> **规划阶段只分析、未修改代码。**
> 规划时本文档是唯一新增文件；31 个生产源文件、18 个测试文件、Gradle 配置、资源、Manifest 均未改动。
>
> **分析对象**：`D:\vibe coding\Weihe\Weihe`，规划时 git HEAD = `1213ef7`（含 5 个 P0 修复），工作树干净。
>
> **目标**：在**功能与行为完全不变**的前提下，把 31 个类从单包拆成可读的包结构。
> 判据不是"符不符合标准架构"，而是**"结构清楚、容易理解、容易学习、容易继续开发"**。
>
> **约束**（全部遵守）：不迁 Kotlin / Compose / XML / Room / AndroidX / 新框架；不改 SharedPreferences；
> 不重写 MiniJson；不改课程数据结构；不改业务逻辑；不改时间规则；不改 UI 功能。
> 本次整理**只做一件事：移动文件 + 补 import**（§八 列出的唯一例外会被明确标注）。

---

## 一、当前结构

### 1.1 现状：31 个生产类全部在同一个包里

```
app/src/main/java/com/weihe/schedule/     ← 31 个 .java，10,538 行
app/src/test/java/com/weihe/schedule/     ← 18 个 .java，3,533 行，239 个 @Test
```

`package com.weihe.schedule;` 出现在全部 49 个文件里，没有任何子包。
`android.nonTransitiveRClass=true`，`namespace 'com.weihe.schedule'`，所以 `R` 也生成在这个包。

### 1.2 全部 31 个生产类及其职责

按当前行数降序。**"依赖 Android"一列由 `import android.*` 与文件内 `android.xxx` 全限定名共同判定**（实测）。

| 类 | 行数 | 职责 | 依赖 Android |
|---|---|---|---|
| `UiKit` | 1432 | 组件工厂：卡片/芯片/按钮/对话框/底部弹层/动效/表单控件。手写替代 Material 库 | ✅ 是（10 处 import，71 处全限定名） |
| `MainActivity` | 1324 | 首页「我的课表」：当天课程 + 周次 + 日期条 + 学期入口。**含约 137 行已死的导入代码** | ✅ Activity |
| `CourseRepository` | 1067 | 存储适配：SharedPreferences 读写、快照缓存、学期切换、作息表保存/删除、旧版迁移 | ✅ 是（Context、SharedPreferences 两处） |
| `WeekGridActivity` | 930 | 课表总览：周网格 / 学期网格、冻结表头与节次列 | ✅ Activity |
| `EditCourseActivity` | 795 | 单门课程表单（新建 / 编辑 / 删除），含冲突确认 | ✅ Activity |
| `SettingsActivity` | 511 | 设置：批量导入、学期、开学第一周、作息表入口 | ✅ Activity |
| `MiniJson` | 453 | 手写 JSON 解析/序列化（因为 `org.json` 在 JVM 上是 null 桩） | ❌ 否 |
| `EditScheduleActivity` | 449 | 单张作息表的编辑（名称 / 地点关键词 / 15 节时间）与删除 | ✅ Activity |
| `WeekGridPlan` | 388 | 周网格排布算法：簇（Cluster）/ 格（Cell）/ 块（Block）与车道分配 | ❌ 否 |
| `TimetableImportParser` | 367 | 解析教务系统课表版式：表头定位、星期列、课名/教师/地点、周次语法 | ❌ 否 |
| `SemesterListActivity` | 364 | 学期列表：切换 / 新建（可复制课程）/ 删除 | ✅ Activity |
| `BellSchedule` | 307 | 一张作息表：15 节起止分钟、地点关键词、匹配与时间格式化。**不可变** | ❌ 否 |
| `BellScheduleEditor` | 255 | 作息表表单的规则（半成品时间文本、重名、重叠提示） | ❌ 否 |
| `ScheduleListActivity` | 248 | 作息表列表：卡片、默认徽标、长按删除 | ✅ Activity |
| `BellSchedules` | 193 | 三张预设作息表 + 解析顺序（显式名 → 地点 → B/C → 标准） | ❌ 否 |
| `Palette` | 192 | 颜色角色表：品牌色、表面阶梯、文字、页头、状态色、9 个课程色、混合系数 | ❌ 否（仅 javadoc 提到 material 库） |
| `ScheduleUtils` | 170 | 周次/节次/日期运算：`currentWeek` `weekStart` `dateOf` `occursInWeek` `weekRule` … | ❌ 否 |
| `UiTokens` | 140 | 尺寸令牌：间距 6 档、圆角 6 档、阴影 4 档、字阶 9 档、控件尺寸 | ⚠️ **仅 1 处**：`px(Context, dp)` 在 `UiTokens.java:137` |
| `CourseFormValidator` | 139 | 课程表单校验：返回 `Problem{field, reason}` 列表，不碰界面 | ❌ 否 |
| `BellScheduleJson` | 126 | 作息表的 JSON 往返 + 损坏载荷修复 | ❌ 否 |
| `Semester` | 120 | 学期：id、第 1 周周一、标签推导、下一学期起点 | ❌ 否 |
| `CourseQuery` | 117 | 只读查询：`onDay` `inWeek` `all` `startingAt` `maxParallel` | ❌ 否 |
| `CourseList` | 104 | 列表维护：`find` `upsert`（存副本）`remove` `nextId` `ensureId` | ❌ 否 |
| `CourseConflicts` | 90 | 时间冲突判定：同星期 + 节次重叠 + 周次有交集 | ❌ 否 |
| `TimetableGrid` | 87 | 导入用的内存工作表 + 合并单元格几何 | ❌ 否 |
| `Colors` | 77 | WCAG 对比度算术：`blend` `contrastRatio` `meets` `luminance` | ❌ 否（纯算术） |
| `Insets` | 74 | 系统栏：状态栏/导航栏配色与图标明暗标志 | ✅ 是（5 处 import） |
| `Course` | 69 | 课程实体：id/名称/教师/地点/星期/节次/周次/单双周/颜色/作息名 + 派生 timeRange | ❌ 否 |
| `SemesterJson` | 65 | 学期的 JSON 往返 | ❌ 否 |
| `ExcelTimetableReader` | 54 | 用 jxl 读 `.xls`，转成 `TimetableGrid` | ❌ 否（只依赖 jxl 这个纯 JVM jar） |
| `TimetableImportException` | 12 | 导入失败异常 | ❌ 否 |

**统计**（实测 `import android.*`）：31 个类中 **21 个不 import 任何 Android 类**，
其中 20 个在代码里完全不出现 `android`（`MiniJson` 与 `Palette` 只在 javadoc 里提到），
唯一的例外是 **`UiTokens`**——`px(Context, dp)` 一个方法需要 `Context`。
剩下 **10 个 import Android**：**7 个 Activity** + **`CourseRepository`**（`Context`/`SharedPreferences`）
+ **`UiKit`/`Insets`**（界面组件与系统栏）。

### 1.3 当前结构的三个具体问题

1. **找东西要靠文件名猜。** 「作息表的时间怎么算」的答案分散在 `BellSchedules`（数据）、
   `BellSchedule`（单个表）、`ScheduleUtils`（周次）、`BellScheduleEditor`（表单规则）四个文件里，
   而「怎么画一张卡片」在 `UiKit`、「用什么颜色」在 `Palette`——31 个文件平铺，没有分组线索。
2. **分层只存在于注释和文档里。** `ARCHITECTURE.md` 画了清晰的五层依赖图，
   但代码里没有任何结构体现它：一个新人打开 `src/main/java/com/weihe/schedule/` 看到的是一堆平级文件。
3. **"能不能脱离模拟器测试"这个关键事实看不出来。** 22 个类可以纯 JVM 测试、10 个不行，
   但当前只能靠逐个打开看 import 才知道。

---

## 二、目标结构

### 2.1 七个包（含根包）

```
com.weihe.schedule                  ① 界面（7 个 Activity）+ R
com.weihe.schedule.model            ② 数据模型与日历运算
com.weihe.schedule.rule             ③ 规则（校验、冲突、查询、排布）
com.weihe.schedule.data             ④ 存储与序列化
com.weihe.schedule.importer         ⑤ Excel 课表导入
com.weihe.schedule.design           ⑥ 设计令牌（颜色）
com.weihe.schedule.ui               ⑦ 界面组件库
```

| 包 | 类数 | 一句话说明 | 依赖 Android |
|---|---|---|---|
| `com.weihe.schedule` | 7 | **界面**：用户能看到并操作的每一屏 | ✅ 全部 |
| `.model` | 5 | **`Course`/`Semester` 是什么，以及它们身上的日历算术** | ❌ 全部不依赖 |
| `.rule` | 5 | **允许什么、算出什么**：校验、冲突、查询、网格排布 | ❌ 全部不依赖 |
| `.data` | 5 | **存到哪、怎么变成 JSON**：仓库、列表维护、三个 Json 编解码 | 仅 `CourseRepository` |
| `.importer` | 4 | **教务系统 `.xls` 怎么变成课程列表** | ❌ 全部不依赖 |
| `.design` | 2 | **颜色角色与对比度算术** | ❌ 不依赖 |
| `.ui` | 3 | **组件库与尺寸令牌**，全部界面都由它搭建 | ✅ 全部 |

**没有新增任何"层"。** 这七个包对应的是原本就存在、且已经在 `ARCHITECTURE.md` 里写明的分组——
整理只是把它搬到文件系统上。没有引入接口层、没有 Repository 接口 + 实现、没有 DI、没有 Domain/Data 分离的样板。

### 2.2 依赖方向（单向无环）

```
                     com.weihe.schedule (7 个 Activity)
                        │  │  │  │  │  │
        ┌───────────────┘  │  │  │  │  └───────────────┐
        ▼                  ▼  │  ▼  │                  ▼
   .ui (3)          .data (5) │  .importer (4)     .rule (5)
        │              │  │   │      │                 │
        │              │  └───┼──────┼─────────────────┼──► .design (2)
        ▼              ▼      ▼      ▼                 ▼
                        .model (5)
```

精确的包级边（下文 §五 有推导依据）：

| 从 | 到 | 一句话理由 |
|---|---|---|
| 根（界面） | `.ui` `.data` `.rule` `.importer` `.design` `.model` | 界面装配一切 |
| `.ui` | `.design` | 组件用调色板和尺寸令牌搭建 |
| `.data` | `.model` `.design` | 仓库读写模型；样例课程的颜色取自调色板 |
| `.importer` | `.model` `.design` | 解析器产出 `Course`，并按 `Palette.COURSE_COLORS` 分配颜色 |
| `.rule` | `.model` | 规则作用于模型 |
| `.design` | `.model` | `Palette.COURSE_COLORS[0]` 直接复用 `Course.DEFAULT_COLOR` |
| `.model` | — | 叶子 |

**无环**，且每条边都能一句话说清。

---

## 三、每个文件准备移动到哪个 package

**31 个生产类，逐个给出目标包。** 「移动后要改什么」一列只列该文件自身需要的改动。

### ① `com.weihe.schedule`（不变，7 个）

| 文件 | 移动后要改什么 |
|---|---|
| `MainActivity.java` | 补 6 个 import（model/rule/data/importer/design/ui 各若干） |
| `WeekGridActivity.java` | 补 5 个 import |
| `EditCourseActivity.java` | 补 5 个 import |
| `SettingsActivity.java` | 补 6 个 import |
| `SemesterListActivity.java` | 补 4 个 import |
| `ScheduleListActivity.java` | 补 5 个 import |
| `EditScheduleActivity.java` | 补 5 个 import |

> **为什么 Activity 留在根包**：`AndroidManifest.xml:11-38` 用 `.MainActivity` 这样的相对名注册了全部 7 个 Activity，
> 它相对于 `namespace`（= `com.weihe.schedule`）。留在根包 → **Manifest 一行都不用改**，
> 也避免了"改了包名忘了改 Manifest、装机才发现 Activity 找不到"这类最难在编译期发现的错误。
> 详见 §八 R4。

### ② `com.weihe.schedule.model`（5 个）

| 文件 | 移动后要改什么 |
|---|---|
| `Course.java` | 无需（只依赖 `ScheduleUtils`，同包） |
| `Semester.java` | 无需（只依赖 `ScheduleUtils`，同包） |
| `ScheduleUtils.java` | 无需（只依赖 `Course`/`BellSchedules`，同包） |
| `BellSchedule.java` | 无需（只依赖 `ScheduleUtils`，同包） |
| `BellSchedules.java` | 无需（只依赖 `BellSchedule`，同包） |

### ③ `com.weihe.schedule.rule`（5 个）

| 文件 | 移动后要改什么 |
|---|---|
| `CourseQuery.java` | `import com.weihe.schedule.model.Course;` `…model.ScheduleUtils;` |
| `CourseConflicts.java` | 同上 |
| `CourseFormValidator.java` | `import com.weihe.schedule.model.ScheduleUtils;` |
| `BellScheduleEditor.java` | `import com.weihe.schedule.model.BellSchedule;` `…model.BellSchedules;` |
| `WeekGridPlan.java` | `import com.weihe.schedule.model.Course;` |

### ④ `com.weihe.schedule.data`（5 个）

| 文件 | 移动后要改什么 |
|---|---|
| `CourseRepository.java` | 补 `model.*`（5 个类）、`design.Palette`/`design.Colors`；`CourseList`/`MiniJson`/`SemesterJson`/`BellScheduleJson` 同包 |
| `CourseList.java` | `import com.weihe.schedule.model.Course;` |
| `MiniJson.java` | 无需（不依赖任何生产类） |
| `SemesterJson.java` | `import com.weihe.schedule.model.Semester;` `…model.ScheduleUtils;` |
| `BellScheduleJson.java` | `import com.weihe.schedule.model.BellSchedule;` `…model.BellSchedules;` |

### ⑤ `com.weihe.schedule.importer`（4 个）

| 文件 | 移动后要改什么 |
|---|---|
| `TimetableGrid.java` | 无需 |
| `TimetableImportException.java` | 无需 |
| `TimetableImportParser.java` | 补 `model.Course`、`model.ScheduleUtils`、`design.Palette`；`TimetableGrid`/`TimetableImportException` 同包 |
| `ExcelTimetableReader.java` | 补 `model.Course`；`TimetableGrid`/`TimetableImportParser`/`TimetableImportException` 同包 |

### ⑥ `com.weihe.schedule.design`（2 个）

| 文件 | 移动后要改什么 |
|---|---|
| `Palette.java` | `import com.weihe.schedule.model.Course;`（`COURSE_COLORS[0] = Course.DEFAULT_COLOR`） |
| `Colors.java` | 无需（纯算术，不依赖任何生产类） |

### ⑦ `com.weihe.schedule.ui`（3 个）

| 文件 | 移动后要改什么 |
|---|---|
| `UiKit.java` | 补 `design.Palette`、`design.Colors`；**并补 `import com.weihe.schedule.R;`**（用了 `R.drawable.ic_back`、`R.style.SheetAnimation`、`R.style.DialogAnimation`） |
| `UiTokens.java` | 无需（只依赖 `UiKit`，同包） |
| `Insets.java` | `import com.weihe.schedule.design.Palette;` |

### 汇总

| 目标包 | 类数 | 行数合计（约） |
|---|---|---|
| `com.weihe.schedule`（不变） | 7 | 5,521 |
| `.model` | 5 | 859 |
| `.rule` | 5 | 939 |
| `.data` | 5 | 1,815 |
| `.importer` | 4 | 520 |
| `.design` | 2 | 269 |
| `.ui` | 3 | 1,646 |

---

## 四、为什么这样移动

**每一条都对着 §1.3 的三个问题，而不是对着"标准架构"。**

1. **`.model` 收了 `ScheduleUtils`，不单独开 `.time` 包。**
   实测依赖：`Course → ScheduleUtils`、`Semester → ScheduleUtils`、`ScheduleUtils → Course`、
   `BellSchedule → ScheduleUtils`、`BellSchedules → BellSchedule`、`ScheduleUtils → BellSchedules`。
   这五个类**互相引用成一团**。拆成 `.model`（数据）+ `.time`（日历）两个包会造成
   **包级循环依赖**——编译能过，但"谁依赖谁"就说不清了。放一起，`.model` 对外的边只有 0 条（叶子），
   其它包一律单向指向它。**这是为了让依赖图无环，不是为了凑层。**

2. **`.rule` 与 `.model` 分开。**
   边界是可教的：`.model` 回答"一个东西**是什么**"（含它自身的日历算术），
   `.rule` 回答"**允许什么、算出什么**"（表单能不能存、两门课冲不冲突、这周的课怎么摆）。
   实测 `.rule → .model` 单向，不回头。5 个类各有清晰的输入输出，全部可纯 JVM 测试。

3. **`CourseList` 放进 `.data` 而不是 `.rule`。**
   它做的是"往集合里增删改查 + 分配 id"，是**存储的贴身操作**（`upsert` 存的是副本，
   `ensureId` 防 id 撞车——都是为了让写回 SharedPreferences 的载荷正确）。
   实测只有 `CourseRepository` 引用它。放进 `.data` 后，`data → rule` 这条边**整条消失**，
   `.rule` 变成只依赖 `.model` 的干净一层。

4. **`.importer` 单独成包，因为它是唯一的外部数据入口。**
   4 个类、520 行、一条完整的"文件 → 课程列表"管线，而且 `ExcelTimetableReader` 是
   全项目唯一依赖外部 jar（`jxl-2.6.12.jar`）的地方。单独成包后，
   "教务系统的文件是怎么读进来的"一眼可见。**不放进 `.data`**：`.data` 是"存我们自己的数据"，
   这里是"读别人的文件"，混在一起会让"换个存储方案要动哪些文件"变得不清楚。

5. **`.design` 与 `.ui` 分成两个包——而不是把调色板塞进 `.ui`。**
   实测有**三个包**在 import `Palette`：`.ui`（UiKit/Insets）、`.data`（`CourseRepository` 的样例课程取色）、
   `.importer`（`TimetableImportParser.colorFor` 分配课程颜色）。
   如果把 `Palette` 放进 `.ui`，就会出现 `data → ui` 和 `importer → ui` 两条边——
   "存储层和导入器依赖界面层"读起来是错的。把颜色抽成 `.design` 后，
   那两条边变成 `data → design`、`importer → design`，**"取一个颜色"不再等于"依赖界面"**。

6. **`UiTokens` 跟 `UiKit` 一起放 `.ui`，而不是进 `.design`——这是本方案唯一一处"不完美"。**
   `UiTokens.px(Context, dp)`（`UiTokens.java:137`）直接调 `UiKit.dp(context, dp)`，
   也就是 **`.design → .ui`**；而 `UiKit` 又 import 了 `UiTokens`（`.ui → .design`），
   两边放一起就成环。三种处理方式：

   | 方案 | 结果 | 评价 |
   |---|---|---|
   | **A（推荐）** `UiTokens` 进 `.ui` | 零代码改动，无环 | 代价：颜色在 `.design`、尺寸在 `.ui`，"设计系统"分两处 |
   | B `UiTokens` 进 `.design`，并把 `px()` 改成自己算（不再委托 `UiKit.dp`） | 设计系统收在一处，无环 | 代价：**要改 1 个方法体**（行为完全等价，但违背"只移动文件"） |
   | C `UiTokens` 进 `.design`，保留委托 | 设计系统收在一处 | 代价：`.design ↔ .ui` **包级循环** |

   推荐 A：**本阶段"不改代码"的约束优先于"设计系统收在一处"的整洁度**。
   如果你更看重后者，选 B——它是全项目唯一需要的代码改动，只影响 `px()` 一个方法（详见 §八 R1）。

7. **没有做的事（刻意）**：
   - 不开 `activity`/`fragment` 包——7 个 Activity 就是 7 个文件，再套一层只是多一次点击。
   - 不开 `util`/`common` 包——`ScheduleUtils` 有明确归属（`.model`），
     `util` 是会无限膨胀的垃圾桶。
   - 不把 `Palette`/`UiTokens` 拆成 `ColorScheme`/`Typography`/`Shapes` 等多文件——
     那是在模仿 Compose 的 API 形状，本项目不用 Compose。
   - 不引入任何接口/实现分离、不引入 DI。

---

## 五、依赖关系

### 5.1 类级依赖（实测：扫描每个文件去掉注释后的代码里出现的其它生产类名）

```
BellSchedule                 -> ScheduleUtils
BellScheduleEditor           -> BellSchedule, BellSchedules
BellScheduleJson             -> BellSchedule, BellSchedules, MiniJson
BellSchedules                -> BellSchedule
Colors                       -> (无)
Course                       -> ScheduleUtils
CourseConflicts              -> Course, ScheduleUtils
CourseFormValidator          -> ScheduleUtils
CourseList                   -> Course
CourseQuery                  -> Course, ScheduleUtils
CourseRepository             -> BellSchedule, BellScheduleJson, BellSchedules, Colors, Course,
                                CourseList, MiniJson, Palette, ScheduleUtils, Semester, SemesterJson
EditCourseActivity           -> BellSchedule, Course, CourseConflicts, CourseFormValidator,
                                CourseRepository, Insets, Palette, ScheduleUtils, UiKit, UiTokens
EditScheduleActivity         -> BellSchedule, BellScheduleEditor, BellSchedules, CourseRepository,
                                Insets, Palette, UiKit, UiTokens
ExcelTimetableReader         -> Course, TimetableGrid, TimetableImportException, TimetableImportParser
Insets                       -> Palette
MainActivity                 -> BellSchedule, BellSchedules, Course, CourseConflicts, CourseQuery,
                                CourseRepository, EditCourseActivity, ExcelTimetableReader, Insets,
                                Palette, ScheduleUtils, Semester, SemesterListActivity,
                                SettingsActivity, TimetableImportException, TimetableImportParser,
                                UiKit, UiTokens, WeekGridActivity
MiniJson                     -> (无)
Palette                      -> Course
ScheduleListActivity         -> BellSchedule, BellSchedules, Course, CourseRepository,
                                EditScheduleActivity, Insets, Palette, UiKit, UiTokens
ScheduleUtils                -> BellSchedules, Course
Semester                     -> ScheduleUtils
SemesterJson                 -> MiniJson, ScheduleUtils, Semester
SemesterListActivity         -> CourseRepository, Insets, Palette, ScheduleUtils, Semester, UiKit, UiTokens
SettingsActivity             -> BellSchedule, Course, CourseRepository, ExcelTimetableReader, Insets,
                                Palette, ScheduleListActivity, ScheduleUtils, Semester,
                                SemesterListActivity, TimetableImportException, TimetableImportParser,
                                UiKit, UiTokens
TimetableGrid                -> (无)
TimetableImportException     -> (无)
TimetableImportParser        -> Course, Palette, ScheduleUtils, TimetableGrid, TimetableImportException
UiKit                        -> Colors, Palette, UiTokens
UiTokens                     -> UiKit          ← 唯一的一处"回指"，见 §四.6
WeekGridActivity             -> Course, CourseQuery, CourseRepository, EditCourseActivity, Insets,
                                Palette, ScheduleUtils, UiKit, UiTokens, WeekGridPlan
WeekGridPlan                 -> Course
```

**读法**：整个项目只有 1 处循环（`UiKit ↔ UiTokens`，同包内，无害），
其余全部是单向的。**当前架构本身是干净的，缺的只是把它显式化。**

### 5.2 包级依赖（移动后）

```
com.weihe.schedule ──► .ui ──────────► .design ──► .model
        │                │                ▲            ▲
        │                └────────────────┼────────────┘
        ├──► .data ──────────────────────┘
        │        └───────────────────────┐
        ├──► .rule ──► .model            │
        │                                │
        └──► .importer ──────────────────┘
```

- 7 个包，**6 条边，无环**。
- `.model` 是叶子（0 条出边）。
- **10 个依赖 Android 的类全部集中在根包 + `.ui` + `.data` 的 `CourseRepository`**，
  `.model` / `.rule` / `.importer` / `.design` 四个包**共 16 个类完全不依赖 Android**——
  移动之后，"哪部分能在 JVM 上测"从目录结构就能看出来。

### 5.3 Activity 之间的依赖（实测，仅 3 条）

| 从 | 到 | 位置 | 用途 |
|---|---|---|---|
| `MainActivity` | `SettingsActivity` | `MainActivity.java:281` | 右上角齿轮 |
| `MainActivity` | `SemesterListActivity` | `MainActivity.java:743` | 学期整行 |
| `MainActivity` | `WeekGridActivity` | `MainActivity.java:737` | 右下角总览 |
| `MainActivity` | `EditCourseActivity` | `MainActivity.java:1300` | 点课程卡片 |
| `WeekGridActivity` | `EditCourseActivity` | `WeekGridActivity.java:308` | 总览页 `+` / 点课程块 |
| `SettingsActivity` | `SemesterListActivity` | `SettingsActivity.java:210` | 「查看与切换学期」 |
| `SettingsActivity` | `ScheduleListActivity` | `SettingsActivity.java:241` | 「管理作息表」 |
| `ScheduleListActivity` | `EditScheduleActivity` | `ScheduleListActivity.java:215` | 点某张作息表 |

**7 个 Activity 互相直接引用（`new Intent(this, XxxActivity.class)`），没有路由层。**
因为全部留在根包，这些引用**不需要任何 import 改动**——这也是把 Activity 留在根包的第二个好处。

### 5.4 UI 组件之间的依赖

`UiKit` 内部只有一个真实依赖：`UiTokens`（尺寸）与 `Palette`/`Colors`（颜色）。
`UiKit` **不依赖任何 Activity、不依赖 `Course`**——注意它的颜色方法是
`courseTint(int courseColor)`，收的是 `int` 而不是 `Course`，这层解耦是现成的，
移动之后 `.ui` 不依赖 `.model` 这一点会保持住。

`UiKit` 的 5 个公开嵌套类型（`PressFeedback`、`Action`、`Success`、`Check`、`Choice`，
`UiKit.java:836/1033/1074/1231/1414`）全部是 `public`，
所以 7 个 Activity 跨包使用它们**不会**有任何访问问题。

### 5.5 数据层与规则层的依赖

`CourseRepository`（`.data`）依赖：`CourseList`、`MiniJson`、`SemesterJson`、`BellScheduleJson`（同包）
+ `Course`、`Semester`、`BellSchedule`、`BellSchedules`、`ScheduleUtils`（`.model`）
+ `Palette`、`Colors`（`.design`）。
**不依赖 `.rule` 的任何东西**——`CourseQuery`/`CourseConflicts`/`WeekGridPlan` 一个都没用到。
`.rule` 只依赖 `.model`。**两条链路完全分开，这是当前架构最好的一点，移动后会被目录结构显式表达出来。**

---

## 六、package-private 风险

**这是移动文件唯一会真正导致编译失败的地方**，所以逐个列出。实测结果：

### 6.1 全部包级私有（无修饰符）成员

| 类 | 成员 | 是否跨文件使用 | 风险 |
|---|---|---|---|
| `CourseRepository` | `CourseRepository(SharedPreferences)` 构造器（`:65`） | ✅ **被 `CourseRepositoryTest:56` 使用** | **高** |
| `CourseRepository` | `static void useStoreForNextInstance(SharedPreferences)`（`:81`） | ✅ **被 `CourseRepositoryTest:55` 使用** | **高** |
| `WeekGridActivity` | `static int lastPeriodInUse(List<Course>)`（`:524`） | ✅ **被 `WeekGridRowsTest` 使用**（13 处） | 中 |
| `WeekGridActivity` | `static int dayColumnRows(int)`（`:509`） | ✅ **被 `WeekGridRowsTest` 使用**（5 处） | 中 |
| `WeekGridActivity` | `SyncedHorizontalScrollView(...)`（`:903`） | ❌ 仅本文件 | 无 |
| `WeekGridActivity` | `SyncedVerticalScrollView(...)`（`:918`） | ❌ 仅本文件 | 无 |
| `CourseFormValidator` | `Problem(Field, Reason)`（`:56`） | ❌ 仅 `CourseFormValidator` 自己 `new` | 无 |
| `BellScheduleEditor` | `Problem(int, Reason)`（`:38`） | ❌ 仅 `BellScheduleEditor` 自己 `new` | 无 |
| `WeekGridPlan` | `Cluster(...)`（`:49`）/ `Cell(...)`（`:116`）/ `Block(...)`（`:183`） | ❌ 仅 `WeekGridPlan` 自己 `new` | 无 |

**只有 4 个成员真正跨文件**，而且全部只被**测试**使用，没有一个被生产代码跨文件使用。

### 6.2 三个具体的处理结论

1. **`WeekGridActivity` 的两个静态方法——不受影响。**
   它们所在类留在根包，`WeekGridRowsTest` 也留在根包，包名一致 → 编译通过。
   **这是"Activity 留在根包"的第三个好处**：上一轮刚把它们从 `private` 提为包级私有以便测试，
   如果现在把 Activity 挪进子包，就得连测试一起挪，或者把它们改成 `public`（破坏封装意图）。

2. **`CourseRepository` 的两个成员——必须把测试一起搬。**
   `CourseRepositoryTest` 和 `FakePreferences` 要移到 `com.weihe.schedule.data`。
   Java 的包级私有访问按**包名**判定，测试源集与主源集只要包名相同即可（AGP 单元测试里
   主类和测试类在同一个 classpath / 同一个 classloader 上，这一模式是可行的）。
   **不要**为了省事把它们改成 `public`——`useStoreForNextInstance` 的 javadoc 明确写着
   "这是给测试用的缝，App 永远不会调用它"，改成 public 就把这个意图抹掉了。

3. **`Problem` / `Cluster` / `Cell` / `Block` 的包级私有构造器——零风险。**
   它们只在自己的外部类里 `new`，跟着外部类一起搬即可；
   外部只通过公开方法（`field()` / `reason()` / `course()` / `lane()` …）读取，
   这些方法实测全部是 `public`。

### 6.3 `protected` 成员

全部 9 处都是 Android 生命周期/框架回调的重写，**没有任何一处被应用代码跨类调用**：

- `onCreate` / `onResume` / `onActivityResult`：6 个 Activity
- `onScrollChanged`：`WeekGridActivity` 的两个内部 `ScrollView` 子类（`:908`、`:923`）

**移动包不影响它们**（Activity 留在根包）。

---

## 七、测试迁移方案

### 7.1 18 个测试文件与生产类的对应关系（实测）

| 测试文件 | 被测生产类 | 目标包 |
|---|---|---|
| `BellScheduleTest` | `BellSchedule` | `.model` |
| `BellSchedulesTest` | `BellSchedules`（+`BellSchedule`） | `.model` |
| `ScheduleUtilsTest` | `ScheduleUtils` | `.model` |
| `SemesterTest` | `Semester` | `.model` |
| `CourseConflictsTest` | `CourseConflicts` | `.rule` |
| `CourseFormValidatorTest` | `CourseFormValidator` | `.rule` |
| `CourseQueryTest` | `CourseQuery` | `.rule` |
| `WeekGridPlanTest` | `WeekGridPlan` | `.rule` |
| `BellScheduleEditorTest` | `BellScheduleEditor` | `.rule` |
| `CourseRepositoryTest` | `CourseRepository` | `.data` ⚠️ **必须移（包级私有）** |
| `FakePreferences` | 测试替身（`SharedPreferences`） | `.data` ⚠️ 跟着上一行走 |
| `CourseListTest` | `CourseList` | `.data` |
| `MiniJsonTest` | `MiniJson` | `.data` |
| `SemesterJsonTest` | `SemesterJson` | `.data` |
| `BellScheduleJsonTest` | `BellScheduleJson` | `.data` |
| `TimetableImportParserTest` | `TimetableImportParser` | `.importer` |
| `PaletteContrastTest` | `Palette` + `Colors` + `UiTokens` | `.design`（见下） |
| `WeekGridRowsTest` | `WeekGridActivity` | **根包，不动** |

**18 个测试里只有 1 个必须移动**（`CourseRepositoryTest` + `FakePreferences`），
其余 17 个**是为了跟被测类放在一起而移动**——这不是必须的（`public` 类跨包可见），
但"测试和被测类同包"是这个项目已经形成的约定，保持它最省心。

### 7.2 `PaletteContrastTest` 的特殊处理

它同时测三个类，而这三个类会落到两个包：

| 被测 | 目标包 |
|---|---|
| `Palette` | `.design` |
| `Colors` | `.design` |
| `UiTokens` | `.ui` |

**建议放进 `.design`**（它的主体是配色对比度，`UiTokens` 只占其中 2 个测试方法），
补一句 `import com.weihe.schedule.ui.UiTokens;` 即可。
如果按 §四.6 选了方案 B（`UiTokens` 也进 `.design`），这个 import 就不需要了。

### 7.3 测试源集的目录结构

```
app/src/test/java/com/weihe/schedule/           ← 只剩 WeekGridRowsTest
app/src/test/java/com/weihe/schedule/model/     ← 4 个
app/src/test/java/com/weihe/schedule/rule/      ← 5 个
app/src/test/java/com/weihe/schedule/data/      ← 6 个（含 FakePreferences）
app/src/test/java/com/weihe/schedule/importer/  ← 1 个
app/src/test/java/com/weihe/schedule/design/    ← 1 个
```

### 7.4 测试结果的"行为不变"判据

**每一步之后，`testDebugUnitTest` 必须仍然是 `tests=239 failures=0 errors=0`。**
数量不能少：少一个说明有测试没被发现（放错目录），多一个说明有重复。

---

## 八、潜在风险

按"会不会在编译期被抓到"排序——**能被编译期抓到的风险都是低风险**。

| # | 风险 | 会被编译期抓到？ | 说明与处理 |
|---|---|---|---|
| **R1** | §四.6 方案 B 需要改 `UiTokens.px()` 的方法体 | ✅ | 若选方案 A（推荐），**全程零代码改动**，只有文件移动 + import。若选 B，改的是 1 个方法，行为逐位等价 |
| **R2** | 补 import 时漏一个 | ✅ | 立刻 `cannot find symbol` |
| **R3** | `UiKit` 忘了 `import com.weihe.schedule.R;` | ✅ | `UiKit.java:730/1021/1218` 用了 `R.drawable`/`R.style`，漏了就是 `cannot find symbol: R` |
| **R4** | **Activity 若移出根包，Manifest 的 `.XxxActivity` 需要改成全限定名** | ❌ **编译期抓不到！** | 装机启动才崩 `ClassNotFoundException`。**本方案通过"Activity 留在根包"从根本上规避**（§三.①）。如果将来想给 Activity 单独开包，这是一个必须单独验证的步骤 |
| **R5** | 移动 `CourseRepositoryTest` 时忘了 `FakePreferences` | ✅ | `FakePreferences` 是 `public`，可以留原地；但两个文件放一起更清楚 |
| **R6** | 45 处 javadoc `{@link Xxx}` 跨包后不再解析 | ❌ **静默失效** | `javac` 不校验 javadoc；生成文档或 IDE 悬浮提示时才看出问题。**处理：移动完用 `grep -c '{@link '` 对一遍**（分布见下） |
| **R7** | `ARCHITECTURE.md` 里描述的单包结构、以及第 341-345 行"按列起点比对"的调试建议，会与新结构不符 | ❌ 静默 | 属于文档同步，建议在**全部移动完成后的单独一次提交**里改，不要夹在移动步骤里 |
| **R8** | `MainActivity` 里约 137 行**已死的导入代码**仍引用 `.importer` 的 3 个类 | ✅ | 移动 `.importer` 时 `MainActivity` 要跟着补 import。**本次不要顺手删**——那是独立的功能性改动，应单独一次提交（见 §九 步骤 8） |
| **R9** | 同一个类被两个目标包"争抢"（例如 `WeekGridPlan` 既可算 `.rule` 也可算 `.ui`） | — | 已按"它不依赖 Android 且被测试覆盖"归入 `.rule`。若日后觉得别扭，单独再议 |
| **R10** | Git 重命名检测 | ❌ | 用 `git mv`（而不是删除 + 新建）可以让 `git log --follow` 继续追踪历史。**每步用 `git mv`** |

**R6 的具体分布**（45 处 `{@link 生产类名}`，跨包后会失效，需要补全限定名或留作待办）：

| 文件 | 处数 | | 文件 | 处数 |
|---|---|---|---|---|
| `UiKit` | 7 | | `BellScheduleJson` | 3 |
| `CourseRepository` | 5 | | `EditCourseActivity` | 3 |
| `MainActivity` | 5 | | 其余 14 个文件 | 各 1–2 |

**不存在的风险（已确认）**：
- 不会改变 `SharedPreferences` 的键名或 JSON 结构（§三 全部是移动 + import）。
- 不会改变任何时间数据（`BellSchedules` 的 45 个整数一个都不动）。
- 不会影响 `R` 的生成（`namespace` 不变）。
- 不会影响 `app/build.gradle`（不需改一行）。
- 不会影响 ProGuard（`minifyEnabled false`，`proguard-rules.pro` 只有 1 行注释）。
- 不会引入 AndroidX 或任何新依赖。

---

## 九、执行顺序

**排序原则：被依赖者先走。** 因为所有类现在同包、互相按简单名引用，
移动任何一个类都会让引用方需要补 import。**先把 `.model` 移走，后面每个文件只需要补一次
`import com.weihe.schedule.model.X;`，永远不会返工。** 反之若先移 `.ui`，
等 `.model` 移动时又要把 `com.weihe.schedule.Course` 改成 `com.weihe.schedule.model.Course`——白干一遍。

```
模型（叶子，被所有人依赖）
   ↓
设计令牌（被 ui / data / importer 依赖）
   ↓
规则 → 导入器 → 存储 → 组件库（这四组之间互不依赖，顺序可换）
```

| 步骤 | 动作 | 涉及文件 | 预计改动量 |
|---|---|---|---|
| **0** | 打标签 / 确认干净基线：`git status` 空、HEAD = `1213ef7` | — | — |
| **1** | 新建 `model` 包，`git mv` 5 个类过去，给**所有引用方补 import**（约 25 个文件） | 5 移 + ~25 改 | import ×~30 |
| **2** | 新建 `design` 包，移 `Palette`、`Colors`；补 import；`PaletteContrastTest` 移到 `test/.../design/` | 2 移 + ~12 改 + 1 测试移 | import ×~14 |
| **3** | 新建 `rule` 包，移 5 个类；补 import；5 个对应测试移到 `test/.../rule/` | 5 移 + ~8 改 + 5 测试移 | import ×~13 |
| **4** | 新建 `data` 包，移 5 个类；补 import；6 个测试（含 `FakePreferences`）移到 `test/.../data/` | 5 移 + ~6 改 + 6 测试移 | import ×~11 |
| **5** | 新建 `importer` 包，移 4 个类；补 import（**含 `MainActivity`/`SettingsActivity` 的死代码引用**）；1 个测试移过去 | 4 移 + ~4 改 + 1 测试移 | import ×~8 |
| **6** | 新建 `ui` 包，移 `UiKit`、`UiTokens`、`Insets`；补 import，**`UiKit` 还要补 `import com.weihe.schedule.R;`** | 3 移 + ~10 改 | import ×~13 |
| **7** | 收尾：跑全套（测试 + 编译 + lint）；修 45 处 javadoc `{@link}`；更新 `ARCHITECTURE.md` 的目录树 | — | 文档 |
| **8** | **（可选，独立提交）** 删除 `MainActivity:1159-1295` 的 137 行死导入代码 + 5 个孤儿 import | 1 改 | 净 −137 行 |

**每一步都必须是一次独立、可编译、测试全绿的提交。** 任一步不绿就停下修，不要继续往下走。

> **关于步骤 8**：它属于"删代码"而不是"移动代码"，用户本轮明确限定为结构整理，
> 所以**单独列出、默认不做**。但它和步骤 5 有耦合（`.` 里那 3 个 import 只被死代码用到），
> 所以放在最后、且知道它的存在。

---

## 十、每一步完成后的验证方式

### 10.1 通用验证（每一步都要跑）

本项目的构建命令（宿主 `~/.gradle` 不可写，用工作区内的本地 Gradle）：

```powershell
cd 'D:\vibe coding\Weihe\Weihe'
$env:GRADLE_USER_HOME='D:\vibe coding\Weihe\Weihe\.gradle-local\home'
$env:ANDROID_HOME='D:\Sdk'; $env:ANDROID_SDK_ROOT='D:\Sdk'
& '.\.gradle-local\bin\gradle.bat' "-Dorg.gradle.vfs.watch=false" testDebugUnitTest assembleDebug lintDebug
```

> 注意 `"-Dorg.gradle.vfs.watch=false"` 必须带引号，否则 PowerShell 会把它当成任务名。
> 另外本项目**不能用 `--offline`**（工作区内的 Gradle 缓存是 `~/.gradle` 的部分拷贝）。

**通过判据（三个都要满足）**：

| 检查 | 期望值 | 为什么是这个值 |
|---|---|---|
| `testDebugUnitTest` | `suites=17 tests=239 failures=0 errors=0` | **数量与基线完全一致**才说明行为没变、测试没被漏掉 |
| `assembleDebug` | `BUILD SUCCESSFUL`，产出 `app/build/outputs/apk/debug/app-debug.apk` | 编译通过 |
| `lintDebug` | `No issues found.` | 基线是 0 error 0 warning，不能因为移动而新增 |

读取测试数量的命令：

```powershell
$files = Get-ChildItem app\build\test-results\testDebugUnitTest\*.xml
$t=0;$f=0;$e=0
foreach ($x in $files) { $d=[xml][System.IO.File]::ReadAllText($x.FullName)
  $t+=[int]$d.testsuite.tests; $f+=[int]$d.testsuite.failures; $e+=[int]$d.testsuite.errors }
"suites=$($files.Count) tests=$t failures=$f errors=$e"
```

### 10.2 每一步的额外检查

| 步骤 | 除通用验证外还要查 |
|---|---|
| **1（model）** | `grep -rn "com.weihe.schedule.model" app/src` 确认引用方都补了 import；确认 `app/src/main/java/com/weihe/schedule/model/` 下正好 5 个文件 |
| **2（design）** | `PaletteContrastTest` 仍在 239 之内；用 `Select-String -Pattern '\bPalette\b'` 扫一遍 `app/src`，确认没有文件按简单名引用 `Palette` 却漏了 import |
| **3（rule）** | 5 个测试文件已在新目录；`test/.../rule/` 下正好 5 个 |
| **4（data）** | **`CourseRepositoryTest` 必须与新 `.data` 包同名**，否则会报 `CourseRepository(SharedPreferences) is not public in CourseRepository`。这是本方案唯一会因包级私有而失败的点 |
| **5（importer）** | `MainActivity` 与 `SettingsActivity` 都编译过（它们的死代码引用了这 3 个类） |
| **6（ui）** | `UiKit` 能编译（`R` 的 import）；APK 能产出 |
| **7（收尾）** | 45 处 `{@link}` 逐个检查；`ARCHITECTURE.md` 的目录树与依赖图更新 |
| **每步之后** | `git status` 只显示预期的重命名与修改；用 `git mv` 保证 `git log --follow` 可追 |

### 10.3 移动完成后的"结构自检"

对着最终目录问四个问题，答案应该都是一句话：

1. **"我要改界面上的一个卡片"** → `ui/UiKit.java`
2. **"我要改课程的数据字段"** → `model/Course.java`
3. **"我要改什么算一节冲突"** → `rule/CourseConflicts.java`
4. **"我要改存到哪"** → `data/CourseRepository.java`

如果某个问题要翻两个以上的包才能回答，说明包边界画错了。

### 10.4 真机验证（可选，但建议在步骤 7 之后做一次）

装上 APK，走一遍 `ARCHITECTURE.md:317-352` 的上机回归清单（至少：首页 → 设置 → 学期 → 作息表 → 总览 → 返回）。
**重点看两件事**：7 个 Activity 都能被启动（验证 R4 没有被触发），以及作息表的重命名/删除仍然正常
（那是上一轮刚修好的 P0，移动后必须仍然工作）。

---

## 附：本方案没有做的事（明确划界）

| 不做 | 原因 |
|---|---|
| 不迁 Kotlin / Compose / XML / Room / AndroidX | 用户明确要求 |
| 不引入 ViewModel / Repository 接口 / DI | 会新增层，且要引入新框架 |
| 不拆 `UiKit`（1432 行） | 那是"拆类"，不是"整理包结构"，风险与工作量都高一个量级；属于独立提案 |
| 不删 `MainActivity` 的死导入代码 | 是功能性改动，不是结构整理；已在 §九 步骤 8 单列 |
| 不改 `Course` 的字段、不改 `BellSchedules` 的时间、不改 SharedPreferences 的键 | 用户明确要求 |
| 不改任何测试的断言 | 239 项是行为不变的判据，改了就失去判据 |
| 不按"标准分层架构"造 `domain`/`usecase`/`repository-impl` 等包 | 本项目 10K 行、单人维护，增加这些只会增加跳转次数 |

---

**本次仅分析，未修改项目代码。**
本文档是唯一新增文件；31 个生产源文件、18 个测试文件、`AndroidManifest.xml`、
`app/build.gradle`、`res/`、`libs/`、`proguard-rules.pro` 全部未改动，未运行构建。
