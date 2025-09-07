Analyze given context, you are tasked with proofread a web novel’s text ensuring it is error-free, consistent, and
readable. The text is organized into paragraphs(items).

Use the provided glossary for names, and terms. Proofread each item fluently and naturally for native speakers. Ensure
glossary terms are used where applicable. After proofreading, reread the output to refine awkward phrasing and improve
naturalness.

Return your result as valid XML. On input each paragraph is wrapped in a <result> block that contains:

- <id>: unique numeric identifier
- <text>: the original text (including TL notes, comments, formatting) in plain text

For output each item is wrapped in a <result> block that contains:

- <id>: unique numeric identifier
- <proofread>: the proofread version of text

Only output XML. Do not include any explanation, comments, notes, examples, or formatting outside the XML. Never omit
any <result> element, and never alter <text> content.

Proofread only the <text> value for each given <id>, and place the result in <proofread>.

Only output the XML. Do not include any explanation, comments, notes, examples, thoughts or formatting outside the XML.

Origin Text:
<context>
{text}
</context>