/*
 * Tencent is pleased to support the open source community by making Tencent Shadow available.
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.tencent.shadow.core.gradle

import com.tencent.shadow.core.gradle.extensions.PackagePluginExtension
import com.tencent.shadow.core.gradle.extensions.PluginApkConfig
import com.tencent.shadow.core.gradle.extensions.PluginBuildType
import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.experimental.and

open class ShadowPluginHelper {
    companion object {
        fun getFileMD5(file: File): String? {
            if (!file.isFile) {
                return null
            }

            val buffer = ByteArray(1024)
            var len: Int
            var inStream: FileInputStream? = null
            val digest = MessageDigest.getInstance("MD5")
            try {
                inStream = FileInputStream(file)
                do {
                    len = inStream.read(buffer, 0, 1024)
                    if (len != -1) {
                        digest.update(buffer, 0, len)
                    }
                } while (len != -1)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            } finally {
                inStream?.close()
            }
            return bytes2HexStr(digest.digest())
        }

        private fun bytes2HexStr(bytes: ByteArray?): String {
            val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
            if (bytes == null || bytes.isEmpty()) {
                return ""
            }

            val buf = CharArray(2 * bytes.size)
            try {
                for (i in bytes.indices) {
                    var b = bytes[i]
                    buf[2 * i + 1] = HEX_ARRAY[(b and 0xF).toInt()]
                    b = b.toInt().ushr(4).toByte()
                    buf[2 * i + 0] = HEX_ARRAY[(b and 0xF).toInt()]
                }
            } catch (e: Exception) {
                return ""
            }

            return String(buf)
        }

        fun getRuntimeApkFile(
            project: Project,
            buildType: PluginBuildType,
            isCopyPath: Boolean
        ): File {
            val packagePlugin = project.extensions.findByName("packagePlugin")
            val extension = packagePlugin as PackagePluginExtension

            val splitList = buildType.runtimeApkConfig.second.split(":")
            val runtimeFileParent =
                splitList[splitList.lastIndex].replace("assemble", "").toLowerCase()
            val runtimeApkName: String = buildType.runtimeApkConfig.first
            val parentDir = File("${extension.assetsDir}/plugin/$runtimeFileParent")
            if (!parentDir.exists())
                parentDir.mkdirs()
            return if (isCopyPath) File(
                "${parentDir}/$runtimeApkName"
            ) else
                File(
                    "${project.rootDir}" +
                            "/${extension.runtimeApkProjectPath}/build/outputs/apk/$runtimeFileParent/$runtimeApkName"
                )
        }

        fun getLoaderApkFile(
            project: Project,
            buildType: PluginBuildType,
            isCopyPath: Boolean
        ): File {
            val packagePlugin = project.extensions.findByName("packagePlugin")
            val extension = packagePlugin as PackagePluginExtension

            val loaderApkName: String = buildType.loaderApkConfig.first
            val splitList = buildType.loaderApkConfig.second.split(":")
            val loaderFileParent =
                splitList[splitList.lastIndex].replace("assemble", "").toLowerCase()
            val parentDir = File("${extension.assetsDir}/plugin/$loaderFileParent")
            if (!parentDir.exists())
                parentDir.mkdirs()
            return if (isCopyPath) File("${parentDir}/$loaderApkName")
            else File(
                "${project.rootDir}" +
                        "/${extension.loaderApkProjectPath}/build/outputs/apk/$loaderFileParent/$loaderApkName"
            )

        }

        /**
         * Android studio  会删除build 文件，所以plugin apk 也需要 拷贝到 output 文件夹下
         */
        fun copyPluginFile(
            project: Project,
            pluginConfig: PluginApkConfig,
        ) {

            val packagePlugin = project.extensions.findByName("packagePlugin")
            val extension = packagePlugin as PackagePluginExtension

            val pluginFile = File(project.rootDir, pluginConfig.apkPath)

            val split1 = pluginConfig.buildTask.split(":")
            val type = split1.last().replace("assemble","").toLowerCase()

            val parent = File("${extension.assetsDir}/plugin/${type}")

            if (!pluginFile.exists() && !parent.exists()) {
                throw IllegalArgumentException(" plugin" + pluginFile.absolutePath + " , plugin file not exist...--拷贝 plugin 文件失败 请先打包插件后操作")
            }

            if (!parent.exists())
                parent.mkdirs()
            val split = pluginConfig.apkPath.split("/")
            val apkName = split.last()
            val copyFile = File(parent,apkName)
            if (pluginFile.exists()) { //有新的 插件apk 文件更新插件 拷贝文件
                if (copyFile.exists())
                    copyFile.delete()
                copyFile.createNewFile()
                copyFileToFile(pluginFile, copyFile)
                println("plugin apk file update successful path:${copyFile}")
            }
        }

        fun getManagerFile(
            project: Project,
            buildType: PluginBuildType,
            isCopyPath:Boolean
        ) :File {

            val packagePlugin = project.extensions.findByName("packagePlugin")
            val extension = packagePlugin as PackagePluginExtension

            val managerApkName: String = buildType.managerApkConfig.first
            val splitList = buildType.managerApkConfig.second.split(":")
            val managerFileParent =
                splitList[splitList.lastIndex].replace("assemble", "").toLowerCase()
            val parentDir = File(extension.assetsDir+"/manager/${managerFileParent}")
            if (!parentDir.exists())
                parentDir.mkdirs()
            return if (isCopyPath) File(parentDir,managerApkName)
            else File(
                "${project.rootDir}" +
                        "/${extension.managerAkProjectPath}/build/outputs/apk/$managerFileParent/$managerApkName"
            )
        }

        fun getPluginFile(
            project: Project,
            pluginConfig: PluginApkConfig
        ): File {

            val packagePlugin = project.extensions.findByName("packagePlugin")
            val extension = packagePlugin as PackagePluginExtension
            val split = pluginConfig.buildTask.split(":")
            val type = split.last().replace("assemble","").toLowerCase()

            val fileName = pluginConfig.apkPath.split("/").last()
            val pluginFile =  File("${extension.assetsDir}/plugin/${type}/${fileName}")
            project.logger.info("pluginFile = $pluginFile")
            return pluginFile
        }


        fun copyFileToFile(rawFile: File, file: File): Boolean {

            val bytes = ByteArray(1024)
            var len = 0
            val outputStream = file.outputStream()
            val inputStream = rawFile.inputStream()
            return try {
                len = inputStream.read(bytes)
                while (len != -1) {
                    outputStream.write(bytes, 0, len)
                    len = inputStream.read(bytes)
                }
                inputStream.close()
                outputStream.flush()
                outputStream.close()
                true
            } catch (e: Exception) {
                false
            }

        }


    }
}