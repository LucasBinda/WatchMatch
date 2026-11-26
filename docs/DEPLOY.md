# Deploy

O WatchMatch roda no seu computador com o mesmo Docker Compose de casa, e um túnel grátis da Cloudflare o
publica na internet com HTTPS.

```
visitante ──HTTPS──► Cloudflare ◄──túnel── cloudflared ──► gateway (Nginx, rate limit) ──► frontend e serviços
                                            (no seu computador)
```

| Peça | Escolha | Por quê |
| ---- | ------- | ------- |
| Onde | O seu computador, com o Docker | Não deu para usar o Azure for Students. O Cloudflare Containers não tem plano grátis (exige o Workers Paid, de US$ 5 por mês), o Render grátis dorme depois de 15 min e não aceita tráfego da rede privada, e o Railway é pago depois de 30 dias |
| Publicar | Túnel rápido da Cloudflare (`cloudflared`) | Grátis e sem conta. O túnel sai do computador para a Cloudflare, então não abre porta no roteador. A Cloudflare cuida do HTTPS |
| Subir | `deploy/deploy.sh` | Faz o build, sobe tudo, mostra o endereço público e atualiza o link fixo |
| Link fixo | GitHub Pages: **https://lucasbinda.github.io/WatchMatch/** | O endereço do túnel muda a cada subida; este não. É uma página que redireciona para o endereço do dia |

Arquivos: [docker-compose.prod.yml](../docker-compose.prod.yml), [deploy/real-ip.conf](../deploy/real-ip.conf),
[deploy/deploy.sh](../deploy/deploy.sh) e [deploy/link.html](../deploy/link.html).

## O que muda na produção

- **Nenhuma porta aberta.** O gateway deixa de abrir a 8080. O único caminho de fora é o túnel.
- **Endereço sorteado, link fixo.** O túnel rápido ganha um endereço `https://<palavras>.trycloudflare.com`,
  que muda toda vez que o container do túnel reinicia. O `deploy.sh` publica esse endereço numa página da
  branch `gh-pages`, e o GitHub Pages a serve em **https://lucasbinda.github.io/WatchMatch/**, que
  redireciona para o endereço do dia. É esse link que vai para quem for avaliar. Antes de redirecionar, a
  página confere se o site responde: com o computador desligado, o túnel caído ou depois do `parar`, ela
  mostra "O WatchMatch está fora do ar agora" em vez da página de erro da Cloudflare.
- **IP real no rate limit.** Atrás do túnel, o gateway veria só o IP do cloudflared, e o site inteiro
  dividiria as 6 buscas por minuto. O `real-ip.conf` faz o Nginx usar o `CF-Connecting-IP`, que a
  Cloudflare preenche com o IP do visitante e sobrescreve se ele mandar um falso. O Nginx só aceita esse
  cabeçalho vindo da rede interna do Compose.
- **Teto de memória por container.** Os três serviços Java, o túnel e os dois Nginx somam até ~1,9 GB.
  A JVM pode usar até 75% do teto do container.
- **Logs giram** (3 arquivos de 10 MB por container). Os containers voltam sozinhos quando o Docker
  reinicia, a não ser que você os tenha parado.
- **DNS fixo.** O catalog-service, o llm-service e o túnel usam o 1.1.1.1 e o 8.8.8.8. Sem isso, cada
  container guarda o DNS da rede de quando subiu. Se o computador troca de rede, esse DNS para de
  responder, e toda busca falha com 502 ("A IA (Groq) não respondeu"). Isso aconteceu em 25/11/2026.

## Passo a passo

### 1. Chaves (uma vez só)

As chaves ficam só no `backend/.env`, que nunca vai para o Git. Se o Compose de casa já funciona, esse
arquivo já existe. Se não:

```bash
cp backend/.env.example backend/.env && nano backend/.env
```

### 2. Subir

Derrube antes o Compose de casa, porque a rede da produção usa outra faixa de IP:

