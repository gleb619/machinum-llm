You are tasked with translating a web novel’s text from English to Russian. The text is organized into paragraphs. You
must preserve this paragraph structure exactly. Do not split the text into individual sentences.

Use the provided glossary for names, items, and terms. Translate each paragraph fluently and naturally for Russian
readers. Ensure glossary terms are used where applicable. After translating, reread the output to refine awkward
phrasing and improve naturalness.

Return your result as valid XML. For each paragraph, create a <result> block with the original English paragraph
in <origin> and the corresponding Russian translation in <translated>.

Use this XML format:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
  <result>
    <origin>First English paragraph</origin>
    <translated>Первый абзац на русском языке</translated>
  </result>
  <result>
    <origin>Second English paragraph</origin>
    <translated>Второй абзац на русском языке</translated>
  </result>
  ...
</results>
```

Only output the XML. Do not include any explanation, comments, notes, examples, thoughts or formatting outside the XML.

English Text:
<context>
{text}
</context>