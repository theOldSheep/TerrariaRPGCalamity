import os
from PIL import Image

THRESHOLD = 10

def tile_image(subdir):
    """Tiles the legging's sprite to create model sprite for boots
    """
    l1_path = os.path.join(subdir, f"{subdir}_layer_1.png")
    l2_path = os.path.join(subdir, f"{subdir}_layer_2.png")

    try:
        with Image.open(l1_path).convert('RGBA') as iml1, Image.open(l2_path).convert('RGBA') as iml2:
            assert iml1.size in [(128, 64), (64, 32)]
            assert iml2.size in [(128, 64), (64, 32)]
            assert iml1.size == iml2.size

            unit_size = iml1.size[1] // 32
            
            # Copy the side sprites
            cropped = iml2.crop((0, unit_size * 23, unit_size * 16, unit_size * 29))
            iml1.paste(cropped, (0, unit_size * 26))

            # Generate the bottom sprite - find darkest 2 colors
            colors = set()
            for i in range(cropped.size[0]):
                for j in range(cropped.size[1]):
                    curr = cropped.getpixel((i,j))
                    if curr[3] < 200:
                        continue
                    colors.add( (curr[0],curr[1],curr[2],255) )
            # Convert to list and sort
            col_lst = list(colors)
            col_lst.sort(key = (lambda i: i[0] + i[1] + i[2]))
            # Find darkest & second darkest, with thresholding
            darkest = col_lst[0]
            darkest_brightness = darkest[0] + darkest[1] + darkest[2]
            second_darkest = darkest
            for i in range(1, len(col_lst)):
                curr = col_lst[i]
                curr_brightness = curr[0] + curr[1] + curr[2]
                second_darkest = curr
                if curr_brightness < darkest_brightness - THRESHOLD:
                    break
            # Set bottom pixels with the darkest 2 colors
            l1pixels = iml1.load()
            for i in range(0, unit_size * 4):
                for j in range(0, unit_size * 4):
                    col = second_darkest
                    if i in [unit_size * 4 - 1, 0] or j in [unit_size * 4 - 1, 0]:
                        col = darkest
                    elif i in [unit_size * 4 - 2, 1] and j in [unit_size * 4 - 2, 1]:
                        col = darkest
                    l1pixels[unit_size * 8 + i, unit_size * 16 + j] = col

            # Save the tiled image
            iml1.save(l1_path)

    except (OSError, ValueError, AssertionError) as e:
        print(f"Error processing {subdir}: {e}")

for subdir in os.listdir():
    if '.' in subdir:
        continue
    print("Handling:", subdir)
    tile_image(subdir)
    # break