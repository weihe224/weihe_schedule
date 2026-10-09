# NEXT_OPTIMIZATION_ROADMAP.md — 「拾课」下一阶段优化路线

> **本文只做分析与规划。未修改任何业务代码、未新增依赖、未执行重构。**
>
> **核查对象**：`D:\vibe coding\Weihe\Weihe`，分支 `refactor/project-structure`，
> HEAD = `efe5ace`（7 个提交的包结构整理 + 文档）。
>
> **所有结论以当前源码为准**，逐条标注 `文件:行号`，并已逐项复核上一轮报告
> （`PROJECT_ANALYSIS.md` / `PROJECT_ARCHITECTURE.md`）中的结论**是否仍然成立**。
> 复核结果见 §1.3——**上一轮的 5 个 P0 与 M12 已全部修复，不得再当作未决问题**。
>
> **未实际验证的一律标注「待确认」**，不把推测写成事实。

---

## 〇、工作区状态核查（先做，未假定）

### 0.1 Git

```
分支        refactor/project-structure
HEAD        efe5ace  docs: record the new package structure and the refactor that produced it
工作区      ⚠️ 有两个未提交的删除：
              D  ARCHITECTURE.md
              D  ARCHITECTURE_REFACTOR_PLAN.md
```

**这两个文件在工作区已被删除，但在 HEAD 中仍然存在**（`git status` 显示 ` D`，未暂存）。
它们的内容我是用 `git show HEAD:ARCHITECTURE.md` 读的。

> **需要你确认这是有意为之还是误删。** 本文没有恢复它们——恢复文件属于改动工作区，
> 不在本轮授权范围内。若是有意删除，建议连同这次删除一起提交，否则后续任何
> `git checkout .` 都会把它们还原回来。另外 `PROJECT_ARCHITECTURE.md` §一 与
> `ARCHITECTURE.md` §一 都记录包结构，删掉后者会让"分层与依赖方向"只剩一份描述。

### 0.2 包结构（与上一轮整理结果一致）

```
com.weihe.schedule              7 个 Activity
com.weihe.schedule.model        5   Course Semester ScheduleUtils BellSchedule BellSchedules
com.weihe.schedule.rule         5   CourseQuery CourseConflicts CourseFormValidator
                                    BellScheduleEditor WeekGridPlan
com.weihe.schedule.data         5   CourseRepository CourseList MiniJson SemesterJson
                                    BellScheduleJson
com.weihe.schedule.importer     4   TimetableGrid TimetableImportParser
                                    ExcelTimetableReader TimetableImportException
com.weihe.schedule.design       2   Palette Colors
com.weihe.schedule.ui           3   UiKit UiTokens Insets
```

### 0.3 测试与构建基线

| 项 | 实测值 |
|---|---|
| 测试源集 | 18 个文件（17 个测试类 + `FakePreferences`），**239 个 `@Test`** |
| `androidTest` 源集 | **不存在**（无仪器测试） |
| 上一轮验证的构建 | `testDebugUnitTest` + `assembleDebug` + `lintDebug` 全绿（`efe5ace` 时点） |

**按包分布**：`.data` 5 个类 70 项 · `.model` 4 个类 67 项 · `.rule` 5 个类 76 项 ·
`.design` 1 个类 14 项 · `.importer` 1 个类 7 项 · 根包 1 个类 5 项。

---

## 一、测试可靠性

### 1.1 现状：覆盖是"纯逻辑强、边界弱、UI 零"

| 被测量 | 结果 |
|---|---|
| **有代码级测试覆盖的生产类** | **22 / 31**（剔除注释后逐类扫描，`javadoc` 里的提及不算覆盖） |
| **零覆盖的 9 个** | `UiKit` `Insets` `ExcelTimetableReader` + `MainActivity` `EditCourseActivity` `EditScheduleActivity` `SettingsActivity` `SemesterListActivity` `ScheduleListActivity` |
| 零覆盖的性质 | **全部是"依赖 Android 因而不可能在 JVM 上测"的类**——这是一致的设计后果，不是疏漏；但它也说明**这 9 个类目前没有任何自动化保护**，只能靠 `ARCHITECTURE.md` §五 的 19 项手工清单 |

### 1.2 候选任务

#### T1 — 导入管线：唯一读文件的类零测试，且丢弃完全静默 【P0】

| 项 | 内容 |
|---|---|
| **是否已实现** | 部分。`TimetableImportParser` 有 **7 个**测试；`ExcelTimetableReader` **0 个** |
| **源文件** | `importer/ExcelTimetableReader`（55 行）· `importer/TimetableImportParser`（370 行）· `importer/TimetableGrid` · `importer/TimetableImportException` · 测试 `importer/TimetableImportParserTest` |
| **证据 ①** | 测试源集中**没有任何文件**引用 `ExcelTimetableReader`。它是全项目唯一读取外部文件、唯一依赖 `jxl` 的类，却完全没有测试 |
| **证据 ②** | `TimetableImportParser` 全文有 **8 处 `continue;`**，且**没有任何计数器变量**——表头前的文本、不匹配 `DETAIL` 正则的行、周次不可解析/越界的行，全部静默丢弃 |
| **证据 ③** | `TimetableImportParserTest` 的 7 个用例全是正向解析（合并单元格、一格两课、连续周合并、单双周、`shiftWeeks` ×3）。**没有一个用例覆盖丢弃路径** |
| **现存问题** | 部分失败的导入**无法被察觉**。用户在设置里导入，界面上只看到"识别到 N 门课程"（`SettingsActivity:458`），而 N 是多少、丢了多少行、为什么丢，全都无从得知 |
| **用户收益** | 高——"我的课表导入后少了几门课"是这类功能最常见的投诉，目前既无法告知也无法自查 |
| **实现难度** | **低-中**。解析器加一个返回值（行数统计 + 丢弃原因列表），不改现有 `parse` 的输出语义；`ExcelTimetableReader` 的测试需要一个小 `.xls` 夹具，可用 `jxl` 自己在测试里写出来（不引入新依赖） |
| **回归风险** | **低**。新增返回值是加法；`ExcelTimetableReaderTest` 是纯新增 |
| **必须增加的测试** | ① `ExcelTimetableReader.read` 用 jxl 现场写一个最小 `.xls` 再读回，断言 `TimetableGrid` 的行列与合并区；② 非 `.xls`（zip/xlsx）输入抛出 `TimetableImportException` 且消息可读；③ 解析器对"表头前有文字""细节列只有 1 个空格""整行周次非法"三种丢弃各有一条用例，断言**丢弃被计数**而不是静默；④ 空表 vs 全丢弃两种情况可区分 |
| **推荐顺序** | **第 2 位**（与 P4 合并交付，见 §6.2） |

#### T2 — 存储写入耐久性：只 `apply()`、从不 `commit()` 【P1】

