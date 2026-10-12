#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/core-test/out"

rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/Protocol321.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/QamCodec.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/FrameHeader.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/HeaderCodec.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/ShortBuilder.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/FrameEncoder.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/Crc32Util.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/ProtocolException.java" \
  "$ROOT/app/src/main/java/com/boreac/am/modem/ReceiverDecoder.java" \
  "$ROOT/core-test/ProtocolSelfTest.java"

java -cp "$OUT" ProtocolSelfTest
