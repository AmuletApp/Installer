package com.github.redditvanced.installer

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import com.android.apksig.ApkSigner
import com.aurora.gplayapi.exceptions.ApiException
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.github.diamondminer88.zip.ZipReader
import com.github.diamondminer88.zip.ZipWriter
import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.github.redditvanced.installer.ui.theme.InstallerTheme
import org.bouncycastle.util.encoders.UrlBase64
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {
    var installing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                Surface(color = MaterialTheme.colors.background) {
                    Button(onClick = {
                        if (!installing) {
                            Toast(this).apply {
                                setText("Do not exit until apk install prompt is shown!")
                                show()
                            }
                            installing = true
                            thread(true) { install(this) }
                        }
                    }) {
                        Text("Install")
                    }
                }
            }
        }
    }
}

const val BASE_URL = "https://redditvanced.ddns.net"
val supportedABIs = listOf("arm64-v8a", "armeabi-v7a", "x86-64")

data class AccountCredentials(
    val username: String,
    val password: String
)

val baseDir = File(Environment.getExternalStorageDirectory(), "RedditVanced")
val buildDir = File(baseDir, "build")

fun install(activity: Activity) {
    Log.i("Installer", "Device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
    Build.SUPPORTED_ABIS.first { it in supportedABIs }
        ?: throw Error("Unsupported ABI!")

    val zipalignBinary = File(activity.applicationInfo.nativeLibraryDir, "libzipalign.so")

    // TODO: check if already installed but keystore gone
    val keystoreFile = File(baseDir, "keystore.ks")
    if (!keystoreFile.exists()) {
        Log.i("Installer", "No keystore exists, generating new keystore")
        KeystoreUtils.newKeystore(keystoreFile)
    }

    val credentials = "$BASE_URL/google"
        .httpGet()
        .set("User-Agent", "RedditVanced")
        .responseObject<AccountCredentials>().third.get()

    Log.i("Installer", "Logging into Google")
    val strProperties = """
        UserReadableName = Google Pixel 2 (api${Build.VERSION.SDK_INT})
        Build.HARDWARE = walleye
        Build.RADIO = g8998-00223-1804251450
        Build.BOOTLOADER = mw8998-002.0071.00
        Build.FINGERPRINT = google/walleye/walleye:9/PPP3.180510.008/4811556:user/release-keys
        Build.BRAND = google
        Build.DEVICE = walleye
        Build.VERSION.SDK_INT = ${Build.VERSION.SDK_INT}
        Build.MODEL = Pixel 2
        Build.MANUFACTURER = Google
        Build.PRODUCT = walleye
        Build.ID = PPP3.180510.008
        Build.VERSION.RELEASE = 9
        TouchScreen = 3
        Keyboard = 1
        Navigation = 1
        ScreenLayout = 2
        HasHardKeyboard = false
        HasFiveWayNavigation = false
        GL.Version = 196610
        Screen.Density = 420
        Screen.Width = 1080
        Screen.Height = 1794
        Platforms = ${Build.SUPPORTED_ABIS.joinToString(",")}
        SharedLibraries = android.ext.services,android.ext.shared,android.test.base,android.test.mock,android.test.runner,com.android.future.usb.accessory,com.android.ims.rcsmanager,com.android.location.provider,com.android.media.remotedisplay,com.android.mediadrm.signer,com.google.android.camera.experimental2017,com.google.android.dialer.support,com.google.android.gms,com.google.android.hardwareinfo,com.google.android.lowpowermonitordevicefactory,com.google.android.lowpowermonitordeviceinterface,com.google.android.maps,com.google.android.media.effects,com.google.android.poweranomalydatafactory,com.google.android.poweranomalydatamodeminterface,com.google.vr.platform,com.qti.vzw.ims.internal,com.qualcomm.embmslibrary,com.qualcomm.qcrilhook,com.qualcomm.qti.QtiTelephonyServicelibrary,com.quicinc.cne,com.quicinc.cneapiclient,com.verizon.embms,com.verizon.provider,com.vzw.apnlib,javax.obex,org.apache.http.legacy
        Features = android.hardware.audio.low_latency,android.hardware.audio.output,android.hardware.audio.pro,android.hardware.bluetooth,android.hardware.bluetooth_le,android.hardware.camera,android.hardware.camera.any,android.hardware.camera.ar,android.hardware.camera.autofocus,android.hardware.camera.capability.manual_post_processing,android.hardware.camera.capability.manual_sensor,android.hardware.camera.capability.raw,android.hardware.camera.flash,android.hardware.camera.front,android.hardware.camera.level.full,android.hardware.faketouch,android.hardware.fingerprint,android.hardware.location,android.hardware.location.gps,android.hardware.location.network,android.hardware.microphone,android.hardware.nfc,android.hardware.nfc.any,android.hardware.nfc.hce,android.hardware.nfc.hcef,android.hardware.opengles.aep,android.hardware.ram.normal,android.hardware.screen.landscape,android.hardware.screen.portrait,android.hardware.sensor.accelerometer,android.hardware.sensor.assist,android.hardware.sensor.barometer,android.hardware.sensor.compass,android.hardware.sensor.gyroscope,android.hardware.sensor.hifi_sensors,android.hardware.sensor.light,android.hardware.sensor.proximity,android.hardware.sensor.stepcounter,android.hardware.sensor.stepdetector,android.hardware.telephony,android.hardware.telephony.carrierlock,android.hardware.telephony.cdma,android.hardware.telephony.euicc,android.hardware.telephony.gsm,android.hardware.touchscreen,android.hardware.touchscreen.multitouch,android.hardware.touchscreen.multitouch.distinct,android.hardware.touchscreen.multitouch.jazzhand,android.hardware.usb.accessory,android.hardware.usb.host,android.hardware.vr.headtracking,android.hardware.vr.high_performance,android.hardware.vulkan.compute,android.hardware.vulkan.level,android.hardware.vulkan.version,android.hardware.wifi,android.hardware.wifi.aware,android.hardware.wifi.direct,android.hardware.wifi.passpoint,android.hardware.wifi.rtt,android.software.activities_on_secondary_displays,android.software.app_widgets,android.software.autofill,android.software.backup,android.software.cant_save_state,android.software.companion_device_setup,android.software.connectionservice,android.software.cts,android.software.device_admin,android.software.device_id_attestation,android.software.file_based_encryption,android.software.home_screen,android.software.input_methods,android.software.live_wallpaper,android.software.managed_users,android.software.midi,android.software.picture_in_picture,android.software.print,android.software.securely_removes_users,android.software.sip,android.software.sip.voip,android.software.verified_boot,android.software.voice_recognizers,android.software.vr.mode,android.software.webview,com.google.android.apps.dialer.SUPPORTED,com.google.android.apps.photos.PIXEL_2017_PRELOAD,com.google.android.feature.EXCHANGE_6_2,com.google.android.feature.GOOGLE_BUILD,com.google.android.feature.GOOGLE_EXPERIENCE,com.google.android.feature.PIXEL_2017_EXPERIENCE,com.google.android.feature.PIXEL_EXPERIENCE,com.google.android.feature.TURBO_PRELOAD,com.google.android.feature.ZERO_TOUCH,com.google.hardware.camera.easel,com.verizon.hardware.telephony.ehrpd,com.verizon.hardware.telephony.lte
        Locales = af,af_ZA,am,am_ET,ar,ar_EG,ar_XB,as,ast,az,be,bg,bg_BG,bn,bs,ca,ca_ES,cs,cs_CZ,da,da_DK,de,de_DE,el,el_GR,en,en_AU,en_CA,en_GB,en_IN,en_US,en_XA,es,es_ES,es_US,et,eu,fa,fa_IR,fi,fi_FI,fil,fil_PH,fr,fr_BE,fr_CA,fr_FR,gl,gu,hi,hi_IN,hr,hr_HR,hu,hu_HU,hy,in,in_ID,is,it,it_IT,iw,iw_IL,ja,ja_JP,ka,kk,km,kn,ko,ko_KR,ky,lo,lt,lt_LT,lv,lv_LV,mk,ml,mn,mr,ms,ms_MY,my,nb,nb_NO,ne,nl,nl_NL,or,pa,pl,pl_PL,pt,pt_BR,pt_PT,ro,ro_RO,ru,ru_RU,si,sk,sk_SK,sl,sl_SI,sq,sr,sr_Latn,sr_RS,sv,sv_SE,sw,sw_TZ,ta,te,th,th_TH,tr,tr_TR,uk,uk_UA,ur,uz,vi,vi_VN,zh,zh_CN,zh_HK,zh_TW,zu,zu_ZA
        GSF.version = 12848063
        Vending.version = 81031200
        Vending.versionString = 10.3.12-all [0] [PR] 198814133
        CellOperator = 310260
        SimOperator = 310260
        TimeZone = America/New_York
        GL.Extensions = GL_AMD_compressed_ATC_texture,GL_AMD_performance_monitor,GL_ANDROID_extension_pack_es31a,GL_APPLE_texture_2D_limited_npot,GL_ARB_vertex_buffer_object,GL_ARM_shader_framebuffer_fetch_depth_stencil,GL_EXT_EGL_image_array,GL_EXT_YUV_target,GL_EXT_blit_framebuffer_params,GL_EXT_buffer_storage,GL_EXT_clip_cull_distance,GL_EXT_color_buffer_float,GL_EXT_color_buffer_half_float,GL_EXT_copy_image,GL_EXT_debug_label,GL_EXT_debug_marker,GL_EXT_discard_framebuffer,GL_EXT_disjoint_timer_query,GL_EXT_draw_buffers_indexed,GL_EXT_external_buffer,GL_EXT_geometry_shader,GL_EXT_gpu_shader5,GL_EXT_memory_object,GL_EXT_memory_object_fd,GL_EXT_multisampled_render_to_texture,GL_EXT_multisampled_render_to_texture2,GL_EXT_primitive_bounding_box,GL_EXT_protected_textures,GL_EXT_robustness,GL_EXT_sRGB,GL_EXT_sRGB_write_control,GL_EXT_shader_framebuffer_fetch,GL_EXT_shader_io_blocks,GL_EXT_shader_non_constant_global_initializers,GL_EXT_tessellation_shader,GL_EXT_texture_border_clamp,GL_EXT_texture_buffer,GL_EXT_texture_cube_map_array,GL_EXT_texture_filter_anisotropic,GL_EXT_texture_format_BGRA8888,GL_EXT_texture_norm16,GL_EXT_texture_sRGB_R8,GL_EXT_texture_sRGB_decode,GL_EXT_texture_type_2_10_10_10_REV,GL_KHR_blend_equation_advanced,GL_KHR_blend_equation_advanced_coherent,GL_KHR_debug,GL_KHR_no_error,GL_KHR_robust_buffer_access_behavior,GL_KHR_texture_compression_astc_hdr,GL_KHR_texture_compression_astc_ldr,GL_NV_shader_noperspective_interpolation,GL_OES_EGL_image,GL_OES_EGL_image_external,GL_OES_EGL_image_external_essl3,GL_OES_EGL_sync,GL_OES_blend_equation_separate,GL_OES_blend_func_separate,GL_OES_blend_subtract,GL_OES_compressed_ETC1_RGB8_texture,GL_OES_compressed_paletted_texture,GL_OES_depth24,GL_OES_depth_texture,GL_OES_depth_texture_cube_map,GL_OES_draw_texture,GL_OES_element_index_uint,GL_OES_framebuffer_object,GL_OES_get_program_binary,GL_OES_matrix_palette,GL_OES_packed_depth_stencil,GL_OES_point_size_array,GL_OES_point_sprite,GL_OES_read_format,GL_OES_rgb8_rgba8,GL_OES_sample_shading,GL_OES_sample_variables,GL_OES_shader_image_atomic,GL_OES_shader_multisample_interpolation,GL_OES_standard_derivatives,GL_OES_stencil_wrap,GL_OES_surfaceless_context,GL_OES_texture_3D,GL_OES_texture_compression_astc,GL_OES_texture_cube_map,GL_OES_texture_env_crossbar,GL_OES_texture_float,GL_OES_texture_float_linear,GL_OES_texture_half_float,GL_OES_texture_half_float_linear,GL_OES_texture_mirrored_repeat,GL_OES_texture_npot,GL_OES_texture_stencil8,GL_OES_texture_storage_multisample_2d_array,GL_OES_vertex_array_object,GL_OES_vertex_half_float,GL_OVR_multiview,GL_OVR_multiview2,GL_OVR_multiview_multisampled_render_to_texture,GL_QCOM_alpha_test,GL_QCOM_extended_get,GL_QCOM_shader_framebuffer_fetch_noncoherent,GL_QCOM_texture_foveated,GL_QCOM_tiled_rendering
        Roaming = mobile-notroaming
        Client = android-google
    """.trimIndent()

    val authData = AuthHelper.build(
        credentials.username,
        "aas_et/AKppINb6RBgs3AYCK2W17ZHSZB1TNFJs1t52TTXymkNRgjJsuyRRY13j8nAyh59qx0HsZHj4nOg3xxnFlhOZ4kwEkxCEqcJx_tI-svvBRCxVletmDw6FJNOuE7Z4Iri-trp0iZq49WDU07NKgAFQ8ejD82GpccK2unNPWc_2GMtvuSwl7WkaE9xMMuM6KYX6AHHl3b9U4SObBlcYzquPbRU=",
        Properties().apply { load(strProperties.reader()) }
    )
    val purchaseHelper = PurchaseHelper(authData)

    // TODO: fetch version
    Log.i("Installer", "Retrieving APK details from Google Play")
    val buyResponse = purchaseHelper.getBuyResponse("com.reddit.frontpage", 405543, 1)
    val deliveryData = purchaseHelper.getDeliveryResponse(
        "com.reddit.frontpage",
        updateVersionCode = 405543,
        offerType = 1,
        downloadToken = buyResponse.encodedDeliveryToken
    )

    val apiApk = when (deliveryData.status) {
        1 -> deliveryData.appDeliveryData
        2 -> throw ApiException.AppNotSupported()
        3 -> throw ApiException.AppNotPurchased()
        else -> throw ApiException.Unknown()
    }

    Log.i("Installer", "Downloading APKs")
    val mainApkFile = File(buildDir, "com.reddit.frontpage.apk")
    val time = measureTimeMillis {
        val downloadMainApk = {
            Log.i("Installer", "Main APK is outdated or not cached, downloading...")
            URL(apiApk.downloadUrl).openStream().use {
                mainApkFile.writeBytes(it.readBytes())
            }
        }

        if (!mainApkFile.exists())
            downloadMainApk()
        else {
            val digest = MessageDigest.getInstance("SHA-1")
            val sha1 = digest.digest(mainApkFile.readBytes())

            // For whatever reason the . is missing
            val apiSha1 = UrlBase64.decode(apiApk.sha1 + '.')

            if (!MessageDigest.isEqual(apiSha1, sha1))
                downloadMainApk()
        }

        for (split in apiApk.splitDeliveryDataList) {
            val apk = File(buildDir, "${split.name}.apk")
            if (apk.exists()) {
                val digest = MessageDigest.getInstance("SHA-1")
                val sha1 = digest.digest(apk.readBytes())

                // For whatever reason the . is missing
                val apiSha1 = UrlBase64.decode(split.sha1 + '.')

                if (MessageDigest.isEqual(apiSha1, sha1))
                    continue
            }
            Log.i("Installer", "Split ${apk.name} is outdated or not cached, downloading...")

            URL(split.downloadUrl).openStream().use {
                apk.writeBytes(it.readBytes())
            }
        }
    }
    Log.i("Installer", "Downloaded APKs in ${time}ms")

    Log.i("Installer", "Patching main apk")
    val mainReader = ZipReader(mainApkFile)

    var dexCount = 0
    mainReader.entryNames.forEach {
        if (it.startsWith("classes"))
            dexCount++
    }

    val originalManifest = mainReader.openEntry("AndroidManifest.xml")!!.read()
    val originalClassesDex = mainReader.openEntry("classes.dex")!!.read()
    mainReader.close()

    val mainWriter = ZipWriter(mainApkFile, true)
    mainWriter.deleteEntries("AndroidManifest.xml", "classes.dex")
    mainWriter.writeEntry("classes${dexCount + 1}.dex", originalClassesDex)

    // Copy injector zip contents into apk
    ZipReader(getInjector(activity)).use { injector ->
        injector.forEach {
            if (!it.isDir)
                mainWriter.writeEntryUncompressed(it.name, it.read())
        }
    }

    // Change AndroidManifest targetSdkVersion to 29 (Credit to Juby)
    val newManifest = ManifestUtils.editManifest(originalManifest)
    mainWriter.writeEntry("AndroidManifest.xml", newManifest)
    mainWriter.close()

    Log.i("Installer", "zipalign-ing apk")
    val mainAligned = File(buildDir, "main.apk.aligned")
    ProcessBuilder()
        .command(
            zipalignBinary.absolutePath,
            "-p",
            "-f",
            "4",
            mainApkFile.absolutePath,
            mainAligned.absolutePath,
        )
        .start().waitFor()
    mainAligned.renameTo(mainApkFile)

    Log.i("Installer", "Signing apks")
    val apkFiles = requireNotNull(buildDir.listFiles { f -> f.extension == "apk" })
    val keySet = KeystoreUtils.loadKeyStore(keystoreFile)
    val signingConfig = ApkSigner.SignerConfig.Builder(
        "RedditVanced Signer",
        keySet.privateKey,
        listOf(keySet.publicKey)
    ).build()

    apkFiles.forEach { apk ->
        val signedFile = File(buildDir, "${apk.name}.signed")
        ApkSigner.Builder(listOf(signingConfig))
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .setOtherSignersSignaturesPreserved(false)
            .setInputApk(apk)
            .setOutputApk(signedFile)
            .build()
            .sign()
        signedFile.renameTo(apk)
    }

    Log.i("Installer", "Installing apks")
    val packageInstaller = activity.packageManager.packageInstaller

    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)

    val sessionId = packageInstaller.createSession(params)
    val session = packageInstaller.openSession(sessionId)
    apkFiles.forEach { apk ->
        session.openWrite(apk.name, 0, apk.length()).use {
            it.write(apk.readBytes())
            session.fsync(it)
        }
    }

    val callbackIntent = Intent(activity.applicationContext, APKInstallCallbackService::class.java)
    val contentIntent = PendingIntent.getService(activity, 0, callbackIntent, 0)
    session.commit(contentIntent.intentSender)
    session.close()

    Log.i("Installer", "Triggered install")
}

fun getInjector(activity: Activity): File {
    val customInjector = File(buildDir, "injector.zip")
    if (customInjector.exists()) return customInjector

    val cachedInjector = File(activity.codeCacheDir, "injector.zip")

    val (_, _, versionResult) = "$BASE_URL/maven/releases/com/github/redditvanced/Injector/maven-metadata.xml"
        .httpGet().responseString()

    val version = when (versionResult) {
        is Result.Failure -> {
            Log.e("Installer", "Failed to get maven-metadata!")
            throw versionResult.getException()
        }
        is Result.Success -> {
            "<release>(.+?)</release>"
                .toRegex()
                .find(versionResult.get())
                ?.groupValues
                ?.get(0)
                ?: throw Error("Failed to find version in maven-metadata!")
        }
    }
    Log.i("Installer", "Fetched injector version: $version")

    val zipData =
        "$BASE_URL/maven/releases/com/github/redditvanced/Injector/$version/Injector-$version.zip"
            .httpGet().body.toByteArray()
    cachedInjector.writeBytes(zipData)

    Log.i("Installer", "Downloaded injector")

    return cachedInjector
}
