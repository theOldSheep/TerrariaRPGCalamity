import os
import re

# Target file extensions used in Minecraft server setups and resource packs
TARGET_EXTENSIONS = ('.yml', '.yaml', '.json', '.mcmeta', '.properties')

# Regex pattern for CJK Unified Ideographs (Chinese characters)
CHINESE_REGEX = re.compile(r'[\u4e00-\u9fff]')
UNICODE_ESCAPE_REGEX = re.compile(r'\\u([0-9a-fA-F]{4})')

outputs = []

def decode_chinese_unicode_escapes(text):
    """
    Converts legacy Minecraft string unicode escapes (like \u4e2d) into 
    raw UTF-8 Chinese characters so matching works uniformly.
    Preserves non-Chinese escapes (like \u00a7 for color codes).
    """
    def replace_match(match):
        hex_code = match.group(1)
        char = chr(int(hex_code, 16))
        if CHINESE_REGEX.match(char):
            return char
        return match.group(0) # Keep original if it's not a Chinese character
    
    return UNICODE_ESCAPE_REGEX.sub(replace_match, text)

def load_locale(locale_path):
    """
    Parses the locale YAML file without external dependencies.
    Normalizes keys/values by stripping quotes and resolving unicode escapes.
    """
    translations = {}
    if not os.path.exists(locale_path):
        outputs.append(f"[-] Error: Locale file '{locale_path}' not found.")
        return translations

    with open(locale_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if ':' in line:
                parts = line.split(':', 1)
                key = parts[0].strip().strip('"').strip("'")
                val = parts[1].strip().strip('"').strip("'")
                
                # Normalize both sides to raw characters for flawless matching
                key = decode_chinese_unicode_escapes(key)
                val = decode_chinese_unicode_escapes(val)
                
                translations[key] = val
    return translations

def localize_project(locale_code):
    locale_filename = f"locale_{locale_code}.yml"
    locale_filepath = os.path.join(os.path.dirname(__file__), locale_filename)
    workspace_dir = os.path.join(os.path.dirname(__file__), 'workspace')
    
    outputs.append(f"[+] Loading translations from {locale_filename}...")
    translations = load_locale(locale_filepath)
    if not translations:
        outputs.append(f"[-] Error: Locale contains no translations.")
        return

    # Sort keys by length descending to prioritize the longest string matches
    sorted_keys = sorted(translations.keys(), key=len, reverse=True)
    
    outputs.append(f"[+] Scanning workspace: {workspace_dir}")
    untranslated_count = 0

    for root, _, files in os.walk(workspace_dir):
        for file in files:
            if not file.endswith(TARGET_EXTENSIONS):
                continue
                
            file_path = os.path.join(root, file)
            relative_path = os.path.relpath(file_path, workspace_dir)
            
            # 1. Read and normalize content
            with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
                content = f.read()
            
            normalized_content = decode_chinese_unicode_escapes(content)
            updated_content = normalized_content
            
            # 2. Perform greedy replacements
            for key in sorted_keys:
                if key in updated_content:
                    updated_content = updated_content.replace(key, translations[key])
            
            # 3. Save modifications back to file in UTF-8
            if updated_content != normalized_content:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(updated_content)
                outputs.append(f"[✓] Localized: {relative_path}")
            
            # 4. Scan line-by-line for unreplaced / partial Chinese characters
            lines = updated_content.splitlines()
            lines_original = normalized_content.splitlines()
            for idx, line in enumerate(lines):
                if CHINESE_REGEX.search(line):
                    untranslated_count += 1
                    outputs.append(f"    [!] MISSING/PARTIAL at {relative_path}:{idx + 1} || {lines_original[idx]} -> {line.strip()}")

    outputs.append("\n[+] Localization process completed.")
    if untranslated_count > 0:
        outputs.append(f"[!] Found {untranslated_count} unlocalized occurrences. Update your {locale_filename} to resolve them.")
    else:
        outputs.append("[✓] Clean sweep! No unlocalized Chinese characters remain.")

if __name__ == "__main__":
    target_locale = input("Enter target locale code (e.g., EN, RU, FR): ").strip()
    localize_project(target_locale)
    with open('out.txt', 'w', encoding='utf-8') as out:
        out.write('\n'.join(outputs))