| 项 | 内容 |
|---|---|
| **是否已实现** | 写入本身已实现且正确；**耐久性语义未验证** |
| **源文件** | `data/CourseRepository`（10 处写入）· 测试 `data/FakePreferences` |
| **证据 ①** | 全项目 `.apply()` = **10 处**，`.commit()` = **0 处**（`CourseRepository:151,239,268,320,351,467,513,707,903,939`） |
| **证据 ②** | `FakePreferences.apply()`（`FakePreferences:167`）**同步写进 `HashMap`**，而真实 `SharedPreferences.apply()` 只同步写内存、**异步落盘** |
| **现存问题** | 测试替身的语义比真实实现更"强"，因此**测试永远发现不了**"写完就被 kill、磁盘上没有"这一类问题。Android 只在进程正常结束前保证 flush 挂起的 `apply()`，被强杀或崩溃时不保证 |
| **用户收益** | 中——影响的是极小概率的丢写窗口，但发生在"导入覆盖"这类不可逆操作上时后果最重 |
| **实现难度** | **低**。只对不可逆的写（导入覆盖、删除学期、恢复备份）改用 `commit()` 并在成功后给出反馈；其余保持 `apply()`（那是官方推荐） |
| **回归风险** | **低-中**。`commit()` 在主线程同步落盘——本项目的载荷是几十 KB 级 JSON，可接受；但**必须在真机上量一次**确认无卡顿 |
| **必须增加的测试** | ① `FakePreferences` 增加一个可切换的"延迟落盘"模式（`apply()` 只入 pending，`commit()` 才可见），断言仓库的关键路径用的是 `commit`；② 一条反向断言：普通写仍走 `apply`（避免为了"安全"把所有写都变成同步） |
| **推荐顺序** | 与 D1 一起做（它们是同一次改动的两半） |

#### T3 — UI 回归零自动化，但工具原语已经就位 【P1】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否**，但有现成基础 |
| **源文件** | `tools/dump-ui.ps1`（1.4 KB，`uiautomator dump` → 打印带 bounds 的节点树）· `tools/tap-ui.ps1`（2.1 KB，按 text/content-desc 查 bounds 再点击） |
| **证据 ①** | `app/src/androidTest` **不存在**；`UiKit`（1435 行）与 7 个 Activity 的测试引用数均为 **0** |
| **证据 ②** | `ARCHITECTURE.md` §五 有一份 **19 项的逐屏手工回归清单**（`git show HEAD:ARCHITECTURE.md` 第 341-390 行），其中第 0、3、8、9、14 项都是**过去真实崩过或错位过**的场景，例如"总课表与单周课表反复切换十次""单独拖表头，表头和表身都必须纹丝不动""杀掉进程重进数据仍在" |
| **证据 ③** | 该清单第 17 项写的是"**221 项测试全绿**"——数字已过期（现 239），说明这份清单**没有随代码演进被维护** |
| **现存问题** | 项目里唯一无自动化的大类就是 UI。清单靠人肉执行，一旦漏跑，`ARCHITECTURE.md` §四 记录的 9 个"踩过的坑"就可能复发（这些坑**全部**是 JVM 测试测不到的） |
| **用户收益** | 高——它保护的是**未来所有改动**（尤其 §4 的深色模式改造会碰 274 处颜色引用） |
| **实现难度** | **中**。用已有的 `dump-ui.ps1`/`tap-ui.ps1` 原语串成一个 `tools/smoke-ui.ps1`，把清单里**可自动判定**的项固化：启动→首页有课→进出设置/学期/作息表/总览→返回→总览页在"总课表/单周"间切换 10 次不崩→`am force-stop` 后重进数据仍在。**零新增依赖**（PowerShell + adb 已在用） |
| **回归风险** | **零**——完全不碰生产代码 |
| **必须增加的测试** | 这个任务本身就是测试。产出物是脚本 + 一份"哪些项能自动判定、哪些仍需人工"的对照表 |
| **环境限制** | ⚠️ **本会话的沙箱禁止模拟器写入 `C:\Users\<user>\.android`**（`emu-last-feature-flags.protobuf.lock` 被拒），因此**这条在本环境无法验证**，需要你在本机跑一次 |
| **推荐顺序** | 第 4 位（P1 首位） |

#### T4 — 纯逻辑的边界用例 【P2，部分待确认】

| 项 | 内容 |
|---|---|
| **是否已实现** | 主体已覆盖（`WeekGridPlan` 16 例、`CourseQuery` 15 例、`CourseConflicts` 13 例） |
| **源文件** | `rule/WeekGridPlan` · `rule/CourseQuery` · `rule/CourseConflicts` |
| **现存问题（待确认）** | 上一轮报告称 `WeekGridPlan.plan` 对**列表内的 null 元素**无保护。**本轮未逐行复核这一条**，不做断言。建议用一条探测性测试确认：`WeekGridPlan.plan(Arrays.asList((Course) null))` 是否抛 NPE |
| **用户收益** | 低——`Course` 列表由 `CourseList` 维护，实际不会含 null |
| **难度/风险** | 低 / 低 |
| **推荐顺序** | 最后。属于"顺手补"，不值得单独立项 |

#### T5 — 测试自身体检 【P2】

| 项 | 内容 |
|---|---|
| **证据** | ① `CourseRepositoryTest` 顶部声明了 `KEY_BACKUP` / `KEY_SCHEDULES` 两个常量——需确认是否仍未被使用（若确实未用，是死代码）；② 上一轮提到的"同一个 `course(...)` 构造器在 3 个测试文件里各抄一份"需要复核 |
| **用户收益** | 低（可读性） |
| **难度/风险** | 低 / 低 |
| **注意** | **不得为了减少行数而重写稳定逻辑**。只做"删掉确实没用的常量"和"抽共享夹具"这类机械动作 |

#### T6 — 文档里的过期数字 【P2】

`ARCHITECTURE.md` §五 第 17 项的"221 项测试"应为 239。该文件目前还处于工作区已删除状态（见 §0.1），
**先决定它的去留再谈改数字**。

### 1.3 复核结论：上一轮报告里**已经修好**的（不得再当未决问题）

| 旧编号 | 旧结论 | 当前实际状态 | 复核依据 |
|---|---|---|---|
| **S1** | 作息表重命名不级联 | **已修复** | `data/CourseRepository.saveBellSchedule` 会调 `renameSchedule`；`EditScheduleActivity` 走这条路；`CourseRepositoryTest` 有 `savingAScheduleUnderANewNameRepointsTheCoursesThatNamedIt` 等 3 条 |
| **S2** | 可删掉最后一张作息表 | **已修复** | `CourseRepository.deleteBellSchedule` 在 `size() <= 1` 时返回 false；两个入口都有提示；`CourseRepositoryTest.theLastScheduleMayNotBeDeleted` |
| **S3** | 地点→作息匹配只认紧邻写法 | **已修复** | `model/BellSchedule` 新增 `buildingLetter` / `namesBuilding`；`BellSchedulesTest` 增加 7 条（含 D305 → D/E 的时刻断言） |
| **S4** | 总览页日列高度少一行 | **已修复** | `WeekGridActivity.dayColumnRows` + `rowSpan(...)`；`WeekGridRowsTest.aDayColumnIsTallEnoughForTheLastPeriodItDraws` 逐节断言 |
| **S5 / M12** | 用数组下标定位作息表 | **已修复** | `EditScheduleActivity.EXTRA_NAME` 取代 `EXTRA_INDEX`；保存/删除都按名字 |
| **—** | 单包 31 个类 | **已整理** | 7 个包（§0.2） |

