import os
from PIL import Image

def tile_image(input_dir, output_dir):
    """Tiles an image into an 8n x 4n transparent canvas with a specific pattern,
    keeping the original image size.
    """

    for filename in os.listdir(input_dir):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            image_path = os.path.join(input_dir, filename)
            try:
                with Image.open(image_path) as img:
                    width, height = img.size

                    # Create transparent canvas with 8x width and 4x height
                    new_width = width * 8
                    new_height = height * 4
                    new_img = Image.new("RGBA", (new_width, new_height), (0, 0, 0, 0))

                    # Tile the original image in the specified pattern
                    positions = [(width, 0), (2 * width, 0),  # First row
                                  (0, height), (width, height), (2 * width, height), (3 * width, height)]  # Second row
                    for pos in positions:
                        new_img.paste(img, pos)

                    # Save the tiled image
                    output_path = os.path.join(output_dir, filename)
                    new_img.save(output_path)

            except (OSError, ValueError) as e:
                print(f"Error processing {filename}: {e}")

# Example usage:
image_dir = "src"
output_dir = "target"
tile_image(image_dir, output_dir)