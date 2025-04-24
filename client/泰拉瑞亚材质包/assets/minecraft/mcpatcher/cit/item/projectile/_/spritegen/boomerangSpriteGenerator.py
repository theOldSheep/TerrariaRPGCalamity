import os
from PIL import Image

def tile_image(input_dir, output_dir):
    """Tiles an image into an n x 4n transparent canvas with a specific pattern,
    keeping the original image size.
    """

    for filename in os.listdir(input_dir):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            image_path = os.path.join(input_dir, filename)
            try:
                with Image.open(image_path) as img:
                    width, height = img.size
                    single_size = max(width, height)
                    
                    hor_offset = int((single_size - width) / 2)
                    ver_offset = int((single_size - height) / 2)
                    
                    # Create a square img with content centered
                    sqr_img = Image.new("RGBA", (single_size, single_size), (0, 0, 0, 0))
                    sqr_img.paste(img, (hor_offset, ver_offset))

                    # Create transparent canvas with 1x width and 4x height
                    new_width = single_size
                    new_height = single_size * 4
                    new_img = Image.new("RGBA", (new_width, new_height), (0, 0, 0, 0))

                    # Tile the original image in the specified pattern
                    for i in range(0, 4):
                        pos = (0, i * single_size)
                        new_img.paste(sqr_img, pos)
                        sqr_img = sqr_img.rotate(-90)

                    # Save the tiled image
                    output_path = os.path.join(output_dir, filename)
                    new_img.save(output_path)

            except (OSError, ValueError) as e:
                print(f"Error processing {filename}: {e}")

# Example usage:
image_dir = "boomerang"
output_dir = "target"
tile_image(image_dir, output_dir)