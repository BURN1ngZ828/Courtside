# Courtside 球场边

一个使用 Kotlin、Jetpack Compose 和 miuix 的 NBA Android 小工具。当前版本 0.2.0，供个人安装调试。

## 已实现

- 在设置页选择并保存一支主队和主题模式。
- 四项悬浮导航：首页、赛程、排名、设置；主队选择入口集中在设置页。
- 比分、战绩、排名及技术统计直接显示。
- 大标题首页、主队赛场横滑卡片（进行中或下一场优先、最近一场）、今日赛事紧凑列表。
- 四个 Tab 的短距离滑动与淡入淡出；详情与选队页面推入、返回退出；底栏出现和隐藏动画；标签按压回弹与颜色过渡。
- 深浅色半透明悬浮胶囊底栏，保留页面滚动状态，底部留白按底栏实际高度调整。
- 设备时区下的日期赛程、日期跳转、主队筛选。
- 常规赛东西部排名；未开赛时不编造排名。
- 比赛详情、分节比分、球队与球员统计切换、双方球队选择；球员姓名固定，表头吸顶，统计列共享横向滚动位置。
- ESPN 真实数据、应用私有缓存、错误提示、手动刷新。
- 前台对进行中或已到计划开赛时间的比赛每分钟刷新，后台停止轮询。
- 内置已核验的 30 队基础资料作为断网时的选队后备；没有内置假比分或演示赛程。
- 内置 30 队标识；首页、赛程、排名和比赛详情均使用本地资源，避免图片域名不可访问或接口缺少图片字段时丢失图标。
- 内置 622 条球员中文名，优先按 ESPN 球员 ID 匹配。已有英文比赛缓存也会显示中文名；未收录的新球员或历史球员保留原英文名。

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
- `PlayerNames.kt`、`assets/players_zh.json`：本地中文球员名表与 ID 匹配。
- `TeamLogos.kt`、`res/drawable-nodpi/`：30 队本地图标。
- `MainActivity.kt`：miuix 主题、卡片、开关、按钮、悬浮导航和自定义界面。
- `Screens.kt`：首页、赛程、排名、设置、选队与比赛详情布局。
- `ParsingTest.kt`：真实响应样本、跨时区日期和异常状态测试。

## 第三方

miuix、AndroidX、Kotlin、OkHttp 等依赖分别遵循其许可证。miuix 项目采用 Apache-2.0。球队名称、标识及赛事数据不因应用源码可见而转移权利。应用无官方合作关系。名称与图标来源见 `ASSET_SOURCES.md`。

参考资料：
- https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4
- https://github.com/compose-miuix-ui/miuix
- https://www.espn.com/nba/

## 验证记录

0.2.0 的构建与模拟器验证结果见交付目录中的《0.2.0更新说明.md》。用户手机上的网络、厂商系统行为及动画流畅度仍待安装后反馈。半透明材质当前没有背景实时模糊，也不声称复刻 Apple 系统玻璃效果。
