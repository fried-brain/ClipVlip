package com.friedbrain.clipvlip

data class ModelArtifact(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
    val minimumRuntime: String
) {
    val sizeLabel: String
        get() = "%.2f GB".format(sizeBytes / 1_000_000_000.0)
}

object ModelCatalog {
    val models = listOf(
        ModelArtifact(
            id = "qwen-2.5-1.5b",
            name = "Qwen 2.5 Instruct · 1.5B",
            role = "Transcript refinement and caption generation",
            description = "Multilingual instruction model. Runs locally after download.",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_597_922_856L,
            sha256 = "c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e",
            license = "Apache License 2.0",
            minimumRuntime = "LiteRT-LM 0.18.0"
        ),
        ModelArtifact(
            id = "deepseek-r1-qwen-1.5b",
            name = "DeepSeek-R1-Distill-Qwen · 1.5B",
            role = "Viral highlight reasoning and hook generation",
            description = "Reasoning model for ranking transcript moments and drafting hooks.",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_833_451_520L,
            sha256 = "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
            license = "MIT License",
            minimumRuntime = "LiteRT-LM 0.18.0"
        )
    )
    fun byId(id: String): ModelArtifact? = models.firstOrNull { it.id == id }
}
