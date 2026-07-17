#include <jni.h>
#include <llama.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#define LOG_TAG "CypherJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

struct ModelState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::mutex mtx;
    int n_ctx = 2048;
};

static bool backend_initialized = false;
static std::mutex backend_mutex;

extern "C" JNIEXPORT jlong JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeLoad(
    JNIEnv *env, jobject, jstring model_path, jint n_ctx, jint n_threads) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return 0;
    }

    {
        std::lock_guard<std::mutex> lock(backend_mutex);
        if (!backend_initialized) {
            llama_backend_init();
            backend_initialized = true;
            LOGI("llama backend initialized");
        }
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    LOGI("Loading model from: %s", path);
    llama_model *model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) {
        LOGE("Failed to load model from file");
        return 0;
    }
    LOGI("Model loaded successfully");

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx > 0 ? n_ctx : 2048);
    cparams.n_threads = static_cast<uint32_t>(n_threads > 0 ? n_threads : 4);
    cparams.n_threads_batch = static_cast<uint32_t>(n_threads > 0 ? n_threads : 4);

    llama_context *ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        return 0;
    }
    LOGI("Context created with n_ctx=%d", cparams.n_ctx);

    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("Failed to get vocabulary");
        llama_free(ctx);
        llama_model_free(model);
        return 0;
    }

    auto *state = new ModelState{model, ctx, vocab, {}, cparams.n_ctx};
    LOGI("Native state created with ptr=%p", (void*)state);
    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeGenerate(
    JNIEnv *env, jobject, jlong ptr, jstring prompt, jint max_tokens, jfloat temperature) {

    auto *state = reinterpret_cast<ModelState *>(ptr);
    if (!state) {
        LOGE("nativeGenerate called with null state ptr");
        return env->NewStringUTF("");
    }

    std::lock_guard<std::mutex> lock(state->mtx);

    if (!state->ctx || !state->model || !state->vocab) {
        LOGE("nativeGenerate called with invalid state (ctx=%p model=%p vocab=%p)",
             (void*)state->ctx, (void*)state->model, (void*)state->vocab);
        return env->NewStringUTF("");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        LOGE("Failed to get prompt string");
        return env->NewStringUTF("");
    }
    std::string prompt_cpp(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    int n_prompt_tokens = llama_tokenize(state->vocab, prompt_cpp.c_str(), -1, nullptr, 0, true, true);
    if (n_prompt_tokens <= 0) {
        LOGW("Tokenization returned %d tokens for prompt", n_prompt_tokens);
        return env->NewStringUTF("");
    }

    int safe_max_tokens = max_tokens;
    if (n_prompt_tokens + safe_max_tokens > state->n_ctx) {
        safe_max_tokens = (state->n_ctx - n_prompt_tokens) - 1;
        if (safe_max_tokens < 1) {
            LOGW("Prompt too long (%d tokens) for context window (%d)", n_prompt_tokens, state->n_ctx);
            return env->NewStringUTF("");
        }
        LOGW("Context window safety: limiting generation from %d to %d tokens", max_tokens, safe_max_tokens);
    }

    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    int tokenized = llama_tokenize(state->vocab, prompt_cpp.c_str(), -1, prompt_tokens.data(), n_prompt_tokens, true, true);
    if (tokenized != n_prompt_tokens) {
        LOGE("Tokenization mismatch: expected %d, got %d", n_prompt_tokens, tokenized);
        return env->NewStringUTF("");
    }

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(state->ctx, batch) != 0) {
        LOGE("Failed to decode prompt batch");
        return env->NewStringUTF("");
    }

    llama_sampler *sampler_chain = nullptr;
    llama_sampler **samplers = nullptr;
    int n_samplers = 0;

    auto init_samplers = [&]() -> bool {
        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        sampler_chain = llama_sampler_chain_init(sparams);
        if (!sampler_chain) { LOGE("Failed to init sampler chain"); return false; }

        llama_sampler *temp_sampler = llama_sampler_init_temp(temperature);
        if (!temp_sampler) { LOGE("Failed to init temp sampler"); return false; }
        llama_sampler_chain_add(sampler_chain, temp_sampler);

        llama_sampler *topk_sampler = llama_sampler_init_top_k(40);
        if (!topk_sampler) { LOGE("Failed to init top-k sampler"); return false; }
        llama_sampler_chain_add(sampler_chain, topk_sampler);

        llama_sampler *dist_sampler = llama_sampler_init_dist(LLAMA_DEFAULT_SEED);
        if (!dist_sampler) { LOGE("Failed to init dist sampler"); return false; }
        llama_sampler_chain_add(sampler_chain, dist_sampler);

        return true;
    };

    if (!init_samplers()) {
        if (sampler_chain) llama_sampler_free(sampler_chain);
        return env->NewStringUTF("");
    }

    std::string result;
    std::vector<llama_token> token_buf = {0};
    int eos_id = llama_vocab_eos(state->vocab);

    for (int i = 0; i < safe_max_tokens; i++) {
        llama_token id = llama_sampler_sample(sampler_chain, state->ctx, -1);
        if (id == eos_id || id == -1) break;

        char buf[16];
        int n = llama_token_to_piece(state->vocab, id, buf, sizeof(buf) - 1, 0, true);
        if (n > 0) {
            buf[n] = '\0';
            result.append(buf, n);
        }

        token_buf[0] = id;
        batch = llama_batch_get_one(token_buf.data(), 1);
        if (llama_decode(state->ctx, batch) != 0) {
            LOGW("Decode failed at token %d", i);
            break;
        }
    }

    llama_sampler_free(sampler_chain);
    LOGI("Generated %zu bytes of text", result.size());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_cypher_assistant_CypherBrain_nativeUnload(JNIEnv *, jobject, jlong ptr) {
    auto *state = reinterpret_cast<ModelState *>(ptr);
    if (!state) {
        LOGW("nativeUnload called with null state ptr");
        return;
    }

    LOGI("Unloading native state at %p", (void*)state);

    {
        std::lock_guard<std::mutex> lock(state->mtx);
        if (state->ctx) {
            llama_free(state->ctx);
            state->ctx = nullptr;
            LOGI("Llama context freed");
        }
        if (state->model) {
            llama_model_free(state->model);
            state->model = nullptr;
            LOGI("Llama model freed");
        }
        state->vocab = nullptr;
    }

    delete state;
    LOGI("Native state deleted");
}
