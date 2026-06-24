import os
import re
import io

try:
    from ruamel.yaml import YAML
except ImportError:
    print("[-] Critical Error: Missing required library 'ruamel.yaml'.")
    print("[-] Please install it by running: pip install ruamel.yaml")
    exit(1)

# Initialize the YAML handler
yaml = YAML()
yaml.preserve_quotes = True
yaml.width = 4096 # Prevent unwanted line wrapping

TARGET_EXTENSIONS = ('.yml', '.yaml', '.json', '.mcmeta', '.properties', '.java')

CHINESE_REGEX = re.compile(r'[\u4e00-\u9fff]')
UNICODE_ESCAPE_REGEX = re.compile(r'\\u([0-9a-fA-F]{4})')
COLOR_CODE_REGEX = re.compile(r'§#[0-9a-fA-F]{6}|§[0-9a-fA-fk-orK-OR0-9]')

JSON_VAL_REGEX = re.compile(r'^(\s*"(?:subtitle|name|description|text)"\s*:\s*")(.*)("\s*,?\s*)$')
PROPERTIES_REGEX = re.compile(r'^([^=]+=\s*)(.*)$')

# --- FEATURE: WHITELIST FILTERING ---
# If a line/key/context doesn't contain any of these, it will be skipped.
# Leave empty [] to translate everything.
WHITELIST_SUBSTRINGS = ['lore', 'name', 'display']

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
            
            if ':' in stripped:
                key, val = stripped.split(':', 1)
                key = key.strip().strip("'\"")
                val = val.strip().strip("'\"")
                
                key = decode_chinese_unicode_escapes(key)
                val = decode_chinese_unicode_escapes(val)
                
                translations[key] = val
                
    return translations

def tokenize_and_translate_text(text, translation_pattern, translations):
    if not text:
        return text

    raw_segments = translation_pattern.split(text)
    if len(raw_segments) == 1:
        return text

    segments = []
    for i, seg in enumerate(raw_segments):
        if not seg: continue
        is_translated = (i % 2 == 1)
        text_content = translations[seg] if is_translated else seg
        segments.append(text_content)

    if not segments: return ""

    result = segments[0]
    for curr_text in segments[1:]:
        clean_prev = COLOR_CODE_REGEX.sub('', result)
        clean_curr = COLOR_CODE_REGEX.sub('', curr_text)

        if not clean_prev or not clean_curr:
            result += curr_text
            continue

        c1 = clean_prev[-1]
        c2 = clean_curr[0]
        add_space = False
        
        if not c1.isspace() and not c2.isspace():
            if c1.isalnum() and c2.isalnum():
                is_c1_chinese = bool(CHINESE_REGEX.fullmatch(c1))
                is_c2_chinese = bool(CHINESE_REGEX.fullmatch(c2))
                if not (is_c1_chinese and is_c2_chinese):
                    add_space = True

        result += (' ' + curr_text) if add_space else curr_text

    return result

def translate_yaml_node(node, translation_pattern, translations, parent_key=""):
    """
    Recursively walk the ruamel.yaml AST. Translates keys/values/lists and migrates comments.
    """
    changed = False
    
    if isinstance(node, dict):
        keys = list(node.keys())
        for k in keys:
            new_k = k
            
            # 1. Translate the key
            if isinstance(k, str) and CHINESE_REGEX.search(k):
                if not WHITELIST_SUBSTRINGS or any(sub in k for sub in WHITELIST_SUBSTRINGS):
                    new_k = tokenize_and_translate_text(k, translation_pattern, translations)
            
            # 2. Re-insert the key to preserve order AND migrate block comments
            if new_k != k:
                val = node[k]
                pos = list(node.keys()).index(k)
                comments = node.ca.items.get(k, None) # Grab attached comments
                
                node.pop(k)
                node.insert(pos, new_k, val)
                
                # Re-attach the multiline/inline comments to the new English key
                if comments:
                    node.ca.items[new_k] = comments
                    
                changed = True
                k = new_k # Update active key for value processing
                
            # 3. Translate the value
            val = node[k]
            if isinstance(val, str) and CHINESE_REGEX.search(val):
                if not WHITELIST_SUBSTRINGS or any(sub in str(k) or sub in val for sub in WHITELIST_SUBSTRINGS):
                    node[k] = tokenize_and_translate_text(val, translation_pattern, translations)
                    changed = True
            else:
                if translate_yaml_node(val, translation_pattern, translations, parent_key=str(k)):
                    changed = True
                    
    elif isinstance(node, list):
        for i in range(len(node)):
            item = node[i]
            if isinstance(item, str) and CHINESE_REGEX.search(item):
                # Inherit the parent_key (e.g., 'lore') to see if this list item passes the whitelist
                if not WHITELIST_SUBSTRINGS or any(sub in parent_key or sub in item for sub in WHITELIST_SUBSTRINGS):
                    node[i] = tokenize_and_translate_text(item, translation_pattern, translations)
                    changed = True
            else:
                if translate_yaml_node(item, translation_pattern, translations, parent_key=parent_key):
                    changed = True
                    
    return changed

def process_line_by_file_type(line, ext, translation_pattern, translations):
    # Fallback handler for non-YAML files (json, properties, mcmeta)
    if WHITELIST_SUBSTRINGS and not any(sub in line for sub in WHITELIST_SUBSTRINGS):
        if not CHINESE_REGEX.search(line):
            return line

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

    sorted_keys = sorted(translations.keys(), key=len, reverse=True)
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
            
            if ext in ('.yml', '.yaml'):
                try:
                    yaml_data = yaml.load(normalized_content)
                    if yaml_data is not None:
                        changed = translate_yaml_node(yaml_data, translation_pattern, translations, parent_key="")
                        if changed:
                            buf = io.StringIO()
                            yaml.dump(yaml_data, buf)
                            updated_content = buf.getvalue()
                        else:
                            updated_content = normalized_content
                    else:
                        updated_content = normalized_content
                except Exception as e:
                    outputs.append(f"[-] Error parsing YAML structure in {relative_path}: {e}")
                    updated_content = normalized_content
            else:
                lines = normalized_content.splitlines()
                updated_lines = []
                for line in lines:
                    updated_line = process_line_by_file_type(line, ext, translation_pattern, translations)
                    updated_lines.append(updated_line)
                
                updated_content = '\n'.join(updated_lines)
                if normalized_content.endswith('\n') and not updated_content.endswith('\n'):
                    updated_content += '\n'
            
            # Write out changes
            if updated_content != normalized_content:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(updated_content)
                outputs.append(f"[✓] Localized: {relative_path}")
            
            # Tally missing coverage (Applies to both yaml and text file states)
            lines_original = normalized_content.splitlines()
            for idx, line in enumerate(updated_content.splitlines()):
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