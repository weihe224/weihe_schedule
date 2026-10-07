# 架构与验证状态

> 这份文档记录本项目当前的分层、**哪些部分已经被验证、哪些还没有**，以及后续演进路线。
> 判断标准只有一条：**一个类能不能脱离模拟器和真机跑起来。**

---

## 一、分层与依赖方向

```
                     界面（Android）—— 只读规则，不实现规则
   MainActivity · WeekGridActivity · EditCourseActivity
   SettingsActivity · SemesterListActivity · ScheduleListActivity
   EditScheduleActivity
                    │
                    │ 读规则 / 读数据
                    ▼
   规则层（零 Android 依赖，全部有 JVM 测试）
   ┌───────────────┬────────────────┬──────────────────┬─────────────────┐
   │ WeekGridPlan  │ CourseQuery    │ CourseConflicts  │CourseFormValidator│
   │ 网格排布/分道  │ 按周/天查课     │ 时间冲突判定      │  表单校验规则     │
   └───────┬───────┴───────┬────────┴────────┬─────────┴────────┬────────┘
           │               │                 │                  │
   ┌───────┴───────────────┴─────────────────┴──────────────────┴────────┐
   │ ScheduleUtils（周次/节次/日期）   CourseList（列表语义）              │
   │ Semester（学期模型）  SemesterJson（学期存储）                        │
   │ BellSchedules / BellSchedule（作息解析与时间）                       │
   │ BellScheduleEditor（作息表单规则）   MiniJson（存储用的 JSON）        │
   └───────────────────────────────┬────────────────────────────────────┘
                                   ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │ 数据模型：Course / Palette / TimetableGrid / TimetableImportParser  │
   └────────────────────────────────────────────────────────────────────┘

   存储：CourseRepository ──► MiniJson ──► SharedPreferences
         （每个学期一套 courses:<id> 与 semester_start:<id>；
           作息表全局共用；内存快照缓存 + 每次返回深拷贝）
   设计系统：Palette（颜色角色）· Colors（对比度运算，纯逻辑）
            · UiTokens（间距/圆角/字阶/控件尺寸）· UiKit（组件层）· Insets（系统栏）
   外部读取：ExcelTimetableReader（jxl，只读 .xls）
```

**依赖方向是单向的：上层依赖下层，下层不知道上层存在。**
`ScheduleUtils` 不知道 `CourseQuery`，`CourseQuery` 不知道 Activity，
`MiniJson` 不知道 `SharedPreferences`。

---

## 二、作息时间：三组，全部来自学校表格

全校第 1、2 节与第 6 节之后相同，只有第 3–5 节按楼栋分三组，
而且三组在这三节上两两不同，所以最少就是三张表：

| 组 | 覆盖场所 | 第 3 节 | 第 4 节 | 第 5 节 |
|---|---|---|---|---|
| **教学楼作息**（默认） | 教学楼 A、F、J301 室 | 09:55–10:35 | 10:40–11:20 | 11:20–12:00 |
| B/C 楼作息 | 教学楼 B、C、J302 室及**其他教学场所** | 09:55–10:35 | 10:35–11:15 | 11:20–12:00 |
| D/E 楼作息 | 教学楼 D、E、J303 室 | 10:15–10:55 | 10:55–11:35 | 11:40–12:20 |

第 1–2 节全校都是 08:15–08:55 / 08:55–09:35；第 6 节起全校一致
（6-7 节 13:20–14:40、8-9 节 15:00–16:20、10-11 节 16:35–17:55、
12-13 节 18:10–19:30、14 节 19:35–20:15、15 节 20:20–21:00）。

学校表格写的是**块**（「第3、4节：09:55-11:15」），一组块拆成单节时按 40 分钟一节、
中间留 5 分钟课间——这与表格给出的各块边界逐一对得上（B/C 的 09:55–11:15 正是 40+5+40）。

**「其他教学场所」不是关键词，而是 `resolve()` 里的一条规则**：地点为空、或没有任何楼栋
关键词命中时落到 B/C 组。做成关键词就必须排在最后，而排在最后永远不会赢，
所以写在顺序明确的地方。

解析顺序：课程显式指定的作息名 → 第一个地点关键词命中的作息 → B/C 组 → 标准作息。

**作息时间表里没有任何推测值。** 早期版本曾内置编造的第 6 节之后的时间，
并为此做过一整套「时间待确认」机制（`unconfirmedFromPeriod`、界面提示、JSON 字段）；
时间全部得到确认后，那套机制已整体删除。

