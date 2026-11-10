# Deploy

```
pedidos.jopes.com.br      → Vercel (painel, pasta web/)
api-pedidos.jopes.com.br  → VPS Hostinger → Caddy → API Spring Boot → Postgres
```

## 1. VPS

1. No painel da Hostinger, crie a VPS com **Ubuntu 24.04** e cadastre a sua chave SSH pública.
2. Entre como root e rode o bootstrap. Ele cria o usuário `deploy`, desliga login por senha, liga firewall, fail2ban, swap e Docker:
   ```bash
   ssh root@<ip-da-vps>
   curl -fsSL https://raw.githubusercontent.com/JoaoOliveira07/pedido-certo/main/infra/vps/bootstrap.sh | bash -s -- deploy
   ```
3. **Sem fechar a sessão de root**, teste em outro terminal: `ssh deploy@<ip-da-vps>`.
4. Como `deploy`, crie o `.env` com segredos de verdade:
   ```bash
   cd /opt/jopes
   curl -fsSLO https://raw.githubusercontent.com/JoaoOliveira07/pedido-certo/main/infra/vps/.env.example
   mv .env.example .env
   sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 24)/" .env
   sed -i "s/^PIX_WEBHOOK_SECRET=.*/PIX_WEBHOOK_SECRET=$(openssl rand -hex 32)/" .env
   chmod 600 .env
   ```
5. Agende o backup diário (`crontab -e`):
   ```
   15 3 * * * /opt/jopes/backup.sh >> /opt/jopes/backups/backup.log 2>&1
   ```

## 2. DNS na Hostinger

Em **Domínios → jopes.com.br → DNS / Nameservers**, mantenha os registros atuais do `@` e do `www` (que apontam para a Vercel) e adicione:

| Tipo | Nome | Valor | TTL |
|---|---|---|---|
| A | `api-pedidos` | IP da VPS | 3600 |
| CNAME | `pedidos` | o valor que a Vercel mostrar ao adicionar o domínio (normalmente `cname.vercel-dns.com`) | 3600 |

Opcional: um registro `A` com nome `*` para o IP da VPS. Assim qualquer API futura (`api-outro.jopes.com.br`) já chega na VPS sem mexer no DNS; os registros específicos (`www`, `pedidos`) continuam valendo por cima do curinga.

O Caddy só consegue emitir o certificado depois que o `api-pedidos` já resolve para a VPS. Confira com `dig +short api-pedidos.jopes.com.br`.

## 3. GitHub

1. **Settings → Secrets and variables → Actions**, crie:
   - `VPS_HOST`: IP da VPS
   - `VPS_USER`: `deploy`
   - `VPS_SSH_KEY`: uma chave privada **só para o deploy** (gere com `ssh-keygen -t ed25519 -f deploy_key -N ""` e adicione o `deploy_key.pub` em `/home/deploy/.ssh/authorized_keys`)
2. **Settings → Environments**, crie o ambiente `producao` (dá para exigir aprovação manual antes de cada deploy).
3. Depois do primeiro deploy, em **Packages → pedido-certo-api → Package settings**, mude a visibilidade para **Public**. Assim a VPS baixa a imagem sem precisar de login no registry.

## 4. Vercel

1. **Add New → Project**, importe o repositório e defina **Root Directory = `web`**.
2. Variável de ambiente: `NEXT_PUBLIC_API_URL = https://api-pedidos.jopes.com.br`.
3. **Settings → Domains**, adicione `pedidos.jopes.com.br` e copie o CNAME que ela mostrar para o DNS da Hostinger.

## Fluxo de cada push na `main`

1. **CI**: testes da API (unitários, arquitetura e integração com Postgres) e do painel.
2. **Deploy da API** (só se o CI passou): imagem no GHCR com a tag do commit; na VPS, baixa `compose.yml` e `Caddyfile` daquele commit, sobe a versão nova e espera o `readiness` responder `UP`.
3. **Vercel** publica o painel.

## Rollback

```bash
ssh deploy@<ip-da-vps>
cd /opt/jopes
sed -i 's/^API_TAG=.*/API_TAG=<tag-anterior>/' .env   # as tags são os hashes curtos dos commits
docker compose up -d pedido-certo-api
```

## Restaurar um backup

Em um banco vazio (por exemplo, depois de recriar o volume `pgdata`), antes de subir a API:

```bash
gunzip -c backups/pedidocerto-AAAA-MM-DD_HHMM.sql.gz | docker compose exec -T postgres psql -U pedidocerto -d pedidocerto
```
