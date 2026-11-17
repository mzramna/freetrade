# Livre Comércio: referência de configuração

Esta página documenta todas as configurações do Livre Comércio: o arquivo de configuração do servidor, o arquivo de
valores e a linguagem da fórmula de preço. Para um passo a passo de configuração de servidor ou modpack, veja o
[guia do servidor e do modpack](server-modpack-guide.md).

## Sumário

- [Arquivos e onde ficam](#arquivos-e-onde-ficam)
- [freetrade-server.toml](#freetrade-servertoml)
  - [[market]](#market)
  - [[currency]](#currency)
  - [[fees]](#fees)
  - [[machines]](#machines)
  - [[guide]](#guide)
  - [[database]](#database)
  - [[triggers]](#triggers)
- [values.json](#valuesjson)
  - [Estrutura](#estrutura)
  - [Tags e prioridade](#tags-e-prioridade)
  - [Valores derivados das receitas](#valores-derivados-das-receitas)
  - [Mão de obra](#mão-de-obra)
  - [Recalcular versus recarregar](#recalcular-versus-recarregar)
  - [Itens sem preço](#itens-sem-preço)
- [Linguagem da fórmula de preço](#linguagem-da-fórmula-de-preço)
  - [Variáveis, operadores e funções](#variáveis-operadores-e-funções)
  - [Como um preço é calculado](#como-um-preço-é-calculado)
  - [Exemplos resolvidos](#exemplos-resolvidos)

## Arquivos e onde ficam

| Arquivo | Onde | Criado | Finalidade |
|---------|------|--------|------------|
| `freetrade-server.toml` | `config/` na pasta da instância: a pasta do servidor, ou a pasta do jogo no single player. Vale para todos os mundos da instância. | Na primeira inicialização. Um modpack pode distribuí-lo em `config/`. | Todas as configurações, exceto os valores dos itens. |
| `freetrade-server.toml` | `serverconfig/` na pasta de um mundo | Você cria, se o mundo precisar de configurações próprias | Substitui o arquivo da instância só para esse mundo. O NeoForge grava um `readme.txt` lá. |
| `values.json` | `config/freetrade/` | Na primeira carga, com uma lista padrão | Valores base, estoque de referência, estoque inicial e itens excluídos. |
| `freetrade.db` | Pasta do mundo (SQLite) | Na primeira inicialização | O banco de dados do mercado: estoque, saldos, gatilhos, histórico e preços forçados. |

Um mundo que tem o seu próprio `serverconfig/freetrade-server.toml` usa essa cópia no lugar do arquivo da instância.
Mudanças no arquivo da instância não chegam a esse mundo.

O servidor aplica o `freetrade-server.toml` quando o NeoForge carrega ou recarrega a configuração. As configurações do
banco (`[database]`) são lidas só quando o servidor inicia, então mude-as com o servidor parado. Mudanças no
`values.json` são aplicadas por `/freetrade reload`, e os valores derivados precisam de `/freetrade recalc` (veja
[Recalcular versus recarregar](#recalcular-versus-recarregar)).

## freetrade-server.toml

O arquivo tem sete seções. Cada chave lista o tipo, o padrão, a faixa permitida, a explicação e um exemplo.

```toml
[market]
formula = "b * (r / x)^1"
price_ceiling = -1.0
min_unit_price = 0.001
reference_stock = 1000
initial_stock = "REFERENCE"
random_min = 0.5
random_max = 1.5
dynamic_propagation = true
labor_percent = 0.0
accept_damaged_items = true

[currency]
currency_items = ["minecraft:emerald=1", "minecraft:emerald_block=9"]
lightmans_coins = true
lightmans_bank = true
lightmans_um_per_core_value = 0.01

[fees]
tax_on_sell_percent = 0.0
tax_on_buy_percent = 0.0
backoff_enabled = true
backoff_threshold = 3
backoff_window_seconds = 600
backoff_percent = 10.0
backoff_max_level = 20
treasury_initial = -1

[machines]
interval_ticks = 20
batch_size = 64
villager_max_stacks = 27

[guide]
show_catalog = true

[database]
type = "SQLITE"
sqlite_file = "freetrade.db"
postgres_url = "jdbc:postgresql://localhost:5432/freetrade"
postgres_user = "freetrade"
postgres_password = ""
server_id = "default"
cache_refresh_seconds = 10
history_days = 90

[triggers]
check_interval_ticks = 100
```

### [market]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `formula` | texto | `b * (r / x)^1` | fórmula válida | Preço de uma unidade em função do estoque `x`, do valor base `b` e do estoque de referência `r`. Uma fórmula inválida é rejeitada (um erro é registrado) e o padrão é usado. Veja [Linguagem da fórmula de preço](#linguagem-da-fórmula-de-preço). | `"b * (r / x)^2"` |
| `price_ceiling` | decimal | `-1.0` | -1 a 1e15 | Preço máximo de uma unidade, em U.M. `-1` significa sem teto. | `25.0` |
| `min_unit_price` | decimal | `0.001` | 0 a 1e9 | Preço mínimo de uma unidade, em U.M. Evita que itens muito abundantes valham 0. | `0.01` |
| `reference_stock` | inteiro | `1000` | 1 a 2305843009213693951 | `r` padrão: o estoque em que um item custa o seu valor base. Cada item pode mudar esse valor em `values.json`. | `4096` |
| `initial_stock` | enum | `"REFERENCE"` | `REFERENCE`, `RANDOM`, `ZERO` | Estoque que um item recebe na primeira vez que o mercado o vê. `REFERENCE` = `r`, então o preço começa no valor base. `RANDOM` = um valor aleatório entre `random_min * r` e `random_max * r`. `ZERO` = vazio, então os jogadores precisam vender primeiro. O `initial_stock` de cada item em `values.json` sempre tem prioridade. | `"ZERO"` |
| `random_min` | decimal | `0.5` | 0 a 100 | Limite inferior do estoque inicial no modo `RANDOM`, como múltiplo de `r`. | `0.25` |
| `random_max` | decimal | `1.5` | 0 a 100 | Limite superior do estoque inicial no modo `RANDOM`, como múltiplo de `r`. Se for menor que `random_min`, usa-se `random_min`. | `2.0` |
| `dynamic_propagation` | booleano | `true` | true / false | Itens derivados tiram o valor base do preço atual dos ingredientes, então as variações do mercado se propagam pelas receitas. Valores declarados não são afetados. | `false` |
| `labor_percent` | decimal | `0.0` | 0 a 1000 | Porcentagem somada ao valor de um item fabricado, sobre os ingredientes, a cada etapa de fabricação. As etapas se acumulam em uma cadeia. | `5.0` |
| `accept_damaged_items` | booleano | `true` | true / false | Aceita ferramentas e armaduras danificadas, pagando na proporção da durabilidade restante. Quando `false`, itens danificados são recusados. | `false` |

### [currency]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `currency_items` | lista de textos | `["minecraft:emerald=1", "minecraft:emerald_block=9"]` | `id_do_item=valor`, valor de pelo menos 0,001 | Itens que são dinheiro, cada um com seu valor em U.M. (decimais são aceitos). O dinheiro tem valor fixo e nunca é negociado como mercadoria. O troco é dado começando pelos maiores valores. Uma entrada sem `=`, ou com valor abaixo de 0,001, é rejeitada. | `["minecraft:emerald=1", "minecraft:gold_ingot=0.5"]` |
| `lightmans_coins` | booleano | `true` | true / false | Quando o Lightman's Currency está instalado, as moedas dele são aceitas como dinheiro. | `false` |
| `lightmans_bank` | booleano | `true` | true / false | Quando o Lightman's Currency está instalado, os jogadores podem pagar e receber pela conta bancária. | `false` |
| `lightmans_um_per_core_value` | decimal | `0.01` | 0,001 a 1e9 | Quantas U.M. vale uma unidade de valor central (core value) do Lightman's Currency. Uma moeda vale o seu valor central vezes esta taxa. | `0.05` |

### [fees]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `tax_on_sell_percent` | decimal | `0.0` | 0 a 100 | Porcentagem de cada venda para a associação que vai para a conta de imposto (conta dos operadores). O jogador recebe o resto. | `5.0` |
| `tax_on_buy_percent` | decimal | `0.0` | 0 a 1000 | Porcentagem somada a cada compra da associação. O valor extra vai para a conta de imposto. | `2.5` |
| `backoff_enabled` | booleano | `true` | true / false | Liga ou desliga o backoff anti-armazenamento. | `true` |
| `backoff_threshold` | inteiro | `3` | 1 a 1000 | Idas e voltas antes que a penalidade comece a crescer. Uma ida e volta é vender e recomprar o mesmo item dentro da janela. Uma negociação em que o arredondamento favoreceu o jogador também conta como ida e volta. | `5` |
| `backoff_window_seconds` | inteiro | `600` | 1 a 2592000 | Duração da janela que define uma ida e volta, e de uma janela ociosa (em segundos). | `1200` |
| `backoff_percent` | decimal | `10.0` | 0 a 1000 | Porcentagem somada ao preço de compra para cada nível de penalidade. O pagamento por vender o item é dividido por 1 + porcentagem / 100 x nível. | `15.0` |
| `backoff_max_level` | inteiro | `20` | 1 a 1000 | Nível máximo de penalidade. Com os padrões, o preço de compra sobe no máximo 200%, e o pagamento por venda cai para no máximo um terço. | `10` |
| `treasury_initial` | inteiro (U.M.) | `-1` | -1 a 9223372036854775 | `-1` significa tesouraria infinita: as negociações não a tocam, então ela sempre paga. Qualquer outro valor torna a tesouraria finita: vender para a associação é pago com ela, comprar dela a reabastece, e, quando ela está vazia, a associação para de comprar dos jogadores. O valor é o saldo inicial, gravado apenas quando o banco de dados é criado pela primeira vez. Mudanças posteriores não redefinem o saldo; use `/freetrade treasury deposit` ou `remove`. Veja [Configurações econômicas](server-modpack-guide.md#configurações-econômicas-imposto-tesouraria-backoff). | `100000` |

### [machines]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `interval_ticks` | inteiro | `20` | 1 a 72000 | Ticks (20 por segundo) entre as vendas e compras automáticas do Ponto de Venda e da Compra Automática. | `40` |
| `batch_size` | inteiro | `64` | 1 a 100000 | Máximo de unidades que uma máquina negocia em uma operação. | `256` |
| `villager_max_stacks` | inteiro | `27` | 1 a 27 | Pilhas de um item que o jogador pode colocar na grade de venda do Representante Comercial. A grade tem 27 espaços, então o intervalo termina em 27. | `9` |

### [guide]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `show_catalog` | booleano | `true` | true / false | Se jogadores comuns podem ver a aba de catálogo do livro Guia da Associação Comercial: os itens que a associação negocia, com preços e estoque. Quando `false`, a aba fica vazia para eles e mostra um aviso. Operadores (nível de permissão 2) continuam vendo. A negociação com o Representante Comercial não é afetada. Veja [o livro-guia e o catálogo](server-modpack-guide.md#o-livro-guia-e-o-catálogo). | `false` |

### [database]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `type` | enum | `"SQLITE"` | `SQLITE`, `POSTGRES` | `SQLITE` guarda o mercado em um arquivo dentro do mundo. `POSTGRES` é recomendado para servidores, e vários servidores podem compartilhar um banco PostgreSQL, formando uma só economia. Todos devem usar o mesmo modpack. | `"POSTGRES"` |
| `sqlite_file` | texto | `"freetrade.db"` | nome de arquivo ou caminho absoluto | Arquivo SQLite, relativo à pasta do mundo, ou um caminho absoluto. Usado só quando `type` é `SQLITE`. | `"mercado/freetrade.db"` |
| `postgres_url` | texto | `"jdbc:postgresql://localhost:5432/freetrade"` | URL JDBC | URL JDBC do banco PostgreSQL. Usada só quando `type` é `POSTGRES`. | `"jdbc:postgresql://db.interno:5432/freetrade"` |
| `postgres_user` | texto | `"freetrade"` | qualquer | Usuário do PostgreSQL. | `"freetrade_mod"` |
| `postgres_password` | texto | `""` | qualquer | Senha do PostgreSQL. Ela fica em texto puro neste arquivo, então restrinja o acesso a ele. | `"troque-isto"` |
| `server_id` | texto | `"default"` | qualquer | Nome deste servidor no histórico de transações. Dê um nome diferente a cada servidor que compartilha o banco. | `"sobrevivencia-1"` |
| `cache_refresh_seconds` | inteiro | `10` | 1 a 3600 | Segundos entre as recargas de estoque, saldos e gatilhos a partir do banco. Usado só com `POSTGRES`, onde outros servidores podem alterar os mesmos dados. | `5` |
| `history_days` | inteiro | `90` | 0 a 36500 | Dias de histórico de transações mantidos. Registros antigos são removidos cerca de uma vez por hora. `0` mantém o histórico para sempre. | `30` |

### [triggers]

| Chave | Tipo | Padrão | Faixa | Explicação | Exemplo |
|-------|------|--------|-------|------------|---------|
| `check_interval_ticks` | inteiro | `100` | 1 a 72000 | Ticks entre as verificações periódicas dos gatilhos das contas de tesouraria e de imposto. Os gatilhos também são verificados após cada negociação ou mudança de conta. | `20` |

## values.json

`config/freetrade/values.json` define o valor base, o estoque de referência e o estoque inicial dos itens, e quais itens
são excluídos. Se o arquivo não existir, o Livre Comércio o cria com uma lista padrão de valores na primeira carga.

### Estrutura

```jsonc
{
  "values": {
    "minecraft:diamond": 16,
    "#minecraft:logs": 4
  },
  "reference_stock": {
    "minecraft:cobblestone": 4096,
    "#c:ingots": 500
  },
  "initial_stock": {
    "minecraft:iron_ingot": 256
  },
  "excluded": [
    "minecraft:bedrock",
    "#c:hidden_from_recipe_viewers"
  ]
}
```

| Seção | Tipo do valor | Significado |
|-------|---------------|-------------|
| `values` | número, acima de 0 | Valor base em U.M. de uma unidade. Valores zero e negativos são ignorados, então o item é avaliado pelas receitas. |
| `reference_stock` | inteiro, de pelo menos 1 | `r` de cada item: o estoque em que o item custa o seu valor base. Substitui o `reference_stock` da configuração do servidor. |
| `initial_stock` | inteiro, de pelo menos 0 | Estoque com que o item começa. Substitui o modo de `initial_stock` da configuração do servidor. |
| `excluded` | lista de textos | Itens que nunca recebem valor derivado de receita e nunca recebem estoque inicial. `excluded` sempre vence: um item listado aqui nunca é negociado, mesmo que também tenha um valor em `values`. |

Chaves que começam com `_` (por exemplo `_comment`) são ignoradas. Cada chave é um id de item, como `minecraft:diamond`,
ou uma tag escrita com `#` na frente, como `#minecraft:logs`.

**Conteúdo padrão.** O arquivo padrão declara valores base para itens comuns do Minecraft, por exemplo `#minecraft:logs` a
4 U.M., `minecraft:diamond` a 64 U.M. e `minecraft:nether_star` a 1024 U.M. Define `reference_stock` para diamantes (200),
estrelas do Nether (10) e sucata de netherita (50), e exclui itens como bedrock, barreiras, blocos de comando, blocos de
estrutura, geradores de monstros e a tag `#c:hidden_from_recipe_viewers`.

### Tags e prioridade

- Uma chave `#tag` adiciona todos os itens dessa tag de item. As tags são lidas do registro de tags de itens do jogo
  quando o arquivo é carregado, então tags de datapacks e de mods funcionam.
- Um id de item exato sempre vence uma tag para o mesmo item.
- Se duas tags listam o mesmo item, vale a tag que aparece primeiro no arquivo.
- Um arquivo JSON inválido não é aplicado. O erro é registrado e os valores anteriores continuam em uso.

### Valores derivados das receitas

Itens sem valor declarado (e não excluídos) recebem um valor a partir das receitas. O cálculo:

1. Lê todas as receitas que o servidor conhece, com resultado e ingredientes, incluindo receitas de fabricação,
   fundição, corte de pedra, ferraria e receitas de mods que expõem ingredientes e resultados. Receitas especiais são
   ignoradas, e energia e fluidos são desconsiderados.
2. Para cada receita, pega a alternativa mais barata de cada espaço de ingrediente (uma tag conta como qualquer um dos
   seus itens), soma o custo, subtrai os itens que a receita devolve (como um balde vazio) e divide pelo número de itens
   que a receita produz.
3. Usa a receita mais barata para cada item. Repete até que nenhum valor mude, então ciclos (por exemplo, receitas de
   lingote e de bloco) se estabilizam no caminho mais barato.

Os valores derivados são guardados no banco de dados. Quando um servidor inicia e o banco já tem valores, eles são
carregados e não são calculados de novo.

### Mão de obra

`labor_percent` (em `[market]`) soma uma porcentagem a cada etapa de fabricação. Uma cadeia de três etapas com 10% de mão
de obra custa cerca de 33% a mais que seus ingredientes puros (`1,1 * 1,1 * 1,1`).

### Recalcular versus recarregar

| Comando | O que faz | Use depois de |
|---------|-----------|---------------|
| `/freetrade reload` | Relê a configuração do servidor e o `values.json`. Os valores declarados, o estoque de referência, o estoque inicial e os itens excluídos são atualizados. Os valores derivados já guardados no banco são mantidos. | Mudar a configuração, ou editar valores declarados, estoque de referência ou itens excluídos. |
| `/freetrade recalc` | Calcula de novo todos os valores derivados, a partir das receitas e dos valores declarados atuais, e os guarda. O estoque é mantido. | Adicionar, remover ou alterar mods ou receitas; mudar `labor_percent`; usar `setvalue`. |

`/freetrade setvalue <item> <valor>` grava o valor no `values.json` e recarrega. Ele não muda os valores derivados de
outros itens. Use `/freetrade recalc` para propagar a mudança.

### Itens sem preço

Um item é **sem preço** quando não tem valor declarado, não tem valor derivado de receita, não está excluído e não é um
item de dinheiro. Itens sem preço não são negociados. Liste-os com:

```
/freetrade unpriced [página]
```

Cada página mostra 15 itens. Para dar preço a um deles, adicione-o em `values` e use `/freetrade reload`, exclua-o, ou
instale o mod que fornece a receita dele e use `/freetrade recalc`.

## Linguagem da fórmula de preço

A chave `formula` em `[market]` dá o preço de **uma unidade** em um estoque dado.

### Variáveis, operadores e funções

| Elemento | Significado |
|----------|-------------|
| `x` | Estoque do item (unidades). Limitado a pelo menos 1. |
| `b` | Valor base do item em U.M. |
| `r` | Estoque de referência do item. Limitado a pelo menos 1. |
| `e`, `pi` | As constantes e (2,71828...) e pi (3,14159...). |
| `+`, `-`, `*`, `/` | Operações aritméticas. O sinal de menos unário é permitido, como em `-x`. |
| `^` | Potência. Associativa à direita, e o expoente pode ser negativo, como em `2^-1`. |
| `( )` | Agrupamento. |
| `min(a, b)`, `max(a, b)`, `pow(a, b)` | Mínimo, máximo e potência. Dois argumentos. |
| `log(a)` | Logaritmo na base 10. Um argumento. |
| `ln(a)` | Logaritmo natural. Um argumento. |
| `sqrt(a)`, `abs(a)`, `exp(a)` | Raiz quadrada, valor absoluto e e elevado a `a`. Um argumento. |

Números podem ser escritos como `2`, `0.5` ou `1e3`. Qualquer outro nome é um erro. O analisador verifica a fórmula
quando a configuração é carregada, então um erro de digitação é detectado antes de o mercado usá-la.

### Como um preço é calculado

- A fórmula dá o valor de uma unidade quando o estoque é `x`. O resultado é limitado: um resultado negativo ou NaN vira
  0, um valor base de 0 ou menos dá 0, e `price_ceiling` (quando não é `-1`) o limita.
- O resultado nunca fica abaixo de `min_unit_price`.
- Uma negociação é precificada unidade por unidade. Vender `n` unidades com estoque `S` paga as unidades avaliadas no
  estoque de `S+1` até `S+n`. Comprar `n` unidades com estoque `S` cobra as unidades avaliadas de `S` até `S-n+1`. Por
  isso, vender e recomprar as mesmas unidades custa o mesmo, e negociações grandes movem o preço enquanto acontecem.
- Cada negociação calcula primeiro o total real (preços unitários com derrapagem, impostos, durabilidade e o backoff do jogador), depois arredonda esse total para U.M. inteiras, metade para cima. Preços mostrados são sempre U.M. inteiras.
- Itens que valem menos de 1 U.M. são negociados em lotes: o número inteiro de unidades mais próximo que vale cerca de 1 U.M., e no mínimo 2. Vendas levam lotes inteiros, e compras arredondam para cima em lotes inteiros. O tamanho do lote acompanha o preço atual.
- Valores monetários são guardados em milésimos de U.M.

### Exemplos resolvidos

Considere `b = 10` U.M. e `r = 1000` (os padrões).

| Fórmula | Estoque `x` | Preço de uma unidade | Observações |
|---------|-------------|----------------------|-------------|
| `b * (r / x)^1` | 1000 | 10.0 | Com estoque `r`, o preço é o valor base. |
| `b * (r / x)^1` | 500 | 20.0 | Metade do estoque dobra o preço. |
| `b * (r / x)^1` | 2000 | 5.0 | O dobro do estoque reduz o preço pela metade. |
| `b * (r / x)^2` | 500 | 40.0 | Curva mais íngreme: metade do estoque quadruplica o preço. |
| `b * (r / x)^2` | 2000 | 2.5 | O dobro do estoque divide o preço por 4. |
| `b * (r / x)^2`, `price_ceiling = 25` | 250 | 25.0 | A fórmula dá 160, limitado ao teto de 25 U.M. |
| `b * min(4, r / x)` | 100 | 40.0 | `min` limita o multiplicador a 4, então o preço nunca passa de 4 vezes `b`. |

Vender 2 unidades com estoque 1000 pela fórmula padrão as avalia em `x = 1001` e `x = 1002`, porque cada unidade é
precificada com o estoque depois de ser somada: `10 * 1000/1001 + 10 * 1000/1002`, cerca de 19,97 U.M.

Arredondamento: 3 itens que valem 1,5 U.M. cada custam 4,5 U.M. no total, então o comprador paga 5 U.M. (metade para cima). Um item que vale 1,4 U.M. custa 1 U.M.
