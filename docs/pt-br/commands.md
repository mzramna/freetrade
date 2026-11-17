# Livre Comércio: comandos

Todo comando começa com `/freetrade`, ou com o atalho `/ft`. Os dois são iguais. Para o significado das configurações que
estes comandos mudam, veja a [referência de configuração](configuration.md).

## Sumário

- [Quem pode usar cada comando](#quem-pode-usar-cada-comando)
- [Comando de jogador](#comando-de-jogador)
- [Comandos de administrador](#comandos-de-administrador)
- [Contas: tesouraria e imposto](#contas-tesouraria-e-imposto)
- [Gatilhos](#gatilhos)
- [Exemplos](#exemplos)

## Quem pode usar cada comando

| Quem | Comandos |
|------|----------|
| Qualquer jogador | `price` |
| Operadores (nível de permissão 2 ou mais), e blocos de comando no nível padrão | Todos os demais: `recalc`, `reload`, `setstock`, `setvalue`, `unpriced`, `history`, `account`, `treasury`, `taxaccount`, `trigger` |

Comandos que mudam dinheiro mostram o novo saldo. Comandos que devolvem um número usam esse número como resultado do
comando, que blocos de comando podem ler (veja [Contas](#contas-tesouraria-e-imposto)).

## Comando de jogador

| Comando | Descrição |
|---------|-----------|
| `/freetrade price` | Mostra o preço do item na sua mão principal. |
| `/freetrade price <item>` | Mostra o preço de um item. A resposta traz o estoque, o preço de venda por unidade (depois do imposto de venda), o preço de compra por unidade (com imposto de compra), o tamanho do lote quando uma unidade vale menos de 1 U.M., e o valor base com a origem (declarado ou das receitas). Itens de dinheiro mostram o próprio valor. |

## Comandos de administrador

| Comando | Descrição |
|---------|-----------|
| `/freetrade recalc` | Calcula de novo todos os valores derivados a partir das receitas atuais e dos valores declarados, e os guarda. A resposta informa quantos itens foram derivados e quantos ficaram sem preço, e quanto tempo levou. Roda em segundo plano. |
| `/freetrade reload` | Reaplica a configuração do servidor e relê o `values.json`. Os valores derivados já guardados são mantidos. |
| `/freetrade setstock <item> <quantidade>` | Define o estoque de um item. A quantidade é um número inteiro, zero ou mais. |
| `/freetrade setvalue <item> <valor>` | Grava o valor base do item no `values.json` e recarrega. Um valor `0` remove o valor declarado. Os valores derivados de outros itens não mudam até o `recalc`. |
| `/freetrade unpriced [página]` | Lista os itens sem preço, 15 por página. A página 1 é o padrão. |
| `/freetrade history [item]` | Mostra as últimas 10 transações, opcionalmente só de um item. Cada linha mostra o id do servidor, a hora, o jogador, a quantidade, o item, o valor, o imposto e o estoque depois da negociação. |

## Contas: tesouraria e imposto

A associação tem duas contas:

- **tesouraria** (`treasury`): o dinheiro da própria associação. As vendas para a associação são pagas por ela, e as
  compras da associação somam nela. Quando a tesouraria é infinita (`treasury_initial = -1`), as negociações não a
  alteram. Com a tesouraria finita e vazia, a associação para de comprar dos jogadores; os jogadores sempre podem comprar dela.
- **imposto** (`tax`, a conta de imposto dos operadores): recebe os impostos de venda e de compra. O nome dela nos
  comandos é `tax`.

### Sintaxe

```
/freetrade account <conta> balance
/freetrade account <conta> query
/freetrade account <conta> query <operador> <valor>
/freetrade account <conta> deposit <quantidade>
/freetrade account <conta> remove <quantidade>
/freetrade account <conta> withdraw <quantidade>
/freetrade account <conta> withdraw <quantidade> <item-de-dinheiro>
/freetrade account <conta> give <jogador> <quantidade>
```

Atalhos para as duas contas:

| Atalho | Equivale a |
|--------|------------|
| `/freetrade treasury ...` | `/freetrade account treasury ...` |
| `/freetrade taxaccount ...` | `/freetrade account tax ...` |

As quantidades são em U.M., e podem ter decimais (por exemplo, `12.5`). O mínimo é 0,001. O nome da conta vem antes do
subcomando: `account treasury query`, e não `account query treasury`.

| Subcomando | O que faz |
|------------|-----------|
| `balance` | Mostra o saldo. O resultado do comando é o saldo em U.M. inteiras. A nota da tesouraria aparece quando ela é infinita. |
| `query` | Sem operador, funciona como `balance`. Com `<operador> <valor>`, compara o saldo com o valor. Responde "Condição verdadeira" e devolve `1`, ou "Condição falsa" e devolve `0`. Os operadores são `>=`, `>`, `<=`, `<` e `=`. |
| `deposit <quantidade>` | Acrescenta U.M. à conta. Nenhum item é tirado de você. |
| `remove <quantidade>` | Subtrai U.M. da conta. Falha se a conta não tiver o suficiente. Nenhum item é dado a você. |
| `withdraw <quantidade>` | Tira U.M. da conta e dá a você itens de dinheiro em troca, começando pelos maiores. A quantidade é arredondada para baixo até o que os itens de dinheiro conseguem formar. O resultado do comando é o total de U.M. inteiras retiradas. |
| `withdraw <quantidade> <item-de-dinheiro>` | Igual, mas dá apenas esse item de dinheiro. A quantidade é arredondada para baixo até um múltiplo do valor do item. Por exemplo, `withdraw 50 minecraft:emerald_block` tira 45 U.M. e dá 5 blocos de esmeralda. |
| `give <jogador> <quantidade>` | Igual a `withdraw`, mas os itens de dinheiro vão para o jogador indicado. |

Qualquer outro nome de conta é aceito, mas é uma conta separada que o mercado não usa.

**Blocos de comando e console.** `withdraw` sem `give` entrega os itens ao jogador que executa o comando. Um bloco de
comando ou o console não tem jogador, então o comando **falha com uma mensagem e não tira dinheiro**. Em automações, use
`give <jogador> <quantidade>`.

## Gatilhos

Um gatilho executa um comando quando uma conta chega a uma condição. Os gatilhos são salvos no banco de dados, são
verificados depois de cada negociação ou mudança de conta, e também periodicamente (veja `check_interval_ticks`).

### Sintaxe

```
/freetrade trigger list
/freetrade trigger add <id> <conta> <operador> <valor> once run <comando>
/freetrade trigger add <id> <conta> <operador> <valor> repeat run <comando>
/freetrade trigger add <id> <conta> <operador> <valor> once <custo> <espera> <comando>
/freetrade trigger add <id> <conta> <operador> <valor> repeat <custo> <espera> <comando>
/freetrade trigger remove <id>
/freetrade trigger reset <id>
/freetrade trigger enable <id>
/freetrade trigger disable <id>
```

| Parte | Significado |
|-------|-------------|
| `<id>` | Um nome único para o gatilho, em uma palavra. Adicionar um id já existente substitui esse gatilho. |
| `<conta>` | `treasury` ou `tax`. |
| `<operador>` | `>=`, `>`, `<=`, `<` ou `=`. |
| `<valor>` | O limite, em U.M. |
| `once` | O gatilho dispara uma vez. Depois de disparar, fica desativado. `reset` o ativa de novo. |
| `repeat` | O gatilho dispara de novo cada vez que a condição passa de falsa para verdadeira. Com espera, dispara no máximo uma vez por espera enquanto a condição continuar verdadeira. |
| `<custo>` | U.M. tiradas da **mesma conta** quando o gatilho dispara, antes de o comando rodar. O gatilho não dispara se o saldo for menor que o custo. O custo é tirado na mesma transação do banco que verifica a condição. |
| `<espera>` | Segundos entre dois disparos de um gatilho `repeat`. `0` significa sem espera. |
| `<comando>` | O comando a executar, sem a `/` inicial. Ocupa o resto da linha. Roda com o nível de permissão mais alto (4). |

O comando pode usar estes marcadores, que são trocados antes de rodar:

| Marcador | Trocado por |
|----------|-------------|
| `{id}` | O id do gatilho. |
| `{account}` | O nome da conta. |
| `{balance}` | O saldo da conta depois que o custo foi tirado. |
| `{cost}` | O custo deste disparo. |
| `{count}` | Quantas vezes o gatilho disparou, incluindo este disparo. |

`trigger list` mostra cada gatilho como `id: conta operador limite | modo cost=... cooldown=...s fired=N -> /comando`.
Gatilhos desativados aparecem com `[off]`.

`trigger reset <id>` zera a contagem de disparos, arma o gatilho e o ativa. `enable` e `disable` mudam só o estado de
ativação.

## Exemplos

```
/ft price                                 (o item na mão)
/ft price minecraft:oak_log               (um item específico)
/ft setvalue minecraft:diamond 32         (declara um novo valor base)
/ft reload                                (aplica)
/ft recalc                                (propaga para os itens derivados)
/ft setstock minecraft:iron_ingot 5000    (define o estoque)
/ft unpriced 2                            (segunda página dos itens sem preço)
/ft history minecraft:diamond             (negociações recentes de diamante)
/ft treasury deposit 500                  (acrescenta 500 U.M. à tesouraria)
/ft taxaccount balance                    (mostra a conta de imposto)
/ft taxaccount withdraw 50 minecraft:emerald_block
/ft taxaccount give Alex 20               (paga 20 U.M. do imposto a um jogador)
/ft account treasury query                (devolve o saldo em U.M. inteiras)
/ft account treasury query >= 1000        (1 se a tesouraria tem pelo menos 1000 U.M., senão 0)
/ft trigger add bonus tax >= 500 once run say Meta de imposto atingida!
```

Exemplos de gatilhos, com explicações, estão no [guia do servidor e do modpack](server-modpack-guide.md#gatilhos-e-blocos-de-comando).
