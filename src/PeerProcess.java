import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PeerProcess {
    private int localPeerId;
    private Config config;
    private List<PeerInfo> peerInfoList = new ArrayList<>();
    // Maintain a map of connections (peerID -> ConnectionHandler)
    private Map<Integer, ConnectionHandler> connectionMap = new ConcurrentHashMap<>();

    public PeerProcess(int localPeerId) {
        this.localPeerId = localPeerId;
    }

    public void start() {
        try {
            // Step 2: Read configuration files
            config = new Config("Common.cfg");
            readPeerInfo("PeerInfo.cfg");

            // Step 4: Start the server thread for incoming connections
            new Thread(this::startServer).start();

            // Optional pause to ensure the server has started
            Thread.sleep(1000);

            // Step 4: Initiate connections to peers with lower IDs
            initiateConnections();

            System.out.println("Peer " + localPeerId + " setup complete.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Read PeerInfo.cfg and populate the list.
    private void readPeerInfo(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            PeerInfo pi = PeerInfo.parse(line);
            peerInfoList.add(pi);
        }
        br.close();
    }

    // Server-side: Listen for incoming connections.
    private void startServer() {
        int myPort = 0;
        for (PeerInfo pi : peerInfoList) {
            if (pi.peerId == localPeerId) {
                myPort = pi.port;
                break;
            }
        }
        try (ServerSocket serverSocket = new ServerSocket(myPort)) {
            System.out.println("Peer " + localPeerId + " listening on port " + myPort);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted connection from " + socket.getInetAddress());
                ConnectionHandler handler = new ConnectionHandler(socket, localPeerId);
                // For a complete implementation, you may add the handler to a map after handshake.
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Client-side: Connect to peers with lower peer IDs.
    private void initiateConnections() {
        for (PeerInfo pi : peerInfoList) {
            if (pi.peerId < localPeerId) {
                try {
                    Socket socket = new Socket(pi.hostName, pi.port);
                    System.out.println("Peer " + localPeerId + " connected to peer " + pi.peerId);
                    ConnectionHandler handler = new ConnectionHandler(socket, localPeerId);
                    connectionMap.put(pi.peerId, handler);
                    new Thread(handler).start();
                } catch (IOException e) {
                    System.out.println("Failed to connect to peer " + pi.peerId);
                    e.printStackTrace();
                }
            }
        }
    }

    // Main method to launch the peer process.
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java PeerProcess <peerId>");
            System.exit(1);
        }
        int peerId = Integer.parseInt(args[0]);
        PeerProcess peer = new PeerProcess(peerId);
        peer.start();
    }
}
