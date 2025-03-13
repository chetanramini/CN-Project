import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PeerProcess {
    private int localPeerId;
    private Config config;
    private List<PeerInfo> peerInfoList = new ArrayList<>();
    private Map<Integer, ConnectionHandler> connectionMap = new ConcurrentHashMap<>();
    private FileManager fileManager;

    public PeerProcess(int localPeerId) {
        this.localPeerId = localPeerId;
    }

    public void start() {
        try {
            // Step 2: Read configuration files
            config = new Config("Common.cfg");
            readPeerInfo("PeerInfo.cfg");

            // Initialize FileManager based on whether this peer has the complete file.
            boolean hasFile = false;
            for (PeerInfo pi : peerInfoList) {
                if (pi.peerId == localPeerId) {
                    hasFile = pi.hasFile;
                    break;
                }
            }

            // ✅ FIXED: Use correct folder naming (no "peer_" prefix)
            String peerFolder = String.valueOf(localPeerId);

            fileManager = new FileManager(peerFolder, config.fileName, config.fileSize, config.pieceSize, hasFile);

            // Step 4 (Server-Side): Start a server thread for incoming connections.
            new Thread(this::startServer).start();

            // Optional pause to ensure the server thread has started.
            Thread.sleep(1000);

            // Step 4 (Client-Side): Initiate connections to peers with lower peer IDs.
            initiateConnections();

            // Step 6: Schedule choking/unchoking tasks.
            scheduleChokingTasks();

            System.out.println("Peer " + localPeerId + " setup complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Reads PeerInfo.cfg and populates peerInfoList.
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

    // Server-side functionality: listens for incoming connections.
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
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Client-side functionality: connects to peers with lower peer IDs.
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

    // Step 6: Schedule periodic tasks for choking/unchoking mechanism.
    private void scheduleChokingTasks() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Task for selecting preferred neighbors every unchokingInterval seconds.
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Peer " + localPeerId + " selecting preferred neighbors...");
            // TODO: Compute download rates from each connection.
            // TODO: Select top config.numberOfPreferredNeighbors among interested peers.
            // TODO: Send unchoke messages to these peers and choke others as necessary.
        }, config.unchokingInterval, config.unchokingInterval, TimeUnit.SECONDS);

        // Task for optimistic unchoking every optimisticUnchokingInterval seconds.
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("Peer " + localPeerId + " selecting optimistic unchoked neighbor...");
            // TODO: Randomly select one peer from those that are choked but interested.
            // TODO: Send an unchoke message to that peer.
        }, config.optimisticUnchokingInterval, config.optimisticUnchokingInterval, TimeUnit.SECONDS);
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