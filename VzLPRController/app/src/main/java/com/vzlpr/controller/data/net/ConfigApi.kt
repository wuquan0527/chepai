package com.vzlpr.controller.data.net

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.digest.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.vzlpr.controller.data.model.CameraConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials as OkHttpCredentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 相机配置/控制 HTTP 接口（本机 -> 相机）。
 *
 * 为兼容**所有型号/固件**：
 *  - 鉴权：同时支持 无鉴权 / Basic / Digest（先预置 Basic 头，遇 401 再由 Digest 应答）；
 *  - 开闸：对多种已知 IO 接口路径逐个尝试（见 VzProtocol.Gate.ioCandidates）。
 * 每套账号密码复用一个带鉴权缓存的 OkHttpClient。
 */
class ConfigApi {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val FORM = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    }

    data class Result(val ok: Boolean, val code: Int, val body: String)

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    /** 为某组账号构造/复用带 Basic+Digest 鉴权的客户端 */
    private fun clientFor(cfg: CameraConfig): OkHttpClient {
        val key = "${cfg.user}:${cfg.password}"
        return clientCache.getOrPut(key) {
            val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
            val cred = Credentials(cfg.user, cfg.password)
            val dispatcher = DispatchingAuthenticator.Builder()
                .with("digest", DigestAuthenticator(cred))
                .with("basic", BasicAuthenticator(cred))
                .build()
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .authenticator(CachingAuthenticatorDecorator(dispatcher, authCache))
                .addInterceptor(AuthenticationCacheInterceptor(authCache))
                .build()
        }
    }

    private fun baseRequest(url: String, cfg: CameraConfig): Request.Builder =
        Request.Builder().url(url)
            // 预置 Basic 头：对只认 Basic、且不下发 401 的相机更省事；Digest 相机会忽略它并走 401 应答
            .header("Authorization", OkHttpCredentials.basic(cfg.user, cfg.password))

    suspend fun get(url: String, cfg: CameraConfig): Result = withContext(Dispatchers.IO) {
        exec(baseRequest(url, cfg).get().build(), cfg)
    }

    suspend fun postJson(url: String, json: String, cfg: CameraConfig): Result =
        withContext(Dispatchers.IO) {
            exec(baseRequest(url, cfg).post(json.toRequestBody(JSON)).build(), cfg)
        }

    suspend fun postForm(url: String, form: String, cfg: CameraConfig): Result =
        withContext(Dispatchers.IO) {
            exec(baseRequest(url, cfg).post(form.toRequestBody(FORM)).build(), cfg)
        }

    private fun exec(req: Request, cfg: CameraConfig): Result =
        runCatching {
            clientFor(cfg).newCall(req).execute().use { r ->
                Result(r.isSuccessful, r.code, r.body?.string() ?: "")
            }
        }.getOrElse { Result(false, -1, it.message ?: "error") }

    // ---------------- 高层封装 ----------------

    /** 读取设备首页（判断在线/型号，稳定可用） */
    suspend fun ping(cfg: CameraConfig): Result =
        get("http://${cfg.ip}:${cfg.httpPort}/", cfg)

    /**
     * 直连相机继电器/IO 开闸：逐个尝试已知接口，返回第一个成功；全失败则返回最后一次结果。
     * 适用于道闸接在相机板载继电器(IO/GPIO)的接线方式。
     */
    suspend fun openGate(cfg: CameraConfig, ioIndex: Int = 0, holdMs: Int = 800): Result {
        var last = Result(false, -1, "no endpoint")
        for ((path, jsonBody) in VzProtocol.Gate.ioCandidates(ioIndex, holdMs)) {
            val url = VzProtocol.cgi(cfg.ip, path, cfg.httpPort)
            val r = postJson(url, jsonBody, cfg)
            if (r.ok) return r
            last = r
        }
        return last
    }

    /** 软触发一次识别（虚拟线圈软件触发）。【部分固件亦可用心跳 snapnow】 */
    suspend fun forceTrigger(cfg: CameraConfig): Result {
        val url = VzProtocol.webConfigPage(cfg.ip, VzProtocol.PAGE_SET_ALARM, cfg.httpPort)
        return postJson(url, """{"cmd":"forceTrigger"}""", cfg)
    }

    /** 写入网络参数（改 IP 等）。提交后相机通常会重启。【需按固件核对字段】 */
    suspend fun setNetwork(cfg: CameraConfig, newIp: String, mask: String, gateway: String): Result {
        val url = VzProtocol.webConfigPage(cfg.ip, VzProtocol.PAGE_SET_NET, cfg.httpPort)
        return postJson(url, """{"ip":"$newIp","mask":"$mask","gateway":"$gateway"}""", cfg)
    }

    /** 恢复出厂设置（逆自 dll：configrestore.cgi） */
    suspend fun restoreFactory(cfg: CameraConfig): Result =
        get(VzProtocol.cgi(cfg.ip, VzProtocol.CGI_CONFIG_RESTORE, cfg.httpPort), cfg)

    /** 向相机名单库下发一条白名单（脱机开闸模式用）。【需按固件核对字段】 */
    suspend fun addCameraWhitelist(cfg: CameraConfig, plate: String, expire: String = ""): Result {
        val url = VzProtocol.webConfigPage(cfg.ip, VzProtocol.PAGE_USERS, cfg.httpPort)
        return postJson(url, """{"op":"add","plate":"$plate","expire":"$expire"}""", cfg)
    }
}
