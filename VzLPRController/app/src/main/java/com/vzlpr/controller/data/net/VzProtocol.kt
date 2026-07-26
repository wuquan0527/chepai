package com.vzlpr.controller.data.net

/**
 * 臻识(Vzenith)车牌识别一体机网络协议常量与工具。
 *
 * 本文件是整个 App 对接相机协议的「唯一事实来源」。所有端口、URL 路径、
 * JSON 字段名都集中在这里，方便按现场相机固件版本做微调。
 *
 * 说明来源：
 *  - RTSP 地址格式：从原 Windows 配置工具 VzLPRSDK.dll / VzStreamClient.dll 中逆出的字符串常量；
 *  - HTTP 主动推送(AlarmInfoPlate)协议：臻识官方推送协议文档，字段最稳定、跨固件通用；
 *  - CGI / main.htm 配置接口：从 LPRConfigTool.exe / VzLPRSDK.dll 逆出的字符串；
 *    不同固件差异较大，已标注「固件相关」，请按现场设备实际情况调整。
 */
object VzProtocol {

    // ---------- 默认端口 ----------
    /** 相机内置 Web/配置服务端口（HTTP） */
    const val DEFAULT_HTTP_PORT = 80
    /** RTSP 视频流端口 */
    const val DEFAULT_RTSP_PORT = 554
    /** 局域网设备搜索 UDP 广播端口（臻识 SDK 常用；不同固件可能不同，可在设置中修改） */
    const val DISCOVERY_UDP_PORT = 3600
    /** App 内置 HTTP 服务器监听端口——用于接收相机主动推送的车牌结果 */
    const val DEFAULT_PUSH_LISTEN_PORT = 8088

    // ---------- 默认账号 ----------
    const val DEFAULT_USER = "admin"
    const val DEFAULT_PASSWORD = "admin"

    // ---------- RTSP 码流地址（逆自 DLL 常量） ----------
    /**
     * 主码流。带鉴权格式：rtsp://user:pass@ip:554/h264
     * 逆出的其它可用路径：main_stream_ex / sub_stream_1 / sub_stream_ex / vzinfo
     */
    fun rtspMain(ip: String, user: String = DEFAULT_USER, pwd: String = DEFAULT_PASSWORD,
                 port: Int = DEFAULT_RTSP_PORT): String =
        "rtsp://$user:$pwd@$ip:$port/h264"

    /** 子码流（更低分辨率、更省带宽，适合手机预览） */
    fun rtspSub(ip: String, user: String = DEFAULT_USER, pwd: String = DEFAULT_PASSWORD,
                port: Int = DEFAULT_RTSP_PORT): String =
        "rtsp://$user:$pwd@$ip:$port/sub_stream_1"

    /** 主码流(扩展)——部分固件用 main_stream_ex */
    fun rtspMainEx(ip: String, user: String = DEFAULT_USER, pwd: String = DEFAULT_PASSWORD,
                   port: Int = DEFAULT_RTSP_PORT): String =
        "rtsp://$user:$pwd@$ip:$port/main_stream_ex"

    /** 抓拍单帧 JPEG（部分固件支持；否则用 RTSP 关键帧） */
    fun snapshotUrl(ip: String, port: Int = DEFAULT_HTTP_PORT): String =
        "http://$ip:$port/cgi-bin/snapshot.cgi"

    // ---------- 配置类 CGI / 页面（固件相关，逆自 exe/dll 字符串） ----------
    // LPRConfigTool.exe 中出现的配置页面（Web UI）：
    //   main.htm?SetNetPort        网络参数
    //   main.htm?SetAlarm          识别/报警参数
    //   main.htm?SetPlateDeviceIO  IO/开闸设置
    //   main.htm?AddEditUsers      名单(白名单)管理
    //   main.htm?StorgeDeviceMana  存储管理
    // VzLPRSDK.dll 中出现的 CGI：
    //   /axistalk.cgi   /cgi-bin/update.cgi   /configrestore.cgi
    fun webConfigPage(ip: String, page: String, port: Int = DEFAULT_HTTP_PORT): String =
        "http://$ip:$port/main.htm?$page"

    fun cgi(ip: String, path: String, port: Int = DEFAULT_HTTP_PORT): String =
        "http://$ip:$port/$path"

    const val CGI_CONFIG_RESTORE = "configrestore.cgi"   // 恢复出厂
    const val CGI_UPDATE = "cgi-bin/update.cgi"          // 固件升级
    const val PAGE_SET_NET = "SetNetPort"
    const val PAGE_SET_ALARM = "SetAlarm"
    const val PAGE_SET_IO = "SetPlateDeviceIO"
    const val PAGE_USERS = "AddEditUsers"

