#!/usr/bin/env python3
"""
Glosbe Dictionary Builder for Spanish-Korean

This script uses APIs to create a Spanish-Korean dictionary
with translations and example phrases in Yomitan format.

Features:
- Fetches translations from MyMemory API
- Gets example phrases using Glosbe similar phrases API
- Tracks problematic words for manual review
- Saves in Yomitan dictionary format (JSON)
- Resume capability for interrupted runs

Usage: python glosbe_dictionary_builder.py spanish_words_with_flags.txt
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

class GlosbeClient:
    """Client for Glosbe API interactions with VPN failover support"""

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Spanish-Korean Dictionary Builder/1.0'
        })
        self.vpn_available = self._check_vpn_availability()
        self.current_location = None
        self.request_count = 0
        self.rate_limit_hits = 0
        self.last_request_time = 0
        self.min_request_interval = 1.0  # Minimum 1 second between requests

    def _rate_limit(self):
        """Enforce minimum interval between API requests"""
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

    def _check_vpn_availability(self) -> bool:
        """Check if a supported VPN client is available"""
        import subprocess
        
        # Try multiple VPN clients in order of preference (free options first)
        vpn_clients = [
            {
                'name': 'ProtonVPN',
                'commands': ['protonvpn-cli', 'protonvpn'],
                'check_cmd': ['protonvpn-cli', 'status'],
                'connect_cmd': ['protonvpn-cli', 'connect', '--free'],  # Use free servers
                'disconnect_cmd': ['protonvpn-cli', 'disconnect'],
                'list_cmd': ['protonvpn-cli', 'connect', '--help'],
                'requires_admin': False,
                'is_free': True
            },
            {
                'name': 'Windscribe',
                'commands': ['windscribe', 'windscribe.exe'],
                'check_cmd': ['windscribe', 'status'],
                'connect_cmd': ['windscribe', 'connect', 'best'],  # Connect to best free location
                'disconnect_cmd': ['windscribe', 'disconnect'],
                'list_cmd': ['windscribe', 'locations'],
                'requires_admin': False,
                'is_free': True
            },
            {
                'name': 'NordVPN',
                'commands': ['nordvpn', 'nordvpn.exe'],
                'check_cmd': ['nordvpn', 'status'],
                'connect_cmd': ['nordvpn', 'connect'],
                'disconnect_cmd': ['nordvpn', 'disconnect'],
                'list_cmd': ['nordvpn', 'countries'],
                'requires_admin': False,
                'is_free': False  # Has free trial
            },
            {
                'name': 'ExpressVPN',
                'commands': ['expressvpn', 'expressvpn.exe'],
                'check_cmd': ['expressvpn', 'status'],
                'connect_cmd': ['expressvpn', 'connect', 'smart'],
                'disconnect_cmd': ['expressvpn', 'disconnect'],
                'list_cmd': ['expressvpn', 'list', 'all'],
                'requires_admin': False,
                'is_free': False
            },
            {
                'name': 'TunnelBear',
                'commands': ['tunnelbear', 'TunnelBear.exe', '"C:\\Program Files\\TunnelBear\\TunnelBear.exe"', '"C:\\Program Files (x86)\\TunnelBear\\TunnelBear.exe"'],
                'check_cmd': ['tunnelbear', '--version'],
                'connect_cmd': ['tunnelbear', 'connect'],
                'disconnect_cmd': ['tunnelbear', 'disconnect'],
                'list_cmd': ['tunnelbear', 'list'],
                'requires_admin': True,
                'is_free': True  # Has free tier
            }
        ]
        
        for vpn in vpn_clients:
            for cmd in vpn['commands']:
                try:
                    # Check if command exists
                    result = subprocess.run([cmd] + vpn['check_cmd'][1:], 
                                          capture_output=True, 
                                          text=True, 
                                          timeout=10)
                    if result.returncode == 0:
                        self.vpn_client = vpn
                        logger.info(f"{vpn['name']} VPN detected and available")
                        return True
                except (subprocess.TimeoutExpired, FileNotFoundError, subprocess.SubprocessError):
                    continue
            
            # Special check for TunnelBear admin requirement
            if vpn['name'] == 'TunnelBear':
                try:
                    result = subprocess.run(vpn['check_cmd'], 
                                          capture_output=True, 
                                          text=True, 
                                          timeout=5)
                    if result.returncode == 0:
                        self.vpn_client = vpn
                        logger.warning(f"{vpn['name']} VPN detected but requires administrator privileges")
                        return True
                except Exception as e:
                    if "Acceso denegado" in str(e) or "Access denied" in str(e) or "[WinError 5]" in str(e):
                        self.vpn_client = vpn
                        logger.warning(f"{vpn['name']} CLI requires administrator privileges. Assuming VPN is available and will attempt to switch when needed.")
                        return True
        
        logger.warning("No supported VPN client found. Install one of: Mullvad, ProtonVPN, NordVPN, ExpressVPN, or TunnelBear")
        logger.warning("Without VPN switching, the script will only use retry logic for rate limits")
        return False

    def _switch_vpn_location(self) -> bool:
        """Switch to a different VPN location using the detected VPN client"""
        if not self.vpn_available or not hasattr(self, 'vpn_client'):
            return False
            
        import subprocess
        import random
        
        vpn = self.vpn_client
        
        try:
            logger.info(f"Switching {vpn['name']} VPN location...")
            
            # Disconnect first
            logger.info("Disconnecting current VPN connection...")
            result = subprocess.run(vpn['disconnect_cmd'], 
                                  capture_output=True, 
                                  text=True, 
                                  timeout=30)
            if result.returncode != 0:
                logger.warning(f"Disconnect command failed: {result.stderr}")
            
            time.sleep(3)  # Wait for disconnect
            
            # Connect to new location
            if vpn['name'] == 'ProtonVPN':
                # ProtonVPN: connect to free server
                logger.info("Connecting to ProtonVPN free server...")
                result = subprocess.run(['protonvpn-cli', 'connect', '--free'], 
                                      capture_output=True, 
                                      text=True, 
                                      timeout=60)
            elif vpn['name'] == 'Windscribe':
                # Windscribe: connect to best free location
                logger.info("Connecting to Windscribe best free location...")
                result = subprocess.run(['windscribe', 'connect', 'best'], 
                                      capture_output=True, 
                                      text=True, 
                                      timeout=60)
            elif vpn['name'] == 'NordVPN':
                # NordVPN: connect to random server
                logger.info("Connecting to random NordVPN server...")
                result = subprocess.run(['nordvpn', 'connect'], 
                                      capture_output=True, 
                                      text=True, 
                                      timeout=60)
            elif vpn['name'] == 'ExpressVPN':
                # ExpressVPN: connect to smart location
                logger.info("Connecting to ExpressVPN smart location...")
                result = subprocess.run(['expressvpn', 'connect', 'smart'], 
                                      capture_output=True, 
                                      text=True, 
                                      timeout=60)
            elif vpn['name'] == 'TunnelBear':
                # TunnelBear: connect to random location
                locations = [
                    'United States', 'Canada', 'United Kingdom', 'Germany', 
                    'France', 'Italy', 'Spain', 'Netherlands', 'Switzerland',
                    'Australia', 'Japan', 'Singapore', 'Brazil', 'Mexico'
                ]
                available_locations = [loc for loc in locations if loc != self.current_location]
                new_location = random.choice(available_locations) if available_locations else random.choice(locations)
                
                logger.info(f"Connecting TunnelBear to {new_location}...")
                result = subprocess.run(['tunnelbear', 'connect', new_location], 
                                      capture_output=True, 
                                      text=True, 
                                      timeout=60)
                if result.returncode == 0:
                    self.current_location = new_location
            
            if result.returncode == 0:
                logger.info(f"Successfully switched {vpn['name']} VPN location")
                time.sleep(8)  # Wait for connection to stabilize
                return True
            else:
                logger.error(f"Failed to switch {vpn['name']} VPN: {result.stderr}")
                return False
                
        except (subprocess.TimeoutExpired, subprocess.SubprocessError) as e:
            if vpn['requires_admin'] and ("Acceso denegado" in str(e) or "Access denied" in str(e) or "[WinError 5]" in str(e)):
                logger.error(f"{vpn['name']} VPN switching requires administrator privileges. Please run the script as administrator or switch VPN manually.")
                return False
            else:
                logger.error(f"{vpn['name']} VPN switch failed: {e}")
                return False

    def _handle_rate_limit(self, url: str) -> bool:
        """Handle rate limiting with retries and VPN switching"""
        self.rate_limit_hits += 1
        logger.warning(f"Rate limit hit #{self.rate_limit_hits} for {url}")
        
        # First try exponential backoff
        base_delay = 60  # Start with 1 minute
        max_delay = 900  # Max 15 minutes
        
        for attempt in range(3):
            delay = min(base_delay * (2 ** attempt), max_delay)
            logger.info(f"Waiting {delay} seconds before retry...")
            time.sleep(delay)
            
            # Test if the rate limit is still active with a simple request
            try:
                response = self.session.get('https://httpbin.org/ip', timeout=10)
                if response.status_code == 200:
                    logger.info("Rate limit appears resolved, continuing...")
                    return True
            except Exception:
                continue
        
        # If retries fail, try VPN switch
        if self.vpn_available and self.rate_limit_hits >= 2:
            logger.info("Retries failed, attempting VPN switch...")
            if self._switch_vpn_location():
                self.rate_limit_hits = 0  # Reset counter
                return True
        
        # If everything fails, ask user to manually switch VPN
        logger.error("All automatic recovery methods failed.")
        logger.error("Please manually switch your VPN location and press Enter to continue...")
        input("Press Enter when VPN is switched: ")
        self.rate_limit_hits = 0
        return True

    def get_translation(self, spanish_word: str) -> Optional[str]:
        """Get Korean translation for Spanish word using MyMemory API"""
        self._rate_limit()
        
        try:
            params = {
                'q': spanish_word,
                'langpair': 'es|ko'
            }

            response = self.session.get(
                'https://api.mymemory.translated.net/get',
                params=params,
                timeout=15
            )
            
            # Handle rate limiting
            if response.status_code == 429:
                if self._handle_rate_limit('MyMemory API'):
                    # Retry the request after handling rate limit
                    return self.get_translation(spanish_word)
                else:
                    return None
            
            response.raise_for_status()

            data = response.json()
            matches = data.get('matches', [])

            if matches:
                # Filter out empty translations
                valid_matches = [m for m in matches if m.get('translation', '').strip()]

                if valid_matches:
                    # Prefer translations that contain Korean characters
                    def is_korean_text(text):
                        return any('\uac00' <= char <= '\ud7af' or '\u1100' <= char <= '\u11ff' for char in text)

                    korean_matches = [m for m in valid_matches if is_korean_text(m.get('translation', ''))]
                    if korean_matches:
                        # Sort Korean matches by match score and quality
                        best_match = max(korean_matches,
                                       key=lambda x: (x.get('match', 0), x.get('quality', 0)))
                    else:
                        # Fallback to any valid match if no Korean found
                        best_match = max(valid_matches,
                                       key=lambda x: (x.get('match', 0), x.get('quality', 0)))

                    translation = best_match.get('translation', '').strip()

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
        self._rate_limit()
        
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
            
            # Handle rate limiting
            if response.status_code == 429:
                if self._handle_rate_limit('Glosbe API'):
                    # Retry the request after handling rate limit
                    return self.get_similar_phrases(spanish_word)
                else:
                    return []

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

class DictionaryBuilder:
    """Main dictionary building logic"""

    def __init__(self, client: GlosbeClient):
        self.client = client
        self.translations = {}  # word -> translation
        self.examples = {}      # word -> list of example phrases
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
                            # New format: word|flags
                            word, flags = line.split('|', 1)
                            word_flag_pairs.append((word.strip(), flags.strip()))
                        else:
                            # Old format: just word
                            word_flag_pairs.append((line.strip(), ''))
                
                # Remove duplicates based on word only
                seen = set()
                unique_pairs = []
                for word, flags in word_flag_pairs:
                    if word not in seen:
                        seen.add(word)
                        unique_pairs.append((word, flags))
                
                logger.info(f"Loaded {len(unique_pairs)} unique words with flags from {file_path}")
                return unique_pairs
        except FileNotFoundError:
            logger.error(f"Word list file not found: {file_path}")
            sys.exit(1)

    def process_word(self, spanish_word: str, flags: str = '') -> Tuple[Optional[str], List[str]]:
        """Process a single word: get translation and examples"""
        logger.info(f"Processing: {spanish_word} ({flags})")

        # Get translation
        translation = self.client.get_translation(spanish_word)

        # Get example phrases
        examples = self.client.get_similar_phrases(spanish_word)

        # Validate translation
        if not translation or len(translation.strip()) == 0:
            logger.warning(f"No valid translation found for: {spanish_word}")
            self.problematic_words.add(spanish_word)
            return None, examples

        return translation.strip(), examples

    def process_words(self, word_flag_pairs: List[Tuple[str, str]], batch_size: int = 50, max_workers: int = 5) -> Dict[str, Tuple[str, str, List[str]]]:
        """Process words in batches with concurrent API calls"""
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
                            translation, examples = future.result()
                            if translation:
                                results[word] = (translation, flags, examples)
                                self.translations[word] = translation
                                self.examples[word] = examples
                        except Exception as e:
                            logger.error(f"Error processing word '{word}': {e}")
                            self.problematic_words.add(word)

                        self.processed_words.add(word)

            # Save progress after each batch
            self.save_progress(results)
            
            # Small delay between batches to be respectful to APIs
            if i + batch_size < len(word_flag_pairs):
                time.sleep(1)

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

    def create_yomichan_entry(self, spanish: str, korean: str, flags: str, examples: List[str]) -> List:
        """Create a Yomichan dictionary entry with translation, flags and examples"""
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

    def save_yomichan(self, results: Dict[str, Tuple[str, str, List[str]]], filename: str):
        """Save results in Yomichan term_bank format"""
        entries = []

        for spanish, (korean, flags, examples) in results.items():
            entry = self.create_yomichan_entry(spanish, korean, flags, examples)
            entries.append(entry)

        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(entries, f, ensure_ascii=False, indent=2)

        logger.info(f"Saved {len(entries)} entries to {filename}")


def main():
    if len(sys.argv) < 2:
        print("Usage: python glosbe_dictionary_builder.py spanish_words_with_flags.txt")
        print("Examples:")
        print("  python glosbe_dictionary_builder.py spanish_words_with_flags.txt")
        sys.exit(1)

    word_list_file = sys.argv[1]

    # Initialize components
    client = GlosbeClient()
    builder = DictionaryBuilder(client)

    # Load previous progress
    builder.load_progress()

    # Load word list with flags
    word_flag_pairs = builder.load_word_list(word_list_file)

    # Filter out already processed words
    words_to_process = [(word, flags) for word, flags in word_flag_pairs if word not in builder.processed_words]
    logger.info(f"Need to process {len(words_to_process)} out of {len(word_flag_pairs)} total words")

    results = {}

    if not words_to_process:
        logger.info("All words already processed!")
    else:
        # Process words with concurrent API calls
        results = builder.process_words(words_to_process, batch_size=25, max_workers=1)

    # Always save problematic words
    builder.save_problematic_words()

    # Save results
    builder.save_yomichan(results, 'term_bank_spanish_korean_full.json')

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
    main()
