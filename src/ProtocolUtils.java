import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ProtocolUtils {
    public static final String HANDSHAKE_HEADER = "P2PFILESHARINGPROJ"; // exactly 18 bytes
    public static final int HANDSHAKE_MSG_LENGTH = 32; // 18 + 10 + 4

    // Creates a handshake message for the given peerId.
    public static byte[] createHandshakeMessage(int peerId) {
        ByteBuffer buffer = ByteBuffer.allocate(HANDSHAKE_MSG_LENGTH);
        // Write the header (18 bytes)
        byte[] headerBytes = HANDSHAKE_HEADER.getBytes(StandardCharsets.US_ASCII);
        buffer.put(headerBytes);
        // Write 10 zero bytes
        byte[] zeros = new byte[10];
        buffer.put(zeros);
        // Write the 4-byte peer ID
        buffer.putInt(peerId);
        return buffer.array();
    }

    // Parses a handshake message and returns the peerId.
    public static int parseHandshakeMessage(byte[] handshake) throws Exception {
        if (handshake.length != HANDSHAKE_MSG_LENGTH) {
            throw new Exception("Invalid handshake message length");
        }
        ByteBuffer buffer = ByteBuffer.wrap(handshake);
        byte[] headerBytes = new byte[18];
        buffer.get(headerBytes);
        String header = new String(headerBytes, StandardCharsets.US_ASCII);
        if (!header.equals(HANDSHAKE_HEADER)) {
            throw new Exception("Invalid handshake header: " + header);
        }
        // Skip 10 zero bytes (automatically done by buffer position)
        buffer.position(28);
        int peerId = buffer.getInt();
        return peerId;
    }
}
