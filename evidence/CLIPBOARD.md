# Official server clipboard: static protocol evidence

No network requests, real clipboard reads/writes, or target execution were performed. Full managed evidence `ev_21ab3f299b569e851d29851c9630a2af107cfe94a917f3b52bef3acd4d1182b4` is retained in `server-media-evidence-bundle.json`; complete local extraction `/tmp/remote-mouse-analysis/windows-members-local.json` reports coverage complete. RemoteMouse.exe SHA256 `e9b98e6cce2cb7dbdaec44828aafd6be17c5ffe30f24b17bcaa95edf5d2cd4a7`; windows_api.dll SHA256 `af15d114d93df4cc935456105d3379b59668b21cf6c8cb769ffa936d5ac4a5c1`.

## Supported channel

Bidirectional clipboard exists on the Dock TCP server **1979**, not the raw command UDP1978 channel. Listener state machine `0x0600039a`, IL18–49, binds0.0.0.0:1979. Receiver `A.m.A` `0x0600038f` reads a marker byte and requires `#` (IL272–330); legacy input displays an update-client message and disconnects. Then it reads one little-endian float (discarded), one UInt32LE body length, and that body. Length must be between TCPCrypto size25 and268435456 inclusive (IL330–426). The first25 body bytes are TCPCrypto; remaining bytes go through native DecodeTCP (`0x060002d4`, IL528–537). This is a different format from modern UDP RMChunk.

Plaintext dispatcher `0x06000391` recognizes `clp` (IL1108–1119), then exactly two operation characters at bytes3..4:

- `clp-r`: schedules clipboard read on the UI thread (`0x06000397`).
- `clp-w` + `txt` + four bytes + UTF8(text): write. Minimum decoded request length12; format at offsets5..7; actual text bytes are **all bytes from offset12 to end** (IL1470–1566). The four bytes at8..11 are not inspected at all by this handler. Encoding original text byte count as Int32LE there is consistent with the response format, but original Android construction has not been verified.

Write routine `0x060003ad` decodes UTF8 and calls System.Windows.Forms.Clipboard.SetText for format `txt`. Its result bool is discarded by `0x06000399`, which shows a local updated notification regardless; **there is no write ACK** on this path. Do not report confirmed remote success merely because a socket write succeeds. A deliberate subsequent read-back could verify text, but requires another explicit operation and may race clipboard changes.

Read routine `0x060003ac` calls Clipboard.GetDataObject, enumerates formats, and returns the first supported image or text. Text uses Clipboard.GetText(UnicodeText enum1), then UTF8 (IL290–317). Types include `txt`, `img`, `emp` (null clipboard object), `nop` (no supported data or caught exception). An image may win over text due to format enumeration; a text-only client must reject image data rather than interpret it as text. No automatic subscription, change notification or push route was found here: `clp-r` is explicit request/reply.

## TCP codec can be ported to Java

Native static disassembly: `GetTCPCryptoSize` at0x10001b20 returns0x19=25. `DecodeTCP`0x10001dc0 and `EncodePayload`0x10001f70 are short XOR loops. No Windows DLL is needed to reproduce this framing/codec.

TCPCrypto native packed layout:

| Offset | Bytes | Meaning |
|---|---:|---|
|0|8|doubleLE Unix seconds, each byte XOR mask|
|8|4|CK, copied but unused by this decoder|
|12|4|Ck, copied but unused by this decoder|
|16|1|mask XOR0x20|
|17|8|eight payload key bytes, each XOR mask|

Decoder computes `mask = crypto[16] ^ 0x20`; `key[i] = crypto[17+i] ^ mask`; `plain[i] = cipher[i] ^ key[i % 8]`; timestamp is doubleLE of deobfuscated bytes0..7. CK/Ck are not validated by this decoder. Request construction therefore is `'#' + floatLE(version) + uint32LE(25+plain.length) + crypto25 + xorPayload`. The received float is discarded by the examined server (not compared). Version3.0 is a reasonable consistent value, not an observed Android TCP constructor claim. Payload `clp-r` is5 bytes, without command-port length headers or newline.

