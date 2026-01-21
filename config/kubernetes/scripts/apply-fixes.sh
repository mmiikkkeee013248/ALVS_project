#!/bin/bash
# Phase 4: Apply permanent hardware/resource adjustments
# Run this after identifying the root cause

set -e

echo "=== Phase 4: Applying Fixes ==="
echo ""

# Detect the issue type
echo "Please review the diagnosis output and select the fix:"
echo ""
echo "1. Disk IO is high (await > 50ms in iostat)"
echo "2. CPU is high (load average > CPU count)"
echo "3. Both"
echo ""

read -p "Enter fix number (1-3) or 'auto' for automatic detection: " FIX_TYPE

if [ "$FIX_TYPE" = "auto" ]; then
    # Auto-detect
    if command -v iostat >/dev/null 2>&1; then
        AWAIT=$(iostat -x 1 2 | tail -1 | awk '{print $10}')
        if [ -n "$AWAIT" ] && (( $(echo "$AWAIT > 50" | bc -l) )); then
            FIX_TYPE="1"
        else
            FIX_TYPE="2"
        fi
    else
        FIX_TYPE="2"
    fi
fi

case $FIX_TYPE in
    1)
        echo "=== Fix 4.1: Reducing Disk IO Pressure ==="
        
        # Limit journald log size
        echo "Limiting systemd-journald log size to 500MB..."
        sudo sed -i 's/#SystemMaxUse=/SystemMaxUse=500M/' /etc/systemd/journald.conf
        sudo sed -i 's/SystemMaxUse=.*/SystemMaxUse=500M/' /etc/systemd/journald.conf || true
        
        # Restart journald
        sudo systemctl restart systemd-journald && echo "✓ Journald restarted"
        
        # Set high IO priority for etcd
        ETCD_PID=$(pgrep -f etcd || echo "")
        if [ -n "$ETCD_PID" ]; then
            echo "Setting high IO priority for etcd (PID: $ETCD_PID)..."
            sudo ionice -c2 -n0 -p $ETCD_PID && echo "✓ etcd IO priority set"
        fi
        
        echo ""
        echo "Recommendation: Move VM to SSD if possible"
        echo "VirtualBox: Settings → General → Default Machine Folder → point to SSD"
        ;;
    
    2)
        echo "=== Fix 4.2: Reducing CPU Pressure ==="
        
        # Further limit API server if not already done
        echo "Checking API server limits..."
        if ! sudo grep -q "max-requests-inflight=400" /etc/kubernetes/manifests/kube-apiserver.yaml; then
            echo "Reducing API server limits further..."
            sudo sed -i 's/--max-requests-inflight=800/--max-requests-inflight=400/' /etc/kubernetes/manifests/kube-apiserver.yaml
            sudo sed -i 's/--max-mutating-requests-inflight=400/--max-mutating-requests-inflight=200/' /etc/kubernetes/manifests/kube-apiserver.yaml
            echo "✓ API server limits reduced"
            echo "API server will restart automatically in ~1 minute"
        else
            echo "API server limits already at minimum"
        fi
        
        echo ""
        echo "Recommendation: Increase CPU cores in VirtualBox"
        echo "VirtualBox: System → Processor → increase to 6-8 cores (if host allows)"
        ;;
    
    3)
        echo "=== Fix 4.3: Applying Both Fixes ==="
        # Apply disk IO fix
        echo "Limiting systemd-journald log size to 500MB..."
        sudo sed -i 's/#SystemMaxUse=/SystemMaxUse=500M/' /etc/systemd/journald.conf
        sudo sed -i 's/SystemMaxUse=.*/SystemMaxUse=500M/' /etc/systemd/journald.conf || true
        sudo systemctl restart systemd-journald && echo "✓ Journald restarted"
        
        ETCD_PID=$(pgrep -f etcd || echo "")
        if [ -n "$ETCD_PID" ]; then
            sudo ionice -c2 -n0 -p $ETCD_PID && echo "✓ etcd IO priority set"
        fi
        
        # Apply CPU fix
        if ! sudo grep -q "max-requests-inflight=400" /etc/kubernetes/manifests/kube-apiserver.yaml; then
            sudo sed -i 's/--max-requests-inflight=800/--max-requests-inflight=400/' /etc/kubernetes/manifests/kube-apiserver.yaml
            sudo sed -i 's/--max-mutating-requests-inflight=400/--max-mutating-requests-inflight=200/' /etc/kubernetes/manifests/kube-apiserver.yaml
            echo "✓ API server limits reduced"
        fi
        ;;
    
    *)
        echo "Invalid option. Exiting."
        exit 1
        ;;
esac

echo ""
echo "=== Verification ==="
echo "Waiting 30 seconds for changes to take effect..."
sleep 30

echo "Current system state:"
uptime
free -h | head -2

if command -v iostat >/dev/null 2>&1; then
    echo ""
    echo "Disk IO (1 second sample):"
    iostat -x 1 1 | tail -2
fi

echo ""
echo "=== Fix Applied ==="
echo "Monitor the system for the next few minutes."
echo "If lockups continue, consider:"
echo "1. Increasing VM resources (CPU/RAM)"
echo "2. Moving VM to faster storage (SSD)"
echo "3. Reducing number of Kubernetes workloads"
