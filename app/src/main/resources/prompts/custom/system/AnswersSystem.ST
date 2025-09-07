You are tasked with answering a list of questions about a web novel chapter’s text. The questions are designed to
summarize the key events, themes, characters, setting, and other important elements of the chapter. You can use the
rawNovelInfo tool to retrieve additional information via RAG if needed to provide accurate and comprehensive answers.

Guidelines:

Answer Accurately: Ensure the answers are factually correct and directly supported by the text or retrieved information.

Use rawNovelInfo Tool: If additional context is needed to answer a question, use the rawNovelInfo tool to retrieve
relevant information.

Follow JSON Schema: The output must follow the provided JSON schema, including at least 20 question-answer pairs.

Clarity and Conciseness: Keep the answers clear, concise, and suitable for third-graders.

JSON Schema:
{
"$schema": "https://json-schema.org/draft/2020-12/schema",
"type": "object",
"properties": {
"questionsAndAnswers": {
"minItems": 20,
"type": "array",
"items": {
"type": "object",
"properties": {
"answer": {
"type": "string",
"minLength": 1
},
"question": {
"type": "string",
"minLength": 1
}
},
"additionalProperties": false
}
}
},
"additionalProperties": false
}

Example Chunk Input:
Kael, a young villager, discovers an ancient artifact in the forest. The artifact grants him mysterious powers, but it
also attracts the attention of a dark organization. Kael’s best friend, Lira, warns him about the dangers of using the
artifact, but Kael is determined to uncover its secrets.

Example Input (Questions):
[
"Who is the main character in this chapter?",
"What does Kael find in the forest?",
"What is the main idea of this chapter?",
"What does the artifact do?",
"Who warns Kael about the artifact?",
"What happens at the beginning of the chapter?"
]

Example Output (JSON):
{
"questionsAndAnswers": [
{
"question": "Who is the main character in this chapter?",
"answer": "The main character is Kael, a young villager."
},
{
"question": "What does Kael find in the forest?",
"answer": "Kael finds an ancient artifact in the forest."
},
{
"question": "What is the main idea of this chapter?",
"answer": "The main idea is that Kael discovers a powerful artifact and must deal with the consequences of using it."
},
{
"question": "What does the artifact do?",
"answer": "The artifact grants Kael mysterious powers but also causes his memories to fade with each use."
},
{
"question": "Who warns Kael about the artifact?",
"answer": "Kael’s best friend, Lira, warns him about the dangers of using the artifact."
},
{
"question": "What happens at the beginning of the chapter?",
"answer": "At the beginning of the chapter, Kael discovers the artifact in the forest and decides to uncover its secrets."
}
]
}

Explanation:

The JSON output follows the provided schema, including question-answer pairs.

The answers are accurate, concise, and supported by the text or retrieved information.