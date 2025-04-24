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
    private static volatile boolean shuttingDown = false;

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
        Logger.log(localPeerId, "Peer " + localPeerId + " is connected from peer " + remotePeerId + ".");
    }

    public void sendBitfield() {
        byte[] bf = fileManager.getBitfieldBytes();
        sendMessage(new Message.BitfieldMessage(bf));
        Logger.log(localPeerId, "Sent bitfield to peer " + remotePeerId + ".");
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
        } catch (SocketException | EOFException e) {
            Logger.log(localPeerId, "Connection closed by peer " + remotePeerId + ".");    
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
                Logger.log(localPeerId, "Received bitfield from peer " + remotePeerId + ".");

                int miss = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (miss != -1) {
                    sendMessage(new Message.InterestedMessage());
                    isInterested = true;
                    Logger.log(localPeerId, "Peer " + localPeerId + " sent 'interested' to " + remotePeerId + ".");
                } else {
                    sendMessage(new Message.NotInterestedMessage());
                    isInterested = false;
                    Logger.log(localPeerId, "Peer " + localPeerId + " sent 'not interested' to " + remotePeerId + ".");
                }

            } else if (msg instanceof Message.InterestedMessage) {
                isInterested = true;

            } else if (msg instanceof Message.NotInterestedMessage) {
                isInterested = false;

            } else if (msg instanceof Message.UnchokeMessage) {
                isUnchoked = true;
                Logger.log(localPeerId, "Peer " + localPeerId + " is unchoked by " + remotePeerId + ".");
                int next = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (next != -1) {
                    sendMessage(new Message.RequestMessage(next));
                    Logger.log(localPeerId, "Peer " + localPeerId + " requested piece " + next + " from peer " + remotePeerId + ".");
                }

            } else if (msg instanceof Message.ChokeMessage) {
                isUnchoked = false;
                Logger.log(localPeerId, "Peer " + localPeerId + " is choked by " + remotePeerId + ".");

            } else if (msg instanceof Message.RequestMessage) {
                int idx = ((Message.RequestMessage) msg).pieceIndex;
                if (!isUnchoked) {
                    Logger.log(localPeerId, "Ignored request for piece " + idx + " from peer " + remotePeerId + " (choked).");
                    return;
                }
                if (fileManager.getBitfield().get(idx)) {
                    byte[] data = readPieceFromLocalFile(idx);
                    if (data != null) {
                        sendMessage(new Message.PieceMessage(idx, data));
                        Logger.log(localPeerId, "Peer " + localPeerId + " sent piece " + idx + " to peer " + remotePeerId + ".");
                    }
                }

            } else if (msg instanceof Message.PieceMessage) {
                Message.PieceMessage pm = (Message.PieceMessage) msg;
                int idx = pm.pieceIndex;
                Logger.log(localPeerId, "Peer " + localPeerId + " has downloaded the piece " + idx + " from peer " + remotePeerId + ". Now the number of pieces it has is " + fileManager.getBitfield().cardinality() + ".");
                fileManager.writePiece(idx, pm.pieceData);
                downloadedBytes += pm.pieceData.length;

                // 🆕 Broadcast HaveMessage
                for (ConnectionHandler h : PeerProcess.handlers) {
                    if (h != this) {
                        h.sendMessage(new Message.HaveMessage(idx));
                        Logger.log(localPeerId, "Peer " + localPeerId + " sent 'have' message for piece " + idx + " to peer " + h.remotePeerId + ".");
                    }
                }

                if (!fileManager.isComplete() && isUnchoked) {
                    int next = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                    if (next != -1) {
                        sendMessage(new Message.RequestMessage(next));
                        Logger.log(localPeerId, "Peer " + localPeerId + " requested piece " + next + " from peer " + remotePeerId + ".");
                    }
                } else if (fileManager.isComplete() && !shuttingDown) {
                    shuttingDown = true;
                    Logger.log(localPeerId, "Peer " + localPeerId + " has downloaded the complete file.");
                    for (ConnectionHandler h : PeerProcess.handlers) {
                        h.sendMessage(new Message.CompleteMessage());
                    }
                    
                
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);  // Give time for logs/messages
                            Logger.log(localPeerId, "Peer " + localPeerId + " is shutting down.");
                            System.exit(0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }                
                
            } else if (msg instanceof Message.HaveMessage) {
                int pieceIndex = ((Message.HaveMessage) msg).pieceIndex;
                remoteBitfield.set(pieceIndex);
                Logger.log(localPeerId, "Peer " + localPeerId + " received the 'have' message from peer " + remotePeerId + " for the piece " + pieceIndex + ".");

                int missing = getMissingPiece(remoteBitfield, fileManager.getBitfield());
                if (missing != -1) {
                    sendMessage(new Message.InterestedMessage());
                    isInterested = true;
                    Logger.log(localPeerId, "Peer " + localPeerId + " sent 'interested' to " + remotePeerId + ".");
                } else {
                    sendMessage(new Message.NotInterestedMessage());
                    isInterested = false;
                    Logger.log(localPeerId, "Peer " + localPeerId + " sent 'not interested' to " + remotePeerId + ".");
                }
            } else if (msg instanceof Message.CompleteMessage) {
                PeerProcess.completedPeers.add(remotePeerId);
                Logger.log(localPeerId, "Peer " + localPeerId + " received COMPLETE message from peer " + remotePeerId + ".");
            
                // Check if we should shut down
                if (fileManager.isComplete() && PeerProcess.completedPeers.size() == PeerProcess.peerCount - 1 && !shuttingDown) {
                    shuttingDown = true;
                    Logger.log(localPeerId, "All peers have completed. Shutting down...");
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            System.exit(0);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(Message msg) {
        try {
            if (!isSocketClosed()) {
                dout.write(msg.toBytes());
                dout.flush();
            }
        } catch (SocketException ignored) {
            // ✅ Suppress completely — we know the socket is closed during shutdown
        } catch (IOException e) {
            // You can log it to file silently if needed:
            // Logger.log(localPeerId, "IOException sending message to " + remotePeerId + ": " + e.getMessage());
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
    
    public void closeConnection() {
        try {
            if (din != null) din.close();
            if (dout != null) dout.close();
            if (socket != null && !socket.isClosed()) socket.close();
            Logger.log(localPeerId, "Closed connection to peer " + remotePeerId + ".");
        } catch (IOException e) {
            Logger.log(localPeerId, "Error while closing connection to peer " + remotePeerId + ".");
            e.printStackTrace();
        }
    }    

    public boolean isSocketClosed() {
        return socket == null || socket.isClosed();
    }    
}
