#!/usr/bin/env python3
"""
Glosbe Dictionary Builder for Spanish-Korean

This script uses Glosbe APIs to create a comprehensive Spanish-Korean dictionary
with translations and example phrases in Yomichan format.

Features:
- Fetches translations from Glosbe API
- Gets example phrases using similar phrases API
- Falls back to Google Translate if Glosbe fails
- Tracks problematic words for manual review
- Saves in Yomichan dictionary format
- Resume capability for interrupted runs

Usage: python glosbe_dictionary_builder.py spanish_words.txt
"""

import requests
import json
import csv
import time
import logging
import sys
from typing import List, Dict, Optional, Tuple
from pathlib import Path
import urllib.parse

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('dictionary_builder.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class GlosbeClient:
    """Client for Glosbe API interactions"""

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Spanish-Korean Dictionary Builder/1.0'
        })

    def get_translation(self, spanish_word: str) -> Optional[str]:
        """Get Korean translation for Spanish word using Glosbe API"""
        try:
            response = self.session.post(
                'https://translator-api.glosbe.com/translateByLangWithScore',
                params={'sourceLang': 'es', 'targetLang': 'ko'},
                data=spanish_word.encode('utf-8'),
                timeout=15
            )
            response.raise_for_status()

            data = response.json()
            translation = data.get('translation', '').strip()

            if translation and len(translation) > 0:
                logger.debug(f"Translation found: {spanish_word} -> {translation}")
                return translation

            logger.warning(f"No translation found for: {spanish_word}")
            return None

        except Exception as e:
            logger.error(f"Translation API failed for '{spanish_word}': {e}")
            return None

    def get_similar_phrases(self, spanish_word: str) -> List[str]:
        """Get similar Spanish phrases for examples"""
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
                timeout=15
            )
            response.raise_for_status()

            data = response.json()
            phrases = []
            for phrase_data in data.get('phrases', []):
                if not phrase_data.get('reverse', False):  # Only Spanish phrases
                    phrase = phrase_data.get('phrase', '').strip()
                    if phrase and phrase != spanish_word:  # Don't include the original word
                        phrases.append(phrase)

            logger.debug(f"Found {len(phrases)} similar phrases for: {spanish_word}")
            return phrases[:5]  # Limit to 5 examples

        except Exception as e:
            logger.error(f"Similar phrases API failed for '{spanish_word}': {e}")
            return []

    def get_google_translation(self, spanish_word: str) -> Optional[str]:
        """Fallback to Google Translate API"""
        try:
            # Note: This is an unofficial API and may break
            # Use responsibly and consider official Google Translate API
            params = {
                'anno': '3',
                'client': 'te',
                'format': 'html',
                'v': '1.0',
                'sl': 'es',
                'tl': 'ko',
                'sp': 'nmt',
                'tc': '1',
                'tk': '556785.976223',
                'mode': '1'
            }

            data = {'q': spanish_word}

            response = self.session.post(
                'https://translate.googleapis.com/translate_a/t',
                params=params,
                data=data,
                timeout=15
            )
            response.raise_for_status()

            # Response is a JSON array like ["놀다"]
            result = response.json()
            if isinstance(result, list) and len(result) > 0:
                translation = result[0].strip()
                if translation:
                    logger.info(f"Google Translate fallback: {spanish_word} -> {translation}")
                    return translation

            return None

        except Exception as e:
            logger.error(f"Google Translate fallback failed for '{spanish_word}': {e}")
            return None

