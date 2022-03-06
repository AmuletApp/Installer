package com.github.redditvanced.installer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import com.github.redditvanced.installer.ui.theme.InstallerTheme
import kotlin.concurrent.thread

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
                            thread(true) { install() }
                        }
                    }) {
                        Text("Install")
                    }
                }
            }
        }
    }
}

const val BASE_URL = "https://redditvanced.ddns.net/"

fun install() {
    Log.i("Installer", "Installing apk")

}

//@Preview(showBackground = true)
//@Composable
//fun DefaultPreview() {
//    InstallerTheme {
//        Greeting("Android")
//    }
//}