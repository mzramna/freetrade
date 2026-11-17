<img src="docs/icon.png" width="128" alt="Ícone do Livre Comércio" align="right">

# Livre Comércio (Free Trade)

[English](README.md) | **Português (Brasil)**

Mod de economia para NeoForge, feito para modpacks e servidores. Uma **Associação Comercial** global guarda o
estoque de todos os itens, e os preços seguem a oferta: quanto mais estoque, mais barato. Itens sem valor declarado
recebem um valor derivado das suas receitas, então itens de outros mods também ganham preço automaticamente.

## Downloads
Baixe o jar da sua versão do Minecraft em [Releases](https://github.com/mzramna/freetrade/releases/latest):

| Jar                           | Minecraft | NeoForge | Packs |
|-------------------------------|-----------|----------|-------|
| `freetrade-1.21.1-<ver>.jar`  | 1.21.1    | 21.1.x   | família ATM10 |
| `freetrade-26.1.2-<ver>.jar`  | 26.1.2    | 26.1.x   | ATM11 |

Instale **no servidor e nos clientes**. Todos os servidores que usam a mesma economia devem rodar o mesmo modpack.

## Conceitos
- **Associação Comercial:** o mercado do servidor inteiro. Estoque e preços ficam salvos no banco.
- **U.M. (unidade monetária):** a moeda. Por padrão, 1 esmeralda = 1 U.M. e 1 bloco de esmeralda = 9 U.M.
  Os itens de dinheiro são definidos em `currency_items`. As moedas e contas bancárias do Lightman's Currency
  também funcionam quando o mod está instalado.
- **Fórmula de preço:** `y = b * (r / x)^1`, onde `x` é o estoque atual, `b` o valor base e `r` o estoque de referência.
  Menos estoque, preço mais alto. A fórmula padrão não tem teto (`max_price = -1`). A expressão é configurável,
  e cada item pode sobrescrever `r`.
- **Slippage:** compras grandes são precificadas unidade por unidade, então o preço muda durante a transação.
- **Lotes em U.M. inteiras:** todo pagamento é um número inteiro de U.M., arredondado a favor da associação.
  Itens baratos são negociados em lote, ex.: 2 sticks = 1 U.M.
- **Estoque inicial** começa no estoque de referência `r`, para o primeiro vendedor não receber o preço máximo.

## Valores derivados das receitas
Itens sem valor declarado recebem o valor da receita mais barata que os produz: crafting, fornalha, cortador de pedras,
ferraria e máquinas de mods (energia e fluidos são ignorados). A mão de obra é uma porcentagem (padrão 0%).
Os valores são calculados na primeira inicialização ou com `/freetrade recalc`, e depois ficam salvos no banco.

Os preços **propagam**: o valor base de um item é o preço atual dos ingredientes dividido pelo rendimento da receita,
e depois a fórmula aplica o estoque do próprio item. Vender muita madeira deixa tábuas e sticks mais baratos também.
Itens declarados na config usam o próprio valor e não herdam dos ingredientes. Itens sem receita e sem valor ficam
fora do mercado e aparecem em `/freetrade unpriced`.

## Arquivo de valores
`config/freetrade/values.json` guarda valor base, estoque de referência e estoque inicial por item.

```jsonc
{
  "values": { "minecraft:diamond": 16, "#minecraft:logs": 4 },   // id do item ou #tag
  "reference_stock": { "minecraft:cobblestone": 4096 },          // r específico por item
  "initial_stock": { "minecraft:iron_ingot": 256 },              // estoque inicial
  "excluded": ["minecraft:bedrock", "#c:ores/netherite"]          // nunca negociados
}
```

## Representante Comercial (villager)
Um villager desempregado vira **Representante Comercial** ao encontrar um bloco **Compra Automática** (sua estação de trabalho).
- **Vender:** uma grade com um tipo de item por vez. Coloque quantos stacks quiser do mesmo item e veja o valor.
- **Comprar:** escolha um item no catálogo com busca, defina a quantidade, pague com itens de dinheiro e receba o troco
  nas maiores denominações possíveis.

## Ponto de Venda
Bloco que guarda um único item, escolhido por um ghost slot (arraste do JEI, EMI ou REI).
- Capacidade: 54 stacks x (1 + nº de baús/barris encostados), com no máximo 5 barris. O aumento é virtual: se um barril
  sai, o bloco deixa de aceitar itens até o estoque caber de novo.
- Com **sinal de redstone**, vende o conteúdo para a associação.
- A GUI mostra a quantidade guardada, o item, o preço por unidade, o saldo e setas de moeda (↑/↓) para saque manual.
- Itens de dinheiro inseridos viram saldo, então o bloco funciona como máquina de troco. Eles não são vendidos como mercadoria.
- Funil ou outra automação só retira o item de dinheiro selecionado, com redstone ativa e saldo suficiente.

## Compra Automática
Bloco com ghost slot para o **item-alvo**. Itens de dinheiro inseridos viram saldo, e o bloco compra o item-alvo
enquanto houver saldo, estoque na associação e espaço. A capacidade funciona como no Ponto de Venda.
A automação só extrai os itens comprados com redstone ativa. Só o dono e os OPs abrem a GUI.

## Guia da Associação Comercial
Craftado com um livro e uma esmeralda. Clique com o botão direito para abrir uma tela com duas abas:
1. **Como funciona:** páginas sobre a fórmula, U.M., villagers, blocos, imposto, backoff e tesouraria.
2. **Catálogo:** busca por nome ou id, preço de venda e de compra por unidade, lote para 1 U.M., estoque e filtro
   todos / em estoque / sem estoque.

## Imposto, backoff e tesouraria
- `tax_on_sell_percent` e `tax_on_buy_percent` enviam uma parte de cada transação para a **conta de imposto** dos OPs
  (`/freetrade taxaccount`).
- **Backoff anti-"banco de inventário":** se o jogador vende e recompra o mesmo item muitas vezes em pouco tempo, a
  recompra fica `backoff_percent` mais cara por nível. O nível decai com o tempo e vale por jogador e por item.
- **Tesouraria:** o dinheiro da associação. O padrão `-1` é infinito.

## Banco de dados
- **SQLite** (padrão): arquivo na pasta do mundo. Bom para single player e servidores pequenos.
- **Postgres** (recomendado para servidor): pode ser **compartilhado por vários servidores**, formando uma só economia.
  Cada transação é atômica no banco, e o histórico registra o `server_id`.

## Comandos
Alias: `/ft`. Os comandos de admin exigem OP (nível 2).

| Comando | Descrição |
|---------|-----------|
| `/freetrade price [item]` | Preço e estoque de um item (o item na mão, por padrão). |
| `/freetrade recalc` | Recalcula os valores derivados das receitas. |
| `/freetrade reload` | Recarrega a configuração. |
| `/freetrade setstock <item> <n>` | Define o estoque de um item. |
| `/freetrade setvalue <item> <v>` | Define o valor base de um item (use `recalc` para propagar). |
| `/freetrade unpriced [página]` | Lista os itens sem preço. |
| `/freetrade history [item]` | Transações recentes. |
| `/freetrade account <treasury\|tax> balance` | Mostra o saldo. |
| `/freetrade account <treasury\|tax> query [op valor]` | Saldo, ou 1/0 quando comparado com um valor. |
| `/freetrade account <treasury\|tax> deposit` | Deposita os itens de dinheiro na mão. |
| `/freetrade account <treasury\|tax> withdraw <qtd> [item]` | Saca U.M. como itens de dinheiro. |
| `/freetrade account <treasury\|tax> remove` | Remove os itens de dinheiro do jogador. |
| `/freetrade account <treasury\|tax> give <jogador> <qtd>` | Dá U.M. da conta para um jogador. |
| `/freetrade trigger add <id> <conta> <op> <valor> <once\|repeat> run <comando>` | Registra um gatilho. |
| `/freetrade trigger add <id> <conta> <op> <valor> <modo> <custo> <cooldownSegundos> <comando>` | Gatilho com custo e cooldown. |
| `/freetrade trigger list \| remove <id> \| reset <id> \| enable <id> \| disable <id>` | Gerencia os gatilhos. |

`treasury ...` e `taxaccount ...` são aliases de `account treasury ...` e `account tax ...`.

Placeholders do comando do gatilho: `{id}`, `{account}`, `{balance}`, `{cost}`, `{count}`.

### Exemplos com blocos de comando
- Um comparador ligado em `/freetrade account query treasury >= 1000` emite sinal quando a tesouraria tem pelo menos 1000 U.M.
- `/freetrade trigger add rico treasury >= 1000 repeat run say Tesouraria cheia: {balance} U.M.` avisa toda vez que a
  tesouraria cruza 1000 subindo.
- `/freetrade trigger add bonus treasury >= 500 once 100 0 run give @a minecraft:diamond` dispara uma vez e gasta
  100 U.M. da tesouraria quando dispara.

## Configuração
- Config do servidor: `serverconfig/freetrade-server.toml` do mundo (ou `defaultconfigs/` no modpack). Tem a fórmula,
  imposto, banco, backoff e tesouraria.
- Valores: `config/freetrade/values.json` (veja acima).

## Mais documentação
- Guia do jogador: [docs/pt-br/player-guide.md](docs/pt-br/player-guide.md)
- Guia do servidor e do modpack: [docs/pt-br/server-modpack-guide.md](docs/pt-br/server-modpack-guide.md)
- Comandos: [docs/pt-br/commands.md](docs/pt-br/commands.md)
- Configuração: [docs/pt-br/configuration.md](docs/pt-br/configuration.md)
- Mercados regionais futuros (nota de desenho, não implementado): [docs/regions.md](docs/regions.md)

## Compilando
| Branch   | Java |
|----------|------|
| `1.21.1` | 21   |
| `26.1`   | 25   |

```bash
./gradlew build              # jar em build/libs
./gradlew runGameTestServer  # GameTests automatizados
```

O CI roda build + GameTests em todo push e pull request. Enviar uma tag `v*`, ou fazer push no branch `release`,
compila e testa os dois branches e publica um release com os dois jars.

## Licença
[MIT](LICENSE)
