import os

CONFIG_DIR = os.path.dirname(os.path.abspath(__file__))

def read_common_config():
    """Reads Common.cfg file and returns configurations as a dictionary."""
    config_path = os.path.join(CONFIG_DIR, "Common.cfg")
    config = {}
    with open(config_path, "r") as file:
        for line in file:
            key, value = line.strip().split()
            config[key] = int(value) if value.isdigit() else value
    return config

def read_peer_info():
    """Reads PeerInfo.cfg and returns a list of peer details."""
    config_path = os.path.join(CONFIG_DIR, "PeerInfo.cfg")
    peers = []
    with open(config_path, "r") as file:
        for line in file:
            parts = line.strip().split()
            peer_id, host, port, has_file = parts
            peers.append({
                "peer_id": int(peer_id),
                "host": host,
                "port": int(port),
                "has_file": bool(int(has_file))
            })
    return peers

# Testing
if __name__ == "__main__":
    print("Common Config:", read_common_config())
    print("Peer Info:", read_peer_info())
