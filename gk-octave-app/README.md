# GK Motor Octave Noise Analyzer v2.2

## High overall noise level diagnostic
- The original app's default overall weighting was Z (no frequency weighting). The v2.2 default is A-weighting, the common electrical motor noise metric dB(A).
- One capture calculates and displays broadband **A, C, Z Leq** simultaneously from the SAME FFT sample data and shows Z-A difference. Switching the octave weighting still works. No artificial acoustic adjustment is added.
- CSV preserves A/C/Z broadband digital Leq, displayed values, weighting and other motor details.
- Motor test inputs for RPM, frequency, load, mic distance, supply type and placement; background and motor-running recordings with tentative per-band energy subtraction; 1/1 and 1/3 octave analysis; timer and CSV.
- `MIC` recording gain on this Lenovo tablet is not verified and may be processed, so these are *not guaranteed absolute SPL measurements*. The app never substitutes relative adjusted numbers for acoustically calibrated data. A valid comparison with a reference sound-level meter must use identical weighting, time integration, microphone distance/position and conditions.
- The tool is not IEC 61260/61672 certified. Motor sound-power acceptance per IEC 60034-9 / ISO 3744 requires compliant equipment.

Developed By: Gaurav Kharva.