---

## 三、验证状态

### 已经验证（本机可复现）

| 验证项 | 手段 | 结果 |
|---|---|---|
| 全量编译 | `./gradlew assembleDebug`（AGP 8.13.0 + Gradle 8.13 + JDK 20） | 通过，产出已签名 `app/build/outputs/apk/debug/app-debug.apk` |
| 业务规则与存储 | `./gradlew testDebugUnitTest`，17 个文件、**220 项 `@Test`** | 全部 PASS |
| 静态检查 | `./gradlew lintDebug` | 0 error、0 warning |
| 配色可访问性 | `PaletteContrastTest`，13 项 WCAG AA 断言 | 全部 PASS |
| 真机渲染 | 模拟器上安装运行，逐屏点击并抓 `uiautomator dump` 核对坐标 | 主要界面渲染正确，无崩溃 |

测试分布（按文件）：

| 文件 | 项数 | 覆盖内容 |
|---|---|---|
| `CourseRepositoryTest` | 25 | 存储层：JSON 往返、快照与防泄漏、损坏载荷备份、id 分配、按课解析作息（三种场所三种时间）、作息重命名/清除级联、学期新建与隔离、旧版迁移、**跨实例缓存失效（`reload`）** |
| `ScheduleUtilsTest` | 19 | 周次运算、`currentWeek` 边界钳制、`dateOf` 越界钳制 |
| `BellScheduleEditorTest` | 17 | 作息表单：时间各种写法、结束早于开始、与上一节重叠只提示不拦截、重名、批量报错顺序 |
| `BellScheduleTest` | 17 | 三组作息逐节对时、组间差异位置、地点关键词匹配、标签与解析往返 |
| `WeekGridPlanTest` | 16 | 网格结构：每门课恰好落在一格、同格同道的课不重叠、分道最小化 |
| `CourseFormValidatorTest` | 15 | 表单规则：必填、非数字、越界、起止先后、问题上报顺序 |
| `CourseQueryTest` | 15 | 查询层：按星期过滤、排序、奇偶周、周窗口、不改动入参 |
| `BellSchedulesTest` | 14 | 作息解析：显式名优先、按地点匹配、兜底顺序、删除后回退 |
| `CourseConflictsTest` | 13 | 冲突判定：单双周互斥、周次窗口相接/重叠、编辑时不与自己冲突 |
| `PaletteContrastTest` | 13 | 设计系统：文字/背景满足 WCAG AA、课程色混合后可读、字阶与间距阶有序 |
| `CourseListTest` | 12 | 列表操作：替换/追加语义、存副本、id 碰撞向前找空位 |
| `BellScheduleJsonTest` | 11 | 存储往返、损坏载荷修复、非对象条目跳过、关键词往返 |
| `SemesterTest` | 10 | 学期模型：名称由起始日推导、手填名称优先、起始日归一到周一 |
| `MiniJsonTest` | 9 | JSON 读写：转义、unicode、数字形态、坏文档拒绝 |
| `TimetableImportParserTest` | 7 | 导入解析：合并单元格跨节、一格多课、单双周、周次平移 |
| `SemesterJsonTest` | 4 | 学期存储往返、坏载荷逐条跳过 |
| `WeekGridRowsTest` | 3 | 总览行数随显示课程增减（含触发过崩溃的收缩）、不超过每日节次上限 |

### 尚未验证（必须在真机/模拟器上确认）

> **关于模拟器的一个坑**：出现过一次「运行起来黑屏」。排查结论是模拟器侧的问题，不是应用：
> 当时 `mCurrentFocus=NotificationShade`、焦点应用是 GMS 的 `ErrorActivity`，我们的进程已被杀；
> 干净重启后应用冷启动、清数据全新安装两条路径都渲染完整。
> 同一台模拟器上 `android.hardware.uwb-service` 每 5 秒 SIGABRT 一次
> （`failed to open the serial device`，模拟器没有 UWB 硬件），持续刷 tombstone、拖慢系统。
> 再遇到黑屏，先冷启动模拟器（Cold Boot Now）再判断，并抓 `adb logcat -d | grep "at com.weihe"`。

1. **响应式**：只在一个 420dpi / 1080×2424 的模拟器上验证过。
   小屏、大字体缩放、刘海屏/挖孔屏都没测。文字承载控件已从固定高度改为 `minHeight`（图标按钮除外），
   这是能防住大字体截断的关键，但需要真机确认。
