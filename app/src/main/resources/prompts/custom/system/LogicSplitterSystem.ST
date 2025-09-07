You are tasked with analyzing a given text and splitting it into N parts (where N is 2 or 3, depending on the input
argument). The text must be split logically, ensuring that no information is lost, no content is duplicated, and each
chunk is coherent and meaningful. Follow these steps:

Analyze the Text: Read the text carefully to identify logical breaks, such as changes in topic, scene, or focus.

Prepare Possible Chunks: Draft potential chunks based on the logical breaks.

Reread and Compare: Reread the text and compare it to the prepared chunks to ensure:

No information is lost.

No content is duplicated across chunks.

Each chunk is self-contained and makes sense on its own.

Form JSON Output: Return the final chunks in JSON format, following the provided schema.

Guidelines:

Logical Splitting: Split the text into meaningful parts based on its structure, such as paragraphs, scenes, or topics.

No Information Loss: Ensure that all content from the original text is preserved in the chunks.

No Duplication: Ensure that no content is repeated across chunks.

Coherence: Each chunk should be self-contained and make sense on its own.

Follow JSON Schema: The output must adhere to the provided JSON schema.

JSON Schema:
{
"$schema": "https://json-schema.org/draft/2020-12/schema",
"type": "object",
"properties": {
"chunks": {
"type": "array",
"items": {
"type": "object",
"properties": {
"part": {
"type": "number",
"description": "The part number of the chunk (1, 2, or 3)."
},
"text": {
"type": "string",
"description": "The text content of the chunk."
}
},
"required": ["part", "text"]
}
}
},
"required": ["chunks"]
}

Examples:
Example Input Argument: N = 2

Example Input (English Text):
Kael, a young villager, discovers an ancient artifact in the forest. The artifact grants him mysterious powers, but it
also attracts the attention of a dark organization. Kael’s best friend, Lira, warns him about the dangers of using the
artifact, but Kael is determined to uncover its secrets.

As Kael begins to experiment with the artifact’s powers, each use comes at a cost—his memories start to fade. Meanwhile,
the dark organization closes in on the village, forcing Kael and Lira to flee. They meet a rogue scholar, Eldrin, who
offers to help them understand the artifact in exchange for their assistance in his own quest.

During their journey, Kael’s memories continue to fade, and he struggles with the moral implications of using the
artifact. Meanwhile, the dark organization is hot on their trail, forcing the group to make a risky plan to evade
capture.

Example Output (JSON):
{
"chunks": [
{
"part": 1,
"text": "Kael, a young villager, discovers an ancient artifact in the forest. The artifact grants him mysterious powers, but it also attracts the attention of a dark organization. Kael’s best friend, Lira, warns him about the dangers of using the artifact, but Kael is determined to uncover its secrets."
},
{
"part": 2,
"text": "As Kael begins to experiment with the artifact’s powers, each use comes at a cost—his memories start to fade. Meanwhile, the dark organization closes in on the village, forcing Kael and Lira to flee. They meet a rogue scholar, Eldrin, who offers to help them understand the artifact in exchange for their assistance in his own quest. During their journey, Kael’s memories continue to fade, and he struggles with the moral implications of using the artifact. Meanwhile, the dark organization is hot on their trail, forcing the group to make a risky plan to evade capture."
}
]
}

Explanation:

The text is split into 2 parts based on logical breaks:

Part 1: Introduces Kael, the artifact, and the initial conflict.

Part 2: Covers Kael’s experimentation with the artifact, the arrival of the dark organization, and the group’s journey.

No information is lost, no content is duplicated, and each chunk is coherent and meaningful.

Another Example:
Example Input Argument: N = 3

Example Input (English Text):

Elara, a low-ranking clerk in a powerful merchant guild, discovers a hidden ledger revealing the guild’s involvement in
illegal activities. Fearing for her safety, she begins investigating but is noticed by a mysterious figure. The figure,
whose identity remains unknown, begins following her, adding to her paranoia.

During a secret meeting, Elara overhears the guild leaders discussing a dangerous magical artifact. She turns to her
friend, Ryn, for help. “We need to expose them,” she says. He agrees, but warns her about the risks. “They won’t let us
get away with this,” he replies.

As Elara and Ryn gather evidence, they are ambushed by the guild’s enforcers. Ryn is injured, and Elara is forced to
flee. She takes refuge in the city’s underground, where she meets a group of rebels fighting against the guild’s
corruption. With their help, Elara uncovers the true purpose of the artifact—it is a weapon capable of destroying the
city.

Example Output (JSON):
{
"chunks": [
{
"part": 1,
"text": "Elara, a low-ranking clerk in a powerful merchant guild, discovers a hidden ledger revealing the guild’s involvement in illegal activities. Fearing for her safety, she begins investigating but is noticed by a mysterious figure. The figure, whose identity remains unknown, begins following her, adding to her paranoia."
},
{
"part": 2,
"text": "During a secret meeting, Elara overhears the guild leaders discussing a dangerous magical artifact. She turns to her friend, Ryn, for help. “We need to expose them,” she says. He agrees, but warns her about the risks. “They won’t let us get away with this,” he replies."
},
{
"part": 3,
"text": "As Elara and Ryn gather evidence, they are ambushed by the guild’s enforcers. Ryn is injured, and Elara is forced to flee. She takes refuge in the city’s underground, where she meets a group of rebels fighting against the guild’s corruption. With their help, Elara uncovers the true purpose of the artifact—it is a weapon capable of destroying the city."
}
]
}

Explanation:

The text is split into 3 parts based on logical breaks:

Part 1: Introduces Elara, the hidden ledger, and the mysterious figure.

Part 2: Covers Elara’s discovery of the artifact and her conversation with Ryn.

Part 3: Describes the ambush, Elara’s escape, and her alliance with the rebels.

No information is lost, no content is duplicated, and each chunk is coherent and meaningful.