You are tasked with creating a glossary of key terms, concepts, and entities mentioned in a web novel chapter’s text.
The text is presented in chunks, and your task is to examine each segment to recognize and explain the key terms. For
each
glossary item, provide a clear and concise name, a detailed explanation of what the term means within the context of the
text, and optionally, examples of how the term is used or alternative phrasing. Use the rawNovelInfo tool to retrieve
additional information via RAG if needed to ensure accuracy and comprehensiveness.

Guidelines:

Identify Key Terms: Extract terms related to characters, locations, abilities, objects, events, themes, relationships,
conflicts, goals, symbols, emotions, time, factions, powers, mysteries, history, culture, transformations, etc.

Provide Context: Explain the term’s meaning within the context of the text.

Use Examples: Include examples or alternative phrasing where applicable.

Follow JSON Schema: The output must follow the provided JSON schema.

Use rawNovelInfo Tool: If additional context is needed, use the rawNovelInfo tool to retrieve relevant information.

JSON Schema:
{
"$schema": "https://json-schema.org/draft/2020-12/schema",
"type": "array",
"items": {
"type": "object",
"properties": {
"category": {
"type": "string",
"description": "A short explanation of what the term means within the context of the text (e.g.,
Character/Location/Ability/Object/Event/Theme/Relationship/Conflict/Goal/Symbol/Emotion/Time/Faction/Power/Mystery/History/Culture/Transformation)."
},
"description": {
"type": "string",
"description": "A statement or account that describes the term. Optionally, provide examples of how the term is used or
alternative phrasing."
},
"name": {
"type": "string",
"description": "A clear, concise identifier of the term."
}
},
"description": "A glossary term.",
"additionalProperties": false
}
}

Example Input (Multiple Chunks):
Chunk 1:
Kael, a young villager, discovers an ancient artifact in the forest. The artifact grants him mysterious powers, but it
also attracts the attention of a dark organization. Kael’s best friend, Lira, warns him about the dangers of using the
artifact, but Kael is determined to uncover its secrets.

Chunk 2:
Kael begins to experiment with the artifact’s powers, but each use comes at a cost—his memories start to fade.
Meanwhile, the dark organization closes in on the village, forcing Kael and Lira to flee. They meet a rogue scholar,
Eldrin, who offers to help them understand the artifact in exchange for their assistance in his own quest.

Chunk 3:
As they journey with Eldrin, Kael’s memories continue to fade, and he struggles with the moral implications of using the
artifact. Meanwhile, the dark organization closes in, forcing the group to make a risky plan to evade capture.

Example Output (JSON Glossary):
[
{
"name": "Kael",
"category": "Character",
"description": "The protagonist of the story, a young villager who discovers an ancient artifact in the forest. He is determined to uncover the artifact’s secrets despite the risks."
},
{
"name": "Ancient Artifact",
"category": "Object",
"description": "A mysterious object found by Kael in the forest. It grants him powers but also attracts the attention of a dark organization. Example: 'The artifact glowed with an eerie light as Kael touched it.'"
},
{
"name": "Lira",
"category": "Character",
"description": "Kael’s best friend, who warns him about the dangers of using the artifact. She plays a supportive role in the story."
},
{
"name": "Dark Organization",
"category": "Faction",
"description": "A mysterious group that seeks the ancient artifact for unknown purposes. They pose a threat to Kael and his village."
},
{
"name": "Eldrin",
"category": "Character",
"description": "A rogue scholar who helps Kael and Lira understand the artifact. In exchange, he asks for their assistance in his own quest."
},
{
"name": "Memory Loss",
"category": "Conflict",
"description": "A consequence of using the artifact. Each use causes Kael’s memories to fade, creating a moral dilemma for him."
},
{
"name": "Moral Dilemma",
"category": "Theme",
"description": "A recurring theme in the story, as Kael struggles with the consequences of using the artifact and the ethical implications of his actions."
}
]

Explanation:

The JSON output follows the provided schema, including name, category, and description for each glossary item.

The descriptions are detailed and include examples where applicable.

The rawNovelInfo tool can be used to retrieve additional context if needed.