# 长庚环 AI 填充用：配置与环境事实表

## 1. 文档定位

本文档的目标是帮助后续 AI 分清三件事：

1. 哪些是已经写在代码里的事实。
2. 哪些是通过环境变量、Gradle 属性、`local.properties`、Supabase 后台或第三方平台注入的事实。
3. 哪些配置一旦缺失，会导致哪些功能看起来“坏了”。

本文档不记录任何真实 secret，不暴露本机私有值。它只描述机制、角色、依赖关系和失效影响。

## 2. Android 构建关键配置

### 2.1 当前已落地事实

Android 当前唯一运行入口为 `:app-shell`。  
构建关键事实包括：

- 运行模块：`:app-shell`
- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 24`
- Kotlin / Java 目标为 11

Android 的对外 API 基地址不是写死在 Kotlin 业务代码里，而是通过 Gradle 属性注入到 `BuildConfig.API_BASE_URL`。

### 2.2 当前运行行为

当前 `gradle.properties` 中：

- `DEBUG_API_BASE_URL=https://cloud.changgengring.cyou/`
- `RELEASE_API_BASE_URL=https://cloud.changgengring.cyou/`

因此当前 debug 与 release 的 API 主域名都已统一到：

- `https://cloud.changgengring.cyou/`

### 2.3 关键代码与文档落点

- `gradle.properties`
- `app-shell/build.gradle.kts`
- `README.md`
- `docs/00_入口与维护/00_项目总览与使用指南.md`

### 2.4 当前边界与不要误写的点

- 不要再把 Android 当前主域名写成旧的 `cloud-next-psi.vercel.app`。
- 不要把 `app/` 写成当前 Android 运行模块；它已归档。
- 不要把 `BuildConfig` 中的值写成手填常量；它们来自 Gradle 注入。

### 2.5 适合写进正式材料的标准表述

长庚环 Android 端采用 `:app-shell` 作为唯一运行宿主，运行时关键网络配置通过 Gradle 统一注入，使 debug 与 release 构建共享同一套生产 API 域名口径。

## 3. 生产域名与 API 基地址

### 3.1 当前已落地事实

当前系统的统一业务域名是：

- `https://cloud.changgengring.cyou`

它承担以下角色：

- Android API 基地址
- Web 登录页与认证回跳页承载域名
- 云端 `cloud-next` 的对外统一访问域名

### 3.2 当前运行行为

Android 登录、注册、忘记密码、资料、报告、医生问诊增强、机器人叙事等主接口，都会通过这一域名进入云端。

邮箱确认与密码重置的邮件回跳页，也使用这个域名下的页面：

- `/auth/confirm`
- `/reset-password`

### 3.3 当前边界与不要误写的点

- 不要把 `vercel.app` 预览域名写成正式对外主域名。
- 不要把 `cloud.changgengring.cyou` 写成“只服务 Web 前端”；它同时服务 Android API 和认证回跳页。

## 4. Supabase / Resend / Vercel / Cloudflare 的角色分工

### 4.1 Supabase

当前 Supabase 的角色主要是：

- 认证底座
- 用户状态与资料相关数据底座
- 邮件确认和重置密码能力的上游执行者

当前注册、登录、重发确认邮件和忘记密码并不是 Android 直接发邮件，而是：

Android -> `cloud-next` -> Supabase Auth -> 邮件链路

### 4.2 Resend

当前 Resend 的角色是：

- 邮件发送服务
- 通过 Supabase Custom SMTP 接入认证邮件链路

也就是说，项目当前不是在 `cloud-next` 内直接手写调用 Resend API 发认证邮件，而是借 Resend 作为 Supabase 的 SMTP 发信基础设施。

### 4.3 Vercel

当前 Vercel 的角色是：

- 托管 `cloud-next`
- 对外发布 `cloud.changgengring.cyou` 所指向的生产站点

### 4.4 Cloudflare

当前 Cloudflare 的角色是：

- DNS 托管
- 域名记录管理
- 为 `cloud.changgengring.cyou` 和 `mail.changgengring.cyou` 提供 DNS 解析支撑

### 4.5 当前边界与不要误写的点

- 不要把 Resend 写成独立业务后端；它当前主要是邮件基础设施。
- 不要把 Supabase 写成整个项目所有数据的唯一后端；项目还有本地数据库和自定义业务 API。
- 不要把 Cloudflare 写成应用运行容器；当前应用实际运行在 Vercel。

### 4.6 适合写进正式材料的标准表述

长庚环当前采用 Vercel 托管 `cloud-next`，使用 Cloudflare 统一域名解析，使用 Supabase 作为账号体系底座，并通过 Resend 提供 SMTP 发信能力，从而构成“统一域名、统一认证回跳、统一邮件通道”的云端配置体系。

## 5. 讯飞配置的注入方式

### 5.1 当前已落地事实

Android 项目中，讯飞相关配置不是写死在 Kotlin 类中，而是由 `app-shell/build.gradle.kts` 从 `local.properties` 读取后注入 `BuildConfig`。

当前注入的能力范围包括但不限于：

