#include <jni.h>
#include <llama.h>
#include <string>
#include <vector>

struct ModelState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
};

extern "C" JNIEXPORT jlong JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeLoad(
    JNIEnv *env, jobject, jstring model_path, jint n_ctx, jint n_threads) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    llama_model *model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (!model) return 0;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_threads = static_cast<uint32_t>(n_threads);
    cparams.n_threads_batch = static_cast<uint32_t>(n_threads);

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) { llama_free_model(model); return 0; }

    auto *state = new ModelState{model, ctx};
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeGenerate(
    JNIEnv *env, jobject, jlong ptr, jstring prompt, jint max_tokens, jfloat temperature) {

    auto *state = reinterpret_cast<ModelState *>(ptr);
    if (!state || !state->ctx) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    int n_prompt = llama_tokenize(state->ctx, prompt_str, -1, nullptr, 0, true, true);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    if (n_prompt <= 0) return env->NewStringUTF("");

    std::vector<llama_token> tokens(n_prompt);
    llama_tokenize(state->ctx, prompt_str, -1, tokens.data(), n_prompt, true, true);

    std::string result;
    for (int i = 0; i < max_tokens && i < n_prompt + max_tokens; i++) {
        if (llama_eval(state->ctx, tokens.data(), tokens.size(), 0) != 0) break;

        llama_token id = llama_sample_token(state->ctx, temperature, 0.8f, 40, 1.1f, 64);
        if (id == llama_token_eos(state->ctx)) break;

        char buf[8];
        int n = llama_token_to_piece(state->ctx, id, buf, sizeof(buf), 0, true);
        result.append(buf, n);
        tokens = {id};
    }
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeUnload(JNIEnv *, jobject, jlong ptr) {
    auto *state = reinterpret_cast<ModelState *>(ptr);
    if (state) {
        if (state->ctx) llama_free(state->ctx);
        if (state->model) llama_free_model(state->model);
        delete state;
    }
}
