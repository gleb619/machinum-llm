You are tasked with creating a glossary of key terms, concepts, and entities mentioned in a web novel chapter’s text.
The text is provided in chunks, and you must analyze each chunk to identify and define the important terms. For each
glossary item, provide a clear and concise name, a detailed explanation of what the term means within the context of the
text, and optionally, examples of how the term is used or alternative phrasing. Use the rawNovelInfo tool to retrieve
additional information via RAG if needed to ensure accuracy and comprehensiveness.

Guidelines:

Identify Key Terms: Extract terms related to characters, locations, abilities, objects, events, themes, relationships,
conflicts, goals, symbols, emotions, time, factions, powers, mysteries, history, culture, transformations, etc.

Provide Context: Explain the term’s meaning within the context of the text.

Use Examples: Include examples or alternative phrasing where applicable.

Add References: Include a references field to list the names of related terms (e.g., "Kael’s Village" references
"Kael"). Note, that references, must have their own a stand-alone term in the glossary.

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
},
"references": {
"type": "array",
"items": {
"type": "string"
},
"description": "A list of names of related terms (e.g., 'Kael’s Village' references 'Kael')."
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
"description": "The protagonist of the story, a young villager who discovers an ancient artifact in the forest. He is
determined to uncover the artifact’s secrets despite the risks.",
"references": ["Ancient Artifact", "Lira", "Eldrin"]
},
{
"name": "Ancient Artifact",
"category": "Object",
"description": "A mysterious object found by Kael in the forest. It grants him powers but also attracts the attention of
a dark organization. Example: 'The artifact glowed with an eerie light as Kael touched it.'",
"references": ["Kael", "Dark Organization"]
},
{
"name": "Lira",
"category": "Character",
"description": "Kael’s best friend, who warns him about the dangers of using the artifact. She plays a supportive role
in the story.",
"references": ["Kael"]
},
{
"name": "Dark Organization",
"category": "Faction",
"description": "A mysterious group that seeks the ancient artifact for unknown purposes. They pose a threat to Kael and
his village.",
"references": ["Ancient Artifact", "Kael"]
},
{
"name": "Eldrin",
"category": "Character",
"description": "A rogue scholar who helps Kael and Lira understand the artifact. In exchange, he asks for their
assistance in his own quest.",
"references": ["Kael", "Lira"]
},
{
"name": "Memory Loss",
"category": "Conflict",
"description": "A consequence of using the artifact. Each use causes Kael’s memories to fade, creating a moral dilemma
for him.",
"references": ["Kael", "Ancient Artifact"]
}
]

Explanation:

The JSON output follows the provided schema, including name, category, description, and references for each glossary
item.

The references field lists related terms (e.g., "Kael" references "Ancient Artifact," "Lira," and "Eldrin"). Could be
empty.

Note that "Kael" term has references to "Ancient Artifact", and "Ancient Artifact" also present as stand-alone term.
This is because the terms can be scattered throughout the text of a web novel's text, and occur with varying
intensity. That's why we're added them to glossary separately.

The descriptions are detailed and include examples where applicable.

The rawNovelInfo tool can be used to retrieve additional context if needed.

For given text collected 6 items, for real one text it could be more(e.g glossary could contain more terms, it
based on given text size).