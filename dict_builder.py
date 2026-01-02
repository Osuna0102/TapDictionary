#!/usr/bin/env python3
"""
Spanish-Korean Dictionary Builder for Yomichan Format

This script converts a CSV file with Spanish-Korean word pairs into
Yomichan dictionary format (term_bank JSON).

CSV Format: spanish_word,korean_translation
Example:
hola,안녕하세요
adiós,안녕히 가세요

Usage: python dict_builder.py input.csv output.json
"""

import json
import sys
import csv
from typing import List, Dict, Any

def create_yomichan_entry(spanish: str, korean: str, frequency: int = 0) -> List[Any]:
    """
    Create a Yomichan dictionary entry.

    Format: [term, reading, definition_tags, rule_identifier, popularity_score, definitions]
    """
    return [
        spanish,  # term
        "",       # reading (empty for Spanish)
        "",       # definition_tags
        "unknown", # rule_identifier
        frequency, # popularity_score
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

def csv_to_yomichan(csv_file: str, output_file: str):
    """Convert CSV to Yomichan term_bank JSON"""
    entries = []

    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) >= 2:
                spanish, korean = row[0].strip(), row[1].strip()
                if spanish and korean:
                    entry = create_yomichan_entry(spanish, korean)
                    entries.append(entry)

    # Write to JSON
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(entries, f, ensure_ascii=False, indent=2)

    print(f"Created {len(entries)} dictionary entries in {output_file}")

def create_sample_csv():
    """Create a sample CSV file with common Spanish words"""
    sample_words = [
        ("hola", "안녕하세요"),
        ("adiós", "안녕히 가세요"),
        ("gracias", "감사합니다"),
        ("por favor", "부탁합니다"),
        ("sí", "네"),
        ("no", "아니요"),
        ("agua", "물"),
        ("comida", "음식"),
        ("casa", "집"),
        ("amigo", "친구"),
        ("familia", "가족"),
        ("trabajo", "일"),
        ("escuela", "학교"),
        ("libro", "책"),
        ("tiempo", "시간"),
        ("dinero", "돈"),
        ("amor", "사랑"),
        ("feliz", "행복한"),
        ("triste", "슬픈"),
        ("grande", "큰"),
        ("pequeño", "작은"),
        ("rojo", "빨간"),
        ("azul", "파란"),
        ("verde", "초록"),
        ("uno", "하나"),
        ("dos", "둘"),
        ("tres", "셋"),
        ("diez", "열"),
        ("veinte", "스물"),
        ("cien", "백"),
        ("hombre", "남자"),
        ("mujer", "여자"),
        ("niño", "아이"),
        ("niña", "소녀"),
        ("padre", "아버지"),
        ("madre", "어머니"),
        ("hermano", "형제"),
        ("hermana", "자매"),
        ("hijo", "아들"),
        ("hija", "딸"),
        ("comer", "먹다"),
        ("beber", "마시다"),
        ("dormir", "자다"),
        ("correr", "달리다"),
        ("caminar", "걷다"),
        ("hablar", "말하다"),
        ("escuchar", "듣다"),
        ("ver", "보다"),
        ("leer", "읽다"),
        ("escribir", "쓰다"),
        ("ayer", "어제"),
        ("hoy", "오늘"),
        ("mañana", "내일"),
        ("lunes", "월요일"),
        ("martes", "화요일"),
        ("miércoles", "수요일"),
        ("jueves", "목요일"),
        ("viernes", "금요일"),
        ("sábado", "토요일"),
        ("domingo", "일요일"),
        ("enero", "1월"),
        ("febrero", "2월"),
        ("marzo", "3월"),
        ("abril", "4월"),
        ("mayo", "5월"),
        ("junio", "6월"),
        ("julio", "7월"),
        ("agosto", "8월"),
        ("septiembre", "9월"),
        ("octubre", "10월"),
        ("noviembre", "11월"),
        ("diciembre", "12월")
    ]

    with open('spanish_korean_sample.csv', 'w', encoding='utf-8', newline='') as f:
        writer = csv.writer(f)
        for spanish, korean in sample_words:
            writer.writerow([spanish, korean])

    print("Created sample CSV: spanish_korean_sample.csv")
    return 'spanish_korean_sample.csv'

if __name__ == "__main__":
    if len(sys.argv) == 1:
        # No arguments - create sample and convert it
        print("No CSV file provided. Creating sample dictionary...")
        csv_file = create_sample_csv()
        output_file = 'term_bank_spanish_korean.json'
        csv_to_yomichan(csv_file, output_file)

    elif len(sys.argv) == 3:
        csv_file = sys.argv[1]
        output_file = sys.argv[2]
        csv_to_yomichan(csv_file, output_file)

    else:
        print("Usage: python dict_builder.py [input.csv] [output.json]")
        print("Or run without arguments to create a sample dictionary")