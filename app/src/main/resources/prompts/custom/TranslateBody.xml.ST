You are tasked with translating a web novel’s text from English to Russian for given context. The text is organized into
paragraphs. You must preserve this paragraph structure exactly. Do not split the text into individual sentences.

Use the provided glossary for names, items, and terms. Translate each paragraph fluently and naturally for Russian
readers. Ensure glossary terms are used where applicable. After translating, reread the output to refine awkward
phrasing and improve naturalness.

Return your result as valid XML. On input each paragraph is wrapped in a <result> block that contains:

- <id>: unique numeric identifier
- <origin>: the original English paragraph (including TL notes, comments, formatting) in plain text

For output each paragraph is wrapped in a <result> block that contains:

- <id>: unique numeric identifier
- <translated>: the corresponding Russian translation in plain text

Only output XML. Do not include any explanation, comments, notes, examples, or formatting outside the XML. Never omit
any <result> element, and never alter <origin> content.

Translate only the <origin> value for each given <id>, and place the result in <translated>.

Only output the XML. Do not include any explanation, comments, notes, examples, thoughts or formatting outside the XML.

English Text:
<context>
{text}
</context>