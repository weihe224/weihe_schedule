# 拾课（WeiheSchedule）

一个**纯 Java** 的 Android 课程表应用。面向高校学生：把教务系统导出的 Excel 课表导入进来，
在手机上以周课表的形式查看、编辑和提醒。

单机应用，**不需要账号、不联网、不上传任何数据**，全部内容保存在本机。

---

## 主要功能

以下均为当前代码中**实际存在**的功能。

### 课表查看
- **课表总览**：整个学期一张网格，周一到周日为列，节次为行
- **单周课表**：按周查看，显示每一列的具体日期
- **首页**：当天课程列表、周次切换、星期导航、回到本周 / 回到今天
- 同一门课一周上多次时，各次作为独立记录分别展示
- 时间冲突的课程会被标出

### 课程管理
- 新增 / 编辑 / 删除课程
- **复制课程**：以现有课程为模板新建一条，不覆盖原记录
- **课程搜索**：按名称、教师、地点搜索
- **课程详情**：名称、时间、周次、地点、教师分层展示
- **颜色**：9 种可选课程颜色；修改一条记录的颜色时，可选择**只改当前课次**或**同步本学期所有同名安排**（只改颜色，其他字段不动）
- 教师 / 地点**快捷填充**：根据已有课程给出常用值
- 编辑表单在页面重建或进程被回收后**恢复未保存的输入**

### 学期与作息表
- 多个学期，各自独立的课表与作息时间
- 新建学期时**自动推算下一个学期名称**（如 `2026 秋季学期` → `2027 春季学期`）
- 学期起始日期（第 1 周周一）可调整，课程周次对应的日期随之变化
- **多套作息时间表**（不同教学楼上下课时间不同），按**地点关键字**自动匹配；
  可指定一张默认作息表作为兜底
- 每节课时长可配置，每日节次数量随课程动态增减

### 导入与备份
- **Excel 导入**：读取教务系统导出的 `.xls` 课表（依赖内置的 jxl）
- 导入时可指定**开学日期**与**学期总周数**
- 导入前有预览与确认，导入后有问题报告
- **备份导出**：导出为 JSON 文件（明文，含教师姓名与上课地点）
- **备份预览与恢复**：恢复前可预览内容；恢复过程带快照与回滚，失败不会留下半套数据

### 提醒
- 上课提醒，可设置提前时间
- 设备重启或应用更新后自动重新排程

### 界面
- 日期与时刻统一使用应用内的选择器（日期为月历，时间为时/分滚轮），全项目不使用系统日期/时间弹窗
- 深绿色 + 浅色背景，统一的圆角、间距与字号规范

---

## 技术栈

以 `app/build.gradle` 与 `build.gradle` 的实际配置为准：

| 项 | 值 |
|---|---|
| 语言 | **Java**（项目内无 Kotlin 文件） |
| UI | Android Views，**全部在 Java 中构建**（`res/layout` 不存在） |
| Android Gradle Plugin | **8.13.0** |
| Gradle | **8.13** |
| Java 版本 | `sourceCompatibility` / `targetCompatibility` = **17** |
| 编译 / 目标 SDK | `compileSdk` **34**，`targetSdk` **34** |
| 最低 SDK | `minSdk` **26**（Android 8.0） |
| 包名 / 版本 | `com.weihe.schedule`，`versionName 1.0` |
| 依赖 | 仅 `libs/jxl-2.6.12.jar`（读取 Excel）与 `junit:junit:4.13.2`（测试） |

未使用 AndroidX、Material Components、Compose、Room 或任何第三方 UI / 网络库。

---

## 环境准备

| 需要 | 说明 |
|---|---|
| **JDK** | **17 或更高**（本项目在 JDK 20 上构建通过；`sourceCompatibility` 为 17） |
| **Android SDK** | 需安装 **Platform 34** 与对应 Build-Tools；设置 `ANDROID_HOME` 或 `sdk.dir` |
| **Android Studio** | 任意支持 AGP 8.13 的版本（Ladybug 及以后）。**非必需** —— 命令行 Gradle 即可构建 |
| 网络 | 首次构建需联网下载 Gradle 发行版与依赖（仓库已配置国内镜像优先） |

打开项目：Android Studio → `Open` → 选择项目根目录（含 `settings.gradle` 的那一层），
等待 Gradle Sync 完成后即可运行 `app` 配置。

> 本项目自带 Gradle Wrapper（`gradlew.bat` / `gradlew`）。若本机没有可用的 Gradle，
> 用 Wrapper 即可；下文命令以 Wrapper 为例。

---

## 构建与测试

在项目根目录执行（Windows）：

