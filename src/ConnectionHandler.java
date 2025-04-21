import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.BitSet;

public class ConnectionHandler implements Runnable {
    public int downloadedBytes = 0;  // Resets every interval
    private Socket socket;
    public int localPeerId;
    public int remotePeerId = -1;
    private DataInputStream din;
    private DataOutputStream dout;
    private FileManager fileManager;
    private BitSet remoteBitfield = new BitSet();

    // Choking/unchoking flags:
    public boolean isInterested = false;
    public boolean isUnchoked   = false;
    public boolean isOptimistic = false;

    public ConnectionHandler(Socket socket, int localPeerId, FileManager fileManager) {
        this.socket      = socket;
        this.localPeerId = localPeerId;
        this.fileManager = fileManager;
        try {
            din  = new DataInputStream(socket.getInputStream());
            dout = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendHandshake() throws IOException {
        dout.write(ProtocolUtils.createHandshakeMessage(localPeerId));
        dout.flush();
    }

    public void receiveHandshake() throws Exception {
        byte[] buf = new byte[ProtocolUtils.HANDSHAKE_MSG_LENGTH];
        din.readFully(buf);
        remotePeerId = ProtocolUtils.parseHandshakeMessage(buf);
        System.out.println("Handshake received from peer " + remotePeerId);
    }

    public void sendBitfield() {
        byte[] bf = fileManager.getBitfieldBytes();
        sendMessage(new Message.BitfieldMessage(bf));
        System.out.println("Sent bitfield to peer " + remotePeerId);
    }

    @Override
    public void run() {
        try {
            sendHandshake();
            receiveHandshake();
            sendBitfield();

            while (true) {
                int len = din.readInt();
                byte[] rest = new byte[len];
                din.readFully(rest);
                ByteBuffer bb = ByteBuffer.allocate(4 + len).putInt(len).put(rest);
                Message msg = Message.fromBytes(bb.array());
                processMessage(msg);
            }
        } catch (EOFException eof) {
            System.out.println("Connection closed by peer " + remotePeerId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (din  != null) din.close();
                if (dout != null) dout.close();
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void processMessage(Message msg) {
        try {
            if (msg instanceof Message.BitfieldMessage) {
                byte[] bf = msg.payload == null ? new byte[0] : msg.payload;
                remoteBitfield = BitSet.valueOf(bf);
                System.out.println("Received bitfield from peer " + remotePeerId);

                int miss = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (miss != -1) {
                    sendMessage(new Message.InterestedMessage());
                    isInterested = true;
                    System.out.println("Sent Interested to peer " + remotePeerId);
                } else {
                    sendMessage(new Message.NotInterestedMessage());
                    isInterested = false;
                    System.out.println("Sent NotInterested to peer " + remotePeerId);
                }

            } else if (msg instanceof Message.InterestedMessage) {
                isInterested = true;

            } else if (msg instanceof Message.NotInterestedMessage) {
                isInterested = false;

            } else if (msg instanceof Message.UnchokeMessage) {
                isUnchoked = true;
                System.out.println("Received Unchoke from peer " + remotePeerId);
                // ALWAYS request next piece when unchoked
                int next = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (next != -1) {
                    sendMessage(new Message.RequestMessage(next));
                    System.out.println("Sent request for piece " + next + " after unchoke");
                }

            } else if (msg instanceof Message.ChokeMessage) {
                isUnchoked = false;
                System.out.println("Received Choke from peer " + remotePeerId);

            } else if (msg instanceof Message.RequestMessage) {
                int idx = ((Message.RequestMessage) msg).pieceIndex;
                if (!isUnchoked) {
                    System.out.println("Ignoring request for piece " + idx +
                                       " from " + remotePeerId + " (choked)");
                    return;
                }
                if (fileManager.getBitfield().get(idx)) {
                    byte[] data = readPieceFromLocalFile(idx);
                    if (data != null) {
                        sendMessage(new Message.PieceMessage(idx, data));
                        System.out.println("Sent piece " + idx + " to peer " + remotePeerId);
                    }
                }

            } else if (msg instanceof Message.PieceMessage) {
                Message.PieceMessage pm = (Message.PieceMessage) msg;
                int idx = pm.pieceIndex;
                System.out.println("Received piece " + idx + " from peer " + remotePeerId);
                fileManager.writePiece(idx, pm.pieceData);
                downloadedBytes += pm.pieceData.length;

                if (!fileManager.isComplete() && isUnchoked) {
                    int next = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                    if (next != -1) {
                        sendMessage(new Message.RequestMessage(next));
                        System.out.println("Sent request for piece " + next + " to peer " + remotePeerId);
                    }
                } else if (fileManager.isComplete()) {
                    System.out.println("Peer " + localPeerId + " has the complete file!");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(Message msg) {
        try {
            dout.write(msg.toBytes());
            dout.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getMissingPiece(BitSet remote, BitSet local) {
        for (int i = 0, total = fileManager.getNumberOfPieces(); i < total; i++) {
            if (remote.get(i) && !local.get(i)) return i;
        }
        return -1;
    }

    private byte[] readPieceFromLocalFile(int pieceIndex) {
        File f = new File(String.valueOf(localPeerId), "piece_" + pieceIndex + ".dat");
        if (!f.exists()) return null;
        try (InputStream is = new BufferedInputStream(new FileInputStream(f))) {
            byte[] data = new byte[(int) f.length()];
            is.read(data);
            return data;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
