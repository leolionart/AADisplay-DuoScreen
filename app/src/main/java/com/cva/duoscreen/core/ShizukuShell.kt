package com.cva.duoscreen.core

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val mainHandler = Handler(Looper.getMainLooper())

object ShizukuShell {
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

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = p.inputStream.bufferedReader()
            val out = reader.readLine() ?: ""
            p.waitFor(1, TimeUnit.SECONDS)
            p.destroy()
            out.contains("uid=0")
        } catch (e: Throwable) {
            false
        }
    }

    fun isPrivilegedAvailable(): Boolean = hasPermission() || isRootAvailable()

    private fun runRootInternal(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val outputFuture = executor.submit<String> {
                process.inputStream.bufferedReader().use { it.readText().trim() }
            }
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroy()
                outputFuture.cancel(true)
                return "Error: root command timed out"
            }
            val output = outputFuture.get(1, TimeUnit.SECONDS)
            if (process.exitValue() != 0 && output.isEmpty()) {
                "Error: root command failed with exit code ${process.exitValue()}"
            } else {
                output
            }
        } catch (e: Throwable) {
            "Error: ${e.message}"
        }
    }

    private fun runShellInternal(cmd: String): String {
        return if (hasPermission()) {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
                val outputFuture = executor.submit<String> {
                    process.inputStream.bufferedReader().use { it.readText().trim() }
                }
                if (!process.waitFor(4, TimeUnit.SECONDS)) {
                    process.destroy()
                    outputFuture.cancel(true)
                    return "Error: shell command timed out"
                }
                val output = outputFuture.get(1, TimeUnit.SECONDS)
                if (process.exitValue() != 0 && output.isEmpty()) {
                    "Error: shell command failed with exit code ${process.exitValue()}"
                } else {
                    output
                }
            } catch (e: Throwable) {
                // Fallback to root on Shizuku failure
                runRootInternal(cmd)
            }
        } else {
            // Shizuku not permitted, try root
            runRootInternal(cmd)
        }
    }

    fun executeShell(cmd: String): String {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "executeShell must not run on the main thread; use executeShellAsync"
        }
        return runShellInternal(cmd)
    }

    fun executeShellAsync(cmd: String, callback: ((String) -> Unit)? = null) {
        executor.execute {
            val result = runShellInternal(cmd)
            if (callback != null) {
                mainHandler.post { callback(result) }
            }
        }
    }

    fun launchAppOnDisplay(packageName: String, displayId: Int, callback: ((String) -> Unit)? = null) {
        if (!isSafePackageName(packageName) || displayId < 0) {
            callback?.let { mainHandler.post { it("Error: invalid package or display") } }
            return
        }
        val command = "am start --display $displayId --windowingMode 1 " +
            "$(cmd package resolve-activity --brief ${shellQuote(packageName)} | tail -n 1)"
        executeShellAsync(command, callback)
    }

    private fun isSafePackageName(packageName: String): Boolean =
        packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun injectTap(displayId: Int, x: Float, y: Float) {
        if (displayId < 0 || !x.isFinite() || !y.isFinite() || x < 0f || y < 0f) return
        executor.execute {
            runShellInternal("input -d $displayId tap ${x.toInt()} ${y.toInt()}")
        }
    }
}