---

## 二、数据安全

### 2.1 候选任务

#### D1 — 导入是**破坏性全覆盖且不可撤销** 【P0】★

| 项 | 内容 |
|---|---|
| **是否已实现** | 导入功能已实现；**安全网完全没有** |
| **源文件** | `SettingsActivity.confirmImport` · `data/CourseRepository.replaceCourses`（`:555-569`） |
| **证据 ①** | `SettingsActivity:499` 直接 `repository.replaceCourses(shifted)`，按钮文案是"覆盖导入" |
| **证据 ②** | `CourseRepository.replaceCourses:555-569` 的做法是：逐条 `copy()`、补 id、**清空 `scheduleName`**、然后 `saveCourses(courses)`。**没有任何备份、没有撤销入口、没有历史版本** |
| **证据 ③** | 全项目搜索 `ACTION_SEND` / `createChooser` = **0 处**；也就是说覆盖之后，旧课表**在 App 内没有任何办法找回** |
| **现存问题** | 用户选错文件、或多选对话框里选错、或把"起始周"设错，**当前课表即刻永久消失**。这是本 App 唯一一条"一次误操作 = 数据永久丢失"的路径 |
| **用户收益** | **极高**——直接把最坏的失败模式从"永久丢失"降级为"点一下撤销" |
| **实现难度** | **低**。在 `replaceCourses` 写盘前，把当前学期课表的**原始 JSON 字符串**存到 `courses_previous:<semesterId>`；设置页在快照存在时显示"撤销上次导入"。一个方法 + 一个入口 |
| **回归风险** | **低**。纯新增键，不改 `courses:<id>` 的格式与语义；快照只保留最近一份，不会无限增长 |
| **必须增加的测试** | ① 导入后旧数据可从快照完整恢复（逐字段）；② 快照只保留最近一次（第二次导入覆盖第一次的快照）；③ 撤销一次之后再撤销无效（快照被清）；④ 快照与当前学期绑定——切到别的学期看不到、也不误用它的快照；⑤ 撤销后 `courses:<id>` 的 JSON 与导入前**逐字节相同** |
| **推荐顺序** | **第 1 位**（见 §6.1） |

#### D2 — 课表导不出、带不走 【P1】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否** |
| **源文件** | 无（`SettingsActivity` 现有 5 张卡片：批量导入课表 / 学期 / 开学第一周 / 作息时间与节次 / 关于） |
| **证据** | 全项目无 `ACTION_SEND`、无 `createChooser`、无 `ACTION_CREATE_DOCUMENT` 的写用法、无 `WRITE_EXTERNAL_STORAGE`/`MediaStore`。数据只能靠 `adb shell run-as` 手工取出 |
| **现存问题** | 换手机、留档、把课表发给同学、导入前先备份——**全都做不到**。用户的数据事实上被锁在 App 里 |
| **用户收益** | 高——这是"数据安全"里唯一还能主动改善的能力缺口 |
| **实现难度** | **低-中**。`ACTION_CREATE_DOCUMENT` 选目标文件 + 用现有 `MiniJson` 序列化。**不改变存储格式**：导出是只读派生格式，`courses:<id>` 的结构一个字节都不动 |
| **回归风险** | **低**。纯新增；`MainActivity` 里那段死导入代码（`openImportPicker`，`:1176`）正好是不必模仿的反面教材——它 `startActivityForResult` 却**没有 `onActivityResult`** |
| **必须增加的测试** | ① 导出序列化是纯函数，直接 JVM 测：给定课程列表 → JSON → 用 `MiniJson` 读回 → 逐字段相等；② 空课表导出仍是合法 JSON（不输出 `null`）；③ 汉字/特殊字符（`教学楼F楼F102多`、含引号的地名）往返不失真；④ 导出的 JSON 能再次喂给导入侧的转换函数（若有） |
| **备注** | 与 D1 组合后形成完整闭环：**能备份 → 能撤销 → 能带走** |

#### D3 — `courses_backup` 只写不读 【P1】

| 项 | 内容 |
|---|---|
| **是否已实现** | 写了一半 |
| **源文件** | `data/CourseRepository`：`KEY_COURSES_BACKUP`（`:25`）· `backupDamaged`（`:512-514`） |
| **证据** | `KEY_COURSES_BACKUP` 在全项目只出现 **4 次**：一次声明、一次写入（`:513`）、两次在 javadoc 里（`:286`、`:393`）。**没有任何读取点** |
| **证据** | `backupDamaged` 自己的注释写着 "so it can still be recovered **by hand**"——这个"by hand"实际含义是 `adb`，普通用户做不到 |
| **现存问题** | 存储损坏时 App 静默跳过坏条目并留了备份，但**从不告诉用户**、也**不给恢复入口**。等于买了一份保险却没有理赔电话 |
| **用户收益** | 中-高。存储损坏不常见，但一旦发生，用户目前看到的是"课表变少了"，且无从挽回 |
| **实现难度** | **低**。`CourseRepository` 加 `hasDamagedBackup()` / `restoreDamagedBackup()`；设置页在存在备份时多显示一张卡片 |
| **回归风险** | **低**。纯新增 API + 条件显示的入口 |
| **必须增加的测试** | ① 写入损坏载荷 → `hasDamagedBackup()` 为真、原始字符串可原样取回；② 恢复后 `courses:<id>` 等于损坏前的原始载荷；③ 无备份时恢复 API 返回 false 且不写任何键；④ 恢复本身走 D1 的快照路径（恢复前再存一份，避免"用坏了再恢复更坏"） |
| **推荐顺序** | 与 D1 同一批（复用同一套"留住上一份数据"的机制） |

#### D4 — 关键写入改用 `commit()` 【P1】

见 §T2。与 D1 是同一次改动的两半。

#### D5 — 备份策略未声明 【P2】

`AndroidManifest.xml:4` 是 `android:allowBackup="true"`，但**没有** `android:fullBackupContent`、
也**没有** `android:dataExtractionRules`（Android 12+）。

| 项 | 内容 |
|---|---|
| **现存问题** | 云备份/设备迁移的行为**完全依赖平台默认**：`weihe_schedule.xml` 会随自动备份上传，其中包含 `courses_backup` 里的**疑似损坏原文**。行为和隐私边界都没有在代码里写明 |
| **用户收益** | 低-中（换机时数据能跟过去是好事；但需要明确哪些键该走备份） |
| **难度/风险** | 低 / 低（但要改 `AndroidManifest.xml` 与新增 `res/xml/` 规则文件） |
| **测试** | 无法用 JVM 测试验证；只能靠 `adb shell bmgr` 手工验证 |
| **推荐顺序** | P2。**先决策"要不要云备份"，再写规则** |

#### D6 — 导入无来源记录 【P2】

