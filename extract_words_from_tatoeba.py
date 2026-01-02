#!/usr/bin/env python3
"""
Extract Spanish words from TATOEBA sentence pairs TSV file
"""

import re
from collections import Counter

def extract_words_from_tatoeba_tsv(tsv_file: str, output_file: str):
    """Extract unique Spanish words from TATOEBA TSV file"""
    spanish_words = set()

    with open(tsv_file, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue

            # Split by tabs
            parts = line.split('\t')
            if len(parts) >= 2:
                # Spanish sentence is the second column (index 1)
                spanish_sentence = parts[1]

                # Tokenize: split on whitespace and punctuation
                # Keep only alphabetic characters and apostrophes
                words = re.findall(r"\b[\wáéíóúüñÁÉÍÓÚÜÑ]+\b", spanish_sentence.lower())

                # Add to set
                spanish_words.update(words)

    # Sort alphabetically
    sorted_words = sorted(spanish_words)

    # Save to file
    with open(output_file, 'w', encoding='utf-8') as f:
        for word in sorted_words:
            f.write(word + '\n')

    print(f"Extracted {len(sorted_words)} unique Spanish words from TATOEBA sentences")
    print(f"Saved to {output_file}")

    return sorted_words

if __name__ == "__main__":
    tsv_file = "Sentence pairs in Spanish-Korean - 2026-01-02.tsv"
    output_file = "spanish_words_from_tatoeba.txt"

    extract_words_from_tatoeba_tsv(tsv_file, output_file)