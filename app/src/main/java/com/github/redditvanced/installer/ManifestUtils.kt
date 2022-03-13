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
                object : NodeVisitor(super.child(ns, name)) {
                    override fun child(ns: String?, name: String): NodeVisitor {
                        val visitor = super.child(ns, name)
                        return if (name != "uses-sdk") visitor
                        else object : NodeVisitor(visitor) {
                            override fun attr(
                                ns: String?,
                                name: String,
                                resourceId: Int,
                                type: Int,
                                value: Any
                            ) {
                                var obj = value
                                if ("targetSdkVersion" == name)
                                    obj = 29
                                super.attr(ns, name, resourceId, type, obj)
                            }
                        }
                    }
                }
        })
        return writer.toByteArray()
    }
}