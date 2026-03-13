package com.example.newstart.xfyun.auth

import android.util.Base64
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacWsUrlSigner {

    fun sign(baseUrl: String, apiKey: String, apiSecret: String): String {
        val parts = parseUrl(baseUrl)
        val date = rfc1123Date()
        val requestLine = "GET ${parts.pathWithQuery} HTTP/1.1"
        val signatureOrigin = "host: ${parts.host}\ndate: $date\n$requestLine"
        val signature = hmacSha256Base64(apiSecret, signatureOrigin)
        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        val signedHttpUrl = parts.httpUrl.newBuilder()
            .addQueryParameter("authorization", authorization)
            .addQueryParameter("date", date)
            .addQueryParameter("host", parts.host)
            .build()
            .toString()
        return when {
            baseUrl.startsWith("wss://", ignoreCase = true) -> signedHttpUrl.replaceFirst("https://", "wss://")
            baseUrl.startsWith("ws://", ignoreCase = true) -> signedHttpUrl.replaceFirst("http://", "ws://")
            else -> signedHttpUrl
        }
    }
}

object HmacHttpSigner {

    fun buildSignedUrl(method: String, url: String, apiKey: String, apiSecret: String): String {
        val parts = parseUrl(url)
        val date = rfc1123Date()
        val requestLine = "${method.uppercase(Locale.US)} ${parts.pathWithQuery} HTTP/1.1"
        val signatureOrigin = "host: ${parts.host}\ndate: $date\n$requestLine"
        val signature = hmacSha256Base64(apiSecret, signatureOrigin)
        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return parts.httpUrl.newBuilder()
            .addQueryParameter("authorization", authorization)
            .addQueryParameter("date", date)
            .addQueryParameter("host", parts.host)
            .build()
            .toString()
    }

    fun buildHeaders(method: String, url: String, apiKey: String, apiSecret: String): Headers {
        val parts = parseUrl(url)
        val date = rfc1123Date()
        val requestLine = "${method.uppercase(Locale.US)} ${parts.pathWithQuery} HTTP/1.1"
        val signatureOrigin = "host: ${parts.host}\ndate: $date\n$requestLine"
        val signature = hmacSha256Base64(apiSecret, signatureOrigin)
        val authorizationOrigin =
            "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return Headers.Builder()
            .add("Host", parts.host)
            .add("Date", date)
            .add("Authorization", authorization)
            .build()
    }
}

object TsSignaSigner {

    fun signa(appId: String, apiKey: String, ts: String): String {
        val md5 = MessageDigest.getInstance("MD5")
            .digest((appId + ts).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(apiKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        val raw = mac.doFinal(md5.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }
}

private data class ParsedUrl(
    val httpUrl: okhttp3.HttpUrl,
    val host: String,
    val pathWithQuery: String
)

private fun parseUrl(rawUrl: String): ParsedUrl {
    val normalized = rawUrl
        .replaceFirst("wss://", "https://")
        .replaceFirst("ws://", "http://")
    val httpUrl = normalized.toHttpUrl()
    val defaultPort = if (httpUrl.isHttps) 443 else 80
    val host = buildString {
        append(httpUrl.host)
        if (httpUrl.port != defaultPort) {
            append(':')
            append(httpUrl.port)
        }
    }
    val pathWithQuery = buildString {
        append(httpUrl.encodedPath)
        if (httpUrl.encodedQuery != null) {
            append('?')
            append(httpUrl.encodedQuery)
        }
    }
    return ParsedUrl(httpUrl = httpUrl, host = host, pathWithQuery = pathWithQuery)
}

private fun hmacSha256Base64(secret: String, payload: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    val result = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
    return Base64.encodeToString(result, Base64.NO_WRAP)
}

private fun rfc1123Date(): String {
    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("GMT")
    return format.format(Date())
}
