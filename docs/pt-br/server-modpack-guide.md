# Livre Comércio: guia do servidor e do modpack

Este guia é para donos de servidor e criadores de modpack. Ele cobre a instalação do mod, a escolha do banco de dados,
o compartilhamento de uma economia entre servidores, o preenchimento dos valores dos itens para um modpack, e as
configurações econômicas que os operadores podem usar. Os nomes e padrões das configurações estão na
[referência de configuração](configuration.md), e os comandos estão na [referência de comandos](commands.md).

## Sumário

- [Instalação](#instalação)
- [Config do servidor em modpacks e mundos](#config-do-servidor-em-modpacks-e-mundos)
- [Escolhendo o banco de dados](#escolhendo-o-banco-de-dados)
- [Compartilhando uma economia entre servidores](#compartilhando-uma-economia-entre-servidores)
- [Preenchendo os valores de um modpack](#preenchendo-os-valores-de-um-modpack)
- [Recalcular depois de adicionar mods](#recalcular-depois-de-adicionar-mods)
- [Configurações econômicas (imposto, tesouraria, backoff)](#configurações-econômicas-imposto-tesouraria-backoff)
- [A conta de imposto e os comandos de operador](#a-conta-de-imposto-e-os-comandos-de-operador)
- [Gatilhos e blocos de comando](#gatilhos-e-blocos-de-comando)
- [Backups](#backups)
- [Solução de problemas](#solução-de-problemas)

## Instalação

O Livre Comércio roda no NeoForge para Minecraft 1.21.1 (NeoForge 21.1.x, Java 21) e Minecraft 26.1.2 (NeoForge 26.1.x,
Java 25). No 26.1 só o JEI é suportado para arrastar itens, porque o EMI ainda não tem versão para 26.1.

| Onde | O que instalar |
|------|----------------|
| Servidor | O jar `freetrade`. Obrigatório. |
| Clientes | O mesmo jar `freetrade`. Obrigatório, porque as janelas de negociação e o guia são telas do cliente que usam as mensagens de rede do mod. |
| Clientes (opcional) | JEI ou EMI, para arrastar itens para os slots de alvo do Ponto de Venda e da Compra Automática, e para as dicas de preço. Nenhum dos dois é necessário no servidor. |
| Servidor e clientes (opcional) | Lightman's Currency, para as moedas dele como dinheiro e para a opção de banco. O mod funciona sem ele. |

Todo servidor que compartilha uma economia deve usar o mesmo modpack, para que as receitas e os valores batam.

## Config do servidor em modpacks e mundos

As configurações do servidor ficam em `config/freetrade-server.toml` na pasta da instância. A instância é a pasta do
servidor, ou a pasta do jogo no single player. O arquivo é criado na primeira inicialização, e vale para todos os mundos
daquela instância. Para distribuir configurações padrão em um modpack, inclua `config/freetrade-server.toml` no pacote.

Para dar a um mundo configurações próprias, coloque uma cópia em `<mundo>/serverconfig/freetrade-server.toml`. O NeoForge
usa essa cópia naquele mundo, em vez do arquivo da instância, e grava um `readme.txt` na pasta que explica isso. Mudanças
no arquivo da instância não chegam a um mundo que tem cópia própria.

Coloque o seu `values.json` na pasta `config/freetrade/` do modpack. O Livre Comércio o carrega quando o jogo inicia, e não
sobrescreve um arquivo que já exista. Veja [Preenchendo os valores de um modpack](#preenchendo-os-valores-de-um-modpack).

## Escolhendo o banco de dados

| | SQLite (`type = "SQLITE"`) | PostgreSQL (`type = "POSTGRES"`) |
|---|---|---|
| Onde os dados ficam | Um arquivo, `freetrade.db`, na pasta do mundo | Um banco PostgreSQL em um servidor que você mantém |
| Configuração | Nenhuma | Criar um banco e um usuário |
| Servidores que podem compartilhar | Um | Vários, como uma economia só |
| Indicado para | Single player e servidores pequenos | Servidores, principalmente quando vários compartilham um mercado |
| Backups | Pare o servidor e copie o arquivo (veja [Backups](#backups)) | Use `pg_dump` |

Use SQLite, a menos que você queira que vários servidores compartilhem uma economia. Para servidores de verdade, o
PostgreSQL é a escolha recomendada, porque o banco fica separado do mundo.

### Configuração do PostgreSQL

1. Crie um banco chamado `freetrade` e um usuário com permissão para criar tabelas nele. O mod cria as próprias tabelas
   `ft_*` na primeira vez que inicia.
2. Coloque os dados de conexão na seção `[database]`:

```toml
[database]
type = "POSTGRES"
postgres_url = "jdbc:postgresql://db.interno:5432/freetrade"
postgres_user = "freetrade"
postgres_password = "troque-isto"
server_id = "sobrevivencia-1"
```

O driver JDBC já vem dentro do jar do mod.

Um arquivo mínimo do Docker Compose para um banco na mesma rede do servidor pode ser assim. É só um exemplo. Troque a
senha, e não publique a porta na internet.

```yaml
services:
  freetrade-db:
    image: postgres:16
    environment:
      POSTGRES_DB: freetrade
      POSTGRES_USER: freetrade
      POSTGRES_PASSWORD: troque-isto
    volumes:
      - freetrade-pg:/var/lib/postgresql/data

volumes:
  freetrade-pg:
```

Se o banco não puder ser alcançado quando o servidor inicia, o Livre Comércio registra um erro, a negociação fica
desligada até que você reinicie o servidor, e os comandos respondem "O mercado está offline no momento."

### Nota para desenvolvedores: testes com PostgreSQL

O repositório tem um teste de integração, `MarketPostgresTest`, que roda duas instâncias do mercado em um mesmo banco
PostgreSQL, do mesmo jeito que dois servidores compartilham uma economia. Para rodá-lo:

```
FT_PG_URL=jdbc:postgresql://host:port/db FT_PG_USER=... FT_PG_PASSWORD=... ./gradlew test
```

O teste é pulado quando `FT_PG_URL` não está definido, então `./gradlew test` roda sem banco.

## Compartilhando uma economia entre servidores

Vários servidores podem usar o mesmo banco PostgreSQL. Eles então compartilham estoque, saldos, conta de imposto,
tesouraria, histórico de transações e gatilhos.

Regras a seguir:

- **Mesmo modpack em todos.** Os valores derivados ficam guardados no banco de dados. O primeiro servidor a iniciar com
  o banco vazio os calcula. Os outros os carregam, então todos precisam ter as mesmas receitas.
- **`server_id` único.** Cada servidor escreve o seu nome no histórico. Use `sobrevivencia-1`, `sobrevivencia-2` e assim por
  diante.
- **Mesmas configurações em todos.** A fórmula, os impostos, o backoff e a tesouraria são de cada servidor. Se dois
  servidores usam valores diferentes, eles precificam o mesmo estoque de formas diferentes.
- **Atualização do cache.** Com PostgreSQL, cada servidor guarda na memória uma cópia do estoque, dos saldos e dos
  gatilhos, e a recarrega a cada `cache_refresh_seconds` (padrão 10). Os preços vistos em um servidor podem estar atrasados
  esse tempo. As negociações em si rodam dentro de uma transação do banco que trava a linha do estoque, então dois
  servidores não conseguem vender as mesmas unidades duas vezes.
- **Gatilhos disparam uma vez.** A verificação de um gatilho é feita dentro de uma transação do banco que trava a linha do
  gatilho. Servidores que compartilham um banco nunca disparam o mesmo gatilho duas vezes. O comando roda no servidor que
  ganhou a trava.

## Preenchendo os valores de um modpack

O `config/freetrade/values.json` decide o valor base de cada item, a velocidade com que o preço muda e o estoque com que
ele começa. O formato completo está na [referência de configuração](configuration.md#valuesjson). Uma forma prática de
preencher:

1. **Declare os itens importantes.** Dê valores base aos itens que não devem depender de receitas, como materiais raros:

   ```jsonc
   {
     "values": {
       "minecraft:diamond": 64,
       "#minecraft:logs": 4,
       "#c:ingots": 8
     }
   }
   ```

   Tags são práticas para famílias inteiras. Um id exato sempre vence uma tag para o mesmo item.

2. **Defina a velocidade de variação dos preços.** `reference_stock` é o estoque em que um item custa o seu valor base. Um
   valor menor faz o preço subir mais rápido quando o estoque cai. Um valor maior o deixa mais estável.

   ```jsonc
   "reference_stock": { "minecraft:netherite_scrap": 50, "#c:gems": 200 }
   ```

3. **Defina o estoque inicial.** `initial_stock` define o estoque que um item recebe na primeira vez que o mercado o vê. O
   modo de `initial_stock` na configuração do servidor (`REFERENCE`, `RANDOM` ou `ZERO`) vale para todos os outros itens:
   - `REFERENCE` (padrão): o estoque é igual ao `reference_stock`, então o preço começa no valor base.
   - `RANDOM`: um estoque aleatório entre `random_min` e `random_max` vezes o estoque de referência.
   - `ZERO`: sem estoque, então os jogadores precisam vender um item antes que alguém possa comprá-lo.

   Uma entrada `initial_stock` de um item específico sempre vence o modo.

4. **Exclua o que nunca deve ser negociado.** `excluded` tira itens dos valores derivados de receitas e não dá a eles
   estoque inicial. A lista padrão já exclui bedrock, barreiras, blocos de comando, blocos de estrutura, geradores de
   monstros e itens parecidos.

   > **Atenção:** `excluded` sempre vence. Um item listado em `excluded` nunca é negociado, mesmo que também tenha um valor em `values`.

5. **Aplique e confira.** Rode `/freetrade reload`, depois `/freetrade price <item>` para os itens que interessam, e
   `/freetrade unpriced` para achar itens sem preço.

O estoque inicial vale só para itens que o mercado ainda não viu. Mudar `initial_stock` depois não muda o estoque de itens
que já têm estoque. Use `/freetrade setstock` para isso.

## Recalcular depois de adicionar mods

Os valores derivados vêm das receitas e ficam guardados no banco de dados. Quando você adiciona ou remove mods, ou muda
receitas, rode:

```
/freetrade recalc
```

O recalc calcula de novo todos os valores derivados a partir das receitas atuais e mantém o estoque como está. Itens novos
recebem o estoque inicial de acordo com as configurações de `initial_stock`. Depois confira `/freetrade unpriced` para ver
os itens que continuam sem preço.

O `/freetrade reload` **não** calcula receitas novas. Ele só reaplica os valores declarados e as configurações. Veja
[Recalcular versus recarregar](configuration.md#recalcular-versus-recarregar).

## Configurações econômicas (imposto, tesouraria, backoff)

Estas configurações estão na seção `[fees]` do arquivo de configuração do servidor. Os nomes exatos e as faixas estão na
[referência de configuração](configuration.md#fees).

**Impostos.** `tax_on_sell_percent` tira uma porcentagem de cada venda para a associação, e o vendedor recebe o restante.
`tax_on_buy_percent` acrescenta uma porcentagem a cada compra. Os dois vão para a conta de imposto. Os impostos fazem parte
do preço que o jogador vê, então aparecem no catálogo e em `/freetrade price`.

**Tesouraria.** `treasury_initial = -1` (o padrão) torna a tesouraria infinita. As negociações não a tocam, então a
associação sempre paga. Uma tesouraria finita é o dinheiro da própria associação:

- vender para a associação é pago com a tesouraria, e quando ela não consegue pagar, a venda é reduzida ou recusada com
  "A associação não tem dinheiro";
- comprar da associação soma o preço à tesouraria;
- com a tesouraria vazia, a associação para de comprar dos jogadores, mas os jogadores sempre podem comprar dela;
- `treasury_initial` define o saldo inicial apenas quando o banco de dados é criado pela primeira vez. Depois, os operadores
  podem mudar o saldo com `/freetrade treasury deposit` e `remove`.

**Backoff.** O backoff anti-armazenamento vem ligado por padrão. Ele conta uma ida e volta quando um jogador vende um item e
o recompra dentro de `backoff_window_seconds`. A partir de `backoff_threshold` idas e voltas, cada nova ida e volta aumenta o
preço de compra daquele jogador para aquele item em `backoff_percent` por nível, até `backoff_max_level` níveis. Um nível
cai depois de cada janela inteira sem idas e voltas.

Exemplos de ajuste:

| Objetivo | Configurações |
|----------|---------------|
| Proteção mais forte contra usar o mercado como depósito | `backoff_threshold = 2`, `backoff_percent = 20.0`, `backoff_window_seconds = 1800` |
| Desligar | `backoff_enabled = false` |

**Outras configurações de preço** em `[market]`:

- `price_ceiling` limita o preço de uma unidade.
- `min_unit_price` impede que itens abundantes fiquem de graça.
- `labor_percent` acrescenta uma porcentagem por etapa de fabricação, então itens fabricados custam mais que seus ingredientes.
- `dynamic_propagation = false` impede que os itens derivados acompanhem o preço atual dos ingredientes.

## A conta de imposto e os comandos de operador

A conta de imposto recolhe os impostos de venda e de compra. Os operadores a usam para recompensas e eventos:

```
/freetrade taxaccount balance
/freetrade taxaccount withdraw 50
/freetrade taxaccount give <jogador> 20
```

Os impostos se acumulam na conta conforme as negociações acontecem. Os operadores podem tirar dinheiro com `withdraw` (os
itens vão para o operador que executa o comando) ou `give` (os itens vão para o jogador indicado). Um bloco de comando ou o console não tem jogador, então deve usar `give`: `withdraw` ali falha e não tira nada. A lista completa está na
[referência de comandos](commands.md#contas-tesouraria-e-imposto).

## Gatilhos e blocos de comando

Gatilhos executam um comando quando a tesouraria ou a conta de imposto chega a uma condição. Um gatilho também pode gastar
dinheiro da mesma conta antes de rodar. O comando roda no nível de permissão 4, então pode fazer tudo o que um operador faz.

Os exemplos abaixo podem ser usados como estão escritos, no chat ou em um bloco de comando.

**Leitura por comparador.** Coloque este comando em um bloco de comando e ligue um comparador a ele. O comando devolve `1`
quando a tesouraria tem pelo menos 1000 U.M., e `0` caso contrário:

```
/freetrade treasury query >= 1000
```

**Anúncio único.** Dispara uma vez, quando a conta de imposto chega a 500 U.M.:

```
/freetrade trigger add bonus tax >= 500 once run say Meta de imposto atingida!
```

**Recompensa repetida que gasta da conta de imposto.** Quando a conta de imposto tem pelo menos 100 U.M., ela pode disparar
uma vez por hora. Cada disparo tira 20 U.M. da conta de imposto, e depois roda o comando, que aqui dá uma esmeralda a cada
jogador online:

```
/freetrade trigger add recompensa tax >= 100 repeat 20 3600 give @a minecraft:emerald 1
```

O comando pode anunciar o disparo em vez disso, usando os marcadores `{count}` (quantos disparos até agora) e `{balance}`
(o saldo depois do custo):

```
/freetrade trigger add recompensa tax >= 100 repeat 20 3600 say Recompensa #{count}: {balance} U.M. restam no imposto
```

Notas:

- Gatilhos `once` se desativam sozinhos depois de disparar. Use `/freetrade trigger reset <id>` para armá-los de novo.
- `repeat` sem espera dispara uma vez cada vez que a condição fica verdadeira, e espera ela ficar falsa de novo para poder
  disparar outra vez.
- Um custo só é tirado se o saldo for pelo menos igual ao custo. Caso contrário, o gatilho não dispara.
- Blocos de comando precisam de nível de permissão 2 para rodar comandos `/freetrade account` e `/freetrade trigger`.
- O saldo da `treasury` não muda com a tesouraria infinita (`treasury_initial = -1`), então gatilhos na tesouraria precisam
  de uma tesouraria finita, ou de um depósito manual.

## Backups

**SQLite.** Pare o servidor antes de copiar o banco. O Livre Comércio usa o modo WAL, então copie os três arquivos juntos:
`freetrade.db`, `freetrade.db-wal` e `freetrade.db-shm`. Restaure-os com o servidor parado.

**PostgreSQL.** Faça backup do banco com `pg_dump` em uma agenda. Os backups do mundo não incluem o mercado quando ele fica
no PostgreSQL.

**Os dois.** Mantenha o modpack e o arquivo `values.json` sob controle de versão. O recalc recria os valores derivados, mas
precisa das mesmas receitas.

## Solução de problemas

| Sintoma | Causa provável e solução |
|---------|--------------------------|
| "O mercado está offline no momento." | O banco não abriu na inicialização. Confira no log a mensagem "could not open its database". Verifique o `type`, a URL, o usuário e a senha, se o PostgreSQL está acessível e se a pasta do SQLite é gravável. A negociação fica desligada até um reinício. |
| "Erro no banco de dados. Tente de novo." | Uma transação falhou, muitas vezes porque a conexão caiu. O Livre Comércio reconecta sozinho. Se isso se repetir, verifique o servidor do banco. O log pode mostrar "Database connection lost, reconnecting". |
| Um item diz "não é negociado" | Ele não tem valor declarado nem valor de receita, ou está excluído. Rode `/freetrade unpriced`. Adicione um valor e rode `/freetrade reload`, ou rode `/freetrade recalc` depois de adicionar o mod. |
| Um valor alterado não chegou aos itens derivados | Valores declarados se aplicam com `reload`. Itens derivados precisam de `/freetrade recalc`. |
| Dois servidores mostram preços diferentes | Os `cache_refresh_seconds` ou as configurações são diferentes, ou o banco é compartilhado mas os modpacks não são iguais. Compare as configurações e espere um intervalo de atualização. |
| Um gatilho não dispara | Confira em `/freetrade trigger list` se há `[off]`, o saldo da conta e o custo. Um gatilho `once` que já disparou fica desligado até ser reiniciado. |
| A opção de banco do Lightman's não aparece | O Lightman's Currency não está instalado no cliente e no servidor, ou `lightmans_bank` está como `false`. |
| Itens continuam com o preço antigo depois de atualizar um mod | Rode `/freetrade recalc` e depois `/freetrade price <item>` para conferir o resultado. |
