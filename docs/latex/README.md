# LaTeX-версии документов

Файлы:
- `tz.tex` — полноформатная LaTeX-версия ТЗ (ГОСТ-подобная верстка по образцу, текст синхронизируется вручную с `../tz.md`)
- `pmi.tex` — версия ПМИ из `../pmi.md`
- `pi.tex` — алиас ПМИ (по запросу «ПИ»)
- `tp.tex` — версия ТП из `../tp_text_program.md`

`pmi.tex`, `pi.tex`, `tp.tex` используют `\markdownInput{...}` и поэтому синхронизированы с исходными `.md`.

Пример сборки:
```bash
cd docs/latex
pdflatex tz.tex
pdflatex pmi.tex
pdflatex tp.tex
```

Для сборки нужен LaTeX-пакет `markdown`.
