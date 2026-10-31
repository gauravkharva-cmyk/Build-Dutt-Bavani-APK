# GK Octave Analyzer v1.9 — Android microphone factory-reference mode

- Built-in microphone, offline, FFT-based 1/1 and 1/3 octave analysis; A/C/Z weighting; cumulative Leq; timer with auto-stop; CSV export including raw dBFS data.
- **No arbitrary offset or manually adjusted relative levels.** Previous manual modes automatically deactivated on upgrade.
- Automatically requests the UNPROCESSED audio source ONLY if Android AudioManager declares it supported.
- When a single active microphone provides `MicrophoneInfo.getSensitivity()` in dBFS at a 94 dB SPL 1 kHz tone, computes indicative SPL = measured dBFS + 94 - sensitivity.
- If Android declares UNPROCESSED but no valid active sensitivity metadata is available, uses the Android Compatibility Definition nominal unprocessed 1 kHz 94 dB SPL signal level: PCM RMS=520/32768 = about -36 dBFS. Explicitly labels this as an Android nominal, **not device-verified calibration**. Manufacturer mic frequency response and gain remain error sources.
- If the device does NOT declare UNPROCESSED, does **not** invent a positive SPL. Shows raw negative dBFS and tells the user why.
- Microphone info diagnostics show routing, support and reference source; CSV records full provenance.

Neither an API nor an Android standard guarantees traceable acoustic measurement accuracy. This is NOT IEC 61260-1 or IEC 61672 certified and should not be used for industrial motor noise acceptance or hearing safety.

Developed By: Gaurav Kharva.