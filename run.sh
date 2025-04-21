#!/bin/bash
osascript -e 'tell app "Terminal" to do script "java PeerProcess 1001"'
osascript -e 'tell app "Terminal" to do script "java PeerProcess 1002"'
osascript -e 'tell app "Terminal" to do script "java PeerProcess 1003"'
echo "✅ Peers are running in new Terminal windows."