package com.github.redditvanced.installer

import android.app.Activity
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import com.aliucord.libzip.Zip
import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.redditvanced.installer.ui.theme.InstallerTheme
import com.github.theapache64.gpa.api.Play
import kotlinx.coroutines.runBlocking
import java.io.File
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

data class AccountCredentials(
    val username: String,
    val password: String
)

val baseDir = File(Environment.getExternalStorageDirectory(), "RedditVanced")
val buildDir = File(baseDir, "build")

fun install(activity: Activity) {
    val keystore = File(baseDir, "keystore.ks")
    if (!keystore.exists()) {
        Log.i("Installer", "No keystore exists, generating new keystore")
        Signer.newKeystore(keystore)
    }

    val credentials = "$BASE_URL/google"
        .httpGet()
        .set("User-Agent", "RedditVanced")
        .responseObject<AccountCredentials>().third.get()

    Log.i("Installer", "Logging into Google")
    val account = runBlocking {
        Play.login(credentials.username, credentials.password)
    }
    val play = Play.getApi(account)

    Log.i("Installer", "Retrieving APK details from Google Play")
    val apiApk = runBlocking {
        // TODO: fetch version
        play.delivery("com.reddit.frontpage", 405543, 1)
            ?: throw Error("Failed to retrieve APK details (null)")
    }

    Log.i("Installer", "Downloading APKs")
    val mainApkFile = File(buildDir, "com.reddit.frontpage.apk")
    val time = measureTimeMillis {
        apiApk.openApp().use {
            mainApkFile.writeBytes(it.readBytes())
        }

        for (split in 0 until apiApk.splitCount) {
            val name = apiApk.getSplitId(split)
            apiApk.openSplitDelivery(split).use {
                File("$name.apk").writeBytes(it.readBytes())
            }
        }
    }
    Log.i("Installer", "Downloaded APKs in ${time}ms")

    Log.i("Installer", "Patching main apk")
    val mainApkZip = Zip(mainApkFile.absolutePath, 6, 'a')

    var dexCount = 0
    val entryDexRegex = "classes(\\d).dex".toRegex()
    for (i in 0 until mainApkZip.totalEntries) {
        val index = entryDexRegex.find(mainApkZip.entryName)
            ?.groups
            ?.get(1)!!.value
            .toIntOrNull()
            ?: continue
        if (dexCount < index) dexCount = index
    }

    // read original classes.dex
    mainApkZip.openEntry("classes.dex")
    val originalClassesDex = mainApkZip.readEntry()
    mainApkZip.closeEntry()

    // write original classes.dex to classesN+1.dex, N being the dex count
    mainApkZip.openEntry("classes${dexCount + 1}.dex")
    mainApkZip.writeEntry(originalClassesDex, originalClassesDex.size.toLong())
    mainApkZip.closeEntry()

    // copy injector zip contents into apk
    val injectorZip = Zip(getInjector(activity).absolutePath, 6, 'r')
    for (i in 0 until injectorZip.totalEntries) {
        injectorZip.openEntryByIndex(i)
        val bytes = injectorZip.readEntry()

        mainApkZip.openEntry(injectorZip.entryName)
        mainApkZip.writeEntry(bytes, bytes.size.toLong())
        mainApkZip.closeEntry()

        injectorZip.closeEntry()
    }

    injectorZip.close()
    mainApkZip.close()

    Log.i("Installer", "Signing apks")
    val apkFiles = requireNotNull(buildDir.listFiles { f -> f.extension == "apk" })
    apkFiles.forEach {
        Signer.signApk(it, keystore)
    }

    Log.i("Installer", "Installing apks")
    val packageInstaller = activity.packageManager.packageInstaller

    val sessionParams =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)

    val sessionId = packageInstaller.createSession(sessionParams)
    val session = packageInstaller.openSession(sessionId)
    apkFiles.forEach { apk ->
        session.openWrite(apk.name, 0, apk.length()).use {
            it.write(apk.readBytes())
            session.fsync(it)
        }
    }

    // send to hell
    val contentIntent = PendingIntent.getActivity(activity, 0, null, 0)
    session.commit(contentIntent.intentSender)
    session.close()
}

fun getInjector(activity: Activity): File {
    val customInjector = File(buildDir, "injector.zip")
    if (customInjector.exists()) return customInjector

    val cachedInjector = File(activity.codeCacheDir, "injector.zip")

    val versionBody = "$BASE_URL/maven/releases/com/github/redditvanced/Injector/maven-metadata.xml"
        .httpGet().body.asString("text/xml")

    val version = "<release>(.+?)</release>"
        .toRegex()
        .find(versionBody)
        ?.groupValues
        ?.get(1)
        ?: throw Error("Failed to find version in maven-metadata!")

    Log.i("Installer", "Fetched injector version: $version")

    val zipData =
        "$BASE_URL/maven/releases/com/github/redditvanced/Injector/$version/Injector-$version.zip"
            .httpGet().body.toByteArray()
    cachedInjector.writeBytes(zipData)

    Log.i("Installer", "Downloaded injector")

    return cachedInjector
}
