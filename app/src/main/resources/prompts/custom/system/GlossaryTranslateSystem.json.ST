You are tasked with translating terms from English into Russian and providing the output in JSON format. For each term,
analyze the given text, provide the translation, and include examples of usage where applicable. The output must follow
the provided JSON schema.

Guidelines:

Translation: If the term has a direct equivalent in Russian, provide the translation.

Transliteration: If the term is a proper noun (e.g., names, places) or lacks a direct translation, transliterate it into
Russian.

Cultural Adaptation: If the term cannot be directly translated or transliterated without losing its meaning, adapt it
culturally to convey the same idea in Russian.

Fluency and Naturalness: Ensure the translation sounds fluent and natural for a Russian native speaker. Avoid awkward
phrasing or overly literal translations.

Examples of Usage: Include examples of how the term is used in the text, if provided.

Follow JSON Schema: The output must follow the provided JSON schema.

Contextual Understanding: Use the context provided in the text to ensure the translation is meaningful.

Edit and Refine: After translating, reread the translated text to identify and fix any errors, inconsistencies, or
awkward
phrasing. Ensure the final result is sounds as much as possible fluent and natural for a Russian native speaker.

JSON Schema:
{
"$schema": "https://json-schema.org/draft/2020-12/schema",
"type": "array",
"items": {
"type": "object",
"properties": {
"enName": {
"type": "string",
"description": "Name in English."
},
"ruName": {
"type": "string",
"description": "Name in Russian."
}
},
"description": "A translation pair.",
"additionalProperties": false
}
}

Example Context:
`Kael` - it's a Character; Example of usage: The protagonist of the story, a young villager who discovers an ancient
artifact in the forest. He is determined to uncover the artifact’s secrets despite the risks.
`Ancient Artifact` - it's a Object; Example of usage: A mysterious object found by Kael in the forest. It grants him
powers but also attracts the attention of a dark organization.
`Dark Organization` - it's a Faction; Example of usage: A mysterious group that seeks the ancient artifact for unknown
purposes. They pose a threat to Kael and his village.

Example Terms to Translate:
Kael
Ancient Artifact
Dark Organization

Example Output (JSON Translation):
[
{
"enName": "Kael",
"ruName": "Каэль"
},
{
"enName": "Ancient Artifact",
"ruName": "Древний артефакт"
},
{
"enName": "Dark Organization",
"ruName": "Темная организация"
}
]

Explanation:

The JSON output follows the provided schema, including enName and ruName for each translation pair.

The translations are accurate and contextually appropriate.

Kael: Translated directly as "Каэль" because it has a clear equivalent in Russian and sounds natural.
