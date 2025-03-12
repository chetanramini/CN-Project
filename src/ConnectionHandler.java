import java.io.*;
import java.net.*;

public class ConnectionHandler implements Runnable {
    private Socket socket;
    private int localPeerId;
    private int remotePeerId = -1;
    private DataInputStream din;
    private DataOutputStream dout;

    public ConnectionHandler(Socket socket, int localPeerId) {
        this.socket = socket;
        this.localPeerId = localPeerId;
        try {
            din = new DataInputStream(socket.getInputStream());
            dout = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Send the handshake message.
    public void sendHandshake() throws IOException {
        byte[] handshakeMsg = ProtocolUtils.createHandshakeMessage(localPeerId);
        dout.write(handshakeMsg);
        dout.flush();
    }

    // Receive and parse the handshake message.
    public void receiveHandshake() throws Exception {
        byte[] handshakeMsg = new byte[ProtocolUtils.HANDSHAKE_MSG_LENGTH];
        din.readFully(handshakeMsg);
        remotePeerId = ProtocolUtils.parseHandshakeMessage(handshakeMsg);
        System.out.println("Handshake received from peer " + remotePeerId);
    }

    @Override
    public void run() {
        try {
            // For simplicity, we send the handshake first then receive.
            sendHandshake();
            receiveHandshake();

            // After handshake, you can implement additional message exchange.
            while (true) {
                // Read the message length (4 bytes)
                int msgLength = din.readInt();
                // Read the message type (1 byte)
                byte msgType = din.readByte();
                // Read the payload (if any)
                byte[] payload = new byte[msgLength - 1];
                if (payload.length > 0) {
                    din.readFully(payload);
                }
                System.out.println("Received message type " + msgType + " from peer " + remotePeerId);
                // Process the message based on type...
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

    // Optional: A method to send messages.
    public void sendMessage(byte msgType, byte[] payload) throws IOException {
        int length = 1 + (payload == null ? 0 : payload.length);
        dout.writeInt(length);
        dout.writeByte(msgType);
        if (payload != null) {
            dout.write(payload);
        }
        dout.flush();
    }
}

