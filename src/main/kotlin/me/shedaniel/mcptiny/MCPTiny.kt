/*
 * Copyright (c) 2019, 2020 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("MCPTiny")

package me.shedaniel.mcptiny

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.TinyExporter
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MCPNamespace.loadMCPFromURLZip
import me.shedaniel.linkie.namespaces.MCPNamespace.loadTsrgFromURLZip
import me.shedaniel.linkie.namespaces.YarnNamespace
//import me.shedaniel.linkie.rewireIntermediaryFrom
import me.shedaniel.linkie.utils.div
import me.shedaniel.linkie.utils.info
import me.shedaniel.linkie.utils.tryToVersion
import me.shedaniel.linkie.utils.warn
import java.io.File
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import com.soywiz.korio.file.std.localVfs
import me.shedaniel.linkie.MappingsBuilder
import me.shedaniel.linkie.MappingsSource
import java.io.FileOutputStream

import me.shedaniel.linkie.Class
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.getMethodByObf
import me.shedaniel.linkie.getObfMergedDesc
import me.shedaniel.linkie.getFieldByObfName
import me.shedaniel.linkie.rearrangeClassMap

fun main(args: Array<String>) = runBlocking {
    require(args.size == 2) { "You must include two arguments: <minecraft version> <mcp snapshot>! " }
    val version = args.first().tryToVersion() ?: throw IllegalArgumentException("${args.first()} is not a valid version!")
    val mcpVersion = args[1]
    //.takeIf { arg -> arg.all { it.isDigit() } } ?: throw IllegalArgumentException("${args[1]} is not a valid mcp version! (It should be in a form of date, example: 20200916)")

    """
        ==================================================
        Please DO NOT redistribute the mappings converted.
        It may be a violation of MCP's license!
        ==================================================
    """.trimIndent().lineSequence().forEach { warn(it) }

    info("Loading in namespaces...")

    Namespaces.init(LinkieConfig.DEFAULT.copy(
        cacheDirectory = (localVfs(System.getProperty("user.dir")) / "linkie-cache").jail(),
        namespaces = listOf(MCPNamespace, YarnNamespace)
    ))
    runBlocking { delay(2000) }
    runBlocking { while (MCPNamespace.reloading || YarnNamespace.reloading) delay(100) }
    require(YarnNamespace.getAllVersions().contains(version.toString())) { "${args.first()} is not a valid version!" }

    val mcp = MappingsContainer(version.toString(), name = "MCP")
    val mcpBuilder = MappingsBuilder(true, true, mcp).apply {
        loadTsrgFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$version/mcp_config-$version.zip"))
        if (mcpVersion.endsWith("-mixed") || mcpVersion.endsWith("-yarn")) {
            loadMCPFromURLZip(URL("https://maven.tterrag.com/de/oceanlabs/mcp/mcp_snapshot/$mcpVersion-$version/mcp_snapshot-$mcpVersion-$version.zip"))
        } else {
            loadMCPFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/$mcpVersion-$version/mcp_snapshot-$mcpVersion-$version.zip"))
        }
        source(MappingsSource.MCP_TSRG)
    }
    mcpBuilder.build()
    val yarn = YarnNamespace.getProvider(version.toString()).get()

    mcp.classes.values.forEach {
        // Use SRG instead of Yarn Intermediary for fields and methods that aren't mapped.
        it.fields.forEach { field ->
            if (field.mappedName == null) {
                field.mappedName = field.intermediaryName
            }
        }
        it.methods.forEach { method ->
            if (method.mappedName == null) {
                method.mappedName = method.intermediaryName
            }
        }
    }
    mcp.rewireIntermediaryFrom(yarn, true)

    info("Outputting to output.jar (overriding if exists!)")
    val path = File(File(System.getProperty("user.dir")), "output.jar")
    if (path.exists()) path.delete()
    ZipOutputStream(path.outputStream()).use { zipOutputStream ->
        val zipEntry = ZipEntry("mappings/mappings.tiny")
        zipOutputStream.putNextEntry(zipEntry)

        val bytes = TinyExporter.export(mcp, "intermediary", "named").readBytes()
        zipOutputStream.write(bytes, 0, bytes.size)
        zipOutputStream.closeEntry()
    }
    info("Done!")
}

// Copied from https://github.com/linkie/linkie-core/blob/master/src/main/kotlin/me/shedaniel/linkie/Mappings.kt
fun MappingsContainer.rewireIntermediaryFrom(
    obf2intermediary: MappingsContainer,
    removeUnfound: Boolean = false,
    mapClassNames: Boolean = true,
) {
    val classO2I = mutableMapOf<String, Class>()
    obf2intermediary.classes.forEach { (_, clazz) -> clazz.obfMergedName?.also { classO2I[it] = clazz } }
    classes.values.removeIf { clazz ->
        val replacement = classO2I[clazz.obfName.merged]
        if (replacement != null) {
            if (mapClassNames) {
                clazz.mappedName = clazz.intermediaryName
            } else {
                clazz.mappedName = null
            }
            clazz.intermediaryName = replacement.intermediaryName

            clazz.methods.removeIf { method ->
                val replacementMethod = replacement.getMethodByObf(obf2intermediary, method.obfMergedName!!, method.getObfMergedDesc(this))
                if (replacementMethod != null) {
//                    method.mappedName = method.intermediaryName
                    method.intermediaryName = replacementMethod.intermediaryName
                    method.intermediaryDesc = replacementMethod.intermediaryDesc
                }
                replacementMethod == null && removeUnfound
            }
            clazz.fields.removeIf { field ->
                val replacementField = replacement.getFieldByObfName(field.obfMergedName!!)
                if (replacementField != null) {
//                    field.mappedName = field.intermediaryName
                    field.intermediaryName = replacementField.intermediaryName
                    field.intermediaryDesc = replacementField.intermediaryDesc
                }
                replacementField == null && removeUnfound
            }
        }
        replacement == null && removeUnfound
    }

    rearrangeClassMap()
}
