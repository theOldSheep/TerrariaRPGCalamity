import os
import traceback
from PIL import Image

# https://stackoverflow.com/questions/41718892/pillow-resizing-a-gif
def analyseImage(im):
    results = {
        'size': im.size,
        'mode': 'full',
    }
    try:
        while True:
            if im.tile:
                tile = im.tile[0]
                update_region = tile[1]
                update_region_dimensions = update_region[2:]
                if update_region_dimensions != im.size:
                    results['mode'] = 'partial'
                    break
            im.seek(im.tell() + 1)
    except EOFError:
        pass
    return results


def extract_and_resize_frames(im, resize_to=None):
    """
    Iterate the GIF, extracting each frame and resizing them

    Returns:
        An array of all frames
    """
    mode = analyseImage(im)

    if not resize_to:
        resize_to = (im.size[0] // 2, im.size[1] // 2)

    i = 0
    p = im.getpalette()
    last_frame = im.convert('RGBA')

    all_frames = []

    try:
        while True:
            # print("saving %s (%s) frame %d, %s %s" % (path, mode, i, im.size, im.tile))

            '''
            If the GIF uses local colour tables, each frame will have its own palette.
            If not, we need to apply the global palette to the new frame.
            '''
            # if not im.getpalette():
            #     im.putpalette(p)

            new_frame = Image.new('RGBA', im.size)

            '''
            Is this file a "partial"-mode GIF where frames update a region of a different size to the entire image?
            If so, we need to construct the new frame by pasting it on top of the preceding frames.
            '''
            if mode == 'partial':
                new_frame.paste(last_frame)

            new_frame.paste(im, (0, 0), im.convert('RGBA'))

            # new_frame.thumbnail(resize_to, Image.ANTIALIAS)
            all_frames.append(new_frame)

            i += 1
            last_frame = new_frame
            im.seek(im.tell() + 1)
    except EOFError:
        pass

    return all_frames


def tile_image(input_dir, output_dir):
    """Tiles an image into an n x 4n transparent canvas with a specific pattern,
    keeping the original image size.
    """

    for filename in os.listdir(input_dir):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg', '.gif')):
            image_path = os.path.join(input_dir, filename)
            isgif = filename.lower().endswith('.gif')
            try:
                with Image.open(image_path) as img:
                    frames = [img]
                    if isgif:
                        frames = extract_and_resize_frames(img)
                    print(frames)
                    width, height = frames[0].size
                    single_size = max(width, height)
                    
                    hor_offset = int((single_size - width) / 2)
                    ver_offset = int((single_size - height) / 2)
                    
                    # Create a square img with content centered
                    sqr_img = Image.new("RGBA", (single_size, single_size * len(frames)), (0, 0, 0, 0))
                    for i in range(len(frames)):
                        sqr_img.paste(frames[i], (hor_offset, single_size * i + ver_offset))
                    # Save the tiled image
                    output_path = os.path.join(output_dir, filename.replace('.gif', '.png'))
                    sqr_img.save(output_path)

            except (OSError, ValueError) as e:
                print(f"Error processing {filename}: {e}")
                traceback.print_exception(e)

# Example usage:
image_dir = "rawimg"
output_dir = "target"
tile_image(image_dir, output_dir)