# 标题
附录 A：Android 现行架构与运行入口

- 适用任务：Android 架构理解、模块级开发文档写作、页面与数据流定位
- 阅读优先级：高
- 是否允许对外直接引用：允许，但必须保留“现行 / legacy”说明

## 1. 运行入口与壳层

当前 Android 运行时只认 `:app-shell`。

关键入口文件：

- `app-shell/src/main/java/com/example/newstart/SleepHealthApp.kt`
- `app-shell/src/main/java/com/example/newstart/MainActivity.kt`
- `app-shell/src/main/java/com/example/newstart/service/DataCollectionService.kt`
- `core-common/src/main/res/navigation/mobile_navigation.xml`

`SleepHealthApp` 负责：

- 应用级初始化
- 仓储和配置初始化
- 演示 bootstrap 同步入口
- 本地模型 / 云会话相关启动准备

`MainActivity` 负责：

- 主导航承载
- 页面标题与页面 key 维护
- 机器人展示 / 隐藏 / 尺寸策略
- 机器人交互播报和页面讲解

## 2. 模块分层

### 2.1 壳层与入口层

- `app-shell`

职责：

- Application 与 Activity
- Android 构建目标
- 入口资源和顶层壳逻辑
- 全局数字人
- 数据采集服务

### 2.2 共享基础层

- `core-common`
- `core-model`
- `core-data`
- `core-ble`
- `core-network`
- `core-db`
- `core-ml`

这些模块分别承担：

- `core-common`：导航资源、主题、公共 UI、部分共享控制器和资源
- `core-model`：领域模型、协议目录、评估与干预核心枚举 / 数据类
- `core-data`：仓储层、数据编排、云端与本地整合
- `core-ble`：戒指 BLE 协议与解析
- `core-network`：API client、会话、网络模型、部分讯飞 / 云端接入
- `core-db`：Room 数据库、DAO、实体、迁移
- `core-ml`：端侧模型、解析器、本地 AI / 规则兜底

### 2.3 业务特性层

- `feature-home`
- `feature-device`
- `feature-doctor`
- `feature-relax`
- `feature-trend`
- `feature-profile`

这些模块不是简单占位，它们承担当前用户主路径的大部分页面逻辑。

## 3. 页面到模块的现行映射

### 3.1 今日

位置：

- `feature-home/ui/home/MorningReportFragment.kt`
- `feature-home/ui/home/MorningReportViewModel.kt`
- `feature-home/ui/home/HealthDataFragment.kt`

职责：

- 展示当日恢复分与解释
- 消费健康指标、本地恢复分、睡眠摘要、建议
- 与趋势和干预入口联动

### 3.2 设备

位置：

- `feature-device/ui/device/DeviceFragment.kt`
- `feature-device/ui/device/DeviceViewModel.kt`

职责：

- 设备扫描、连接
- 演示戒指和真实戒指展示策略
- 连接状态与采集状态联动

### 3.3 医生

位置：

- `feature-doctor/ui/doctor/DoctorChatFragment.kt`
- `feature-doctor/ui/doctor/DoctorChatViewModel.kt`
- `feature-doctor/ui/doctor/DoctorInferenceEngine.kt`

职责：

- 医生对话
- 问诊流程控制
- 问诊评估快照
- 问诊单生成
- 与桌面机器人讲解联动

### 3.4 趋势

位置：

- `feature-trend/ui/trend/SleepTrendFragment.kt`
- `feature-trend/ui/trend/SleepTrendViewModel.kt`

职责：

- 近 7 天 / 近 30 天趋势
- 周报 / 月报
- 干预效果回顾
- 周期总结与 CTA

### 3.5 我的

位置：

- `feature-profile/ui/profile/ProfileFragment.kt`
- `CloudAuthFragment.kt`
- `PersonalInfoFragment.kt`
- `NotificationSettingsFragment.kt`
- `DataPrivacyFragment.kt`
- `AboutProjectFragment.kt`

职责：

- 云端账号登录与资料同步
- 账户设置四页
- 通知、隐私、项目说明
- 演示模式相关设置和资料同步入口

### 3.6 放松 / 干预扩展链

核心位置：

- `feature-relax/ui/relax`
- `feature-relax/ui/intervention`

这一层当前实际覆盖：

- Relax Hub
- 症状引导
- 医检报告分析
- 药物识别
- 饮食分析
- 呼吸训练
- Zen 交互
- 干预中心
- 干预会话
- 复盘

## 4. 数据采集与本地状态

### 4.1 采集层

Android 侧真实采集链不是页面本身，而是：

- `DataCollectionService`
- `DeviceViewModel`
- `core-ble`

它负责：

- 连接戒指
- 解析原始帧
- 生成健康指标 / PPG / 设备状态
- 写入本地数据库

### 4.2 本地数据库层

Room 由 `core-db` 持有，承担：

- 睡眠数据
- 健康指标
- 医生会话与评估
- 医检报告与指标
- 干预任务 / 执行
- 放松会话
- 画像快照
- 药物与饮食记录
- 恢复分与其他衍生状态

### 4.3 仓储编排层

`core-data` 是 Android 内真正把“页面需求”和“本地 / 云端数据源”粘起来的层。很多页面并不是直接访问 DAO，而是通过 repository / service 组合拿到结果。

## 5. 机器人与全局讲解层

当前机器人相关关键文件位于：

- `app-shell/ui/avatar/Avatar3DView.kt`
- `AvatarEntryAudioRegistry.kt`
- `DesktopAvatarNarrationService.kt`
- `DesktopAvatarPromptBuilder.kt`
- `AvatarSpeechPlaybackController.kt`

当前真实形态：

- 页面可带入场讲解
- 点击机器人可生成交互讲解
- 文案生成与 TTS 播报解耦
- 页面存在特定隐藏 / 缩放 / 布局规则

当前这部分已经不是 demo 玩具，而是现行产品体验的一部分。

## 6. Android 当前必须写清的边界

1. 当前主运行入口是 `:app-shell`，不是 `app/`。
2. 现行业务包名仍大量保留 `com.example.newstart.*`，不能只靠包名判断模块归属。
3. `feature-*` 里既有当前主链页面，也有少量包装壳或过渡入口，必须以导航与调用链为准。
4. Android 端当前真实主链支持：
   - 页面交互
   - BLE 采集
   - Room 落库
   - 一部分本地模型 / 规则兜底
5. Android 端并不承担全部 AI 能力，很多高价值链路仍依赖云端增强。
