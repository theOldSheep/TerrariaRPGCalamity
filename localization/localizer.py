import os
import re

TARGET_EXTENSIONS = ('.yml', '.yaml', '.json', '.mcmeta', '.properties', '.java')

CHINESE_REGEX = re.compile(r'[\u4e00-\u9fff]')
UNICODE_ESCAPE_REGEX = re.compile(r'\\u([0-9a-fA-F]{4})')

# Matches both §a (vanilla) and §#123456 (hex mod color codes)
COLOR_CODE_REGEX = re.compile(r'§#[0-9a-fA-F]{6}|§[0-9a-fA-fk-orK-OR0-9]')

# Handles quotes safely without tripping over internal colons
YAML_LINE_REGEX = re.compile(
    r"^\s*(?:\"([^\"]*)\"|'([^']*)'|([^:\s'\"]+))\s*:\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s'\"]+.*))?\s*$"
)

JSON_VAL_REGEX = re.compile(r'^(\s*"(?:subtitle|name|description|text)"\s*:\s*")(.*)("\s*,?\s*)$')
PROPERTIES_REGEX = re.compile(r'^([^=]+=\s*)(.*)$')

outputs = []

def decode_chinese_unicode_escapes(text):
    def replace_match(match):
        hex_code = match.group(1)
        return chr(int(hex_code, 16))
    return UNICODE_ESCAPE_REGEX.sub(replace_match, text)

def load_locale(locale_path):
    translations = {}
    if not os.path.exists(locale_path):
        outputs.append(f"[-] Error: Locale file '{locale_path}' not found.")
        return translations

    with open(locale_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            stripped = line.strip()
            if not stripped or stripped.startswith('#'):
                continue
            
            match = YAML_LINE_REGEX.match(stripped)
            if match:
                groups = match.groups()
                key = next((g for g in groups[:3] if g is not None), "")
                val = next((g for g in groups[3:] if g is not None), "")
                
                key = decode_chinese_unicode_escapes(key)
                val = decode_chinese_unicode_escapes(val)
                
                translations[key] = val
            else:
                outputs.append(f"[-] Warning: Skipping malformed locale line {line_num}: {stripped}")
                
    return translations

def tokenize_and_translate_text(text, translation_pattern, translations):
    if not text:
        return text

    # OPTIMIZATION: re.split with capturing groups natively retains both the split delimiters (the keys) 
    # and the un-split text. Even indices = untranslated, odd indices = matched keys.
    raw_segments = translation_pattern.split(text)
    
    if len(raw_segments) == 1:
        return text

    segments = []
    for i, seg in enumerate(raw_segments):
        if not seg:
            continue
        is_translated = (i % 2 == 1)
        text_content = translations[seg] if is_translated else seg
        segments.append(text_content)

    if not segments:
        return ""

    # Reconstruct segments using strict alphanumeric boundary spacing rules
    result = segments[0]
    for curr_text in segments[1:]:
        # Strip color formatting markers out to evaluate the visible textual boundary
        clean_prev = COLOR_CODE_REGEX.sub('', result)
        clean_curr = COLOR_CODE_REGEX.sub('', curr_text)

        if not clean_prev or not clean_curr:
            result += curr_text
            continue

        c1 = clean_prev[-1]
        c2 = clean_curr[0]

        add_space = False
        
        # BUGFIX: Only inject spaces if the boundary is between two word/alphanumeric characters.
        # This prevents spaces from being shoved into punctuation like `"` or `)`.
        if not c1.isspace() and not c2.isspace():
            if c1.isalnum() and c2.isalnum():
                # Avoid inserting spaces between two adjacent Chinese characters
                is_c1_chinese = bool(CHINESE_REGEX.fullmatch(c1))
                is_c2_chinese = bool(CHINESE_REGEX.fullmatch(c2))
                
                if not (is_c1_chinese and is_c2_chinese):
                    add_space = True

        if add_space:
            result += ' ' + curr_text
        else:
            result += curr_text

    return result

def process_line_by_file_type(line, ext, translation_pattern, translations):
    if ext in ('.json', '.mcmeta'):
        match = JSON_VAL_REGEX.match(line)
        if match:
            prefix, payload, suffix = match.groups()
            translated_payload = tokenize_and_translate_text(payload, translation_pattern, translations)
            return f"{prefix}{translated_payload}{suffix}"
            
    elif ext == '.properties':
        match = PROPERTIES_REGEX.match(line)
        if match:
            prefix, payload = match.groups()
            translated_payload = tokenize_and_translate_text(payload, translation_pattern, translations)
            return f"{prefix}{translated_payload}"

    return tokenize_and_translate_text(line, translation_pattern, translations)

def localize_project(locale_code):
    locale_filename = f"locale_{locale_code}.yml"
    locale_filepath = os.path.join(os.path.dirname(__file__), locale_filename)
    workspace_dir = os.path.join(os.path.dirname(__file__), 'workspace')
    
    outputs.append(f"[+] Loading translations from {locale_filename}...")
    translations = load_locale(locale_filepath)
    if not translations:
        outputs.append(f"[-] Error: Locale contains no translations.")
        return

    # Sort keys by length descending to prioritize greedy matches
    sorted_keys = sorted(translations.keys(), key=len, reverse=True)
    
    # Pre-compile the regex pattern once for the entire run (massive speedup)
    escaped_keys = [re.escape(k) for k in sorted_keys]
    translation_pattern = re.compile(f"({'|'.join(escaped_keys)})")

    outputs.append(f"[+] Scanning workspace: {workspace_dir}")
    untranslated_count = 0

    for root, _, files in os.walk(workspace_dir):
        for file in files:
            if not file.endswith(TARGET_EXTENSIONS):
                continue
                
            file_path = os.path.join(root, file)
            _, ext = os.path.splitext(file_path)
            relative_path = os.path.relpath(file_path, workspace_dir)
            
            with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
                content = f.read()
            
            normalized_content = decode_chinese_unicode_escapes(content)
            lines = normalized_content.splitlines()
            updated_lines = []
            
            for line in lines:
                updated_line = process_line_by_file_type(line, ext, translation_pattern, translations)
                updated_lines.append(updated_line)
            
            updated_content = '\n'.join(updated_lines)
            if normalized_content.endswith('\n') and not updated_content.endswith('\n'):
                updated_content += '\n'
            
            if updated_content != normalized_content:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(updated_content)
                outputs.append(f"[✓] Localized: {relative_path}")
            
            lines_original = normalized_content.splitlines()
            for idx, line in enumerate(updated_lines):
                if CHINESE_REGEX.search(line):
                    untranslated_count += 1
                    orig_line = lines_original[idx] if idx < len(lines_original) else ""
                    outputs.append(f"    [!] MISSING/PARTIAL at {relative_path}:{idx + 1} || {orig_line} -> {line.strip()}")

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