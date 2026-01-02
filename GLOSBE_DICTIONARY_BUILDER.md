# Glosbe API Dictionary Builder Documentation

## Overview

This document explains how to create a script that uses Glosbe's APIs to build a comprehensive Spanish-Korean dictionary from a list of Spanish words. The script will fetch translations and save them in a format compatible with Yomichan dictionary format.

## Glosbe APIs Used

### 1. Translation API
**URL**: `https://translator-api.glosbe.com/translateByLangWithScore?sourceLang=es&targetLang=ko`

**Method**: POST

**Payload**: Raw text (Spanish word)

**Response**:
```json
{
    "translation": "놀고",
    "scores": [-1.1453444957733154]
}
```

**Notes**:
- Returns the best Korean translation
- Scores indicate translation confidence (lower is better)
- May return empty translation for unknown words

### 2. Similar Phrases API
**URL**: `https://iapi.glosbe.com/iapi3/similar/similarPhrasesMany`

**Parameters**:
- `p`: Spanish word/phrase to search for
- `l1`: Source language (es)
- `l2`: Target language (ko)
- `removeDuplicates`: true
- `searchCriteria`: Complex priority string
- `env`: en

**Example**: `https://iapi.glosbe.com/iapi3/similar/similarPhrasesMany?p=jugar&l1=es&l2=ko&removeDuplicates=true&searchCriteria=WORDLIST-ALPHABETICALLY-3-s%3BPREFIX-PRIORITY-3-s%3BTRANSLITERATED-PRIORITY-3-s%3BFUZZY-PRIORITY-3-s%3BWORDLIST-ALPHABETICALLY-3-r%3BPREFIX-PRIORITY-3-r%3BTRANSLITERATED-PRIORITY-3-r%3BFUZZY-PRIORITY-3-r&env=en`

**Response**:
```json
{
    "phrases": [
        {
            "reverse": false,
            "phrase": "jugar",
            "transliterated": null,
            "emphStart": 0,
            "emphLength": 5,
            "emphTStart": 0,
            "emphTLength": 0,
            "nonLatin": false
        }
    ],
    "success": true
}
```

**Notes**:
- Returns similar Spanish phrases
- Useful for expanding vocabulary from seed words
- `reverse: false` means Spanish to Korean

## Script Architecture

### Core Components

1. **Word List Loader**: Load 10,000 Spanish words from file
2. **API Client**: Handle HTTP requests to Glosbe APIs
3. **Translation Fetcher**: Get Korean translations for each word
4. **Data Processor**: Clean and validate translations
5. **Output Generator**: Save in CSV or Yomichan JSON format
6. **Progress Tracker**: Save progress to resume interrupted runs
7. **Error Handler**: Retry failed requests, log errors

### Rate Limiting & Best Practices

- **Rate Limit**: Glosbe APIs have rate limits. Implement delays between requests (1-2 seconds)
- **Batch Processing**: Process in batches of 100-500 words
- **Resume Capability**: Save progress after each batch
- **Error Recovery**: Retry failed requests up to 3 times with exponential backoff
- **Caching**: Cache successful translations to avoid re-fetching

### Data Flow

```
Spanish Word List → API Requests → Translation Data → Validation → Output Format
```

## Implementation Guide

### 1. Setup Python Environment

```python
import requests
import json
import csv
import time
import logging
from typing import List, Dict, Optional
from pathlib import Path

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
```

### 2. API Client Class

```python
class GlosbeClient:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Spanish-Korean Dictionary Builder/1.0'
        })

    def get_translation(self, spanish_word: str) -> Optional[str]:
        """Get Korean translation for Spanish word"""
        try:
            response = self.session.post(
                'https://translator-api.glosbe.com/translateByLangWithScore',
                params={'sourceLang': 'es', 'targetLang': 'ko'},
                data=spanish_word.encode('utf-8'),
                timeout=10
            )
            response.raise_for_status()

            data = response.json()
            translation = data.get('translation', '').strip()

            if translation and len(translation) > 0:
                return translation
            return None

        except Exception as e:
            logger.error(f"Translation failed for '{spanish_word}': {e}")
            return None

    def get_similar_phrases(self, spanish_word: str) -> List[str]:
        """Get similar Spanish phrases"""
        try:
            params = {
                'p': spanish_word,
                'l1': 'es',
                'l2': 'ko',
                'removeDuplicates': 'true',
                'searchCriteria': 'WORDLIST-ALPHABETICALLY-3-s;PREFIX-PRIORITY-3-s;TRANSLITERATED-PRIORITY-3-s;FUZZY-PRIORITY-3-s;WORDLIST-ALPHABETICALLY-3-r;PREFIX-PRIORITY-3-r;TRANSLITERATED-PRIORITY-3-r;FUZZY-PRIORITY-3-r',
                'env': 'en'
            }

            response = self.session.get(
                'https://iapi.glosbe.com/iapi3/similar/similarPhrasesMany',
                params=params,
                timeout=10
            )
            response.raise_for_status()

            data = response.json()
            phrases = []
            for phrase_data in data.get('phrases', []):
                if not phrase_data.get('reverse', False):  # Only Spanish phrases
                    phrase = phrase_data.get('phrase', '').strip()
                    if phrase:
                        phrases.append(phrase)

            return phrases

        except Exception as e:
            logger.error(f"Similar phrases failed for '{spanish_word}': {e}")
            return []
```

### 3. Dictionary Builder Class