```bat
:: 单元测试
gradlew.bat testDebugUnitTest

:: 构建 Debug APK
gradlew.bat assembleDebug

:: Lint 检查
gradlew.bat lintDebug

:: 三个一起跑（推荐，改动后作为提交前门禁）
gradlew.bat --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

- **务必带上 `--rerun-tasks`**：否则 Gradle 可能跳过未变化的测试任务，让你读到上一次的结果。
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`（文本版 `lint-results-debug.txt`）
- **构建产物**：`app/build/outputs/apk/debug/app-debug.apk`

安装到已连接的设备 / 模拟器：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> 注意：`assembleDebug` **不会**自动安装。改了界面后如果设备上看起来没变化，
> 先确认是否漏了上面的 `adb install`。

### 测试的边界

- 测试全部是 **JVM 单元测试**（`app/src/test/`），覆盖业务规则、数据读写、颜色与对比度等。
- 项目**没有仪器测试**（不存在 `app/src/androidTest/`），因此**界面行为无法自动化验证**，
  涉及布局与交互的改动必须在设备或模拟器上手动走查。

---

## 项目目录说明

```
app/
  build.gradle                 应用模块配置（SDK 版本、依赖）
  libs/jxl-2.6.12.jar          Excel 读取库
  src/
    main/
      AndroidManifest.xml
      java/com/weihe/schedule/
        *Activity.java          10 个页面（首页、课表总览、编辑课程、设置……）
        model/                  数据模型与纯计算（Course / Semester / BellSchedule / ScheduleUtils）
        data/                   数据读写：CourseRepository（唯一入口）、JSON 编解码、备份
        rule/                   纯业务规则（查询、冲突、校验、草稿、周次、命名……）
        reminder/               提醒的计算、调度与广播接收
        importer/               Excel 读取与课表解析
        ui/                     公共 UI 组件（UiKit / UiTokens / Pickers / WheelColumnView / Insets）
        design/                 设计常量（Palette 调色板、色彩对比度计算）
      res/
        drawable/               图标（vector）
        values/                 颜色、字符串、主题、动画
    test/java/com/weihe/schedule/   JVM 单元测试
build.gradle                   根构建脚本（插件版本）
settings.gradle                模块与仓库配置
gradlew.bat / gradlew          Gradle Wrapper
tools/                         开发辅助脚本（图标生成、UI dump 等），不参与打包
```

`app/src/main/res/` 下**没有 `layout/`** —— 界面全部由 Java 代码构建。

---

## 数据与兼容性

### 数据保存在哪里

全部使用 `SharedPreferences`，**没有数据库、没有网络**：

| 存储 | 内容 |
|---|---|
| `weihe_schedule` | 学期、课程、作息表、备份快照 |
| `weihe_schedule_reminders` | 提醒设置与已排程的提醒 |

对数据的读写**只经过** `data/CourseRepository`，页面不直接访问 `SharedPreferences`。

### 关键格式

- **日期**：`yyyy-MM-dd` 字符串
- **时刻**：作息表的每节起止时间存的是**自午夜起的整数分钟**（如 `495` 表示 08:15）
- **晚课**：以 `24:xx` 表示（上限 `29:59`），使晚间节次仍属于当天的课表
- **课程颜色**：每条课程自带一个 ARGB 整数
- **按学期分键**：`courses:<学期id>`、`semester_start:<学期id>`

### 兼容要求（改动前务必注意）

1. **不要更改上述键名、JSON 结构与字段含义** —— 已有用户的课表会读不出来。
2. **课程颜色以每条记录自带的值为准**：调色板可以调整，但**已保存的颜色不会被重新上色**；
   不在当前调色板中的旧颜色仍会被保留并在选择器中显示出来。
3. **作息表时长的兼容**：存储中缺少时长字段的旧数据按默认值（40 分钟）解释，不会读取失败。
4. **导入**：导入会替换当前学期的课表，操作前会二次确认。
5. **备份是明文 JSON**，包含教师姓名与上课地点；导出与恢复都以文件形式由用户自己保管。

### 隐私

应用不联网、不上报任何数据、不申请与课程表无关的权限。请勿把本机的备份文件
（含个人课表信息）提交到仓库。

---

## 开发规范与项目状态

| 文档 | 用途 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | **长期开发规范**：技术栈约束、分层与编码规范、构建与测试要求、工作区保护规则 |
| [`AGENT_HANDOFF.md`](AGENT_HANDOFF.md) | **当前项目状态的唯一有效文档**：已完成/未完成、未提交修改、已知问题、下一步任务 |
| [`AGENT_HANDOFF_ARCHIVE.md`](AGENT_HANDOFF_ARCHIVE.md) | **历史归档**：更早的交接记录与逐轮开发日志，仅作背景参考，其中的完成度判断可能已过期 |

贡献代码前请先读 `AGENTS.md`；了解当前进度与遗留问题请读 `AGENT_HANDOFF.md`。
