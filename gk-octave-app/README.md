# GK Motor Octave Noise Analyzer — v2.1

An engineering-oriented offline Android acoustic spectrum tool for electric motors. Based on v2.0. APK package com.gk.octaveanalyzer.stable.

- 1/1- and 1/3-octave analysis (FFT estimates), A/C/Z weighting, live and Leq, 10s-10min/custom timer and auto-stop, CSV export.
- Motor test setup captures motor ID, RPM, supply frequency, drive type (direct/VFD), load %, microphone distance and measurement position.
- Displays expected **shaft 1X, 2X and 2×electrical frequency** guidance from entered inputs; these are **not fault diagnoses**.
- **Background capture with motor OFF** before measuring running motor, using identical band and weighting settings and microphone location. Running and background Leq shown per band, with *logarithmic energy subtraction* where motor–background difference is >3 dB. Values between 3–10 dB flagged uncertain; ≤3 dB flagged "BG high" (not separable).
- CSV includes motor metadata, background and running Leq in raw dBFS, net motor band where separable, relative acoustic delta, and warnings.
- Android MIC gain and DSP may vary between runs, invalidating background corrections. All uncalibrated dBFS data remains relative, and is NOT calibrated sound-pressure level dB SPL or sound-power level dB(A).
- Real acceptance tests per IEC 60034-9:2021 and ISO 3744 require compliant acoustic instruments, suitable environments, mic measurement geometry and corrections. This app is **not IEC 61260-1 Class 1/2 or IEC 61672 certified**.
- Developed By: Gaurav Kharva.