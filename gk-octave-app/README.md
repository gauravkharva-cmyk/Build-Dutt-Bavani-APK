# GK Octave Sound Analyzer v1.4

This is the single consolidated Android APK integrating all prior changes (v1.0–v1.3):
- Built-in microphone, offline, real-time FFT based 1/1-octave and 1/3-octave plots and level tables.
- A / C / Z frequency weighting; live level and cumulative Leq.
- Measurement timer: unlimited, 10 / 30 / 60 seconds, 2 / 5 / 10 minutes or custom 1-3600 seconds. Automatic stop retains last result.
- Save as CSV: measurement duration, weighting, band data, live and Leq, and clearly marked reference status.
- Optional acoustic calibration from a real 1 kHz reference, with reset.
- Optional estimated positive sound-level display WITHOUT external calibration: record at least 10 seconds in a quiet room with A weighting, then choose an assumed background dBA to derive a rough offset. Display and CSV explicitly label this as an estimate, never certified.
- On-first-launch guidance to avoid confusing negative raw dBFS with wrong readings.
- Dark-mode contrast fix for the inactive 1/1-octave button.
- Developed By: Gaurav Kharva.

**Accuracy limitation:** Raw dBFS is expected to be negative. Without an independent acoustic reference the app cannot know true dB SPL. The approximate mode is an assumption, not a measured absolute sound level, may have large errors, and is unsuitable for motor acceptance, IEC 61260/61672 tests, hearing safety or legal decisions.