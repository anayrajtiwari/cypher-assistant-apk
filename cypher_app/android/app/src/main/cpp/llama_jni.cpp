#include <jni.h>
#include <llama.h>
#include <string>
#include <vector>

struct ModelState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
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
    if (!ctx) { llama_model_free(model); return 0; }

    const llama_vocab *vocab = llama_model_get_vocab(model);

    auto *state = new ModelState{model, ctx, vocab};
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeGenerate(
    JNIEnv *env, jobject, jlong ptr, jstring prompt, jint max_tokens, jfloat temperature) {

    auto *state = reinterpret_cast<ModelState *>(ptr);
    if (!state || !state->ctx) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_cpp(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    int n_prompt = llama_tokenize(state->vocab, prompt_cpp.c_str(), -1, nullptr, 0, true, true);
    if (n_prompt <= 0) return env->NewStringUTF("");

    std::vector<llama_token> prompt_tokens(n_prompt);
    llama_tokenize(state->vocab, prompt_cpp.c_str(), -1, prompt_tokens.data(), n_prompt, true, true);

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(state->ctx, batch) != 0) {
        return env->NewStringUTF("");
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *sampler_chain = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler_chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler_chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler_chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    std::vector<llama_token> token_buf = {0};

    for (int i = 0; i < max_tokens; i++) {
        llama_token id = llama_sampler_sample(sampler_chain, state->ctx, -1);
        if (id == llama_vocab_eos(state->vocab)) break;

        char buf[8];
        int n = llama_token_to_piece(state->vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        token_buf[0] = id;
        batch = llama_batch_get_one(token_buf.data(), 1);
        if (llama_decode(state->ctx, batch) != 0) break;
    }

    llama_sampler_free(sampler_chain);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeUnload(JNIEnv *, jobject, jlong ptr) {
    auto *state = reinterpret_cast<ModelState *>(ptr);
    if (state) {
        if (state->ctx) llama_free(state->ctx);
        if (state->model) llama_model_free(state->model);
        delete state;
    }
}
