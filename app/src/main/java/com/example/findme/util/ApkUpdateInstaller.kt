package com.example.findme.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.example.findme.BuildConfig
import com.example.findme.data.repository.ReleaseVersion
import java.io.File
import java.security.MessageDigest

object ApkUpdateInstaller {
    @Suppress("DEPRECATION")
    fun validate(context: Context, file: File, expectedVersion: String, installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong()) {
        check(file.isFile) { "No se encontró la actualización descargada." }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("El archivo descargado no es una actualización válida.")
        check(archive.packageName == context.packageName) { "El archivo descargado no corresponde a FindMe." }
        val versionCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        check(versionCode > installedVersionCode && ReleaseVersion.equivalent(archive.versionName.orEmpty(), expectedVersion)) {
            "La versión descargada no corresponde a esta actualización."
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveSigners = signers(archive)
        check(archiveSigners.isNotEmpty() && archiveSigners == signers(installed)) {
            "La firma de la actualización no coincide con FindMe."
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun intent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
