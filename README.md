
# CN-Project

A fully functional **Peer-to-Peer (P2P) File Sharing Network** implemented in Java. This project simulates core concepts of decentralized communication, inspired by BitTorrent protocols, as part of a Computer Networks academic assignment.

## Overview
This project models a P2P network where each peer can download and upload parts of a file without relying on a centralized server. The system demonstrates real-world P2P principles such as piece distribution, peer coordination, and efficient bandwidth usage through dynamic choking algorithms.

## 📽️ Demonstration
For this demo, we configured peers to operate over the same LAN network. Our team procured multiple laptops to simulate a real P2P environment, ensuring each peer ran on a distinct IP address as defined in PeerInfo.cfg.
The video of the project demonstration can be found at the following link:

[Project Demo Video](https://drive.google.com/drive/folders/1n_BupHMKabBekRX1SMdH-wcxgDFycsj1?usp=sharing)

## Implementation Status
Fully functional as per provided specifications.

## Features
- **Decentralized Peer Communication**
- **Handshake & Custom Messaging Protocol**
- **Bitfield Exchange for Piece Awareness**
- **Choke/Unchoke & Optimistic Unchoking Mechanism**
- **File Segmentation, Transfer, and Reassembly**
- **Dynamic Preferred Neighbors Selection**
- **Thread-safe Concurrent Connections**
- **Spec-Compliant Logging of All Events**
- **Graceful Shutdown Upon Completion**

## Project Structure
```
CN-Project/
├── peer_1001/ ... peer_1006/   # Peer-specific directories
├── bin/                   # Compiled .class files
├── src/                   # Java source files
├── Common.cfg             # Global configuration
├── PeerInfo.cfg           # Peer setup details
├── compile.sh             # Script to compile Java code
├── run.sh                 # Script to launch peers
├── log_peer_100X.log      # Logs per peer
├── README.md              # Project guide
└── Project_Report.pdf     # Detailed documentation
```

## Configuration Files
### `Common.cfg`
Defines system-wide settings:
- `NumberOfPreferredNeighbors`
- `UnchokingInterval`
- `OptimisticUnchokingInterval`
- `FileName`, `FileSize`, `PieceSize`

### `PeerInfo.cfg`
Lists all peers in the network:
```
<PeerID> <Address> <Port> <HasFile>
```
Example:
```
1001 192.168.0.48 6009 1
1002 192.168.0.49 6009 0
1003 192.168.0.52 6009 0
1004 192.168.0.53 6009 0
1005 192.168.0.54 6009 0
1006 192.168.0.55 6009 0
```

## Setup & Compilation
### Prerequisites
- Java 8 or higher
- Unix-based OS (macOS/Linux preferred)

### Compile the Project
```bash
bash compile.sh
```
This compiles all `.java` files from `src/` into `bin/`.

## Running the Application
We ran the same command simulataneously on all computers, this is an example:
```bash
 java -cp bin PeerProcess 1001
```
Each peer process will initiate connections, exchange messages, and begin file piece sharing automatically.

## Logging
Each peer generates a log file:
```
log_peer_<PeerID>.log
```
These logs capture:
- Peer connections & disconnections
- Choke/Unchoke events
- Piece requests and receptions
- Completion notifications

## Technical Highlights
- **Custom Protocol Implementation**: Handshake, Bitfield, Have, Interested, Choke, Unchoke messages.
- **Dynamic Neighbor Selection**: Based on download rates.
- **Optimistic Unchoking**: Ensures fairness across peers.
- **Multithreading**: Handles multiple simultaneous peer connections.
- **File Handling**: Splitting, tracking missing pieces, and reassembling upon completion.

## Project Goals
- Demonstrate understanding of P2P architectures.
- Implement core networking protocols using Java sockets.
- Apply concurrency principles in a networked environment.
- Simulate real-world file sharing dynamics.

## Potential Improvements
- Implement **file integrity checks** using hash validation.
- Add **dynamic peer discovery** instead of static configs.
- Enhance performance with smarter bandwidth allocation algorithms.
- Introduce a basic **GUI or monitoring dashboard**.

## License
This project is developed for academic purposes under university guidelines.

## Acknowledgments
Developed as part of the Computer Networks coursework. The design follows standard academic P2P specifications with customized enhancements.

## Contributors
**Ram Pandey**  
**Chetan Reddy Ramini**  
**Sonali Karneedi**

This project was collaboratively developed as part of the Computer Networks course to demonstrate key P2P networking concepts through a Java-based implementation.
