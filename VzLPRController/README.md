# 车牌一体机控制器（Android 版）

把原 Windows 端的 **臻识(Vzenith)车牌识别一体机配置工具 `LPRConfigTool.exe`** 用安卓重写的 App。
原程序是闭源 exe + 一堆专有 DLL（`VzLPRSDK.dll`、`VzPlayer2.dll`、`DuiLib.dll` 等），无法直接“转换”，
本项目是**按它对外使用的网络协议在 Android(Kotlin/Compose)上重新实现**，手机/安卓一体机通过局域网连接相机即可使用。

> 适用对象：成都臻识科技的网络车牌识别相机 / 一体机（VZ 系列等，走 HTTP 推送 + RTSP 的型号）。

---

## 一、原 exe 功能 → 安卓实现 对照

| 原 Windows 工具功能 | 本项目对应模块 | 实现方式 |
|---|---|---|
| 局域网搜索相机、显示 IP/型号 | `搜索` 页 · `DeviceDiscovery.kt` | UDP 广播探测 + 本机所在网段 80 端口并发扫描 |
| 修改相机 IP / 网络参数 | `配置` 页 · `ConfigApi.setNetwork` | HTTP 提交（`main.htm?SetNetPort`） |
| 名单/白名单管理 | `白名单` 页 · Room 数据库 | 本地 SQLite 白名单，增删/启停/批量导入；命中自动放行 |
| 接收车牌识别结果 | `监控` 页 · `PushServer.kt` | App 内置 HTTP 服务器接收相机主动推送(`AlarmInfoPlate`) |
| 开闸 / IO 继电器控制 | `配置` 页 · `ConfigApi.openGate` | 命中白名单自动开闸，或手动“开闸”按钮 |
| 软触发抓拍 | `配置` 页 · `ConfigApi.forceTrigger` | HTTP 软触发 |
| 实时视频预览 | `预览` 页 · Media3 ExoPlayer | RTSP 拉流（主/子码流） |
| 恢复出厂 | `配置` 页 · `ConfigApi.restoreFactory` | `configrestore.cgi` |

## 二、协议来源

- **RTSP 地址格式**：逆自原 `VzLPRSDK.dll` / `VzStreamClient.dll` 的字符串常量，主要有
  `rtsp://用户:密码@IP:554/h264`（主码流）、`/sub_stream_1`（子码流）、`/main_stream_ex`、`/vzinfo`。
- **HTTP 主动推送(`AlarmInfoPlate`)**：臻识官方推送协议，字段最稳定、跨固件通用（相机识别到车牌后
  POST 一段 JSON 给你配置的服务器地址，含车牌号、颜色、置信度、Base64 大图等）。
- **配置类 CGI / `main.htm?...`**：逆自 `LPRConfigTool.exe` / `VzLPRSDK.dll` 字符串
  （`SetNetPort`、`SetAlarm`、`SetPlateDeviceIO`、`AddEditUsers`、`configrestore.cgi` 等）。

所有协议常量集中在 **`app/src/main/java/com/vzlpr/controller/data/net/VzProtocol.kt`**，方便按现场固件调整。

## 三、构建 & 运行

需要 **Android Studio（Koala 2024.1 或更新）**、JDK 17。

1. 用 Android Studio 打开本项目根目录（首次会自动下载 Gradle 8.7 / AGP 8.5.2 依赖，需联网）。
2. 等待 Gradle Sync 完成（Studio 会自动生成 `local.properties` 指向你的 Android SDK）。
3. 连接安卓手机/一体机（或用模拟器），点 **Run ▶**，或命令行：
   ```bash
   ./gradlew assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
   ```

技术栈：Kotlin 1.9.24 · Jetpack Compose(Material3) · Room · OkHttp(+Digest) · NanoHTTPD · Media3(RTSP)。
`minSdk 24`（Android 7.0）· `targetSdk 34`。

## 四、现场使用步骤

1. **搜索**：手机与相机接入同一 WiFi/局域网 → `搜索` 页点“开始搜索”找到相机 IP。
2. **预览**：`预览` 页填 IP、用户名(默认 admin)、密码 → 播放，验证 RTSP 通。
3. **接收推送**：`监控` 页点“启动服务”，页面会显示 `http://本机IP:8088/`；
   在**相机后台的「HTTP 推送」里把服务器地址填成这个**，相机识别到车牌就会推过来。