导入后不记录文件名、导入时间、丢了多少行。将来做"导入历史"要先有 D1 的快照与 T1 的统计。

---

## 三、产品功能

### 3.1 现状

`SettingsActivity` 的 5 张卡片 + 首页/总览/编辑/作息表 = 当前全部功能面。
**没有**：提醒、详情页、搜索、导入明细。

### 3.2 候选任务

#### P1 — 上课提醒 【P2】

| 项 | 内容 |
|---|---|
| **是否已实现** | **完全没有** |
| **证据** | 全项目搜索 `Notification` / `AlarmManager` / `WorkManager` / `JobScheduler` / `POST_NOTIFICATIONS` / `BroadcastReceiver` = **0 处命中** |
| **约束下的可行路径** | ⚠️ **不能用 `WorkManager`**（AndroidX，项目已关闭 `androidx.useAndroidX`）。可行路线只有框架自带：`AlarmManager` + `BroadcastReceiver` + `NotificationManager` |
| **真正的难点（不是写代码）** | ① Android 13+ 需要运行时申请 `POST_NOTIFICATIONS`；② Android 12+ `SCHEDULE_EXACT_ALARM` 需要用户在系统设置里单独授权，否则只能用不精确闹钟；③ 必须处理"设备重启后重新排程"（`BOOT_COMPLETED`）；④ 提醒时刻要经过 `BellSchedules.resolve` + `ScheduleUtils.occursInWeek`（单双周、周次区间）计算，还要处理跨午夜作息（`BellSchedule.MAX_MINUTE` 允许到 29:59） |
| **用户收益** | 高——课程表 App 最被期待的功能 |
| **实现难度** | **高**（权限矩阵 + 排程生命周期 + 与作息表/单双周联动 + 重启恢复） |
| **回归风险** | 中（新增权限会改 Manifest；排程逻辑若写错会在错误时间打扰用户） |
| **必须增加的测试** | 提醒时刻的计算必须做成**纯函数**并全量 JVM 测试：给定（课程、学期起点、作息表、今天）→ 下一次提醒时刻。覆盖：单双周、课程已结束、跨午夜节次、作息表被删、今天没课。**排程/权限部分无法 JVM 测试**，只能靠 T3 的脚本做冒烟 |
| **推荐顺序** | P2。**建议先做"下一节课"卡片**（首页显示下一节课的时间与地点，零权限、零排程），它复用同一套时刻计算，能先把纯逻辑部分测透，再决定要不要上闹钟 |

#### P2 — 课程详情页 【P1】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否**。点课程卡直接进编辑表单 |
| **源文件** | `MainActivity:1315-1318`（`new Intent(this, EditCourseActivity.class)` + `EXTRA_COURSE_ID`）· `EditCourseActivity` 有 6 个 section：基本信息 / 时间 / 周次 / 单双周 / 作息 / 颜色（`:176,203,279,416,473,581`） |
| **现存问题** | **查看是高频动作，现在直通一个 806 行的重表单。** 用户只想看一眼"这门课在哪、第几周"，却进入了一个带 30 个周次 chip、会改数据的编辑页——误触即改，而保存是显式的"保存"按钮 + 冲突确认，流程很重 |
| **用户收益** | 高（最高频的交互路径从"编辑表单"变成"只读详情"） |
| **实现难度** | **中**。新增一个只读 Activity，或给 `EditCourseActivity` 加只读模式。推荐**独立详情页**——编辑页已经 806 行，再塞模式分支会更难维护 |
| **回归风险** | **中**。新页面要接好：返回后首页刷新、从总览页进入、删除后返回、进程恢复 |
| **必须增加的测试** | 详情页要展示的每一行都是 `Course` + 作息表的**纯派生值**（时间范围、周次规则、适用作息表名、冲突数量）。把这些派生逻辑抽成一个纯类并 JVM 测试：① 单双周文案；② 跨作息表的时刻；③ 课程已结束/未开始的措辞；④ 空地点/空教师时的占位 |
| **推荐顺序** | **P1 首位**（与 §6 的第 3 位候选相邻，见 §5 说明） |

#### P3 — 课程搜索 【P2】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否** |
| **证据** | 全项目无 `SearchView`、无 `onQueryText`、无过滤方法；`CourseQuery` 现有 API 是 `onDay` / `inWeek` / `all` / `startingAt` / `maxParallel`（**没有按关键字查**） |
| **用户收益** | 中——23 门课时意义不大，**但它决定了课表规模的上限**；且导入一个学期的真实课表后很容易到几十门 |
| **实现难度** | **低**。`rule/CourseQuery` 加 `search(courses, keyword)`（纯函数）+ 首页/总览一个搜索入口 |
| **回归风险** | **低** |
| **必须增加的测试** | 纯函数测试：① 匹配课名/教师/地点三个字段；② 大小写与全角/半角；③ 空关键字返回全部（或空，需先定契约）；④ 无结果；⑤ 与 `inWeek` 组合仍正确 |
| **推荐顺序** | P2 |

#### P4 — 导入结果反馈 【P0】

| 项 | 内容 |
|---|---|
| **是否已实现** | **部分** |
| **源文件** | `SettingsActivity.confirmImport`（`:433-507`）· `showImportError`（`:509-518`）· `importer/TimetableImportParser` |
| **已经做到的** | 成功路径**有**汇总：`"识别到 N 门课程，共 M 条安排"`（`:458-459`）；导入前弹确认框、按钮是"覆盖导入"；失败有 `UiKit.message("导入失败", detail)`，并对 `.xlsx`/zip 追加"请另存为 .xls"的提示（`:513-516`） |
| **证据（缺口）** | ① 解析器丢弃行**无计数**（8 处 `continue;`，见 T1）；② 结果是空列表时只有一句 `"没有识别到课程"`（`:435`）——**空文件、格式不对、表头没找到，三者无法区分**；③ 没有"哪几行被跳过、为什么"的明细 |
| **现存问题** | 用户无法判断"导入成功"到底是"全导进来了"还是"只导进来一半" |
| **用户收益** | **高**——直接消除"我的课表少了几门课"这类无法自查的问题 |
| **实现难度** | **低**（依赖 T1 先给出统计） |
| **回归风险** | **低** |
| **必须增加的测试** | 见 T1 的测试清单；UI 侧再加"丢弃数 > 0 时确认框必须显示丢弃明细"的判定（可归入 T3 脚本） |
| **推荐顺序** | **第 2 位**，与 T1 合并交付 |

---

## 四、体验优化

### 4.1 候选任务

