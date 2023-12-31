import os
from PIL import Image

# Iterate through all .png files in the current directory
for filename in os.listdir("."):
    if filename.endswith(".png"):
        img = Image.open(filename)

        # Get the original image dimensions
        width, height = img.size
        n = width   # Assuming square images

        # Calculate new dimensions
        new_width = 8 * n
        new_height = 4 * n

        # Create a new, larger image with a white background
        new_img = Image.new("RGB", (new_width, new_height), color="white")

        # Paste the original image into the upper-left corner
        new_img.paste(img, (0, 0))

        # Save the resized image with a modified filename
        os.makedirs("resized_images", exist_ok=True)  # Create output directory
        new_filename = f"resized_images/{filename[:-4]}_resized.png"
        new_img.save(new_filename)

print("Images resized and saved successfully!")