#include "echo_processor.h"
#include "speex/speex_echo.h"
#include "speex/speex_preprocess.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

enum { HISTORY = 32000, LEARN = 12800 };

typedef struct {
    SpeexEchoState *echo;
    SpeexPreprocessState *preprocess;
    int silent_reference_frames;
    int16_t history[HISTORY], microphone[LEARN];
    int64_t samples, learning_start;
    int alignment_delay, learned, direct_delay;
    double direct_gain;
    int16_t previous_dry[PP_ECHO_FRAME];
    int16_t previous_direct[PP_ECHO_FRAME];
} EchoProcessor;

static int initialize_filters(EchoProcessor *processor) {
    processor->echo = speex_echo_state_init(PP_ECHO_FRAME, PP_ECHO_RATE / 4);
    processor->preprocess = speex_preprocess_state_init(PP_ECHO_FRAME, PP_ECHO_RATE);
    if (!processor->echo || !processor->preprocess) return 0;
    int rate = PP_ECHO_RATE, enabled = 1, disabled = 0;
    int noise_db = 0, echo_db = -55, active_echo_db = -6;
    speex_echo_ctl(processor->echo, SPEEX_ECHO_SET_SAMPLING_RATE, &rate);
    speex_preprocess_ctl(processor->preprocess, SPEEX_PREPROCESS_SET_ECHO_STATE, processor->echo);
    speex_preprocess_ctl(processor->preprocess, SPEEX_PREPROCESS_SET_DENOISE, &enabled);
    speex_preprocess_ctl(processor->preprocess, SPEEX_PREPROCESS_SET_AGC, &disabled);
    speex_preprocess_ctl(processor->preprocess, SPEEX_PREPROCESS_SET_NOISE_SUPPRESS, &noise_db);
    speex_preprocess_ctl(processor->preprocess, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS, &echo_db);
    speex_preprocess_ctl(processor->preprocess, SPEEX_PREPROCESS_SET_ECHO_SUPPRESS_ACTIVE, &active_echo_db);
    return 1;
}

void *pp_echo_create(void) {
    EchoProcessor *processor = calloc(1, sizeof(*processor));
    if (!processor) return NULL;
    processor->learning_start = -1;
    if (!initialize_filters(processor)) { pp_echo_destroy(processor); return NULL; }
    return processor;
}

void pp_echo_destroy(void *handle) {
    EchoProcessor *processor = handle;
    if (!processor) return;
    if (processor->preprocess) speex_preprocess_state_destroy(processor->preprocess);
    if (processor->echo) speex_echo_state_destroy(processor->echo);
    free(processor);
}

static void process_frame(EchoProcessor *processor, const int16_t *microphone, const int16_t *rendered, int16_t *cleaned) {
    double energy = 0;
    for (int i = 0; i < PP_ECHO_FRAME; ++i) energy += (double)rendered[i] * rendered[i];
    if (energy > PP_ECHO_FRAME * 32.0 * 32.0) processor->silent_reference_frames = 0;
    else processor->silent_reference_frames++;
    speex_echo_cancellation(processor->echo, microphone, rendered, cleaned);
    int16_t dry[PP_ECHO_FRAME];
    memcpy(dry, cleaned, sizeof(dry));
    speex_preprocess_run(processor->preprocess, cleaned);
    // A sustained sung note must not become "stationary noise" when playback is silent.
    // Keep the same one-frame delay as the overlap-add preprocessor.
    if (processor->silent_reference_frames > 13) memcpy(cleaned, processor->previous_dry, sizeof(dry));
    memcpy(processor->previous_dry, dry, sizeof(dry));
}

static int16_t reference_at(EchoProcessor *p, int64_t at) {
    return at < 0 ? 0 : p->history[at % HISTORY];
}

static double correlation(EchoProcessor *p, int lag, int stride) {
    double cross = 0, mic_power = 0, ref_power = 0;
    // Include the entrance envelope: a sustained low chord alone has many
    // periodic correlation peaks and can select an incorrect acoustic delay.
    for (int i = 0; i < LEARN; i += stride) {
        double mic = 0, ref = 0;
        for (int j = 0; j < stride; ++j) {
            mic += p->microphone[i+j];
            ref += reference_at(p, p->learning_start+i+j-lag);
        }
        cross += mic*ref; mic_power += mic*mic; ref_power += ref*ref;
    }
    return cross*cross / (mic_power*ref_power+1);
}

