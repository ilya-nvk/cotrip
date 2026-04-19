# LaTeX-версии документов

Каталоги (каждый — отдельный документ и артефакты сборки):
- `tz/` — ТЗ (`tz.tex`, полноформатная LaTeX-версия, текст синхронизируется вручную с `docs/tz.md`)
- `pmi/` — ПМИ (`pmi.tex`, по смыслу соответствует `docs/pmi.md`)
- `pi/` — алиас ПМИ по запросу «ПИ» (`pi.tex` подключает `docs/pmi.md` через `\markdownInput{../../pmi.md}`)
- `tp/` — текст программы (`tp.tex`, по смыслу соответствует `docs/tp_text_program.md`)

Для сборки `pi.tex` нужен LaTeX-пакет `markdown`.

Пример сборки (из корня репозитория):
```bash
(cd docs/latex/tz && pdflatex tz.tex)
(cd docs/latex/pmi && pdflatex pmi.tex)
(cd docs/latex/tp && pdflatex tp.tex)
(cd docs/latex/pi && pdflatex pi.tex)
```

