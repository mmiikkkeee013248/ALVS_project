#!/bin/bash
# Скрипт для автоматического перезапуска Kubernetes кластера

set -e

MASTER_IP="192.168.56.10"
SSH_USER="mike_1111"
SUDO_PASSWORD="1111"

echo "=== Перезапуск Kubernetes кластера ==="

# Функция для выполнения команд на удаленной машине
remote_exec() {
    local host=$1
    shift
    ssh -o StrictHostKeyChecking=no ${SSH_USER}@${host} "echo ${SUDO_PASSWORD} | sudo -S $*"
}

# 1. Сброс кластера на всех узлах
echo "1. Сброс кластера на всех узлах..."
remote_exec ${MASTER_IP} "kubeadm reset -f"
remote_exec "192.168.56.11" "kubeadm reset -f"
remote_exec "192.168.56.12" "kubeadm reset -f"

# 2. Очистка на master
echo "2. Очистка на master узле..."
remote_exec ${MASTER_IP} "rm -rf /etc/cni/net.d /var/lib/etcd"
remote_exec ${MASTER_IP} "iptables -F && iptables -t nat -F"

# 3. Инициализация master
echo "3. Инициализация master узла..."
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} << 'EOF'
cd ~/ALVS_project/config/kubernetes/ansible
ansible-playbook 02-init-master.yml -e 'ansible_become_password=1111'
EOF

# 4. Настройка kubeconfig
echo "4. Настройка kubeconfig..."
remote_exec ${MASTER_IP} "cp /etc/kubernetes/admin.conf ~/.kube/config"
remote_exec ${MASTER_IP} "chown ${SSH_USER}:${SSH_USER} ~/.kube/config"

# 5. Установка CNI
echo "5. Установка CNI плагина (Flannel)..."
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} "kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml"

# 6. Ожидание готовности master
echo "6. Ожидание готовности master узла..."
sleep 30
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} "kubectl get nodes"

# 7. Присоединение workers
echo "7. Присоединение worker узлов..."
JOIN_CMD=$(ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} "kubeadm token create --print-join-command" | tail -1)
remote_exec "192.168.56.11" "${JOIN_CMD}"
remote_exec "192.168.56.12" "${JOIN_CMD}"

# 8. Ожидание готовности всех узлов
echo "8. Ожидание готовности всех узлов..."
sleep 20
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} "kubectl get nodes"

# 9. Установка provisioner
echo "9. Установка local-path-provisioner..."
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} "kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml"
sleep 20

# 10. Развертывание приложения
echo "10. Развертывание приложения..."
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} << 'EOF'
cd ~/ALVS_project/config/kubernetes/manifests
kubectl apply -f 00-namespace.yml
kubectl apply -f 01-postgres-secret.yml
kubectl apply -f 02-postgres-pvc.yml
kubectl apply -f 03-postgres-deployment.yml
kubectl apply -f 04-app-configmap.yml
kubectl apply -f 05-app-secret.yml
kubectl apply -f 06-app-deployment.yml
EOF

echo "=== Кластер перезапущен ==="
echo "Проверка статуса через 60 секунд..."
sleep 60
ssh -o StrictHostKeyChecking=no ${SSH_USER}@${MASTER_IP} "kubectl get pods -A"
