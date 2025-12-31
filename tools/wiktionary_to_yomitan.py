#!/usr/bin/env python3
"""
Wiktionary to Yomitan Converter
Converts Wiktionary JSON data to Yomitan dictionary format

Usage:
    python wiktionary_to_yomitan.py input.json output.zip

Requirements:
    pip install wiktextract
"""

import json
import zipfile
import sys
from typing import List, Dict, Any

def extract_korean_translations(entry: Dict[str, Any]) -> List[str]:
    """
    Extract Korean translations from a Wiktionary entry
    
    Wiktionary structure varies by language, adjust as needed
    """
    translations = []
    
    # Check for translations field
    if 'translations' in entry:
        for translation in entry['translations']:
            # Look for Korean language code
            if translation.get('lang_code') == 'ko' or translation.get('lang') == 'Korean':
                korean_text = translation.get('word') or translation.get('text')
                if korean_text:
                    translations.append(korean_text)
    
    # Check for senses (meanings)
    if 'senses' in entry:
        for sense in entry['senses']:
            if 'translations' in sense:
                for trans in sense['translations']:
                    if trans.get('lang_code') == 'ko' or trans.get('lang') == 'Korean':
                        korean_text = trans.get('word') or trans.get('text')
                        if korean_text and korean_text not in translations:
                            translations.append(korean_text)
    
    return translations


def extract_part_of_speech(entry: Dict[str, Any]) -> str:
    """Extract part of speech from entry"""
    pos = entry.get('pos', '')
    if pos:
        return pos
    
    # Check in senses
    if 'senses' in entry and entry['senses']:
        return entry['senses'][0].get('pos', '')
    
    return ''


