# 0007. API em VPS com Docker e Caddy; painel na Vercel

- Status: aceito
- Data: 2026-11-10

## Contexto

A API é uma aplicação Spring Boot de longa duração: mantém conexões SSE abertas e roda um relay a cada 500 ms. Isso não combina com plataformas serverless, que além disso não rodam Java nativamente. O orçamento é o de um projeto pessoal, e o mesmo servidor deve hospedar os próximos projetos.

## Decisão

- **API e Postgres** numa VPS Ubuntu da Hostinger, em Docker Compose, com o **Caddy** na frente emitindo HTTPS automaticamente. Só o Caddy publica portas.
- **Painel** exportado como site estático e publicado na **Vercel**, que dá previews por pull request sem custo.
- **Deploy** pelo GitHub Actions: imagem no GHCR, SSH na VPS, `docker compose up -d` e espera do `readiness`.
- Limites de memória por container; a JVM usa `MaxRAMPercentage` para respeitar o limite.

## Alternativas

- **PaaS (Render, Railway, Fly.io)**: menos operação, mas planos gratuitos dormem (cold start ruim para Spring Boot) e o custo cresce por serviço.
- **AWS (ECS/Fargate + RDS)**: é o que uso no trabalho, com Terraform, mas custa várias vezes mais que uma VPS para uma demo.
- **Kubernetes (k3s)**: interessante, desproporcional para um nó só.

## Consequências

- Custo fixo e baixo, e a VPS comporta os próximos projetos (um bloco no `Caddyfile` e um serviço no compose cada).
- A operação é minha: atualizações de segurança automáticas, backup diário com `pg_dump`, firewall e SSH só com chave estão no `bootstrap.sh` e no `backup.sh`.
- Um nó só: se a VPS cair, a API cai. Aceitável para uma demonstração.