class DictionaryBuilder:
    """Main dictionary building logic"""

    def __init__(self, client: GlosbeClient):
        self.client = client
        self.translations = {}  # word -> translation
        self.examples = {}      # word -> list of example phrases
        self.processed_words = set()
        self.problematic_words = set()

    def load_word_list(self, file_path: str) -> List[str]:
        """Load Spanish words from file"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                words = [line.strip() for line in f if line.strip()]
            words = list(set(words))  # Remove duplicates
            logger.info(f"Loaded {len(words)} unique words from {file_path}")
            return words
        except FileNotFoundError:
            logger.error(f"Word list file not found: {file_path}")
            sys.exit(1)

    def process_word(self, spanish_word: str) -> Tuple[Optional[str], List[str]]:
        """Process a single word: get translation and examples"""
        logger.info(f"Processing: {spanish_word}")

        # Get translation
        translation = self.client.get_translation(spanish_word)

        # If no translation from Glosbe, try Google Translate
        if not translation:
            logger.info(f"Trying Google Translate fallback for: {spanish_word}")
            translation = self.client.get_google_translation(spanish_word)

        # Get example phrases
        examples = self.client.get_similar_phrases(spanish_word)

        # Validate translation
        if not translation or len(translation.strip()) < 2:
            logger.warning(f"No valid translation found for: {spanish_word}")
            self.problematic_words.add(spanish_word)
            return None, examples

        return translation.strip(), examples

    def process_words(self, words: List[str], batch_size: int = 50) -> Dict[str, Tuple[str, List[str]]]:
        """Process words in batches with progress saving"""
        results = {}

        for i in range(0, len(words), batch_size):
            batch = words[i:i + batch_size]
            batch_num = i//batch_size + 1
            total_batches = (len(words) + batch_size - 1)//batch_size
            logger.info(f"Processing batch {batch_num}/{total_batches} ({len(batch)} words)")

            for word in batch:
                if word in self.processed_words:
                    continue

                translation, examples = self.process_word(word)
                if translation:
                    results[word] = (translation, examples)
                    self.translations[word] = translation
                    self.examples[word] = examples

                self.processed_words.add(word)

                # Rate limiting - be respectful to APIs
                time.sleep(2.0)

            # Save progress after each batch
            self.save_progress(results)

        return results

    def save_progress(self, results: Dict[str, Tuple[str, List[str]]]):
        """Save current progress to resume later"""
        progress_data = {
            'translations': self.translations,
            'examples': self.examples,
            'processed_words': list(self.processed_words),
            'problematic_words': list(self.problematic_words),
            'results_count': len(results)
        }

        with open('progress.json', 'w', encoding='utf-8') as f:
            json.dump(progress_data, f, ensure_ascii=False, indent=2)

        logger.info(f"Progress saved: {len(self.processed_words)} processed, {len(results)} successful")

    def load_progress(self):
        """Load previous progress"""
        try:
            with open('progress.json', 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.translations = data.get('translations', {})
                self.examples = data.get('examples', {})
                self.processed_words = set(data.get('processed_words', []))
                self.problematic_words = set(data.get('problematic_words', []))
                logger.info(f"Loaded progress: {len(self.processed_words)} processed, {len(self.problematic_words)} problematic")
        except FileNotFoundError:
            logger.info("No previous progress found, starting fresh")

    def save_problematic_words(self):
        """Save list of words that couldn't be translated"""
        with open('debug_problematic_words.txt', 'w', encoding='utf-8') as f:
            f.write("# Words that couldn't be translated automatically\n")
            f.write("# You can manually add Korean translations for these\n")
            f.write("# Format: spanish_word,korean_translation\n\n")
            for word in sorted(self.problematic_words):
                f.write(f"{word},\n")

        logger.info(f"Saved {len(self.problematic_words)} problematic words to debug_problematic_words.txt")

    def create_yomichan_entry(self, spanish: str, korean: str, examples: List[str]) -> List:
        """Create a Yomichan dictionary entry with translation and examples"""
        # Build structured content
        content = []

        # Main translation
        content.append({
            "tag": "div",
            "content": [
                {
                    "tag": "strong",
                    "content": korean
                }
            ]
        })

        # Add examples if available
        if examples:
            content.append({"tag": "br"})
            content.append({
                "tag": "div",
                "content": [
                    {
                        "tag": "em",
                        "content": "Examples:"
                    }
                ]
            })

            example_list = []
            for example in examples[:3]:  # Limit to 3 examples
                example_list.append({
                    "tag": "li",
                    "content": example
                })

            if example_list:
                content.append({
                    "tag": "ul",
                    "content": example_list
                })

        return [
            spanish,  # term
            "",       # reading
            "",       # definition_tags
            "unknown", # rule_identifier
            0,        # popularity_score
            [
                {
                    "type": "structured-content",
                    "content": content
                }
            ]
        ]

    def save_yomichan(self, results: Dict[str, Tuple[str, List[str]]], filename: str):
        """Save results in Yomichan term_bank format"""
        entries = []

        for spanish, (korean, examples) in results.items():
            entry = self.create_yomichan_entry(spanish, korean, examples)
            entries.append(entry)

        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(entries, f, ensure_ascii=False, indent=2)

        logger.info(f"Saved {len(entries)} entries to {filename}")

    def save_csv(self, results: Dict[str, Tuple[str, List[str]]], filename: str):
        """Save results as CSV for easy editing"""
        with open(filename, 'w', encoding='utf-8', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(['spanish', 'korean', 'examples'])

            for spanish, (korean, examples) in results.items():
                examples_str = '; '.join(examples)
                writer.writerow([spanish, korean, examples_str])

        logger.info(f"Saved {len(results)} entries to {filename}")

def main():
    if len(sys.argv) != 2:
        print("Usage: python glosbe_dictionary_builder.py <spanish_words.txt>")
        print("Example: python glosbe_dictionary_builder.py spanish_words.txt")
        sys.exit(1)

    word_list_file = sys.argv[1]

    # Initialize components
    client = GlosbeClient()
    builder = DictionaryBuilder(client)

    # Load previous progress
    builder.load_progress()

    # Load word list
    words = builder.load_word_list(word_list_file)

    # Filter out already processed words
    words_to_process = [w for w in words if w not in builder.processed_words]
    logger.info(f"Need to process {len(words_to_process)} out of {len(words)} total words")

    if not words_to_process:
        logger.info("All words already processed!")
    else:
        # Process words
        results = builder.process_words(words_to_process)

        # Save results
        builder.save_csv(results, 'spanish_korean_dictionary.csv')
        builder.save_yomichan(results, 'term_bank_spanish_korean_full.json')

    # Always save problematic words
    builder.save_problematic_words()

    # Final summary
    total_processed = len(builder.processed_words)
    total_successful = len(builder.translations)
    total_problematic = len(builder.problematic_words)

    logger.info("=" * 50)
    logger.info("BUILD COMPLETE")
    logger.info("Total words processed: %d", total_processed)
    logger.info("Successful translations: %d", total_successful)
    logger.info("Problematic words: %d", total_problematic)
    if total_processed > 0:
        success_rate = total_successful / total_processed * 100
        logger.info("Success rate: %.1f%%", success_rate)
    else:
        logger.info("Success rate: 0%")
    logger.info("=" * 50)

if __name__ == "__main__":
    main()</content>
<parameter name="filePath">d:\GitHub\TapDictionary\glosbe_dictionary_builder.py
