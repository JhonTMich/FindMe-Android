package com.example.findme.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import com.example.findme.BuildConfig
import com.example.findme.data.repository.ReleaseVersion
import java.io.File
import java.security.MessageDigest

/** Certificate SHA-256 digests of an APK: the signers it uses now and, after a rotation, its lineage. */
data class SignerSet(val current: Set<String>, val lineage: Set<String>? = null)

object UpdateSignerPolicy {
    /**
     * Same signers, or a v3 key rotation whose lineage includes the installed key (Android 9+).
     * Android enforces this too; checking first avoids opening the installer for a foreign APK.
     */
    fun accepts(update: SignerSet, installed: SignerSet): Boolean {
        if (update.current.isEmpty() || installed.current.isEmpty()) return false
        if (update.current == installed.current) return true
        val lineage = update.lineage ?: return false
        return update.current.size == 1 && installed.current.size == 1 && installed.current.single() in lineage
    }
}

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
        check(UpdateSignerPolicy.accepts(signers(archive), signers(installed))) {
            "La firma de la actualización no coincide con FindMe."
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): SignerSet {
        if (Build.VERSION.SDK_INT < 28) return SignerSet(digests(info.signatures))
        val signing = info.signingInfo ?: return SignerSet(emptySet())
        if (signing.hasMultipleSigners()) return SignerSet(digests(signing.apkContentsSigners))
        return SignerSet(digests(signing.apkContentsSigners), digests(signing.signingCertificateHistory))
    }

    private fun digests(signatures: Array<Signature>?): Set<String> = signatures.orEmpty().map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    }.toSet()

    fun intent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
