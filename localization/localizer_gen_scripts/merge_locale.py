import os
import re
from collections import defaultdict
import yaml  # Requires `pip install pyyaml`

# =====================================================================
# CONFIGURATION
# =====================================================================
TOPIC_FILES = [
    "localizer_gen_scripts/locale_EN_armor_sets.yml",
    "localizer_gen_scripts/locale_EN_biome_height.yml",
    "localizer_gen_scripts/locale_EN_boss_event.yml",
    "localizer_gen_scripts/locale_EN_buffs.yml",
    "localizer_gen_scripts/locale_EN_death_msg_title.yml",
    "localizer_gen_scripts/locale_EN_enemy.yml",
    "localizer_gen_scripts/locale_EN_game_title.yml",
    "localizer_gen_scripts/locale_EN_item_lore_generated.yml",
    "localizer_gen_scripts/locale_EN_items.yml",
    "localizer_gen_scripts/locale_EN_java.yml",
    "localizer_gen_scripts/locale_EN_menu.yml",
    "localizer_gen_scripts/locale_EN_NPC.yml",
    "localizer_gen_scripts/locale_EN_prefix.yml",
    "localizer_gen_scripts/locale_EN_projectiles.yml",
    "localizer_gen_scripts/locale_EN_resourcepack.yml",
    "localizer_gen_scripts/locale_EN_status_msg.yml",
]

OVERRIDE_FILE = "localizer_gen_scripts/locale_EN_override.yml"
OUTPUT_FILE = "localizer_gen_scripts/processed.yml"

# =====================================================================
# HELPER FUNCTIONS
# =====================================================================


def parse_yaml_lines(file_path):
    """Parses a YAML file safely using PyYAML, handling comments and complex quoting."""
    translations = {}
    if not os.path.exists(file_path):
        print(f"[Warning] File not found: {file_path}")
        return translations

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            # yaml.safe_load automatically skips comments and handles all string quotes perfectly
            data = yaml.safe_load(f)

            if data and isinstance(data, dict):
                translations = data
            else:
                print(
                    f"[Warning] File parsed successfully but did not return a valid dictionary structure."
                )

    except yaml.YAMLError as e:
        print(f"[Error] Failed to parse YAML file due to a syntax issue: {e}")
    except Exception as e:
        print(f"[Error] An unexpected error occurred: {e}")

    return translations

# =====================================================================
# MAIN PROCESSING
# =====================================================================
def main():
    # 1. Load files
    print("Loading override file...")
    overrides = parse_yaml_lines(OVERRIDE_FILE)

    print("Loading topic files...")
    # Map to track key -> list of all translations found across topics
    key_to_topic_translations = defaultdict(list)
    
    original_ordering = []
    original_ordering_seen = set()
    for path in TOPIC_FILES:
        print(f"Loading topic file {path}...")
        topic_data = parse_yaml_lines(path)
        for key, value in topic_data.items():
            if key not in original_ordering_seen:
                original_ordering.append(key)
                original_ordering_seen.add(key)
            key_to_topic_translations[key].append(value)
    for key, value in overrides.items():
        if key not in original_ordering_seen:
            original_ordering.append(key)
            original_ordering_seen.add(key)

    # 2. Process unique keys and apply rules
    final_translations = {}
    warning_inconsistencies = {}  # key -> list of distinct translations

    # Gather all unique keys from both overrides and topics
    all_keys = set(overrides.keys()).union(key_to_topic_translations.keys())

    for key in all_keys:
        # Rule #1: Key is in override
        if key in overrides:
            final_translations[key] = overrides[key]
            continue

        # Fetch translations from topics
        topic_vals = key_to_topic_translations.get(key, [])
        distinct_vals = list(set(topic_vals))

        # Rule #2: All topics agree (1 or more occurrences, but only 1 unique value)
        if len(distinct_vals) == 1:
            final_translations[key] = distinct_vals[0]
        
        # Rule #3: Topics disagree
        elif len(distinct_vals) > 1:
            warning_inconsistencies[key] = distinct_vals
            final_translations[key] = "TODO"

    # 3. Additional check: Ambiguity (Different keys mapping to the same final value)
    value_to_keys = defaultdict(list)
    for key, val in final_translations.items():
        if val != "TODO":  # Exclude unresolved TODOs from ambiguity warnings
            value_to_keys[val].append(key)

    warning_ambiguities = {
        val: keys for val, keys in value_to_keys.items() if len(keys) > 1
    }

    # =====================================================================
    # OUTPUT AND REPORTING
    # =====================================================================
    # Save processed key-values
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        towrite = []
        towrite.append("# Generated Translation File\n")
        for key in original_ordering:
            # Safe-formatting with double quotes
            towrite.append(f'"{key}": "{final_translations[key]}"')
        f.write('\n'.join(towrite))
            
    print(f"\nSuccessfully saved compiled translations to: {OUTPUT_FILE}")

    # Print Warning Inconsistencies
    print("\n" + "="*50)
    print(" WARNING: INCONSISTENCIES (Multi-translations for same key)")
    print("="*50)
    if warning_inconsistencies:
        for key, vals in sorted(warning_inconsistencies.items()):
            print(f"Key: '{key}' has conflicting translations: {vals}")
    else:
        print("None! All topic translations align cleanly.")

    # Print Warning Ambiguities
    print("\n" + "="*50)
    print(" WARNING: AMBIGUITIES (Different keys translating to the same value)")
    print("="*50)
    if warning_ambiguities:
        for val, keys in sorted(warning_ambiguities.items()):
            print(f"Value: '{val}' is shared by multiple keys: {keys}")
    else:
        print("None! All values map to unique keys.")

if __name__ == "__main__":
    main()