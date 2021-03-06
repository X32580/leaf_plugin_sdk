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

package com.tencent.shadow.core.transform_kit

import com.android.build.api.transform.TransformInvocation
import javassist.ClassPool
import javassist.CtClass
import org.gradle.api.Project
import java.io.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis

abstract class AbstractTransform(
    project: Project,
    classPoolBuilder: ClassPoolBuilder
) : JavassistTransform(project, classPoolBuilder) {

    protected abstract val mTransformManager: AbstractTransformManager
    private val mOverrideCheck = OverrideCheck()
    private lateinit var mDebugClassJar: File
    private lateinit var mDebugClassJarZOS: ZipOutputStream


    private fun cleanDebugClassFileDir() {
        val transformTempDir = File(project.buildDir, "transform-temp")
        transformTempDir.deleteRecursively()
        transformTempDir.mkdirs()
        mDebugClassJar = File.createTempFile("transform-temp", ".jar", transformTempDir)
        mDebugClassJarZOS = ZipOutputStream(FileOutputStream(mDebugClassJar))
    }

    override fun beforeTransform(invocation: TransformInvocation) {
        super.beforeTransform(invocation)
        ReplaceClassName.resetErrorCount()
        cleanDebugClassFileDir()
    }

    override fun onTransform() {
        //Fixme: ?????????OverrideCheck.prepare??????mCtClassInputMap????????????
        //??????????????????????????????????????????????????????ApplicationInfoTest???????????????Activity???????????????superclass???
//        mOverrideCheck.prepare(mCtClassInputMap.keys.toSet())

        mTransformManager.setupAll()
        mTransformManager.fireAll()
    }

    override fun afterTransform(invocation: TransformInvocation) {
        super.afterTransform(invocation)

        mDebugClassJarZOS.flush()
        mDebugClassJarZOS.close()

        //CtClass???????????????????????????????????????????????????superClass??????????????????
        //??????????????????????????????ClassPool????????????????????????????????????????????????????????????
        val debugClassPool = classPoolBuilder.build()
        debugClassPool.appendClassPath(mDebugClassJar.absolutePath)
        val inputClassNames = mCtClassInputMap.keys.map { it.name }
        onCheckTransformedClasses(debugClassPool, inputClassNames)
    }

    override fun onOutputClass(entryName: String?, className: String, outputStream: OutputStream) {
        classPool[className].debugWriteJar(entryName, mDebugClassJarZOS)
        super.onOutputClass(entryName, className, outputStream)
    }

    private fun CtClass.debugWriteJar(outputEntryName: String?, outputStream: ZipOutputStream) {
        //??????META-INF
        if (outputEntryName != null
            && listOf<(String) -> Boolean>(
                { it.startsWith("META-INF/") },
                { it == "module-info.class" },
            ).any { it(outputEntryName) }
        ) return

        try {
            val entryName = outputEntryName ?: (name.replace('.', '/') + ".class")
            outputStream.putNextEntry(ZipEntry(entryName))
            val p = stopPruning(true)
            toBytecode(DataOutputStream(outputStream))
            defrost()
            stopPruning(p)
        } catch (e: Exception) {
            outputStream.close()
            throw RuntimeException(e)
        }
    }

    open fun onCheckTransformedClasses(debugClassPool: ClassPool, classNames: List<String>) {
        var delayException: Exception? = null
        val start1 = System.currentTimeMillis()
        try {
            checkReplacedClassHaveRightMethods(debugClassPool, classNames)
        } catch (e: Exception) {
            if (delayException == null) {
                delayException = e
            } else {
                delayException.addSuppressed(e)
            }
        }
        project.logger.info("checkReplacedClassHaveRightMethods???????????????(ms):${System.currentTimeMillis() - start1}")

        val start2 = System.currentTimeMillis()
        try {
            val t2 = measureTimeMillis {
                //                checkOverrideMethods(debugClassPool, classNames)
            }
            System.err.println("t2:$t2")
        } catch (e: Exception) {
            if (delayException == null) {
                delayException = e
            } else {
                delayException.addSuppressed(e)
            }
        }
        project.logger.info("checkOverrideMethods???????????????(ms):${System.currentTimeMillis() - start2}")

        if (delayException != null) {
            throw delayException
        }
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????
     */
    private fun checkReplacedClassHaveRightMethods(
        debugClassPool: ClassPool,
        classNames: List<String>
    ) {
        val result = ReplaceClassName.checkAll(debugClassPool, classNames)
        if (result.isNotEmpty()) {
            val tempFile = File.createTempFile(
                "shadow_replace_class_have_right_methods",
                ".txt",
                project.buildDir
            )
            val bw = BufferedWriter(FileWriter(tempFile))

            result.forEach {
                val defClass = it.key
                bw.appendln("Class ${defClass}???????????????:")
                val methodMap = it.value
                methodMap.forEach {
                    val methodString = it.key
                    val useClass = it.value

                    bw.appendln("${methodString}?????????????????????:")
                    useClass.forEach {
                        bw.appendln(it)
                    }
                }
                bw.newLine()
            }
            bw.flush()
            bw.close()
            throw IllegalStateException("?????????????????????????????????????????????????????????${tempFile.absolutePath}")
        }
    }

    private fun checkOverrideMethods(debugClassPool: ClassPool, classNames: List<String>) {
        val result = mOverrideCheck.check(debugClassPool, classNames)
        if (result.isNotEmpty()) {
            val tempFile = File.createTempFile("shadow_override_check", ".txt", project.buildDir)
            val bw = BufferedWriter(FileWriter(tempFile))
            result.forEach {
                bw.appendln("In Class ${it.key} ??????????????????Override?????????:")
                it.value.map { "${it.first.name}:${it.first.signature}(??????????????????${it.second})" }
                    .forEach {
                        bw.appendln(it)
                    }
                bw.newLine()
            }
            bw.flush()
            bw.close()
            throw IllegalStateException("??????Override?????????????????????Override??????????????????${tempFile.absolutePath}")
        }
    }

}
