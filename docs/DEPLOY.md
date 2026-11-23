# Deploy

O WatchMatch roda numa VM com o mesmo Docker Compose de casa, mais um Caddy na frente para o HTTPS.

```
internet ──443──► Caddy (HTTPS) ──► gateway (Nginx, rate limit) ──► frontend e serviços
```

| Peça | Escolha | Por quê |
| ---- | ------- | ------- |
| Onde | VM no Azure for Students | US$ 100 de crédito pelo GitHub Student Pack, sem cartão. O Render grátis não serve, porque um serviço grátis não recebe tráfego da rede privada e dorme depois de 15 min. O Railway é pago depois de 30 dias |
| HTTPS | Caddy + nome grátis do DuckDNS | O Caddy pega o certificado do Let's Encrypt sozinho e renova sozinho |
| Atualizar | `deploy/deploy.sh` na VM | `git pull` + build + `up`, sem CI para configurar |

Arquivos: [docker-compose.prod.yml](../docker-compose.prod.yml), [deploy/Caddyfile](../deploy/Caddyfile),
[deploy/real-ip.conf](../deploy/real-ip.conf), [deploy/setup-vm.sh](../deploy/setup-vm.sh) e
[deploy/deploy.sh](../deploy/deploy.sh).

## O que muda na produção

- **Só o Caddy abre portas (80 e 443).** O gateway deixa de abrir a 8080, e o HTTP redireciona para HTTPS.
- **IP real no rate limit.** Atrás do Caddy, o gateway veria só o IP dele, e o site inteiro dividiria
  as 6 buscas por minuto. O `real-ip.conf` faz o Nginx usar o `X-Forwarded-For`, mas só vindo da rede
  interna do Compose. O Caddy descarta o `X-Forwarded-For` que o visitante mandar, então não dá para
  burlar o limite trocando esse cabeçalho.
- **Teto de memória por container.** Os três serviços Java, o Caddy e os dois Nginx somam até ~1,9 GB.
  Parado, tudo usa ~510 MB. A JVM pode usar até 75% do teto do container.
- **Logs giram** (3 arquivos de 10 MB por container) e os containers voltam sozinhos se a VM reiniciar.

## Passo a passo

### 1. Conta e VM no Azure

1. Ative o Azure for Students em <https://azure.microsoft.com/free/students> com o e-mail da FAESA (ou
   pelo GitHub Student Pack).
2. No portal, crie uma **máquina virtual**:
   - imagem **Ubuntu Server 24.04 LTS**;
   - tamanho **B2s** (2 vCPU, 4 GB), ou outro com 4 GB. Com 2 GB até roda, mas o build fica lento e
     depende do swap;
   - autenticação por **chave SSH** (o portal gera e baixa a chave);
   - portas de entrada: **SSH (22), HTTP (80) e HTTPS (443)**;
   - em Rede, deixe o **IP público como Estático**, para o endereço não mudar quando a VM reiniciar.
3. Anote o IP público da VM.

Confira o preço do tamanho escolhido na tela de criação: a região muda o valor, e o Brazil South
costuma ser mais caro que o East US. O crédito aparece em "Cost Management".

### 2. Nome com HTTPS (DuckDNS)

1. Entre em <https://www.duckdns.org> e crie um subdomínio, por exemplo `watchmatch`.
2. Em "current ip", coloque o IP público da VM e clique em "update ip".

O site vai ficar em `https://watchmatch.duckdns.org`. Como o IP é estático, não precisa atualizar de novo.

### 3. Preparar a VM (uma vez só)

```bash
ssh -i chave-baixada.pem azureuser@IP-DA-VM
```

```bash
git clone https://github.com/LucasBinda/notComplete.git && cd notComplete
```

```bash
sudo deploy/setup-vm.sh
```

Esse script instala o Docker, cria 2 GB de swap e libera só as portas 22, 80 e 443. Depois saia do SSH
e entre de novo, para o Docker funcionar sem `sudo`.

### 4. Chaves e domínio (uma vez só)

As chaves ficam só na VM e nunca vão para o Git:

```bash
cp backend/.env.example backend/.env && nano backend/.env
```

```bash
echo DOMAIN=watchmatch.duckdns.org > .env
```

### 5. Subir

```bash
deploy/deploy.sh
```

Na primeira vez, o build leva uns 10 minutos. O Caddy pede o certificado assim que sobe, e o Let's
Encrypt precisa achar o domínio apontando para a VM, por isso o DuckDNS vem antes. Para conferir:

```bash
curl -I https://watchmatch.duckdns.org/health
```

### Atualizar depois

Faça o push para a `main` no seu computador e, na VM:

```bash
cd notComplete && deploy/deploy.sh
```

### Ver o que está acontecendo

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail 50 recommendation-service
```

```bash
docker stats --no-stream
```

## Testar a produção em casa

O `DOMAIN=localhost` faz o Caddy usar um certificado próprio (o navegador avisa que não é confiável).
Derrube antes o Compose de casa, porque a rede da produção usa outra faixa de IP:

```bash
docker compose down
```

```bash
DOMAIN=localhost docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --wait
```

Abra <https://localhost>. Para voltar ao de casa, rode `down` com os dois arquivos e depois `docker compose up -d`.

Testado assim em 23/11/2026, com estes resultados:
- HTTPS 200, e o HTTP redireciona com 308;
- a 8080 está fechada;
- o `X-Forwarded-For` falso foi ignorado, e o rate limit recusou a 6ª busca seguida;
- uma busca de ponta a ponta voltou com 12 títulos e as justificativas da IA.

## Plano B: Railway

Se o Azure for Students não sair, o Railway roda os mesmos Dockerfiles. Cada pasta vira um serviço, e os
nomes internos (`catalog-service`, `llm-service`, `recommendation-service`, `frontend`) precisam bater com
os do Compose. O próprio Railway faz o HTTPS, então o Caddy sai. O `real-ip.conf` precisa confiar na faixa
do proxy deles. O Railway é pago depois dos 30 dias de teste (US$ 5 por mês mais o uso).
