with open('all_spanish_words.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()
    print(f'Total words in all_spanish_words.txt: {len(lines)}')

import json
with open('progress.json', 'r', encoding='utf-8') as f:
    data = json.load(f)
    print(f'Processed words: {len(data["processed_words"])}')
    print(f'Successful translations: {len(data["translations"])}')