2. **六个 Activity 的 Android API 用法**：滑动手势与 `GestureDetector` 的配合、
   `GridLayout` 里跨行 FrameLayout 的排布、`EditText.setError` 弹窗位置、对话框内嵌 `ScrollView` 的高度。
3. **设计系统的实际观感**：对比度是算出来的，但**圆角阴影是否真的柔和、卡片层次是否够**，
   只能看真机。
4. **总览的纵向同步**：节次列跟随网格纵向滚动已实现，但当前课表最多 11 行仍放得下，
   没能实测触发；课表长到需要纵向滚动时才会生效。
5. **`单双周` 仍是平台 `RadioButton`**，是编辑器里唯一未自绘的控件。
6. **Excel 导入的端到端流程**：文件选择器、后台线程、弹窗时序，
   以及 `jxl` 对真实教务系统文件的兼容性（只支持 `.xls`，不支持 `.xlsx`）。

本机环境有两个已知限制：宿主 `~/.gradle` 与 `~/.android` 不可写，所以用工作区内的
`.gradle-local/home` 与 `.android-home/debug.keystore`；模拟器无法在本会话中常驻，
验证靠 `adb install` + `uiautomator dump` 逐屏核对。

---

## 四、踩过的坑（都留下了代码注释与测试）

这一节记的都是**编译器和单元测试抓不到、只有真机或仔细测量才会暴露**的问题。

### 1. 别在触摸监听器里调用 `performClick()`

按压反馈最早的实现给视图装 `OnTouchListener`，ACTION_UP 里既做动画又调 `target.performClick()`。
理由「不这样点击监听器就不触发」本身是错的，而且引入了**按钮有概率需要点两次**的 bug：
触摸监听器与框架自己的点击判定在抢同一次点击。

现在**根本不碰触摸监听器**：`UiKit.PressFeedback` 是 `LinearLayout` 子类，重写
`dispatchTouchEvent` 只为观察手势做动画，事件原样交给 `super`。代价是 `ImageButton` 无法使用
（父类不能换），所以 `pressScale()` 对非 `PressFeedback` 视图**降级为不做动画**，而不是退回去装监听器
——宁可少一个动画，也不要一个会吞点击的监听器。

### 2. `res/values` 里的 style 名字不要带点

`Theme.WeiheSchedule` 同时存在于 `values/themes.xml` 与 `values-v27/themes.xml`。
**资源系统不会把两份定义合并**，它挑最匹配的一份整份使用。早期版本在 v27 文件里写了完整 `parent`
加一条 `windowLightNavigationBar`，结果在 **API 27 及以上**把基础主题里的 `statusBarColor`、
`colorAccent`、`windowBackground` 全部丢掉，悄悄退回平台默认值。

也**不能靠省略 parent 来「继承」**：AAPT 会把 `Theme.WeiheSchedule` 当作 `style/Theme` 的隐式父样式
去链接，直接报 `resource style/Theme not found`。同样的坑在 `values/animations.xml` 又踩了一次
（`Animation.WeiheSchedule.Sheet`），改成无点的 `SheetAnimation` + 显式 parent 才好。

**结论**：两份 themes 文件各自写完整定义；style 名字不带点，除非父样式确实存在且被显式写出。

### 3. `GridLayout` 缩小行数前必须先清空子视图

总览页的行数由当前显示的课决定（`lastPeriodInUse`），所以它会变小。`GridLayout.setRowCount`
会拿**当前已有子视图**的行索引校验新行数，于是「先 `setRowCount` 再 `removeAllViews`」直接抛
`IllegalArgumentException: rowCount must be greater than or equal to the maximum of all grid indices...`，
点「单周课表」必崩。**正确顺序是先 `removeAllViews()` 再 `setRowCount()`。**

### 4. 表头绝不能自己能动

表头原来是网格的第 0 行，会**两个方向都滚走**：往右拖周几消失，往下拖节次消失，
屏幕上没有任何坐标参照。拆成三块之后，表头又被套进了一个**自己的 `HorizontalScrollView`**，
靠滚动监听跟表身同步——那仍然是个能被用户拖动的容器，于是出现了「单独滑动表头而表身不动」。

**根因是同步思路本身**：两个可滚动容器靠监听互相追，永远存在被拖开的窗口。
现在改成**只有一个可滚动的视图**：