Read response `0x06000390` is **three UTF8 type bytes + Int32LE original payload byte count + XOR payload**. No `#`, outer length or TCPCrypto header is sent. Null payload yields type+length0. For a nonempty payload, wrapper `0x060002ce` invokes native EncodePayload, which XORs using an eight-byte **global native key** set during the most recent DecodeTCP. Thus nominal response key is the key of the request; another simultaneous official Dock client can change that global key. Serialize this client's own Dock operations and treat malformed UTF8/type/length as failure. This protocol supplies obfuscation, not authenticated encryption or TLS.

Synthetic offline vector (not a live request): mask5a, key0102030405060708, timestamp1700000000.125, CK/Ck0, plaintext `clp-r`, version3.0 -> full request hex:

`23000040401e0000005a5a521aa60e831b00000000000000007a5b58595e5f5c5d52626e732977`

For that same key, a synthetic response containing text `ABC` is `74787403000000404040`. The example timestamp is historical and will be rejected live. Vectors are derived from disassembly, not independently executed against the DLL.

## Authorization and implementation bounds

Dispatcher `0x06000391` IL59–94 checks the existing verified command-port1978 host authorization when server RequiresClientVerification is enabled (`0x060002ab`), rejects unauthorized Dock commands, then renews authorization (`0x060002aa`). It enforces timestamp within300 seconds of DateTimeOffset.Now.ToUnixTimeSeconds (IL94–170). A global timestamp cache rejects replay and retains up to500 values (IL782–912). Generate a unique recent fractional-second timestamp for each request and keep existing server authentication intact. No additional password syntax inside the Dock payload was demonstrated.

Official receiver allows256MiB, which is inappropriate for a small text client. Impose a much smaller local UTF8 byte limit (for example64KiB), validate the three-byte response type and signed length before allocating, use exact stream reads and bounded connect/read timeouts, reject invalid UTF8 and non-text content, and perform clipboard access only after the user's explicit action. Android clipboard read restrictions still apply; this is not evidence for silent background sync. Connection failure on1979 must remain distinguishable from empty clipboard. Windows lock-screen clipboard availability is unverified.

Original Android inspection via current REA was unavailable: provider returned capability_unavailable because REA_JADX_MCP_JAR is unset. Retained Android Encrypter class summary proves an encodeTCP(String) method exists, but does not supply its body or original clipboard write constructor. Server-side parser and DLL codec establish Java feasibility; version choice and reserved write length are explicitly identified above as unverified client convention. No runtime interoperability has been tested.

## User validation and URL failure

The user confirmed phone → PC and PC → phone with a short plain text copied from Notepad. Copying a browser URL returns official type `nop` (`CLIP-NO-TEXT`), not a client length, UTF8 or connectivity failure.

Reader 0x060003ac IL74–148 compares each format lowercased against `png`, `dataobject`, `jpg`, `bitmap`. Any match enters GetData(DataFormats.Bitmap,true) and Image/Bitmap conversion (IL150–205). An exception jumps to the global catch IL344 and returns `nop`, skipping remaining text formats. Formats that do not match images are tested against UnicodeText/Text/Html, and the loop otherwise continues. Thus an advertised DataObject without a usable bitmap preceding text can explain the browser URL failure. This is a source-based inference; the user's actual format list was not captured.

There is no force-text request flag: the single clp-r path invokes this no-argument reader. Android cannot recover text omitted from a nop reply. Copying via Notepad works as a workaround. The own server uses CF_UNICODETEXT directly and bypasses this format-selection bug; physical browser URL testing of that route remains pending.

## Native Windows regression verification

Run [41723301963](https://github.com/epava1516/mando_multimedia_windows/actions/runs/41723301963), commit1ebcd2d, passed the real Windows clipboard URL fixtures. An isolated runner registered DataObject before CF_UNICODETEXT with a synthetic URL, and the own reader returned the exact Unicode URL. A second fixture wrote and read a URL with Unicode characters, query parameters and fragment. These tests exercise actual user32/kernel32 clipboard calls, not mocks. No user PC clipboard was accessed. The server implementation is the same as the distributed alpha.7 package; browser testing on the user's PC and migration still require running that package on Windows.
