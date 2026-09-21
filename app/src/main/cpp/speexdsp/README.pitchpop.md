# SpeexDSP 1.2.1

Vendored C sources/headers from the official release:
https://downloads.xiph.org/releases/speex/speexdsp-1.2.1.tar.gz

Archive SHA-256: `8c777343e4a6399569c72abc38a95b24db56882c83dbdb6c6424a5f4aeb54d3d`.
Original license and authors are retained in COPYING and AUTHORS. Only the echo
canceller, preprocessor and their FFT/filterbank dependencies are built.
`include/speex/speexdsp_config_types.h` is our standard-integer portability header.
The remaining upstream source is unmodified. The build uses floating point and
SmallFT, with no SIMD assembly or network/runtime dependency.
