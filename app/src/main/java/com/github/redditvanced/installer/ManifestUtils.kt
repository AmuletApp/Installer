package com.github.redditvanced.installer

import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter
import pxb.android.axml.NodeVisitor

object ManifestUtils {
    fun editManifest(bytes: ByteArray): ByteArray {
        val reader = AxmlReader(bytes)
        val writer = AxmlWriter()
        reader.accept(object : AxmlVisitor(writer) {
            override fun child(ns: String?, name: String) =
                object : ReplaceAttrsVisitor(super.child(ns, name), mapOf()) {

                    override fun child(ns: String?, name: String): NodeVisitor {
                        val nv = super.child(ns, name)
                        return when (name) {
                            "application" -> object :
                                ReplaceAttrsVisitor(
                                    nv,
                                    mapOf("label" to "Aliucord", "extractNativeLibs" to false)
                                ) {
                                override fun child(ns: String?, name: String): NodeVisitor {
                                    val nv = super.child(ns, name)
                                    return when (name) {
                                        "activity" -> ReplaceAttrsVisitor(
                                            nv,
                                            mapOf("label" to "Amulet")
                                        )
                                        else -> nv
                                    }
                                }
                            }
                            else -> nv
                        }
                    }
                }
        })
        return writer.toByteArray()
    }
}