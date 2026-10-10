package com.cva.duoscreen.car

import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import java.security.MessageDigest

class DuoCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        val builder = HostValidator.Builder(this)
        listOf("com.google.android.projection.gearhead", "com.google.android.gms").forEach { packageName ->
            try {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val certificates = if (info.signingInfo.hasMultipleSigners()) {
                    info.signingInfo.apkContentsSigners
                } else {
                    info.signingInfo.signingCertificateHistory
                }
                certificates.forEach { certificate ->
                    val digest = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
                        .joinToString(":") { byte -> "%02x".format(byte) }
                    builder.addAllowedHost(packageName, digest)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Missing host package is not implicitly trusted.
            }
        }
        return builder.build()
    }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = DuoMainScreen(carContext)
    }
}
