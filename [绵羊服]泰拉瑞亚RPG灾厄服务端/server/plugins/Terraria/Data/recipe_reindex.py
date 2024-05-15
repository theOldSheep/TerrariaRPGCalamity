import yaml

def get_item_type(full_section):
    return full_section.split(":")[0]

def _prepare_recipe_category(recipe_data_section, armor_items, accessory_items, potion_items, weapon_items, material_items):
    """
    This function tries to predict unknown "recipeCategory" from a set of rules.

    Args:
        recipe_data_section (dictionary): the recipe's section in the configuration.
        armor_items (set): A set of all armor items read from the file.
        accessory_items (set): A set of all accessory items read from the file.
        potion_items (dictionary): Information about potion items from 'potionItem.yml'.
        weapon_items (dictionary): Information about weapon items from 'items.yml'.
        material_items (set): A set of all material items gathered from recipes.
    """
    if "recipeCategory" not in recipe_data_section:
        result_item = get_item_type( recipe_data_section.get("resultItem") )
        # Check Armor and Accessory
        if result_item in armor_items:
            recipe_data_section["recipeCategory"] = "ARMOR"
        elif result_item in accessory_items:
            recipe_data_section["recipeCategory"] = "ACCESSORY"
        # Check Potions
        elif result_item in potion_items:
            if "maxHealth" in potion_items[result_item] or "maxMana" in potion_items[result_item]:
                recipe_data_section["recipeCategory"] = "CONSUMABLE_PERMANENT"
            else:
                recipe_data_section["recipeCategory"] = "CONSUMABLE"
        # Check Weapons
        elif result_item in weapon_items:
            damage_type = weapon_items[result_item]["attributes"]["damageType"]
            recipe_data_section["recipeCategory"] = f"WEAPON_{damage_type.upper()}"
        # Check Material
        elif result_item in material_items:
            recipe_data_section["recipeCategory"] = "MATERIAL"


def clean_recipe_indices(config_file_path, save_file_path, armor_set_file_path, items_file_path, potion_items_file_path):
    """
    This function cleans the recipe indices, adds 'recipeCategory' (respecting existing ones),
    and maintains a 'categoryOrder' section. It also identifies armor, accessory, consumable, weapon,
    and material recipes based on 'armorSet.yml', 'items.yml', and 'potionItem.yml'.

    Args:
        config_file_path (str): Path to the input YAML configuration file.
        save_file_path (str): Path to save the modified YAML configuration file.
        armor_set_file_path (str): Path to the 'armorSet.yml' file.
        items_file_path (str): Path to the 'items.yml' file.
        potion_items_file_path (str): Path to the 'potionItem.yml' file.
    """
    # Open source files
    with open(config_file_path, "r", encoding='UTF-8') as f:
        config = yaml.safe_load(f)
    with open(armor_set_file_path, "r", encoding='UTF-8') as f:
        armor_sets = yaml.safe_load(f)
    with open(items_file_path, "r", encoding='UTF-8') as f:
        items = yaml.safe_load(f)
    with open(potion_items_file_path, "r", encoding='UTF-8') as f:
        potion_items = yaml.safe_load(f)

    # Initialize category info and related lookup information
    all_categories = list()
    if ("categoryOrder" in config):
        all_categories = config["categoryOrder"]
    armor_items = set(armor_sets["pieces"].keys())
    accessory_items = set([item_name for item_name, item_data in items.items()
                           if "lore" in item_data and "[饰品]" in item_data['lore'][0]])
    weapon_items = {item_name: item_data for item_name, item_data in items.items()
                    if "attributes" in item_data and "damageType" in item_data["attributes"]}
    material_items = set([])
    for _, recipes in config.items():
        if _ == "categoryOrder":
            continue
        for _, recipe_data in recipes.items():
            if ("requireItem" in recipe_data):
                for item_str in recipe_data["requireItem"]:
                    # Extract item name even from things like "Item:2"
                    item_name = get_item_type(item_str)
                    material_items.add(item_name)

    for station, recipes in config.items():
        # This section is not a crafting station
        if station == "categoryOrder":
            continue

        new_recipes = {}
        recipe_index = 1
        for recipe_key in recipes.keys():
            if recipe_key == "containedStations":
                new_recipes[recipe_key] = recipes[recipe_key]
            else:
                recipe_data = recipes[recipe_key]
                new_recipes[recipe_index] = recipe_data
                recipe_index += 1

                _prepare_recipe_category(recipe_data, armor_items, accessory_items, potion_items, weapon_items, material_items)

                # Collect unique categories
                if "recipeCategory" in recipe_data:
                    recipe_category = recipe_data["recipeCategory"]
                    if recipe_category not in all_categories:
                        all_categories.append(recipe_category)
        config[station] = new_recipes

    config = {"categoryOrder": all_categories, **config}

    with open(save_file_path, "w", encoding='UTF-8') as f:
        yaml.safe_dump(config, f, default_flow_style=False, allow_unicode=True, sort_keys=False)

    print("Existing recipe categories:")
    for category in all_categories:
        print(f"- {category}")

if __name__ == "__main__":
    config_file_path = "recipes.yml"
    config_file_save_path = "recipes_fixed.yml"
    armor_set_file_path = "armorSet.yml"
    items_file_path = "items.yml"
    potion_items_file_path = "potionItem.yml"
    clean_recipe_indices(config_file_path, config_file_save_path, armor_set_file_path, items_file_path, potion_items_file_path)