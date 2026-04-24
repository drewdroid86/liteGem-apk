package com.litegem.app

import android.util.Log

private const val TAG = "LlamaCppBridge"

/**
 * JNI bridge to llama.cpp prebuilt .so
 * Phase 1: stub implementation — returns placeholder responses
 * Phase 2: drop in arm64-v8a/libllama.so compiled with Vulkan
 */
object LlamaCppBridge {

    private var modelLoaded = false
    private var modelPath = ""

    init {
        try {
            System.loadLibrary("llama")
            Log.i(TAG, "libllama.so loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libllama.so not found — using stub mode")
        }
    }

    fun loadModel(path: String) {
        modelPath = path
        modelLoaded = true
        Log.i(TAG, "Model registered: $path")
        // Native: nLoadModel(path)
    }

    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        onToken: ((String) -> Unit)? = null
    ): String {
        if (!modelLoaded) return "[Error: no model loaded]"
        // Native: return nGenerate(prompt, maxTokens, onToken)
        // Stub for Phase 1 — replace with JNI call in Phase 2
        val stub = "[liteGem stub] Prompt received: \"${prompt.take(60)}...\" " +
                "GGUF backend not yet linked. Phase 2 incoming."
        onToken?.invoke(stub)
        return stub
    }

    fun unload() {
        modelLoaded = false
        modelPath = ""
        // Native: nUnload()
    }

    // JNI declarations — implemented in libllama.so (Phase 2)
    private external fun nLoadModel(path: String): Boolean
    private external fun nGenerate(prompt: String, maxTokens: Int): String
    private external fun nUnload()
}
