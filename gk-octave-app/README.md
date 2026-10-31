GK Octave Analyzer v2.0
Original offline Android FFT 1/1 and 1/3 octave analysis, A/C/Z weighting, live + Leq, timer and CSV preserved.

New diagnostics:
- 3-tone synthetic signal FFT self-test. Compares dBFS FFT energy with exact time-domain RMS; no mic reference is assumed.
- getActiveMicrophones() sensitivity metadata is checked for both normal MIC and UNPROCESSED pathways, not only when the device reports UNPROCESSED support.
- MIC factory sensitivity supports *indicative* SPL but capture gain/noise suppression can invalidate it; labels warn of unverified gain.
- If factory metadata is unavailable and UNPROCESSED unsupported, only raw dBFS can be calculated. Digital dBFS is correctly negative below full scale.
- No arbitrary user offsets.
NOT a certified sound level meter and not for industrial motor noise acceptance without calibration.
Developed By: Gaurav Kharva.