# Livre Comércio: guia do jogador

Este guia é para jogadores. Ele explica como funciona a Associação Comercial, como vender e comprar, e como usar o
Ponto de Venda e a Compra Automática. Para as configurações do servidor, veja a
[referência de configuração](configuration.md) e o [guia do servidor e do modpack](server-modpack-guide.md).

## Sumário

- [O que é a Associação Comercial](#o-que-é-a-associação-comercial)
- [Dinheiro: U.M. e itens de dinheiro](#dinheiro-um-e-itens-de-dinheiro)
- [O Guia da Associação Comercial (livro)](#o-guia-da-associação-comercial-livro)
- [Como achar ou criar um Representante Comercial](#como-achar-ou-criar-um-representante-comercial)
- [Vender para o Representante Comercial](#vender-para-o-representante-comercial)
- [Comprar do Representante Comercial](#comprar-do-representante-comercial)
- [Ponto de Venda](#ponto-de-venda)
- [Compra Automática](#compra-automática)
- [Por que os preços mudam](#por-que-os-preços-mudam)
- [Backoff anti-armazenamento](#backoff-anti-armazenamento)
- [Consultar um preço com /freetrade price](#consultar-um-preço-com-freetrade-price)
- [Solução de problemas](#solução-de-problemas)

## O que é a Associação Comercial

A Associação Comercial é um mercado único, compartilhado por todos no servidor. Ela mantém um **estoque** de cada
item que negocia. Cada item tem um preço que depende desse estoque:

- quando a associação tem muito de um item, o item é barato;
- quando tem pouco, o item é caro;
- todo item tem um **valor base** (quanto ele vale em U.M.). Itens que ninguém declarou recebem o valor base a partir
  das receitas que os fabricam.

Você pode vender itens para a associação e comprar itens dela. Compra e venda usam os mesmos preços, então recomprar
exatamente o que você vendeu não custa nada a mais, exceto impostos e o [backoff](#backoff-anti-armazenamento).

Há três formas de negociar:

| Forma | O que faz |
|-------|-----------|
| Representante Comercial (aldeão) | Vende uma pilha de um tipo de item, ou compra do catálogo, em uma janela. |
| Ponto de Venda (bloco) | Guarda um item e o vende enquanto recebe sinal de redstone. |
| Compra Automática (bloco) | Compra um item para você, com o dinheiro que você colocou nele. |

## Dinheiro: U.M. e itens de dinheiro

A moeda é a **U.M.** (unidade monetária). Por padrão:

| Item de dinheiro | Valor |
|------------------|-------|
| Esmeralda | 1 U.M. |
| Bloco de esmeralda | 9 U.M. |

O servidor pode mudar essa lista. Se o Lightman's Currency estiver instalado, as moedas dele também podem ser dinheiro,
e você pode usar sua conta bancária do Lightman's (veja [Comprar do Representante Comercial](#comprar-do-representante-comercial)).

O dinheiro nunca é negociado como mercadoria. Você não pode vender esmeraldas para a associação.

Quando recebe troco, ele volta em itens de dinheiro, começando pelos maiores. Por exemplo, 10 U.M. de troco com o dinheiro
padrão são um bloco de esmeralda (9 U.M.) e uma esmeralda (1 U.M.).

## O Guia da Associação Comercial (livro)

O Guia da Associação Comercial é um livro que mostra o mercado. Crafte-o com **um livro e uma esmeralda** (em qualquer
disposição). Clique com o botão direito para abrir o guia. Ele tem duas abas.

**Como funciona** tem sete páginas curtas:

1. A Associação Comercial e a U.M.
2. Preços e oferta.
3. Valores pelas receitas.
4. O Representante Comercial.
5. Ponto de Venda.
6. Compra Automática.
7. Impostos, backoff e tesouraria. As taxas atuais aparecem no cabeçalho.

**Catálogo** lista todos os itens que a associação negocia:

- uma caixa de busca (por nome ou id do item);
- o preço de venda e o preço de compra de uma unidade (a compra mostra "Sem estoque" quando não há estoque);
- o tamanho do lote, quando uma unidade vale menos de 1 U.M. (por exemplo, "2 unidades = 1 U.M.");
- o estoque atual;
- um filtro de **Todos**, **Em estoque** e **Sem estoque**;
- um botão **Atualizar**, que pede ao servidor os preços e estoques atuais.

Depois de abrir o guia ou uma janela de negociação, passar o mouse sobre um item no inventário também mostra o preço.
Segure Shift para ver o tamanho do lote.

O catálogo mostra o preço de compra sem o seu backoff pessoal. Se você tiver uma penalidade de backoff, o preço real é
mais alto. Veja [Backoff anti-armazenamento](#backoff-anti-armazenamento).

## Como achar ou criar um Representante Comercial

Um **Representante Comercial** é um aldeão com profissão própria. Você consegue um colocando um bloco de **Compra
Automática**:

- um aldeão desempregado que encontra uma Compra Automática como posto de trabalho vira Representante Comercial;
- cada Compra Automática aceita um representante;
- um aldeão que já tem emprego não muda.

Para fabricar a Compra Automática, crafte assim (P = qualquer tábua, E = esmeralda, B = barril, H = funil):

```
P E P
P B P
P H P
```

Coloque a Compra Automática e espere perto dela um aldeão desempregado a escolher. Clique com o botão direito no
Representante Comercial para abrir a janela de negociação. Qualquer jogador pode usar o representante, não só quem
colocou a Compra Automática.

Um aldeão bebê não abre a janela de negociação.

## Vender para o Representante Comercial

O lado esquerdo da janela é a grade de venda:

- aceita **um tipo de item por vez**, até **27 pilhas** (o servidor pode reduzir esse limite);
- itens de dinheiro não podem ser colocados na grade;
- itens encantados, renomeados ou modificados são recusados, e a mensagem avisa. Qualquer pilha com dados extras
  (encantamentos, nomes, conteúdo e similares) é recusada;
- ferramentas e armaduras danificadas pagam na proporção da durabilidade restante. O servidor pode recusar itens
  danificados por completo;
- itens que não podem ser negociados são recusados.

Quando você aperta **Vender**, a associação paga **apenas U.M. inteiras**. Ela pega só as unidades necessárias para
ganhar a U.M. inteira que paga, e devolve o resto. Por exemplo, se 3 gravetos pagariam 1,5 U.M., a associação pega 2
gravetos, paga 1 U.M. e devolve o terceiro graveto.

O pagamento sai em itens de dinheiro, começando pelos maiores. Se a opção de banco do Lightman's estiver ligada, o
pagamento vai para a sua conta bancária.

## Comprar do Representante Comercial

O lado direito da janela é o catálogo:

1. Pesquise ou filtre a lista e selecione um item.
2. Defina a quantidade com **-64**, **-1**, **+1** e **+64**. Você pode comprar até 2.304 unidades de uma vez.
3. Confira o custo estimado e aperte **Comprar**.

Regras de pagamento:

- o preço é pago com itens de dinheiro do seu inventário. A janela usa os seus menores itens de dinheiro primeiro, e
  depois devolve o troco;
- a compra é **tudo ou nada**. Se você não tiver dinheiro suficiente para a quantidade inteira, nada é comprado, e a
  mensagem diz quanto falta;
- se o preço mudou entre o seu clique e a compra, você verá uma mensagem e poderá tentar de novo;
- os preços de compra incluem o imposto de compra e, quando se aplica a você, o seu backoff pessoal;
- um troco menor que o menor item de dinheiro não pode ser devolvido. Com apenas esmeraldas como dinheiro, o preço da
  compra pode ter uma fração de U.M. que se perde. Tenha troco exato sempre que possível.

**Botão Banco:** só aparece quando o Lightman's Currency está instalado e o servidor permite. Quando está ligado, a
compra é paga pela sua conta bancária do Lightman's, e o troco é depositado nela. Se o banco não puder pagar, a compra
é recusada.

## Ponto de Venda

O Ponto de Venda guarda um item e o vende para a associação enquanto recebe um **sinal de redstone** (qualquer energia
de redstone encostada no bloco).

Para fabricá-lo, crafte (P = qualquer tábua, E = esmeralda, C = um baú de madeira, H = funil):

```
P E P
P C P
P H P
```

**Definir o item.** Abra o bloco e use o slot fantasma **Alvo**. Clique nele segurando um item, ou arraste um item do
JEI ou do EMI até ele. Clique com o cursor vazio para limpar. Você só pode trocar o item quando não houver nenhum item do
tipo antigo guardado.

**Guardar itens.** Coloque itens do tipo alvo no slot de entrada, ou deixe um funil inserir. Os itens são acrescentados
ao armazenamento, não vendidos na hora.

**Capacidade.** O armazenamento guarda 54 pilhas do item alvo (uma pilha é o tamanho normal de pilha do item). Cada baú
ou barril encostado no bloco acrescenta mais um conjunto de 54 pilhas, até cinco recipientes, então o máximo é 6 vezes a
capacidade base. Baús incluem baús armadilha. Se você remover um recipiente e os itens guardados não couberem mais, o
bloco para de aceitar itens até que caibam de novo.

**Venda.** Enquanto o bloco está energizado, ele vende até 64 unidades cerca de uma vez por segundo (o servidor pode
mudar a velocidade e o tamanho do lote). O dinheiro vai para o saldo do bloco, que guarda valores exatos (frações
incluídas).

**Dinheiro no bloco.**

- Use os botões **^** e **v** para escolher qual item de dinheiro o bloco entrega.
- Clique com o botão esquerdo no slot de dinheiro para pegar um item de dinheiro. Clique com o botão direito, ou com
  Shift, para pegar até uma pilha (64).
- **Tudo** pega o saldo inteiro como troco.
- **Pegar** devolve ao seu inventário até 64 itens guardados.
- Dinheiro colocado no slot de entrada vira saldo, então o bloco funciona como máquina de troco.
- Itens de dinheiro nunca são vendidos como mercadoria.

**Funis e automação.** Um funil pode colocar no bloco itens do alvo e dinheiro. Um funil só pode tirar o item de dinheiro
selecionado, e só enquanto o bloco estiver energizado e tiver saldo suficiente.

**Dono.** O jogador que coloca o bloco é o dono. Só o dono e os operadores do servidor podem abrir o bloco ou quebrá-lo.
Um bloco colocado sem jogador (por exemplo, por um comando) não tem dono, e qualquer um pode usá-lo.

As mensagens de status da interface dizem por que o bloco não está vendendo, por exemplo "Aguardando sinal de
redstone", "Sem estoque" ou "Armazenamento cheio".

## Compra Automática

A Compra Automática compra para você um **item alvo**, pago com o dinheiro que você colocou nela.

Para fabricá-la, veja [Como achar ou criar um Representante Comercial](#como-achar-ou-criar-um-representante-comercial).

**Definir o alvo.** Use o mesmo slot fantasma do Ponto de Venda. Você só pode trocá-lo quando não há itens guardados.

**Dinheiro.** Os itens de dinheiro que você coloca nela viram saldo. O bloco compra o alvo enquanto tiver saldo, a
associação tiver estoque e houver espaço no armazenamento. Ele compra até 64 unidades por ciclo, ao preço de compra do
catálogo, e **não** precisa de sinal de redstone para comprar.

**Retirar itens.** O dono pode pegar de volta os itens comprados e o saldo que sobrou. Funis só podem tirar os itens
comprados enquanto o bloco estiver energizado.

**Capacidade e dono** funcionam como no Ponto de Venda. O bloco também é o posto de trabalho de um Representante Comercial.

**Backoff.** As compras feitas pela Compra Automática contam para o backoff do dono, como as negociações pessoais dele.

## Por que os preços mudam

- **Oferta.** Vender um item aumenta o estoque, o que baixa o preço. Comprar reduz o estoque, o que sobe o preço. Por
  padrão, o preço segue a fórmula `b * (r / x)^1`, em que `x` é o estoque, `b` o valor base e `r` o estoque de
  referência. Dobrar o estoque divide o preço por dois.
- **Derrapagem.** Uma negociação grande é precificada unidade por unidade. Cada unidade que você vende baixa o preço da
  próxima, e cada unidade que você compra sobe o preço da próxima.
- **Propagação.** O valor base de um item fabricado acompanha os preços atuais dos ingredientes. Se muita gente vende
  toras, as toras ficam mais baratas, e as tábuas e os gravetos feitos com elas também.
- **Valores pelas receitas.** Itens que ninguém declarou recebem um valor da receita mais barata que os fabrica. Itens
  de mods também são precificados assim.
- **Teto.** O servidor pode limitar o preço de uma unidade, para que um item muito escasso não custe mais que o teto.
- **Impostos.** O servidor pode acrescentar uma porcentagem às vendas e às compras. Os impostos vão para a conta de
  imposto dos operadores, não para a tesouraria.

O guia e o comando `/freetrade price` sempre mostram os números atuais.

## Backoff anti-armazenamento

A associação não é um depósito. Se você vende um item e o recompra dentro da **janela de backoff** (padrão 600 segundos,
ou seja, 10 minutos), isso conta como uma **ida e volta**. Ao atingir o **limite** de idas e voltas (padrão 3), cada nova
ida e volta aumenta o seu preço de compra daquele item em **10% por nível**, até no máximo 20 níveis (+200%).

A sua penalidade cai um nível a cada janela inteira sem idas e voltas. A penalidade é contada por jogador e por item. Os
operadores do servidor podem mudar todos esses valores; veja a [referência de configuração](configuration.md#fees).

## Consultar um preço com /freetrade price

```
/freetrade price            (o item na mão principal)
/freetrade price <item>     (qualquer item, pelo id ou nome)
```

Qualquer jogador pode usar. A resposta mostra:

- o estoque na associação;
- quanto a associação paga por unidade a você (depois do imposto de venda);
- quanto a associação cobra por unidade (com imposto de compra, para uma unidade no estoque atual);
- quantas unidades formam 1 U.M., quando uma unidade vale menos de 1 U.M.;
- o valor base e se ele foi declarado pelo servidor ou calculado pelas receitas.

Itens de dinheiro mostram o próprio valor. Itens que não são negociados dizem "não é negociado".

## Solução de problemas

| Mensagem ou problema | O que significa e o que fazer |
|----------------------|-------------------------------|
| "O mercado está offline no momento." | O servidor não conseguiu abrir o banco de dados. Peça a um operador que verifique o log do servidor. |
| "Erro no banco de dados. Tente de novo." | Uma negociação falhou. Tente de novo. Se continuar, avise um operador. |
| "X não é negociado." | O item não tem valor nem receita. Operadores podem listar esses itens com `/freetrade unpriced`. |
| "A associação não tem estoque de X." | O estoque está zerado. Espere alguém vender, ou compre outro item. |
| "Você precisa de N U.M. para isto." | Você não tem dinheiro suficiente no inventário (ou no banco). A compra foi cancelada. |
| "A associação não tem dinheiro." | A tesouraria é finita e está vazia, então ela não pode pagar vendas. |
| "Itens encantados, renomeados ou modificados são recusados." | Pilhas com dados extras não podem ser vendidas. Use itens comuns. |
| "Somente o dono (nome) e os operadores podem usar este bloco." | Outra pessoa colocou o bloco. Fale com o dono ou com um operador. |
| "O preço mudou. Tente de novo." | O preço mudou entre o seu clique e a compra. Tente de novo. |
| O Ponto de Venda não vende. | Confira se ele recebe sinal de redstone e se a interface mostra um alvo e itens guardados. |
| A Compra Automática não compra. | Confira o saldo, o alvo, o estoque e o espaço livre na interface. |
