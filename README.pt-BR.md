<img src="docs/icon.png" width="128" alt="Ícone do Livre Comércio" align="right">

# Livre Comércio (Free Trade)

[English](README.md) | **Português (Brasil)**

Mod de economia para NeoForge, feito para modpacks e servidores. Uma **Associação Comercial** global guarda o estoque de
todos os itens, e os preços seguem a oferta: quanto mais estoque, mais barato. Itens sem valor declarado recebem um valor
derivado das suas receitas, então itens de outros mods também ganham preço automaticamente.

## Downloads

Baixe o jar em [Releases](https://github.com/mzramna/freetrade/releases/latest):

| Jar                          | Minecraft | NeoForge | Observações |
|------------------------------|-----------|----------|-------------|
| `freetrade-1.21.1-<ver>.jar` | 1.21.1    | 21.1.x   | Java 21 |

Uma versão para o Minecraft 26.1 (NeoForge 26.1.x, Java 25) está planejada e aparecerá aqui quando for lançada.

Instale **no servidor e nos clientes**. Todo servidor que compartilha uma economia deve usar o mesmo modpack.

## Recursos

- **Preços pela oferta.** Cada item tem um valor base e uma fórmula que move o preço conforme o estoque. A fórmula padrão é
  `b * (r / x)^1`, e o servidor pode mudá-la, colocar um teto ou usar uma curva mais íngreme.
- **Valores pelas receitas.** Itens sem valor declarado recebem um valor da receita mais barata que os fabrica, e o preço
  acompanha os ingredientes.
- **Derrapagem.** Negociações grandes são precificadas unidade por unidade, então o preço se move enquanto você negocia.
- **Vendas inteiras ao Representante Comercial.** Só as unidades que ganham U.M. inteiras são pegas, e o resto é devolvido.
- **Representante Comercial.** Um aldeão desempregado vira representante na Compra Automática. Venda um tipo de item ou
  compre do catálogo na janela dele.
- **Ponto de Venda e Compra Automática.** Máquinas que vendem um item com sinal de redstone, e compram um item com o dinheiro
  que você coloca nelas. Têm capacidade que cresce com baús e barris encostados.
- **Guia da Associação Comercial.** Um livro com um tour em "Como funciona" e um catálogo pesquisável, com preços de compra e
  venda, estoque e filtro de estoque.
- **Impostos, backoff e tesouraria.** Impostos opcionais de venda e compra para uma conta de operadores, um backoff
  anti-armazenamento para idas e voltas, e uma tesouraria finita opcional.
- **Dinheiro.** Esmeraldas e blocos de esmeralda por padrão, outros itens se o servidor escolher, e moedas e contas bancárias
  do Lightman's Currency quando esse mod está instalado.
- **Banco de dados.** SQLite para um mundo só, ou PostgreSQL, que vários servidores podem compartilhar como uma economia.
- **Gatilhos.** Comandos que rodam quando a tesouraria ou a conta de imposto chega a uma condição, gastando dinheiro antes, se
  configurado. Os operadores os criam; veja a [referência de comandos](docs/pt-br/commands.md#gatilhos) para a sintaxe.

## Início rápido

**Jogadores.** Crafte um **Guia da Associação Comercial** com um livro e uma esmeralda, e clique com o botão direito nele para
ler o mercado. Para negociar, coloque uma **Compra Automática** perto de um aldeão desempregado, e clique com o botão direito no
novo Representante Comercial. O [guia do jogador](docs/pt-br/player-guide.md) cobre vendas, compras, o Ponto de Venda e a
Compra Automática.

**Donos de servidor e criadores de modpack.** Instale o jar no servidor e em todos os clientes. Ajuste as configurações em
`serverconfig/freetrade-server.toml` (ou em `defaultconfigs/` no modpack), e declare os valores dos itens em
`config/freetrade/values.json`. Depois de adicionar mods, rode `/freetrade recalc`. O
[guia do servidor e do modpack](docs/pt-br/server-modpack-guide.md) explica a escolha do banco, o compartilhamento de uma
economia e os backups.

## Documentação

- [Guia do jogador](docs/pt-br/player-guide.md): como negociar e como o mercado funciona.
- [Guia do servidor e do modpack](docs/pt-br/server-modpack-guide.md): instalação, bancos de dados, economias compartilhadas,
  preenchimento de valores para um modpack, configurações econômicas e solução de problemas.
- [Referência de configuração](docs/pt-br/configuration.md): todas as configurações do servidor, o formato do `values.json` e a
  linguagem da fórmula de preço.
- [Referência de comandos](docs/pt-br/commands.md): todos os comandos `/freetrade`, com permissões e exemplos.
- [Mercados regionais](docs/regions.md): uma nota de projeto para uma versão futura. Não está implementado.

## Traduções

O Livre Comércio está disponível em inglês e em português do Brasil. Para adicionar um idioma, tanto o texto do jogo quanto a
documentação podem ser traduzidos. Veja [TRANSLATING.md](TRANSLATING.md).

## Compilação

| Branch   | Java |
|----------|------|
| `1.21.1` | 21   |

```bash
./gradlew build              # jar em build/libs
./gradlew runGameTestServer  # GameTests automáticos
```

O CI roda build e GameTests a cada push e pull request. Um push de tag `v*`, ou um push para o branch `release`, publica um
release com os jars compilados.

## Licença

[MIT](LICENSE)
