# Translating Free Trade

Thank you for helping translate Free Trade. There are two kinds of text to translate:

- **Game text**: item and block names, the GUI, the messages and the pages of the Trade Association Guide book. It
  lives in one JSON file per language.
- **Documentation**: the README and the files in `docs/`. These are Markdown files.

You can translate one or both. A translation can be submitted as a pull request.

## Contents

- [Translating the game text](#translating-the-game-text)
- [Translating the documentation](#translating-the-documentation)
- [Translating with an AI assistant](#translating-with-an-ai-assistant)
- [Checking a translation](#checking-a-translation)
- [Submitting a pull request](#submitting-a-pull-request)
- [Português (Brasil)](#português-brasil)

## Translating the game text

The English file is `src/main/resources/assets/freetrade/lang/en_us.json`. The Brazilian Portuguese file is
`pt_br.json` in the same folder.

1. Copy `en_us.json` to a new file named with the Minecraft locale code, in lowercase, with an underscore. Examples:
   `es_es.json` (Spanish), `fr_fr.json` (French), `de_de.json` (German).
2. Translate the **values only**. Keep every key on the left of the colon exactly as it is.
3. Keep the placeholders. `%s`, `%1$s` and `%2$s` are replaced by the game with a number, a name or a text. Keep each
   placeholder, and keep the numbered ones (`%1$s`) in whatever order your sentence needs.
4. Keep the `\n` escapes in the guide page texts (`freetrade.guide.page.*.body`). They mark where a line ends.
5. Keep product and mod names as they are: Free Trade, JEI, EMI, Lightman's Currency, NeoForge, Minecraft.
6. Keep command syntax, such as `/freetrade recalc` and `/freetrade price`, and symbols such as `U.M.`, `×`, `÷`, `↑` and
   `↓`.
7. The Trade Association Guide book pages are the keys `freetrade.guide.page.1.title` to `freetrade.guide.page.7.body`.
   Everything under `freetrade.guide.*` is shown in the guide screen.

To test, start the game with the new file in `resources`, or put it in a resource pack, select the language, and press
F3 + T to reload the resources. Look at the guide book, a Trade Representative window, and a Sell Point. Check that
the text fits on the buttons.

## Translating the documentation

The documentation is written in English in `docs/en/` and in the root `README.md`. A translation keeps the same file
names, the same section order and the same tables, and it is placed in a folder named after its language code:

1. Copy `docs/en/` to `docs/<code>/`, using a lowercase language code. Examples: `docs/es/` (Spanish), `docs/fr/`
   (French). Brazilian Portuguese uses `docs/pt-br/`.
2. Copy `README.md` to `README.<code>.md` in the root. Brazilian Portuguese uses `README.pt-BR.md`.
3. Translate the text, the table cells and the headings. Keep these unchanged:
   - file names and link targets (for example `configuration.md`);
   - code blocks, including their contents (commands, JSON, TOML, YAML and the formula);
   - command names, config keys (such as `price_ceiling`), item ids (such as `minecraft:emerald`), tags (such as
     `#minecraft:logs`) and the trigger placeholders `{id}`, `{account}`, `{balance}`, `{cost}` and `{count}`;
   - the operators `>=`, `>`, `<=`, `<` and `=`, and the names `treasury` and `tax` in commands.
4. Headings change the anchors that other pages link to. GitHub makes an anchor from the heading text (lowercase,
   spaces to hyphens, most punctuation removed). When you translate a heading, update every `#anchor` link that points
   to it, in the same file and in the other files that link to it.
5. Keep the same section order in every file, so that a reader can switch between languages and find the same section.
6. Add the language to the language switch line at the top of **every** README. The English line looks like this:

   ```markdown
   **English** | [Português (Brasil)](README.pt-BR.md) | [Español](README.es.md)
   ```

   Each README links to the others, with its own language in bold.

## Translating with an AI assistant

An AI assistant can make a first draft. Always have a fluent speaker review it. Use these prompts.

**For a Markdown file (README or docs page):**

```text
You are translating the documentation of "Free Trade", a Minecraft NeoForge economy mod, from English to <LANGUAGE>
(<LOCALE>). Translate the Markdown file below.

Rules:
1. Output only the translated file. Do not add notes or comments.
2. Keep the Markdown structure: the same headings in the same order, the same tables with the same number of
   columns, the same lists, and the same links. Translate the link text, but keep every link target unchanged,
   including #anchors. Anchors are fixed by a reviewer afterwards.
3. Do not translate anything inside code blocks or inline code. This includes commands (/freetrade, /ft), JSON and
   TOML keys, config keys, item ids (minecraft:...), tags (#...), trigger placeholders ({id}, {account}, {balance},
   {cost}, {count}), operators (>=, >, <=, <, =), the accounts treasury and tax in commands, and the unit U.M.
4. Keep product and mod names: Free Trade, JEI, EMI, Lightman's Currency, NeoForge, Minecraft.
5. Use the usual Minecraft terms in <LANGUAGE>, as the official game translation uses them. Keep the same term for the
   same concept everywhere: for example Trade Association, Trade Representative, Sell Point, Auto Buyer, backoff, money
   item, treasury, tax account.
6. Do not add facts, and do not remove any. If a sentence is unclear, translate it literally and keep its meaning.
7. Keep the tone plain and direct.

File:
<PASTE THE MARKDOWN FILE HERE>
```

**For the game text (lang JSON):**

```text
You are translating the Minecraft language file of "Free Trade", a NeoForge mod, from English to <LANGUAGE>
(<LOCALE>). The input is a JSON object of key and value pairs.

Rules:
1. Output only valid JSON with exactly the same keys, in the same order. Translate only the values.
2. Keep every placeholder: %s, %1$s, %2$s and so on. Keep each one, and keep the numbered ones in your sentence order.
3. Keep every \n escape. Keep symbols such as U.M., ×, ÷, ↑, ↓ and ·.
4. Keep command syntax such as /freetrade recalc, and product names: Free Trade, JEI, EMI, Lightman's Currency,
   NeoForge, Minecraft.
5. Use the usual Minecraft terms in <LANGUAGE>, consistently.
6. Keep each translation short enough for a button or a line in the game's window.

Input:
<PASTE THE JSON HERE>
```

## Checking a translation

Run these checks from the repository root before you submit. Replace `es_es` with your locale.

1. **The JSON is valid.**

   ```bash
   python3 -m json.tool src/main/resources/assets/freetrade/lang/es_es.json > /dev/null && echo valid
   ```

2. **The same keys as the English file.** This prints the keys that are missing (translation incomplete) and the extra
   keys (usually a typo):

   ```bash
   python3 -c "import json; a=json.load(open('src/main/resources/assets/freetrade/lang/en_us.json')); b=json.load(open('src/main/resources/assets/freetrade/lang/es_es.json')); print('missing:', sorted(set(a)-set(b))); print('extra:', sorted(set(b)-set(a)))"
   ```

3. **The same placeholders.** This prints each key whose `%s` placeholders differ from the English text:

   ```bash
   python3 -c "import json,re; p=lambda s: sorted(re.findall(r'%([0-9]+[$])?s', s)); a=json.load(open('src/main/resources/assets/freetrade/lang/en_us.json')); b=json.load(open('src/main/resources/assets/freetrade/lang/es_es.json')); print([k for k in a if k in b and p(a[k])!=p(b[k])])"
   ```

4. **The same number of line breaks** in the guide pages (`\n` escapes), so each page keeps its lines.
5. **Markdown.** Each section heading is present in the same order. Tables have the same columns. Code blocks are
   identical to the English ones. Every link points to a file that exists, and every `#anchor` matches a heading.
6. **In the game.** Load the language, reload resources with F3 + T, and check the guide book and the trade window.
7. **A fluent speaker has read it.** Check the consistency of the terms.

## Submitting a pull request

1. Fork the repository on GitHub, and create a branch named after the language, for example `translation/es-es`.
2. Add only your translation files: the lang file, the `docs/<code>/` folder and the `README.<code>.md` file. Add the
   new language to the switch line of every README, and nothing else.
3. Run `./gradlew build` to make sure the resources still build.
4. Open a pull request against the branch the translation applies to (currently `1.21.1`). In the description, say
   what you translated (game text, documentation or both), which locale it is, and who reviewed it.
5. CI builds the mod and runs the GameTests. Fix anything it reports and push again.

Thank you for helping other players read Free Trade in their own language.

---

## Português (Brasil)

O português do Brasil já está traduzido em `pt_br.json` e em `docs/pt-br/`, com `README.pt-BR.md` na raiz. Para
revisar ou corrigir essa tradução, edite esses arquivos e siga as mesmas verificações deste guia: o JSON precisa ser
válido, as chaves e os placeholders `%s` devem coincidir com os do `en_us.json`, e os comandos, as chaves de
configuração e os blocos de código não são traduzidos. Quando um título mudar, atualize os links `#âncora` que
apontam para ele. Para enviar uma correção, abra um pull request contra o branch `1.21.1` com a descrição do que
mudou.