#### U1 — 深色模式 【P2，但**工作量被严重低估**】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否** |
| **证据 ①** | `res/` 下**没有** `values-night`（目录列表：`anim` `drawable` `mipmap-*` `values` `values-v27`） |
| **证据 ②** | 主题在两个文件里都硬编码 `parent="android:style/Theme.Material.Light.NoActionBar"`（`values/themes.xml`、`values-v27/themes.xml`），并显式设 `windowLightStatusBar=true`、`windowLightNavigationBar=true` |
| **证据 ③（关键）** | `design/Palette` 是 **52 个 `public static final int` 常量**，全项目被引用 **274 次**，分布在 11 个文件：`UiKit` 77 · `MainActivity` 55 · `WeekGridActivity` 41 · `EditCourseActivity` 40 · `EditScheduleActivity` 21 · `SettingsActivity` 14 · `ScheduleListActivity` 11 · `SemesterListActivity` 10 · 其余 5 |
| **现存问题** | 系统切深色时 App 仍画浅色。**不错乱，但在深色系统下刺眼**，且 Android 13+ 用户默认期待跟随系统 |
| **为什么难度是「高」** | `Palette.X` 是**编译期常量**。要跟随主题，就必须让这些值在运行时按模式变化，两条路都很大：**(a)** 把 274 处 `Palette.X` 改成方法调用（如 `Palette.surface()`）——改动面 274 处且编译器不会帮你判语义；**(b)** 保留常量名但去掉 `final`、在 Activity 启动时按模式重写全部 52 个静态字段——**所有已缓存的 View 颜色都不会自动重画**，且全局可变静态状态与 §"每个 Activity 一个 repository"的现有约定相冲 |
| **第三条路（推荐先验证）** | 在**一个页面**上做原型：把该页用到的 `Palette` 字段包一层 `Theme.colour(role)`，验证"不重画已缓存颜色"这个坑到底有多深。**先出结论，再决定是否全面铺开** |
| **用户收益** | 高 |
| **实现难度** | **高** |
| **回归风险** | **高**——274 处引用、`PaletteContrastTest` 的 14 条对比度断言目前只覆盖浅色一套，深色必须**再配一整套 WCAG 断言** |
| **必须增加的测试** | ① `PaletteContrastTest` 扩展为**按主题参数化**，深色每对文字/背景同样断言 ≥ 4.5:1 / ≥ 3:1，非文字边界保持双向区间；② 断言深浅两套的 52 个角色**无遗漏**（角色清单齐全性） |
| **推荐顺序** | P2。**必须先做 T3 冒烟脚本**，否则改完 274 处没有回归网 |

#### U2 — 横屏 / 大屏 【P2】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否** |
| **证据** | `AndroidManifest.xml` 中 **7 个 Activity 全部** `android:screenOrientation="portrait"`（第 13、18、22、27、32、36、40 行） |
| **现存问题** | 平板上是巨型拉伸；折叠屏展开后仍是竖屏窄栏 |
| **用户收益** | 中（取决于用户是否用平板/折叠屏） |
| **实现难度** | **中-高**。本项目**没有 XML 布局**，多栏必须在 Java 里分支；`WeekGridActivity` 的滚动同步架构（`ARCHITECTURE.md` §四.4 记录的坑）对尺寸变化敏感 |
| **回归风险** | **中-高**——表头冻结/列对齐那套逻辑曾经出过严重 bug |
| **必须增加的测试** | 布局计算若抽成纯函数可 JVM 测；其余只能靠 T3 脚本 + 手工 |
| **推荐顺序** | P2，且**排在 U1 之后**（两者都要动尺寸/颜色，一起做省一次回归） |

#### U3 — 表单状态不保存 【P0】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否** |
| **证据 ①** | 全项目 `onSaveInstanceState` / `onRestoreInstanceState` = **0 处** |
| **证据 ②（关键，本轮新发现）** | 全项目 `setId(` / `generateViewId` / `R.id.` = **0 处**。**所有 View 都没有 id**，因此连框架**自动**的 View 状态保存都失效——这不是"没写 `onSaveInstanceState`"，而是"连兜底的那层也没有" |
| **证据 ③** | 受影响的输入：`EditScheduleActivity` 有 `EditText[15]` × 2 = **30 个时间框** + 名称 + 关键词（`:54-55`）；`EditCourseActivity` 有 5 个命名输入 + 30 个周次 chip；`SemesterListActivity:328,337` 与 `SettingsActivity:358` 的对话框输入同样无 id |
| **现存问题** | 竖屏锁定只挡住了旋转这一条路径，**挡不住进程回收**。用户填了一半课表切去别的 App，回来发现全空；`Activity` 被销毁重建（分屏、深色切换、开发者选项"不保留活动"）同样全丢 |
| **用户收益** | **高**——直接消除一类"白填了"的挫败 |
| **实现难度** | **低**。给两类编辑页实现 `onSaveInstanceState`，把字段值写进 `Bundle`；对话框里的输入需要重新设计（对话框不是 View 树的一部分） |
| **回归风险** | **低** |
| **必须增加的测试** | ① 把"表单字段 ↔ Bundle"的映射抽成一个**纯类**（如 `FormState`），JVM 测：写入→读出逐字段相等；② 空表单/部分填写/超长文本；③ 周次 chip 的选中集合；④ 30 个时间框的半成品文本（`"8.0"`、`"8:"`、全角冒号）必须原样还原，不能被规范化 |
| **推荐顺序** | 见 §5 |

#### U4 — 动画未适配无障碍 【P2】

| 项 | 内容 |
|---|---|
| **是否已实现** | **否** |
| **证据** | 全项目无 `ANIMATOR_DURATION_SCALE`、无 `Settings.Global` 读取 |
| **现存问题** | 在系统里关掉动画的用户（含部分前庭功能敏感用户）仍会看到全部入场动画（`UiKit` 的 180–220ms `DecelerateInterpolator` 错落入场） |
| **用户收益** | 中（无障碍合规 + 尊重系统设置） |
| **实现难度** | **极低**（读一次 scale，为 0 时跳过动画） |
| **回归风险** | **低** |
| **必须增加的测试** | 抽一个 `UiKit.animationsEnabled(Context)` 的判定纯函数测两条分支 |
| **推荐顺序** | P2，但**性价比最高**，可以搭在任何一次 UI 改动里顺手做 |

#### U5 — UI 一致性 【P2】

| 项 | 内容 |
|---|---|
| **证据（本轮机测）** | ① **4 种输入框构造**：`UiKit.field`（`SemesterListActivity:338`、`SettingsActivity:358`）· `EditCourseActivity.input:190` · `EditScheduleActivity.input:281` + `timeInput:298` · `SemesterListActivity.dateField:328`；② **3 套校验反馈**：`EditCourseActivity` 与 `EditScheduleActivity` 用 `setError`（共 21 处），`SettingsActivity:374`/`SemesterListActivity:279,286` 用对话框内报错；③ **9 种卡片内边距**：4 / 6 / 7 / 8 / 10 / 12 / 14 / 15 / 22 dp；④ **160 处 `dp(字面量)`**（`EditCourseActivity` 73 · `EditScheduleActivity` 29 · `WeekGridActivity` 24 · `ScheduleListActivity` 14 · `MainActivity` 10 · `SettingsActivity` 10）；⑤ 已废弃别名仍有 **4 处**在用：`Palette.INK` 在 `SettingsActivity:235,237`，`Palette.CARD` 在 `WeekGridActivity:700,864`；⑥ 版本号硬编码 `"WeiheSchedule 1.0 · minSdk 26"`（`SettingsActivity:336`），与 `app/build.gradle` 的 `versionName` 无关联 |
| **现存问题** | 同一个表单里"名称"和"周次"长得不一样；改一处样式要翻 6 个文件 |
| **用户收益** | 中（观感一致性） |
| **实现难度** | **中**（分散在多处，但不难） |
| **回归风险** | **中**——纯视觉改动，**没有自动化视觉回归**，改完必须靠 T3 脚本 + 逐屏人工确认 |
| **建议做法** | **分批、按风险从低到高**：先做零视觉风险的（版本号改从 `PackageManager` 读、废弃别名替换、删无用 import），再做有视觉变化的（统一到 `UiKit.field`、抽卡片内边距 token） |
| **注意** | ⚠️ 版本号若改用 `BuildConfig` 需要开 `buildConfig true`（改 `build.gradle`）。**用 `PackageManager.getPackageInfo().versionName` 可以零配置改动**，更符合本轮约束 |
| **推荐顺序** | P2，且**必须在 T3 之后** |