- IAT
- TTS
- OCR
- Spark
- RTASR
- RAASR
- AIUI / 数字人
- 虚拟人 Avatar ID

### 5.2 当前运行行为

运行时 `XfyunConfig` 会从 `BuildConfig` 读取这些配置，并按能力类型组织成具体凭据对象。  
数字人相关能力还会同时依赖：

- AIUI 相关配置
- 虚拟人 Avatar ID

### 5.3 当前边界与不要误写的点

- 不要把讯飞配置写成存放在仓库常量文件中；当前做法是本地机密配置 + Gradle 注入。
- 不要把“所有讯飞能力都必须依赖同一个配置项”写成事实；不同能力的键值集合并不相同。
- 不要把注释乱码等同于配置失效，真正生效的是 `KEY=VALUE` 行而不是注释。

### 5.4 适合写进正式材料的标准表述

长庚环对讯飞能力采用本地配置、Gradle 注入、运行时统一读取的方式管理，将语音、OCR、Spark 和数字人相关参数隔离在构建配置层，而非硬编码在业务逻辑中。

## 6. `local.properties`、Gradle、Vercel 环境变量、Supabase 后台配置之间的关系

### 6.1 `local.properties`

它主要面向 Android 本地构建时使用，典型承载内容包括：

- 讯飞能力配置
- 本地开发环境特定参数

这些值不应提交到仓库。

### 6.2 Gradle 属性与 BuildConfig

Gradle 负责把一部分配置读出来并注入到 Android 运行时，例如：

- `API_BASE_URL`
- 讯飞相关配置
- 若存在，也包括 OpenRouter 等模型接入配置

### 6.3 Vercel 环境变量

Vercel 主要用于 `cloud-next` 的部署配置，典型包括：

- `APP_PUBLIC_BASE_URL`
- Supabase 相关服务端 key
- 内部 worker token
- 管理后台、模型、供应商相关环境变量

### 6.4 Supabase 后台配置

Supabase Dashboard 当前承担：

- Site URL
- Redirect URLs
- Custom SMTP
- Auth 行为控制

也就是说，认证功能是否真正闭环，不只看 `cloud-next` 代码，还要看 Supabase 后台配置是否与当前域名一致。

### 6.5 当前边界与不要误写的点

- 不要把 `local.properties` 写成云端运行时配置源；它只影响本地 Android 构建。
- 不要把 Vercel 环境变量写成 Android 本地参数；它们属于云端部署层。
- 不要把认证邮件的回跳逻辑写成只由 App 决定；实际还依赖 Supabase Redirect URLs。

## 7. 哪些值不能写进仓库

当前不应写进仓库的内容包括：

- 各类 API Key、Secret、Token
- `local.properties`
- `keystore.properties`
- 签名文件
- 本机绝对路径相关私密配置
- 可直接用于第三方服务调用的私有密钥

在文档层面，后续 AI 应避免：

- 直接抄录密钥样式值
- 把本地私有配置写进正式报告
- 把示例环境变量当成真实生产值

## 8. 哪些配置不完整会导致哪些功能失效

### 8.1 API 基地址不正确

可能导致：

- Android 登录、报告、医生、机器人等主接口全部失败或打到旧云端

### 8.2 `APP_PUBLIC_BASE_URL` 不正确

可能导致：

- 邮箱确认链接回跳错误
- 重置密码链接落到错误页面
- 生产域名与邮件回跳不一致

### 8.3 Supabase Redirect URLs 未配置

可能导致：

- 忘记密码邮件点开后被重定向到登录页或无效页
- 邮箱确认流程不闭环

### 8.4 Supabase Custom SMTP / Resend 未配置好

可能导致：

- 注册确认邮件发送失败
- 重置密码邮件发送失败
- 用户觉得“注册或忘记密码不工作”

### 8.5 讯飞配置缺失

可能导致：

- 语音识别、语音合成、OCR、Spark 或数字人能力提示“未配置”
- 数字人形象和 AIUI 相关能力初始化失败

### 8.6 本地 BLE / 数据库配置异常

可能导致：

- 戒指采集链路无法启动
- 今日页、趋势页没有可用数据
- 本地与云端同步失去基础数据来源

## 9. 当前边界与统一写法建议

1. 当前项目采用“代码事实 + 环境注入 + 平台后台配置”三层协作，不是单一配置文件就能决定所有行为。
2. Android 构建配置、云端部署配置和第三方平台配置必须分开描述。
3. 认证邮件链路的闭环依赖 Android、`cloud-next`、Supabase、Resend 和域名解析多层共同成立。
4. 不要在任何对外材料中暴露真实密钥或本机私有值。

## 10. 适合写进正式材料的统一标准表述

长庚环当前采用分层配置机制：Android 侧通过 Gradle 与 `local.properties` 管理本地构建参数和讯飞能力接入，云端 `cloud-next` 通过 Vercel 环境变量管理运行时配置，认证回跳与邮件链路由 Supabase 与 Resend 协同完成，统一业务域名则由 Cloudflare 与 Vercel 联合提供。这种设计将运行代码、环境注入和第三方平台控制面明确分离，既便于部署，也降低了敏感信息直接进入仓库的风险。

