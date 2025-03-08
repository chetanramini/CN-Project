from peer import Peer

# Create Peer 1002 (which connects to Peer 1001 automatically)
peer = Peer(peer_id=1002, port=6002, connect_to=("localhost", 6001))

# Start connection (no arguments needed)
peer.connect_to_peer()