static int16_t subtract_direct(EchoProcessor *p, int16_t mic, int64_t at) {
    double value = mic - p->direct_gain * reference_at(p, at-p->direct_delay);
    return (int16_t)lrint(fmax(-32768, fmin(32767, value)));
}

// Estimate latency from the first normal music, without emitting a calibration sound.
void pp_echo_process(void *handle, const int16_t *microphone, const int16_t *rendered, int16_t *cleaned) {
    EchoProcessor *p = handle;
    int64_t position = p->samples;
    double energy = 0;
    for (int i = 0; i < PP_ECHO_FRAME; ++i) {
        p->history[(position+i) % HISTORY] = rendered[i];
        energy += (double)rendered[i]*rendered[i];
    }
    if (p->learning_start < 0 && energy > PP_ECHO_FRAME*32.0*32.0) p->learning_start = position;
    if (!p->learned && p->learning_start >= 0) {
        int offset = (int)(position-p->learning_start);
        memcpy(p->microphone+offset, microphone, PP_ECHO_FRAME*sizeof(int16_t));
        if (offset+PP_ECHO_FRAME == LEARN) {
            p->learned = 1;
            int lag = 0; double best = 0;
            for (int candidate = 0; candidate <= 4000; candidate += 4) {
                double score = correlation(p, candidate, 4);
                if (score > best) { best = score; lag = candidate; }
            }
            int coarse = lag; best = 0;
            for (int candidate = coarse > 4 ? coarse-4 : 0; candidate <= coarse+4 && candidate <= 4000; ++candidate) {
                double score = correlation(p, candidate, 1);
                if (score > best) { best = score; lag = candidate; }
            }
            if (best > 0.15) {
                p->alignment_delay = lag > 160 ? lag-160 : 0;
                // Initialize the direct acoustic path only when the introduction provides
                // a strong match. The adaptive filter handles reflections and later changes.
                if (best > 0.995) {
                    double cross = 0, power = 0;
                    for (int i = 0; i < LEARN; ++i) {
                        double ref = reference_at(p, p->learning_start+i-lag);
                        cross += p->microphone[i]*ref; power += ref*ref;
                    }
                    p->direct_delay = lag;
                    p->direct_gain = fmax(-2, fmin(2, cross/(power+1)));
                }
                speex_echo_state_reset(p->echo);
                int16_t ref[PP_ECHO_FRAME], scratch[PP_ECHO_FRAME], dry[PP_ECHO_FRAME];
                for (int i = 0; i < LEARN; i += PP_ECHO_FRAME) {
                    for (int j = 0; j < PP_ECHO_FRAME; ++j) {
                        ref[j] = reference_at(p, p->learning_start+i+j-p->alignment_delay);
                        dry[j] = subtract_direct(p, p->microphone[i+j], p->learning_start+i+j);
                    }
                    speex_echo_cancellation(p->echo, p->microphone+i, ref, scratch);
                }
                memcpy(p->previous_direct, dry, sizeof(dry));
                memcpy(p->previous_dry, scratch, sizeof(scratch));
                memcpy(cleaned, scratch, sizeof(scratch));
                speex_preprocess_run(p->preprocess, cleaned);
                p->samples += PP_ECHO_FRAME;
                return;
            }
        }
    }
    int16_t reference[PP_ECHO_FRAME], dry[PP_ECHO_FRAME];
    for (int i = 0; i < PP_ECHO_FRAME; ++i) {
        reference[i] = reference_at(p, position+i-p->alignment_delay);
        dry[i] = subtract_direct(p, microphone[i], position+i);
    }
    // Keep the adaptive filter trained on the actual microphone/echo pair. Feeding
    // an already cancelled signal teaches it a zero path during a long intro,
    // then lets it learn (and suppress) the singer when the guide starts.
    process_frame(p, microphone, reference, cleaned);
    if (p->direct_gain != 0) {
        // The >.995 correlation check only selects this for an almost pure delayed
        // gain path. Preserve the same one-frame output latency as the preprocessor.
        memcpy(cleaned, p->previous_direct, sizeof(dry));
        memcpy(p->previous_direct, dry, sizeof(dry));
    }
    p->samples += PP_ECHO_FRAME;
}