```bash
docker compose down
```

```bash
deploy/deploy.sh
```

Na primeira vez, o build leva uns 10 minutos. No fim, o script mostra o endereço:

```
==> Pronto: https://exemplo-palavras-aleatorias.trycloudflare.com
```

Para conferir de outro aparelho (o celular no 4G, por exemplo):

```bash
curl -I https://<endereço>/health
```

### Link fixo (uma vez só)

Na primeira subida o `deploy.sh` cria a branch `gh-pages` com a página de redirecionamento. Depois disso, ative
o GitHub Pages para essa branch (ou em Settings → Pages → Branch `gh-pages`, pasta `/`):

```bash
gh api -X POST repos/LucasBinda/WatchMatch/pages -f 'source[branch]=gh-pages' -f 'source[path]=/'
```

Cada publicação do link é um commit na `gh-pages` e um deploy do GitHub Pages, então o `deploy.sh` só publica
quando precisa: quando o endereço do túnel mudou (o túnel foi recriado) ou quando a `deploy/link.html` mudou.
Rodar o `deploy.sh` de novo com o túnel de pé (para atualizar o código, por exemplo) não publica nada, e o
`parar` também não: a página confere sozinha se o site responde. Na prática, é uma publicação por sessão de
apresentação. Quando publica, o link passa a apontar para o endereço novo em 1 a 10 minutos (build do Pages e
cache). O `deploy.sh` só envia a branch `gh-pages`: não troca de branch nem mexe na pasta de trabalho ou na
`main`. Para subir sem mexer no link: `PUBLICAR_LINK=0 deploy/deploy.sh`.

Isso é um contorno do endereço sorteado do túnel rápido. O usual é um endereço fixo por DNS, com domínio próprio
e túnel nomeado (seção "Endereço fixo"), em que ligar e desligar o site não publica nada.

### Enquanto estiver no ar

- O computador precisa ficar **ligado, conectado e sem suspender**.
- O túnel rápido aceita até 200 pedidos ao mesmo tempo e não tem garantia de disponibilidade. Para uma
  apresentação, isso sobra.
- Abrir `/api/recommend` direto no navegador dá **403**, e isso é esperado: a busca só aceita POST, e o
  site faz esse POST sozinho. Para testar, use a página.

### Achar o endereço de novo

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs tunnel | grep trycloudflare
```

### Atualizar

O script sobe o código que está na pasta. Para publicar a versão da `main`:

```bash
git pull && deploy/deploy.sh
```

O `up` só recria os containers que mudaram. Se o túnel não mudou, o endereço continua o mesmo.

### Ver o que está acontecendo

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail 50 recommendation-service
```

```bash
docker stats --no-stream
```

### Tirar do ar e voltar ao de casa

Derruba tudo, inclusive o túnel (que só existe no Compose de produção: um `docker compose down` simples não o
derruba). Não publica nada: o link fixo tenta abrir o site, não consegue e mostra que está fora do ar.

```bash
deploy/deploy.sh parar
```

```bash
docker compose up -d
```

## Endereço fixo (opcional)

Um endereço que não muda pede um **túnel nomeado**, que precisa de uma conta grátis na Cloudflare **e de um
domínio próprio** cadastrado nela. A Cloudflare não dá domínio de graça, e um `.com` custa por volta de
US$ 10 por ano. Com o domínio:

1. No painel da Cloudflare, em Zero Trust → Networks → Tunnels, crie um túnel e copie o token.
2. Em "Public Hostname", aponte o nome escolhido (ex.: `watchmatch.seudominio.com`) para `http://gateway:80`.
3. No `docker-compose.prod.yml`, troque o `command` do `tunnel` por `tunnel --no-autoupdate run`
   e acrescente `environment: { TUNNEL_TOKEN: "${TUNNEL_TOKEN}" }`.
4. Ponha `TUNNEL_TOKEN=...` no arquivo `.env` da raiz, que fica fora do Git.
