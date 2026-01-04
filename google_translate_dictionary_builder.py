#!/usr/bin/env python3
"""
Google Translate Dictionary Builder for Spanish-Korean

Uses Google Translate's unofficial API to create a Spanish-Korean dictionary
with translations in Yomitan format.

Features:
- Fast and reliable Google Translate API
- Tracks problematic words for manual review
- Saves in Yomitan dictionary format (JSON)
- Resume capability for interrupted runs

Usage: python google_translate_dictionary_builder.py input_words.txt
"""

import requests
import json
import time
import logging
import sys
from typing import List, Dict, Optional, Tuple
from pathlib import Path
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

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

class GoogleTranslateClient:
    """Client for Google Translate API interactions"""

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
        self.request_count = 0
        self.last_request_time = 0
        self.min_request_interval = 0.1  # 100ms between requests (10 requests/second)
        self.lock = threading.Lock()  # Thread-safe rate limiting

    def _rate_limit(self):
        """Enforce minimum interval between API requests (thread-safe)"""
        with self.lock:
            current_time = time.time()
            time_since_last = current_time - self.last_request_time
            
            if time_since_last < self.min_request_interval:
                sleep_time = self.min_request_interval - time_since_last
                time.sleep(sleep_time)
            
            self.last_request_time = time.time()
            self.request_count += 1
            
            # Log every 100 requests
            if self.request_count % 100 == 0:
                logger.info(f"Processed {self.request_count} API requests")

    def get_translation(self, spanish_word: str, max_retries: int = 3) -> Optional[str]:
        """Get Korean translation for Spanish word using Google Translate API"""
        for attempt in range(max_retries):
            try:
                self._rate_limit()
                
                # Try the second endpoint first (more reliable)
                url = "https://translate.googleapis.com/translate_a/single"
                params = {
                    'client': 'gtx',
                    'dt': 't',
                    'sl': 'es',  # Spanish
                    'tl': 'ko',  # Korean
                    'q': spanish_word
                }
                
                response = self.session.get(url, params=params, timeout=10)
                
                # Handle rate limiting (unlikely with Google)
                if response.status_code == 429:
                    wait_time = (2 ** attempt) * 2  # Exponential backoff: 2s, 4s, 8s
                    logger.warning(f"Rate limit hit for '{spanish_word}', waiting {wait_time}s...")
                    time.sleep(wait_time)
                    continue
                
                response.raise_for_status()
                
                # Parse the nested array response
                data = response.json()
                
                # The translation is in the first element: data[0][0][0]
                if data and len(data) > 0 and len(data[0]) > 0:
                    translation = data[0][0][0]
                    # logger.debug(f"Translated '{spanish_word}' -> '{translation}'")
                    return translation
                else:
                    logger.warning(f"Unexpected response format for '{spanish_word}'")
                    return None
                    
            except requests.exceptions.RequestException as e:
                logger.error(f"Request failed for '{spanish_word}' (attempt {attempt+1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                return None
            except (json.JSONDecodeError, IndexError, KeyError, TypeError) as e:
                logger.error(f"Failed to parse response for '{spanish_word}': {e}")
                return None
        
        return None

    # def get_similar_phrases(self, spanish_word: str, max_retries: int = 3) -> List[str]:
    #     """Get similar Spanish phrases using Glosbe API"""
    #     self._rate_limit()
    #     
    #     url = 'https://iapi.glosbe.com/iapi3/similar/similarPhrasesMany'
    #     params = {
    #         'p': spanish_word,
    #         'l1': 'es',  # Spanish
    #         'l2': 'ko',  # Korean
    #         'removeDuplicates': 'true',
    #         'searchCriteria': 'WORDLIST-ALPHABETICALLY-3-s;PREFIX-PRIORITY-3-s;TRANSLITERATED-PRIORITY-3-s;FUZZY-PRIORITY-3-s;WORDLIST-ALPHABETICALLY-3-r;PREFIX-PRIORITY-3-r;TRANSLITERATED-PRIORITY-3-r;FUZZY-PRIORITY-3-r',
    #         'env': 'en'
    #     }
    #     
    #     for attempt in range(max_retries):
    #         try:
    #             response = self.session.get(url, params=params, timeout=10)
    #             
    #             if response.status_code == 429:
    #                 wait_time = (2 ** attempt) * 2
    #                 logger.warning(f"Glosbe rate limit hit, waiting {wait_time}s...")
    #                 time.sleep(wait_time)
    #                 continue
    #             
    #             response.raise_for_status()
    #             data = response.json()
    #             
    #             phrases = []
    #             for phrase_data in data.get('phrases', []):
    #                 if not phrase_data.get('reverse', False):  # Only Spanish phrases
    #                     phrase = phrase_data.get('phrase', '').strip()
    #                     if phrase and phrase != spanish_word and len(phrase.split()) <= 3:  # Short phrases only
    #                         phrases.append(phrase)
    #             
    #             logger.debug(f"Found {len(phrases)} similar phrases for: {spanish_word}")
    #             return phrases[:5]  # Limit to 5 examples
    #             
    #         except Exception as e:
    #             logger.error(f"Similar phrases API failed for '{spanish_word}' (attempt {attempt+1}/{max_retries}): {e}")
    #             if attempt < max_retries - 1:
    #                 time.sleep(1)
    #                 continue
    #             return []
    #     
    #     return []

class DictionaryBuilder:
    """Main dictionary building logic"""

    def __init__(self, client: GoogleTranslateClient):
        self.client = client
        self.translations = {}  # word -> translation
        # self.examples = {}      # word -> list of example phrases  # Commented out for speed
        self.processed_words = set()
        self.problematic_words = set()

    def load_word_list(self, file_path: str) -> List[Tuple[str, str]]:
        """Load Spanish words with flags from file"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                word_flag_pairs = []
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#'):  # Skip comments
                        if '|' in line:
                            # Format: word|flags
                            word, flags = line.split('|', 1)
                            word_flag_pairs.append((word.strip(), flags.strip()))
                        else:
                            # Just word
                            word_flag_pairs.append((line.strip(), ''))
                
                # Remove duplicates
                seen = set()
                unique_pairs = []
                for word, flags in word_flag_pairs:
                    if word not in seen:
                        seen.add(word)
                        unique_pairs.append((word, flags))
                
                logger.info(f"Loaded {len(unique_pairs)} unique words from {file_path}")
                return unique_pairs
        except FileNotFoundError:
            logger.error(f"Word list file not found: {file_path}")
            sys.exit(1)

    def process_word(self, spanish_word: str, flags: str = '') -> Optional[str]:
        """Process a single word: get translation only (fast mode)"""
        try:
            # Get translation
            translation = self.client.get_translation(spanish_word)

            # Validate translation
            if not translation or len(translation.strip()) == 0:
                logger.warning(f"No valid translation found for: {spanish_word}")
                return None

            return translation.strip()
        except Exception as e:
            logger.error(f"Failed to process word '{spanish_word}': {e}")
            return None

    def process_words(self, word_flag_pairs: List[Tuple[str, str]], batch_size: int = 100, max_workers: int = 30) -> Dict[str, Tuple[str, str]]:
        """Process words in batches with concurrent API calls (LIGHTNING FAST)"""
        results = {}

        for i in range(0, len(word_flag_pairs), batch_size):
            batch = word_flag_pairs[i:i + batch_size]
            batch_num = i//batch_size + 1
            total_batches = (len(word_flag_pairs) + batch_size - 1)//batch_size
            logger.info(f"Processing batch {batch_num}/{total_batches} ({len(batch)} words)")

            # Filter out already processed words
            words_to_process = [(word, flags) for word, flags in batch if word not in self.processed_words]

            if words_to_process:
                # Use ThreadPoolExecutor for concurrent API calls
                with ThreadPoolExecutor(max_workers=max_workers) as executor:
                    # Submit all tasks
                    future_to_word = {
                        executor.submit(self.process_word, word, flags): (word, flags)
                        for word, flags in words_to_process
                    }

                    # Process completed tasks
                    for future in as_completed(future_to_word):
                        word, flags = future_to_word[future]
                        try:
                            translation = future.result()
                            if translation:
                                results[word] = (translation, flags)
                                self.translations[word] = translation
                                # Remove from problematic if it was there
                                self.problematic_words.discard(word)
                            else:
                                # Translation failed - add to problematic
                                self.problematic_words.add(word)
                        except Exception as e:
                            logger.error(f"Unexpected error processing word '{word}': {e}")
                            self.problematic_words.add(word)

                        # Mark as processed regardless of success/failure
                        self.processed_words.add(word)

            # Save progress after each batch
            self.save_progress(results)
            
            # Small delay between batches to be respectful to APIs
            if i + batch_size < len(word_flag_pairs):
                time.sleep(0.1)

        return results

    def save_progress(self, results: Dict[str, Tuple[str, str]]):
        """Save current progress to resume later"""
        progress_data = {
            'translations': self.translations,
            # 'examples': self.examples,  # Commented out for speed
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
                # self.examples = data.get('examples', {})  # Commented out for speed
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

    def create_yomichan_entry(self, spanish: str, korean: str, flags: str) -> List:
        """Create a Yomichan dictionary entry with translation and flags (fast mode)"""
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

        # Add flags if available
        if flags:
            content.append({"tag": "br"})
            content.append({
                "tag": "div",
                "content": [
                    {
                        "tag": "small",
                        "content": f"Flags: {flags}"
                    }
                ]
            })

        # Examples commented out for speed
        # # Add examples if available
        # if examples:
        #     content.append({"tag": "br"})
        #     content.append({
        #         "tag": "div",
        #         "content": [
        #             {
        #                 "tag": "em",
        #                 "content": "Examples:"
        #             }
        #         ]
        #     })
        # 
        #     example_list = []
        #     for example in examples[:3]:  # Limit to 3 examples
        #         example_list.append({
        #             "tag": "li",
        #             "content": example
        #         })
        # 
        #     if example_list:
        #         content.append({
        #             "tag": "ul",
        #             "content": example_list
        #         })

        return [
            spanish,  # term
            "",       # reading
            "",       # definition_tags
            "unknown", # rule_identifier
            0,        # popularity_score
            [         # structured_content
                {
                    "type": "structured-content",
                    "content": content
                }
            ],
            0,        # sequence
            ""        # term_tags
        ]

    def save_yomichan(self, results: Dict[str, Tuple[str, str]], output_file: str):
        """Save results in Yomichan format"""
        entries = []
        for spanish, (korean, flags) in results.items():
            entry = self.create_yomichan_entry(spanish, korean, flags)
            entries.append(entry)

        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(entries, f, ensure_ascii=False, indent=2)

        logger.info(f"Saved {len(entries)} entries to {output_file}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python google_translate_dictionary_builder.py <word_list_file>")
        sys.exit(1)

    word_list_file = sys.argv[1]

    logger.info("=" * 50)
    logger.info("Google Translate Dictionary Builder - Spanish to Korean")
    logger.info("=" * 50)

    # Initialize client and builder
    client = GoogleTranslateClient()
    builder = DictionaryBuilder(client)

    # Load previous progress if available
    builder.load_progress()

    # Load word list
    word_flag_pairs = builder.load_word_list(word_list_file)

    # Create a lookup dict for flags
    word_to_flags = {word: flags for word, flags in word_flag_pairs}

    # Prioritize problematic words from previous runs
    problematic_to_retry = []
    for word in builder.problematic_words:
        if word in word_to_flags:
            problematic_to_retry.append((word, word_to_flags[word]))

    # Regular words that haven't been processed yet
    regular_words_to_process = [
        (word, flags) for word, flags in word_flag_pairs 
        if word not in builder.processed_words and word not in builder.problematic_words
    ]

    # Combine: problematic words first, then regular words
    words_to_process = problematic_to_retry + regular_words_to_process

    logger.info(f"Problematic words to retry: {len(problematic_to_retry)}")
    logger.info(f"Regular words to process: {len(regular_words_to_process)}")
    logger.info(f"Total words to process: {len(words_to_process)}")
    logger.info(f"Already processed: {len(builder.processed_words)}")

    # Process words
    if words_to_process:
        results = builder.process_words(words_to_process)
    else:
        results = {word: (builder.translations[word], word_to_flags.get(word, '')) 
                  for word in builder.translations if word in word_to_flags}

    # Always save problematic words
    builder.save_problematic_words()

    # Save results
    builder.save_yomichan(results, 'term_bank_spanish_korean_full.json')

    # Final summary
    total_processed = len(builder.processed_words)
    total_successful = len(builder.translations)
    total_problematic = len(builder.problematic_words)

    logger.info("=" * 50)
    logger.info("BUILD COMPLETE")
    logger.info(f"Total words processed: {total_processed}")
    logger.info(f"Successful translations: {total_successful}")
    logger.info(f"Problematic words: {total_problematic}")
    if total_processed > 0:
        success_rate = total_successful / total_processed * 100
        logger.info(f"Success rate: {success_rate:.1f}%")
    else:
        logger.info("Success rate: 0%")
    logger.info("=" * 50)

if __name__ == "__main__":
    main()
