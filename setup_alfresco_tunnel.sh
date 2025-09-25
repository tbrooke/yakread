#!/bin/bash

# Mount Zion Alfresco SSH Tunnel Setup
# This script sets up the SSH tunnel to access the Alfresco server

set -e

echo "🔗 Setting up SSH tunnel to Mount Zion Alfresco"
echo "=============================================="

# Configuration
REMOTE_HOST="tmb@trust"
LOCAL_PORT="8080"
REMOTE_ALFRESCO_HOST="generated-setup-alfresco-1"
REMOTE_ALFRESCO_PORT="8080"
TUNNEL_NAME="mtzion-alfresco-tunnel"

echo "📡 SSH Tunnel Configuration:"
echo "   Remote host: $REMOTE_HOST"
echo "   Local port: $LOCAL_PORT"
echo "   Remote Alfresco: $REMOTE_ALFRESCO_HOST:$REMOTE_ALFRESCO_PORT"

# Check if tunnel is already running
if pgrep -f "ssh.*$LOCAL_PORT:$REMOTE_ALFRESCO_HOST:$REMOTE_ALFRESCO_PORT.*$REMOTE_HOST" > /dev/null; then
    echo "⚠️  SSH tunnel already running on port $LOCAL_PORT"
    echo "   Use 'pkill -f \"ssh.*$LOCAL_PORT:$REMOTE_ALFRESCO_HOST.*$REMOTE_HOST\"' to stop it"
    echo "   Or continue to use the existing tunnel"
else
    echo "🚀 Starting SSH tunnel..."
    # Start SSH tunnel in background
    # -f: go to background
    # -N: don't execute remote command
    # -L: local port forwarding
    ssh -f -N -L $LOCAL_PORT:$REMOTE_ALFRESCO_HOST:$REMOTE_ALFRESCO_PORT $REMOTE_HOST
    
    echo "✅ SSH tunnel started successfully!"
fi

echo ""
echo "🔧 Tunnel Status:"
echo "   Local Alfresco URL: http://localhost:$LOCAL_PORT"
echo "   Admin credentials: admin/admin (typical default)"

# Wait a moment for tunnel to establish
sleep 2

echo ""
echo "🧪 Testing connection to Alfresco through tunnel..."

# Test if Alfresco is responding through the tunnel
if curl -s --connect-timeout 10 "http://localhost:$LOCAL_PORT/alfresco" > /dev/null; then
    echo "✅ Alfresco accessible through tunnel at http://localhost:$LOCAL_PORT"
else
    echo "❌ Cannot reach Alfresco through tunnel"
    echo "   Check that:"
    echo "   1. SSH tunnel is working: ssh $REMOTE_HOST"
    echo "   2. Alfresco is running on the remote server"
    echo "   3. Docker container 'generated-setup-alfresco-1' is running"
fi

echo ""
echo "🎯 Next Steps:"
echo "   1. Update your Alfresco config to use: http://localhost:$LOCAL_PORT"
echo "   2. Test the route-driven system: bb test_alfresco_connection.clj"  
echo "   3. Enable dynamic routes when ready"
echo ""
echo "To stop the tunnel later:"
echo "   pkill -f \"ssh.*$LOCAL_PORT:$REMOTE_ALFRESCO_HOST.*$REMOTE_HOST\""