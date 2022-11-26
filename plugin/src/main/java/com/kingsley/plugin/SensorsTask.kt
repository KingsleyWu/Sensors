package com.kingsley.plugin

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.kingsley.plugin.TheRouterGetAllClassesTask.isRouterMap
import groovy.io.FileType
import groovy.lang.Closure
import org.apache.commons.io.FileUtils
import org.codehaus.groovy.runtime.IOGroovyMethods
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.bouncycastle.asn1.iana.IANAObjectIdentifiers.directory
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.io.*

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream


abstract class SensorsTask : DefaultTask() {

    @InputFiles
    abstract fun getAllJars(): ListProperty<RegularFile>

    @InputFiles
    abstract fun getAllDirectories(): ListProperty<Directory>

    @OutputFiles
    abstract fun getOutput(): RegularFileProperty

    @TaskAction
    fun taskAction() {
        val routeMapStringSet = HashSet<String>()
        val flowTaskMap = HashMap<String, String>()
        val startFirst = System.currentTimeMillis()
        var start = System.currentTimeMillis()

        println("---------TheRouter transform start-------------------------------------------")
        var theRouterJar: File? = null
        var theRouterServiceProvideInjecter: JarEntry? = null
        val jarOutput =
            JarOutputStream(BufferedOutputStream(FileOutputStream(getOutput().get().asFile)))
        getAllJars().get().forEach { file ->
            val jarFile = JarFile(file.asFile)
            var e = jarFile.entries()
            while (e.hasMoreElements()) {
                val jarEntry = e.nextElement()
                try {
                    if (jarEntry.name.contains("TheRouterServiceProvideInjecter")) {
                        theRouterJar = file.asFile
                        theRouterServiceProvideInjecter = jarEntry
                    } else {
                        tag(jarEntry.name)
                        jarOutput.putNextEntry(JarEntry(jarEntry.name))
                        jarFile.getInputStream(jarEntry).use {
                            if (isRouterMap(jarEntry.name)) {
                                val reader = ClassReader(JarFile(file.asFile).getInputStream(JarEntry(jarEntry.name)))
                                val cn = ClassNode()
                                reader.accept(cn, 0)
                                val fieldList = cn.fields
                                for (fieldNode in fieldList) {
                                    if ("ROUTERMAP" == fieldNode.name) {
                                        println("---------TheRouter in jar get route map from: ${jarEntry.name}-------------------------------")
                                        routeMapStringSet.add(fieldNode.value.toString())
                                    }
                                }
                            } else if (isServiceProvider(jarEntry.name)) {
                                val reader = ClassReader(JarFile(file.asFile).getInputStream(JarEntry(jarEntry.name)))
                                val cn = ClassNode()
                                reader.accept(cn, 0)
                                val fieldList = cn.fields
                                        for (fieldNode in fieldList) {
                                    if ("FLOW_TASK_JSON" == fieldNode.name) {
                                        println("---------TheRouter in jar get flow task json from: ${jarEntry.name}-------------------------------")
                                        val map = Gson().fromJson(fieldNode.value.toString(), HashMap::class.java) as HashMap<String, String>
                                        flowTaskMap.putAll(map)
                                    }
                                }
                            }
                            IOGroovyMethods.leftShift(jarOutput, it)
                        }
                        jarOutput.closeEntry()
                    }
                } catch (e: Exception){
                    e.printStackTrace()
                }
                e = jarFile.entries()
            }
            jarFile.close()
        }
        getAllDirectories().get().forEach { directory ->
            val file = directory.asFile
            val relativePath = file.toURI()
                .relativize(file.toURI())
                .path
                .replace(File.separatorChar, '/')
            jarOutput.putNextEntry(JarEntry(relativePath))
            tag(relativePath)
            FileInputStream(file).use {
                if (isRouterMap(relativePath)) {
                    val reader = ClassReader(FileInputStream(file.absolutePath))
                    val cn = ClassNode()
                    reader.accept(cn, 0)
                    val fieldList = cn.fields
                    for (fieldNode in  fieldList) {
                        if ("ROUTERMAP" == fieldNode.name) {
                            println("---------TheRouter in jar get route map from: ${relativePath}-------------------------------")
                            routeMapStringSet.add(fieldNode.value.toString())
                        }
                    }
                } else if (isServiceProvider(relativePath)) {
                    val reader = ClassReader(FileInputStream(file.absolutePath))
                    val cn = ClassNode()
                    reader.accept(cn, 0)
                    val fieldList = cn.fields
                    for (fieldNode in fieldList) {
                        if ("FLOW_TASK_JSON" == fieldNode.name) {
                            println("---------TheRouter in jar get flow task json from: ${relativePath}-------------------------------")
                            val map = Gson().fromJson(fieldNode.value.toString(), HashMap::class.java) as HashMap<String, String>
                            flowTaskMap.putAll(map)
                        }
                    }
                }
                IOGroovyMethods.leftShift(jarOutput, it)
            }
            jarOutput.closeEntry()
        }

        if (theRouterJar != null && theRouterServiceProvideInjecter != null) {
            val jarFile = JarFile(theRouterJar)
            jarOutput.putNextEntry(JarEntry(theRouterServiceProvideInjecter!!.name))
            jarFile.getInputStream(theRouterServiceProvideInjecter).use {
                val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
                jarOutput.write(cw.toByteArray())
            }
            jarOutput.closeEntry()
        }

        jarOutput.close()

        var time = System.currentTimeMillis() - start
        println("---------TheRouter ASM, spend：${time}ms----------------------")
        start = System.currentTimeMillis()

        val flowTaskDependMap = HashMap<String, HashSet<String>>()
        flowTaskMap.keys.forEach {
            var value = flowTaskDependMap[it]
            if (value == null) {
                value = HashSet()
            }
            val dependsOn = flowTaskMap[it] ?: ""
            if (dependsOn.isNotBlank()) {
                dependsOn.split(",").forEach { depend ->
                    if (depend.isNotBlank()) {
                        value.add(depend.trim())
                    }
                }
            }
            flowTaskDependMap[it] = value
        }
        flowTaskDependMap.keys.forEach {

        }

//        flowTaskDependMap.keySet().each {
//            TheRouterPluginUtils.fillTodoList(flowTaskDependMap, it)
//        }


        time = System.currentTimeMillis() - start
        println("---------TheRouter check flow task map, spend:${time}ms--------------")

        time = System.currentTimeMillis() - startFirst;
        println("---------TheRouter transform finish, spend:${time}ms------------------------------------------")

    }

    fun tag(className: String) : Boolean{
        return false
    }

    fun isAutowired(className: String) : Boolean{
        return false
    }

    fun isRouterMap(className: String) : Boolean{
        return false
    }

    fun isServiceProvider(className: String) : Boolean{
        return false
    }
}
