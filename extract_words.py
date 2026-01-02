#!/usr/bin/env python3
"""
Extract Spanish words and flags from Hunspell es_CO.dic file
"""

def extract_spanish_words():
    """Extract Spanish words and their flags from es_CO.dic file"""
    words_with_flags = []
    
    with open('es_CO.dic', 'r', encoding='latin-1') as f:
        lines = f.readlines()[1:]  # Skip first line (count)

    for line in lines:
        line = line.strip()
        if line and not line.startswith('#'):  # Skip comments
            if '/' in line:
                word, flags = line.split('/', 1)
                words_with_flags.append((word.strip(), flags.strip()))
            else:
                # Some lines might not have flags
                words_with_flags.append((line.strip(), ''))

    print(f'Extracted {len(words_with_flags)} Spanish words with flags from es_CO.dic')
    print('Sample entries:', words_with_flags[:5])

    # Save to file with flags
    with open('spanish_words_with_flags.txt', 'w', encoding='utf-8') as f:
        for word, flags in words_with_flags:
            f.write(f'{word}|{flags}\n')

    print('Saved to spanish_words_with_flags.txt')
    
    # Also save just words for compatibility
    words_only = [word for word, flags in words_with_flags]
    with open('spanish_words_from_dic.txt', 'w', encoding='utf-8') as f:
        f.write('\n'.join(words_only))

    print('Saved words only to spanish_words_from_dic.txt')
    return words_with_flags

if __name__ == '__main__':
    extract_spanish_words()