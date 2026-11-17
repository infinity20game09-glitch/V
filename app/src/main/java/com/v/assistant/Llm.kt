package com.v.assistant

fun interface TokenCallback {
    fun onToken(s: String): Boolean
}

object Llm {
    init { System.loadLibrary("vcore") }
    external fun nativeLoad(path: String, nCtx: Int, threads: Int): Boolean
    external fun nativeUnload()
    external fun nativeStop()
    external fun nativeGenerate(
        roles: Array<String>, contents: Array<String>,
        maxTokens: Int, temp: Float, cb: TokenCallback
    ): String
}
