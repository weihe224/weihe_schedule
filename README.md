<!-- README for the 拾课 Android app -->

<p align="center">
  <img src="icon.png" alt="拾课应用图标" width="112">
</p>

<h1 align="center">拾课（WeiheSchedule）</h1>

<p align="center">轻量、离线优先的 Android 课程表应用</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Language-Java%2017-orange" alt="Java 17">
  <img src="https://img.shields.io/badge/Version-1.1-blue" alt="Version 1.1">
</p>

拾课面向高校学生，帮助你管理学期课表、快速查看每天和每周的课程，并通过上课提醒与手机桌面小组件减少漏课。应用以原生 Android API 和 Java 编写，课程数据保存在本机，不需要注册账号。

- **项目地址：** https://github.com/weihe224/weihe_schedule
- **当前版本：** 1.1（versionCode 2）
- **最低系统要求：** Android 8.0（API 26）

## 功能一览

### 课表查看与课程管理

- **多种课表视图：** 查看单周课表、学期课表总览和当天课程。
- **周次与日期对应：** 根据学期起始日期计算教学周，支持单双周课程。
- **课程增删改查：** 添加、编辑、删除和复制课程，可记录课程名称、教师、地点、上课节次、周次等信息。
- **课程备注：** 为课程补充学习或上课相关说明。
- **搜索与冲突提示：** 按课程名称、教师或地点查找课程，并识别时间冲突。
- **课程颜色：** 为课程设置标记色；同一门课程有多条时间安排时，可按需同步颜色。

### 学期与作息时间表

- **多学期管理：** 每个学期拥有独立的课程数据。
- **自定义学期信息：** 设置学期起始日期和总周数；新建学期时可沿用自动生成的学期名称。
- **多套作息时间表：** 为不同教学区域维护不同的上下课时间，并可根据上课地点关键字匹配作息表。
- **默认作息表：** 为未匹配到地点规则的课程指定兜底作息表。
- **灵活设置节次：** 默认每节课 40 分钟、每天 15 节，可根据学校安排调整。

### 课表导入与备份

- **Excel 课表导入：** 当前支持读取 `.xls` 格式的课表文件。
- **导入预览：** 导入前检查解析结果，导入后查看问题报告；导入时可设置开学日期和学期总周数。
- **JSON 备份：** 导出课表、学期与作息表数据，便于保存或迁移。
- **备份预览与恢复：** 恢复前可预览备份内容；恢复过程包含数据校验与回滚保护。

> **请留意：** 导入课表会替换当前学期的课程安排，操作前请检查导入预览。JSON 备份为明文文件，可能包含课程、教师和上课地点等个人信息，请妥善保管，不要上传到公开仓库。

### 上课提醒与桌面小组件

- **上课提醒：** 可设置提前提醒时间；设备重启或应用更新后会尝试恢复提醒排程。
- **三种桌面小组件：**
  - **2×1：** 紧凑显示当前或下一门课。
  - **2×2：** 显示当前或下一门课，并尽可能展示紧随其后的课程。
  - **3×2：** 以列表形式展示最多两门相关课程。
- **随课表更新：** 修改课程后，小组件会同步刷新；点击小组件可进入应用查看详情。

> Android 13 及以上设备需要允许通知权限。Android 12 及以上设备的精确闹钟设置也可能影响提醒的准时程度；如果提醒没有出现，请检查应用内提醒设置和系统权限。桌面小组件受空间限制，不会展示全天的所有课程。

## 界面与实现

- 使用原生 Android Views 构建应用页面。
- 桌面小组件使用 Android `RemoteViews` 所需的 XML 布局资源。
- 使用应用内日期和时间选择器，保持界面交互一致。
- 当前不包含账号系统、云端同步或网络请求功能。

## 技术栈

| 项目                  | 配置                           |
| --------------------- | ------------------------------ |
| 开发语言              | Java 17                        |
| Android Gradle Plugin | 8.13.0                         |
| Gradle Wrapper        | 8.13                           |
| compileSdk            | 34                             |
| targetSdk             | 34                             |
| minSdk                | 26（Android 8.0）              |
| applicationId         | `com.weihe.schedule`           |
| 主要依赖              | `jxl-2.6.12.jar`（读取 Excel） |
| 单元测试              | JUnit 4.13.2                   |

