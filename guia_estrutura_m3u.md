# Guia Definitivo: Estrutura de Listas M3U para IPTV

Este documento é a referência completa para treinar seu App / Parser a entender como os dados estão organizados dento de um arquivo `.m3u` ou `m3u_plus`.

---

## 1. O Padrão M3U (Como tudo funciona)
Toda lista IPTV válida começa obrigatoriamente com a tag de cabeçalho:
```m3u
#EXTM3U
```

Abaixo dela, cada conteúdo digital (seja um Canal, um Filme ou um Episódio) ocupa exatamente **duas linhas**:
1. **A linha de Metadados (`#EXTINF`)**: Contém nome, logotipo, categoria e IDs.
2. **A linha do Link (`http...`)**: O endereço do servidor (arquivos `.ts`, `.mp4`, `.mkv`).

---

## 2. Dissecando a Linha de Metadados (#EXTINF)

A estrutura visual da tag segue este padrão rígido:
`#EXTINF:<duração> <Atributo1="Valor"> <Atributo2="Valor">, <Nome de Exibição>`

### Atributos Cruciais para o seu App:
- `tvg-id`: Identificador único no EPG (Guia de Programação). Útil apenas para TV ao Vivo. Se vazio (`""`), ignore para EPG.
- `tvg-name`: O "Nome de Busca" do arquivo. Normalmente igual ao nome exibido.
- `tvg-logo`: A imagem da **Capa do Filme/Série** ou o **Ícone do Canal**. Seu app deve baixar ou renderizar essa URL nas miniaturas.
- `group-title`: A **Categoria** ou **Pasta**. É aqui que você sabe se um conteúdo deve ir pra aba "FIlmes", "Séries" ou "Canais".

> **DICA PARA O PARSER**: Tudo que está APÓS a última vírgula (`,`) da linha `#EXTINF` é o **TÍTULO FINAL** que o usuário vai ler na tela do aplicativo. Extraia usando um `split(',')[-1]`.

---

## 3. Comportamento e Exemplos por Tipo de Conteúdo

### 🎬 A. FILMES (Movies)
**Como Identificar**: O `group-title` (Categoria) geralmente contém as palavras `Filme`, `Lançamento`, `Cinema`, `VOD`, ou gêneros como `Ação`, `Comédia`. O título frequentemente possui o **Ano (ex: 2024)**.
**O que extrair no App**: Extraia o Título Base, extraia a tag do Ano via Regex, e use o arquivo `.mp4` / `.mkv` direto pro player.

**Exemplo Extraído da sua Lista:**
```m3u
#EXTINF:-1 xui-id="2107" tvg-name="Pasárgada" tvg-logo="https://image.tmdb.org/t/p/w600_and_h900_bestv2/1TK4KTXt3mQAgq09GxmohNz9PTM.jpg" group-title="Filmes | Nacionais",Pasárgada
http://cdn.cinextv.com.br:80/play/Y0WFxvG0bMFqj7uCzTJpOTs0DpJReEzw9T1BqvSfX0QfXH7KP_95BIWId2PR63VR#.mp4
```

### 📺 B. SÉRIES E EPISÓDIOS (Series)
**Como Identificar**: As séries não vêm agrupadas magicamente. O arquivo M3U trata **cada episódio como se fosse um "filme" separado**. 
A grande sacada pro parser é olhar duas coisas:
1. O `group-title` tem a palavra `Série` ou o nome da produtora (ex: `Séries | Netflix`).
2. O Título Final DEVE conter a marcação de Temporada e Episódio: **`S01E01`**, **`S01 E01`**, etc.

**O que extrair no App**: 
1. Use Regex (`[Ss]\d{2}[Ee]\d{2}`) para achar "S01E01".
2. Tudo que estiver **ANTES** de `S01E` é o Nome da Série (Pasta raiz que você vai criar no app).
3. O `tvg-logo` na linha desse episódio geralmente é a capa da série inteira, ou uma thumb específica do episódio. Agrupe todos os que têm o mesmo "Nome de Série" numa mesma view.

