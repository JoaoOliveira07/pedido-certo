#!/usr/bin/env bash
# Prepara uma VPS Ubuntu 24.04 recém-criada (Hostinger ou qualquer outra) para receber o deploy.
# Rode uma vez, como root:   curl -fsSL <raw deste arquivo> | bash -s -- deploy
#
# O que faz: cria o usuário de deploy com a sua chave SSH, desliga login por senha e de root,
# liga firewall (só 22, 80 e 443), fail2ban, atualizações automáticas de segurança, swap e Docker.
set -euo pipefail

DEPLOY_USER="${1:-deploy}"
APP_DIR=/opt/jopes

if [[ $EUID -ne 0 ]]; then
  echo "Rode como root." >&2
  exit 1
fi
if [[ ! -s /root/.ssh/authorized_keys ]]; then
  echo "Nenhuma chave SSH em /root/.ssh/authorized_keys. Cadastre a sua no painel da Hostinger antes," >&2
  echo "senão você fica trancado para fora quando o login por senha for desligado." >&2
  exit 1
fi

echo "==> Pacotes"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ca-certificates curl ufw fail2ban unattended-upgrades

echo "==> Usuário $DEPLOY_USER"
if ! id "$DEPLOY_USER" &>/dev/null; then
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
install -m 600 -o "$DEPLOY_USER" -g "$DEPLOY_USER" /root/.ssh/authorized_keys "/home/$DEPLOY_USER/.ssh/authorized_keys"

echo "==> SSH só com chave, sem root"
cat > /etc/ssh/sshd_config.d/10-hardening.conf <<CONF
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
AllowUsers $DEPLOY_USER
CONF
sshd -t
systemctl reload ssh

echo "==> Firewall"
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> fail2ban e atualizações de segurança"
systemctl enable --now fail2ban
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Swap de 2 GB (folga para a JVM em VPS pequena)"
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo "/swapfile none swap sw 0 0" >> /etc/fstab
fi

echo "==> Docker"
if ! command -v docker &>/dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -yq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
usermod -aG docker "$DEPLOY_USER"

# Atenção: o Docker publica portas por fora do ufw. Por isso só o Caddy publica portas no compose.

echo "==> Pasta da aplicação"
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR" "$APP_DIR/backups"

echo
echo "Pronto. Próximos passos:"
echo "  1. Teste em outro terminal: ssh $DEPLOY_USER@<ip-da-vps>  (antes de fechar esta sessão)"
echo "  2. Crie $APP_DIR/.env a partir de infra/vps/.env.example"
echo "  3. Agende o backup: crontab -e  (como $DEPLOY_USER) e cole a linha do infra/vps/backup.sh"
