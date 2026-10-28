// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import com.hongguotv.core.ContentRepository
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26])
class PlaybackRegistrationCompatibilityTest {
    @Test fun emptyModelUsesRegisteredDeviceOnBothSupportedAndroidGenerations() {
        var registrations = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when {
                request.url.encodedPath == "/service/2/device_register/" -> {
                    registrations++
                    """{"device_id_str":"7654321","install_id_str":"1234567"}"""
                }
                request.url.queryParameter("device_id") != "7654321" -> ""
                else -> """{"data":{"123":{"video_model":{"video_list":[{"main_url":"https://example.com/video","video_meta":{"definition":"720p","codec_type":"h264","vwidth":720,"vheight":1280},"encrypt_info":{"encrypt":false}}]}}}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(body.toResponseBody()).build()
        }.build()
        val repository = ContentRepository(http)
        assertEquals("720P", repository.stream("123").quality)
        assertEquals("720P", repository.stream("123").quality)
        assertEquals(1, registrations)
    }
}
