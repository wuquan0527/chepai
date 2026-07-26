package com.vzlpr.controller.data.net

import com.vzlpr.controller.data.model.PlateDecision
import com.vzlpr.controller.data.model.PlateEvent
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject

/** 车牌推送处理回调（在 NanoHTTPD 工作线程上被同步调用） */
interface PlatePushHandler {
    /** 返回本次应答动作（是否放行/是否带串口开闸命令等） */
    fun onPlate(event: PlateEvent): PlateDecision
    /** 返回 true 表示让相机在本次心跳后立即抓拍一次（软触发） */
    fun onHeartbeat(): Boolean = false
    /** 服务器状态变化（用于 UI 提示） */
    fun onLog(message: String) {}
}

/**
 * 内置 HTTP 服务器：接收臻识相机主动推送的车牌识别结果。
 *
 * 在相机后台「HTTP 推送」里，把服务器地址填成本手机的局域网 IP + 本服务端口
 * （默认 8088），相机识别到车牌就会 POST 一段 AlarmInfoPlate JSON 过来。
 */
class PushServer(
    port: Int,
    private val handler: PlatePushHandler
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.POST) {
                return NanoHTTPD.newFixedLengthResponse(
                    Response.Status.OK, "text/plain",
                    "VzLPR push server is running"
                )
            }
            val body = readBody(session)
            handler.onLog("收到推送 ${body.length} 字节")

            val root = JSONObject(if (body.isBlank()) "{}" else body)

            // 心跳（相机发 "Heartbeat"，个别固件小写）
            if (root.has("Heartbeat") || root.has(VzProtocol.Push.KEY_HEARTBEAT)) {
                val snap = handler.onHeartbeat()
                return json(VzProtocol.Push.heartbeatResponse(snap))
            }

            val event = parsePlate(root, session.remoteIpAddress ?: "")
            if (event == null) {
                return json(VzProtocol.Push.ackResponse())
            }
            val decision = handler.onPlate(event)
            json(VzProtocol.Push.plateResponse(decision))
        } catch (e: Exception) {
            handler.onLog("解析异常: ${e.message}")
            json("""{"Response_AlarmInfoPlate":{"info":"error"}}""")
        }
    }

    private fun json(s: String): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", s)

    /** 读取 POST 原始体：NanoHTTPD 对非表单类型会把原文放进 files["postData"] */
    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            files["postData"] ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** 从推送 JSON 中提取车牌信息，兼容嵌套(result.PlateResult)与扁平两种结构。 */
    private fun parsePlate(root: JSONObject, remoteIp: String): PlateEvent? {
        val alarm = root.optJSONObject(VzProtocol.Push.KEY_ALARM)
            ?: root.optJSONObject("Response_AlarmInfoPlate")
            ?: root

        // 车牌可能在 alarm.result.PlateResult 或直接在 alarm 上
        val plateObj = alarm.optJSONObject(VzProtocol.Push.KEY_RESULT)
            ?.optJSONObject(VzProtocol.Push.KEY_PLATE_RESULT)
            ?: alarm.optJSONObject(VzProtocol.Push.KEY_PLATE_RESULT)
            ?: alarm

        val license = plateObj.optString(VzProtocol.Push.KEY_LICENSE, "").trim()
        if (license.isEmpty() || license == "null") return null

        val colorValue = plateObj.optInt(VzProtocol.Push.KEY_COLOR_VALUE, -1)
        val colorName = if (colorValue >= 0) VzProtocol.plateColorName(colorValue)
        else plateObj.optString(VzProtocol.Push.KEY_COLOR_TYPE, "未知")

        val confidence = plateObj.optInt(VzProtocol.Push.KEY_CONFIDENCE, 0)
        val trigger = plateObj.optInt(VzProtocol.Push.KEY_TRIGGER_TYPE, 0)
        val image = alarm.optString(VzProtocol.Push.KEY_IMAGE_FILE, "")
            .ifEmpty { plateObj.optString(VzProtocol.Push.KEY_IMAGE_FILE, "") }
        val deviceName = alarm.optString(VzProtocol.Push.KEY_DEVICE_NAME, "")
        val ip = alarm.optString(VzProtocol.Push.KEY_IP, "").ifEmpty { remoteIp }

        return PlateEvent(
            plate = license,
            colorName = colorName,
            confidence = confidence,
            deviceIp = ip,
            deviceName = deviceName,
            timeMillis = System.currentTimeMillis(),
            triggerType = trigger,
            imageBase64 = image.ifEmpty { null }
        )
    }
}
