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
        PeerProcess.logger.log("Peer " + localPeerId + " is connected from Peer " + remotePeerId + ".");
    }

    public void sendBitfield() {
        byte[] bf = fileManager.getBitfieldBytes();
        sendMessage(new Message.BitfieldMessage(bf));
        PeerProcess.logger.log("Peer " + localPeerId + " sent Bitfield to Peer " + remotePeerId + ".");
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
            PeerProcess.logger.log("Peer " + localPeerId + " connection closed by Peer " + remotePeerId + ".");
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
                PeerProcess.logger.log("Peer " + localPeerId + " received Bitfield from Peer " + remotePeerId + ".");
                int miss = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (miss != -1) {
                    sendMessage(new Message.InterestedMessage());
                    isInterested = true;
                    PeerProcess.logger.log("Peer " + localPeerId + " sent Interested to Peer " + remotePeerId + ".");
                } else {
                    sendMessage(new Message.NotInterestedMessage());
                    isInterested = false;
                    PeerProcess.logger.log("Peer " + localPeerId + " sent NotInterested to Peer " + remotePeerId + ".");
                }

            } else if (msg instanceof Message.InterestedMessage) {
                isInterested = true;
                PeerProcess.logger.log("Peer " + localPeerId + " received Interested from Peer " + remotePeerId + ".");

            } else if (msg instanceof Message.NotInterestedMessage) {
                isInterested = false;
                PeerProcess.logger.log("Peer " + localPeerId + " received NotInterested from Peer " + remotePeerId + ".");

            } else if (msg instanceof Message.UnchokeMessage) {
                isUnchoked = true;
                PeerProcess.logger.log("Peer " + localPeerId + " is unchoked by Peer " + remotePeerId + ".");
                int next = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (next != -1) {
                    sendMessage(new Message.RequestMessage(next));
                    PeerProcess.logger.log("Peer " + localPeerId + " sent Request for piece " + next + " to Peer " + remotePeerId + ".");
                }

            } else if (msg instanceof Message.ChokeMessage) {
                isUnchoked = false;
                PeerProcess.logger.log("Peer " + localPeerId + " is choked by Peer " + remotePeerId + ".");

            } else if (msg instanceof Message.RequestMessage) {
                int idx = ((Message.RequestMessage) msg).pieceIndex;
                if (!isUnchoked) return;
                if (fileManager.getBitfield().get(idx)) {
                    byte[] data = readPieceFromLocalFile(idx);
                    if (data != null) {
                        sendMessage(new Message.PieceMessage(idx, data));
                        PeerProcess.logger.log("Peer " + localPeerId + " sent piece " + idx + " to Peer " + remotePeerId + ".");
                    }
                }

            } else if (msg instanceof Message.PieceMessage) {
                Message.PieceMessage pm = (Message.PieceMessage) msg;
                int idx = pm.pieceIndex;
                fileManager.writePiece(idx, pm.pieceData);
                downloadedBytes += pm.pieceData.length;
                PeerProcess.logger.log("Peer " + localPeerId + " has downloaded the piece " + idx + " from Peer " + remotePeerId +
                        ". Now the number of pieces it has is " + fileManager.getBitfield().cardinality() + ".");

                for (ConnectionHandler h : PeerProcess.handlers) {
                    if (h != this) {
                        h.sendMessage(new Message.HaveMessage(idx));
                        PeerProcess.logger.log("Peer " + localPeerId + " sent the ‘have’ message to Peer " + h.remotePeerId + " for piece " + idx + ".");
                    }
                }

                if (!fileManager.isComplete() && isUnchoked) {
                    int next = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                    if (next != -1) {
                        sendMessage(new Message.RequestMessage(next));
                        PeerProcess.logger.log("Peer " + localPeerId + " sent Request for piece " + next + " to Peer " + remotePeerId + ".");
                    }
                } else if (fileManager.isComplete()) {
                    PeerProcess.logger.log("Peer " + localPeerId + " has downloaded the complete file.");
                }

            } else if (msg instanceof Message.HaveMessage) {
                int pieceIndex = ((Message.HaveMessage) msg).pieceIndex;
                remoteBitfield.set(pieceIndex);
                PeerProcess.logger.log("Peer " + localPeerId + " received the ‘have’ message from Peer " + remotePeerId + " for the piece " + pieceIndex + ".");
                int missing = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (missing != -1) {
                    sendMessage(new Message.InterestedMessage());
                    isInterested = true;
                    PeerProcess.logger.log("Peer " + localPeerId + " sent Interested to Peer " + remotePeerId + ".");
                } else {
                    sendMessage(new Message.NotInterestedMessage());
                    isInterested = false;
                    PeerProcess.logger.log("Peer " + localPeerId + " sent NotInterested to Peer " + remotePeerId + ".");
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
        File f = new File("peer_" + localPeerId, "piece_" + pieceIndex + ".dat");
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
