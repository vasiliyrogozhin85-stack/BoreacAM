import com.boreac.am.modem.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

public final class ProtocolSelfTest {
    public static void main(String[] args) throws Exception {
        if (!QamCodec.selfTest()) throw new AssertionError("QAM loopback failed");

        byte[] source = new byte[2500];
        new Random(321).nextBytes(source);
        long fileCrc = Crc32Util.of(source, source.length);
        int total = (source.length + Protocol321.PAYLOAD_MAX - 1) / Protocol321.PAYLOAD_MAX;
        int session = 1234567;

        ShortArray all = new ShortArray();
        all.zeros(Protocol321.leadSamples());

        for (int packet = 0; packet < total; packet++) {
            int start = packet * Protocol321.PAYLOAD_MAX;
            int len = Math.min(Protocol321.PAYLOAD_MAX, source.length - start);
            byte[] payload = Arrays.copyOfRange(source, start, start + len);
            FrameHeader header = new FrameHeader(
                    Protocol321.FRAME_DATA,
                    Protocol321.MODE_NORMAL,
                    session,
                    packet,
                    total,
                    len,
                    source.length,
                    fileCrc,
                    Crc32Util.of(payload, payload.length)
            );
            short[] frame = FrameEncoder.buildFrame(header, payload);
            all.add(frame);
            all.add(frame);
        }

        FrameHeader end = new FrameHeader(
                Protocol321.FRAME_END,
                Protocol321.MODE_NORMAL,
                session,
                total,
                total,
                0,
                source.length,
                fileCrc,
                0
        );
        all.add(FrameEncoder.buildFrame(end, new byte[0]));

        File decoded = File.createTempFile("boreacam-test", ".bin");
        ReceiverDecoder.Result result = ReceiverDecoder.decode(all.array(), decoded, null);
        byte[] restored = Files.readAllBytes(decoded.toPath());
        decoded.delete();

        if (!Arrays.equals(source, restored)) throw new AssertionError("File loopback failed");
        if (result.fileCrc != fileCrc) throw new AssertionError("CRC mismatch");

        System.out.println("BoreacAM protocol self-test: OK");
        System.out.println("Bytes: " + restored.length + ", packets: " + result.packets);
    }

    private static final class ShortArray {
        private short[] data = new short[4096];
        private int size;

        void add(short[] values) {
            ensure(size + values.length);
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        void zeros(int count) {
            ensure(size + count);
            size += count;
        }

        short[] array() {
            return Arrays.copyOf(data, size);
        }

        private void ensure(int required) {
            if (required <= data.length) return;
            int next = data.length;
            while (next < required) next *= 2;
            data = Arrays.copyOf(data, next);
        }
    }
}