def convert_to_yomitan(input_file: str, output_file: str, 
                       source_lang: str = 'es', target_lang: str = 'ko'):
    """
    Convert Wiktionary JSON to Yomitan format
    
    Args:
        input_file: Path to Wiktionary JSON file
        output_file: Path to output ZIP file
        source_lang: Source language code (e.g., 'es' for Spanish)
        target_lang: Target language code (e.g., 'ko' for Korean)
    """
    
    print(f"Loading Wiktionary data from {input_file}...")
    
    # Load Wiktionary data
    with open(input_file, 'r', encoding='utf-8') as f:
        entries = []
        for line in f:
            try:
                entry = json.loads(line.strip())
                entries.append(entry)
            except json.JSONDecodeError:
                continue
    
    print(f"Loaded {len(entries)} entries")
    
    # Convert to Yomitan format
    term_bank = []
    sequence = 1
    
    for entry in entries:
        # Get word
        word = entry.get('word', '').strip()
        if not word:
            continue
        
        # Get Korean translations
        korean_translations = extract_korean_translations(entry)
        if not korean_translations:
            continue
        
        # Get part of speech
        pos = extract_part_of_speech(entry)
        
        # Calculate score (higher for more common words)
        # You can improve this with frequency data
        score = 5
        if entry.get('frequent', False):
            score = 10
        
        # Yomitan format: [expression, reading, tags, rules, score, glosses, sequence, termTags]
        term_bank.append([
            word,                   # expression (Spanish word)
            "",                     # reading (empty for Spanish)
            "",                     # tags
            pos[:20] if pos else "", # rules (use POS, truncate to 20 chars)
            score,                  # score
            korean_translations,    # glosses (Korean translations)
            sequence,               # sequence number
            ""                      # termTags
        ])
        
        sequence += 1
        
        # Log progress every 1000 entries
        if sequence % 1000 == 0:
            print(f"Processed {sequence} entries...")
    
    print(f"Converted {len(term_bank)} entries")
    
    if len(term_bank) == 0:
        print("ERROR: No translations found!")
        print("Check if the input file has Korean translations")
        return
    
    # Create index.json
    index = {
        "title": f"{source_lang.upper()}-{target_lang.upper()} Wiktionary",
        "format": 3,
        "revision": "wiktionary_2024",
        "sequenced": True,
        "author": "Wiktionary Contributors",
        "url": f"https://{source_lang}.wiktionary.org",
        "description": f"{source_lang.upper()} to {target_lang.upper()} dictionary extracted from Wiktionary",
        "attribution": "CC BY-SA 3.0"
    }
    
    # Create ZIP file
    print(f"Creating {output_file}...")
    with zipfile.ZipFile(output_file, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Write index
        zf.writestr('index.json', json.dumps(index, ensure_ascii=False, indent=2))
        
        # Write term banks (split into chunks of 10000 entries)
        chunk_size = 10000
        for i in range(0, len(term_bank), chunk_size):
            chunk = term_bank[i:i+chunk_size]
            bank_num = (i // chunk_size) + 1
            zf.writestr(
                f'term_bank_{bank_num}.json',
                json.dumps(chunk, ensure_ascii=False)
            )
            print(f"  - term_bank_{bank_num}.json ({len(chunk)} entries)")
    
    print(f"\n✅ Success! Created {output_file}")
    print(f"   Total entries: {len(term_bank)}")
    print(f"   Dictionary: {index['title']}")
    print(f"\nYou can now import this dictionary into your app!")


def create_sample_dictionary():
    """
    Create a sample Spanish-Korean dictionary for testing
    """
    
    term_bank = [
        # Common greetings
        ["hola", "", "", "", 10, ["안녕", "안녕하세요"], 1, "greeting"],
        ["adiós", "", "", "", 10, ["안녕", "잘 가", "안녕히 가세요"], 2, "greeting"],
        ["buenos días", "", "", "", 10, ["좋은 아침", "안녕하세요"], 3, "greeting"],
        ["buenas tardes", "", "", "", 10, ["안녕하세요"], 4, "greeting"],
        ["buenas noches", "", "", "", 10, ["안녕하세요", "잘 자"], 5, "greeting"],
        
        # Basic words
        ["sí", "", "", "", 10, ["네", "예"], 6, ""],
        ["no", "", "", "", 10, ["아니오", "아니"], 7, ""],
        ["gracias", "", "", "", 10, ["감사합니다", "고맙습니다"], 8, ""],
        ["por favor", "", "", "", 10, ["부탁합니다", "주세요"], 9, ""],
        ["perdón", "", "", "", 9, ["미안합니다", "죄송합니다"], 10, ""],
        ["lo siento", "", "", "", 9, ["미안합니다", "죄송합니다"], 11, ""],
        
        # Common nouns
        ["casa", "", "n", "", 8, ["집"], 12, ""],
        ["libro", "", "n", "", 7, ["책"], 13, ""],
        ["agua", "", "n", "", 9, ["물"], 14, ""],
        ["comida", "", "n", "", 8, ["음식"], 15, ""],
        ["tiempo", "", "n", "", 8, ["시간", "날씨"], 16, ""],
        ["día", "", "n", "", 9, ["날", "하루"], 17, ""],
        ["noche", "", "n", "", 8, ["밤"], 18, ""],
        ["mañana", "", "n", "", 8, ["아침", "내일"], 19, ""],
        ["tarde", "", "n", "", 7, ["오후", "늦은"], 20, ""],
        ["persona", "", "n", "", 8, ["사람"], 21, ""],
        ["hombre", "", "n", "", 8, ["남자", "사람"], 22, ""],
        ["mujer", "", "n", "", 8, ["여자"], 23, ""],
        ["niño", "", "n", "", 7, ["아이", "남자아이"], 24, ""],
        ["niña", "", "n", "", 7, ["여자아이"], 25, ""],
        
        # Common verbs
        ["ser", "", "v", "", 10, ["이다", "있다"], 26, ""],
        ["estar", "", "v", "", 10, ["있다", "이다"], 27, ""],
        ["tener", "", "v", "", 9, ["가지다", "있다"], 28, ""],
        ["hacer", "", "v", "", 9, ["하다", "만들다"], 29, ""],
        ["ir", "", "v", "", 9, ["가다"], 30, ""],
        ["venir", "", "v", "", 8, ["오다"], 31, ""],
        ["ver", "", "v", "", 8, ["보다"], 32, ""],
        ["comer", "", "v", "", 8, ["먹다"], 33, ""],
        ["beber", "", "v", "", 7, ["마시다"], 34, ""],
        ["dormir", "", "v", "", 7, ["자다"], 35, ""],
        
        # Colors
        ["rojo", "", "adj", "", 7, ["빨간", "빨강"], 36, ""],
        ["azul", "", "adj", "", 7, ["파란", "파랑"], 37, ""],
        ["verde", "", "adj", "", 7, ["초록", "녹색"], 38, ""],
        ["amarillo", "", "adj", "", 7, ["노란", "노랑"], 39, ""],
        ["negro", "", "adj", "", 7, ["검은", "검정"], 40, ""],
        ["blanco", "", "adj", "", 7, ["하얀", "흰색"], 41, ""],
        
        # Numbers
        ["uno", "", "num", "", 9, ["하나", "일"], 42, ""],
        ["dos", "", "num", "", 9, ["둘", "이"], 43, ""],
        ["tres", "", "num", "", 9, ["셋", "삼"], 44, ""],
        ["cuatro", "", "num", "", 8, ["넷", "사"], 45, ""],
        ["cinco", "", "num", "", 8, ["다섯", "오"], 46, ""],
    ]
    
    index = {
        "title": "Spanish-Korean Basic",
        "format": 3,
        "revision": "sample_v1",
        "sequenced": True,
        "author": "GodTap Dictionary",
        "url": "",
        "description": "Basic Spanish to Korean dictionary (sample)"
    }
    
    output_file = "spanish-korean-basic.zip"
    with zipfile.ZipFile(output_file, 'w', zipfile.ZIP_DEFLATED) as zf:
        zf.writestr('index.json', json.dumps(index, ensure_ascii=False, indent=2))
        zf.writestr('term_bank_1.json', json.dumps(term_bank, ensure_ascii=False))
    
    print(f"✅ Created sample dictionary: {output_file}")
    print(f"   Entries: {len(term_bank)}")
    print(f"\nThis is a basic dictionary for testing. For a full dictionary,")
    print(f"extract data from Wiktionary using wiktextract.")


if __name__ == "__main__":
    if len(sys.argv) == 1:
        print("Creating sample Spanish-Korean dictionary...")
        create_sample_dictionary()
    elif len(sys.argv) >= 3:
        input_file = sys.argv[1]
        output_file = sys.argv[2]
        source_lang = sys.argv[3] if len(sys.argv) > 3 else 'es'
        target_lang = sys.argv[4] if len(sys.argv) > 4 else 'ko'
        
        convert_to_yomitan(input_file, output_file, source_lang, target_lang)
    else:
        print("Usage:")
        print("  python wiktionary_to_yomitan.py                    # Create sample dictionary")
        print("  python wiktionary_to_yomitan.py input.json output.zip [source_lang] [target_lang]")
        print("\nExample:")
        print("  python wiktionary_to_yomitan.py wikt_es.json spanish-korean.zip es ko")
