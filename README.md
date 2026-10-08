# Sip & Fit · 喝水与健身

个人使用的 Kotlin 安卓应用原型，最低 Android 8.0。数据保存在手机本地，不接入服务器。

## 已实现

- 喝水快捷记录（250 毫升或自定义），显示当日总量。
- 喝水提醒：默认 09:00—21:00、间隔 90 分钟、12:30—14:00 暂停；记录喝水后重新计时。
- 达到自设目标后可停止当天喝水提醒，次日恢复。
- 健身固定时间提醒，选择每周重复日期；当天完成或跳过后取消后续提醒。
- 通知快捷操作：记录、延后 15 分钟、跳过。
- 通知权限和精确定时权限入口；精确定时未授权时使用可能延迟的提醒。
- 重启、系统时间/时区改变、应用更新后恢复安排；不补发积压提醒。

提醒默认关闭，需要主动开启并保存设置。每日目标仅用于记录，不是饮水健康建议。

## 开发和运行

需要 Android Studio、JDK 17、Android SDK 35、Gradle 8.11.1。打开本目录并同步项目，连接安卓手机，运行 `app`。

```sh
./gradlew testDebugUnitTest assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

提醒规则使用纯 Kotlin 编写；单元测试覆盖喝水后重新计时、午休、夜间、目标达成、稍后提醒、跨天、健身重复日期和错过提醒等行为。

## 当前限制与验证

- 使用临时 JDK 17、Kotlin 2.1.20 和 Android SDK 35 完成全部 Kotlin 源码编译检查。
- 独立运行 JUnit 提醒规则测试：16 项全部通过。
- GitHub Actions 已通过 `testDebugUnitTest`、`assembleDebug` 和 `lintDebug`，生成 debug APK；尚未进行真机验证。
- [已通过的构建与 APK 下载](https://github.com/seven11sys/sip-and-fit/actions/runs/37722723138)：登录 GitHub 后，在 Artifacts 中下载 `sip-and-fit-debug`，解压得到 `app-debug.apk`。
- 已配置 GitHub Actions，在代码推送后运行测试、构建和 lint，通过后提供 debug APK 下载。文档提交可以使用 `[skip ci]` 跳过重复构建。
- 需要真机验证：通知允许/拒绝、精确定时允许/拒绝、锁屏省电、手机重启、时区修改、快捷操作、关闭提醒。
- Android 系统和厂商的省电策略可能延迟提醒；强行停止应用后，需再次打开应用恢复安排。
- 当前 UI 是可操作的原型。周统计、数据导入导出、运动类型/时长、桌面小组件和鸿蒙原生版本尚未实现。
- 暂不支持跨午夜的喝水提醒时段。

## 后续迁移

`ReminderRules.kt` 管理提醒规则，`ReminderScheduler.kt` 封装安卓系统接口。鸿蒙版需要单独接入系统代理提醒；本地数据导入导出将在后续补充，不能直接安装安卓 APK 作为原生鸿蒙应用。
