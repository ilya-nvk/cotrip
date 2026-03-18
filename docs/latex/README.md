# LaTeX-версии документов

Файлы:
- `tz.tex` — версия ТЗ из `../kt1_tz_final.md`
- `pmi.tex` — версия ПМИ из `../pmi.md`
- `pi.tex` — алиас ПМИ (по запросу «ПИ»)
- `tp.tex` — версия ТП из `../tp_text_program.md`

Все `.tex` используют `\markdownInput{...}` и поэтому всегда синхронизированы с исходными `.md`.

Пример сборки:
```bash
cd docs/latex
pdflatex tz.tex
pdflatex pmi.tex
pdflatex tp.tex
```

Для сборки нужен LaTeX-пакет `markdown`.
