/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
 *
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kcra.takenaka.core.util

import java.io.File
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URISyntaxException
import java.net.URL
import java.net.URLConnection
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Copies the byte stream of the URL to a file.
 *
 * @param target the file
 * @param options the copy options, the default is to overwrite existing files
 */
fun URL.copyTo(target: Path, vararg options: CopyOption = arrayOf(StandardCopyOption.REPLACE_EXISTING)) =
    openStream().use { Files.copy(it, target, *options) }

/**
 * Makes an HTTP(S) request to the URL.
 *
 * @param method the HTTP method
 * @param action the connection handler
 * @return the result of the action
 */
inline fun <R> URL.httpRequest(method: String = "GET", action: (URLConnection) -> R): R {
    val conn = openConnection()

    if (conn is HttpURLConnection) {
        conn.requestMethod = method
    }
    try {
        return action(conn)
    } finally {
        if (conn is HttpURLConnection) {
            conn.disconnect()
        }
    }
}

/**
 * Fetches the URL and returns the value of the Content-Length header of the response, returning -1 if the status code is not in the 2xx range.
 */
inline val URL.contentLength: Long
    get() {
        httpRequest(method = "HEAD") {
            if (!it.ok) {
                return -1
            }
            return it.contentLengthLong
        }
    }

/**
 * Fetches the URL and returns the value of the Last-Modified header of the response, returning -1 if the status code is not in the 2xx range.
 */
inline val URL.lastModified: Long
    get() {
        httpRequest(method = "HEAD") {
            if (!it.ok) {
                return -1
            }
            return it.lastModified
        }
    }

inline val URLConnection.responseCode: Int
    get() = when {
        this is HttpURLConnection -> responseCode
        url.protocol == "file" -> if (ok) 200 else 404
        else -> 200
    }

/**
 * Returns whether the request's status code is in the 2xx range.
 */
inline val URLConnection.ok: Boolean
    get() = when {
        this is HttpURLConnection -> responseCode in 200..299
        url.protocol == "file" -> try {
            val file = File(url.toURI())
            file.isFile && file.canRead()
        } catch (e: URISyntaxException) {
            false
        }
        else -> true
    }

/**
 * Copies the byte stream of the HTTP connection to a file.
 *
 * @param target the file
 * @param options the copy options, the default is to overwrite existing files
 */
fun URLConnection.copyTo(target: Path, vararg options: CopyOption = arrayOf(StandardCopyOption.REPLACE_EXISTING)) =
    inputStream.use { Files.copy(it, target, *options) }

/**
 * Copies the byte stream of the HTTP connection to a string.
 *
 * @return the response
 */
fun URLConnection.readText(): String = inputStream.reader().use(Reader::readText)
