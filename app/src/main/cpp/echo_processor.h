#ifndef PITCHPOP_ECHO_PROCESSOR_H
#define PITCHPOP_ECHO_PROCESSOR_H
#include <stdint.h>
enum { PP_ECHO_FRAME = 320, PP_ECHO_RATE = 16000 };
void *pp_echo_create(void);
void pp_echo_destroy(void *handle);
void pp_echo_process(void *handle, const int16_t *microphone, const int16_t *rendered, int16_t *cleaned);
#endif
