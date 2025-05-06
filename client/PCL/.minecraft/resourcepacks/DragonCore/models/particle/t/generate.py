import os

for filename in os.listdir():
    print(filename)
    if filename.lower().endswith(('.json')):
        print("JSON!")
        with open(filename, 'r') as f:
            lines = ''.join(f.readlines())
            dir_name = filename.replace('.json', '')
            # create folder
            try:
                os.mkdir(dir_name)
            except:
                pass
            for i in range(65):
                # 16-64: only even length
                if (i > 16 and i % 2 != 0):
                    continue
                for j in range(2):
                    # only 0-4 has .5 accuracy
                    if (i >= 5 and j != 0):
                        continue
                    # save file
                    trail_len = i + 0.5 * j
                    if (trail_len < 1e-9):
                        continue
                    save_name = f"{dir_name}/{i}"
                    if (j != 0):
                        save_name += f"_{j}"
                    save_name += ".json"
                    with open(save_name, 'w') as save_file:
                        save_file.write(lines.replace("variable.len = 1", f"variable.len = {trail_len}"))