package com.litegem.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "InferenceEngine"

/**
 * Dual-backend inference router.
 *
 * .task model  →  MediaPipe LlmInference  →  NPU / GPU delegate (LiteRT)
 * .gguf model  →  llama.cpp via JNI       →  Vulkan GPU
 *
 * Exposes a single [generate] suspend function so the API layer
 * doesn't need to know which backend fired.
 */
class InferenceEngine(private val context: Context) {

    enum class Backend { MEDIAPIPE_NPU, LLAMACPP_GPU, NONE }

    private var mediaPipeEngine: LlmInference? = null
    private var currentBackend = Backend.NONE
    private var currentModelPath = ""

    /**
     * Load a model. Automatically picks backend by file extension.
     * @param modelPath absolute path to .task or .gguf file
     */
    suspend fun loadModel(modelPath: String): Result<Backend> = withContext(Dispatchers.IO) {
        try {
            unload()
            val file = File(modelPath)
            if (!file.exists()) return@withContext Result.failure(
                IllegalArgumentException("Model not found: $modelPath")
            )

            currentBackend = when {
                modelPath.endsWith(".task") -> loadMediaPipe(modelPath)
                modelPath.endsWith(".gguf") -> loadLlamaCpp(modelPath)
                else -> return@withContext Result.failure(
                    IllegalArgumentException("Unsupported format. Use .task or .gguf")
                )
            }
            currentModelPath = modelPath
            Log.i(TAG, "Loaded $modelPath via $currentBackend")
            Result.success(currentBackend)
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generate a response. Streams tokens via [onToken] callback.
     * @param prompt the full prompt string
     * @param onToken called for each token as it is generated
     * @return the complete response string
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        onToken: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (currentBackend) {
                Backend.MEDIAPIPE_NPU -> generateMediaPipe(prompt, onToken)
                Backend.LLAMACPP_GPU  -> generateLlamaCpp(prompt, maxTokens, onToken)
                Backend.NONE -> Result.failure(IllegalStateException("No model loaded"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // MediaPipe / LiteRT backend (.task → NPU)
    // -------------------------------------------------------------------------

    private fun loadMediaPipe(modelPath: String): Backend {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            // GPU delegate — LiteRT routes to NPU automatically on Tensor G4
            .setPreferredBackend(LlmInference.Backend.GPU)
            .setMaxTokens(2048)
            .build()
        mediaPipeEngine = LlmInference.createFromOptions(context, options)
        return Backend.MEDIAPIPE_NPU
    }

    private fun generateMediaPipe(
        prompt: String,
        onToken: ((String) -> Unit)?
    ): Result<String> {
        val engine = mediaPipeEngine
            ?: return Result.failure(IllegalStateException("MediaPipe engine not loaded"))

        val sb = StringBuilder()
        engine.generateResponseAsync(prompt) { partialResult, done ->
            partialResult?.let {
                sb.append(it)
                onToken?.invoke(it)
            }
        }
        // generateResponseAsync is callback-based; for now return full result
        // TODO: convert to proper coroutine Flow in next iteration
        val result = engine.generateResponse(prompt)
        return Result.success(result)
    }

    // -------------------------------------------------------------------------
    // llama.cpp backend (.gguf → Vulkan GPU)
    // -------------------------------------------------------------------------

    private fun loadLlamaCpp(modelPath: String): Backend {
        // llama.cpp JNI bridge — native lib loaded from jniLibs/
        // Stub until we drop in the prebuilt .so in the next phase
        Log.i(TAG, "llama.cpp backend selected for $modelPath")
        LlamaCppBridge.loadModel(modelPath)
        return Backend.LLAMACPP_GPU
    }

    private fun generateLlamaCpp(
        prompt: String,
        maxTokens: Int,
        onToken: ((String) -> Unit)?
    ): Result<String> {
        val result = LlamaCppBridge.generate(prompt, maxTokens, onToken)
        return Result.success(result)
    }

    // -------------------------------------------------------------------------

    fun unload() {
        mediaPipeEngine?.close()
        mediaPipeEngine = null
        LlamaCppBridge.unload()
        currentBackend = Backend.NONE
        currentModelPath = ""
    }

    fun status(): Map<String, String> = mapOf(
        "backend" to currentBackend.name,
        "model" to currentModelPath.substringAfterLast("/").ifEmpty { "none" }
    )
}
