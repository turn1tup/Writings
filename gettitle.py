import os

dir = "."


def get_files(folder_path, interests=[".md", ".xlsx","pptx"]):
    results = []
    for root, dirs, files in os.walk(folder_path):
        for file in files:
            if any(file.endswith(ext) for ext in interests):
                file_path = os.path.join(root, file)
                results.append(file_path)

    return results


files = get_files(dir)


class Writing:
    def __init__(self, id, y, m, file):
        self.id = id
        self.y = y
        self.m = m
        self.file = file




writings = []
for file in files:
    sp_file = file.split(os.path.sep)
    if len(sp_file) < 3:
        continue
    y = int(sp_file[1])
    m = int(sp_file[2])
    writings.append(Writing(y*100+m,y, m, file))

writings = sorted(writings,reverse=True, key=lambda x:x.id)

current_y = None
current_m = None
for w in writings:
    if current_y is None or w.y != current_y:
        print(f"## {w.y}年\r\n")
        current_y = w.y
        current_m = None
    if current_m is None or w.m != current_m:
        print(f"### {w.m}月\r\n")
        current_m = w.m
    line = f"[{os.path.basename(w.file).split('.')[0]}]({w.file})\r\n"
    line = line.replace("\\","/")
    print(line)