4. **白名单**：`白名单` 页添加或批量导入车牌；`监控` 页打开“命中白名单自动开闸”。
5. **开闸设备**：`配置` 页填相机 IP/账号 → “设为开闸设备”，之后命中白名单会自动向该相机发开闸命令。
6. **无相机自测**：`监控` 页启动服务后点“模拟推送自测”，App 会给自己发一条模拟车牌（优先用白名单里的），
   可直接验证「解析→白名单比对→记录→开闸日志→抓拍图显示」整条链路，不需要真机在场。

> `监控` 页「最近识别」会显示相机推送的**抓拍大图**（推送 JSON 里的 `imageFile` Base64，自动解码）。

## 五、开闸：全型号兼容设计（重点）

臻识开闸的“字节/接口”其实取决于**接在相机上的道闸控制器/接线方式**，与相机型号本身关系不大。
为覆盖所有型号/接线，`配置` 页提供 **5 种开闸方式**（默认 AUTO），协议核心在 `VzProtocol.Gate` + `ConfigApi`：

| 开闸方式 | 适用接线 | 机制 |
|---|---|---|
| **AUTO（默认，推荐）** | 不确定时 | 应答里带 `is_pay=true` + 串口开闸命令，并**后台并发尝试** HTTP 继电器接口，全都试 |
| 推送应答·串口透传 | 道闸控制器接相机 **RS485/232** | 应答 `serialData`（Base64）把开闸十六进制命令透传给控制器 |
| 推送应答·is_pay 联动 | 相机侧已配置授权联动 | 仅回 `is_pay=true`，相机自行联动 |
| HTTP·相机继电器 | 道闸接相机**板载继电器(IO/GPIO)** | 主动发 HTTP 命令闭合继电器，逐个尝试已知接口路径 |
| 下发相机名单·脱机开闸 | 需相机脱机自动开 | 命中后把车牌下发到相机名单库，由相机本地匹配开闸 |

- **串口开闸命令**在 `配置` 页可填十六进制（按你现场道闸控制器手册），默认 `FF 01 01 01` 仅为示例；串口通道 0/1 可选。
- **鉴权全兼容**：`ConfigApi` 用 `okhttp-digest`，自动支持 **无鉴权 / Basic / Digest** 三种，无需手动区分型号。
- **HTTP 继电器接口候选**集中在 `VzProtocol.Gate.ioCandidates`，若你的固件路径特殊，在那里加一条即可。

推送协议本身（`AlarmInfoPlate` 接收、`Response_AlarmInfoPlate` 应答、心跳 `Heartbeat`/`snapnow` 软触发）
是臻识全系通用的权威格式，已实现，一般无需改。**改 IP / 写识别参数**这类配置写入接口仍随固件不同，
若失败请对照《HTTP 协议》文档在 `ConfigApi` 调整。

## 六、目录结构

```
app/src/main/java/com/vzlpr/controller/
├─ MainActivity.kt            底部导航 + 5 个页面
├─ VzApp.kt                   Application
├─ data/
│  ├─ model/Models.kt         设备/车牌事件/相机配置 数据类
│  ├─ local/AppDatabase.kt    Room：白名单 + 通行记录
│  ├─ net/
│  │  ├─ VzProtocol.kt        ★协议常量“唯一事实来源”
│  │  ├─ DeviceDiscovery.kt   UDP + 子网扫描
│  │  ├─ ConfigApi.kt         相机 HTTP 配置/控制
│  │  ├─ PushServer.kt        内置 HTTP 服务器（收推送）
│  │  └─ PushService.kt       前台常驻服务
│  └─ repo/AppRepository.kt   仓储 + 推送处理（白名单比对/开闸）
├─ vm/ViewModels.kt           5 个 ViewModel
└─ ui/screens/                Discovery/Whitelist/Monitor/Preview/Config 页
```

## 七、已知限制

- **本项目未包含相机端的本地车牌识别算法**（那是相机硬件内 `VzLPRSDK` 做的、闭源）。本 App 走
  “相机识别→推送→手机比对白名单→开闸”的架构，不在手机上跑识别。若想手机本地识别，可另接入
  开源方案（如 HyperLPR3 Android SDK）。
- 配置类接口按最常见固件封装，个别老/新固件需按上文第五节微调。