**Exemplo Extraído da sua Lista:**
```m3u
#EXTINF:-1 xui-id="2134" tvg-name="3 Palavrinhas S01E01" tvg-logo="http://image.tmdb.org//t/p/w600_and_h900_bestv2/8bJ0eyI21JtjMo0obfwFYcZyjil.jpg" group-title="Series | Outras Produtoras",3 Palavrinhas S01E01
http://cdn.cinextv.com.br:80/play/t848lCywvJAim-j6HDXrjOTRjwiM0sgGXe_slVOpRBKqWjbuxLU6rN8cV6vu0NAC#.mp4
```

### 📡 C. CANAIS DE TV AO VIVO (Live TV)
**Como Identificar**: O arquivo final costuma ser `.ts` ou não ter extensão. As categorias (`group-title`) são geralmente `Canais Abertos`, `Esportes`, `Noticias`, `Infantil`, sem mencionar VOD/Filmes. O título é limpo e sem datas de lançamento.

**O que extrair no App**: Pegue o ID de Guia EPG (`tvg-id`) se existir, e coloque no Player de TV. Canais Ao Vivo devem sempre estar numa tela apartada de Filmes/Séries.

**Exemplo Extraído da sua Lista:**
```m3u
#EXTINF:-1 xui-id="10" tvg-id="" tvg-name="BBB 01" tvg-logo="http://cdn.cinextv.com.br:80/images/tlKRnc42PEAJ1Jtyn_-G2lDjDafRc8iHwPLRoF0VNF21G1TrWFzfzuOB_KwhhfCHsXvpKVedUAgKsGBJ_3UlBne5PhiDDpcFhfftoHYuc0nRbeVjK-qP6M0pwTxNgCw6HMzVxICT99FkkZKUWK3P1CKYprrigu4zr7z2wPKOtt4.png" group-title="↪️ Big Brother ✅",BBB 01
http://cdn.cinextv.com.br:80/play/gRiICXZSbCJR_II0NYufDC0iKzwPU6ZrCFTvWg2EYLQ/ts
```

### 📂 D. PASTAS AVULSAS / MISCELÂNEA (Misc/24h)
Existem categorias que são geradas livremente pelo dono do servidor IPTV. 
**Exemplos Comuns**: `Shows`, `Câmeras Ao Vivo`, `Novelas`, `Canais 24h (Séries 24h)`, `Variedades`, `Rádios`.
Nesse caso, seu Parser pode criar uma aba "Novelas" isolada ou agrupar em "Outros" se não contiver a keyword principal (Série/Filme/Canais). `Canais 24h` comportam-se como TV Ao Vivo (é um streaming sem fim), não como VOD.

**Exemplo:**
```m3u
#EXTINF:-1 tvg-id="" tvg-name="Chaves 24H" tvg-logo="http://logo.chaves.jpg" group-title="Canais 24 Horas",Chaves Ao Vivo 24H
http://cdn.servidor.com/live/chaves_24h/index.m3u8
```

---

## 4. O Fluxo Perfeito para seu Parser M3U (App Builder)

Siga com sua IA este diagrama lógico para varrer as linhas e popular as "Abas" do seu Aplicativo:

1. **Leia a Linha 1** -> Se for `#EXTINF`, guarde a string temporariamente e extraia os atributos.
2. **Leia a Linha 2** -> Se começar com `http`, você formou um Item Completo.
3. **Classificação (If / Else)**:
   - Se `group-title` tiver "Série" OU Título tiver "S01E01" -> **Mandar pra Lista de Séries (Agrupar pelo nome base e ordernar episódios)**.
   - Else If `group-title` tiver "Filme", "Lançamento" -> **Mandar pra Lista de Filmes (Remover sufixos de qualidade 4K/Dual do nome do UI)**.
   - Else If `url` terminar em `.ts` ou `group-title` tiver "tv", "aberto", "esportes" -> **Mandar pra Lista de Canais ao Vivo**.
   - Else -> **Mandar pra uma aba "Variedades/Outros"**.
4. **Resolução de Capas**: Sempre dê Fallback. Se `tvg-logo=""`, coloque uma imagem placeholder preta com o Nome Centralizado no seu App.