#### U6 — `WeekGridActivity.currentWeek` 在 `onCreate` 冻结 【P1】

| 项 | 内容 |
|---|---|
| **是否已实现** | 逻辑存在，但只在创建时算一次 |
| **源文件** | `WeekGridActivity`：`currentWeek` 字段（`:76`）只在 `:130` 赋值；`onResume`（`:141-151`）只做 `reload()` + 重取 `semesterStart` + `render()`；消费点有 `:323,383-384,447,869` |
| **证据** | 赋值点全文**只有一处**（`:130`，`onCreate`）。`addDayHeaders` 用的是**当下**的 `LocalDate.now()`，而 `subtitle`（`:447`）、`styleTodayChip`（`:869`）、周切换钳制（`:383`）用的是**冻结的** `currentWeek` |
| **现存问题** | App 在后台跨过周一零点后回到前台，**同一屏里两处对"今天"的判断不一致**：日期表头显示新的一周，"本周"标签仍指向上一周 |
| **用户收益** | 中——低频但会造成明确的错误显示 |
| **实现难度** | **极低**（在 `onResume` 里重算一次） |
| **回归风险** | **低**，但要注意：用户手动切到第 5 周时跨周回来，"回到本周"的目标会变，需要确认这符合预期 |
| **必须增加的测试** | 把"跨周后应显示第几周"抽成纯函数，用 `ScheduleUtilsTest` 的既有风格覆盖：同一学期起点、`today` 分别为周日前/后 |
| **推荐顺序** | P1（可与 U3 一起做，都是生命周期类的低风险修复） |

#### U7 — 无加载态与错误态 【P2】

| 项 | 内容 |
|---|---|
| **证据** | 导入有进度对话框（`SettingsActivity:405-407`），但首页/总览的 SharedPreferences 读取**没有**进度；`render()` 在主线程解析 JSON。存储损坏时静默跳过坏条目（`CourseRepository` 宽容解析 + `backupDamaged`），**界面不告知** |
| **用户收益** | 低-中（课表规模小时无感） |
| **难度/风险** | 低 / 低 |
| **备注** | 与 D3 天然配对：一旦有了"备份存在"的检测，就必须有地方把它显示出来 |

---

## 五、任务总表与分档

### P0 —— 数据会丢，且现在没有兜底

| # | 任务 | 依据 | 成本 | 风险 |
|---|---|---|---|---|
| **P0-1** | **导入前自动快照 + 一键撤销**（D1） | `SettingsActivity:499` → `CourseRepository.replaceCourses:555-569` 不可逆覆盖 | 低 | 低 |
| **P0-2** | **让导入不再静默丢数据**（T1 + P4） | 8 处 `continue;` 无计数；`ExcelTimetableReader` 0 测试；空文件与全丢不可区分 | 低-中 | 低 |
| **P0-3** | **表单状态保存**（U3） | `onSaveInstanceState` 0 处 + `setId` 0 处 → 连框架兜底都没有 | 低 | 低 |

### P1 —— 明确的缺口，风险可控

| # | 任务 | 依据 | 成本 | 风险 |
|---|---|---|---|---|
| **P1-1** | 课表导出（D2） | 无 `ACTION_SEND`/`ACTION_CREATE_DOCUMENT` 写入 | 低-中 | 低 |
| **P1-2** | `courses_backup` 恢复入口（D3） | 键只写不读（`CourseRepository:513` 是唯一写入点） | 低 | 低 |
| **P1-3** | 关键写入走 `commit()`（D4/T2） | 10 处 `apply` / 0 处 `commit`；`FakePreferences.apply` 同步 | 低 | 低-中 |
| **P1-4** | UI 回归冒烟脚本（T3） | 无 `androidTest`；19 项手工清单未自动化 | 中 | 零（不碰生产代码） |
| **P1-5** | 课程详情页（P2 项） | `MainActivity:1315` 查看直通 806 行编辑表单 | 中 | 中 |
| **P1-6** | `currentWeek` 冻结（U6） | `:130` 唯一赋值点，`onResume` 不重算 | 极低 | 低 |

### P2 —— 价值真实但成本或风险高，或依赖前面的成果

| # | 任务 | 备注 |
|---|---|---|
| P2-1 | 深色模式（U1） | **274 处引用 / 52 个编译期常量**；需先原型验证；**必须排在 T3 之后** |
| P2-2 | 横屏/大屏（U2） | 无 XML 布局，多栏要在 Java 分支；滚动同步架构敏感 |
| P2-3 | 上课提醒（P1 项） | 权限矩阵 + 排程生命周期；**建议先做"下一节课"卡片过渡** |
| P2-4 | 课程搜索（P3 项） | `CourseQuery.search` 纯函数，成本低，价值中等 |
| P2-5 | UI 一致性收敛（U5） | 分批；**视觉回归无自动化，必须排在 T3 之后** |
| P2-6 | 动画无障碍（U4） | 成本极低，可搭车 |
| P2-7 | 备份策略声明（D5） | 先决策"要不要云备份" |
| P2-8 | 纯逻辑边界用例（T4/T5）、导入历史（D6）、加载/错误态（U7） | 顺手补 |

---

## 六、未来 3 个最值得优先执行的任务

**选择依据**：只挑"用户会真实受损 + 现在没有任何兜底 + 成本低 + 能在本环境用 JVM 测试验证"的。
深色模式虽然收益高，但成本与回归风险都是最高档，**且必须先有 UI 回归网**，所以不进前三。

---

### 6.1 第 1 位：导入安全网（自动快照 + 一键撤销）

**为什么是它**：这是全 App **唯一一条"一次误操作 = 数据永久丢失"** 的路径
（`SettingsActivity:499` → `CourseRepository.replaceCourses:555-569`，无备份、无撤销、
全项目也没有导出可以把数据提前拿走）。修它的成本却是最低的一档。

**具体实施步骤**

