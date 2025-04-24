import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class Message {
    public int length;  // 1 + payload length
    public byte type;
    public byte[] payload;

    public Message(byte type, byte[] payload) {
        this.type = type;
        this.payload = payload;
        this.length = 1 + (payload == null ? 0 : payload.length);
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(type);
        if (payload != null)
            buffer.put(payload);
        return buffer.array();
    }

    public static Message fromBytes(byte[] data) throws Exception {
        if (data.length < 5)
            throw new Exception("Invalid message length");
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int length = buffer.getInt();
        byte type = buffer.get();
        byte[] payload = null;
        if (length > 1) {
            payload = new byte[length - 1];
            buffer.get(payload);
        }
        switch (type) {
            case 0:
                return new ChokeMessage();
            case 1:
                return new UnchokeMessage();
            case 2:
                return new InterestedMessage();
            case 3:
                return new NotInterestedMessage();
            case 4:
                if (payload == null || payload.length != 4)
                    throw new Exception("Invalid have message payload");
                int pieceIndex = ByteBuffer.wrap(payload).getInt();
                return new HaveMessage(pieceIndex);
            case 5:
                return new BitfieldMessage(payload);
            case 6:
                if (payload == null || payload.length != 4)
                    throw new Exception("Invalid request message payload");
                int reqIndex = ByteBuffer.wrap(payload).getInt();
                return new RequestMessage(reqIndex);
            case 7:
                if (payload == null || payload.length < 4)
                    throw new Exception("Invalid piece message payload");
                ByteBuffer buf = ByteBuffer.wrap(payload);
                int pieceIdx = buf.getInt();
                byte[] pieceData = Arrays.copyOfRange(payload, 4, payload.length);
                return new PieceMessage(pieceIdx, pieceData);
            case 8:
                return new CompleteMessage();
            default:
                throw new Exception("Unknown message type: " + type);
        }
    }

    // Message types below:

    public static class ChokeMessage extends Message {
        public ChokeMessage() {
            super((byte) 0, null);
        }
    }

    public static class UnchokeMessage extends Message {
        public UnchokeMessage() {
            super((byte) 1, null);
        }
    }

    public static class InterestedMessage extends Message {
        public InterestedMessage() {
            super((byte) 2, null);
        }
    }

    public static class NotInterestedMessage extends Message {
        public NotInterestedMessage() {
            super((byte) 3, null);
        }
    }

    public static class HaveMessage extends Message {
        public int pieceIndex;
        public HaveMessage(int pieceIndex) {
            super((byte) 4, ByteBuffer.allocate(4).putInt(pieceIndex).array());
            this.pieceIndex = pieceIndex;
        }
    }

    public static class BitfieldMessage extends Message {
        public BitfieldMessage(byte[] bitfield) {
            super((byte) 5, bitfield);
        }
    }

    public static class RequestMessage extends Message {
        public int pieceIndex;
        public RequestMessage(int pieceIndex) {
            super((byte) 6, ByteBuffer.allocate(4).putInt(pieceIndex).array());
            this.pieceIndex = pieceIndex;
        }
    }

    public static class PieceMessage extends Message {
        public int pieceIndex;
        public byte[] pieceData;
        public PieceMessage(int pieceIndex, byte[] pieceData) {
            super((byte) 7, createPayload(pieceIndex, pieceData));
            this.pieceIndex = pieceIndex;
            this.pieceData = pieceData;
        }
        private static byte[] createPayload(int pieceIndex, byte[] pieceData) {
            ByteBuffer buffer = ByteBuffer.allocate(4 + pieceData.length);
            buffer.putInt(pieceIndex);
            buffer.put(pieceData);
            return buffer.array();
        }
    }

    public static class CompleteMessage extends Message {
        public CompleteMessage() {
            super((byte) 8, new byte[0]); // Cast to byte ✅
        }
    }       
}
