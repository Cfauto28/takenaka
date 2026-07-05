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

package me.kcra.takenaka.core.mapping.resolve.impl

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.kcra.takenaka.core.Workspace
import me.kcra.takenaka.core.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * A provider of Feather's maven-metadata.xml file.
 *
 * This class is thread-safe, but presumes that only one instance will operate on a workspace at a time.
 *
 * @property workspace the workspace
 * @property xmlMapper an [ObjectMapper] that can deserialize XML trees
 * @property relaxedCache whether output cache verification constraints should be relaxed
 * @author Matouš Kučera
 */
class FeatherMetadataProvider @Deprecated(
    "Jackson will be an implementation detail in the future.",
    ReplaceWith("FeatherMetadataProvider(workspace, relaxedCache)")
) constructor(val workspace: Workspace, private val xmlMapper: ObjectMapper, val relaxedCache: Boolean = true) {
    /**
     * A map of versions and their builds.
     */
    val versions by lazy(::parseVersions)

    /**
     * The XML node of the maven-metadata file.
     */
    private val metadata by lazy(::readMetadata)

    /**
     * Creates a new metadata provider.
     *
     * @param workspace the workspace
     * @param relaxedCache whether output cache verification constraints should be relaxed
     */
    @Suppress("DEPRECATION")
    constructor(workspace: Workspace, relaxedCache: Boolean = true) : this(workspace, XML_MAPPER, relaxedCache)

    /**
     * Parses Feather version strings in the metadata file.
     *
     * @return the version metadata
     */
    private fun parseVersions(): Map<String, List<YarnBuild>> = buildMap<String, MutableList<YarnBuild>> {
        metadata["versioning"]["versions"]["version"].forEach { versionNode ->
            val buildString = versionNode.asText()
            val isNewFormat = "+build" in buildString

            val lastDotIndex = buildString.lastIndexOf('.')

            var version = buildString.substring(0, lastDotIndex)
            if (isNewFormat) version = version.removeSuffix("+build")

            val buildNumber = buildString.substring(lastDotIndex + 1, buildString.length).toInt()

            getOrPut(version, ::mutableListOf) += YarnBuild(version, buildNumber, isNewFormat)
        }
    }

    /**
     * Reads the metadata file from cache, fetching it if the cache missed.
     *
     * @return the metadata XML node
     */
    private fun readMetadata(): JsonNode {
        val file = workspace[METADATA]

        val metadataLocation = "https://maven.ornithemc.net/releases/net/ornithemc/feather-gen2/maven-metadata.xml"
        if (relaxedCache && METADATA in workspace) {
            URL("$metadataLocation.sha1").httpRequest {
                if (it.readText() == file.getChecksum(sha1Digest)) {
                    try {
                        return xmlMapper.readTree(file).apply {
                            logger.info { "read cached Feather mappings metadata" }
                        }
                    } catch (e: JacksonException) {
                        logger.warn(e) { "failed to read cached Feather mappings metadata, fetching it again" }
                    }
                } else {
                    logger.warn { "cached Feather mappings metadata is outdated or corrupt, fetching it again" }
                }
            }
        }

        URL(metadataLocation).copyTo(file)

        logger.info { "fetched Feather metadata" }
        return xmlMapper.readTree(file)
    }

    companion object {
        /**
         * The file name of the cached version metadata.
         */
        const val METADATA = "feather_metadata.xml"
    }
}

