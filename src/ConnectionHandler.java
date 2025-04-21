import java.io.*;
import java.net.*;
import java.util.BitSet;

public class ConnectionHandler implements Runnable {
    private Socket socket;
    private int localPeerId;
    private int remotePeerId = -1;
    private DataInputStream din;
    private DataOutputStream dout;
    private FileManager fileManager; // Injected from PeerProcess

    public ConnectionHandler(Socket socket, int localPeerId, FileManager fileManager) {
        this.socket = socket;
        this.localPeerId = localPeerId;
        this.fileManager = fileManager;

        try {
            din = new DataInputStream(socket.getInputStream());
            dout = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendHandshake() throws IOException {
        byte[] handshakeMsg = ProtocolUtils.createHandshakeMessage(localPeerId);
        dout.write(handshakeMsg);
        dout.flush();
    }

    public void receiveHandshake() throws Exception {
        byte[] handshakeMsg = new byte[ProtocolUtils.HANDSHAKE_MSG_LENGTH];
        din.readFully(handshakeMsg);
        remotePeerId = ProtocolUtils.parseHandshakeMessage(handshakeMsg);
        System.out.println("Handshake received from peer " + remotePeerId);
    }

    public void sendBitfield(BitSet localBitfield, int numPieces) throws IOException {
        byte[] bitfieldBytes = ProtocolUtils.bitSetToByteArray(localBitfield, numPieces);
        sendMessage((byte) 5, bitfieldBytes);
        System.out.println("Sent bitfield to peer " + remotePeerId);
    }

    public void handleBitfield(byte[] payload) throws IOException {
        BitSet remoteBitfield = ProtocolUtils.byteArrayToBitSet(payload);
        System.out.println("Received bitfield from peer " + remotePeerId);

        boolean interested = false;
        for (int i = 0; i < fileManager.getBitfield().length(); i++) {
            if (!fileManager.getBitfield().get(i) && remoteBitfield.get(i)) {
                interested = true;
                break;
            }
        }

        byte msgType = (byte)(interested ? 2 : 3); // interested or not interested
        sendMessage(msgType, null);
        System.out.println("Sent " + (interested ? "interested" : "not interested") + " to peer " + remotePeerId);
    }

    public void sendMessage(byte msgType, byte[] payload) throws IOException {
        int length = 1 + (payload == null ? 0 : payload.length);
        dout.writeInt(length);
        dout.writeByte(msgType);
        if (payload != null) {
            dout.write(payload);
        }
        dout.flush();
    }

    @Override
    public void run() {
        try {
            sendHandshake();
            receiveHandshake();

            // Send bitfield after handshake
            sendBitfield(fileManager.getBitfield(), fileManager.getBitfield().length());

            while (true) {
                int msgLength = din.readInt();
                byte msgType = din.readByte();
                byte[] payload = new byte[msgLength - 1];
                if (payload.length > 0) {
                    din.readFully(payload);
                }

                switch (msgType) {
                    case 5:
                        handleBitfield(payload);
                        break;
                    default:
                        System.out.println("Received message type " + msgType + " from peer " + remotePeerId);
                }
            }

        } catch (EOFException eof) {
            System.out.println("Connection closed by peer " + remotePeerId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (din != null) din.close();
                if (dout != null) dout.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