| 部件 | 实现 |
|---|---|
| 表身 | 唯一持有滚动状态的 `HorizontalScrollView` |
| 表头 | 不可滚动，由表身的 `onScrollChanged` 用 `setTranslationX(-x)` 平移 |
| 节次列 | 不可滚动，由上下的 `onScrollChanged` 用 `scrollTo(0, y)` 跟随 |

**能滚动的视图就是能被拖走的视图**。让跟随者根本没有滚动能力，就不需要同步，也就没有可以失效的同步。
跟随用 `setTranslationX` 而不是 `scrollTo`：平移一个没有内容可滚的视图，天然没有反向回声。

改完立刻冒出第二个问题：**周三之后的日子全没了**。原因是「不可滚动」的视图必须自己声明宽度，
而这件事有两个容易同时踩错的点：

- **宽度要按日格宽度算，不是按节次列宽度**。日格是 `DAY_WIDTH_DP`（118dp），
  节次列只有 `LABEL_WIDTH_DP`（42dp）；按后者算出来的表头只够放三天。
  当时是靠一个临时探针确认的：`headerWidth=1008`，而 7 列实际需要 `7 × 310 = 2170px`。
- **表头视图要覆盖整个滚动范围，不能只覆盖视口**。表头向左平移后，右侧必须有内容补进来；
  若表头只有视口那么宽，平移就把后面的日子移出了裁剪区，一周在第三天就结束了。
  现在表头宽度 = 视口实测宽度 + 最大横向滚动量，两者都在 `addOnLayoutChangeListener` 里
  取实测值设定，不靠估算。同时关掉网格自身的 `clipChildren`，
  否则比网格宽的子视图会先被网格自己的边界裁掉，等不到外层裁剪生效。

对齐的两次返工也记在这里：

| 尝试 | 结果 |
|---|---|
| 只给表头加 `GRID_PAD_DP` 左内边距 | 差 **126px**——那是节次列宽度，是主项，6dp 只是零头 |
| 表头外包一层 `LinearLayout` + 宽度等于节次列的占位 `View` | 正确，但**加到了 `bodyRow` 之后**，表头跑到了屏幕底部 |

实测数据（`uiautomator dump`，1080px 宽设备，列宽 310px）：

```
初始          表头 [142] [452] [762]     表身 [142] [452] [762]
单独拖表头    表头 [142] [452] [762]     表身 [142] [452] [762]   ← 表头不动
拖到最右      表头 周五 / 周六 / 周日     表身同列对齐
```

两条结论：**偏移量只能有一份**（节次列宽度是 `labelColumnWidthPx` 一个字段，两处共用）；
**`LinearLayout` 的子视图顺序 = addView 顺序**，表头要在上面就必须在 `bodyRow` 之前添加。

### 5. 迁移要按「名字 + 时间」匹配，不能只按时间

`replacePlaceholderSchedules` 负责把早期版本编造的作息换成学校的真实时间。
第一版只比对**时间签名**，注释还写着「按签名而不是按名字匹配」——那个理由当时就不对，
而且会丢数据：学校各版本作息的**下午和晚上从来没变过**，所以一个只改了上午时间的用户，
他的作息在下午那 10 节上仍与旧模板完全一致，于是被判定为「没人编辑过的占位表」而**整张替换掉**。

这个 bug 是被 `aSchedulePastMidnightSurvivesAReopen` 抓出来的——它正是「只改上午、保存、重开」这个动作。
现在要求**名字和时间同时命中**：名字 ∈ {标准作息, C/D/E 楼作息}（本应用发布过、现已退役的名字）
且时间等于某个历史模板。

### 6. 每个 Activity 一个 repository，所以快照必须能丢弃

`CourseRepository` 缓存课程、作息与学期列表，而缓存**只被「经过同一个实例」的写入清空**。
每个 Activity 各自 `new CourseRepository(this)`，于是：在设置页导入课表 → 设置页的实例写存储、
清自己的缓存；返回首页 → 首页的 `refreshAll()` 读的是**它自己实例**的旧快照，从不重读存储。
结果总览显示新课程、首页显示旧课程：**两个界面在字面上持有不同的数据。**

修法是 `CourseRepository.reload()`，在**五个 Activity 的 `onResume` 里、读数据之前**调用。
`onResume` 是重新读取的便宜时机，而且这让「另一个界面改了东西」成为常规情况而不是特例。
`CourseRepositoryTest` 有两项测试锁住这个契约，其中一项**故意断言「不 reload 就看不到」**。

### 7. 状态栏：同色，而不是透明