1. **`data/CourseRepository` 新增快照键与两个方法**（不动 `courses:<id>` 的格式）
   - 键：`courses_previous:<semesterId>`，值与 `courses:<id>` 同样是「课程数组的 JSON 字符串」，
     直接复用现有的 `MiniJson` 与课程序列化代码，**不新增格式**。
   - `public boolean hasPreviousCourses()` / `public boolean restorePreviousCourses()`
   - `restorePreviousCourses()` 语义：读快照 → 写回 `courses:<id>` → 清空快照 → `reload()` 缓存 →
     返回是否成功。**恢复本身也要先把当前值存进快照**，否则"恢复错了再恢复"会二次丢失。
2. **`replaceCourses` 写盘前先存快照**
   - 位置：`CourseRepository.replaceCourses`（`:555`）在 `saveCourses(courses)`（`:568`）**之前**，
     把 `preferences.getString(coursesKey(), null)` 原样写入 `courses_previous:<id>`。
   - 注意：**不要**对它做 `MiniJson` 往返后再存——存**原始字符串**才能保证 D1 测试项⑤
     "撤销后与原值逐字节相同"。
   - 快照只保留**最近一份**（直接覆盖写，不做列表）。
3. **关键写入改用 `commit()`**（D4，与上一步同批）
   - 只改这一条路径：快照写入 + `saveCourses` 用 `commit()`，并检查返回值。
   - `commit()` 返回 false 时**不要**提示"导入成功"，改为提示失败并保留快照。
   - 其余 9 处写入保持 `apply()`（官方推荐，且没有不可逆性）。
4. **`SettingsActivity` 增加"撤销上次导入"入口**
   - `createImportCard` 内，当 `repository.hasPreviousCourses()` 为真时多显示一行操作。
   - 文案需说明"只能撤销一次"与"将恢复为导入前的课表"。
   - 恢复后调用现有的 `refresh()`（`:500` 同一路径），保证学期/作息表/课表一起刷新。
5. **`EditScheduleActivity` 的删除路径不在此列** —— 它已有"至少保留一张"的保护（§1.3 S2）。

**必须增加的测试**（`data/CourseRepositoryTest`，沿用 `FakePreferences` 与 `repositoryFor(...)` 夹具）

| # | 测试 | 断言 |
|---|---|---|
| 1 | `importingKeepsThePreviousTimetableRecoverable` | 导入前 3 门课 → `replaceCourses` 换成 1 门 → `hasPreviousCourses()` 为真 → 恢复 → 3 门课逐字段回来 |
| 2 | `theRecoveredPayloadIsByteIdenticalToWhatWasStored` | 恢复后 `preferences.getString("courses:<id>")` 与导入前**字符串相等**（不是"字段相等"） |
| 3 | `onlyTheMostRecentImportCanBeUndone` | 连续导入两次 → 撤销一次 → 只剩一个快照，再撤销返回 false |
| 4 | `theSnapshotBelongsToOneSemester` | 学期 A 导入 → 切到学期 B → B 看不到 A 的快照；在 B 恢复不成功且不改动 B 的数据 |
| 5 | `afailedCommitDoesNotClaimSuccess` | `FakePreferences` 增加可注入的"commit 失败"模式 → `replaceCourses` 不得留下"已导入"的状态 |
| 6 | `undoAfterUndoDoesNothing` | 恢复后快照被清，第二次恢复返回 false 且不改数据 |

**完成判据**：239 + 6 = **245** 项测试全绿；`assembleDebug` 与 `lintDebug` 干净。

---

### 6.2 第 2 位：让导入不再静默丢数据（解析器统计 + 结果明细）

**为什么是它**：`TimetableImportParser` 有 **8 处 `continue;`** 且**没有任何计数器**，
而用户看到的是"识别到 N 门课程"。**导入是这台 App 唯一的数据入口**，
现在"导进来一半"和"全导进来"在界面上长得一模一样。
`ExcelTimetableReader`（唯一读文件的类）更是 **0 测试**。

**具体实施步骤**

1. **给解析器加一个统计结果类型**（`importer` 包内新增一个类，不改现有 `parse` 的返回类型）
   - 形如 `TimetableImportParser.Report { int totalRows; int parsedRows; List<String> dropped; }`
   - 新增 `parseWithReport(TimetableGrid)`，现有 `parse(grid)` 保留并委托给它
     （**保持向后兼容**，现有 7 个测试不动）。
2. **在 8 处 `continue` 上补计数与原因**
   - 逐处归类：表头前文本 / `DETAIL` 正则不匹配 / 周次不可解析 / 周次越界 / 锚点行不在节次表。
   - 记录的是**行号 + 原因**，不记录整行文本（避免把用户的课表内容写进日志）。
3. **区分三种"空结果"**：文件为空 / 找不到表头 / 表头找到了但一行都没解析出来。
   现在这三种都落到 `SettingsActivity:435` 的一句 `"没有识别到课程"`。
4. **`ExcelTimetableReader` 补测试**
   - 用 `jxl` **在测试里现场生成**一个最小 `.xls`（不引入新依赖、不往仓库塞二进制夹具）。
   - 断言：读回的 `TimetableGrid` 行列数正确、合并区几何正确、越界/反向的合并区被拒绝。
   - 断言：喂 zip（伪装成 `.xls` 的 xlsx）时抛 `TimetableImportException` 且消息可读。
5. **`SettingsActivity.confirmImport` 展示明细**
   - 现有汇总（`:458`）后面追加一行，例如"其中 N 行未能识别"。
   - 丢弃数 > 0 时，确认框里给出**前几条原因**（不要一次列 200 行）。
6. **不要做的事**：不改 `parse` 的既有行为、不改 `TimetableGrid` 的数据结构、
   不让解析器去碰 Android（它现在零 Android 依赖，必须保持，否则 T1 的测试就跑不了）。

**必须增加的测试**

| # | 测试 | 断言 |
|---|---|---|
| 1 | `readsAMinimalWorksheetWrittenByJxl` | 现场写 `.xls` → `ExcelTimetableReader.read` → 网格尺寸与合并区正确 |
| 2 | `aZipDisguisedAsXlsIsRejectedWithAReadableMessage` | 抛 `TimetableImportException`，消息非空 |
| 3 | `textBeforeTheHeaderIsCountedAsDropped` | `dropped` 非空且原因可读 |
| 4 | `aDetailLineWithASingleSpaceIsReported` | 单空格分隔的细节行**被计数**（现在完全静默） |
| 5 | `anUnparseableWeekRangeIsReported` | 同上 |
| 6 | `anEmptyFileAndAFullyUnparsedFileAreDistinguishable` | 两者 `Report` 不同（`totalRows` 或原因列表不同） |
| 7 | `theExistingSevenCasesStillPassUnchanged` | 现有断言一行不改 |

**完成判据**：测试数 245 + 7 = **252** 全绿；现有 7 个导入用例的断言**一字未改**。

---

### 6.3 第 3 位：表单状态保存（`onSaveInstanceState`）

**为什么是它**：本轮新发现的证据比旧报告更严重——不只是"没写 `onSaveInstanceState`"，
而是**全项目 `setId(` 也是 0 处**，也就是说**连框架自动的 View 状态保存都失效**。
`EditScheduleActivity` 有 **30 个时间输入框**（`EditText[15]` × 2，`:54-55`），
用户填到第 12 节切走再回来，全部归零。竖屏锁定只挡住了旋转，挡不住进程回收。

