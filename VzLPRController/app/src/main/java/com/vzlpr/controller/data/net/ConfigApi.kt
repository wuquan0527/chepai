package com.vzlpr.controller.data.net

import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
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
 * 相机配置/控制 HTTP 接口（本机 -> 相机）。兼容 无鉴权 / Basic / Digest。
 */
class ConfigApi {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val FORM = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    }

    data class Result(val ok: Boolean, val code: Int, val body: String)

    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    private fun clientFor(cfg: CameraConfig): OkHttpClient {
        val key = "${cfg.user}:${cfg.password}"
        return clientCache.getOrPut(key) {
            val cred = Credentials(cfg.user, cfg.password)
            val dispatcher = DispatchingAuthenticator.Builder()
                .with("digest", DigestAuthenticator(cred))
                .with("basic", BasicAuthenticator(cred))
                .build()
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .authenticator(dispatcher)
                .build()
        }
    }

    private fun baseRequest(url: String, cfg: CameraConfig): Request.Builder =
        Request.Builder().url(url)
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

    suspend fun ping(cfg: CameraConfig): Result =
        get("http://${cfg.ip}:${cfg.httpPort}/", cfg)

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

    suspend fun forceTrigger(cfg: CameraConfig): Result {
        val url = VzProtocol.webConfigPage(cfg.ip, VzProtocol.PAGE_SET_ALARM, cfg.httpPort)
        return postJson(url, """{"cmd":"forceTrigger"}""", cfg)
    }

    suspend fun setNetwork(cfg: CameraConfig, newIp: String, mask: String, gateway: String): Result {
        val url = VzProtocol.webConfigPage(cfg.ip, VzProtocol.PAGE_SET_NET, cfg.httpPort)
        return postJson(url, """{"ip":"$newIp","mask":"$mask","gateway":"$gateway"}""", cfg)
    }

    suspend fun restoreFactory(cfg: CameraConfig): Result =
        get(VzProtocol.cgi(cfg.ip, VzProtocol.CGI_CONFIG_RESTORE, cfg.httpPort), cfg)

    suspend fun addCameraWhitelist(cfg: CameraConfig, plate: String, expire: String = ""): Result {
        val url = VzProtocol.webConfigPage(cfg.ip, VzProtocol.PAGE_USERS, cfg.httpPort)
        return postJson(url, """{"op":"add","plate":"$plate","expire":"$expire"}""", cfg)
    }
}
