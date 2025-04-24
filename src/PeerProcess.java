import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PeerProcess {
    private int localPeerId;
    private Config config;
    private List<PeerInfo> peerInfoList = new ArrayList<>();
    // Use a thread-safe list for all handlers
    public static List<ConnectionHandler> handlers = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;   // Track the server socket
    private FileManager fileManager;

    public PeerProcess(int localPeerId) {
        this.localPeerId = localPeerId;
    }

    public void start() {
        try {
            // 1. Load configuration
            config = new Config("Common.cfg");
            readPeerInfo("PeerInfo.cfg");

            // 2. Initialize FileManager
            boolean hasFile = peerInfoList.stream()
                .filter(pi -> pi.peerId == localPeerId)
                .findFirst()
                .get()
                .hasFile;
            fileManager = new FileManager(
                String.valueOf(localPeerId),
                config.fileName,
                config.fileSize,
                config.pieceSize,
                hasFile
            );

            // 3. Start listening for incoming connections
            new Thread(this::startServer).start();
            Thread.sleep(1000);

            // 4. Initiate outgoing connections to lower‐ID peers
            initiateConnections();

            // 5. Schedule random choke/unchoke every 3 seconds
            scheduleChokingTasks();
            scheduleOptimisticUnchoking();

            monitorSeederShutdown();

            setupShutdownHook();

            Logger.log(localPeerId, "Peer " + localPeerId + " setup complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readPeerInfo(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    peerInfoList.add(PeerInfo.parse(line));
                }
            }
        }
    }

    private void startServer() {
        int myPort = peerInfoList.stream()
            .filter(pi -> pi.peerId == localPeerId)
            .findFirst().get().port;
    
        try {
            serverSocket = new ServerSocket(myPort);
            Logger.log(localPeerId, "Peer " + localPeerId + " is listening on port " + myPort + ".");
            try {
                while (true) {
                    Socket sock = serverSocket.accept();
                    ConnectionHandler handler = new ConnectionHandler(sock, localPeerId, fileManager);
                    handlers.add(handler);
                    new Thread(handler).start();
                }
            } catch (SocketException e) {
                Logger.log(localPeerId, "Server socket closed, no longer accepting connections.");
            } catch (IOException e) {
                Logger.log(localPeerId, "IOException in server socket: " + e.getMessage());
                e.printStackTrace();
            }            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }    

    private void initiateConnections() {
        for (PeerInfo pi : peerInfoList) {
            if (pi.peerId < localPeerId) {
                try {
                    Socket sock = new Socket(pi.hostName, pi.port);
                    ConnectionHandler handler = new ConnectionHandler(sock, localPeerId, fileManager);
                    handlers.add(handler);
                    new Thread(handler).start();
                } catch (IOException e) {
                    Logger.log(localPeerId, "Peer " + localPeerId + " failed to connect to peer " + pi.peerId + ".");
                }
            }
        }
    }

    private void scheduleChokingTasks() {
        // Optional cleanup: Only allow seeder peers to run choking logic
        if (!fileManager.isComplete()) return;

        ScheduledExecutorService svc = Executors.newScheduledThreadPool(1);
        final int p = config.unchokingInterval;
        final int k = config.numberOfPreferredNeighbors;
    
        svc.scheduleAtFixedRate(() -> {
            // 1. Get all interested peers
            List<ConnectionHandler> interested = handlers.stream()
                .filter(h -> h.isInterested)
                .collect(Collectors.toList());
    
            // 2. Sort by download rate (descending)
            interested.sort((h1, h2) -> Integer.compare(h2.downloadedBytes, h1.downloadedBytes));
    
            // 3. Pick top k preferred neighbors
            List<ConnectionHandler> preferred = interested.subList(0, Math.min(k, interested.size()));
    
            // 4. Log selection
            String prefIds = preferred.stream()
                .map(h -> String.valueOf(h.remotePeerId))
                .collect(Collectors.joining(","));
                Logger.log(localPeerId, "Peer " + localPeerId + " has the preferred neighbors " + prefIds + ".");
    
            // 5. Send choke/unchoke
            for (ConnectionHandler h : handlers) {
                if (preferred.contains(h)) {
                    if (!h.isUnchoked || h.isOptimistic) {
                        h.sendMessage(new Message.UnchokeMessage());
                        h.isUnchoked = true;
                        h.isOptimistic = false;
                        Logger.log(localPeerId, "Peer " + h.remotePeerId + " is unchoked by " + localPeerId + ".");
                    }
                } else {
                    if (h.isUnchoked && !h.isOptimistic) {
                        h.sendMessage(new Message.ChokeMessage());
                        h.isUnchoked = false;
                        Logger.log(localPeerId, "Peer " + h.remotePeerId + " is choked by " + localPeerId + ".");
                    }
                }
    
                // 6. Reset download counters
                h.downloadedBytes = 0;
            }
        }, 0, p, TimeUnit.SECONDS);
    }
    

    public static void main(String[] args) {
        if (args.length != 1) {
            Logger.log(0, "Usage: java PeerProcess <peerId>"); // Use 0 since peerId is unknown at this point
            System.exit(1);
        }
        new PeerProcess(Integer.parseInt(args[0])).start();
    }

    private void scheduleOptimisticUnchoking() {
        ScheduledExecutorService svc = Executors.newScheduledThreadPool(1);
        final int m = config.optimisticUnchokingInterval;
    
        svc.scheduleAtFixedRate(() -> {
            // Filter for peers that are interested but choked (and not already optimistic)
            List<ConnectionHandler> candidates = handlers.stream()
                .filter(h -> h.isInterested && !h.isUnchoked && !h.isOptimistic)
                .collect(Collectors.toList());
    
            if (candidates.isEmpty()) return;
    
            // Pick one at random
            ConnectionHandler chosen = candidates.get(new Random().nextInt(candidates.size()));
            if (!chosen.isSocketClosed()) {
                chosen.sendMessage(new Message.UnchokeMessage());
                chosen.isUnchoked = true;
                chosen.isOptimistic = true;
                Logger.log(localPeerId, "Peer " + localPeerId + " has the optimistically unchoked neighbor " + chosen.remotePeerId + ".");
            }
            
        }, 0, m, TimeUnit.SECONDS);
    }    

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.log(localPeerId, "Shutdown initiated for peer " + localPeerId + ".");
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    Logger.log(localPeerId, "Server socket closed.");
                }
            } catch (IOException e) {
                Logger.log(localPeerId, "Error while closing server socket.");
                e.printStackTrace();
            }
    
            for (ConnectionHandler handler : handlers) {
                handler.closeConnection();
            }
            Logger.log(localPeerId, "All connections closed for peer " + localPeerId + ".");
        }));
    } 
    
    private void monitorSeederShutdown() {
        if (!fileManager.isComplete()) return;  // Only for seeders
    
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(5000);  // Check every 5 seconds
                    boolean activePeers = handlers.stream()
                        .anyMatch(h -> !h.isSocketClosed());
    
                    if (!activePeers) {
                        Logger.log(localPeerId, "Seeder peer " + localPeerId + " detected all peers disconnected. Shutting down...");
                        System.exit(0);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }    
}