    // ---------- HTTP 主动推送(相机 -> 本机)协议字段 ----------
    // 相机识别到车牌后，会向配置的服务器地址 POST 一段 JSON。
    // 顶层键随固件不同可能是 AlarmInfoPlate 或 Response_AlarmInfoPlate。
    object Push {
        const val KEY_ALARM = "AlarmInfoPlate"
        const val KEY_HEARTBEAT = "heartbeat"          // 心跳包顶层键
        const val KEY_RESULT = "result"
        const val KEY_PLATE_RESULT = "PlateResult"
        const val KEY_LICENSE = "license"              // 车牌号
        const val KEY_COLOR_TYPE = "colorType"         // 车牌颜色(字符串,部分固件)
        const val KEY_COLOR_VALUE = "colorValue"       // 车牌颜色(数值,0蓝1黄2白3黑4绿…)
        const val KEY_CONFIDENCE = "confidence"        // 置信度
        const val KEY_IMAGE_FILE = "imageFile"         // 大图 Base64
        const val KEY_IMAGE_FRAGMENT = "imageFragmentFile" // 车牌小图 Base64
        const val KEY_TIMESTAMP = "timeStamp"
        const val KEY_TRIGGER_TYPE = "triggerType"     // 触发方式
        const val KEY_DEVICE_NAME = "deviceName"
        const val KEY_IP = "ipaddr"

        /**
         * 车牌应答（权威格式）。相机会重传本次结果，直到收到带 info:"ok" 的合法应答。
         *  - is_pay:"true"  授权/放行标志（部分固件据此联动开闸）
         *  - content        LED 显示文本；也可为 "retransfer_stop" 停止重传
         *  - serialData[]   串口透传：把 data(Base64) 从相机 serialChannel 口发出去，
         *                   用于驱动接在相机 RS485/232 上的道闸控制器/LED/语音。
         */
        fun plateResponse(decision: com.vzlpr.controller.data.model.PlateDecision): String {
            val inner = org.json.JSONObject()
            inner.put("info", "ok")
            inner.put("is_pay", if (decision.isPay) "true" else "false")
            inner.put("content", decision.ledText)
            if (decision.serialFrames.isNotEmpty()) {
                val arr = org.json.JSONArray()
                decision.serialFrames.forEach { f ->
                    arr.put(
                        org.json.JSONObject()
                            .put("serialChannel", f.channel)
                            .put("data", f.base64Data)
                            .put("dataLen", f.len)
                    )
                }
                inner.put("serialData", arr)
            }
            return org.json.JSONObject().put("Response_AlarmInfoPlate", inner).toString()
        }

        /** 简单确认应答（不开闸） */
        fun ackResponse(): String =
            """{"Response_AlarmInfoPlate":{"info":"ok"}}"""

        /** 心跳应答；snapNow=true 可让相机立即抓拍一次（软触发） */
        fun heartbeatResponse(snapNow: Boolean = false): String {
            val inner = org.json.JSONObject().put("info", "ok").put("shutoff", "ok")
            if (snapNow) inner.put("snapnow", "yes")
            return org.json.JSONObject().put("Response_Heartbeat", inner).toString()
        }
    }

    /**
     * 开闸命令与直连 IO 接口候选。
     *
     * 说明：真正的“开闸字节”取决于接在相机上的**道闸控制器/继电器品牌**，与相机型号无关。
     * 这里给出一个常见的通用示例命令，现场可在设置里改成你控制器的实际命令（十六进制）。
     * 若道闸接在相机**板载继电器(IO/GPIO)**上，则用下面的 HTTP IO 候选接口触发。
     */
    object Gate {
        /** 通用开闸命令示例（十六进制）。很多简单继电器板用一字节脉冲；请按现场控制器手册修改。 */
        const val DEFAULT_OPEN_HEX = "FF 01 01 01"
        const val DEFAULT_SERIAL_CHANNEL = 0

        /** 把 "FF 01 A0" 之类的十六进制字符串解析为字节数组（忽略空格/逗号，容错） */
        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            val n = clean.length / 2
            val out = ByteArray(n)
            for (i in 0 until n) {
                out[i] = ((hexDigit(clean[i * 2]) shl 4) or hexDigit(clean[i * 2 + 1])).toByte()
            }
            return out
        }

        private fun hexDigit(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> 0
        }

        /**
         * 直连相机继电器/IO 的候选 HTTP 接口（不同固件路径不同，AUTO 模式会逐个尝试）。
         * 每项为 (相对路径, JSON请求体)；{IDX}/{MS} 由调用方替换为 IO 序号/保持时长。
         */
        fun ioCandidates(ioIndex: Int, holdMs: Int): List<Pair<String, String>> = listOf(
            "main.htm?SetPlateDeviceIO" to """{"cmd":"ioControl","io":$ioIndex,"level":1,"hold":$holdMs}""",
            "cgi-bin/io.cgi" to """{"channel":$ioIndex,"action":"open","hold":$holdMs}""",
            "API/IOControl" to """{"IOOutput":{"channel":$ioIndex,"value":1,"holdMs":$holdMs}}""",
            "cgi-bin/gpio.cgi" to """{"gpio":$ioIndex,"value":1,"hold":$holdMs}"""
        )
    }

    // ---------- 车牌颜色枚举 ----------
    fun plateColorName(value: Int): String = when (value) {
        0 -> "蓝"
        1 -> "黄"
        2 -> "白"
        3 -> "黑"
        4 -> "绿"
        5 -> "黄绿"
        else -> "未知"
    }
}
