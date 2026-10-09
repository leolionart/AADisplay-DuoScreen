package com.cva.duoscreen.shizuku

import android.content.pm.PackageManager
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ShizukuHelper {
    private val executor: ExecutorService = Executors.newCachedThreadPool()


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

    private fun runShellInternal(cmd: String): String {
        if (!hasPermission()) {
            return "Error: Shizuku permission not granted"
        }
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

    fun executeShell(cmd: String): String {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return runShellInternal(cmd)
        }
        return try {
            executor.submit<String> { runShellInternal(cmd) }.get(3, TimeUnit.SECONDS)
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }
    }

    fun executeShellAsync(cmd: String, callback: ((String) -> Unit)? = null) {
        executor.execute {
            val result = runShellInternal(cmd)
            callback?.invoke(result)
        }
    }

    fun launchAppOnDisplay(packageName: String, displayId: Int, callback: ((String) -> Unit)? = null) {
        executeShellAsync("am start --display $displayId --windowingMode 1 $(cmd package resolve-activity --brief $packageName | tail -n 1)", callback)
    }

    fun injectTap(displayId: Int, x: Float, y: Float) {
        executor.execute {
            runShellInternal("input -d $displayId tap ${x.toInt()} ${y.toInt()}")
        }
    }
}
