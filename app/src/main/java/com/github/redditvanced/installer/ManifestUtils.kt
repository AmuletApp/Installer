package com.github.redditvanced.installer

import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter
import pxb.android.axml.NodeVisitor

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val APP_NAME = "Amulet"

object ManifestUtils {
    fun editManifest(bytes: ByteArray): ByteArray {
        val reader = AxmlReader(bytes)
        val writer = AxmlWriter()

        reader.accept(object : AxmlVisitor(writer) {
            override fun child(ns: String?, name: String): NodeVisitor {
                return object : ReplaceAttrsVisitor(super.child(ns, name), mapOf()) {
                    init {
                        // FIXME: For whatever reason, android fucking refuses to  that this is in the manifest
                        // Any fucking aXML decoder will tell you its there though
                        // ????????????????????
                        // Precompiled manifest res/raw/manifest.xml will be used for now
                        super.child(null, "uses-permission").attr(
                            ANDROID_NAMESPACE,
                            "name",
                            -1,
                            TYPE_STRING,
                            "android.permission.MANAGE_EXTERNAL_STORAGE"
                        )
                    }

                    override fun child(ns: String?, name: String): NodeVisitor {
                        val nv = super.child(ns, name)
                        return when (name) {
                            "uses-sdk" -> ReplaceAttrsVisitor(nv, mapOf("targetSdkVersion" to 29))
                            "uses-permission" -> object : NodeVisitor(nv) {
                                override fun attr(
                                    ns: String?,
                                    name: String,
                                    resourceId: Int,
                                    type: Int,
                                    obj: Any?
                                ) {
//                                    if (name != "maxSdkVersion")
                                    super.attr(ns, name, resourceId, type, obj)
                                }
                            }
                            "application" -> applicationAttrsVisitor(nv)
                            else -> nv
                        }
                    }
                }
            }
        })

        return writer.toByteArray()
    }

    private fun applicationAttrsVisitor(originalVisitor: NodeVisitor) = object :
        ReplaceAttrsVisitor(
            originalVisitor,
            mapOf(
                "label" to APP_NAME,
                "extractNativeLibs" to false,
            )
        ) {

        override fun child(ns: String?, name: String): NodeVisitor {
            val nv = super.child(ns, name)
            return when (name) {
                "activity" -> ReplaceAttrsVisitor(
                    nv,
                    mapOf("label" to APP_NAME)
                )
                else -> nv
            }
        }
    }
}