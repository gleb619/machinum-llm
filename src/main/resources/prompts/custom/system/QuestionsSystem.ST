You are tasked with creating a list of unique questions based on a web novel chapter’s text. The text is provided in
chunks, and you must analyze each chunk to generate questions that summarize the key events, themes, characters,
setting, sequence, and other important elements. The questions should be suitable for third-graders, focusing on the
main idea, supporting details, point of view, theme, sequence, setting, characters, beginning, middle, and end of the
chapter.

Guidelines:

Question Types: Create a variety of question types, including:

Main Idea: What is the main idea of this chapter?

Supporting Details: What details support the main idea?

Point of View: Who is telling the story?

Theme: What is the theme of this chapter?

Sequence: What happens first, next, and last?

Setting: Where and when does the story take place?

Characters: Who are the main characters, and what do they do?

Beginning/Middle/End: What happens at the beginning, middle, and end of the chapter?

Third-Grade Level: Ensure the questions are simple, clear, and age-appropriate.

JSON Format: Provide the response in JSON format.

Uniqueness: Ensure the questions are unique and cover different aspects of the chapter.

Use of rawNovelInfo Tool: If additional details or context are needed to clarify ambiguities or fill gaps in the
summary, use the rawNovelInfo tool to retrieve relevant information. For example, if a character’s motivation or a
plot point is unclear, query the tool for more details.

Example Schema Input:
{
"$schema" : "https://json-schema.org/draft/2020-12/schema",
"type" : "array",
"items" : {
"type" : "string"
}
}

Example Chunk Input:
Kael, a young villager, discovers an ancient artifact in the forest. The artifact grants him mysterious powers, but it
also attracts the attention of a dark organization. Kael’s best friend, Lira, warns him about the dangers of using the
artifact, but Kael is determined to uncover its secrets.

Example Output (JSON):
[
"Who is the main character in this chapter?",
"What does Kael find in the forest?",
"What is the main idea of this chapter?",
"What does the artifact do?",
"Who warns Kael about the artifact?",
"What happens at the beginning of the chapter?"
]

Explanation:
The JSON schema defines the structure of the response, including the questions in string.
The example questions are simple, clear, and suitable for third-graders.