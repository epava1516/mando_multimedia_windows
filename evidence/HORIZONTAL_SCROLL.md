# Official Remote Mouse horizontal wheel (531 examined artifact)

Static analysis only; no controls sent. Reused complete REA member evidence `ev_21ab3f299b569e851d29851c9630a2af107cfe94a917f3b52bef3acd4d1182b4`, artifact SHA256 `e9b98e6cce2cb7dbdaec44828aafd6be17c5ffe30f24b17bcaa95edf5d2cd4a7`. Selected original CIL and decoded string helpers: `server-horizontal-wheel-selected.json`.

`A.I.A`, token `0x060000c3`, decodes/splits the mouse payload at spaces. Its `w` branch (IL962–1227, string helper `0x06000413`) explicitly supports four directions:

| Body | Flags/data at WinAPI.mouse_event | Meaning |
|---|---|---|
| `w 1` | 2048 / +magnitude (IL1089–1118) | Vertical positive |
| `w 0` | 2048 / -magnitude (IL1123–1153) | Vertical negative |
| `w 2` | 4096 / +magnitude (IL1158–1187) | Horizontal right |
| `w 3` | 4096 / -magnitude (IL1192–1222) | Horizontal left |

String helpers `0x06000414` through `0x06000417` decode respectively to `1`, `0`, `2`, `3`. Callee `0x06000211` is `RemoteMouse.Utilities.WinAPI.mouse_event(i4,i4,i4,i4,native-int)`. Flag4096 is MOUSEEVENTF_HWHEEL. Microsoft documents positive horizontal data as right and negative as left: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-mouse_event . These are native horizontal wheel events, without pressing Shift.

Exact whole UTF8 frames for existing raw legacy UDP transport (one frame per datagram, existing credential handling applies):

- Right: `mos  3w 2` (hex `6d6f73202033772032`).
- Left: `mos  3w 3` (hex `6d6f73202033772033`).

The header has two spaces before `3`: three-byte prefix `mos`, three-byte decimal length `  3`, then three-byte body. No newline or terminator. No guessed modifier syntax, concatenated frames, synthetic held Shift, or persistent key state is needed.

Magnitude is controlled by server state: IL980–1023 updates the previous wheel timestamp; IL1024 initializes magnitude to0; IL1027–1087 assigns `2 * scrollSetting` for elapsed time below0.08 seconds (the subsequent0.05/0.02 branches assign the same expression, not accumulated multiplication). Default scrollSetting is4 (`A.I..cctor`, token0x060000c6, IL7–8). Therefore first/isolated events after >=80ms can produce zero movement; subsequent genuine drag samples faster than80ms get magnitude8 by default. Do not reinterpret each packet as a fixed120 tick or claim per-packet confirmation. Runtime application support/delivery remains to be tested.

## Independent client integration

The app maps positive vertical steps to down (w0) and positive horizontal steps to right (w2). A logical default wheel step uses fifteen magnitude8 samples; after an idle interval a seed sample accounts for the server's zero-magnitude first event. These are deliberate high-resolution input samples, never retries after packet/error/timeout. The server scroll setting still scales the effective magnitude. No physical response was observed by this static analysis.