状态栏与 Header 要「融合」，直觉做法是把状态栏设成透明、让 Header 画到它下面。
**在本项目里这条路是错的**：窗口仍然 fit system windows，透明的状态栏后面露出的是窗口背景，
于是在深色 Header 上方多出一条浅色横条，比原来更糟。真正画到状态栏下面需要
`WindowCompat.setDecorFitsSystemWindows(false)`，它在 `androidx.core` 里，本项目不能依赖。

现在做的是给状态栏和 Header **同一个颜色**（`@color/inverse_surface` = `Palette.INVERSE_SURFACE`）。
这两个颜色必须手动保持一致——Header 由 Java 常量绘制，状态栏由 XML 资源绘制。

### 8. 平台对话框与表单控件全部换掉

界面上最后一批「借来的」控件是 `AlertDialog`、`CheckBox`、`RadioButton`。它们能工作，
但外观由平台主题决定，摆在本应用自己的字体与间距旁边就露出来了。实测差距很具体：

| | 平台 `AlertDialog` | `UiKit.dialog` |
|---|---|---|
| 圆角 | 直角 | 24dp，与卡片同一套 |
| 宽度 | 撑满屏幕 | 两侧留 32dp，最宽 400dp |
| 标题 | 比本应用任何标题都大 | `TITLE_MEDIUM`，与卡片标题同级 |
| 动作按钮 | 一律用强调色 | 语义色：**删除是红色，不是品牌色** |
| 遮罩 | 0.6 左右，几乎压黑背景 | 0.38，对话框坐在页面上而不是盖住它 |

**删除按钮是品牌绿**本身就是可用性问题：确认框里最像「推荐操作」的那个按钮，
恰恰是会把数据删掉的。现在 `Action.destructive` 用 `Palette.ERROR`，且永远是文本按钮——
破坏性操作不该被画成主操作。

表单控件同理：`CheckBox` 的方框与 `RadioButton` 的圆点都由平台主题决定尺寸和着色。
现在 `UiKit.checkbox` 与 `UiKit.radioRow` 自绘，并且**整行都是点击目标**（20dp 的方框远低于
48dp 的最小触控尺寸，用户实际瞄的是那一行）。单选点的内点用 `•` 而不是 `●`：
`●` 在标签字号下会填满整个圆环，选中与未选中就不再是一对。

**表单校验失败时的行为**也一并修好了。平台实现要先 `show()`，再
`getButton(BUTTON_POSITIVE).setOnClickListener(...)` 去**替换**框架刚设好的监听器，
只为让校验失败时对话框不关闭。现在 `UiKit.Action.validated` 直接表达这件事：
工作返回 false 就保持打开，错误信息留在引起它的字段旁边。

**排查方式**：这次仍然靠截图对比加坐标核对。用 `uiautomator dump` 找出对话框按钮的中心
（`取消 center=(741,1362)`、`删除 center=(876,1362)`），单击一次验证「点一次就生效」——
上一轮那个「有概率要点两次」的教训之后，这一条是每个按钮都要过的。

---

## 五、上机回归清单

按顺序点一遍，全部通过才算基线可信：

0. **每个按钮只点一次就必须生效**（日期、课程卡片、返回箭头各单击一次）。
   这是「有概率需要点两次」那类 bug 的唯一检查手段，JVM 测试测不到手势派发。
1. 启动后能看到示例课程（首次运行写入 9 门样例课），顶部显示「今天 · 年月日 · 周X」
2. **点标题下方的学期整行 → 进入学期列表**（右侧 chip 只在看旧学期时出现）；
   左右滑动可切换周次；「回到本周」在非本周时出现
3. **切到下一周后，右下角出现「回到今天」；点它必须同时回到本周与今天，并且按钮自身消失**
4. 右上角 ⚙ → 「开学第一周周一」改日期，周次与日期立刻跟着变
5. 右上角 ⚙ → 作息列表应当是**三份**：教学楼作息（默认）、D/E 楼作息、B/C 楼作息。
   逐节核对第 3–5 节与本文档第二节的表格一致，且**界面上不再出现「时间待确认」**
6. 新建一门「教学楼 D305」的课：作息选「自动」，时间应按 D/E 楼作息显示；
   改成「教学楼 E302」「教学楼 F420」「教学楼 C203」应分别得到 D/E、A/F、B/C 的时间
7. **导入后各界面必须一致**：在设置里导入课表 → 返回首页，首页当天的课程要**立刻**变成导入的结果
   （不是重启后）；再到总览确认两边一致
