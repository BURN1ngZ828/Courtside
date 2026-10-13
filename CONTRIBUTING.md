# 贡献说明

欢迎安装反馈、数据错误、姓名纠正与小范围改进。问题报告请写 APK 版本、手机与 Android 版本、复现步骤、预期和实际结果；数据问题附比赛双方、日期、设备时区及获取时间。截图遮挡个人信息，不提交密钥、Cookie 或本地缓存。

沿用 Kotlin／Compose／miuix 结构，不加入演示比分，不用首发推测在场状态。姓名与队标按稳定 ID 匹配，修订保留来源。

提交前运行 `./gradlew :app:assembleRelease :app:testDebugUnitTest`（Windows 使用 `gradlew.bat`）。UI 改动附截图，确认双语姓名、固定表头、横向滚动与大字体。PR 说明问题、行为变化及实际验证。

不提交本地设置、签名密钥、SDK、缓存或 APK。安装包通过 Release 发布，见 `docs/RELEASING.md`。
