package com.hongguotv.core

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TabVideoModelTest {
    private val path = "/reading/bookapi/video_tab/multi_video_model/v1/"
    private val model = """{"video_list":[{"main_url":"https://example.test/video","video_meta":{"definition":"720p","codec_type":"h264","vwidth":720,"vheight":1280},"encrypt_info":{"encrypt":false}}]}"""

    private fun repository(respond: (Request) -> Pair<Int, String>) = ContentRepository(
        OkHttpClient.Builder().addInterceptor { chain ->
            val (status, body) = respond(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(body.toResponseBody()).build()
        }.build(),
        playbackSession = PlaybackDeviceSession { DeviceRegister.Registered("333", "444") },
    )

    @Test fun currentEndpointUsesRegisteredIdentityAndAcceptsObjectOrStringModels() {
        for (stringModel in listOf(false, true)) {
            var calls = 0
            val repo = repository { request ->
                calls++
                assertEquals(path, request.url.encodedPath)
                assertEquals("GET", request.method)
                assertEquals("333", request.url.queryParameter("device_id"))
                assertEquals("444", request.url.queryParameter("iid"))
                assertEquals("123", request.url.queryParameter("video_id"))
                for ((key, value) in mapOf("video_id_type" to "0", "device_level" to "1",
                    "need_all_video_definition" to "1", "need_mp4_align" to "0",
                    "use_os_player" to "0", "video_platform" to "1024", "source" to "0", "from_video_id" to "")) {
                    assertEquals(value, request.url.queryParameter(key))
                }
                val entry = JSONObject().put("video_model", if (stringModel) model else JSONObject(model))
                200 to JSONObject().put("code", 0).put("data",
                    JSONObject().put("video_model_data", JSONObject().put("123", entry))).toString()
            }
            assertEquals("https://example.test/video", repo.streamLadder("123").renditions.single().url)
            assertEquals(1, calls)
        }
    }

    @Test fun unavailableMalformedOrWrongEpisodeFallsBackWithoutChangingIdentity() {
        for (response in listOf("", "<html>unavailable</html>", """{"code":100103}""",
            """{"code":0,"data":{"999":{"video_model":$model}}}""",
            """{"code":0,"data":{"123":{"video_model":{"video_list":[]}}}}""")) {
            val requests = mutableListOf<Request>()
            val repo = repository { request ->
                requests += request
                200 to if (request.url.encodedPath == path) response
                    else """{"data":{"123":{"video_model":$model}}}"""
            }
            assertEquals(720, repo.streamLadder("123").renditions.single().quality)
            assertEquals(listOf("GET", "POST"), requests.map { it.method })
            assertEquals(listOf("333", "333"), requests.map { it.url.queryParameter("device_id") })
        }
    }

    @Test fun authorizationAndRateLimitsDoNotTriggerLegacyFallback() {
        for (status in listOf(401, 403, 429)) {
            var calls = 0
            val repo = repository { calls++; status to "" }
            val error = assertThrows(ContentHttpException::class.java) { repo.streamLadder("123") }
            assertEquals(status, error.statusCode)
            assertEquals(1, calls)
        }
    }
}
