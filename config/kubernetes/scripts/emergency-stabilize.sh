#!/bin/bash
# Emergency stabilization script to run immediately after VM reboot
# Run this on k8s-master after reboot to prevent CPU lockups

set -e

echo "=== Emergency Stabilization Script ==="
echo "Stopping all workloads to reduce CPU pressure..."

# Stop all application workloads
kubectl scale deployment -n alvs --all --replicas=0 2>/dev/null || true
kubectl scale deployment -n monitoring --all --replicas=0 2>/dev/null || true
kubectl scale deployment -n local-path-storage --all --replicas=0 2>/dev/null || true

echo "Workloads stopped. Checking API server limits..."

# Verify API server has request limits
if ! sudo grep -q "max-requests-inflight" /etc/kubernetes/manifests/kube-apiserver.yaml; then
    echo "WARNING: API server limits not found. They should be applied via Ansible playbook."
fi

echo "=== System Resource Check ==="
echo "CPU Load:"
uptime

echo -e "\nMemory:"
free -h

echo -e "\nDisk IO (if iostat available):"
iostat -x 1 3 2>/dev/null || echo "iostat not installed. Install with: sudo apt-get install sysstat"

echo -e "\n=== Recent Kernel Messages ==="
sudo dmesg | tail -20

echo -e "\n=== Top CPU consumers ==="
ps aux --sort=-%cpu | head -10

echo -e "\n=== Done ==="
echo "If CPU load is still high, check VirtualBox settings and host resources."
