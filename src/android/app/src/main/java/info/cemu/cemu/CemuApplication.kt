package info.cemu.cemu

import android.app.ActivityManager
import android.app.Application
import android.os.Process
import info.cemu.cemu.common.android.context.internalFolder
import info.cemu.cemu.common.settings.AppSettingsStore
import info.cemu.cemu.common.settings.StorageType
import info.cemu.cemu.common.ui.localization.setLanguage
import info.cemu.cemu.common.ui.localization.setTranslations
import info.cemu.cemu.nativeinterface.NativeActiveSettings.initializeActiveSettings
import info.cemu.cemu.nativeinterface.NativeActiveSettings.setInternalDir
import info.cemu.cemu.nativeinterface.NativeActiveSettings.setNativeLibDir
import info.cemu.cemu.nativeinterface.NativeEmulation.initializeEmulation
import info.cemu.cemu.nativeinterface.NativeEmulation.setDPI
import info.cemu.cemu.nativeinterface.NativeFiles
import info.cemu.cemu.nativeinterface.NativeGraphicPacks.refreshGraphicPacks
import info.cemu.cemu.nativeinterface.NativeLogging.crashLog
import info.cemu.cemu.nativeinterface.NativeSwkbd.initializeSwkbd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern

class CemuApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        configureExceptionHandler()

        AppSettingsStore.init(this)

        NativeFiles.initialize(contentResolver)

        initializeTranslations()

        val storageSettings = runBlocking {
            AppSettingsStore.dataStore.data.map { it.storageSettings }.first()
        }

        if (isEmulationProcess() || storageSettings.storageType != StorageType.NOT_SET) {
            val folder = when (storageSettings.storageType) {
                StorageType.CUSTOM -> storageSettings.customFolderPath?.let { File(it) }
                    ?: internalFolder()

                else -> internalFolder()
            }
            initializeCemu(folder)
            saveDataFiles(folder)
        }
    }

    private fun initializeTranslations() {
        setTranslations(this)

        val language = runBlocking {
            AppSettingsStore.dataStore.data.map { it.guiSettings.language }.first()
        }

        setLanguage(language, this)
    }

    private fun saveDataFiles(folder: File) {
        val dataFolder = folder.resolve("data")

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            return
        }

        val hashFileName = "hash.txt"
        val hashFile = dataFolder.resolve(hashFileName)
        val oldHash = if (hashFile.isFile) hashFile.readText() else "invalid"

        val newHash = try {
            assets.open(hashFileName).use { it.reader().readText() }
        } catch (_: IOException) {
            return
        }

        if (oldHash == newHash) {
            return
        }

        dataFolder.deleteRecursively()
        dataFolder.mkdirs()
        dataFolder.resolve(hashFileName).writeText(newHash)

        fun traverseAssets(path: String = ""): Iterator<String> = iterator {
            val assetFiles = assets.list(path) ?: return@iterator

            if (assetFiles.isEmpty()) {
                yield(path)
            }

            for (assetFile in assetFiles) {
                val assetPath = path + (if (path == "") "" else "/") + assetFile
                for (file in traverseAssets(assetPath)) {
                    yield(file)
                }
            }
        }

        val filePatterns = arrayOf(
            Pattern.compile("gameProfiles/.*"),
            Pattern.compile("resources/.*"),
        )

        fun isFileValid(file: String): Boolean {
            return filePatterns.any { pattern -> pattern.matcher(file).matches() }
        }

        for (assetFile in traverseAssets()) {
            if (!isFileValid(assetFile)) {
                continue
            }

            val outFile = dataFolder.resolve(assetFile)
            outFile.parentFile?.mkdirs()
            assets.open(assetFile)
                .use { asset -> outFile.outputStream().use { out -> asset.copyTo(out) } }
        }
    }

    private fun configureExceptionHandler() {
        if (DefaultUncaughtExceptionHandler == null) {
            DefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        }
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, exception: Throwable ->
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            exception.printStackTrace(printWriter)
            val stacktrace = stringWriter.toString()
            crashLog(stacktrace)
            DefaultUncaughtExceptionHandler!!.uncaughtException(
                thread,
                exception
            )
        }
    }

    private fun initializeCemu(folder: File) {
        val displayMetrics = resources.displayMetrics
        setDPI(displayMetrics.density)
        initializeActiveSettings(
            userDataPath = folder.absolutePath,
            dataPath = folder.resolve("data").absolutePath,
            cachePath = folder.absolutePath,
        )
        setNativeLibDir(applicationInfo.nativeLibraryDir)
        setInternalDir(dataDir.absolutePath)
        initializeEmulation()
        initializeSwkbd()
        refreshGraphicPacks()
        cemuInitialized = true
    }

    fun initializeCemuWithFolder(folder: File) {
        if (cemuInitialized) return
        initializeCemu(folder)
        saveDataFiles(folder)
    }

    private fun isEmulationProcess(): Boolean {
        val pid = Process.myPid()
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return activityManager.runningAppProcesses?.any {
            it.pid == pid && it.processName.endsWith(":EmulationProcess")
        } ?: false
    }

    companion object {
        init {
            System.loadLibrary("CemuAndroid")
        }

        var cemuInitialized: Boolean = false
            private set

        private var DefaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    }
}