```python
class DictionaryBuilder:
    def __init__(self, client: GlosbeClient):
        self.client = client
        self.translations = {}  # Cache translations
        self.processed_words = set()

    def load_word_list(self, file_path: str) -> List[str]:
        """Load Spanish words from file"""
        with open(file_path, 'r', encoding='utf-8') as f:
            words = [line.strip() for line in f if line.strip()]
        return list(set(words))  # Remove duplicates

    def process_words(self, words: List[str], batch_size: int = 100) -> Dict[str, str]:
        """Process words in batches with progress saving"""
        results = {}

        for i in range(0, len(words), batch_size):
            batch = words[i:i + batch_size]
            logger.info(f"Processing batch {i//batch_size + 1}/{(len(words) + batch_size - 1)//batch_size}")

            for word in batch:
                if word in self.processed_words:
                    continue

                translation = self.client.get_translation(word)
                if translation:
                    results[word] = translation
                    self.translations[word] = translation

                self.processed_words.add(word)

                # Rate limiting
                time.sleep(1.5)

            # Save progress
            self.save_progress(results)

        return results

    def save_progress(self, results: Dict[str, str]):
        """Save current progress to file"""
        with open('progress.json', 'w', encoding='utf-8') as f:
            json.dump({
                'translations': self.translations,
                'processed_words': list(self.processed_words)
            }, f, ensure_ascii=False, indent=2)

    def load_progress(self):
        """Load previous progress"""
        try:
            with open('progress.json', 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.translations = data.get('translations', {})
                self.processed_words = set(data.get('processed_words', []))
        except FileNotFoundError:
            pass

    def save_csv(self, results: Dict[str, str], filename: str):
        """Save results as CSV"""
        with open(filename, 'w', encoding='utf-8', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(['spanish', 'korean'])
            for spanish, korean in results.items():
                writer.writerow([spanish, korean])

    def save_yomichan(self, results: Dict[str, str], filename: str):
        """Save results in Yomichan term_bank format"""
        entries = []
        for spanish, korean in results.items():
            entry = [
                spanish,  # term
                "",       # reading
                "",       # definition_tags
                "unknown", # rule_identifier
                0,        # popularity_score
                [
                    {
                        "type": "structured-content",
                        "content": [
                            {
                                "tag": "div",
                                "content": korean
                            }
                        ]
                    }
                ]
            ]
            entries.append(entry)

        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(entries, f, ensure_ascii=False, indent=2)
```

### 4. Main Script

```python
def main():
    client = GlosbeClient()
    builder = DictionaryBuilder(client)

    # Load previous progress
    builder.load_progress()

    # Load word list
    words = builder.load_word_list('spanish_words.txt')

    # Filter out already processed words
    words_to_process = [w for w in words if w not in builder.processed_words]

    logger.info(f"Processing {len(words_to_process)} words")

    # Process words
    results = builder.process_words(words_to_process)

    # Save results
    builder.save_csv(results, 'spanish_korean_translations.csv')
    builder.save_yomichan(results, 'term_bank_spanish_korean.json')

    logger.info(f"Completed! {len(results)} translations saved")

if __name__ == "__main__":
    main()
```

## Usage Instructions

### 1. Prepare Word List
Create `spanish_words.txt` with one word per line:
```
hola
adiós
gracias
casa
comer
beber
...
```

### 2. Run the Script
```bash
python glosbe_dictionary_builder.py
```

### 3. Monitor Progress
- Check `progress.json` for current status
- Script saves progress after each batch
- Can be interrupted and resumed

### 4. Output Files
- `spanish_korean_translations.csv`: Simple CSV format
- `term_bank_spanish_korean.json`: Yomichan dictionary format
- `progress.json`: Resume data

## Advanced Features

### Expanding Vocabulary with Similar Phrases

```python
def expand_vocabulary(self, seed_words: List[str]) -> List[str]:
    """Use similar phrases API to expand word list"""
    expanded = set(seed_words)

    for word in seed_words:
        similar = self.client.get_similar_phrases(word)
        expanded.update(similar)

        # Limit expansion to avoid too many words
        if len(expanded) > 50000:
            break

    return list(expanded)
```

### Quality Filtering

```python
def filter_translations(self, results: Dict[str, str]) -> Dict[str, str]:
    """Filter out low-quality translations"""
    filtered = {}

    for spanish, korean in results.items():
        # Skip if translation is too short or contains numbers/symbols
        if len(korean) < 2 or any(char.isdigit() for char in korean):
            continue

        # Skip if translation looks like it's not Korean
        if not any(ord(char) > 0x1100 for char in korean):  # Basic Korean check
            continue

        filtered[spanish] = korean

    return filtered
```

## Troubleshooting

### Common Issues

1. **Rate Limiting**: Increase delay between requests
2. **Empty Translations**: Some words may not have translations
3. **Encoding Issues**: Ensure UTF-8 encoding for all files
4. **API Changes**: Glosbe may change their API structure

### Error Handling

- Implement exponential backoff for retries
- Log all errors with context
- Save failed words for manual review

## Legal & Ethical Considerations

- Check Glosbe's terms of service for API usage
- Respect rate limits to avoid being blocked
- Consider contributing back to Glosbe or open-source projects
- Ensure the resulting dictionary complies with copyright laws

## Performance Expectations

- **10,000 words**: ~4-6 hours with 1.5s delay
- **Success rate**: ~70-80% (depending on word commonality)
- **Storage**: ~2-3MB for 10,000 entries

This script will give you a much more comprehensive Spanish-Korean dictionary than the 5k entries from Kaikki!</content>
<parameter name="filePath">d:\GitHub\TapDictionary\GLOSBE_DICTIONARY_BUILDER.md