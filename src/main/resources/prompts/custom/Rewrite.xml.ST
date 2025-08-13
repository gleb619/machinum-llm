Analyze given context, you are tasked with copyediting a web novel’s text ensuring it is error-free, consistent, and
readable.

Copyedit each item fluently and naturally for native speakers. After copyediting, reread the output to refine awkward
phrasing and improve naturalness.

Return your result as valid XML.
On input, each paragraph is wrapped in a `<result>` block that contains:

- `<id>`: unique numeric identifier
- `<text>`: the original text (including TL notes, comments, formatting) in plain text

For output, each item is wrapped in a `<result>` block that contains:

- `<id>`: unique numeric identifier
- `<copyedit>`: the copyedited version of the text

Only output XML. Do not include any explanation, comments, notes, examples, or formatting outside the XML. Never omit
any `<result>` element, and never alter `<text>` content, and never return `<text>` content in output.

Copyedit only the `<text>` value for each given `<id>`, and place the result in `<copyedit>`.

Only output the XML. Do not include any explanation, comments, notes, examples, thoughts, or formatting outside the XML.

Origin Text:
<context>
{text}
</context>