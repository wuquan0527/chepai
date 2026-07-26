package com.vzlpr.controller.data.model

/** 局域网中搜索到的相机设备 */
data class VzDevice(
    val ip: String,
    val mac: String = "",
    val model: String = "",
    val serial: String = "",
    val firmware: String = "",
    val httpPort: Int = 80,
    val online: Boolean = true
)

/** 一次车牌识别结果（来自相机主动推送） */
data class PlateEvent(
    val plate: String,
    val colorName: String,
    val confidence: Int,
    val deviceIp: String,
    val deviceName: String,
    val timeMillis: Long,
    val triggerType: Int,
    /** 大图 Base64（可能为空） */
    val imageBase64: String? = null,
    /** 白名单比对结果：true=放行 false=拒绝 */
    val allowed: Boolean = false
)

/** 串口透传帧（相机把 data 透传到 RS485/232，用于驱动道闸控制器/LED/语音） */
data class SerialFrame(
    val channel: Int,        // 0 或 1，对应相机的串口通道
    val base64Data: String,  // 已 Base64 编码的字节
    val len: Int             // 原始字节长度
)

/** 收到一个车牌后，服务器决定回给相机的动作（决定是否/如何开闸） */
data class PlateDecision(
    val allow: Boolean,
    val isPay: Boolean = allow,
    val ledText: String = "",
    val serialFrames: List<SerialFrame> = emptyList()
)

/** 开闸方式——覆盖臻识不同型号/接线的多种开闸途径 */
enum class GateMode(val label: String, val desc: String) {
    AUTO("自动（全部尝试）", "应答带 is_pay+串口开闸命令，并后台并发尝试 HTTP 继电器接口。兼容性最好，推荐。"),
    RESPONSE_SERIAL("推送应答·串口透传", "道闸控制器/继电器接在相机 RS485/232 口时用，应答里下发开闸十六进制命令。"),
    RESPONSE_ISPAY("推送应答·is_pay 联动", "相机据授权(is_pay=true)自行联动开闸/放行（相机侧已配置联动时）。"),
    HTTP_IO("HTTP·相机继电器", "道闸接在相机板载继电器(IO/GPIO)时用，主动发 HTTP 命令让相机闭合继电器。"),
    CAMERA_WHITELIST("下发相机名单·脱机开闸", "把白名单下发到相机名单库，由相机本地匹配后自动开其继电器（脱机可用）。")
}

/** 相机识别/网络参数（用于配置页面读写） */
data class CameraConfig(
    val ip: String,
    val user: String = "admin",
    val password: String = "admin",
    val httpPort: Int = 80,
    val rtspPort: Int = 554,
    // 以下为可配置识别参数示例（实际字段名随固件，提交时在 ConfigApi 内映射）
    val defaultProvince: String = "京",
    val minPlatePixel: Int = 60,
    val triggerMode: Int = 0  // 0=视频触发 1=IO触发 2=混合
)
