package com.aure.clustertune.root

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import java.nio.charset.Charset

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class RootExec {

    private val binder: IBinder?
    var pServerAvailable: Boolean = false
        private set

    init {
        binder = runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            val rawBinder = getService.invoke(serviceManager, "PServerBinder") as IBinder
            pServerAvailable = true
            rawBinder
        }.getOrDefault(null)
    }

    /**
     * Whether privileged commands can run at all: either through the vendor PServer
     * binder service or, as a fallback, a Magisk-style `su` binary. The `su` result is
     * cached process-wide so the (potentially blocking) probe runs only once.
     */
    val isRootAvailable: Boolean
        get() = pServerAvailable || isSuAvailable()

    fun executeAsRoot(cmd: String): Result<String?> {
        return if (binder != null) {
            executeViaPServer(binder, cmd)
        } else {
            executeViaSu(cmd)
        }
    }

    private fun executeViaPServer(target: IBinder, cmd: String): Result<String?> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, "1"))
            target.transact(0, data, reply, 0)
            Result.success(decodeReply(reply))
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun executeViaSu(cmd: String): Result<String?> {
        if (!isSuAvailable()) {
            return Result.failure(IllegalStateException("Root access not available"))
        }
        return runCatching {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.trim().let { value -> if (value.isEmpty() || value == "null") null else value }
        }
    }

    private fun decodeReply(reply: Parcel): String? {
        return reply.createByteArray()
            ?.toString(Charset.defaultCharset())
            ?.trim()
            ?.let { value -> if (value == "null") null else value }
    }

    companion object {
        @Volatile
        private var cachedSuAvailable: Boolean? = null

        private fun isSuAvailable(): Boolean {
            cachedSuAvailable?.let { return it }
            return synchronized(this) {
                cachedSuAvailable ?: probeSu().also { cachedSuAvailable = it }
            }
        }

        private fun probeSu(): Boolean {
            return runCatching {
                val process = ProcessBuilder("su", "-c", "id -u")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                process.waitFor()
                output.lineSequence().lastOrNull()?.trim() == "0"
            }.getOrDefault(false)
        }
    }
}
