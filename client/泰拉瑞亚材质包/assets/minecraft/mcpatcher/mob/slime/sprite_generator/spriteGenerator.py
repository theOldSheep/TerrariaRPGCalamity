import os
from PIL import Image

def tile_image(input_dir, output_dir, vertical_offset_multi, six_or_four):
    """Tiles an image into an 8n x 4n transparent canvas with a specific pattern,
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
                    ver_offset = int((single_size - height) * vertical_offset_multi)

                    # Create transparent canvas with 8x width and 4x height
                    new_width = single_size * 8
                    new_height = single_size * 4
                    new_img = Image.new("RGBA", (new_width, new_height), (0, 0, 0, 0))

                    # Tile the original image in the specified pattern
                    positions = [] 
                    # First row
                    if (six_or_four):
                        for i in range(1, 3):
                            positions.append( (i * single_size + hor_offset, ver_offset) )
                    # Second row
                    for i in range(0, 4):
                        positions.append( (i * single_size + hor_offset, single_size + ver_offset) )
                    # draw in positions
                    for pos in positions:
                        new_img.paste(img, pos)

                    # Save the tiled image
                    output_path = os.path.join(output_dir, filename)
                    new_img.save(output_path)

            except (OSError, ValueError) as e:
                print(f"Error processing {filename}: {e}")

# Example usage:
image_dir_4s = "4sided"
image_dir_4s_fl = "4sided_floor"
image_dir_6s = "6sided"
output_dir = "target"
tile_image(image_dir_4s, output_dir, 0.5, False)
tile_image(image_dir_4s_fl, output_dir, 1, False)
tile_image(image_dir_6s, output_dir, 0.5, True)