# GK Octave Analyzer v1.8 — adjustable relative display

Retains: offline Android built-in microphone capture, real-time 1/1 octave and 1/3 octave FFT analysis, A/C/Z weighting, live band graph + Leq, adjustable measurement duration and automatic stop, CSV export, raw dBFS, no-equipment assumed-room SPL mode, physical 1 kHz calibration.

NEW: explicit user-controlled **relative display shift** in range -40 to +60 dB, +/-5 dB buttons, Set/Reset dialog, stored in app preferences. Adjustment affects relative-mode readout/Leq/band graph only, does not modify raw microphone measurements. CSV always stores raw dBFS alongside adjusted display and adjustment amount. Default adjustment 0 dB and default RELATIVE digital level. Sign remains nonnegative relative-level readings.

**NOT a sound pressure meter**: the relative scale's 0 corresponds to -100 dBFS by convention; optional shift is arbitrary and does not make measurements accurate against real SPL. A valid comparison to measured reference requires proper acoustic instrumentation and microphone characterization. Do not use for motor acceptance tests, IEC certification, or hearing safety.

Developed By: Gaurav Kharva.