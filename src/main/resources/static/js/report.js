/**
 * Analyzes text and returns various metrics
 * @param {string} text - The text to analyze
 * @return {Object} Object containing various text metrics
 */
export function analyzeText(text) {
  if (!text || typeof text !== 'string') {
    return null;
  }

  // Basic metrics
  const textLength = text.length;
  const lines = text.split(/\r\n|\r|\n/);
  const lineCount = lines.length;

  // Tokens (simplified as words and punctuation)
  const tokenRegex = /\S+/g;
  const tokens = text.match(tokenRegex) || [];
  const tokensLength = tokens.length;

  // Words
  const wordRegex = /[a-zA-Z0-9а-яА-Я]+/g;
  const words = text.match(wordRegex) || [];
  const wordCount = words.length;

  // Word lengths
  const wordLengths = words.map(word => word.length);
  const totalWordLength = wordLengths.reduce((sum, length) => sum + length, 0);
  const averageWordLength = wordCount > 0 ? totalWordLength / wordCount : 0;

  // Longest and shortest words
  let longestWord = "";
  let shortestWord = words.length > 0 ? words[0] : "";

  for (const word of words) {
    if (word.length > longestWord.length) {
      longestWord = word;
    }
    if (word.length < shortestWord.length || shortestWord === "") {
      shortestWord = word;
    }
  }

  // Sentences
  const sentenceRegex = /[.!?]+(?=\s+|$)/g;
  const sentences = text.split(sentenceRegex).filter(sentence => sentence.trim() !== "");
  const sentenceCount = sentences.length;

  // Character counts
  const whitespaceCount = (text.match(/\s/g) || []).length;
  const digitCount = (text.match(/\d/g) || []).length;
  const alphabeticCharacterCount = (text.match(/[a-zA-Zа-яА-Я]/g) || []).length;
  const uppercaseCount = (text.match(/[A-ZА-Я]/g) || []).length;
  const lowercaseCount = (text.match(/[a-zа-я]/g) || []).length;
  const punctuationCount = (text.match(/[.,;:!?()[\]{}'""-]/g) || []).length;
  const vowelCount = (text.match(/[aeiouAEIOUаеёиоуыэюяАЕЁИОУЫЭЮЯ]/g) || []).length;
  const consonantCount = alphabeticCharacterCount - vowelCount;

  // Word frequency
  const wordFrequency = {};
  words.forEach(word => {
    const lowerCaseWord = word.toLowerCase();
    wordFrequency[lowerCaseWord] = (wordFrequency[lowerCaseWord] || 0) + 1;
  });

  let mostFrequentWord = "";
  let highestFrequency = 0;

  for (const word in wordFrequency) {
    if (wordFrequency[word] > highestFrequency) {
      highestFrequency = wordFrequency[word];
      mostFrequentWord = word;
    }
  }

  // Unique words
  const uniqueWords = new Set(words.map(word => word.toLowerCase()));
  const uniqueWordCount = uniqueWords.size;

  // Reading time (average reading speed is about 200-250 words per minute)
  const wordsPerMinute = 225;
  const estimatedReadingTime = wordCount > 0 ? wordCount / wordsPerMinute : 0;

  // Text density (ratio of non-whitespace characters to total characters)
  const textDensity = textLength > 0 ? (textLength - whitespaceCount) / textLength : 0;

  // Paragraphs
  const paragraphRegex = /\n\s*\n/g;
  const paragraphs = text.split(paragraphRegex).filter(p => p.trim() !== "");
  const paragraphCount = paragraphs.length > 0 ? paragraphs.length : 1; // At least one paragraph

  // Average line length
  const totalLineLength = lines.reduce((sum, line) => sum + line.length, 0);
  const averageLineLength = lineCount > 0 ? totalLineLength / lineCount : 0;

  // Capital letters (first letter of sentences)
  const capitalLettersCount = uppercaseCount;

  // Alphanumeric ratio
  const alphanumericCount = alphabeticCharacterCount + digitCount;
  const alphanumericRatio = textLength > 0 ? alphanumericCount / textLength : 0;

  // Special characters
  const specialCharacterCount = textLength - alphanumericCount - whitespaceCount;

  return {
    textLength,
    tokensLength,
    wordCount,
    lineCount,
    averageWordLength,
    longestWord,
    shortestWord,
    sentenceCount,
    mostFrequentWord,
    whitespaceCount,
    digitCount,
    alphabeticCharacterCount,
    uppercaseCount,
    lowercaseCount,
    punctuationCount,
    vowelCount,
    consonantCount,
    estimatedReadingTime,
    uniqueWordCount,
    textDensity,
    paragraphCount,
    averageLineLength,
    capitalLettersCount,
    alphanumericRatio,
    specialCharacterCount
  };
}