项目未使用 Kotlin、Jetpack Compose、AndroidX 或数据库框架；主要业务逻辑放在可独立测试的 Java 类中。

## 获取与运行

### 环境要求

- Android Studio（支持 Android Gradle Plugin 8.13.0）
- JDK 17 或更高版本
- Android SDK Platform 34
- Windows 用户可直接使用仓库自带的 `gradlew.bat`

### 在 Android Studio 中打开

1. 克隆仓库：
   ```bash
   git clone https://github.com/weihe224/weihe_schedule.git
   ```
2. 在 Android Studio 中选择 **Open**，打开包含 `settings.gradle` 的项目根目录。
3. 等待 Gradle Sync 完成，并确认本机 Android SDK 配置正确。
4. 选择 `app` 运行配置，在模拟器或已连接的 Android 设备上运行。

### 在 Windows 命令行构建

在项目根目录运行：

```bat
:: 运行 JVM 单元测试、构建 Debug APK，并检查 Lint
gradlew.bat --rerun-tasks testDebugUnitTest assembleDebug lintDebug
```

Debug APK 默认输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如果已安装 Android Platform Tools，并且设备已开启 USB 调试，可执行：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

注意：构建 APK 不会自动安装到手机；安装后若界面没有变化，请确认安装的是刚刚生成的文件。

### 构建签名版本

如需生成供长期使用或分发的签名 APK，可在 Android Studio 中选择：

**Build → Generate Signed Bundle / APK… → APK**

然后创建或选择自己的签名密钥。请将 keystore 和相关密码保存在仓库之外的安全位置，并妥善备份。后续覆盖安装升级通常需要继续使用同一签名密钥；更换密钥可能导致无法直接覆盖安装。

## 项目结构

```text
app/
├── src/main/java/com/weihe/schedule/
│   ├── *Activity.java   页面与交互
│   ├── model/           课程、学期、作息表等数据模型
│   ├── data/            本地存储、JSON 编解码与备份恢复
│   ├── rule/            课程查询、冲突检测、周次等业务规则
│   ├── importer/        Excel 读取与课表解析
│   ├── reminder/        上课提醒与排程恢复
│   ├── widget/          桌面小组件
│   ├── ui/              公共 UI 组件与选择器
│   └── design/          颜色与界面设计常量
├── src/main/res/        图标、主题、动画和小组件资源
└── src/test/            JVM 单元测试
tools/                   图标生成及开发辅助工具
AGENTS.md                项目开发规范
AGENT_HANDOFF.md         当前开发状态与交接记录
AGENT_HANDOFF_ARCHIVE.md 历史交接记录
```

## 测试

测试主要针对不依赖 Android 运行时的业务逻辑、数据读写、课表导入、提醒计算和小组件渲染规则。

```bat
gradlew.bat --rerun-tasks testDebugUnitTest
```

测试报告：

```text
app/build/reports/tests/testDebugUnitTest/index.html
```

当前仓库以 JVM 单元测试为主，没有 Android Instrumentation 测试。因此，涉及页面布局、系统通知、桌面小组件和真实设备权限的改动，仍应在模拟器或实际设备上手动验证。

## 数据与隐私

- 课程、学期和作息时间表保存在设备本地的 `SharedPreferences` 中。
- 当前代码没有配置账号登录、云端同步或网络请求功能。
- 应用会按功能申请通知、精确闹钟及开机完成广播相关权限，用于上课提醒和恢复排程。
- 备份文件是明文 JSON。请勿将真实课表、备份文件、签名密钥或密码提交到公开仓库。

## 参与开发

准备修改代码前，建议先阅读：

- [AGENTS.md：长期开发规范](AGENTS.md)
- [AGENT_HANDOFF.md：当前项目状态](AGENT_HANDOFF.md)
- [AGENT_HANDOFF_ARCHIVE.md：历史交接记录](AGENT_HANDOFF_ARCHIVE.md)

遇到问题时，可在 GitHub 仓库的 Issues 中提交复现步骤、Android 版本和相关日志。请不要附带包含个人课程信息的备份文件。

---

<sub>说明：本仓库当前未提供单独的 LICENSE 文件；在确认授权前，请勿假定项目可以自由再分发或用于商业用途。</sub>
