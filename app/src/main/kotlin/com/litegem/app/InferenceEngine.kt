package com.litegem.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "InferenceEngine"

class InferenceEngine(private val context: Context) {

    enum class Backend { MEDIAPIPE_GPU, LLAMACPP_GPU, NONE }

    private var mediaPipeEngine: LlmInference? = null
    private var currentBackend = Backend.NONE
    private var currentModelPath = ""

    suspend fun loadModel(modelPath: String): Result<Backend> = withContext(Dispatchers.IO) {
        try {
            unload()
            val file = File(modelPath)
            if (!file.exists()) return@withContext Result.failure(
                IllegalArgumentException("Model not found: $modelPath")
            )

            currentBackend = when {
                modelPath.endsWith(".bin") || modelPath.endsWith(".task") -> loadMediaPipe(modelPath)
                modelPath.endsWith(".gguf") -> loadLlamaCpp(modelPath)
                else -> return@withContext Result.failure(
                    IllegalArgumentException("Unsupported format")
                )
            }

            currentModelPath = modelPath
            Result.success(currentBackend)
        } catch (e: Exception) {
            Log.e(TAG, "Load failed", e)
            Result.failure(e)
        }
    }

    private fun loadMediaPipe(modelPath: String): Backend {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setTopK(40)
            .setTemperature(0.7f)
            .setResultListener { result, done ->
                // Async responses handled by UI elsewhere
            }
            .build()
        
        mediaPipeEngine = LlmInference.createFromOptions(context, options)
        return Backend.MEDIAPIPE_GPU
    }

    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (currentBackend) {
                Backend.MEDIAPIPE_GPU -> {
                    mediaPipeEngine?.generateResponseAsync(prompt)
                    Result.success("Processing...")
                }
                Backend.LLAMACPP_GPU -> Result.success("LlamaCpp not implemented yet")
                else -> Result.failure(IllegalStateException("No model loaded"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadLlamaCpp(modelPath: String): Backend {
        return Backend.LLAMACPP_GPU
    }

    fun unload() {
        mediaPipeEngine?.close()
        mediaPipeEngine = null
        currentBackend = Backend.NONE
        currentModelPath = ""
    }
}