**具体实施步骤**

1. **先把"表单 ↔ Bundle"抽成纯类**（关键：这是唯一能 JVM 测试的部分）
   - 例如 `ui/ScheduleFormState`：持有 30 个半成品时间文本 + 名称 + 关键词 + 当前编辑的原名，
     提供 `Bundle toBundle()` / `static ScheduleFormState from(Bundle)`。
   - **必须保持半成品文本原样**：`"8.0"` / `"8:"` / 全角冒号都不能被规范化。
2. **`EditScheduleActivity` 实现 `onSaveInstanceState` / 恢复**
   - 保存：名称、关键词、30 个框、`originalName`、`existingSchedule`。
   - 恢复：在 `onCreate` 里若 `savedInstanceState != null` 则用它构造 `editor`，
     **不要**再走 `BellSchedules.byName` 重新解析（数据可能已经变了）。
   - 注意与既有的 `EXTRA_NAME` 入口（`EditScheduleActivity.EXTRA_NAME`）的优先级：
     恢复态优先于 Intent extra。
3. **`EditCourseActivity` 同样处理**
   - 5 个命名输入 + 30 个周次 chip 的选中集合 + 单双周选择 + 颜色选择。
   - 冲突确认框**不恢复**（它是一次性交互，重建后重新触发更安全）。
4. **对话框里的输入**（`SemesterListActivity:328,337`、`SettingsActivity:358`、`MainActivity` 的 3 处）
   - 对话框不在 View 树里，`onSaveInstanceState` 覆盖不到。
   - 本轮**只处理两个编辑页**；对话框输入属于"打开着对话框时进程被回收"，概率更低，
     单独列为 P2 后续项，避免一次改动面过大。
5. **不要做的事**：不引入 `ViewModel`/`SavedStateHandle`（那是 AndroidX）；
   不改 `Course` 或 `BellSchedule` 的数据结构；不动 `MiniJson`。

**必须增加的测试**（纯类部分，JVM）

| # | 测试 | 断言 |
|---|---|---|
| 1 | `everyHalfTypedTimeSurvivesTheRoundTrip` | 30 个框逐个写入 → `toBundle` → `from` → 逐字段字符串相等 |
| 2 | `halfTypedTextIsNotNormalised` | `"8.0"` / `"8:"` / `"８："` / 空串原样还原 |
| 3 | `theOriginalNameIsKeptSoARenameStillCascades` | 恢复后 `originalName` 不变（否则会退化成"新增一张表"） |
| 4 | `aRestoredSeventeenPeriodValueIsClampedNotCrashed` | 越界/缺字段的 Bundle 不抛异常 |
| 5 | `courseWeekChipsAndColourSurviveTheRoundTrip` | 周次集合与颜色索引一致 |

**完成判据**：测试数 252 + 5 = **257** 全绿。

---

## 七、本轮未验证 / 待确认

**明确不把下列内容当作已确证的问题：**

| 项 | 状态 |
|---|---|
| `WeekGridPlan.plan` 对列表内 `null` 元素是否抛 NPE | **待确认**——本轮未逐行复核；建议用一条探测性测试确认 |
| `EditCourseActivity` 的颜色选择行在 360dp 屏上是否横向溢出 | **待确认**（上一轮标为待确认，本轮仍未做渲染验证）。9 个 30dp 色块 + 8dp 间距 = 342dp，需实际渲染确认 |
| `CourseRepositoryTest` 的 `KEY_BACKUP` / `KEY_SCHEDULES` 是否仍是死常量 | **待确认**——本轮列出了全部 34 个测试方法名，未逐个核对常量使用 |
| 元信息行图标与文字的基线偏移 | **待确认**（上一轮即标待确认） |
| 深色模式下 274 处引用的真实改动量 | **未验证**——只统计了引用数，没有做原型。"必须先原型再决策"是本文的建议，不是结论 |
| 上课提醒所需的 `SCHEDULE_EXACT_ALARM` 在本项目的 `targetSdk 34` 下的具体授权流程 | **未验证**——需查官方文档，本轮未做 |
| T3 冒烟脚本 | **未实现、未运行**。且本会话沙箱禁止模拟器写入 `C:\Users\<user>\.android`，**本环境无法验证**，需要在本机执行 |

---

## 八、明确不做的事（本轮与下一阶段的边界）

| 不做 | 原因 |
|---|---|
| 迁移 Kotlin / Compose / Room / XML | 约束明令禁止 |
| 引入任何新依赖（含 AndroidX、WorkManager、Robolectric、Espresso） | 约束明令禁止；T3 因此选择 adb + PowerShell 而非 Espresso |
| 改变 `courses:<id>` / `semesters` / `bell_schedules` 的存储格式或键名 | 约束明令禁止。D1 的快照是**新增键**，D2 的导出是**只读派生** |
| 删除任何现有测试 | 约束明令禁止。T1/T2 都采用"保留旧 API、新增统计入口"的加法做法 |
| 为了减少行数而重写稳定逻辑 | 约束明令禁止。`MiniJson`、`WeekGridActivity` 的滚动同步、`BellSchedules` 的时间表都不动 |
| 删除 `MainActivity:1176` 起的死导入代码 | 属于"删代码"而非本轮范围；且它是 D2 导出功能的反面教材（`startActivityForResult` 却没有 `onActivityResult`），**做 D2 时应顺手清理，但要单独一次提交** |
| 恢复工作区里被删除的 `ARCHITECTURE.md` / `ARCHITECTURE_REFACTOR_PLAN.md` | 见 §0.1，需你先确认是否有意删除 |

---

## 九、建议的推进节奏

```
第 1 批（P0，建议一次迭代内完成）
  P0-1 导入安全网 ──┬── 同一批改，共用"留住上一份数据"的机制与测试夹具
  P1-2 备份恢复入口 ┘
  P1-3 commit 化     ┘
  P0-2 导入可观测（T1 + P4）
  P0-3 表单状态保存（U3；可与 U6 一起，都是生命周期类低风险修复）

第 2 批（P1）
  P1-4 UI 冒烟脚本  ← 必须在任何视觉改动之前
  P1-1 课表导出
  P1-5 课程详情页
  P1-6 currentWeek 冻结

第 3 批（P2，按收益排序，且深色模式/一致性必须先有冒烟脚本）
  U4 动画无障碍（极低成本，随时可搭车）
  课程搜索
  "下一节课"卡片 → 再评估上课提醒
  深色模式（先原型）
  横屏/大屏
  UI 一致性收敛
```

**每一批的完成判据**：测试数只增不减且全绿 · `assembleDebug` 成功 · `lintDebug` 零问题 ·
涉及 UI 的批次额外跑一次冒烟脚本 · 每批独立提交。

---

**本轮仅分析与规划，未修改任何业务代码、未新增依赖、未执行重构。**
本文是唯一新增文件。
