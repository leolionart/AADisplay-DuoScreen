package com.cva.duoscreen.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            if (isShizukuAvailable()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun executeShell(cmd: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }
    }

    fun launchAppOnDisplay(packageName: String, displayId: Int) {
        executeShell("am start --display $displayId $(cmd package resolve-activity --brief $packageName | tail -n 1)")
    }

    fun injectTap(displayId: Int, x: Float, y: Float) {
        executeShell("input -d $displayId tap ${x.toInt()} ${y.toInt()}")
    }
}
