# Courtside 球场边

一个使用 Kotlin、Jetpack Compose 和 miuix 的 NBA Android 小工具。首版 0.1.0，供个人安装调试。

## 已实现

- 选择并保存一支主队、主题模式和全局防剧透设置。
- 主队主题卡片、下一场已公布比赛、今日赛事。
- 设备时区下的日期赛程、日期跳转、主队筛选。
- 常规赛东西部排名；未开赛时不编造排名。
- 比赛详情、分节比分、球队统计、球员技术统计。
- ESPN 真实数据、应用私有缓存、错误提示、手动刷新。
- 前台对进行中或已到计划开赛时间的比赛每分钟刷新，后台停止轮询。
- 内置已核验的 30 队基础资料作为断网时的选队后备；没有内置假比分或演示赛程。

## 技术与构建

miuix 0.9.4；Android Gradle Plugin 9.4.1；Compose compiler 2.4.20；Gradle 9.7.1。
最低 API 24，target API 36，compile API 37。编译 SDK 要求不等于安装最低版本。
使用 JDK 21、Android SDK Platform 37，联网下载 Maven 依赖。

在 `local.properties` 设置自己的 `sdk.dir`，或配置 `ANDROID_HOME`，然后运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。调试包使用本机 Android debug 签名；本次同一工作环境下后续构建可覆盖安装。其他机器重建时签名可能不同。

## 数据来源与限制

数据来源为 ESPN 的公开赛事接口，不需要 API Key；并非官方对外承诺的开发者 API，没有服务等级或持续兼容性保证。仅供当前个人测试，不构成公开分发或商业使用的授权。

- `site.api.espn.com/apis/site/v2/sports/basketball/nba/teams`
- 同一基础路径下 `scoreboard?dates=YYYYMMDD&limit=100`
- `teams/{id}/schedule?season={year}&seasontype={1,2,3}`
- `summary?event={id}`
- `site.web.api.espn.com/apis/v2/sports/basketball/nba/standings?season={year}&seasontype=2`

日期查询覆盖相邻来源日期，再按比赛开赛瞬间转换到设备时区并过滤、去重。更新标签表示获取时间，不保证与赛场同步。没有比赛或字段缺失时不补造数值。多日期请求部分失败时明确标记数据不完整。

首次测试时从首页选择主队。若网络失败，可以继续选队，但真实比赛需要 ESPN 可访问；不需要填入密钥。在比赛详情点击刷新可重试。没有新闻、视频、投注或后台通知功能。

当前没有真实球员图片、复杂玻璃模糊、多队关注、通知、小组件或自动数据源切换。

## 主要源码

- `Data.kt`：模型、JSON 解析、真实数据请求和缓存。
- `AppViewModel.kt`：页面状态、设置持久化、刷新生命周期。
- `MainActivity.kt`：miuix 主题、卡片、开关、按钮、悬浮导航和自定义界面。
- `ParsingTest.kt`：真实响应样本、跨时区日期和异常状态测试。

## 第三方

miuix、AndroidX、Kotlin、OkHttp、Coil 等依赖分别遵循其许可证。miuix 项目采用 Apache-2.0。球队名称、标识及赛事数据不因应用源码可见而转移权利。应用无官方合作关系。

参考资料：
- https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4
- https://github.com/compose-miuix-ui/miuix
- https://www.espn.com/nba/

## 验证记录

具体构建与模拟器验证结果见交付目录中的《首版说明.md》。用户手机上的网络、系统字体、厂商系统行为及性能仍待安装后反馈。