8. 右下角总览按钮 → 打开总课表：**在「总课表」与「单周课表」之间反复切换十次**——
   行数会随之收缩与增长，这是曾经必崩的一步
9. **总览表头钉住、不能被单独拖走、且与列对齐**：
   ① 把课表往右拖，周几那一行必须跟着走并且**始终留在顶部**；
   ② **单独拖表头那一行：表头和表身都必须纹丝不动**（这是「表头能自己滑」那个 bug 的验收动作）；
   ③ 拖动后 `周一` 的左边界必须和它下面那一列课程**在同一条竖线上**
   （差 1–2px 是设备像素，差几十像素就是错位）
10. 在总览页确认**没有任何时刻**：节次列只有数字，课程块只显示课名、周次规则与地点；
    点右下角 `+` 能新建课程，预选的星期是「今天」（看旧学期时是周一）
11. 新增一门与现有课程同天、节次重叠、周次有交集的课 → 必须弹出「时间冲突」确认框
12. 把「开始周」填成 20（或结束周小于开始周）→ 对应输入框红色错误提示
13. 长按课卡 / 编辑页底部都能删除；删除后列表刷新
14. 杀掉进程重进，课程与作息表都还在
15. **学期**：主页点标题下方的学期整行 → 右下角 `+`：表单里**日期必须自己填**，名称留空则自动命名；
    日期格式不对或该周已是别的学期，会在输入框上报错且**弹窗不关闭**
16. 新建学期后课表应当是**空的**（除非勾选「复制当前学期课程」）；切回原学期数据仍在
17. 运行 `./gradlew testDebugUnitTest`，220 项测试全绿
18. **对话框与表单控件**：每个确认框都应当是圆角、两侧留白的面板（不是贴边直角）；
    删除类操作的按钮必须是**红色文本**，取消在它左边；表单框里校验失败时**对话框不关闭**，
    错误留在出错的字段上；勾选框和单选框是应用自己画的（方框带勾、圆环带点），
    并且**点文字也能切换**，不只是点那个小方框

### 本机运行方式

```powershell
$env:GRADLE_USER_HOME='D:\Weihe\.gradle-local\home'
& 'D:\Weihe\.gradle-local\bin\gradle.bat' testDebugUnitTest assembleDebug lintDebug
```

普通机器上直接 `./gradlew.bat testDebugUnitTest` 即可。

**`input tap` 的坐标必须来自 `uiautomator dump`。** 本会话里按 dp 估算图标中心点空过六七次；
dump 出来的 `bounds` 一次就中（图标实际中心是 `(870,256)`，估算值是 `(757,180)`）。
**`uiautomator dump` 也是唯一可靠的布局验证手段**：截图肉眼判断会漏掉 6–126px 的错位，
而把层级里每个节点的 `bounds` 打出来直接比列起点，一次就能定位。

**不要用 PowerShell 的 `Set-Content -Encoding utf8` 改这些文件**：它会写 BOM（Java 报
`非法字符: '\ufeff'`），而且在非 UTF-8 代码页下会把中文内容二次编码成不可逆的乱码——
本文档就被这样毁过一次，只能重写。要批量改就用字节级读写
（`[System.IO.File]::WriteAllBytes` + `UTF8Encoding($false)`），或者直接用编辑工具。

---

## 六、已知的取舍

- **自建 Material 3，不引入 `com.google.android.material`。** 该库不在 Gradle 缓存里、网络不可用，
  加依赖会直接让构建失败。代价是**没有 ripple 的状态机、没有容器变换、没有现成的
  `MaterialCardView` / `BottomSheetDialog` / `TextInputLayout`**；反馈与动效是手写的
  （`UiKit.ripple` 用 `RippleDrawable` 放在 foreground 槽、`UiKit.pressable` 做按压缩放、
  `UiKit.enter` 做错落入场）。若将来允许联网，`UiKit` 的每个工厂方法正好是一个替换点。
- **`MiniJson` 是手写的。** `org.json` 在 JVM 上返回 null 桩，无法测试；自写的 420 行换来
  存储层可测。
- **总览页不显示任何时刻。** 它同时展示多个场所的课程，而各场所作息不同，
  在那一页写时间就是在替别的楼做主张。
- **资源迁移（`colors.xml` / `strings.xml`）仍然推迟。** 颜色与文案目前主要来自
  `Palette` 与 Java 字符串常量，两边靠手工保持一致——`colors.xml` 里只有平台主题真正需要的
  那几个颜色，且有注释提醒同步。
