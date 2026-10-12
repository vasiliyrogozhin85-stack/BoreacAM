# AudioModem 3.2.3 compatibility

## Frame header — 36 bytes, little endian

| Offset | Length | Value |
|---:|---:|---|
| 0 | 1 | `V` |
| 1 | 1 | `3` |
| 2 | 1 | protocol revision `2` |
| 3 | 1 | frame type |
| 4 | 1 | mode |
| 5 | 4 | session ID |
| 9 | 4 | packet number |
| 13 | 4 | total packet count |
| 17 | 2 | payload length |
| 19 | 4 | file size |
| 23 | 4 | whole-file CRC32 |
| 27 | 4 | packet CRC32 |
| 31 | 4 | reserved, zero |
| 35 | 1 | XOR of bytes 0…34 |

## Frame waveform

```text
8 training symbols
4 sync symbols
6 header symbols
N payload symbols
70 ms zero gap
```

Each DATA frame is transmitted twice.

## QAM symbol

- sample rate: 48000 Hz;
- useful interval: 80 samples;
- cyclic prefix: 16 samples;
- carrier frequencies: `1800 + carrier*600 Hz`;
- pilot carriers: 3 and 12;
- 14 data carriers = 14 nibbles = 7 bytes;
- Gray level mapping: `0→-3, 1→-1, 3→+1, 2→+3`;
- waveform: `I*cos(w*n) - Q*sin(w*n)`;
- amplitude scale: `22000 / 52`.

## Sync words

```text
A5 5A 3C C3 96 69 F0
1E E1 87 78 4B B4 2D
CC 33 F0 0F AA 55 99
69 96 5A A5 C3 3C 0F
```
