#!/usr/bin/env bash
# Prepara uma VM Ubuntu 24.04 nova para o WatchMatch (docs/DEPLOY.md). Roda uma vez só, com sudo:
#   sudo deploy/setup-vm.sh
# Instala o Docker pelo repositório oficial, cria 2 GB de swap (o build Maven dos três serviços aperta
# a memória de uma VM de 4 GB) e deixa o firewall aberto só para SSH, HTTP e HTTPS.
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "Rode com sudo: sudo deploy/setup-vm.sh" >&2
    exit 1
fi
user="${SUDO_USER:-$USER}"

echo "==> Docker (repositório oficial: https://docs.docker.com/engine/install/ubuntu/)"
apt-get update -q
apt-get install -y -q ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
apt-get update -q
apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker "$user"

echo "==> Swap de 2 GB"
if ! swapon --show | grep -q /swapfile; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
    swapon /swapfile
    echo "/swapfile none swap sw 0 0" >> /etc/fstab
fi

echo "==> Firewall: só SSH, HTTP e HTTPS"
ufw allow OpenSSH >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443 >/dev/null
ufw --force enable >/dev/null

echo "==> Pronto. Saia e entre de novo no SSH para o usuário $user usar o Docker sem sudo."
