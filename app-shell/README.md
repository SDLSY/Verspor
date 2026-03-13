# app-shell

`app-shell` 是当前 Android 的唯一运行入口。

它的职责很明确：

- 提供 Application / Activity / Manifest / 打包配置
- 承接当前 Android 运行时代码与公共宿主资源
- 作为后续继续向 `core-*` / `feature-*` 收敛的宿主层

## 当前定位

- Gradle 模块：`:app-shell`
- 类型：Android Application
- 当前状态：活跃入口，当前唯一 APK 宿主

## 当前构建来源

`app-shell/build.gradle.kts` 当前只会引入：

- `app-shell/src/main/...` 下的运行时代码与资源
- `app-shell/src/debug/...`
- `app-shell/src/test/...`
- `app-shell/src/androidTest/...`
- `core-*` 模块提供的基础能力

这意味着：

- 真机运行入口是 `:app-shell`
- `app/` 已不再参与 APK 构建
- 当前仍有不少历史业务代码集中在 `app-shell`，但它们已经脱离 legacy `sourceSets` 链路

## 当前宿主内容

当前 `app-shell/src/main/java` 已承载：

- 运行入口与导航宿主
- 医生、放松、趋势、我的等历史页面实现
- 网络、仓库、AI 服务与工具类中尚未继续模块下沉的部分

## 模块依赖

`app-shell` 已直接依赖：

- `:core-model`
- `:core-common`
- `:core-db`
- `:core-ble`
- `:core-data`
- `:core-network`
- `:core-ml`

其余业务能力正逐步向 `feature-*` 和更稳定的 `core-*` 模块继续收敛。

## 常用命令

```powershell
gradlew.bat :app-shell:assembleDebug
gradlew.bat :app-shell:testDebugUnitTest
gradlew.bat :app-shell:lint
gradlew.bat :app-shell:connectedDebugAndroidTest
```

## 维护约定

- 新业务不要继续优先堆到 `app-shell/src/main`
- 不要再向 `app/` 增加运行时代码
- 若新增公共能力，优先放入 `core-*`
- 若是页面内逻辑，优先放入对应 `feature-*`
- 当某块业务完成迁移后，应同步从 `app-shell` 继续下沉，而不是回写 legacy 目录

相关文档：

- `docs/MODULE_MAP.md`
- `docs/TECH_REFACTOR_STATUS.md`
