package com.friedbrain.clipvlip

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class LiteRtModelRuntime : Closeable {
    private var engine: Engine? = null

    suspend fun load(modelFile: File) = withContext(Dispatchers.IO) {
        require(modelFile.exists() && modelFile.isFile) { "The selected model is not installed." }
        close()
        val nextEngine = Engine(EngineConfig(modelPath = modelFile.absolutePath))
        try {
            nextEngine.initialize()
            engine = nextEngine
        } catch (initializationError: Throwable) {
            if (nextEngine.isInitialized()) {
                try {
                    nextEngine.close()
                } catch (cleanupError: Throwable) {
                    initializationError.addSuppressed(cleanupError)
                }
            }
            throw initializationError
        }
    }

    suspend fun generate(prompt: String, onText: (String) -> Unit) = withContext(Dispatchers.IO) {
        val activeEngine = engine ?: error("Load a local model before generating text.")
        activeEngine.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt).collect { message ->
                onText(message.toString())
            }
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
